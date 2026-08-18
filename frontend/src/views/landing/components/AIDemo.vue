<script setup lang="ts">
/**
 * AIDemo.vue —— 官网 "@AI 召唤交互演示区"
 *
 * 核心交互：
 * 1. 用户在输入框键入，监测 input 事件；检测到 "@AI" 时激活高亮 + 扫描光带 + 浮动就绪标签
 * 2. 下方功能芯片点击后自动填充输入并展示对应 AI 回复预览气泡
 *
 * 技术要点：
 * - ref 管理 inputText / isAIActive / activePreview
 * - discriminated union 实现按类型渲染预览，类型安全
 * - :key 重触发 scanLine 一次性动画
 * - 弹性缓动 cubic-bezier 控制所有过渡
 */
import { ref, computed } from 'vue'

/* ============================================================
   类型定义
   ============================================================ */

/** 功能芯片标识 */
type ChipKey = 'summary' | 'weekly' | 'translate' | 'tech'

/** 会议总结要点 */
interface MeetingPoint {
  no: number
  text: string
}

/** 周报条目（含日期与完成状态） */
interface WeeklyItem {
  date: string
  task: string
  done: boolean
}

/** 翻译润色前后对比 */
interface TranslateCompare {
  before: string
  after: string
}

/** 推荐技术方案 */
interface TechSolution {
  name: string
  reason: string
  tags: string[]
}

/** AI 回复预览内容（联合类型，按 type 区分渲染结构） */
type PreviewContent =
  | { type: 'summary'; title: string; points: MeetingPoint[] }
  | { type: 'weekly'; title: string; items: WeeklyItem[] }
  | { type: 'translate'; title: string; compare: TranslateCompare }
  | { type: 'tech'; title: string; solution: TechSolution }

/** 功能芯片定义 */
interface Chip {
  key: ChipKey
  label: string
  hint: string
}

/* ============================================================
   静态数据
   ============================================================ */

/** 四个功能芯片 */
const chips: Chip[] = [
  { key: 'summary', label: '/总结会议', hint: '提炼会议要点' },
  { key: 'weekly', label: '/生成周报', hint: '一键生成周报' },
  { key: 'translate', label: '/翻译润色', hint: '中英互译优化' },
  { key: 'tech', label: '/推荐技术方案', hint: '智能方案推荐' }
]

/** 每个芯片对应的静态 AI 回复预览（对象映射） */
const previewMap: Record<ChipKey, PreviewContent> = {
  // 会议总结 —— 编号列表
  summary: {
    type: 'summary',
    title: '已为你总结本次会议要点',
    points: [
      { no: 1, text: 'Q3 产品迭代周期缩短至两周，需在 8 月 15 日前完成需求评审。' },
      { no: 2, text: '前端采用 Vue 3 + TypeScript 重构聊天工作台，预计 9 月初灰度上线。' },
      { no: 3, text: 'AI 召唤功能接入 Ollama 本地模型，dev 环境使用 deepseek-r1:8b。' },
      { no: 4, text: '下次会议定于周五 15:00，重点同步联调进度与上线 Checklist。' }
    ]
  },
  // 周报 —— 日期 + 完成状态
  weekly: {
    type: 'weekly',
    title: '本周工作周报已生成',
    items: [
      { date: '周一', task: '完成聊天工作台三栏布局重构', done: true },
      { date: '周二', task: 'WebSocket 断线重连机制优化', done: true },
      { date: '周三', task: 'AI 流式回复 SSE 接口联调', done: true },
      { date: '周四', task: '群组 @提及消息推送联调', done: false },
      { date: '周五', task: '上线前回归测试与性能压测', done: false }
    ]
  },
  // 翻译润色 —— 优化前后对比
  translate: {
    type: 'translate',
    title: '翻译并润色完成',
    compare: {
      before: 'The chat app is good for talking with friends.',
      after: 'ChatVibe delivers a warm, real-time messaging experience that brings friends closer — making every conversation truly meaningful.'
    }
  },
  // 技术方案推荐 —— 方案卡片
  tech: {
    type: 'tech',
    title: '为你推荐最佳技术方案',
    solution: {
      name: 'Vue 3 + Pinia + WebSocket',
      reason:
        '渐进式框架搭配轻量状态管理，结合 SockJS/STOMP 实时通信，开发效率高、生态成熟、类型安全，完美匹配聊天应用的高频更新与实时推送需求。',
      tags: ['Vue 3.5', 'TypeScript', 'Pinia', 'SockJS/STOMP']
    }
  }
}

