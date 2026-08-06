<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台 - 数据概览仪表盘
// 对应 PRD 5.3 数据概览仪表盘
// 含：8个核心指标卡片 + 3张趋势图 + 系统健康状态
// ============================================================
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useECharts, getChartColors } from '@/composables/useECharts'
import { useThemeStore } from '@/stores/theme'
import {
  getDashboardMetrics,
  getUserGrowthTrend,
  getMessageTrend,
  getAiUsageTrend,
  getSystemHealth
} from '@/api/admin'
import type {
  DashboardMetrics,
  UserGrowthTrend,
  MessageTrend,
  AiUsageTrend,
  SystemHealth,
  MiddlewareHealth,
  TrendRange,
  MetricCard
} from '@/types/admin'

const router = useRouter()
const themeStore = useThemeStore()

// --------------------------------------------------------
// 指标卡片数据
// --------------------------------------------------------
const metricsLoading = ref(false)
const metricsFailed = ref(false)
const metrics = ref<DashboardMetrics | null>(null)

/** 格式化数字显示（万级以上缩写） */
function formatNum(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

/** 指标卡片配置 */
const metricCards = computed<MetricCard[]>(() => {
  const m = metrics.value
  const failed = metricsFailed.value
  const dash = '—'
  return [
    {
      key: 'totalUsers',
      label: '总用户数',
      value: failed ? dash : (m ? formatNum(m.totalUsers) : '...'),
      icon: 'User',
      gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      link: '/admin/users'
    },
    {
      key: 'onlineUsers',
      label: '在线用户',
      value: failed ? dash : (m ? formatNum(m.onlineUsers) : '...'),
      icon: 'Connection',
      gradient: 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)',
      link: '/admin/users'
    },
    {
      key: 'todayNewUsers',
      label: '今日新增',
      value: failed ? dash : (m ? m.todayNewUsers : '...'),
      icon: 'UserFilled',
      gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
      link: '/admin/users'
    },
    {
      key: 'todayMessages',
      label: '今日消息',
      value: failed ? dash : (m ? formatNum(m.todayMessages) : '...'),
      icon: 'ChatDotRound',
      gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
      link: '/admin/messages'
    },
    {
      key: 'todayAiCalls',
      label: '今日AI调用',
      value: failed ? dash : (m ? formatNum(m.todayAiCalls) : '...'),
      icon: 'MagicStick',
      gradient: 'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
      link: '/admin/ai'
    },
    {
      key: 'activeGroups',
      label: '活跃群组',
      value: failed ? dash : (m ? m.activeGroups : '...'),
      icon: 'OfficeBuilding',
      gradient: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
      link: '/admin/groups'
    },
    {
      key: 'apiAvailability',
      label: 'API可用率',
      value: failed ? dash : (m ? m.apiAvailability.toFixed(1) + '%' : '...'),
      icon: 'CircleCheck',
      gradient: 'linear-gradient(135deg, #30cfd0 0%, #330867 100%)'
    },
    {
      key: 'avgResponseTime',
      label: '平均响应',
      value: failed ? dash : (m ? m.avgResponseTime + 'ms' : '...'),
      icon: 'Timer',
      gradient: 'linear-gradient(135deg, #5ee7df 0%, #b490ca 100%)'
    }
  ]
})

function handleCardClick(card: MetricCard) {
  if (card.link && !metricsFailed.value) {
    router.push(card.link)
  }
}

// --------------------------------------------------------
// 趋势图数据
// --------------------------------------------------------
const userGrowthRange = ref<TrendRange>(30)
const msgAiRange = ref<TrendRange>(7)
const aiUsageRange = ref<TrendRange>(7)

const userGrowthData = ref<UserGrowthTrend>({ dates: [], cumulative: [] })
const msgTrendData = ref<MessageTrend>({ dates: [], messages: [], aiCalls: [] })
const aiUsageData = ref<AiUsageTrend>({ dates: [], calls: [], providerBreakdown: [] })

const rangeOptions: { label: string; value: TrendRange }[] = [
  { label: '7天', value: 7 },
  { label: '30天', value: 30 },
  { label: '90天', value: 90 }
]

// --------------------------------------------------------
// 系统健康状态
// --------------------------------------------------------
const healthLoading = ref(false)
const health = ref<SystemHealth | null>(null)

const healthList = computed<MiddlewareHealth[]>(() => {
  if (!health.value) return []
  return [health.value.mysql, health.value.redis, health.value.rabbitmq, health.value.minio]
})

function healthColor(status: string): string {
  const map: Record<string, string> = {
    healthy: '#10B981',
    warning: '#F59E0B',
    error: '#EF4444',
    checking: '#94A3B8'
  }
  return map[status] || '#94A3B8'
}

function healthDotClass(status: string): string {
  return `dot-${status}`
}

