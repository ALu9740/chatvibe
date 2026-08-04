<script setup lang="ts">
/**
 * HeroInteractiveBackground · Hero 区交互式背景
 *
 * 三层结构（自下而上）：
 *   1. glowCanvas  — 蓝色液态光斑 + 线条拖尾（头部粗→尾部细），CSS filter: blur() 柔化边缘
 *   2. textCanvas  — 动态字符变异矩阵（A-Z, 0-9），字符在固定位置随机变更
 *   3. veil        — 径向白色渐隐罩，保证中心文案可读性
 *
 * 鼠标在父容器（Hero section）上移动时，光斑以 lerp 平滑跟随，
 * 拖尾呈线条状（头部粗、尾部细），离开时淡出。
 * 字符雨持续运行，不拦截任何指针事件。
 */
import { ref, onMounted, onUnmounted, watch } from 'vue'

// ============================================================
// Props
// ============================================================
const props = withDefaults(defineProps<{
  primaryColor?: string
  coreColor?: string
  trailColor?: string
  blur?: number
  trailLength?: number
  trailWidth?: number
  lerp?: number
  enabled?: boolean
}>(), {
  primaryColor: '#2E7CF6',
  coreColor: '#8FBAFF',
  trailColor: '#8B5CF6',
  blur: 6,
  trailLength: 30,
  trailWidth: 16,
  lerp: 0.12,
  enabled: true
})

// ============================================================
// DOM Refs
// ============================================================
const rootRef = ref<HTMLDivElement | null>(null)
const glowCanvasRef = ref<HTMLCanvasElement | null>(null)
const textCanvasRef = ref<HTMLCanvasElement | null>(null)

// ============================================================
// 内部状态（非响应式，避免 rAF 循环触发 Vue 更新）
// ============================================================
let glowCtx: CanvasRenderingContext2D | null = null
let textCtx: CanvasRenderingContext2D | null = null

/** 宿主元素（父容器 = Hero section / hero__top），用于监听指针事件 */
let hostEl: HTMLElement | null = null

/** devicePixelRatio（上限 2） */
let dpr = 1

/** CSS 像素尺寸 */
let cssW = 0
let cssH = 0

/** 缓存的 getBoundingClientRect（resize/scroll 时更新） */
let rectCache: DOMRect | null = null

// ---- 鼠标 / 动画状态 ----
let targetX = 0.5
let targetY = 0.5
let currentX = 0.5
let currentY = 0.5
let isInside = false
let glowOpacity = 0

/** 拖尾历史点（CSS 像素坐标） */
const trail: { x: number; y: number }[] = []

/** 上次 push 拖尾点的位置（用于判断鼠标是否移动了足够距离） */
let lastPushX = -10000
let lastPushY = -10000

/** rAF 句柄 */
let rafId: number | null = null

/** scroll 节流 rAF */
let scrollRaf: number | null = null

/** prefers-reduced-motion */
let reducedMotion = false

// ---- 字符变异状态 ----
const matrixChars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'

interface MatrixCell {
  char: string       // 当前字符
  alpha: number      // 基础透明度 0.10-0.50
  mutateRate: number // 每帧变异概率
  flash: number      // 变异时短暂高亮（0-1，逐帧衰减）
}

let cells: MatrixCell[] = []
let gridCols = 0
let gridRows = 0

// ---- 观察器 ----
let resizeObserver: ResizeObserver | null = null
let themeObserver: MutationObserver | null = null

// ---- 事件处理函数引用（用于精确 removeEventListener） ----
const fnMove = (e: PointerEvent): void => onPointerMove(e)
const fnEnter = (e: PointerEvent): void => onPointerEnter(e)
const fnLeave = (): void => onPointerLeave()

// ============================================================
// 工具函数
// ============================================================