/* ============================================================
   响应式状态
   ============================================================ */

/** 输入框文本 */
const inputText = ref('')
/** 是否检测到 @AI（激活态） */
const isAIActive = ref(false)
/** 当前展示的预览内容 */
const activePreview = ref<PreviewContent | null>(null)
/** 扫描光带动画重触发 key */
const scanKey = ref(0)

/** 当前激活的芯片标识（用于高亮） */
const activeChipKey = computed<ChipKey | null>(
  () => activePreview.value?.type ?? null
)

/* ============================================================
   交互逻辑
   ============================================================ */

/**
 * 输入事件处理：检测 "@AI" 关键字
 * - 包含 @AI：激活高亮、扫描光带、浮动标签
 * - 不包含：恢复默认，并清空预览
 */
function onInput(e: Event): void {
  const target = e.target as HTMLInputElement
  inputText.value = target.value

  const wasActive = isAIActive.value
  isAIActive.value = inputText.value.includes('@AI')

  // 从非激活 -> 激活：重触发扫描动画
  if (isAIActive.value && !wasActive) {
    scanKey.value++
  }
  // 失去激活：清空预览气泡
  if (!isAIActive.value && activePreview.value) {
    activePreview.value = null
  }
}

/**
 * 点击功能芯片：
 * 1. 输入框自动填入 "@AI /命令"
 * 2. 触发 @AI 检测效果（高亮 + 扫描 + 标签）
 * 3. 在输入框上方淡入对应 AI 回复预览
 */
function selectChip(chip: Chip): void {
  inputText.value = `@AI ${chip.label}`
  isAIActive.value = true
  activePreview.value = previewMap[chip.key]
  scanKey.value++
}

/** 清空输入与预览 */
function clearInput(): void {
  inputText.value = ''
  isAIActive.value = false
  activePreview.value = null
}
</script>

