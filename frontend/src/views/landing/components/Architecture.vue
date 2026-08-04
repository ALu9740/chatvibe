<script setup lang="ts">
/**
 * Architecture.vue —— 系统架构区
 *
 * 主视觉：仿 VS Code 深色终端，逐行打印一次 @AI 请求的全链路日志
 *（接收 → 检测前缀 → 路由引擎 → 模型推理 → WebSocket 推送 → 响应）。
 * 右侧搭配「实时 AI 路由引擎」说明；底部为 4 层架构概览（次要元素）。
 *
 * 触发机制：使用 IntersectionObserver 监听终端卡片进入视口，
 * 命中后激活 TerminalLog 的打字效果（active = true），仅触发一次。
 */
import { ref, onMounted, onUnmounted } from 'vue'
import TerminalLog from '@/views/landing/components/TerminalLog.vue'

/** 与 TerminalLog.vue 中 LogLine 结构保持一致（该组件未导出接口，这里本地定义以保证类型安全） */
interface LogLine {
  text: string
  type: 'request' | 'detect' | 'route' | 'model' | 'ws' | 'response'
}

/** 4 层架构概览数据 */
interface ArchLayerItem {
  no: string
  name: string
  tech: string
}

/** 终端日志内容：模拟一次 @AI 请求的全链路路由 */
const logLines: LogLine[] = [
  { text: 'REQUEST: message.received (room: product-sprint)', type: 'request' },
  { text: 'DETECTED: @AI prefix', type: 'detect' },
  { text: 'ROUTING: ChatVibe-AI-Engine', type: 'route' },
  { text: 'MODEL: alibaba-bailian-qwen-max', type: 'model' },
  { text: 'WEBSOCKET: push to 3 clients', type: 'ws' },
  { text: 'RESPONSE: 200 OK (latency: 0.82s)', type: 'response' }
]

/** 4 层架构概览：前端 → 网关 → 业务 → 数据 */
const archLayers: ArchLayerItem[] = [
  { no: '01', name: '前端展示层', tech: 'Vue 3 SPA' },
  { no: '02', name: '网关安全层', tech: 'Spring Security + JWT' },
  { no: '03', name: '业务服务层', tech: 'Controller / Service / Mapper' },
  { no: '04', name: '数据基础设施', tech: 'MySQL / Redis / AI' }
]

/** 终端打字效果激活状态（进入视口时激活，离开时重置） */
const isActive = ref(false)
/** 终端卡片引用，供 IntersectionObserver 监听 */
const terminalCardRef = ref<HTMLElement | null>(null)

/** IntersectionObserver 实例引用，便于卸载时断开 */
let observer: IntersectionObserver | null = null

onMounted(() => {
  const el = terminalCardRef.value
  // 降级处理：浏览器不支持或元素缺失时直接激活，保证内容可见
  if (typeof IntersectionObserver === 'undefined' || !el) {
    isActive.value = true
    return
  }

  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          // 进入视口：激活打字效果（TerminalLog 的 watch 会在 true 时 start）
          isActive.value = true
        } else {
          // 离开视口：取消激活（TerminalLog 的 watch 会在 false 时 reset）
          isActive.value = false
        }
      })
    },
    // 当终端卡片有 35% 进入视口时触发
    { threshold: 0.35 }
  )
  observer.observe(el)
})