function hexToRgba(hex: string, alpha: number): string {
  const c = hex.replace('#', '')
  let r: number, g: number, b: number
  if (c.length === 3) {
    r = parseInt(c[0] + c[0], 16)
    g = parseInt(c[1] + c[1], 16)
    b = parseInt(c[2] + c[2], 16)
  } else {
    r = parseInt(c.substring(0, 2), 16)
    g = parseInt(c.substring(2, 4), 16)
    b = parseInt(c.substring(4, 6), 16)
  }
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function isDarkTheme(): boolean {
  return document.documentElement.getAttribute('data-theme') === 'dark'
}

// ============================================================
// 尺寸 / Canvas 初始化
// ============================================================

function updateRect(): void {
  if (!hostEl) return
  rectCache = hostEl.getBoundingClientRect()
  cssW = rectCache.width
  cssH = rectCache.height
}

function setupGlowCanvas(): void {
  const canvas = glowCanvasRef.value
  if (!canvas || cssW === 0 || cssH === 0) return
  canvas.width = Math.round(cssW * dpr)
  canvas.height = Math.round(cssH * dpr)
  canvas.style.width = cssW + 'px'
  canvas.style.height = cssH + 'px'
  glowCtx = canvas.getContext('2d')
  if (glowCtx) {
    glowCtx.scale(dpr, dpr)
  }
}

function setupTextCanvas(): void {
  const canvas = textCanvasRef.value
  if (!canvas || cssW === 0 || cssH === 0) return
  canvas.width = Math.round(cssW * dpr)
  canvas.height = Math.round(cssH * dpr)
  canvas.style.width = cssW + 'px'
  canvas.style.height = cssH + 'px'
  textCtx = canvas.getContext('2d')
  if (textCtx) {
    textCtx.scale(dpr, dpr)
  }
}

// ============================================================
// 字符变异矩阵 —— 固定位置随机变更
// ============================================================

/** 初始化字符网格（每个单元格一个 cell） */
function initCells(): void {
  const cellW = 7
  const cellH = 14
  gridCols = Math.ceil(cssW / cellW)
  gridRows = Math.ceil(cssH / cellH)

  cells = []
  for (let i = 0; i < gridCols * gridRows; i++) {
    cells.push({
      char: matrixChars[Math.floor(Math.random() * matrixChars.length)],
      alpha: 0.1 + Math.random() * 0.22,              // 0.10-0.32
      mutateRate: 0.003 + Math.random() * 0.015,       // 0.3%-1.8%/帧
      flash: 0
    })
  }
  // 约 6% 的 cell alpha=0.5 增加层次
  const highlightCount = Math.floor(cells.length * 0.06)
  for (let i = 0; i < highlightCount; i++) {
    const idx = Math.floor(Math.random() * cells.length)
    cells[idx].alpha = 0.5
  }
}

/**
 * 绘制一帧字符变异矩阵
 * 每帧全量重绘：部分 cell 随机变更字符，变更时短暂高亮闪烁
 */
function drawTextMutation(): void {
  if (!textCtx || cssW === 0 || cssH === 0 || cells.length === 0) return

  const cellW = 7
  const cellH = 14
  const fontSize = 10
  const dark = isDarkTheme()
  const baseColor = dark ? '148, 163, 184' : '71, 85, 105'
  // 变异高亮色：浅色主题用品牌蓝，暗色主题用浅蓝
  const flashColor = dark ? '129, 140, 248' : '46, 124, 246'

  textCtx.clearRect(0, 0, cssW, cssH)
  textCtx.font = `${fontSize}px ui-monospace, SFMono-Regular, Consolas, monospace`
  textCtx.textAlign = 'center'
  textCtx.textBaseline = 'middle'

  for (let row = 0; row < gridRows; row++) {
    for (let col = 0; col < gridCols; col++) {
      const idx = row * gridCols + col
      const cell = cells[idx]

      // 随机变异字符
      if (Math.random() < cell.mutateRate) {
        cell.char = matrixChars[Math.floor(Math.random() * matrixChars.length)]
        cell.flash = 1
      }

      // flash 衰减
      if (cell.flash > 0) {
        cell.flash *= 0.88
        if (cell.flash < 0.01) cell.flash = 0
      }

      // 渲染透明度：基础 alpha 与 flash 叠加
      const renderAlpha = cell.alpha + cell.flash * (0.6 - cell.alpha)
      const color = cell.flash > 0.1 ? flashColor : baseColor

      textCtx.fillStyle = `rgba(${color}, ${renderAlpha})`
      textCtx.fillText(
        cell.char,
        col * cellW + cellW / 2,
        row * cellH + cellH / 2
      )
    }
  }
}

/** 静态字符矩阵（reduced-motion 模式使用） */
function drawTextMatrix(): void {
  if (!textCtx || cssW === 0 || cssH === 0) return

  const cellW = 7
  const cellH = 14
  const fontSize = 10
  const cols = Math.ceil(cssW / cellW)
  const rows = Math.ceil(cssH / cellH)
  const dark = isDarkTheme()
  const baseColor = dark ? '148, 163, 184' : '71, 85, 105'

  textCtx.clearRect(0, 0, cssW, cssH)
  textCtx.font = `${fontSize}px ui-monospace, SFMono-Regular, Consolas, monospace`
  textCtx.textAlign = 'center'
  textCtx.textBaseline = 'middle'

  for (let row = 0; row < rows; row++) {
    for (let col = 0; col < cols; col++) {
      const char = matrixChars[Math.floor(Math.random() * matrixChars.length)]
      const alpha = Math.random() < 0.06
        ? 0.5
        : 0.1 + Math.random() * 0.22

      textCtx.fillStyle = `rgba(${baseColor}, ${alpha})`
      textCtx.fillText(
        char,
        col * cellW + cellW / 2,
        row * cellH + cellH / 2
      )
    }
  }
}

// ============================================================
// 线条拖尾绘制（替代原来的圆形光斑）
// ============================================================

function drawTrail(): void {
  if (!glowCtx || cssW === 0 || cssH === 0) return

  glowCtx.clearRect(0, 0, cssW, cssH)
  if (glowOpacity <= 0.001) return

  const len = trail.length
  if (len === 0) return

  glowCtx.lineCap = 'round'
  glowCtx.lineJoin = 'round'

  if (len >= 2) {
    // 从尾部到头部逐段画线，宽度由细到粗
    for (let i = 0; i < len - 1; i++) {
      const t = len > 1 ? i / (len - 1) : 1  // 0（尾）→ 1（头）

      // 线宽：尾部 1px → 头部 trailWidth，用幂函数让头部更粗
      const w = 1 + Math.pow(t, 1.4) * (props.trailWidth - 1)
      // 透明度：0 → 0.9
      const a = t * 0.9 * glowOpacity

      // 颜色：尾部紫色 → 中段品牌蓝 → 头部浅蓝
      let color: string
      if (t > 0.75) {
        color = hexToRgba(props.coreColor, a)
      } else if (t > 0.4) {
        color = hexToRgba(props.primaryColor, a * 0.85)
      } else {
        color = hexToRgba(props.trailColor, a * 0.6)
      }

      glowCtx.beginPath()
      glowCtx.moveTo(trail[i].x, trail[i].y)
      glowCtx.lineTo(trail[i + 1].x, trail[i + 1].y)
      glowCtx.lineWidth = w
      glowCtx.strokeStyle = color
      glowCtx.stroke()
    }
  }

  // 头部光斑（径向渐变圆，提供液态感）
  const head = trail[len - 1]
  const headR = props.trailWidth * 1.8
  const headGrad = glowCtx.createRadialGradient(head.x, head.y, 0, head.x, head.y, headR)
  headGrad.addColorStop(0, hexToRgba(props.coreColor, 0.85 * glowOpacity))
  headGrad.addColorStop(0.4, hexToRgba(props.primaryColor, 0.35 * glowOpacity))
  headGrad.addColorStop(1, hexToRgba(props.primaryColor, 0))
  glowCtx.beginPath()
  glowCtx.arc(head.x, head.y, headR, 0, Math.PI * 2)
  glowCtx.fillStyle = headGrad
  glowCtx.fill()
}

/** reduced-motion 模式：绘制居中静态弱光斑 */
function drawStaticGlow(): void {
  if (!glowCtx || cssW === 0 || cssH === 0) return

  glowCtx.clearRect(0, 0, cssW, cssH)

  const cx = cssW / 2
  const cy = cssH * 0.45
  const radius = 90

  const grad = glowCtx.createRadialGradient(cx, cy, 0, cx, cy, radius)
  grad.addColorStop(0, hexToRgba(props.coreColor, 0.15))
  grad.addColorStop(1, hexToRgba(props.primaryColor, 0.05))

  glowCtx.beginPath()
  glowCtx.arc(cx, cy, radius, 0, Math.PI * 2)
  glowCtx.fillStyle = grad
  glowCtx.fill()
}

// ============================================================
// 动画循环（字符雨 + 光斑拖尾）
// ============================================================

function animate(): void {
  // ---- 字符变异（始终运行） ----
  if (!reducedMotion && props.enabled) {
    drawTextMutation()
  }

  // ---- 光斑位置 lerp ----
  currentX += (targetX - currentX) * props.lerp
  currentY += (targetY - currentY) * props.lerp

  // ---- 整体透明度缓动 ----
  const targetOpacity = isInside ? 1 : 0
  glowOpacity += (targetOpacity - glowOpacity) * 0.08
  if (glowOpacity < 0.001) glowOpacity = 0
  if (glowOpacity > 0.999) glowOpacity = 1

  // ---- 拖尾更新 ----
  if (isInside) {
    const cx = currentX * cssW
    const cy = currentY * cssH
    const dx = cx - lastPushX
    const dy = cy - lastPushY

    // 鼠标移动距离 > 1px 时才 push 新拖尾点（避免静止时无限堆积）
    if (dx * dx + dy * dy > 1) {
      trail.push({ x: cx, y: cy })
      if (trail.length > props.trailLength) {
        trail.shift()
      }
      lastPushX = cx
      lastPushY = cy
    }
  } else if (trail.length > 0) {
    // 鼠标离开：逐帧收缩拖尾
    trail.shift()
  }

  // ---- 绘制拖尾 ----
  drawTrail()

  // 字符雨需要持续运行，所以 rAF 永不停止（除非 enabled=false 或 unmount）
  if (props.enabled) {
    rafId = requestAnimationFrame(animate)
  } else {
    rafId = null
  }
}

// ============================================================
// 事件处理
// ============================================================

function onPointerMove(e: PointerEvent): void {
  if (!rectCache) return
  targetX = (e.clientX - rectCache.left) / rectCache.width
  targetY = (e.clientY - rectCache.top) / rectCache.height
  targetX = Math.max(0, Math.min(1, targetX))
  targetY = Math.max(0, Math.min(1, targetY))
}

function onPointerEnter(e: PointerEvent): void {
  isInside = true
  if (rectCache) {
    targetX = (e.clientX - rectCache.left) / rectCache.width
    targetY = (e.clientY - rectCache.top) / rectCache.height
    targetX = Math.max(0, Math.min(1, targetX))
    targetY = Math.max(0, Math.min(1, targetY))
    // 进入时直接吸附到鼠标位置（避免从远处划过的拖影）
    currentX = targetX
    currentY = targetY
  }
  // 重置 lastPush 以确保第一帧就 push 拖尾点
  lastPushX = -10000
  lastPushY = -10000
}

function onPointerLeave(): void {
  isInside = false
}

function onScroll(): void {
  if (scrollRaf !== null) return
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = null
    updateRect()
  })
}

