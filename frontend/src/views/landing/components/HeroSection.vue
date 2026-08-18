<script setup lang="ts">
/**
 * HeroSection · 官网首页主视觉区
 *
 * 上下两栏布局：上侧文案 + CTA + 技术指标，下侧 macOS 风格聊天窗口模拟器。
 * 聊天窗口自动循环播放预设对话脚本，鼠标悬停时暂停。
 */
import { ref, nextTick, watch, onMounted, onUnmounted } from 'vue'
import ChatMessage, { type ChatMsg } from '@/views/landing/components/ChatMessage.vue'
import HeroInteractiveBackground from '@/components/HeroInteractiveBackground.vue'

// ============================================================
// 事件定义
// ============================================================

/** CTA 按钮动作类型 */
type CtaAction = 'start' | 'demo'

/** 暴露事件：按钮点击时触发 */
const emit = defineEmits<{
  (e: 'cta', action: CtaAction): void
}>()

// ============================================================
// 聊天模拟器状态
// ============================================================

/** 消息列表（响应式） */
const messages = ref<ChatMsg[]>([])

/** 是否正在扫描（AI 思考中的高亮光线） */
const isScanning = ref(false)

/** 是否暂停播放（鼠标悬停时为 true） */
const isPaused = ref(false)

/** 消息容器 DOM 引用，用于自动滚动到底部 */
const msgContainerRef = ref<HTMLElement | null>(null)

/** 消息自增 ID（保证 TransitionGroup key 唯一） */
let msgIdCounter = 0
function nextId(): number {
  return ++msgIdCounter
}

// ============================================================
// 对话脚本 —— 按顺序逐步执行
// ============================================================

/** 脚本单步：delay 为相对上一步的延迟（毫秒） */
interface ScriptStep {
  delay: number
  action: () => void
}

/** 预设对话脚本 */
const script: ScriptStep[] = [
  // 步骤 1：成员 A 发言，@AI 召唤
  {
    delay: 300,
    action: () => {
      messages.value.push({
        id: nextId(),
        sender: '李明',
        avatar: '李',
        content: '@AI 把刚才讨论的 Q3 目标整理成待办，发给新加入的 @张三',
        timestamp: '14:32'
      })
    }
  },
  // 步骤 2：输入框顶部出现扫描光线（AI 正在思考）
  {
    delay: 700,
    action: () => {
      isScanning.value = true
    }
  },
  // 步骤 3：约 0.8 秒后，AI 返回结构化待办清单
  {
    delay: 800,
    action: () => {
      isScanning.value = false
      messages.value.push({
        id: nextId(),
        sender: 'AI 助手',
        avatar: '',
        isAI: true,
        isTodoList: true,
        content:
          '已为你整理 Q3 待办清单\n完成用户认证模块开发\n优化 WebSocket 消息推送性能\n集成 AI 流式回复功能\n@张三 已为你生成个人看板',
        timestamp: '14:32'
      })
    }
  },
  // 步骤 4：张三回复
  {
    delay: 1200,
    action: () => {
      messages.value.push({
        id: nextId(),
        sender: '张三',
        avatar: '张',
        content: '收到，神速！',
        timestamp: '14:33'
      })
    }
  },
  // 步骤 5：停留 3 秒后清空消息，进入下一轮循环
  {
    delay: 3000,
    action: () => {
      messages.value = []
      isScanning.value = false
    }
  }
]

// ============================================================
// 播放控制
// ============================================================

/** 当前计时器句柄 */
let currentTimer: ReturnType<typeof setTimeout> | null = null

/** 当前脚本步骤索引 */
let stepIndex = 0

/** 调度下一步脚本 */
function scheduleNext(): void {
  if (isPaused.value) return

  const step = script[stepIndex]
  currentTimer = setTimeout(() => {
    step.action()
    // 索引循环：到末尾后回到 0，实现无限播放
    stepIndex = (stepIndex + 1) % script.length
    scheduleNext()
  }, step.delay)
}

