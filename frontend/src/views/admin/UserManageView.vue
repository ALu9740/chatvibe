<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台 - 用户管理
// 对应 PRD 5.4 用户管理
// 功能：用户列表检索、封禁/解封、角色变更、密码重置
// ============================================================
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { toast } from '@/utils/toast'
import { getUserList, banUser, unbanUser, changeUserRole, resetUserPassword } from '@/api/admin'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { isAvatarUrl, resolveUploadUrl } from '@/utils/format'
import type { SystemUser, UserQueryParams, UserStatus, UserRole } from '@/types/admin'
import type { AdminRole } from '@/types/admin'
import type { PageResult } from '@/types'

const adminAuthStore = useAdminAuthStore()

/** 当前登录管理员角色 */
const currentRole = computed<AdminRole>(() => adminAuthStore.admin?.role || 'OPERATOR')

const loading = ref(false)
const tableData = ref<SystemUser[]>([])
const total = ref(0)

const queryParams = reactive<UserQueryParams>({
  keyword: '',
  status: '',
  role: '',
  page: 1,
  size: 10
})

const statusOptions: { label: string; value: UserStatus }[] = [
  { label: '正常', value: 'normal' },
  { label: '已封禁', value: 'banned' }
]

const roleOptions: { label: string; value: UserRole }[] = [
  { label: '普通用户', value: 'USER' },
  { label: '运营', value: 'OPERATOR' },
  { label: '管理员', value: 'ADMIN' },
  { label: '超级管理员', value: 'SUPER_ADMIN' }
]

const roleMap: Record<UserRole, string> = {
  USER: '普通用户',
  OPERATOR: '运营',
  ADMIN: '管理员',
  SUPER_ADMIN: '超级管理员'
}

const roleTagType: Record<UserRole, any> = {
  USER: 'info',
  OPERATOR: 'warning',
  ADMIN: 'success',
  SUPER_ADMIN: 'danger'
}

/** 在线状态映射: 0-离线 1-在线 2-忙碌 3-离开 */
const onlineStatusMap: Record<number, { text: string; dotClass: string }> = {
  0: { text: '离线', dotClass: 'dot-offline' },
  1: { text: '在线', dotClass: 'dot-online' },
  2: { text: '忙碌', dotClass: 'dot-busy' },
  3: { text: '离开', dotClass: 'dot-away' }
}

/** 角色变更下拉可选角色（按当前管理员角色过滤） */
const availableRoles = computed(() => {
  if (currentRole.value === 'SUPER_ADMIN') {
    return roleOptions.filter((r) => r.value !== 'SUPER_ADMIN')
  }
  if (currentRole.value === 'ADMIN') {
    return roleOptions.filter((r) => r.value === 'OPERATOR' || r.value === 'USER')
  }
  return roleOptions
})

// 封禁对话框
const banDialogVisible = ref(false)
const banForm = reactive({
  userId: 0,
  userNickname: '',
  userRole: 'USER' as UserRole,
  type: 'temp' as 'temp' | 'permanent',
  duration: '7',
  reason: ''
})