function onResize(): void {
  updateRect()
  setupGlowCanvas()
  setupTextCanvas()
  initCells()
  trail.length = 0
  lastPushX = -10000
  lastPushY = -10000
  if (reducedMotion && props.enabled) {
    drawTextMatrix()
    drawStaticGlow()
  }
}

function onThemeChange(): void {
  // 字符变异每帧读取 isDarkTheme()，自动适配
  // reduced-motion 静态模式需要手动重绘
  if (reducedMotion) {
    drawTextMatrix()
  }
}

// ============================================================
// 监听器注册 / 注销
// ============================================================

function attachListeners(): void {
  if (!hostEl) return
  hostEl.addEventListener('pointermove', fnMove, { passive: true })
  hostEl.addEventListener('pointerenter', fnEnter)
  hostEl.addEventListener('pointerleave', fnLeave)
  window.addEventListener('scroll', onScroll, { passive: true, capture: true })
}

function detachListeners(): void {
  if (hostEl) {
    hostEl.removeEventListener('pointermove', fnMove)
    hostEl.removeEventListener('pointerenter', fnEnter)
    hostEl.removeEventListener('pointerleave', fnLeave)
  }
  window.removeEventListener('scroll', onScroll, { capture: true } as EventListenerOptions)
}

// ============================================================
// 初始化
// ============================================================

