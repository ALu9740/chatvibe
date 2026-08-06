<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台 - 系统配置
// 对应 PRD 5.9 系统配置
// 功能：限流器配置、熔断器配置、缓存管理、邮件配置
// ============================================================
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRateLimiters,
  updateRateLimiter,
  getCircuitBreakers,
  updateCircuitBreaker,
  getCacheStats,
  clearCache,
  getEmailConfig,
  updateEmailConfig
} from '@/api/admin'
import type { RateLimiterConfig, CircuitBreakerConfig, CacheStat, EmailConfig } from '@/types/admin'

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

async function loadRateLimiters() {
  rateLoading.value = true
  try {
    rateLimiters.value = await getRateLimiters()
  } catch {
    ElMessage.error('加载限流器配置失败')
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

async function saveRateLimiter() {
  try {
    await updateRateLimiter(rateEditForm.name, rateEditForm)
    ElMessage.success('限流器配置已更新')
    rateEditing.value = null
    loadRateLimiters()
  } catch {
    ElMessage.error('更新失败')
  }
}

async function loadCircuitBreakers() {
  cbLoading.value = true
  try {
    circuitBreakers.value = await getCircuitBreakers()
  } catch {
    ElMessage.error('加载熔断器配置失败')
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

async function saveCircuitBreaker() {
  try {
    await updateCircuitBreaker(cbEditForm.name, cbEditForm)
    ElMessage.success('熔断器配置已更新')
    cbEditing.value = null
    loadCircuitBreakers()
  } catch {
    ElMessage.error('更新失败')
  }
}

async function loadCacheStats() {
  cacheLoading.value = true
  try {
    cacheStats.value = await getCacheStats()
  } catch {
    ElMessage.error('加载缓存统计失败')
  } finally {
    cacheLoading.value = false
  }
}

async function handleClearCache(name: string) {
  try {
    await ElMessageBox.confirm(`确定清除缓存 ${name} 吗？`, '清除缓存', { type: 'warning' })
    await clearCache(name)
    ElMessage.success('缓存已清除')
    loadCacheStats()
  } catch {
    // 用户取消
  }
}

async function loadEmailConfig() {
  emailLoading.value = true
  try {
    emailConfig.value = await getEmailConfig()
  } catch {
    ElMessage.error('加载邮件配置失败')
  } finally {
    emailLoading.value = false
  }
}

async function saveEmailConfig() {
  if (!emailConfig.value) return
  emailSaving.value = true
  try {
    await updateEmailConfig(emailConfig.value)
    ElMessage.success('邮件配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    emailSaving.value = false
  }
}

function hitRateColor(rate: number): string {
  if (rate >= 80) return 'success'
  if (rate >= 50) return 'warning'
  return 'danger'
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
          <el-table :data="rateLimiters" stripe style="width: 100%">
            <el-table-column label="限流器名称" prop="name" min-width="220" />
            <el-table-column label="周期限制数" width="130">
              <template #default="{ row }">
                <template v-if="rateEditing === row.name">
                  <el-input-number v-model="rateEditForm.limitForPeriod" :min="1" size="small" style="width: 100px" />
                </template>
                <template v-else>{{ row.limitForPeriod }}</template>
              </template>
            </el-table-column>
            <el-table-column label="刷新周期" width="140">
              <template #default="{ row }">
                <template v-if="rateEditing === row.name">
                  <el-input v-model="rateEditForm.limitRefreshPeriod" size="small" style="width: 90px" />
                </template>
                <template v-else>{{ row.limitRefreshPeriod }}</template>
              </template>
            </el-table-column>
            <el-table-column label="超时时间" width="130">
              <template #default="{ row }">
                <template v-if="rateEditing === row.name">
                  <el-input v-model="rateEditForm.timeoutDuration" size="small" style="width: 80px" />
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
                  <el-button type="primary" size="small" link @click="startEditRateLimiter(row)">编辑</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 熔断器配置 -->
      <el-tab-pane label="熔断器配置" name="circuit-breaker">
        <el-card shadow="never" v-loading="cbLoading">
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
                  <el-input v-model="cbEditForm.slowCallDurationThreshold" size="small" style="width: 70px" />
                </template>
                <template v-else>{{ row.slowCallDurationThreshold }}</template>
              </template>
            </el-table-column>
            <el-table-column label="开启等待时间" width="120">
              <template #default="{ row }">
                <template v-if="cbEditing === row.name">
                  <el-input v-model="cbEditForm.waitDurationInOpenState" size="small" style="width: 70px" />
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
                  <el-button type="primary" size="small" link @click="startEditCb(row)">编辑</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 缓存管理 -->
      <el-tab-pane label="缓存管理" name="cache">
        <el-card shadow="never" v-loading="cacheLoading">
          <el-table :data="cacheStats" stripe style="width: 100%">
            <el-table-column label="缓存名称" prop="name" min-width="180" />
            <el-table-column label="命中率" width="140">
              <template #default="{ row }">
                <el-progress :percentage="row.hitRate" :color="hitRateColor(row.hitRate) === 'success' ? '#67C23A' : hitRateColor(row.hitRate) === 'warning' ? '#E6A23C' : '#F56C6C'" :stroke-width="14" :text-inside="true" style="width: 100px" />
              </template>
            </el-table-column>
            <el-table-column label="缓存条数" prop="size" width="120" align="center" />
            <el-table-column label="TTL" prop="ttl" width="100" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="danger" size="small" link @click="handleClearCache(row.name)">清除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 邮件配置 -->
      <el-tab-pane label="邮件配置" name="email">
        <el-card shadow="never" v-loading="emailLoading">
          <el-form v-if="emailConfig" :model="emailConfig" label-width="120px" style="max-width: 500px">
            <el-form-item label="SMTP 服务器">
              <el-input v-model="emailConfig.host" placeholder="smtp.example.com" />
            </el-form-item>
            <el-form-item label="端口">
              <el-input-number v-model="emailConfig.port" :min="1" :max="65535" style="width: 180px" />
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="emailConfig.username" placeholder="noreply@chatvibe.com" />
            </el-form-item>
            <el-form-item label="发件人地址">
              <el-input v-model="emailConfig.fromEmail" placeholder="ChatVibe <noreply@chatvibe.com>" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="emailSaving" @click="saveEmailConfig">保存配置</el-button>
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
</style>
