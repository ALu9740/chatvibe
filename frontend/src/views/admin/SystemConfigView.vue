<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台 - 系统配置
// 对应 PRD 5.9 系统配置
// 功能：限流器配置、熔断器配置、缓存管理、邮件配置
// ============================================================
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRateLimiters,
  updateRateLimiter,
  getCircuitBreakers,
  updateCircuitBreaker,
  getCacheStats,
  clearCache,
  getEmailConfig,
  updateEmailConfig,
  sendTestEmail
} from '@/api/admin'
import { useAdminAuthStore } from '@/stores/adminAuth'
import type { RateLimiterConfig, CircuitBreakerConfig, CacheStat, EmailConfig } from '@/types/admin'

const adminAuth = useAdminAuthStore()
const isSuperAdmin = computed(() => adminAuth.admin?.role === 'SUPER_ADMIN')

const activeTab = ref('rate-limiter')

// 限流器
const rateLimiters = ref<RateLimiterConfig[]>([])
const rateLoading = ref(false)
const rateEditing = ref<string | null>(null)
const rateEditForm = reactive<RateLimiterConfig>({ name: '', limitForPeriod: 0, limitRefreshPeriod: '', timeoutDuration: '' })

// 熔断器
const circuitBreakers = ref<CircuitBreakerConfig[]>([])
const cbLoading = ref(false)
const cbEditing = ref<string | null>(null)
const cbEditForm = reactive<CircuitBreakerConfig>({
  name: '', failureRateThreshold: 0, slowCallRateThreshold: 0,
  slowCallDurationThreshold: '', waitDurationInOpenState: '', permittedNumberOfCallsInHalfOpenState: 0
})

// 缓存
const cacheStats = ref<CacheStat[]>([])
const cacheLoading = ref(false)

// 邮件
const emailConfig = ref<EmailConfig | null>(null)
const emailLoading = ref(false)
const emailSaving = ref(false)
const emailTesting = ref(false)

// ==================== 限流器 ====================

async function loadRateLimiters() {
  rateLoading.value = true
  try {
    rateLimiters.value = await getRateLimiters()
  } catch {
    // 拦截器已提示
  } finally {
    rateLoading.value = false
  }
}

function startEditRateLimiter(row: RateLimiterConfig) {
  rateEditing.value = row.name
  Object.assign(rateEditForm, row)
}

function cancelEditRateLimiter() {
  rateEditing.value = null
}

function validateRateLimiter(): boolean {
  if (!rateEditForm.limitForPeriod || rateEditForm.limitForPeriod < 1 || rateEditForm.limitForPeriod > 10000) {
    ElMessage.warning('周期限制数范围: 1-10000')
    return false
  }
  const refreshSecs = parseDurationToSeconds(rateEditForm.limitRefreshPeriod)
  if (refreshSecs === null || refreshSecs < 1 || refreshSecs > 3600) {
    ElMessage.warning('刷新周期范围: 1s-1h（格式如 1s、1m、1h）')
    return false
  }
  const timeoutSecs = parseDurationToSeconds(rateEditForm.timeoutDuration)
  if (timeoutSecs === null || timeoutSecs < 0 || timeoutSecs > 60) {
    ElMessage.warning('超时时间范围: 0s-60s（格式如 0s、1s、1m）')
    return false
  }
  return true
}

async function saveRateLimiter() {
  if (!validateRateLimiter()) return
  try {
    await updateRateLimiter(rateEditForm.name, rateEditForm)
    ElMessage.success('限流器配置已更新，实时生效')
    rateEditing.value = null
    loadRateLimiters()
  } catch {
    // 拦截器已提示（含冷却期、权限等错误信息）
  }
}

// ==================== 熔断器 ====================

async function loadCircuitBreakers() {
  cbLoading.value = true
  try {
    circuitBreakers.value = await getCircuitBreakers()
  } catch {
    // 拦截器已提示
  } finally {
    cbLoading.value = false
  }
}

function startEditCb(row: CircuitBreakerConfig) {
  cbEditing.value = row.name
  Object.assign(cbEditForm, row)
}

function cancelEditCb() {
  cbEditing.value = null
}