// --------------------------------------------------------
// ECharts 图表
// --------------------------------------------------------
const userGrowthEl = ref<HTMLElement | null>(null)
const msgAiTrendEl = ref<HTMLElement | null>(null)
const aiUsageEl = ref<HTMLElement | null>(null)

/** 用户增长趋势图 option */
const userGrowthOption = computed(() => {
  const c = getChartColors()
  return {
    animation: false,
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.bgOverlay,
      borderColor: c.border,
      textStyle: { color: c.text, fontSize: 12 },
      formatter: (params: { name: string; value: number }[]) => {
        const p = params[0]
        return `<div style="font-weight:600">${p.name}</div><div style="color:${c.textMuted};margin-top:4px">累计用户: <span style="color:${c.primary};font-weight:600">${p.value.toLocaleString()}</span></div>`
      }
    },
    grid: { top: 20, bottom: 30, left: 55, right: 20 },
    xAxis: {
      type: 'category',
      data: userGrowthData.value.dates,
      axisLine: { lineStyle: { color: c.border } },
      axisLabel: { color: c.textMuted, fontSize: 11, interval: 'auto' },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: c.textMuted, fontSize: 11 },
      splitLine: { lineStyle: { color: c.border, type: 'dashed' } }
    },
    series: [{
      name: '累计用户',
      type: 'line',
      data: userGrowthData.value.cumulative,
      smooth: true,
      showSymbol: false,
      lineStyle: { color: c.primary, width: 2.5 },
      itemStyle: { color: c.primary },
      areaStyle: {
        color: {
          type: 'linear' as const, x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: c.primary + '33' },
            { offset: 1, color: c.primary + '05' }
          ]
        }
      }
    }]
  }
})

/** 消息量与AI调用量趋势图 option */
const msgAiTrendOption = computed(() => {
  const c = getChartColors()
  return {
    animation: false,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: c.bgOverlay,
      borderColor: c.border,
      textStyle: { color: c.text, fontSize: 12 }
    },
    legend: {
      data: ['消息量', 'AI 调用量'],
      top: 0, right: 10,
      textStyle: { color: c.textMuted, fontSize: 12 },
      itemWidth: 12, itemHeight: 12,
      itemGap: 16
    },
    grid: { top: 50, bottom: 30, left: 55, right: 55 },
    xAxis: {
      type: 'category',
      data: msgTrendData.value.dates,
      axisLine: { lineStyle: { color: c.border } },
      axisLabel: { color: c.textMuted, fontSize: 11 },
      axisTick: { show: false }
    },
    yAxis: [
      {
        type: 'value', name: '消息量',
        nameTextStyle: { color: c.textMuted, fontSize: 11 },
        axisLine: { show: false },
        axisLabel: { color: c.textMuted, fontSize: 11 },
        splitLine: { lineStyle: { color: c.border, type: 'dashed' } }
      },
      {
        type: 'value', name: 'AI调用',
        nameTextStyle: { color: c.textMuted, fontSize: 11 },
        axisLine: { show: false },
        axisLabel: { color: c.textMuted, fontSize: 11 },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '消息量', type: 'bar', barWidth: '35%',
        data: msgTrendData.value.messages,
        itemStyle: {
          color: {
            type: 'linear' as const, x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: c.primary },
              { offset: 1, color: c.primary + '66' }
            ]
          },
          borderRadius: [4, 4, 0, 0]
        }
      },
      {
        name: 'AI 调用量', type: 'bar', barWidth: '35%', yAxisIndex: 1,
        data: msgTrendData.value.aiCalls,
        itemStyle: {
          color: {
            type: 'linear' as const, x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: c.sky },
              { offset: 1, color: c.sky + '66' }
            ]
          },
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  }
})

/** AI 用量趋势 + 供应商占比 option */
const aiUsageOption = computed(() => {
  const c = getChartColors()
  return {
    animation: false,
    tooltip: {
      trigger: 'item',
      backgroundColor: c.bgOverlay,
      borderColor: c.border,
      textStyle: { color: c.text, fontSize: 12 }
    },
    title: [
      {
        text: '调用量趋势', left: '2%', top: 0,
        textStyle: { color: c.text, fontSize: 13, fontWeight: 600 }
      },
      {
        text: '供应商占比', left: '60%', top: 0,
        textStyle: { color: c.text, fontSize: 13, fontWeight: 600 }
      }
    ],
    grid: { top: 35, bottom: 30, left: 50, right: '48%' },
    xAxis: {
      type: 'category',
      data: aiUsageData.value.dates,
      axisLine: { lineStyle: { color: c.border } },
      axisLabel: { color: c.textMuted, fontSize: 10 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: c.textMuted, fontSize: 10 },
      splitLine: { lineStyle: { color: c.border, type: 'dashed' } }
    },
    series: [
      {
        name: '调用量', type: 'line',
        data: aiUsageData.value.calls,
        smooth: true, showSymbol: false,
        lineStyle: { color: c.sky, width: 2.5 },
        itemStyle: { color: c.sky },
        areaStyle: {
          color: {
            type: 'linear' as const, x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: c.sky + '33' },
              { offset: 1, color: c.sky + '05' }
            ]
          }
        }
      },
      {
        name: '供应商占比', type: 'pie',
        center: ['72%', '55%'], radius: ['38%', '62%'],
        label: { color: c.textMuted, fontSize: 11, formatter: '{b}\n{d}%' },
        labelLine: { lineStyle: { color: c.border } },
        data: aiUsageData.value.providerBreakdown.map((item, idx) => ({
          ...item,
          itemStyle: { color: idx === 0 ? c.primary : c.sky }
        }))
      }
    ]
  }
})