/** 暂停播放（鼠标进入聊天窗口时调用） */
function pausePlayback(): void {
  isPaused.value = true
  if (currentTimer) {
    clearTimeout(currentTimer)
    currentTimer = null
  }
}

/** 恢复播放（鼠标离开聊天窗口时调用） */
function resumePlayback(): void {
  isPaused.value = false
  scheduleNext()
}

// ============================================================
// 自动滚动 —— 新消息出现时滚动到底部
// ============================================================

watch(
  () => messages.value.length,
  () => {
    nextTick(() => {
      const container = msgContainerRef.value
      if (container) {
        container.scrollTo({
          top: container.scrollHeight,
          behavior: 'smooth'
        })
      }
    })
  }
)

// ============================================================
// CTA 按钮点击
// ============================================================

function onCta(action: CtaAction): void {
  emit('cta', action)
}

// ============================================================
// 生命周期
// ============================================================

onMounted(() => {
  // 组件挂载后启动自动播放
  scheduleNext()
})

onUnmounted(() => {
  // 清理定时器，防止内存泄漏
  if (currentTimer) {
    clearTimeout(currentTimer)
    currentTimer = null
  }
})
</script>

<template>
  <section class="hero">
    <div class="hero__inner">
      <!-- ==================== 上侧文案区 ==================== -->
      <div class="hero__top">
        <!-- 交互式背景：字符矩阵 + 液态光斑拖尾 -->
        <HeroInteractiveBackground />

        <!-- 内容层（在背景之上） -->
        <div class="hero__top-content">
        <!-- 版本徽章 -->
        <div class="version-badge">
          <svg
            class="version-badge__star"
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
          </svg>
          <span>v1.0 已发布</span>
        </div>

        <!-- 大标题 -->
        <h1 class="hero__title">
          <span class="grad-text">有温度的沟通</span>
          <span class="ai-grad-text">智能即刻响应</span>
        </h1>

        <!-- 副标题 -->
        <p class="hero__subtitle">
          ChatVibe 融合实时通讯与 AI 智能对话，让每一次交流都有温度、有智能、有即刻响应。私聊、群聊、@AI 召唤，一个界面完成所有协作。
        </p>

        <!-- CTA 按钮组 -->
        <div class="hero__cta">
          <button type="button" class="btn btn-primary" @click="onCta('start')">
            快速开始
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <line x1="5" y1="12" x2="19" y2="12"></line>
              <polyline points="12 5 19 12 12 19"></polyline>
            </svg>
          </button>
          <button type="button" class="btn btn-secondary" @click="onCta('demo')">
            在线体验
          </button>
        </div>

        <!-- 技术指标行 -->
        <div class="hero__metrics">
          <div class="metric">
            <div class="metric__icon metric__icon--blue">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polyline>
              </svg>
            </div>
            <div class="metric__text">
              <div class="metric__value">实时推送</div>
              <div class="metric__label">WebSocket</div>
            </div>
          </div>

          <div class="metric__sep"></div>

          <div class="metric">
            <div class="metric__icon metric__icon--ai">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="4" y="4" width="16" height="16" rx="2"></rect>
                <rect x="9" y="9" width="6" height="6"></rect>
                <line x1="9" y1="1" x2="9" y2="4"></line>
                <line x1="15" y1="1" x2="15" y2="4"></line>
                <line x1="9" y1="20" x2="9" y2="23"></line>
                <line x1="15" y1="20" x2="15" y2="23"></line>
                <line x1="20" y1="9" x2="23" y2="9"></line>
                <line x1="20" y1="14" x2="23" y2="14"></line>
                <line x1="1" y1="9" x2="4" y2="9"></line>
                <line x1="1" y1="14" x2="4" y2="14"></line>
              </svg>
            </div>
            <div class="metric__text">
              <div class="metric__value">毫秒级</div>
              <div class="metric__label">AI 响应</div>
            </div>
          </div>

          <div class="metric__sep"></div>

          <div class="metric">
            <div class="metric__icon metric__icon--green">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
              </svg>
            </div>
            <div class="metric__text">
              <div class="metric__value">E2E</div>
              <div class="metric__label">端到端加密</div>
            </div>
          </div>

          <div class="metric__sep"></div>

          <div class="metric">
            <div class="metric__icon metric__icon--amber">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline>
              </svg>
            </div>
            <div class="metric__text">
              <div class="metric__value">99.99%</div>
              <div class="metric__label">在线可用</div>
            </div>
          </div>
        </div>

        </div><!-- /.hero__top-content -->
      </div>

      <!-- ==================== 下侧聊天窗口模拟器 ==================== -->
      <div class="hero__bottom">
        <!-- 装饰光晕（置于窗口后方） -->
        <div class="chat-glow chat-glow--ai"></div>
        <div class="chat-glow chat-glow--blue"></div>

        <!-- macOS 风格窗口 -->
        <div
          class="chat-window"
          @mouseenter="pausePlayback"
          @mouseleave="resumePlayback"
        >
          <!-- 窗口顶栏 -->
          <div class="chat-window__bar">
            <div class="traffic-lights">
              <span class="traffic-light traffic-light--red"></span>
              <span class="traffic-light traffic-light--yellow"></span>
              <span class="traffic-light traffic-light--green"></span>
            </div>
            <div class="chat-window__tab">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="9" cy="7" r="4"></circle>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
              </svg>
              <span>产品冲刺群</span>
              <span class="chat-window__count">5</span>
            </div>
            <div class="chat-window__status">
              <span class="status-dot"></span>
              在线
            </div>
          </div>

          <!-- 消息列表区 -->
          <div ref="msgContainerRef" class="chat-window__body">
            <TransitionGroup name="hero-msg" tag="div" class="msg-list">
              <ChatMessage
                v-for="msg in messages"
                :key="msg.id"
                :msg="msg"
                align="left"
              />
            </TransitionGroup>
          </div>

          <!-- 输入框区 -->
          <div class="chat-window__input" :class="{ 'is-scanning': isScanning }">
            <!-- 扫描光线（AI 思考中） -->
            <div v-if="isScanning" class="scan-line"></div>
            <div class="input-bar">
              <div class="input-bar__left">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
                </svg>
                <span class="input-bar__placeholder">
                  {{ isScanning ? 'AI 正在思考...' : '输入消息，@AI 召唤智能助手...' }}
                </span>
              </div>
              <div class="input-bar__hint">@</div>
            </div>
          </div>

          <!-- 悬停暂停提示 -->
          <Transition name="fade">
            <div v-if="isPaused" class="pause-hint">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                <rect x="6" y="4" width="4" height="16"></rect>
                <rect x="14" y="4" width="4" height="16"></rect>
              </svg>
              已暂停
            </div>
          </Transition>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
