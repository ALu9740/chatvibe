<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台 - 通知公告
// 对应 PRD 5.8 通知公告
// 功能：
//   5.8.1 系统公告创建与发布（MQ异步、用户搜索、预览）
//   5.8.2 公告历史与撤回（搜索、权限控制、撤回删通知）
//   5.8.3 通知发送记录（只读查询、多条件筛选）
// ============================================================
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAnnouncementList,
  createAnnouncement,
  withdrawAnnouncement,
  getNotificationRecords,
  getUserList
} from '@/api/admin'
import type {
  Announcement,
  CreateAnnouncementRequest,
  NotificationRecord,
  NotificationRecordQueryParams,
  SystemUser
} from '@/types/admin'
import { NOTIFICATION_TYPE_OPTIONS } from '@/types/admin'
import type { PageResult } from '@/types'

// ============================================================
// Tab 切换
// ============================================================
const activeTab = ref<'announcement' | 'records'>('announcement')

// ============================================================
// 系统公告 Tab
// ============================================================
const annLoading = ref(false)
const annTableData = ref<Announcement[]>([])
const annTotal = ref(0)
const annPage = ref(1)
const annSize = ref(10)
const annKeyword = ref('')

// 发布公告对话框
const createDialogVisible = ref(false)
const previewDialogVisible = ref(false)
const createForm = reactive<CreateAnnouncementRequest>({
  title: '',
  content: '',
  scope: 'all',
  targetUserIds: []
})
const creating = ref(false)

// 用户搜索（el-select remote）
const userOptions = ref<SystemUser[]>([])
const userSearchLoading = ref(false)

async function loadAnnouncements() {
  annLoading.value = true
  try {
    const res: PageResult<Announcement> = await getAnnouncementList(
      annPage.value,
      annSize.value,
      annKeyword.value || undefined
    )
    annTableData.value = res.records
    annTotal.value = res.total
  } catch {
    ElMessage.error('加载公告列表失败')
  } finally {
    annLoading.value = false
  }
}

function handleAnnPageChange(p: number) {
  annPage.value = p
  loadAnnouncements()
}

function searchAnnouncements() {
  annPage.value = 1
  loadAnnouncements()
}

function openCreateDialog() {
  createForm.title = ''
  createForm.content = ''
  createForm.scope = 'all'
  createForm.targetUserIds = []
  userOptions.value = []
  createDialogVisible.value = true
}