// 初始化图表
const { rebuildChart: rebuildUserGrowth } = useECharts(userGrowthEl, () => userGrowthOption.value)
const { rebuildChart: rebuildMsgAiTrend } = useECharts(msgAiTrendEl, () => msgAiTrendOption.value)
const { rebuildChart: rebuildAiUsage } = useECharts(aiUsageEl, () => aiUsageOption.value)

// --------------------------------------------------------
// 数据加载
// --------------------------------------------------------
async function loadMetrics() {
  metricsLoading.value = true
  metricsFailed.value = false
  try {
    metrics.value = await getDashboardMetrics()
  } catch {
    metricsFailed.value = true
  } finally {
    metricsLoading.value = false
  }
}

async function loadUserGrowth() {
  try {
    userGrowthData.value = await getUserGrowthTrend(userGrowthRange.value)
    rebuildUserGrowth()
  } catch { /* 静默失败，保留旧数据 */ }
}

async function loadMsgTrend() {
  try {
    msgTrendData.value = await getMessageTrend(msgAiRange.value)
    rebuildMsgAiTrend()
  } catch { /* 静默失败 */ }
}

async function loadAiUsage() {
  try {
    aiUsageData.value = await getAiUsageTrend(aiUsageRange.value)
    rebuildAiUsage()
  } catch { /* 静默失败 */ }
}

async function loadHealth() {
  healthLoading.value = true
  try {
    health.value = await getSystemHealth()
  } catch {
    // 健康检查失败，保留旧数据
  } finally {
    healthLoading.value = false
  }
}

async function loadAllCharts() {
  await Promise.all([loadUserGrowth(), loadMsgTrend(), loadAiUsage()])
}

// --------------------------------------------------------
// 自动刷新
// --------------------------------------------------------
let metricsTimer: ReturnType<typeof setInterval> | null = null
let healthTimer: ReturnType<typeof setInterval> | null = null

// --------------------------------------------------------
// 生命周期
// --------------------------------------------------------
onMounted(async () => {
  // 并行加载所有数据
  await Promise.all([
    loadMetrics(),
    loadAllCharts(),
    loadHealth()
  ])

  // 指标每60秒刷新
  metricsTimer = setInterval(loadMetrics, 60_000)
  // 健康状态每30秒刷新
  healthTimer = setInterval(loadHealth, 30_000)
})

// 主题切换时重建所有图表（ECharts 需要重新读取 CSS 变量）
watch(() => themeStore.isDark, () => {
  rebuildUserGrowth()
  rebuildMsgAiTrend()
  rebuildAiUsage()
})

onBeforeUnmount(() => {
  if (metricsTimer) clearInterval(metricsTimer)
  if (healthTimer) clearInterval(healthTimer)
})

// 时间范围切换时重新加载
function onUserGrowthRangeChange() { loadUserGrowth() }
function onMsgAiRangeChange() { loadMsgTrend() }
function onAiUsageRangeChange() { loadAiUsage() }
</script>