// ============================================================
// Hero 主容器
// ============================================================
.hero {
  width: 100%;
  max-width: $landing-max-w;
  margin: 0 auto;
  padding: 40px 48px 80px;

  &__inner {
    display: flex;
    flex-direction: column;
    gap: 48px;
    align-items: center;
  }
}

// ============================================================
// 上侧文案区
// ============================================================
.hero__top {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 24px;
  width: 100%;
  border-radius: $r-xl;
  overflow: hidden;
  isolation: isolate;
}

// ---- 内容层 ----
.hero__top-content {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 24px;
  padding: 48px 32px;
  width: 100%;
}

// ---- 版本徽章 ----
.version-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: $r-full;
  background: var(--ai-fill);
  border: 1px solid var(--ai-border-soft);
  color: var(--c-ai);
  font-size: 13px;
  font-weight: 600;
  font-family: $font-body;
  width: fit-content;

  &__star {
    color: var(--c-ai-light);
    animation: starBlink 2s ease-in-out infinite;
  }
}

@keyframes starBlink {
  0%,
  100% {
    opacity: 1;
    transform: scale(1) rotate(0deg);
  }
  50% {
    opacity: 0.5;
    transform: scale(0.8) rotate(20deg);
  }
}

// ---- 大标题 ----
.hero__title {
  font-family: $font-display;
  font-size: 64px;
  font-weight: 800;
  line-height: 1.15;
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  letter-spacing: -0.02em;

  .grad-text,
  .ai-grad-text {
    display: inline-block;
  }
}

