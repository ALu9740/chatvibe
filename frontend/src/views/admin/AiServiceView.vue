<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台 - AI 服务管理
// 对应 PRD 5.7 AI 服务管理
// 功能：供应商 CRUD + 连接测试、故障转移优先级配置、AI 对话监控
// ============================================================
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAiProviders,
  addAiProvider,
  updateAiProvider,
  deleteAiProvider,
  testAiProvider,
  getFailoverConfig,
  updateFailoverConfig,
  getAiConversations
} from '@/api/admin'
import type {
  AiProviderStatus,
  AiProviderType,
  SaveAiProviderRequest,
  FailoverConfig,
  AiConversationRecord
} from '@/types/admin'
import type { PageResult } from '@/types'

// ---------- 供应商列表 ----------
const providersLoading = ref(false)
const providers = ref<AiProviderStatus[]>([])

// ---------- 添加/编辑供应商 ----------
const providerDialogVisible = ref(false)
const providerDialogTitle = ref('添加 AI 供应商')
const providerSaving = ref(false)
const editingProviderId = ref<number | null>(null)
const providerForm = ref<SaveAiProviderRequest>({
  name: '',
  type: 'cloud',
  model: '',
  baseUrl: '',
  apiKey: '',
  priorityDev: 1,
  priorityProd: 1
})

const providerTypeOptions: { label: string; value: AiProviderType }[] = [
  { label: '云端 API', value: 'cloud' },
  { label: '本地部署', value: 'local' }
]

// ---------- 测试状态 ----------
const testingId = ref<number | null>(null)

// ---------- 故障转移配置 ----------
const failoverLoading = ref(false)
const failoverConfig = ref<FailoverConfig | null>(null)
const editDialogVisible = ref(false)
const editConfig = ref<FailoverConfig>({ enabled: false, devPriority: [], prodPriority: [] })
const failoverSaving = ref(false)
const devAddSelection = ref('')
const prodAddSelection = ref('')

// ---------- AI 对话记录 ----------
const convLoading = ref(false)
const convList = ref<AiConversationRecord[]>([])
const convTotal = ref(0)
const convPage = ref(1)
const convSize = ref(10)

// ============================================================
// 供应商相关方法
// ============================================================

function statusTagType(status: string): string {
  if (status === 'online') return 'success'
  if (status === 'checking') return 'warning'
  return 'danger'
}

function statusText(status: string): string {
  if (status === 'online') return '在线'
  if (status === 'checking') return '检测中'
  return '离线'
}

function providerTagType(type: string): string {
  return type === 'local' ? 'success' : 'primary'
}

/** 掩码显示 API Key */
function maskApiKey(key: string): string {
  if (!key) return '—'
  if (key.length <= 8) return '****'
  return key.substring(0, 4) + '****' + key.substring(key.length - 4)
}

async function loadProviders() {
  providersLoading.value = true
  try {
    providers.value = await getAiProviders()
  } catch {
    ElMessage.error('加载供应商列表失败')
  } finally {
    providersLoading.value = false
  }
}

/** 打开添加对话框 */
function openAddDialog() {
  editingProviderId.value = null
  providerDialogTitle.value = '添加 AI 供应商'
  providerForm.value = {
    name: '',
    type: 'cloud',
    model: '',
    baseUrl: '',
    apiKey: '',
    priorityDev: providers.value.length + 1,
    priorityProd: providers.value.length + 1
  }
  providerDialogVisible.value = true
}

/** 打开编辑对话框 */
function openEditDialog(row: AiProviderStatus) {
  editingProviderId.value = row.id
  providerDialogTitle.value = '编辑 AI 供应商'
  providerForm.value = {
    id: row.id,
    name: row.name,
    type: row.type,
    model: row.model,
    baseUrl: row.baseUrl,
    apiKey: '',
    priorityDev: row.priorityDev,
    priorityProd: row.priorityProd
  }
  providerDialogVisible.value = true
}

/** 保存供应商（添加或编辑） */
async function saveProvider() {
  const f = providerForm.value
  if (!f.name.trim()) { ElMessage.warning('请输入供应商名称'); return }
  if (!f.model.trim()) { ElMessage.warning('请输入模型名称'); return }
  if (!f.baseUrl.trim()) { ElMessage.warning('请输入 API 地址'); return }
  if (f.type === 'cloud' && !f.apiKey.trim() && editingProviderId.value === null) {
    ElMessage.warning('云端 API 需要填写 API Key')
    return
  }

  providerSaving.value = true
  try {
    if (editingProviderId.value !== null) {
      await updateAiProvider(editingProviderId.value, f)
      ElMessage.success('供应商已更新')
    } else {
      await addAiProvider(f)
      ElMessage.success('供应商已添加')
    }
    providerDialogVisible.value = false
    loadProviders()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    providerSaving.value = false
  }
}