/** 远程搜索用户 */
async function searchUsers(query: string) {
  if (!query || query.trim().length < 1) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const res = await getUserList({ keyword: query.trim(), page: 1, size: 20 })
    userOptions.value = res.records
  } catch {
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

/** 预览公告 */
function handlePreview() {
  if (!createForm.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  if (!createForm.content.trim()) {
    ElMessage.warning('请输入公告内容')
    return
  }
  if (createForm.scope === 'specified' && (!createForm.targetUserIds || createForm.targetUserIds.length === 0)) {
    ElMessage.warning('指定用户范围请至少选择一名用户')
    return
  }
  previewDialogVisible.value = true
}

/** 确认发布 */
async function confirmCreate() {
  if (!createForm.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  if (!createForm.content.trim()) {
    ElMessage.warning('请输入公告内容')
    return
  }
  if (createForm.scope === 'specified' && (!createForm.targetUserIds || createForm.targetUserIds.length === 0)) {
    ElMessage.warning('指定用户范围请至少选择一名用户')
    return
  }
  creating.value = true
  try {
    await createAnnouncement(createForm)
    ElMessage.success('公告已发布，通知正在异步发送中')
    createDialogVisible.value = false
    previewDialogVisible.value = false
    loadAnnouncements()
  } catch {
    ElMessage.error('发布失败')
  } finally {
    creating.value = false
  }
}

async function handleWithdraw(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定撤回公告"${row.title}"吗？撤回后通知记录将从用户通知列表中移除，此操作不可撤销。`,
      '撤回确认',
      { type: 'warning', confirmButtonText: '确认撤回', cancelButtonText: '取消' }
    )
    await withdrawAnnouncement(row.id)
    ElMessage.success('公告已撤回')
    loadAnnouncements()
  } catch {
    // 用户取消
  }
}

// 公告详情
const annDetailVisible = ref(false)
const selectedAnnouncement = ref<Announcement | null>(null)

function handleAnnDetail(row: any) {
  selectedAnnouncement.value = row
  annDetailVisible.value = true
}

// ============================================================
// 通知发送记录 Tab
// ============================================================
const notifLoading = ref(false)
const notifTableData = ref<NotificationRecord[]>([])
const notifTotal = ref(0)
const notifPage = ref(1)
const notifSize = ref(10)
const notifFilters = reactive<NotificationRecordQueryParams>({
  type: '',
  startDate: '',
  endDate: '',
  keyword: '',
  isRead: '',
  page: 1,
  size: 10
})
const dateRange = ref<[string, string] | null>(null)

async function loadNotifications() {
  notifLoading.value = true
  try {
    const params: NotificationRecordQueryParams = {
      type: notifFilters.type || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
      keyword: notifFilters.keyword || undefined,
      isRead: notifFilters.isRead === '' ? undefined : notifFilters.isRead,
      page: notifPage.value,
      size: notifSize.value
    }
    const res: PageResult<NotificationRecord> = await getNotificationRecords(params)
    notifTableData.value = res.records
    notifTotal.value = res.total
  } catch {
    ElMessage.error('加载通知记录失败')
  } finally {
    notifLoading.value = false
  }
}

function handleNotifPageChange(p: number) {
  notifPage.value = p
  loadNotifications()
}

function handleNotifSizeChange(s: number) {
  notifPage.value = 1
  loadNotifications()
}

function handleAnnSizeChange(s: number) {
  annPage.value = 1
  loadAnnouncements()
}

function searchNotifications() {
  notifPage.value = 1
  loadNotifications()
}

function resetNotifFilters() {
  notifFilters.type = ''
  notifFilters.keyword = ''
  notifFilters.isRead = ''
  dateRange.value = null
  notifPage.value = 1
  loadNotifications()
}

/** 通知类型标签颜色 */
function notifTypeTagType(type: number): any {
  const map: Record<number, any> = {
    1: 'danger',
    2: 'warning',
    3: 'success',
    4: 'info',
    5: 'primary',
    6: 'info',
    7: 'info',
    8: 'warning'
  }
  return map[type] || 'info'
}

// ============================================================
// 初始化
// ============================================================
onMounted(() => {
  loadAnnouncements()
})

/** 切换Tab时按需加载数据 */
function handleTabChange(tab: any) {
  if (tab === 'records') {
    loadNotifications()
  } else if (tab === 'announcement') {
    loadAnnouncements()
  }
}
</script>

<template>
  <div class="notification-view">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- ======================================================== -->
      <!-- Tab 1: 系统公告 -->
      <!-- ======================================================== -->
      <el-tab-pane label="系统公告" name="announcement">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <el-input
                  v-model="annKeyword"
                  placeholder="搜索公告标题"
                  style="width: 240px"
                  clearable
                  @keyup.enter="searchAnnouncements"
                  @clear="searchAnnouncements"
                >
                  <template #append>
                    <el-button @click="searchAnnouncements">
                      <el-icon><Search /></el-icon>
                    </el-button>
                  </template>
                </el-input>
              </div>
              <el-button type="primary" size="small" @click="openCreateDialog">
                <el-icon><Plus /></el-icon>&nbsp;发布公告
              </el-button>
            </div>
          </template>
          <el-table :data="annTableData" v-loading="annLoading" stripe style="width: 100%">
            <el-table-column label="ID" prop="id" width="70" />
            <el-table-column label="标题" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="ann-title">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column label="范围" width="100">
              <template #default="{ row }">
                <el-tag :type="row.scope === 'all' ? 'primary' : 'warning'" size="small">
                  {{ row.scope === 'all' ? '全部用户' : '指定用户' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="目标数" width="90" align="center">
              <template #default="{ row }">{{ row.targetCount }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'published' ? 'success' : 'info'" size="small">
                  {{ row.status === 'published' ? '已发布' : '已撤回' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建人" prop="createdBy" width="170" />
            <el-table-column label="创建时间" prop="createdAt" width="160" />
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  size="small"
                  link
                  @click="handleAnnDetail(row)"
                >详情</el-button>
                <el-button
                  v-if="row.status === 'published'"
                  type="warning"
                  size="small"
                  link
                  @click="handleWithdraw(row)"
                >撤回</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="annPage"
              v-model:page-size="annSize"
              :total="annTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleAnnPageChange"
              @size-change="handleAnnSizeChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- ======================================================== -->
      <!-- Tab 2: 通知发送记录 -->
      <!-- ======================================================== -->
      <el-tab-pane label="通知发送记录" name="records">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span class="header-title">通知发送记录</span>
            </div>
          </template>

          <!-- 筛选条件 -->
          <div class="filter-bar">
            <el-select
              v-model="notifFilters.type"
              placeholder="通知类型"
              clearable
              style="width: 140px"
            >
              <el-option
                v-for="opt in NOTIFICATION_TYPE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 260px; margin-right: 4px"
            />
            <el-select
              v-model="notifFilters.isRead"
              placeholder="已读状态"
              clearable
              style="width: 120px"
            >
              <el-option label="已读" :value="1" />
              <el-option label="未读" :value="0" />
            </el-select>
            <el-input
              v-model="notifFilters.keyword"
              placeholder="昵称/邮箱"
              clearable
              style="width: 160px"
              @keyup.enter="searchNotifications"
            />
            <el-button type="primary" size="small" @click="searchNotifications">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button size="small" @click="resetNotifFilters">重置</el-button>
          </div>

          <el-table :data="notifTableData" v-loading="notifLoading" stripe style="width: 100%; margin-top: 12px">
            <el-table-column label="ID" prop="id" width="70" />
            <el-table-column label="接收用户" min-width="160">
              <template #default="{ row }">
                <div class="user-cell">
                  <span class="user-name">{{ row.userNickname || '未知' }}</span>
                  <span class="user-id-text" v-if="row.userEmail">({{ row.userEmail }})</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag :type="notifTypeTagType(row.type)" size="small">{{ row.typeDesc }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip />
            <el-table-column label="内容" prop="content" min-width="200" show-overflow-tooltip />
            <el-table-column label="已读" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isRead === 1 ? 'success' : 'info'" size="small">
                  {{ row.isRead === 1 ? '已读' : '未读' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="发送时间" prop="createdAt" width="160" />
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="notifPage"
              v-model:page-size="notifSize"
              :total="notifTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleNotifPageChange"
              @size-change="handleNotifSizeChange"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ======================================================== -->
    <!-- 发布公告对话框 -->
    <!-- ======================================================== -->
    <el-dialog v-model="createDialogVisible" title="发布公告" width="600px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="标题">
          <el-input
            v-model="createForm.title"
            placeholder="请输入公告标题"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="createForm.content"
            type="textarea"
            :rows="6"
            placeholder="请输入公告内容（纯文本）"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="发送范围">
          <el-radio-group v-model="createForm.scope">
            <el-radio value="all">全部用户</el-radio>
            <el-radio value="specified">指定用户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="createForm.scope === 'specified'" label="目标用户">
          <el-select
            v-model="createForm.targetUserIds"
            multiple
            filterable
            remote
            reserve-keyword
            placeholder="输入用户昵称或邮箱搜索"
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.nickname || '未知'} (${user.email})`"
              :value="user.id"
            />
          </el-select>
          <div class="select-tip" v-if="createForm.targetUserIds && createForm.targetUserIds.length > 0">
            已选择 {{ createForm.targetUserIds.length }} 人（最多1000人）
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button @click="handlePreview">
          <el-icon><View /></el-icon>&nbsp;预览
        </el-button>
        <el-button type="primary" :loading="creating" @click="confirmCreate">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- ======================================================== -->
    <!-- 公告预览对话框 -->
    <!-- ======================================================== -->
    <el-dialog v-model="previewDialogVisible" title="公告预览" width="500px">
      <div class="preview-container">
        <div class="preview-header">
          <el-icon size="24" color="var(--el-color-primary)"><Bell /></el-icon>
          <span class="preview-label">系统公告</span>
        </div>
        <h3 class="preview-title">{{ createForm.title }}</h3>
        <div class="preview-meta">
          <span>发送范围：{{ createForm.scope === 'all' ? '全部用户' : `指定用户（${createForm.targetUserIds?.length || 0}人）` }}</span>
        </div>
        <el-divider />
        <div class="preview-content">{{ createForm.content }}</div>
      </div>
      <template #footer>
        <el-button @click="previewDialogVisible = false">返回编辑</el-button>
        <el-button type="primary" :loading="creating" @click="confirmCreate">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- ======================================================== -->
    <!-- 公告详情对话框 -->
    <!-- ======================================================== -->
    <el-dialog v-model="annDetailVisible" title="公告详情" width="600px">
      <div class="preview-container" v-if="selectedAnnouncement">
        <div class="preview-header">
          <el-icon size="24" color="var(--el-color-primary)"><Bell /></el-icon>
          <span class="preview-label">系统公告</span>
          <el-tag
            :type="selectedAnnouncement.status === 'published' ? 'success' : 'info'"
            size="small"
            style="margin-left: auto"
          >
            {{ selectedAnnouncement.status === 'published' ? '已发布' : '已撤回' }}
          </el-tag>
        </div>
        <h3 class="preview-title">{{ selectedAnnouncement.title }}</h3>
        <div class="preview-meta">
          <span>发送范围：{{ selectedAnnouncement.scope === 'all' ? '全部用户' : '指定用户' }}</span>
          <span style="margin-left: 16px">目标数：{{ selectedAnnouncement.targetCount }} 人</span>
        </div>
        <div class="preview-meta" style="margin-top: 4px">
          <span>创建人：{{ selectedAnnouncement.createdBy }}</span>
          <span style="margin-left: 16px">创建时间：{{ selectedAnnouncement.createdAt }}</span>
        </div>
        <el-divider />
        <div class="preview-content">{{ selectedAnnouncement.content || '无内容' }}</div>
      </div>
      <template #footer>
        <el-button @click="annDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.notification-view {
  display: flex;
  flex-direction: column;
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
}

.ann-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--admin-text-primary);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .user-name {
    font-size: 14px;
    color: var(--admin-text-primary);
  }

  .user-id-text {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.select-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 预览样式 */
.preview-container {
  padding: 8px 0;

  .preview-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;

    .preview-label {
      font-size: 14px;
      color: var(--el-text-color-secondary);
    }
  }

  .preview-title {
    margin: 0 0 8px 0;
    font-size: 18px;
    font-weight: 600;
    color: var(--admin-text-primary);
  }

  .preview-meta {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .preview-content {
    font-size: 14px;
    line-height: 1.8;
    color: var(--admin-text-primary);
    white-space: pre-wrap;
    word-break: break-word;
  }
}
</style>