<template>
  <section class="ai-demo" id="ai-demo">
    <!-- 标题区 -->
    <div class="section-head ai-tag">
      <div class="section-tag">AI 召唤</div>
      <h2>试试看，如何召唤 <span class="ai-grad-text">AI</span>？</h2>
      <p>输入 @AI 召唤智能助手</p>
    </div>

    <div class="ai-demo__stage">
      <!-- AI 回复预览气泡（输入框上方，弹性缓动进入） -->
      <Transition name="preview-pop">
        <div v-if="activePreview" class="ai-preview" :key="activePreview.type">
          <!-- 气泡头部 -->
          <div class="ai-preview__head">
            <div class="ai-preview__avatar"><InkBallAvatar /></div>
            <div class="ai-preview__meta">
              <div class="ai-preview__name">智能助手</div>
              <div class="ai-preview__title">{{ activePreview.title }}</div>
            </div>
            <button
              class="ai-preview__close"
              type="button"
              aria-label="关闭预览"
              @click="clearInput"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>

          <!-- 气泡主体：按 type 渲染不同结构 -->
          <div class="ai-preview__body">
            <!-- 会议总结：编号列表 -->
            <template v-if="activePreview.type === 'summary'">
              <ol class="meeting-list">
                <li v-for="point in activePreview.points" :key="point.no">
                  <span class="meeting-list__no">{{ point.no }}</span>
                  <span class="meeting-list__text">{{ point.text }}</span>
                </li>
              </ol>
            </template>

            <!-- 周报：日期 + 完成状态 -->
            <template v-else-if="activePreview.type === 'weekly'">
              <ul class="weekly-list">
                <li v-for="(item, i) in activePreview.items" :key="i">
                  <span class="weekly-list__date">{{ item.date }}</span>
                  <span class="weekly-list__task">{{ item.task }}</span>
                  <span
                    class="weekly-list__status"
                    :class="item.done ? 'is-done' : 'is-todo'"
                  >
                    {{ item.done ? '已完成' : '进行中' }}
                  </span>
                </li>
              </ul>
            </template>

            <!-- 翻译润色：前后对比 -->
            <template v-else-if="activePreview.type === 'translate'">
              <div class="compare">
                <div class="compare__block compare__before">
                  <div class="compare__label">优化前</div>
                  <p>{{ activePreview.compare.before }}</p>
                </div>
                <div class="compare__arrow">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="5" y1="12" x2="19" y2="12"></line>
                    <polyline points="12 5 19 12 12 19"></polyline>
                  </svg>
                </div>
                <div class="compare__block compare__after">
                  <div class="compare__label">优化后</div>
                  <p>{{ activePreview.compare.after }}</p>
                </div>
              </div>
            </template>

            <!-- 技术方案推荐：方案卡片 -->
            <template v-else-if="activePreview.type === 'tech'">
              <div class="tech-solution">
                <div class="tech-solution__name">{{ activePreview.solution.name }}</div>
                <p class="tech-solution__reason">{{ activePreview.solution.reason }}</p>
                <div class="tech-solution__tags">
                  <span v-for="tag in activePreview.solution.tags" :key="tag">{{ tag }}</span>
                </div>
              </div>
            </template>
          </div>
        </div>
      </Transition>

      <!-- 聊天输入框（外层容纳浮动标签，内层裁剪扫描光带） -->
      <div class="ai-input-wrap">
        <!-- 浮动就绪标签（紫罗兰渐变） -->
        <Transition name="ready-pop">
          <span v-if="isAIActive" class="ai-input__ready">✨ 智能助手已就绪</span>
        </Transition>

        <div class="ai-input" :class="{ 'is-active': isAIActive }">
          <!-- 扫描光带：检测到 @AI 时从左扫到右 -->
          <div
            v-if="isAIActive"
            class="ai-input__scan"
            :key="scanKey"
          ></div>

          <input
            class="ai-input__field"
            type="text"
            :value="inputText"
            placeholder="输入 @AI 询问任何事情..."
            aria-label="AI 召唤输入框"
            @input="onInput"
          />

          <!-- 发送按钮 -->
          <button
            class="ai-input__send"
            type="button"
            aria-label="发送"
            :class="{ 'is-active': isAIActive }"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"></line>
              <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
            </svg>
          </button>
        </div>
      </div>

      <!-- 功能芯片 -->
      <div class="ai-chips">
        <button
          v-for="chip in chips"
          :key="chip.key"
          class="ai-chip"
          :class="{ 'is-active': activeChipKey === chip.key }"
          type="button"
          :aria-label="chip.hint"
          @click="selectChip(chip)"
        >
          <svg class="ai-chip__icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3l1.9 5.8a2 2 0 0 0 1.3 1.3L21 12l-5.8 1.9a2 2 0 0 0-1.3 1.3L12 21l-1.9-5.8a2 2 0 0 0-1.3-1.3L3 12l5.8-1.9a2 2 0 0 0 1.3-1.3L12 3z"></path>
          </svg>
          <span>{{ chip.label }}</span>
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
/* ============================================================
   区块容器
   ============================================================ */
.ai-demo {
  max-width: $landing-max-w;
  margin: 0 auto;
  padding: $section-py 48px;
  position: relative;
}

.ai-demo__stage {
  max-width: 760px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

/* ============================================================
   聊天输入框
   ============================================================ */
.ai-input-wrap {
  position: relative;
}

.ai-input {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 8px 8px 22px;
  border: 2px solid var(--landing-border);
  border-radius: $r-xl;
  background: var(--landing-card);
  box-shadow: $shadow-md;
  overflow: hidden; /* 裁剪扫描光带 */
  transition:
    border-color $dur-normal $ease-out-expo,
    box-shadow $dur-normal $ease-out-expo,
    background $dur-normal $ease-out-expo;

  /* 激活态：渐变边框（padding-box/border-box 技巧兼容圆角）+ 紫罗兰光晕 */
  &.is-active {
    border-color: transparent;
    background:
      linear-gradient(var(--landing-card), var(--landing-card)) padding-box,
      var(--grad-ai) border-box;
    box-shadow: $shadow-ai, $shadow-glow-ai;
  }
}

/* 扫描光带：从左扫到右，一次性动画 */
.ai-input__scan {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 45%;
  left: -100%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    var(--ai-fill) 35%,
    var(--ai-scan-mid) 50%,
    var(--ai-fill) 65%,
    transparent 100%
  );
  pointer-events: none;
  z-index: 1;
  animation: scanLine 0.9s $ease-out-expo;
}