onUnmounted(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <section class="landing-section architecture" id="architecture">
    <!-- 标题区 -->
    <div class="section-head">
      <div class="section-tag">系统架构</div>
      <h2>前后端分离，<span class="ai-grad-text">AI 实时路由</span></h2>
      <p>基于 Spring Boot 3 + WebSocket 的实时 AI 路由引擎，全链路可视化追踪</p>
    </div>

    <!-- 主视觉：终端卡片 + 右侧说明（左右布局） -->
    <div class="arch-main">
      <!-- 仿 VS Code 深色终端卡片 -->
      <div ref="terminalCardRef" class="arch-terminal">
        <!-- 终端头部：红黄绿圆点 + 文件名 -->
        <div class="terminal-bar">
          <span class="terminal-dots">
            <span class="dot dot-red"></span>
            <span class="dot dot-yellow"></span>
            <span class="dot dot-green"></span>
          </span>
          <span class="terminal-title">ChatVibe-AI-Engine.log</span>
          <span class="terminal-badge">live</span>
        </div>

        <!-- 终端日志主体：逐行打字 -->
        <div class="terminal-body">
          <TerminalLog :lines="logLines" :speed="35" :active="isActive" />
        </div>
      </div>

      <!-- 右侧说明 -->
      <div class="arch-side">
        <div class="arch-side-eyebrow">
          <span class="pulse-dot"></span>
          实时引擎
        </div>
        <h3 class="arch-side-title">实时 AI 路由引擎</h3>
        <p class="arch-side-desc">
          基于 Spring Boot 3.2 + WebSocket 的实时 AI 路由，消息进入引擎后自动识别
          <code class="inline-code">@AI</code> 前缀，路由至大模型推理，并通过 WebSocket 将流式结果推送至所有在线客户端。
        </p>
        <ul class="arch-side-list">
          <li>请求接收与 @AI 前缀检测</li>
          <li>动态路由至多模型推理后端</li>
          <li>WebSocket 全连接实时回推</li>
        </ul>
      </div>
    </div>

    <!-- 4 层架构概览（次要元素，半透明小字号） -->
    <div class="arch-layers">
      <div
        v-for="(layer, idx) in archLayers"
        :key="layer.no"
        class="arch-layer-mini"
      >
        <div class="arch-layer-mini-inner">
          <span class="arch-layer-mini-no">{{ layer.no }}</span>
          <div class="arch-layer-mini-info">
            <div class="arch-layer-mini-name">{{ layer.name }}</div>
            <div class="arch-layer-mini-tech">{{ layer.tech }}</div>
          </div>
        </div>
        <span v-if="idx < archLayers.length - 1" class="arch-layer-mini-arrow" aria-hidden="true">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="9 18 15 12 9 6"></polyline>
          </svg>
        </span>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
/* ============================================================
   区块容器
   ============================================================ */
.architecture {
  position: relative;
  z-index: 1;
}

/* ============================================================
   主视觉：终端 + 说明（左右布局）
   ============================================================ */
.arch-main {
  display: flex;
  align-items: center;
  gap: 48px;
  max-width: 1080px;
  margin: 0 auto;
}

/* ---------- 仿 VS Code 深色终端卡片 ---------- */
.arch-terminal {
  flex: 1 1 0;
  min-width: 0;
  background: var(--term-bg);
  border-radius: $r-lg;
  box-shadow: $shadow-lg, 0 0 0 1px rgba(124, 58, 237, 0.18),
              var(--term-shadow);
  overflow: hidden;
  position: relative;

  /* 终端外圈柔光，营造 AI 引擎「在运行」的氛围 */
  &::before {
    content: '';
    position: absolute;
    inset: -2px;
    border-radius: $r-lg;
    background: linear-gradient(135deg, rgba(124, 58, 237, 0.25), rgba(37, 99, 235, 0.18));
    z-index: -1;
    filter: blur(18px);
    opacity: var(--term-glow-opacity);
  }
}

.terminal-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--term-bar-bg);
  border-bottom: 1px solid var(--term-border);
  user-select: none;
}