function validateCircuitBreaker(): boolean {
  if (!cbEditForm.failureRateThreshold || cbEditForm.failureRateThreshold < 1 || cbEditForm.failureRateThreshold > 100) {
    ElMessage.warning('失败率阈值范围: 1-100')
    return false
  }
  if (!cbEditForm.slowCallRateThreshold || cbEditForm.slowCallRateThreshold < 1 || cbEditForm.slowCallRateThreshold > 100) {
    ElMessage.warning('慢调用率阈值范围: 1-100')
    return false
  }
  const waitSecs = parseDurationToSeconds(cbEditForm.waitDurationInOpenState)
  if (waitSecs === null || waitSecs < 1 || waitSecs > 600) {
    ElMessage.warning('开启等待时间范围: 1s-10min（格式如 1s、1m、10m）')
    return false
  }
  if (!cbEditForm.permittedNumberOfCallsInHalfOpenState || cbEditForm.permittedNumberOfCallsInHalfOpenState < 1) {
    ElMessage.warning('半开调用数必须大于 0')
    return false
  }
  return true
}

async function saveCircuitBreaker() {
  if (!validateCircuitBreaker()) return
  try {
    await updateCircuitBreaker(cbEditForm.name, cbEditForm)
    ElMessage.success('熔断器配置已更新，实时生效')
    cbEditing.value = null
    loadCircuitBreakers()
  } catch {
    // 拦截器已提示
  }
}

// ==================== 缓存 ====================

async function loadCacheStats() {
  cacheLoading.value = true
  try {
    cacheStats.value = await getCacheStats()
  } catch {
    // 拦截器已提示
  } finally {
    cacheLoading.value = false
  }
}

async function handleClearCache(name: string) {
  try {
    await ElMessageBox.confirm(
      `确定清除缓存「${name}」吗？此操作会影响所有用户，请谨慎操作。`,
      '清除缓存',
      { type: 'warning', confirmButtonText: '确认清除', cancelButtonText: '取消' }
    )
    await clearCache(name)
    ElMessage.success('缓存已清除')
    loadCacheStats()
  } catch (err) {
    // 用户取消或拦截器已提示
  }
}

// ==================== 邮件 ====================

async function loadEmailConfig() {
  emailLoading.value = true
  try {
    const data = await getEmailConfig()
    // 设置默认值，避免 null 导致前端组件异常
    if (data.sslEnabled === null || data.sslEnabled === undefined) {
      data.sslEnabled = data.port === 465
    }
    if (!data.password) {
      data.password = ''
    }
    emailConfig.value = data
  } catch {
    // 拦截器已提示
  } finally {
    emailLoading.value = false
  }
}

async function saveEmailConfig() {
  if (!emailConfig.value) return
  if (!emailConfig.value.host || !emailConfig.value.host.trim()) {
    ElMessage.warning('SMTP 服务器地址不能为空')
    return
  }
  if (!emailConfig.value.port || emailConfig.value.port < 1 || emailConfig.value.port > 65535) {
    ElMessage.warning('端口范围: 1-65535')
    return
  }
  if (!emailConfig.value.username || !emailConfig.value.username.trim()) {
    ElMessage.warning('SMTP 用户名不能为空')
    return
  }
  emailSaving.value = true
  try {
    await updateEmailConfig(emailConfig.value)
    ElMessage.success('邮件配置已保存')
  } catch {
    // 拦截器已提示
  } finally {
    emailSaving.value = false
  }
}

async function handleSendTestEmail() {
  if (!emailConfig.value) return
  if (!emailConfig.value.host || !emailConfig.value.host.trim()) {
    ElMessage.warning('SMTP 服务器地址不能为空')
    return
  }
  if (!emailConfig.value.port || emailConfig.value.port < 1 || emailConfig.value.port > 65535) {
    ElMessage.warning('端口范围: 1-65535')
    return
  }
  if (!emailConfig.value.username || !emailConfig.value.username.trim()) {
    ElMessage.warning('SMTP 用户名不能为空')
    return
  }
  emailTesting.value = true
  try {
    await sendTestEmail(emailConfig.value)
    ElMessage.success('测试邮件已发送，请查收')
  } catch {
    // 拦截器已提示
  } finally {
    emailTesting.value = false
  }
}

// ==================== 工具函数 ====================

function hitRateColor(rate: number): string {
  if (rate >= 80) return '#67C23A'
  if (rate >= 50) return '#E6A23C'
  return '#F56C6C'
}