/** 删除供应商 */
async function handleDelete(row: AiProviderStatus) {
  try {
    await ElMessageBox.confirm(
      `确定删除供应商 "${row.name}" 吗？删除后该供应商将不可用。`,
      '删除确认',
      { type: 'warning' }
    )
    await deleteAiProvider(row.id)
    ElMessage.success('供应商已删除')
    loadProviders()
  } catch {
    // 用户取消
  }
}

/** 测试供应商连接 */
async function handleTest(row: AiProviderStatus) {
  testingId.value = row.id
  try {
    const result = await testAiProvider(row.id)
    if (result.success) {
      ElMessage.success(result.message)
    } else {
      ElMessage.error(result.message)
    }
    loadProviders()
  } catch {
    ElMessage.error('测试请求失败')
  } finally {
    testingId.value = null
  }
}

// ============================================================
// 故障转移配置方法
// ============================================================

async function loadFailoverConfig() {
  failoverLoading.value = true
  try {
    failoverConfig.value = await getFailoverConfig()
  } catch {
    ElMessage.error('加载故障转移配置失败')
  } finally {
    failoverLoading.value = false
  }
}

/** 所有供应商名称（用于故障转移添加下拉） */
function availableProviderNames(usedList: string[]): string[] {
  return providers.value.map(p => p.name).filter(n => !usedList.includes(n))
}

function openFailoverEdit() {
  if (!failoverConfig.value) return
  editConfig.value = {
    enabled: failoverConfig.value.enabled,
    devPriority: [...failoverConfig.value.devPriority],
    prodPriority: [...failoverConfig.value.prodPriority]
  }
  editDialogVisible.value = true
}

function moveUp(list: 'dev' | 'prod', index: number) {
  if (index === 0) return
  const arr = list === 'dev' ? editConfig.value.devPriority : editConfig.value.prodPriority
  ;[arr[index - 1], arr[index]] = [arr[index], arr[index - 1]]
}

function moveDown(list: 'dev' | 'prod', index: number) {
  const arr = list === 'dev' ? editConfig.value.devPriority : editConfig.value.prodPriority
  if (index === arr.length - 1) return
  ;[arr[index + 1], arr[index]] = [arr[index], arr[index + 1]]
}

function removeProvider(list: 'dev' | 'prod', index: number) {
  const arr = list === 'dev' ? editConfig.value.devPriority : editConfig.value.prodPriority
  arr.splice(index, 1)
}

function addProvider(list: 'dev' | 'prod', name: string) {
  if (!name) { ElMessage.warning('请先选择供应商'); return }
  const arr = list === 'dev' ? editConfig.value.devPriority : editConfig.value.prodPriority
  if (arr.includes(name)) { ElMessage.warning(`${name} 已在列表中`); return }
  arr.push(name)
  if (list === 'dev') devAddSelection.value = ''
  else prodAddSelection.value = ''
}

async function saveFailoverConfig() {
  failoverSaving.value = true
  try {
    await updateFailoverConfig(editConfig.value)
    ElMessage.success('故障转移配置已保存')
    failoverConfig.value = { ...editConfig.value }
    editDialogVisible.value = false
  } catch {
    ElMessage.error('保存失败')
  } finally {
    failoverSaving.value = false
  }
}

// ============================================================
// 对话记录方法
// ============================================================

async function loadConversations() {
  convLoading.value = true
  try {
    const res: PageResult<AiConversationRecord> = await getAiConversations(convPage.value, convSize.value)
    convList.value = res.records
    convTotal.value = res.total
  } catch {
    ElMessage.error('加载对话记录失败')
  } finally {
    convLoading.value = false
  }
}

function handleConvPageChange(page: number) {
  convPage.value = page
  loadConversations()
}

onMounted(() => {
  loadProviders()
  loadFailoverConfig()
  loadConversations()
})
</script>