.ai-input__field {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: $font-body;
  font-size: 16px;
  color: var(--landing-text);
  padding: 13px 0;
  position: relative;
  z-index: 2;

  &::placeholder {
    color: var(--landing-text-muted);
  }
}

/* 发送按钮 */
.ai-input__send {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: $r-full;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: var(--landing-text-muted);
  position: relative;
  z-index: 2;
  transition: all $dur-normal $ease-out-expo;

  &.is-active {
    background: var(--grad-ai);
    box-shadow: $shadow-ai;
  }

  &:not(.is-active) {
    opacity: 0.7;
    cursor: not-allowed;
  }
}

/* 浮动就绪标签（输入框右上角浮现） */
.ai-input__ready {
  position: absolute;
  top: -13px;
  right: 28px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 14px;
  font-family: $font-body;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: var(--grad-ai);
  border-radius: $r-full;
  box-shadow: $shadow-ai;
  white-space: nowrap;
}

/* 浮动标签过渡（弹性缓动） */
.ready-pop-enter-active {
  transition: all 0.4s $ease-spring;
}
.ready-pop-leave-active {
  transition: all 0.25s $ease-smooth;
}
.ready-pop-enter-from {
  opacity: 0;
  transform: translateY(10px) scale(0.8);
}
.ready-pop-leave-to {
  opacity: 0;
  transform: translateY(4px) scale(0.9);
}

/* ============================================================
   功能芯片
   ============================================================ */
.ai-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}

.ai-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 10px 20px;
  border: 1.5px solid var(--landing-border);
  border-radius: $r-full;
  background: var(--landing-card);
  color: var(--landing-text-soft);
  font-family: $font-body;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all $dur-normal $ease-out-expo;

  &__icon {
    flex-shrink: 0;
    color: var(--c-ai-light);
    transition: color $dur-normal $ease-smooth;
  }

  &:hover {
    border-color: var(--c-ai-light);
    color: var(--c-ai);
    transform: translateY(-2px);
    box-shadow: $shadow-sm;

    .ai-chip__icon {
      color: var(--c-ai);
    }
  }

  /* 激活态：渐变描边 + 紫罗兰光晕 */
  &.is-active {
    border-color: transparent;
    background:
      linear-gradient(var(--landing-card), var(--landing-card)) padding-box,
      var(--grad-ai) border-box;
    color: var(--c-ai);
    box-shadow: $shadow-glow-ai;

    .ai-chip__icon {
      color: var(--c-ai);
    }
  }
}

/* ============================================================
   AI 回复预览气泡
   ============================================================ */
.ai-preview {
  border: 2px solid transparent;
  border-radius: $r-xl;
  /* 渐变边框 + 紫罗兰光晕 */
  background:
    linear-gradient(var(--landing-card), var(--landing-card)) padding-box,
    var(--grad-ai) border-box;
  box-shadow: $shadow-glow-ai, $shadow-md;
  padding: 22px 24px;
  overflow: hidden;
}

.ai-preview__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--landing-border);
}

.ai-preview__avatar {
  width: 38px;
  height: 38px;
  border-radius: $r-full;
  background: transparent;
  overflow: hidden;
  flex-shrink: 0;
  animation: floatGlow 3s ease-in-out infinite;
}

.ai-preview__meta {
  flex: 1;
  min-width: 0;
}

.ai-preview__name {
  font-size: 14px;
  font-weight: 700;
  color: var(--landing-text);
  line-height: 1.3;
}

.ai-preview__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-ai);
  margin-top: 2px;
}

