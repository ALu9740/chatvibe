<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台 - 通知公告
// 对应 PRD 5.8 通知公告
// 功能：公告列表、发布新公告、撤回公告
// ============================================================
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAnnouncementList, createAnnouncement, withdrawAnnouncement } from '@/api/admin'
import type { Announcement, CreateAnnouncementRequest } from '@/types/admin'
import type { PageResult } from '@/types'

const loading = ref(false)
const tableData = ref<Announcement[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

// 发布公告对话框
const createDialogVisible = ref(false)
const createForm = reactive<CreateAnnouncementRequest>({
  title: '',
  content: '',
  scope: 'all',
  targetUserIds: []
})
const targetUserInput = ref('')
const creating = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<Announcement> = await getAnnouncementList(page.value, size.value)
    tableData.value = res.records
    total.value = res.total
  } catch {
    ElMessage.error('加载公告列表失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(p: number) {
  page.value = p
  loadData()
}

function openCreateDialog() {
  createForm.title = ''
  createForm.content = ''
  createForm.scope = 'all'
  createForm.targetUserIds = []
  targetUserInput.value = ''
  createDialogVisible.value = true
}

function addTargetUser() {
  const id = Number(targetUserInput.value.trim())
  if (!id || id <= 0) {
    ElMessage.warning('请输入有效的用户ID')
    return
  }
  if (createForm.targetUserIds!.includes(id)) {
    ElMessage.warning('该用户ID已添加')
    return
  }
  createForm.targetUserIds!.push(id)
  targetUserInput.value = ''
}

function removeTargetUser(id: number) {
  const idx = createForm.targetUserIds!.indexOf(id)
  if (idx >= 0) createForm.targetUserIds!.splice(idx, 1)
}

async function confirmCreate() {
  if (!createForm.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  if (!createForm.content.trim()) {
    ElMessage.warning('请输入公告内容')
    return
  }
  if (createForm.scope === 'specified' && createForm.targetUserIds!.length === 0) {
    ElMessage.warning('指定用户范围请至少添加一个用户ID')
    return
  }
  creating.value = true
  try {
    await createAnnouncement(createForm)
    ElMessage.success('公告已发布')
    createDialogVisible.value = false
    loadData()
  } catch {
    ElMessage.error('发布失败')
  } finally {
    creating.value = false
  }
}

async function handleWithdraw(row: Announcement) {
  try {
    await ElMessageBox.confirm(`确定撤回公告"${row.title}"吗？`, '撤回确认', { type: 'warning' })
    await withdrawAnnouncement(row.id)
    ElMessage.success('公告已撤回')
    loadData()
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="notification-view">
    <!-- 公告列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">通知公告列表</span>
          <el-button type="primary" size="small" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>&nbsp;发布公告
          </el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
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
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
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
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 发布公告对话框 -->
    <el-dialog v-model="createDialogVisible" title="发布公告" width="560px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="createForm.title" placeholder="公告标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="createForm.content"
            type="textarea"
            :rows="5"
            placeholder="公告内容"
            maxlength="1000"
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
          <div class="target-users">
            <div class="target-input">
              <el-input v-model="targetUserInput" placeholder="输入用户ID" style="width: 160px" @keyup.enter="addTargetUser" />
              <el-button type="primary" size="small" @click="addTargetUser">添加</el-button>
            </div>
            <div class="target-list" v-if="createForm.targetUserIds!.length">
              <el-tag
                v-for="id in createForm.targetUserIds"
                :key="id"
                closable
                size="small"
                @close="removeTargetUser(id)"
                class="target-tag"
              >用户ID: {{ id }}</el-tag>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="confirmCreate">发布公告</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.notification-view {
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
}

.ann-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--admin-text-primary);
}

.target-users {
  width: 100%;
}

.target-input {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.target-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.target-tag {
  font-size: 13px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
