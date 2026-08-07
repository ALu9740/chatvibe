<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台 - 消息审计
// 对应 PRD 5.5 消息审计
// 功能：消息检索、查看详情（含图片/文件预览）、删除违规消息
// ============================================================
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchMessages, deleteMessage } from '@/api/admin'
import { resolveUploadUrl } from '@/utils/format'
import type { AuditMessage, MessageSearchParams, AdminMessageType } from '@/types/admin'
import type { PageResult } from '@/types'

const loading = ref(false)
const tableData = ref<AuditMessage[]>([])
const total = ref(0)

const queryParams = reactive<MessageSearchParams>({
  keyword: '',
  senderId: '',
  conversationId: '',
  type: '',
  startDate: '',
  endDate: '',
  page: 1,
  size: 10
})

const typeOptions: { label: string; value: AdminMessageType }[] = [
  { label: '文本', value: 'TEXT' },
  { label: '图片', value: 'IMAGE' },
  { label: '文件', value: 'FILE' },
  { label: '系统', value: 'SYSTEM' },
  { label: 'AI', value: 'AI' }
]

const typeTagMap: Record<AdminMessageType, { text: string; type: string }> = {
  TEXT: { text: '文本', type: '' },
  IMAGE: { text: '图片', type: 'success' },
  FILE: { text: '文件', type: 'warning' },
  SYSTEM: { text: '系统', type: 'info' },
  AI: { text: 'AI', type: 'danger' }
}

// ====== 图片预览 ======
const previewVisible = ref(false)
const previewSrc = ref('')

// ====== 消息详情对话框 ======
const detailVisible = ref(false)
const detailMessage = ref<AuditMessage | null>(null)

// ====== 删除原因对话框 ======
const deleteDialogVisible = ref(false)
const deleteForm = reactive({
  messageId: 0,
  reason: ''
})

// ====== 解析 extra JSON ======
function parseExtra(extra?: string): { fileName?: string; fileSize?: number } | null {
  if (!extra) return null
  try {
    return JSON.parse(extra)
  } catch {
    return null
  }
}