function parseDurationToSeconds(str: string): number | null {
  if (!str || !str.trim()) return null
  str = str.trim()
  try {
    if (str.endsWith('ms')) return parseInt(str.slice(0, -2)) / 1000
    if (str.endsWith('s')) return parseInt(str.slice(0, -1))
    if (str.endsWith('m')) return parseInt(str.slice(0, -1)) * 60
    if (str.endsWith('h')) return parseInt(str.slice(0, -1)) * 3600
    return parseInt(str)
  } catch {
    return null
  }
}

onMounted(() => {
  loadRateLimiters()
  loadCircuitBreakers()
  loadCacheStats()
  loadEmailConfig()
})
</script>

<template>
  <div class="system-config">
    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- 限流器配置 -->
      <el-tab-pane label="限流器配置" name="rate-limiter">
        <el-card shadow="never" v-loading="rateLoading">
          <template #header>
            <div class="card-header">
              <span>Resilience4j RateLimiter 实例</span>
              <span class="header-hint">参数范围: 周期数 1-10000 | 刷新周期 1s-1h | 超时 0s-60s | 冷却期 5 分钟</span>
            </div>
          </template>
          <el-table :data="rateLimiters" stripe style="width: 100%">
            <el-table-column label="限流器名称" prop="name" min-width="220" />
            <el-table-column label="周期限制数" width="130">
              <template #default="{ row }">
                <template v-if="rateEditing === row.name">
                  <el-input-number v-model="rateEditForm.limitForPeriod" :min="1" :max="10000" size="small" style="width: 100px" />
                </template>
                <template v-else>{{ row.limitForPeriod }}</template>
              </template>
            </el-table-column>
            <el-table-column label="刷新周期" width="140">
              <template #default="{ row }">
                <template v-if="rateEditing === row.name">
                  <el-input v-model="rateEditForm.limitRefreshPeriod" size="small" style="width: 90px" placeholder="如 1s" />
                </template>
                <template v-else>{{ row.limitRefreshPeriod }}</template>
              </template>
            </el-table-column>
            <el-table-column label="超时时间" width="130">
              <template #default="{ row }">
                <template v-if="rateEditing === row.name">
                  <el-input v-model="rateEditForm.timeoutDuration" size="small" style="width: 80px" placeholder="如 0s" />
                </template>
                <template v-else>{{ row.timeoutDuration }}</template>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <template v-if="rateEditing === row.name">
                  <el-button type="primary" size="small" link @click="saveRateLimiter">保存</el-button>
                  <el-button size="small" link @click="cancelEditRateLimiter">取消</el-button>
                </template>
                <template v-else>
                  <el-button v-if="isSuperAdmin" type="primary" size="small" link @click="startEditRateLimiter(row)">编辑</el-button>
                  <span v-else class="text-muted">仅超管可修改</span>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 熔断器配置 -->
      <el-tab-pane label="熔断器配置" name="circuit-breaker">
        <el-card shadow="never" v-loading="cbLoading">
          <template #header>
            <div class="card-header">
              <span>Resilience4j CircuitBreaker 实例</span>
              <span class="header-hint">参数范围: 失败率 1-100% | 慢调用率 1-100% | 等待时间 1s-10min | 冷却期 5 分钟</span>
            </div>
          </template>
          <el-table :data="circuitBreakers" stripe style="width: 100%">
            <el-table-column label="熔断器名称" prop="name" min-width="200" />
            <el-table-column label="失败率阈值" width="120">
              <template #default="{ row }">
                <template v-if="cbEditing === row.name">
                  <el-input-number v-model="cbEditForm.failureRateThreshold" :min="1" :max="100" size="small" style="width: 90px" />%
                </template>
                <template v-else>{{ row.failureRateThreshold }}%</template>
              </template>
            </el-table-column>
            <el-table-column label="慢调用率阈值" width="130">
              <template #default="{ row }">
                <template v-if="cbEditing === row.name">
                  <el-input-number v-model="cbEditForm.slowCallRateThreshold" :min="1" :max="100" size="small" style="width: 90px" />%
                </template>
                <template v-else>{{ row.slowCallRateThreshold }}%</template>
              </template>
            </el-table-column>
            <el-table-column label="慢调用阈值" width="120">
              <template #default="{ row }">
                <template v-if="cbEditing === row.name">
                  <el-input v-model="cbEditForm.slowCallDurationThreshold" size="small" style="width: 70px" placeholder="如 2s" />
                </template>
                <template v-else>{{ row.slowCallDurationThreshold }}</template>
              </template>
            </el-table-column>
            <el-table-column label="开启等待时间" width="120">
              <template #default="{ row }">
                <template v-if="cbEditing === row.name">
                  <el-input v-model="cbEditForm.waitDurationInOpenState" size="small" style="width: 70px" placeholder="如 30s" />
                </template>
                <template v-else>{{ row.waitDurationInOpenState }}</template>
              </template>
            </el-table-column>
            <el-table-column label="半开调用数" width="110">
              <template #default="{ row }">
                <template v-if="cbEditing === row.name">
                  <el-input-number v-model="cbEditForm.permittedNumberOfCallsInHalfOpenState" :min="1" size="small" style="width: 80px" />
                </template>
                <template v-else>{{ row.permittedNumberOfCallsInHalfOpenState }}</template>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <template v-if="cbEditing === row.name">
                  <el-button type="primary" size="small" link @click="saveCircuitBreaker">保存</el-button>
                  <el-button size="small" link @click="cancelEditCb">取消</el-button>
                </template>
                <template v-else>
                  <el-button v-if="isSuperAdmin" type="primary" size="small" link @click="startEditCb(row)">编辑</el-button>
                  <span v-else class="text-muted">仅超管可修改</span>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 缓存管理 -->
      <el-tab-pane label="缓存管理" name="cache">
        <el-card shadow="never" v-loading="cacheLoading">
          <template #header>
            <div class="card-header">
              <span>Caffeine 本地缓存</span>
              <span class="header-hint">清除操作影响所有用户 {{ isSuperAdmin ? '' : '| 仅超管可清除' }}</span>
            </div>
          </template>
          <el-table :data="cacheStats" stripe style="width: 100%">
            <el-table-column label="缓存名称" prop="name" min-width="180" />
            <el-table-column label="命中率" width="160">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.round(row.hitRate)"
                  :color="hitRateColor(row.hitRate)"
                  :stroke-width="14"
                  :text-inside="true"
                  style="width: 120px"
                />
              </template>
            </el-table-column>
            <el-table-column label="缓存条数" prop="size" width="120" align="center" />
            <el-table-column label="TTL" prop="ttl" width="100" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="isSuperAdmin" type="danger" size="small" link @click="handleClearCache(row.name)">清除</el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 邮件配置 -->
      <el-tab-pane label="邮件配置" name="email">
        <el-card shadow="never" v-loading="emailLoading">
          <template #header>
            <div class="card-header">
              <span>SMTP 邮件服务器配置</span>
              <span class="header-hint">测试邮件间隔 60 秒</span>
            </div>
          </template>
          <el-form v-if="emailConfig" :model="emailConfig" label-width="120px" style="max-width: 500px">
            <el-form-item label="SMTP 服务器">
              <el-input v-model="emailConfig.host" placeholder="如 smtp.163.com" :disabled="!isSuperAdmin" />
            </el-form-item>
            <el-form-item label="端口">
              <el-input-number v-model="emailConfig.port" :min="1" :max="65535" style="width: 180px" :disabled="!isSuperAdmin" />
            </el-form-item>
            <el-form-item label="SSL 加密">
              <el-switch v-model="emailConfig.sslEnabled" :disabled="!isSuperAdmin" />
              <span class="form-hint">端口 465 选 SSL，端口 587 选 STARTTLS（关闭）</span>
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="emailConfig.username" placeholder="SMTP 登录账号" :disabled="!isSuperAdmin" />
            </el-form-item>
            <el-form-item label="授权码/密码">
              <el-input v-model="emailConfig.password" type="password" show-password placeholder="SMTP 授权码" :disabled="!isSuperAdmin" />
            </el-form-item>
            <el-form-item label="发件人地址">
              <el-input v-model="emailConfig.fromEmail" placeholder="留空则使用用户名作为发件人" :disabled="!isSuperAdmin" />
            </el-form-item>
            <el-form-item v-if="isSuperAdmin">
              <el-button type="primary" :loading="emailSaving" @click="saveEmailConfig">保存配置</el-button>
              <el-button type="success" :loading="emailTesting" @click="handleSendTestEmail" style="margin-left: 10px">发送测试邮件</el-button>
            </el-form-item>
            <el-form-item v-else>
              <span class="text-muted">仅超级管理员可操作邮件配置</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.system-config {
  display: flex;
  flex-direction: column;
}

.config-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}

:deep(.el-card__body) {
  padding: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .header-hint {
    font-size: 12px;
    color: #909399;
  }
}

.text-muted {
  font-size: 12px;
  color: #909399;
}

.form-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