function init(): void {
  const root = rootRef.value
  if (!root) return

  hostEl = root.parentElement
  if (!hostEl) return

  reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  dpr = Math.min(window.devicePixelRatio || 1, 2)

  updateRect()
  setupGlowCanvas()
  setupTextCanvas()

  // 初始化字符变异网格
  initCells()

  // 注册监听
  attachListeners()

  // ResizeObserver
  resizeObserver = new ResizeObserver(() => onResize())
  resizeObserver.observe(hostEl)

  // MutationObserver：监听主题切换
  themeObserver = new MutationObserver(() => onThemeChange())
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  })

  if (reducedMotion && props.enabled) {
    // 无障碍模式：静态矩阵 + 静态光斑
    drawTextMatrix()
    drawStaticGlow()
  } else if (props.enabled) {
    // 正常模式：启动 rAF（字符雨 + 光斑拖尾）
    rafId = requestAnimationFrame(animate)
  }
}

// ============================================================
// 生命周期
// ============================================================

onMounted(() => {
  init()
})

onUnmounted(() => {
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
  if (scrollRaf !== null) {
    cancelAnimationFrame(scrollRaf)
    scrollRaf = null
  }
  detachListeners()
  resizeObserver?.disconnect()
  resizeObserver = null
  themeObserver?.disconnect()
  themeObserver = null
  trail.length = 0
  cells = []
  glowCtx = null
  textCtx = null
  hostEl = null
  rectCache = null
})