// ---- 副标题 ----
.hero__subtitle {
  font-size: 18px;
  line-height: 1.6;
  color: var(--landing-text-soft);
  margin: 0;
  max-width: 640px;
}

// ---- CTA 按钮组 ----
.hero__cta {
  display: flex;
  gap: 16px;
  margin-top: 4px;
}

// ---- 技术指标行 ----
.hero__metrics {
  display: flex;
  align-items: center;
  margin-top: 8px;

  .metric {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0 16px;

    &:first-child {
      padding-left: 0;
    }

    &__icon {
      width: 32px;
      height: 32px;
      border-radius: $r-sm;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      &--blue {
        background: rgba(37, 99, 235, 0.1);
        color: $c-primary;
      }

      &--ai {
        background: var(--ai-fill);
        color: var(--c-ai);
      }

      &--green {
        background: rgba(34, 197, 94, 0.1);
        color: $c-online;
      }

      &--amber {
        background: rgba(245, 158, 11, 0.1);
        color: $c-warning;
      }
    }

    &__text {
      display: flex;
      flex-direction: column;
      gap: 1px;
    }

    &__value {
      font-size: 14px;
      font-weight: 700;
      color: var(--landing-text);
      line-height: 1.2;
    }

    &__label {
      font-size: 12px;
      color: var(--landing-text-muted);
      line-height: 1.2;
    }
  }

  .metric__sep {
    width: 1px;
    height: 28px;
    background: var(--landing-border);
    flex-shrink: 0;
  }
}

// ============================================================
// 下侧聊天窗口模拟器
// ============================================================
.hero__bottom {
  position: relative;
  width: 100%;
  max-width: 720px;
}

// ---- 装饰光晕 ----
.chat-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  z-index: 0;
  pointer-events: none;

  &--ai {
    width: 300px;
    height: 300px;
    background: var(--ai-glow-strong);
    top: -40px;
    right: -40px;
  }

  &--blue {
    width: 260px;
    height: 260px;
    background: rgba(37, 99, 235, 0.14);
    bottom: -40px;
    left: -20px;
  }
}

// ---- macOS 风格窗口 ----
.chat-window {
  position: relative;
  z-index: 2;
  background: var(--landing-card);
  border-radius: $r-xl;
  border: 1px solid var(--landing-border);
  box-shadow: $shadow-lg;
  overflow: hidden;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  // 入场 + 持续微浮动动画
  animation: chatFloatIn 0.8s $ease-out-expo both,
    chatFloatY 6s ease-in-out 0.8s infinite;

  // ---- 窗口顶栏 ----
  &__bar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    background: var(--landing-bar-bg, rgba(241, 245, 249, 0.6));
    border-bottom: 1px solid var(--landing-border);
  }

  .traffic-lights {
    display: flex;
    gap: 6px;
    flex-shrink: 0;
  }

  .traffic-light {
    width: 12px;
    height: 12px;
    border-radius: $r-full;
    transition: opacity $dur-fast $ease-smooth;

    &--red {
      background: #ff5f57;
    }
    &--yellow {
      background: #febc2e;
    }
    &--green {
      background: #28c840;
    }
  }

  &__tab {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    justify-content: center;
    font-size: 13px;
    font-weight: 600;
    color: var(--landing-text);
  }

  &__count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: $r-full;
    background: var(--ai-fill);
    color: var(--c-ai);
    font-size: 11px;
    font-weight: 700;
  }

  &__status {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    font-weight: 600;
    color: $c-online;

    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: $r-full;
      background: $c-online;
      animation: pulse 2s ease-in-out infinite;
    }
  }

  // ---- 消息列表区 ----
  &__body {
    height: 380px;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 18px 16px;
    scroll-behavior: smooth;

    // 自定义滚动条
    &::-webkit-scrollbar {
      width: 4px;
    }
    &::-webkit-scrollbar-track {
      background: transparent;
    }
    &::-webkit-scrollbar-thumb {
      background: var(--landing-border);
      border-radius: $r-full;
    }
  }

  // ---- 输入框区 ----
  &__input {
    position: relative;
    padding: 12px 16px;
    border-top: 1px solid var(--landing-border);
    background: var(--landing-card);
    transition: background $dur-normal $ease-smooth;

    // 扫描中：输入区微调背景
    &.is-scanning {
      background: var(--ai-fill-soft);

      .input-bar {
        border-color: var(--ai-border);
      }
    }
  }

  // ---- 扫描光线 ----
  .scan-line {
    position: absolute;
    top: 0;
    left: 0;
    width: 30%;
    height: 2px;
    background: linear-gradient(
      90deg,
      transparent,
      var(--c-ai-light),
      var(--c-ai),
      var(--c-ai-light),
      transparent
    );
    animation: scanLine 1.2s $ease-smooth infinite;
    box-shadow: 0 0 12px var(--ai-scan-glow);
    z-index: 1;
  }

  .input-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 14px;
    border-radius: $r-full;
    background: var(--landing-bg);
    border: 1px solid var(--landing-border);
    color: var(--landing-text-muted);
    transition: border-color $dur-normal $ease-smooth;

    &__left {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;
    }

    &__placeholder {
      font-size: 13px;
    }

    &__hint {
      font-size: 14px;
      font-weight: 700;
      color: var(--c-ai);
      padding: 2px 8px;
      border-radius: $r-sm;
      background: var(--ai-fill);
    }
  }

  // ---- 悬停暂停提示 ----
  .pause-hint {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    border-radius: $r-full;
    background: rgba(15, 23, 42, 0.75);
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    z-index: 10;
    pointer-events: none;
  }
}