// 角色变更对话框
const roleDialogVisible = ref(false)
const roleForm = reactive({
  userId: 0,
  userNickname: '',
  role: 'USER' as UserRole,
  reason: ''
})

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<SystemUser> = await getUserList(queryParams)
    tableData.value = res.records
    total.value = res.total
  } catch {
    toast.error('加载用户列表失败')
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
  queryParams.status = ''
  queryParams.role = ''
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

function openBanDialog(row: any) {
  if (currentRole.value === 'ADMIN' && row.role !== 'USER') {
    toast.error('操作失败', '无权限操作')
    return
  }
  banForm.userId = row.id
  banForm.userNickname = row.nickname
  banForm.userRole = row.role
  banForm.type = 'temp'
  banForm.duration = '7'
  banForm.reason = ''
  banDialogVisible.value = true
}

async function confirmBan() {
  if (!banForm.reason.trim()) {
    toast.warning('请输入封禁原因')
    return
  }
  try {
    const duration = banForm.type === 'temp' ? `${banForm.duration}天` : '永久'
    await banUser(banForm.userId, banForm.type, duration, banForm.reason)
    toast.success('已封禁', '封禁邮件已发送至用户邮箱')
    banDialogVisible.value = false
    loadData()
  } catch (err) {
    toast.error('操作失败', (err as Error).message || '操作失败')
  }
}

async function handleUnban(row: any) {
  if (currentRole.value === 'ADMIN' && row.role !== 'USER') {
    toast.error('操作失败', '无权限操作')
    return
  }
  try {
    await ElMessageBox.confirm(`确定解封用户 ${row.nickname} 吗？`, '解封确认', { type: 'warning' })
  } catch {
    return // 用户取消
  }
  try {
    await unbanUser(row.id)
    toast.success('已解封', '解封邮件已发送至用户邮箱')
    loadData()
  } catch (err) {
    toast.error('操作失败', (err as Error).message || '操作失败')
  }
}

function openRoleDialog(row: any) {
  if (currentRole.value === 'ADMIN' && (row.role === 'SUPER_ADMIN' || row.role === 'ADMIN')) {
    toast.error('操作失败', '无权限操作')
    return
  }
  roleForm.userId = row.id
  roleForm.userNickname = row.nickname
  roleForm.role = row.role
  roleForm.reason = ''
  roleDialogVisible.value = true
}

async function confirmRoleChange() {
  try {
    await changeUserRole(roleForm.userId, roleForm.role, roleForm.reason)
    toast.success('角色已更新')
    roleDialogVisible.value = false
    loadData()
  } catch (err) {
    toast.error('操作失败', (err as Error).message || '操作失败')
  }
}

async function handleResetPassword(row: any) {
  if (currentRole.value === 'ADMIN' && row.role === 'SUPER_ADMIN') {
    toast.error('操作失败', '无权限操作')
    return
  }
  try {
    await ElMessageBox.confirm(`确定重置用户 ${row.nickname} 的密码吗？`, '密码重置', { type: 'warning' })
  } catch {
    return // 用户取消
  }
  try {
    await resetUserPassword(row.id)
    toast.success('密码已重置', '新密码已发送至用户邮箱')
  } catch (err) {
    toast.error('操作失败', (err as Error).message || '操作失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="user-manage">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryParams" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="邮箱 / 昵称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="queryParams.role" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="opt in roleOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 用户表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="用户" min-width="160">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="32" class="user-avatar" :src="isAvatarUrl(row.avatar) ? resolveUploadUrl(row.avatar) : undefined">{{ isAvatarUrl(row.avatar) ? '' : row.nickname?.charAt(0) }}</el-avatar>
              <div class="user-info">
                <span class="user-name">{{ row.nickname }}</span>
                <span class="user-email">{{ row.email }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleTagType[row.role as UserRole]" size="small">{{ roleMap[row.role as UserRole] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'normal' ? 'success' : 'danger'" size="small">
              {{ row.status === 'normal' ? '正常' : '已封禁' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="在线" width="80">
          <template #default="{ row }">
            <span class="online-status">
              <span class="online-text">{{ onlineStatusMap[row.onlineStatus]?.text || '离线' }}</span>
              <i class="online-dot" :class="onlineStatusMap[row.onlineStatus]?.dotClass || 'dot-offline'"></i>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" prop="createdAt" width="170" />
        <el-table-column label="最后活跃" prop="lastActiveAt" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'normal'"
              type="danger"
              size="small"
              link
              @click="openBanDialog(row)"
            >封禁</el-button>
            <el-button
              v-else
              type="success"
              size="small"
              link
              @click="handleUnban(row)"
            >解封</el-button>
            <el-button type="primary" size="small" link @click="openRoleDialog(row)">改角色</el-button>
            <el-button type="warning" size="small" link @click="handleResetPassword(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- 封禁对话框 -->
    <el-dialog v-model="banDialogVisible" title="封禁用户" width="480px">
      <el-form :model="banForm" label-width="80px">
        <el-form-item label="用户">
          <span style="font-weight: 600">{{ banForm.userNickname }}</span>
        </el-form-item>
        <el-form-item label="封禁类型">
          <el-radio-group v-model="banForm.type">
            <el-radio value="temp">临时封禁</el-radio>
            <el-radio value="permanent">永久封禁</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="banForm.type === 'temp'" label="封禁时长">
          <el-input v-model="banForm.duration" style="width: 120px">
            <template #append>天</template>
          </el-input>
        </el-form-item>
        <el-form-item label="封禁原因">
          <el-input v-model="banForm.reason" type="textarea" :rows="3" placeholder="请输入封禁原因" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmBan">确认封禁</el-button>
      </template>
    </el-dialog>

    <!-- 角色变更对话框 -->
    <el-dialog v-model="roleDialogVisible" title="变更角色" width="440px">
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="用户">
          <span style="font-weight: 600">{{ roleForm.userNickname }}</span>
        </el-form-item>
        <el-form-item label="新角色">
          <el-select v-model="roleForm.role" style="width: 100%">
            <el-option v-for="opt in availableRoles" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因">
          <el-input v-model="roleForm.reason" type="textarea" :rows="3" placeholder="可选" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRoleChange">确认变更</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.user-manage {
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

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
  min-width: 0;

  .user-name {
    font-size: 14px;
    font-weight: 600;
    color: var(--admin-text-primary);
  }

  .user-email {
    font-size: 12px;
    color: var(--admin-text-muted);
  }
}

.online-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.online-text {
  font-size: 13px;
  color: var(--admin-text-secondary);
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;

  &.dot-online {
    background: #10B981;
    box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
  }

  &.dot-offline {
    background: #94A3B8;
  }

  &.dot-busy {
    background: #EF4444;
    box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.15);
  }

  &.dot-away {
    background: #F59E0B;
    box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.15);
  }
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