// ============================================================
// Watchers
// ============================================================

watch(() => props.enabled, (val) => {
  if (val) {
    if (reducedMotion) {
      drawTextMatrix()
      drawStaticGlow()
    } else {
      if (cells.length === 0) initCells()
      if (rafId === null) {
        rafId = requestAnimationFrame(animate)
      }
    }
  } else {
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
    if (glowCtx) glowCtx.clearRect(0, 0, cssW, cssH)
    trail.length = 0
    glowOpacity = 0
    isInside = false
  }
})
</script>

<template>
  <div ref="rootRef" class="hero-interactive-bg">
    <!-- 1. 光斑与线条拖尾（底层） -->
    <canvas ref="glowCanvasRef" class="hero-interactive-bg__glow"></canvas>
    <!-- 2. 字符雨矩阵（中层） -->
    <canvas ref="textCanvasRef" class="hero-interactive-bg__text"></canvas>
    <!-- 3. 中心可读性遮罩（顶层） -->
    <div class="hero-interactive-bg__veil"></div>
  </div>
</template>

<style scoped lang="scss">
.hero-interactive-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
}

.hero-interactive-bg__glow {
  position: absolute;
  inset: 0;
  filter: blur(v-bind('blur + "px"'));
}

.hero-interactive-bg__text {
  position: absolute;
  inset: 0;
}

.hero-interactive-bg__veil {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    ellipse 62% 55% at 50% 45%,
    rgba(246, 248, 251, 0.92),
    rgba(246, 248, 251, 0.45) 45%,
    transparent 78%
  );
}

/* 暗色模式：遮罩改为深色 */
[data-theme='dark'] {
  .hero-interactive-bg__veil {
    background: radial-gradient(
      ellipse 62% 55% at 50% 45%,
      rgba(15, 23, 42, 0.92),
      rgba(15, 23, 42, 0.45) 45%,
      transparent 78%
    );
  }
}
</style>