<template>
  <div class="dashboard">
    <!-- 指标卡片区域 -->
    <section class="metrics-section">
      <div class="section-header">
        <h3 class="section-title">核心指标</h3>
        <div class="section-actions">
          <el-tag v-if="metricsFailed" type="danger" size="small" effect="plain">数据获取失败</el-tag>
          <el-button
            :icon="'Refresh'"
            text
            size="small"
            :loading="metricsLoading"
            @click="loadMetrics"
          >刷新</el-button>
        </div>
      </div>
      <div class="metrics-grid">
        <div
          v-for="card in metricCards"
          :key="card.key"
          class="metric-card"
          :class="{ clickable: card.link && !metricsFailed }"
          @click="handleCardClick(card)"
        >
          <div class="card-icon" :style="{ background: card.gradient }">
            <el-icon size="22" color="#fff"><component :is="card.icon" /></el-icon>
          </div>
          <div class="card-body">
            <div class="card-value" :class="{ failed: metricsFailed }">{{ card.value }}</div>
            <div class="card-label">{{ card.label }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- 趋势图区域 -->
    <section class="charts-section">
      <!-- 用户增长趋势 -->
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">用户增长趋势</span>
          <el-radio-group v-model="userGrowthRange" size="small" @change="onUserGrowthRangeChange">
            <el-radio-button v-for="opt in rangeOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <div ref="userGrowthEl" class="chart-canvas"></div>
      </div>

      <!-- 消息量与AI调用量 -->
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">消息量与 AI 调用量</span>
          <el-radio-group v-model="msgAiRange" size="small" @change="onMsgAiRangeChange">
            <el-radio-button v-for="opt in rangeOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <div ref="msgAiTrendEl" class="chart-canvas"></div>
      </div>

      <!-- AI 用量趋势 + 供应商占比 -->
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">AI 用量分析</span>
          <el-radio-group v-model="aiUsageRange" size="small" @change="onAiUsageRangeChange">
            <el-radio-button v-for="opt in rangeOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <div ref="aiUsageEl" class="chart-canvas"></div>
      </div>
    </section>

    <!-- 系统健康状态 -->
    <section class="health-section">
      <div class="section-header">
        <h3 class="section-title">系统健康状态</h3>
        <div class="section-actions">
          <el-button
            :icon="'Refresh'"
            text
            size="small"
            :loading="healthLoading"
            @click="loadHealth"
          >刷新</el-button>
        </div>
      </div>
      <div class="health-grid">
        <div
          v-for="item in healthList"
          :key="item.name"
          class="health-card"
        >
          <div class="health-header">
            <span class="health-name">{{ item.name }}</span>
            <span class="health-status" :style="{ color: healthColor(item.status) }">
              <i class="health-dot" :class="healthDotClass(item.status)"></i>
              {{ item.statusText }}
            </span>
          </div>
          <div class="health-metrics">
            <div
              v-for="m in item.metrics"
              :key="m.label"
              class="health-metric"
            >
              <span class="metric-label">{{ m.label }}</span>
              <span class="metric-value" :class="{ warning: m.warning }">{{ m.value }}</span>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-if="!healthLoading && !health" description="健康状态获取失败" />
    </section>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

// ---------- 区域通用 ----------
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  .section-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--admin-text-primary);
    margin: 0;
  }

  .section-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

// ---------- 指标卡片 ----------
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.metric-card {
  background: var(--admin-card-bg);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: var(--admin-shadow-sm);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid var(--admin-card-border);

  &.clickable {
    cursor: pointer;

    &:hover {
      box-shadow: var(--admin-shadow-lg);
      transform: translateY(-2px);
    }
  }
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--admin-text-primary);
  line-height: 1.2;
  letter-spacing: -0.02em;

  &.failed {
    color: #EF4444;
  }
}

.card-label {
  font-size: 13px;
  color: var(--admin-text-muted);
  margin-top: 4px;
}

// ---------- 图表卡片 ----------
.charts-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chart-card {
  background: var(--admin-card-bg);
  border-radius: 12px;
  padding: 20px;
  box-shadow: var(--admin-shadow-sm);
  border: 1px solid var(--admin-card-border);
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  .chart-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--admin-text-primary);
  }
}

.chart-canvas {
  width: 100%;
  height: 300px;
}

// ---------- 健康状态 ----------
.health-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.health-card {
  background: var(--admin-card-bg);
  border-radius: 12px;
  padding: 20px;
  box-shadow: var(--admin-shadow-sm);
  border: 1px solid var(--admin-card-border);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: var(--admin-shadow-md);
  }
}

.health-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--admin-card-border);

  .health-name {
    font-size: 15px;
    font-weight: 600;
    color: var(--admin-text-primary);
  }

  .health-status {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 500;
  }
}

.health-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;

  &.dot-healthy {
    background: #10B981;
    box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
  }

  &.dot-warning {
    background: #F59E0B;
    box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.15);
    animation: pulse-warning 2s ease-in-out infinite;
  }

  &.dot-error {
    background: #EF4444;
    box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.15);
    animation: pulse-error 1s ease-in-out infinite;
  }

  &.dot-checking {
    background: #94A3B8;
  }
}

@keyframes pulse-warning {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

@keyframes pulse-error {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.health-metrics {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.health-metric {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .metric-label {
    font-size: 13px;
    color: var(--admin-text-muted);
  }

  .metric-value {
    font-size: 13px;
    font-weight: 600;
    color: var(--admin-text-secondary);
    font-family: 'JetBrains Mono', monospace;

    &.warning {
      color: #F59E0B;
    }
  }
}

// ---------- 响应式 ----------
@media (max-width: 1200px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .health-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .metrics-grid,
  .health-grid {
    grid-template-columns: 1fr;
  }
}
</style>
