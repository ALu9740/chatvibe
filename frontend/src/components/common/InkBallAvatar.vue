<script setup lang="ts">
/**
 * 墨丝流转球 — 水墨风 AI 头像组件
 *
 * 颜色通过 CSS 变量驱动，自动适配白天/黑夜主题：
 *   --ink-ball-start / --ink-ball-mid / --ink-ball-end  墨球渐变
 *   --ink-thread-mid / --ink-thread-edge                墨丝渐变
 *   --ink-highlight / --ink-dot / --ink-ring            高光/墨点/外环
 *   --ink-glow                                          暗色微光（白天 transparent）
 *
 * 动画：
 *   两组墨丝反向旋转 + 高光脉冲呼吸
 */
// 生成唯一 ID，避免多实例 SVG 引用冲突
const uid = Math.random().toString(36).slice(2, 9)
</script>

<template>
  <svg
    class="ink-ball-svg"
    viewBox="0 0 100 100"
    width="100%"
    height="100%"
    preserveAspectRatio="xMidYMid meet"
  >
    <defs>
      <!-- 墨球底色径向渐变 -->
      <radialGradient :id="`ibg_${uid}`" cx="38%" cy="32%" r="72%">
        <stop offset="0%" style="stop-color: var(--ink-ball-start); stop-opacity: 0.92" />
        <stop offset="55%" style="stop-color: var(--ink-ball-mid)" />
        <stop offset="100%" style="stop-color: var(--ink-ball-end)" />
      </radialGradient>

      <!-- 墨丝线性渐变（两端透明 → 中间高亮） -->
      <linearGradient :id="`ith_${uid}`" x1="0%" y1="0%" x2="100%" y2="0%">
        <stop offset="0%" style="stop-color: var(--ink-thread-edge); stop-opacity: 0" />
        <stop offset="50%" style="stop-color: var(--ink-thread-mid); stop-opacity: 0.85" />
        <stop offset="100%" style="stop-color: var(--ink-thread-edge); stop-opacity: 0" />
      </linearGradient>

      <!-- 墨丝柔化模糊 -->
      <filter :id="`ibl_${uid}`" x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation="0.6" />
      </filter>

      <!-- 暗色微光模糊 -->
      <filter :id="`igl_${uid}`" x="-40%" y="-40%" width="180%" height="180%">
        <feGaussianBlur stdDeviation="4" />
      </filter>
    </defs>

    <!-- 暗色主题微光（白天 --ink-glow = transparent，不可见） -->
    <circle cx="50" cy="50" r="46" style="fill: var(--ink-glow)" :filter="`url(#igl_${uid})`" />

    <!-- 墨球底色 -->
    <circle cx="50" cy="50" r="47" :fill="`url(#ibg_${uid})`" />

    <!-- 墨丝组 1：顺时针旋转 -->
    <g class="ink-threads-1">
      <path
        d="M 12,42 Q 30,26 50,42 T 88,42"
        :stroke="`url(#ith_${uid})`"
        stroke-width="2.5"
        fill="none"
        :filter="`url(#ibl_${uid})`"
      />
      <path
        d="M 15,58 Q 35,74 55,58 T 85,58"
        :stroke="`url(#ith_${uid})`"
        stroke-width="2"
        fill="none"
        opacity="0.6"
        :filter="`url(#ibl_${uid})`"
      />
    </g>

    <!-- 墨丝组 2：逆时针旋转 -->
    <g class="ink-threads-2">
      <path
        d="M 22,33 Q 42,52 26,68"
        :stroke="`url(#ith_${uid})`"
        stroke-width="1.5"
        fill="none"
        opacity="0.5"
        :filter="`url(#ibl_${uid})`"
      />
      <path
        d="M 73,33 Q 58,52 76,68"
        :stroke="`url(#ith_${uid})`"
        stroke-width="1.5"
        fill="none"
        opacity="0.4"
        :filter="`url(#ibl_${uid})`"
      />
    </g>

    <!-- 高光（脉冲呼吸） -->
    <ellipse
      class="ink-highlight"
      cx="37"
      cy="33"
      rx="11"
      ry="7"
      style="fill: var(--ink-highlight)"
      opacity="0.2"
      :filter="`url(#ibl_${uid})`"
    />

    <!-- 墨点纹理 -->
    <circle cx="62" cy="32" r="1.3" style="fill: var(--ink-dot)" opacity="0.5" />
    <circle cx="28" cy="68" r="0.9" style="fill: var(--ink-dot)" opacity="0.4" />
    <circle cx="72" cy="70" r="1.1" style="fill: var(--ink-dot)" opacity="0.45" />
    <circle cx="45" cy="76" r="0.6" style="fill: var(--ink-dot)" opacity="0.3" />

    <!-- 外环 -->
    <circle
      cx="50"
      cy="50"
      r="48"
      fill="none"
      style="stroke: var(--ink-ring)"
      stroke-width="0.5"
      opacity="0.25"
    />
  </svg>
</template>

<style scoped>
.ink-ball-svg {
  display: block;
}

/* 墨丝旋转动画 */
@keyframes inkRotateCW {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
@keyframes inkRotateCCW {
  from { transform: rotate(360deg); }
  to { transform: rotate(0deg); }
}
/* 高光脉冲 */
@keyframes inkPulse {
  0%, 100% { opacity: 0.18; transform: scale(1); }
  50% { opacity: 0.35; transform: scale(1.06); }
}

.ink-threads-1 {
  transform-origin: center;
  animation: inkRotateCW 6s linear infinite;
}
.ink-threads-2 {
  transform-origin: center;
  animation: inkRotateCCW 8s linear infinite;
}
.ink-highlight {
  transform-origin: center;
  animation: inkPulse 4s ease-in-out infinite;
}

@media (prefers-reduced-motion: reduce) {
  .ink-threads-1,
  .ink-threads-2,
  .ink-highlight {
    animation: none;
  }
}
</style>