<template>
  <div class="ai-service">
    <!-- AI 供应商列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">AI 供应商管理</span>
          <div class="header-actions">
            <el-button size="small" @click="loadProviders">刷新</el-button>
            <el-button type="primary" size="small" @click="openAddDialog">
              <el-icon><Plus /></el-icon>&nbsp;添加供应商
            </el-button>
          </div>
        </div>
      </template>
      <el-table :data="providers" v-loading="providersLoading" stripe style="width: 100%">
        <el-table-column label="ID" prop="id" width="60" />
        <el-table-column label="供应商名称" min-width="120">
          <template #default="{ row }">
            <span class="provider-name-text">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="providerTagType(row.type)" size="small">{{ row.type === 'local' ? '本地' : '云端' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模型" prop="model" min-width="150" />
        <el-table-column label="API 地址" prop="baseUrl" min-width="220" show-overflow-tooltip />
        <el-table-column label="API Key" width="130">
          <template #default="{ row }">
            <span class="api-key-text">{{ maskApiKey(row.apiKey) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="延迟" width="80">
          <template #default="{ row }">
            {{ row.latency > 0 ? row.latency + 'ms' : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="开发优先级" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="primary" size="small">P{{ row.priorityDev }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生产优先级" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small">P{{ row.priorityProd }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              type="success"
              size="small"
              link
              :loading="testingId === row.id"
              @click="handleTest(row)"
            >测试</el-button>
            <el-button type="primary" size="small" link @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" size="small" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 故障转移配置 -->
    <el-card shadow="never" v-loading="failoverLoading">
      <template #header>
        <div class="card-header">
          <span class="header-title">故障转移配置</span>
          <el-button type="primary" size="small" @click="openFailoverEdit">
            <el-icon><Edit /></el-icon>&nbsp;编辑配置
          </el-button>
        </div>
      </template>
      <div v-if="failoverConfig" class="failover-config">
        <el-form label-width="120px">
          <el-form-item label="启用故障转移">
            <el-tag :type="failoverConfig.enabled ? 'success' : 'info'" size="small">
              {{ failoverConfig.enabled ? '已启用' : '未启用' }}
            </el-tag>
          </el-form-item>
          <el-form-item label="开发环境优先级">
            <div class="priority-list">
              <el-tag
                v-for="(name, idx) in failoverConfig.devPriority"
                :key="idx"
                type="primary"
                size="small"
                class="priority-tag"
              >{{ idx + 1 }}. {{ name }}{{ idx === 0 ? '（主）' : '（兜底）' }}</el-tag>
            </div>
          </el-form-item>
          <el-form-item label="生产环境优先级">
            <div class="priority-list">
              <el-tag
                v-for="(name, idx) in failoverConfig.prodPriority"
                :key="idx"
                type="success"
                size="small"
                class="priority-tag"
              >{{ idx + 1 }}. {{ name }}{{ idx === 0 ? '（主）' : '（兜底）' }}</el-tag>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- AI 对话记录 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">AI 对话监控</span>
          <el-button size="small" @click="loadConversations">刷新</el-button>
        </div>
      </template>
      <el-table :data="convList" v-loading="convLoading" stripe style="width: 100%">
        <el-table-column label="对话ID" prop="id" width="90" />
        <el-table-column label="用户" min-width="120">
          <template #default="{ row }">
            <span>{{ row.userNickname }}</span>
            <span class="user-id-text">(ID: {{ row.userId }})</span>
          </template>
        </el-table-column>
        <el-table-column label="对话标题" prop="title" min-width="200" show-overflow-tooltip />
        <el-table-column label="供应商" width="100">
          <template #default="{ row }">
            <el-tag :type="row.provider === 'Ollama' ? 'success' : 'primary'" size="small">{{ row.provider }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模型" prop="model" width="150" />
        <el-table-column label="消息数" prop="messageCount" width="90" align="center" />
        <el-table-column label="最后消息时间" prop="lastMessageAt" width="160" />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="convPage"
          v-model:page-size="convSize"
          :total="convTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next"
          @current-change="handleConvPageChange"
        />
      </div>
    </el-card>

    <!-- 添加/编辑供应商对话框 -->
    <el-dialog v-model="providerDialogVisible" :title="providerDialogTitle" width="560px">
      <el-form :model="providerForm" label-width="100px">
        <el-form-item label="供应商名称">
          <el-input v-model="providerForm.name" placeholder="如：OpenAI、DeepSeek、Moonshot" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="providerForm.type">
            <el-radio v-for="opt in providerTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="providerForm.model" placeholder="如：gpt-4o、deepseek-chat、qwen3.6-flash" maxlength="100" />
        </el-form-item>
        <el-form-item label="API 地址">
          <el-input v-model="providerForm.baseUrl" placeholder="如：https://api.openai.com/v1" maxlength="255" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="providerForm.apiKey"
            type="password"
            show-password
            :placeholder="editingProviderId !== null ? '留空则不修改' : '输入 API 密钥'"
          />
        </el-form-item>
        <el-form-item label="开发优先级">
          <el-input-number v-model="providerForm.priorityDev" :min="1" :max="99" />
          <span class="form-hint">数字越小优先级越高</span>
        </el-form-item>
        <el-form-item label="生产优先级">
          <el-input-number v-model="providerForm.priorityProd" :min="1" :max="99" />
          <span class="form-hint">数字越小优先级越高</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="providerSaving" @click="saveProvider">保存</el-button>
      </template>
    </el-dialog>

    <!-- 故障转移编辑对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑故障转移配置" width="640px">
      <el-form :model="editConfig" label-width="120px">
        <el-form-item label="启用故障转移">
          <el-switch v-model="editConfig.enabled" />
          <span class="switch-hint">关闭后仅使用优先级最高的供应商</span>
        </el-form-item>

        <!-- 开发环境优先级 -->
        <el-form-item label="开发环境优先级">
          <div class="priority-edit-section">
            <div class="priority-edit-hint">排列顺序即为调用优先级，第一名为主供应商，其余为兜底</div>
            <div class="priority-edit-list">
              <div
                v-for="(name, idx) in editConfig.devPriority"
                :key="`dev-${idx}`"
                class="priority-edit-item"
              >
                <span class="priority-rank">{{ idx + 1 }}</span>
                <el-tag type="primary" size="small">{{ name }}</el-tag>
                <span v-if="idx === 0" class="role-label">主</span>
                <span v-else class="role-label fallback">兜底</span>
                <div class="priority-actions">
                  <el-button size="small" link :disabled="idx === 0" @click="moveUp('dev', idx)">
                    <el-icon><Top /></el-icon>
                  </el-button>
                  <el-button size="small" link :disabled="idx === editConfig.devPriority.length - 1" @click="moveDown('dev', idx)">
                    <el-icon><Bottom /></el-icon>
                  </el-button>
                  <el-button size="small" link type="danger" @click="removeProvider('dev', idx)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
            <div class="add-provider-row">
              <el-select v-model="devAddSelection" placeholder="选择供应商" size="small" style="width: 200px">
                <el-option
                  v-for="name in availableProviderNames(editConfig.devPriority)"
                  :key="name"
                  :label="name"
                  :value="name"
                />
              </el-select>
              <el-button type="primary" size="small" @click="addProvider('dev', devAddSelection)">添加</el-button>
            </div>
          </div>
        </el-form-item>

        <!-- 生产环境优先级 -->
        <el-form-item label="生产环境优先级">
          <div class="priority-edit-section">
            <div class="priority-edit-hint">排列顺序即为调用优先级，第一名为主供应商，其余为兜底</div>
            <div class="priority-edit-list">
              <div
                v-for="(name, idx) in editConfig.prodPriority"
                :key="`prod-${idx}`"
                class="priority-edit-item"
              >
                <span class="priority-rank">{{ idx + 1 }}</span>
                <el-tag type="success" size="small">{{ name }}</el-tag>
                <span v-if="idx === 0" class="role-label">主</span>
                <span v-else class="role-label fallback">兜底</span>
                <div class="priority-actions">
                  <el-button size="small" link :disabled="idx === 0" @click="moveUp('prod', idx)">
                    <el-icon><Top /></el-icon>
                  </el-button>
                  <el-button size="small" link :disabled="idx === editConfig.prodPriority.length - 1" @click="moveDown('prod', idx)">
                    <el-icon><Bottom /></el-icon>
                  </el-button>
                  <el-button size="small" link type="danger" @click="removeProvider('prod', idx)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
            <div class="add-provider-row">
              <el-select v-model="prodAddSelection" placeholder="选择供应商" size="small" style="width: 200px">
                <el-option
                  v-for="name in availableProviderNames(editConfig.prodPriority)"
                  :key="name"
                  :label="name"
                  :value="name"
                />
              </el-select>
              <el-button type="primary" size="small" @click="addProvider('prod', prodAddSelection)">添加</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="failoverSaving" @click="saveFailoverConfig">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.ai-service {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .header-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--admin-text-primary);
  }

  .header-actions {
    display: flex;
    gap: 8px;
  }
}

.provider-name-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--admin-text-primary);
}

.api-key-text {
  font-size: 13px;
  color: var(--admin-text-muted);
  font-family: 'JetBrains Mono', monospace;
}

.form-hint {
  font-size: 12px;
  color: var(--admin-text-muted);
  margin-left: 8px;
}

.failover-config {
  max-width: 600px;
}

.priority-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.priority-tag {
  font-size: 13px;
}

.user-id-text {
  font-size: 12px;
  color: var(--admin-text-muted);
  margin-left: 4px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

// ---------- 故障转移编辑对话框 ----------
.switch-hint {
  font-size: 12px;
  color: var(--admin-text-muted);
  margin-left: 8px;
}

.priority-edit-section {
  width: 100%;
}

.priority-edit-hint {
  font-size: 12px;
  color: var(--admin-text-muted);
  margin-bottom: 8px;
}

.priority-edit-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}

.priority-edit-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--admin-item-bg);
  border: 1px solid var(--admin-item-border);
  border-radius: 6px;
  transition: background 0.2s;

  &:hover {
    background: var(--admin-item-hover-bg);
  }
}

.priority-rank {
  font-size: 14px;
  font-weight: 700;
  color: #2563EB;
  width: 20px;
  text-align: center;
}

.role-label {
  font-size: 11px;
  color: #2563EB;
  font-weight: 600;

  &.fallback {
    color: var(--admin-text-muted);
  }
}

.priority-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
}

.add-provider-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