// ====== 文件大小格式化 ======
function formatFileSize(bytes?: number): string {
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

// ====== 文件扩展名 ======
function getFileExt(fileName?: string): string {
  if (!fileName) return 'FILE'
  const parts = fileName.split('.')
  return parts.length > 1 ? parts[parts.length - 1].toUpperCase() : 'FILE'
}

// ====== 解析图片 URL ======
function resolveImageUrl(content: string): string {
  return resolveUploadUrl(content)
}

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<AuditMessage> = await searchMessages(queryParams)
    tableData.value = res.records
    total.value = res.total
  } catch {
    ElMessage.error('加载消息列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.page = 1
  loadData()
}

function handleReset() {
  queryParams.keyword = ''
  queryParams.senderId = ''
  queryParams.conversationId = ''
  queryParams.type = ''
  queryParams.startDate = ''
  queryParams.endDate = ''
  queryParams.page = 1
  loadData()
}

function handlePageChange(page: number) {
  queryParams.page = page
  loadData()
}

function handleSizeChange(size: number) {
  queryParams.size = size
  queryParams.page = 1
  loadData()
}

function openDetail(row: AuditMessage) {
  detailMessage.value = row
  detailVisible.value = true
}

function openDeleteDialog(row: AuditMessage) {
  deleteForm.messageId = row.id
  deleteForm.reason = ''
  deleteDialogVisible.value = true
}

async function confirmDelete() {
  if (!deleteForm.reason.trim()) {
    ElMessage.warning('请输入删除原因')
    return
  }
  try {
    await deleteMessage(deleteForm.messageId, deleteForm.reason)
    ElMessage.success('消息已删除')
    deleteDialogVisible.value = false
    loadData()
  } catch {
    ElMessage.error('操作失败')
  }
}

/** 点击图片预览 */
function clickPreview(content: string) {
  previewSrc.value = resolveImageUrl(content)
  previewVisible.value = true
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="message-audit">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryParams" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="消息内容"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="发送者ID">
          <el-input v-model="queryParams.senderId" placeholder="用户ID" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="会话ID">
          <el-input v-model="queryParams.conversationId" placeholder="会话ID" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 消息表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="消息ID" prop="id" width="90" />
        <el-table-column label="会话" min-width="150">
          <template #default="{ row }">
            <div class="conv-cell">
              <span class="conv-name">{{ row.conversationName }}</span>
              <el-tag size="small" type="info">{{ row.conversationType }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发送者" width="130">
          <template #default="{ row }">
            <span>{{ row.senderName || (row.senderId === 0 ? '系统' : '未知') }}</span>
            <span class="sender-id">(ID: {{ row.senderId }})</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="typeTagMap[row.type as AdminMessageType].type" size="small">
              {{ typeTagMap[row.type as AdminMessageType].text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="280">
          <template #default="{ row }">
            <!-- 图片消息：缩略图预览 -->
            <div v-if="row.type === 'IMAGE'" class="msg-image-cell">
              <img
                :src="resolveImageUrl(row.content)"
                alt="图片消息"
                class="msg-thumbnail"
                @click="clickPreview(row.content)"
              />
            </div>
            <!-- 文件消息：文件卡片 -->
            <div v-else-if="row.type === 'FILE'" class="msg-file-cell">
              <a :href="resolveImageUrl(row.content)" :download="parseExtra(row.extra)?.fileName" target="_blank" class="file-card-mini">
                <span class="file-ext">{{ getFileExt(parseExtra(row.extra)?.fileName) }}</span>
                <div class="file-info-mini">
                  <span class="file-name-mini">{{ parseExtra(row.extra)?.fileName || '未命名文件' }}</span>
                  <span class="file-size-mini">{{ formatFileSize(parseExtra(row.extra)?.fileSize) }}</span>
                </div>
              </a>
            </div>
            <!-- 文本/AI/系统消息 -->
            <span v-else class="msg-content" :class="{ deleted: row.deleted }">{{ row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" prop="createdAt" width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.deleted" type="danger" size="small">已删除</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="!row.deleted"
              type="danger"
              size="small"
              link
              @click="openDeleteDialog(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 图片预览 -->
    <el-dialog v-model="previewVisible" title="图片预览" width="700px" class="image-preview-dialog">
      <img :src="previewSrc" alt="图片预览" class="preview-image" />
    </el-dialog>

    <!-- 消息详情 -->
    <el-dialog v-model="detailVisible" title="消息详情" width="600px">
      <el-descriptions :column="1" border v-if="detailMessage">
        <el-descriptions-item label="消息ID">{{ detailMessage.id }}</el-descriptions-item>
        <el-descriptions-item label="会话">{{ detailMessage.conversationName }} ({{ detailMessage.conversationType }})</el-descriptions-item>
        <el-descriptions-item label="会话ID">{{ detailMessage.conversationId }}</el-descriptions-item>
        <el-descriptions-item label="发送者">{{ detailMessage.senderName }} (ID: {{ detailMessage.senderId }})</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="typeTagMap[detailMessage.type].type" size="small">
            {{ typeTagMap[detailMessage.type].text }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="时间">{{ detailMessage.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailMessage.deleted" type="danger" size="small">已删除</el-tag>
          <el-tag v-else type="success" size="small">正常</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="消息内容">
          <!-- 图片消息 -->
          <div v-if="detailMessage.type === 'IMAGE'" class="detail-image-wrap">
            <img
              :src="resolveImageUrl(detailMessage.content)"
              alt="图片消息"
              class="detail-image"
              @click="clickPreview(detailMessage.content)"
            />
          </div>
          <!-- 文件消息 -->
          <div v-else-if="detailMessage.type === 'FILE'" class="detail-file-wrap">
            <a :href="resolveImageUrl(detailMessage.content)" :download="parseExtra(detailMessage.extra)?.fileName" target="_blank" class="file-card-detail">
              <span class="file-ext-detail">{{ getFileExt(parseExtra(detailMessage.extra)?.fileName) }}</span>
              <div class="file-info-detail">
                <span class="file-name-detail">{{ parseExtra(detailMessage.extra)?.fileName || '未命名文件' }}</span>
                <span class="file-size-detail">{{ formatFileSize(parseExtra(detailMessage.extra)?.fileSize) }}</span>
              </div>
              <span class="file-download-text">下载</span>
            </a>
          </div>
          <!-- 文本/AI/系统消息 -->
          <div v-else class="detail-content" :class="{ deleted: detailMessage.deleted }">{{ detailMessage.content }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 删除对话框 -->
    <el-dialog v-model="deleteDialogVisible" title="删除消息" width="440px">
      <el-form :model="deleteForm" label-width="80px">
        <el-form-item label="消息ID">
          <span style="font-weight: 600">{{ deleteForm.messageId }}</span>
        </el-form-item>
        <el-form-item label="删除原因">
          <el-input v-model="deleteForm.reason" type="textarea" :rows="3" placeholder="请输入删除原因" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmDelete">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.message-audit {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card {
  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}

.table-card {
  :deep(.el-card__body) {
    padding: 16px;
  }
}

.conv-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .conv-name {
    font-size: 14px;
    color: var(--admin-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.sender-id {
  font-size: 12px;
  color: var(--admin-text-muted);
  margin-left: 4px;
}

// ====== 图片缩略图 ======
.msg-image-cell {
  .msg-thumbnail {
    width: 60px;
    height: 60px;
    object-fit: cover;
    border-radius: 6px;
    cursor: pointer;
    border: 1px solid var(--admin-border);
    transition: transform 0.2s, box-shadow 0.2s;

    &:hover {
      transform: scale(1.08);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }
  }
}

// ====== 文件卡片（表格内） ======
.msg-file-cell {
  .file-card-mini {
    display: flex;
    align-items: center;
    gap: 8px;
    text-decoration: none;
    padding: 4px 8px;
    border-radius: 6px;
    border: 1px solid var(--admin-border);
    transition: border-color 0.2s, background 0.2s;
    max-width: 240px;

    &:hover {
      border-color: var(--admin-accent, #2563EB);
      background: var(--admin-hover-bg);
    }
  }

  .file-ext {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 6px;
    background: linear-gradient(135deg, #2563EB, #0EA5E9);
    color: #fff;
    font-size: 9px;
    font-weight: 700;
    flex-shrink: 0;
  }

  .file-info-mini {
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .file-name-mini {
    font-size: 12px;
    color: var(--admin-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 140px;
  }

  .file-size-mini {
    font-size: 11px;
    color: var(--admin-text-muted);
  }
}

.msg-content {
  font-size: 13px;
  color: var(--admin-text-content);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;

  &.deleted {
    color: var(--admin-text-disabled);
    text-decoration: line-through;
  }
}

// ====== 图片预览弹窗 ======
.image-preview-dialog {
  .preview-image {
    width: 100%;
    max-height: 70vh;
    object-fit: contain;
    border-radius: 8px;
  }
}

// ====== 详情弹窗内容 ======
.detail-content {
  font-size: 14px;
  color: var(--admin-text-primary);
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;

  &.deleted {
    color: var(--admin-text-disabled);
    text-decoration: line-through;
  }
}

.detail-image-wrap {
  .detail-image {
    max-width: 100%;
    max-height: 300px;
    border-radius: 8px;
    cursor: pointer;
    border: 1px solid var(--admin-border);
  }
}

.detail-file-wrap {
  .file-card-detail {
    display: flex;
    align-items: center;
    gap: 12px;
    text-decoration: none;
    padding: 12px 16px;
    border-radius: 8px;
    border: 1px solid var(--admin-border);
    transition: border-color 0.2s, background 0.2s;

    &:hover {
      border-color: var(--admin-accent, #2563EB);
      background: var(--admin-hover-bg);
    }
  }

  .file-ext-detail {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 8px;
    background: linear-gradient(135deg, #2563EB, #0EA5E9);
    color: #fff;
    font-size: 11px;
    font-weight: 700;
    flex-shrink: 0;
  }

  .file-info-detail {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 1;
  }

  .file-name-detail {
    font-size: 14px;
    color: var(--admin-text-primary);
    font-weight: 500;
  }

  .file-size-detail {
    font-size: 12px;
    color: var(--admin-text-muted);
  }

  .file-download-text {
    font-size: 13px;
    color: #2563EB;
    font-weight: 500;
  }
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