// ============================================================
// 消息列表容器
// ============================================================
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// ============================================================
// AI 消息渐变边框增强
// ChatMessage 组件已自带 box-shadow: $shadow-glow-ai，
// 此处通过 :deep() 追加渐变边框效果
// ============================================================
:deep(.chat-msg.is-ai .chat-msg__bubble) {
  border: 1.5px solid transparent;
  background:
    linear-gradient(135deg, var(--ai-bubble-bg), var(--ai-bubble-bg-2))
      padding-box,
    var(--grad-ai) border-box;
}

// ============================================================
// TransitionGroup 动画 —— 弹性进入
// ============================================================
.hero-msg-enter-active {
  transition: all 0.5s $ease-spring;
}

.hero-msg-leave-active {
  transition: all 0.3s $ease-smooth;
}

.hero-msg-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

.hero-msg-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

// 暂停提示淡入淡出
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s $ease-smooth;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

// ============================================================
// 关键帧动画
// ============================================================
@keyframes chatFloatIn {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes chatFloatY {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

// ============================================================
// 响应式
// ============================================================
@media (max-width: 1024px) {
  .hero {
    padding: 24px 24px 60px;

    &__inner {
      gap: 40px;
    }
  }

  .hero__title {
    font-size: 44px;
  }

  .chat-window__body {
    height: 340px;
  }
}

@media (max-width: 640px) {
  .hero {
    padding: 16px 16px 48px;
  }

  .hero__title {
    font-size: 32px;
  }

  .hero__subtitle {
    font-size: 15px;
  }

  .hero__cta {
    flex-direction: column;
    width: 100%;

    .btn {
      justify-content: center;
    }
  }

  .hero__metrics {
    flex-wrap: wrap;
    gap: 12px 0;

    .metric__sep {
      display: none;
    }

    .metric {
      padding: 0 12px;
    }
  }

  .chat-window__body {
    height: 300px;
  }
}

// ============================================================
// 暗色模式覆盖
// ============================================================
[data-theme='dark'] {
  .chat-window {
    &__bar {
      --landing-bar-bg: rgba(30, 41, 59, 0.5);
    }

    .traffic-light {
      opacity: 0.85;
    }

    .pause-hint {
      background: rgba(15, 23, 42, 0.88);
    }
  }

  .version-badge {
    background: var(--ai-fill-strong);
    border-color: var(--ai-border);
  }
}
</style>