.ai-preview__close {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: $r-full;
  background: transparent;
  color: var(--landing-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all $dur-fast $ease-smooth;

  &:hover {
    background: var(--landing-border);
    color: var(--landing-text);
  }
}

/* 预览气泡入场过渡（弹性缓动） */
.preview-pop-enter-active {
  transition: all 0.5s $ease-spring;
}
.preview-pop-leave-active {
  transition: all 0.3s $ease-smooth;
}
.preview-pop-enter-from {
  opacity: 0;
  transform: translateY(24px) scale(0.92);
}
.preview-pop-leave-to {
  opacity: 0;
  transform: translateY(-12px) scale(0.95);
}

/* ============================================================
   预览类型 1：会议总结（编号列表）
   ============================================================ */
.meeting-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 13px;

  li {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  &__no {
    flex-shrink: 0;
    width: 26px;
    height: 26px;
    border-radius: $r-sm;
    background: var(--ai-fill);
    color: var(--c-ai);
    font-family: $font-display;
    font-size: 12px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__text {
    font-size: 14px;
    line-height: 1.65;
    color: var(--landing-text);
    padding-top: 3px;
  }
}

/* ============================================================
   预览类型 2：周报（日期 + 完成状态）
   ============================================================ */
.weekly-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;

  li {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 11px 14px;
    border-radius: $r-md;
    background: var(--ai-fill-soft);
  }

  &__date {
    flex-shrink: 0;
    font-family: $font-display;
    font-size: 12px;
    font-weight: 700;
    color: var(--c-ai);
    min-width: 36px;
  }

  &__task {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    color: var(--landing-text);
  }

  &__status {
    flex-shrink: 0;
    font-size: 11px;
    font-weight: 600;
    padding: 3px 10px;
    border-radius: $r-full;

    &.is-done {
      background: rgba(34, 197, 94, 0.12);
      color: #16a34a;
    }

    &.is-todo {
      background: rgba(245, 158, 11, 0.12);
      color: #d97706;
    }
  }
}

/* ============================================================
   预览类型 3：翻译润色（前后对比）
   ============================================================ */
.compare {
  display: flex;
  flex-direction: column;
  gap: 10px;

  &__block {
    padding: 14px 16px;
    border-radius: $r-md;
  }

  &__label {
    font-size: 11px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.6px;
    margin-bottom: 6px;
  }

  &__block p {
    margin: 0;
    font-size: 14px;
    line-height: 1.65;
  }

  &__before {
    background: rgba(148, 163, 184, 0.10);

    .compare__label {
      color: var(--landing-text-muted);
    }

    p {
      color: var(--landing-text-soft);
    }
  }

  &__after {
    background: var(--ai-glow);
    border: 1px solid var(--ai-border-soft);

    .compare__label {
      color: var(--c-ai);
    }

    p {
      color: var(--landing-text);
      font-weight: 500;
    }
  }

  &__arrow {
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--c-ai-light);
  }
}

/* ============================================================
   预览类型 4：技术方案推荐（方案卡片）
   ============================================================ */
.tech-solution {
  padding: 18px;
  border-radius: $r-lg;
  background: linear-gradient(135deg, var(--ai-glow), var(--ai-fill-soft));
  border: 1px solid var(--ai-fill-strong);

  &__name {
    font-family: $font-display;
    font-size: 16px;
    font-weight: 700;
    color: var(--landing-text);
    margin-bottom: 8px;
  }

  &__reason {
    font-size: 13px;
    line-height: 1.7;
    color: var(--landing-text-soft);
    margin: 0 0 14px;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;

    span {
      font-size: 11px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: $r-full;
      background: var(--ai-fill);
      color: var(--c-ai);
    }
  }
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 1024px) {
  .ai-demo {
    padding: 60px 24px;
  }
}

@media (max-width: 640px) {
  .ai-demo {
    padding: 48px 16px;
  }

  .ai-input {
    padding-left: 16px;
  }

  .ai-input__field {
    font-size: 15px;
  }

  .ai-input__ready {
    right: 16px;
    font-size: 11px;
    padding: 4px 10px;
  }

  .ai-chip {
    padding: 9px 16px;
    font-size: 13px;
  }

  .ai-preview {
    padding: 18px 16px;
  }
}
</style>
