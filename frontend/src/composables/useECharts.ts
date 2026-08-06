// ============================================================
// ChatVibe · ECharts 通用 composable
// 负责图表实例的创建、主题适配、resize 监听与销毁
// ============================================================
import { ref, onMounted, onBeforeUnmount, watch, type Ref } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// 按需注册所需组件
echarts.use([
  LineChart,
  BarChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
  CanvasRenderer
])

export type { ECharts } from 'echarts/core'

/** 从 CSS 变量读取颜色值 */
function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

/** 构建与项目设计令牌一致的 ECharts 主题色 */
export function getChartColors() {
  return {
    primary: cssVar('--el-color-primary') || '#2563EB',
    primaryLight: '#3B82F6',
    sky: '#0EA5E9',
    ai: '#7C3AED',
    success: '#10B981',
    warning: '#F59E0B',
    danger: '#EF4444',
    text: cssVar('--el-text-color-primary') || '#0F172A',
    textMuted: cssVar('--el-text-color-secondary') || '#94A3B8',
    border: cssVar('--el-border-color') || '#E2E8F0',
    bg: cssVar('--el-bg-color') || '#FFFFFF',
    bgOverlay: cssVar('--el-bg-color-overlay') || '#FFFFFF'
  }
}

/**
 * ECharts composable
 * @param elRef 图表容器 DOM 引用
 * @param optionFn 返回 ECharts option 的函数（响应式）
 */
export function useECharts(
  elRef: Ref<HTMLElement | null>,
  optionFn: () => Record<string, unknown>
) {
  const chart = ref<echarts.ECharts | null>(null)
  let resizeObserver: ResizeObserver | null = null

  function initChart() {
    if (!elRef.value) return
    chart.value = echarts.init(elRef.value, undefined, { renderer: 'canvas' })
    chart.value.setOption(optionFn())

    // 容器尺寸变化时自动 resize
    resizeObserver = new ResizeObserver(() => {
      chart.value?.resize()
    })
    resizeObserver.observe(elRef.value)
  }

  function updateChart() {
    chart.value?.setOption(optionFn(), { notMerge: false })
  }

  /** 重建图表（用于暗色/亮色主题切换后刷新颜色） */
  function rebuildChart() {
    chart.value?.dispose()
    initChart()
  }

  onMounted(() => {
    initChart()
  })

  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    chart.value?.dispose()
    chart.value = null
  })

  // option 变化时更新图表
  watch(optionFn, () => {
    updateChart()
  }, { deep: true })

  return {
    chart,
    updateChart,
    rebuildChart
  }
}