.terminal-dots {
  display: inline-flex;
  align-items: center;
  gap: 7px;

  .dot {
    width: 12px;
    height: 12px;
    border-radius: $r-full;
    display: inline-block;

    &-red { background: #ff5f56; }
    &-yellow { background: #ffbd2e; }
    &-green { background: #27c93f; }
  }
}

.terminal-title {
  font-family: $font-mono;
  font-size: 12px;
  color: var(--term-title);
  letter-spacing: 0.3px;
  flex: 1;
  text-align: center;
}

.terminal-badge {
  font-family: $font-mono;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  color: var(--term-badge-color);
  background: var(--term-badge-bg);
  border: 1px solid var(--term-badge-border);
  border-radius: $r-full;
  padding: 2px 8px;

  /* 配合全局 blink 动画，呼吸式 live 标识 */
  animation: blink 1.6s steps(1) infinite;
}

.terminal-body {
  background: var(--term-bg);
  /* 顶部一行扫描光带，强化「引擎运行中」的观感 */
  position: relative;
}

/* ---------- 右侧说明 ---------- */
.arch-side {
  flex: 0 0 320px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.arch-side-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: $font-mono;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.6px;
  text-transform: uppercase;
  color: $c-ai;

  .pulse-dot {
    width: 8px;
    height: 8px;
    border-radius: $r-full;
    background: $c-ai-light;
    box-shadow: 0 0 0 0 rgba(167, 139, 250, 0.55);
    animation: pulse 1.8s $ease-smooth infinite;
  }
}

.arch-side-title {
  font-family: $font-display;
  font-size: 28px;
  font-weight: 700;
  color: var(--landing-text);
  margin: 0;
  line-height: 1.25;
}

.arch-side-desc {
  font-size: 15px;
  line-height: 1.7;
  color: var(--landing-text-soft);
  margin: 0;
}

.inline-code {
  font-family: $font-mono;
  font-size: 13px;
  color: $c-ai-light;
  background: rgba(124, 58, 237, 0.1);
  border: 1px solid rgba(124, 58, 237, 0.2);
  border-radius: $r-sm;
  padding: 1px 6px;
}

.arch-side-list {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;

  li {
    position: relative;
    padding-left: 22px;
    font-size: 14px;
    color: var(--landing-text-soft);
    line-height: 1.6;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 8px;
      width: 12px;
      height: 12px;
      border-radius: $r-sm;
      background: $grad-ai;
    }
  }
}

/* ============================================================
   4 层架构概览（次要元素，半透明小字号）
   ============================================================ */
.arch-layers {
  max-width: 1080px;
  margin: 56px auto 0;
  display: flex;
  align-items: stretch;
  gap: 0;
  flex-wrap: nowrap;
}

.arch-layer-mini {
  display: flex;
  align-items: center;
  flex: 1 1 0;
  min-width: 0;

  .arch-layer-mini-inner {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 16px;
    background: var(--landing-card);
    border: 1px solid var(--landing-border);
    border-radius: $r-md;
    opacity: 0.72;
    transition: all $dur-normal $ease-smooth;
  }

  &:hover .arch-layer-mini-inner {
    opacity: 1;
    transform: translateY(-2px);
    border-color: rgba(124, 58, 237, 0.4);
    box-shadow: $shadow-md;
  }

  .arch-layer-mini-arrow {
    flex: 0 0 auto;
    color: var(--landing-text-muted);
    opacity: 0.5;
    padding: 0 8px;
    display: flex;
    align-items: center;
  }
}

.arch-layer-mini-no {
  flex-shrink: 0;
  font-family: $font-mono;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: $grad-ai;
  border-radius: $r-sm;
  padding: 4px 8px;
  letter-spacing: 0.5px;
}

.arch-layer-mini-info {
  min-width: 0;
}

.arch-layer-mini-name {
  font-family: $font-display;
  font-size: 13px;
  font-weight: 600;
  color: var(--landing-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.arch-layer-mini-tech {
  font-family: $font-mono;
  font-size: 11px;
  color: var(--landing-text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 1024px) {
  .arch-main {
    gap: 32px;
  }
  .arch-side {
    flex: 0 0 280px;
  }
  .arch-layer-mini .arch-layer-mini-inner {
    padding: 12px 12px;
    gap: 10px;
  }
}

@media (max-width: 860px) {
  /* 终端与说明堆叠为上下布局 */
  .arch-main {
    flex-direction: column;
    align-items: stretch;
    gap: 32px;
  }
  .arch-side {
    flex: 0 0 auto;
  }
}

@media (max-width: 640px) {
  .arch-side-title {
    font-size: 24px;
  }
  /* 4 层架构换行为两行 */
  .arch-layers {
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 40px;
  }
  .arch-layer-mini {
    flex: 1 1 calc(50% - 5px);
    min-width: calc(50% - 5px);

    .arch-layer-mini-arrow {
      display: none;
    }
    .arch-layer-mini-inner {
      width: 100%;
    }
  }
}
</style>
