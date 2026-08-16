<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台 - 群组管理
// 对应 PRD 5.6 群组管理
// 功能：群组列表检索、解散群组
// ============================================================
import { ref, reactive, onMounted } from 'vue'
import { toast } from '@/utils/toast'
import { getGroupList, dissolveGroup } from '@/api/admin'
import { isAvatarUrl, resolveUploadUrl } from '@/utils/format'
import type { SystemGroup, GroupQueryParams, GroupStatus } from '@/types/admin'
import type { PageResult } from '@/types'

const loading = ref(false)
const tableData = ref<SystemGroup[]>([])
const total = ref(0)

const queryParams = reactive<GroupQueryParams>({
  keyword: '',
  ownerId: '',
  status: '',
  page: 1,
  size: 10
})

const statusOptions: { label: string; value: GroupStatus }[] = [
  { label: '正常', value: 'normal' },
  { label: '已解散', value: 'dissolved' }
]

// 解散对话框
const dissolveDialogVisible = ref(false)
const dissolveForm = reactive({
  groupId: 0,
  groupName: '',
  reason: ''
})

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<SystemGroup> = await getGroupList(queryParams)
    tableData.value = res.records
    total.value = res.total
  } catch {
    toast.error('加载群组列表失败')
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
  queryParams.ownerId = ''
  queryParams.status = ''
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

function openDissolveDialog(row: any) {
  dissolveForm.groupId = row.id
  dissolveForm.groupName = row.name
  dissolveForm.reason = ''
  dissolveDialogVisible.value = true
}

async function confirmDissolve() {
  if (!dissolveForm.reason.trim()) {
    toast.warning('请输入解散原因')
    return
  }
  try {
    await dissolveGroup(dissolveForm.groupId, dissolveForm.reason)
    toast.success('群组已解散')
    dissolveDialogVisible.value = false
    loadData()
  } catch {
    toast.error('操作失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="group-manage">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryParams" @submit.prevent>
        <el-form-item label="群组名">
          <el-input
            v-model="queryParams.keyword"
            placeholder="群组名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="群主ID">
          <el-input v-model="queryParams.ownerId" placeholder="用户ID" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 群组表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="群组ID" prop="id" width="90" />
        <el-table-column label="群组名称" min-width="160">
          <template #default="{ row }">
            <div class="group-cell">
              <el-avatar :size="32" class="group-avatar" :src="isAvatarUrl(row.avatar) ? resolveUploadUrl(row.avatar) : undefined">{{ isAvatarUrl(row.avatar) ? '' : row.name?.charAt(0) }}</el-avatar>
              <span class="group-name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="群主" min-width="140">
          <template #default="{ row }">
            <span>{{ row.ownerName }}</span>
            <span class="owner-id">(ID: {{ row.ownerId }})</span>
          </template>
        </el-table-column>
        <el-table-column label="成员数" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.memberCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'normal' ? 'success' : 'danger'" size="small">
              {{ row.status === 'normal' ? '正常' : '已解散' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createdAt" width="160" />
        <el-table-column label="最后消息" prop="lastMessageAt" width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'normal'"
              type="danger"
              size="small"
              link
              @click="openDissolveDialog(row)"
            >解散</el-button>
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

    <!-- 解散对话框 -->
    <el-dialog v-model="dissolveDialogVisible" title="解散群组" width="460px">
      <el-form :model="dissolveForm" label-width="80px">
        <el-form-item label="群组">
          <span style="font-weight: 600">{{ dissolveForm.groupName }}</span>
        </el-form-item>
        <el-form-item label="解散原因">
          <el-input v-model="dissolveForm.reason" type="textarea" :rows="3" placeholder="请输入解散原因" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dissolveDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmDissolve">确认解散</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.group-manage {
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

.group-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.group-avatar {
  background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
  color: #fff;
  flex-shrink: 0;
}

.group-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--admin-text-primary);
}

.owner-id {
  font-size: 12px;
  color: var(--admin-text-muted);
  margin-left: 4px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
