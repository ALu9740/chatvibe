<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台 - 操作日志
// 对应 PRD 5.10 操作日志
// 功能：操作日志检索、日期范围筛选、查看详情、导出Excel
// ============================================================
import { ref, reactive, onMounted } from 'vue'
import { toast } from '@/utils/toast'
import { getOperationLogs, exportOperationLogs } from '@/api/admin'
import type { OperationLog, LogQueryParams, OperationType } from '@/types/admin'
import type { PageResult } from '@/types'
import * as XLSX from 'xlsx'

const loading = ref(false)
const exporting = ref(false)
const tableData = ref<OperationLog[]>([])
const total = ref(0)

const queryParams = reactive<LogQueryParams>({
  operator: '',
  type: '',
  startDate: '',
  endDate: '',
  page: 1,
  size: 10
})

/** 日期范围（el-date-picker daterange 绑定值 [start, end]） */
const dateRange = ref<[string, string] | null>(null)

const typeOptions: { label: string; value: OperationType }[] = [
  { label: '登录', value: 'LOGIN' },
  { label: '登出', value: 'LOGOUT' },
  { label: '封禁用户', value: 'USER_BAN' },
  { label: '解封用户', value: 'USER_UNBAN' },
  { label: '角色变更', value: 'ROLE_CHANGE' },
  { label: '密码重置', value: 'PASSWORD_RESET' },
  { label: '删除消息', value: 'MESSAGE_DELETE' },
  { label: '解散群组', value: 'GROUP_DISSOLVE' },
  { label: '转让群主', value: 'GROUP_TRANSFER' },
  { label: '发布公告', value: 'ANNOUNCEMENT_PUBLISH' },
  { label: '撤回公告', value: 'ANNOUNCEMENT_WITHDRAW' },
  { label: '限流配置', value: 'RATE_LIMIT_CONFIG' },
  { label: '熔断配置', value: 'CIRCUIT_BREAKER_CONFIG' },
  { label: '清除缓存', value: 'CACHE_CLEAR' },
  { label: '管理员账号管理', value: 'ADMIN_ACCOUNT_MANAGE' },
  { label: '添加AI供应商', value: 'AI_PROVIDER_ADD' },
  { label: '更新AI供应商', value: 'AI_PROVIDER_UPDATE' },
  { label: '删除AI供应商', value: 'AI_PROVIDER_DELETE' },
  { label: '故障转移配置', value: 'FAILOVER_CONFIG' },
  { label: '邮件配置', value: 'EMAIL_CONFIG' }
]

const typeTextMap: Record<OperationType, string> = {
  LOGIN: '登录',
  LOGOUT: '登出',
  USER_BAN: '封禁用户',
  USER_UNBAN: '解封用户',
  ROLE_CHANGE: '角色变更',
  PASSWORD_RESET: '密码重置',
  MESSAGE_DELETE: '删除消息',
  GROUP_DISSOLVE: '解散群组',
  GROUP_TRANSFER: '转让群主',
  ANNOUNCEMENT_PUBLISH: '发布公告',
  ANNOUNCEMENT_WITHDRAW: '撤回公告',
  RATE_LIMIT_CONFIG: '限流配置',
  CIRCUIT_BREAKER_CONFIG: '熔断配置',
  CACHE_CLEAR: '清除缓存',
  ADMIN_ACCOUNT_MANAGE: '管理员账号管理',
  AI_PROVIDER_ADD: '添加AI供应商',
  AI_PROVIDER_UPDATE: '更新AI供应商',
  AI_PROVIDER_DELETE: '删除AI供应商',
  AI_PROVIDER_MANAGE: 'AI供应商管理',
  FAILOVER_CONFIG: '故障转移配置',
  EMAIL_CONFIG: '邮件配置'
}

const typeTagType: Record<OperationType, any> = {
  LOGIN: 'success',
  LOGOUT: 'info',
  USER_BAN: 'danger',
  USER_UNBAN: 'success',
  ROLE_CHANGE: 'warning',
  PASSWORD_RESET: 'warning',
  MESSAGE_DELETE: 'danger',
  GROUP_DISSOLVE: 'danger',
  GROUP_TRANSFER: 'warning',
  ANNOUNCEMENT_PUBLISH: 'primary',
  ANNOUNCEMENT_WITHDRAW: 'info',
  RATE_LIMIT_CONFIG: 'warning',
  CIRCUIT_BREAKER_CONFIG: 'warning',
  CACHE_CLEAR: 'info',
  ADMIN_ACCOUNT_MANAGE: 'danger',
  AI_PROVIDER_ADD: 'primary',
  AI_PROVIDER_UPDATE: 'warning',
  AI_PROVIDER_DELETE: 'danger',
  AI_PROVIDER_MANAGE: 'primary',
  FAILOVER_CONFIG: 'warning',
  EMAIL_CONFIG: 'warning'
}

/** 日期范围变更时同步到 queryParams */
function handleDateChange(val: [string, string] | null) {
  if (val && val.length === 2) {
    queryParams.startDate = val[0]
    queryParams.endDate = val[1]
  } else {
    queryParams.startDate = ''
    queryParams.endDate = ''
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<OperationLog> = await getOperationLogs(queryParams)
    tableData.value = res.records
    total.value = res.total
  } catch {
    toast.error('加载操作日志失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.page = 1
  loadData()
}

function handleReset() {
  queryParams.operator = ''
  queryParams.type = ''
  queryParams.startDate = ''
  queryParams.endDate = ''
  dateRange.value = null
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

/** 导出当前筛选条件下的操作日志为 Excel */
async function handleExport() {
  exporting.value = true
  try {
    const logs: OperationLog[] = await exportOperationLogs({
      operator: queryParams.operator,
      type: queryParams.type,
      startDate: queryParams.startDate,
      endDate: queryParams.endDate
    })

    if (logs.length === 0) {
      toast.warning('没有可导出的数据')
      return
    }

    // 转换为 Excel 行数据（使用中文表头）
    const rows = logs.map(log => ({
      '日志ID': log.id,
      '操作类型': typeTextMap[log.type] || log.type,
      '操作者邮箱': log.operatorEmail,
      '操作者ID': log.operatorId,
      '操作详情': log.detail,
      'IP地址': log.ip,
      '操作时间': log.createdAt
    }))

    const ws = XLSX.utils.json_to_sheet(rows)
    // 设置列宽
    ws['!cols'] = [
      { wch: 8 },   // 日志ID
      { wch: 16 },  // 操作类型
      { wch: 24 },  // 操作者邮箱
      { wch: 10 },  // 操作者ID
      { wch: 50 },  // 操作详情
      { wch: 16 },  // IP地址
      { wch: 20 }   // 操作时间
    ]

    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '操作日志')

    const now = new Date()
    const dateStr = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
    XLSX.writeFile(wb, `操作日志_${dateStr}.xlsx`)

    toast.success('导出成功', `共导出 ${logs.length} 条记录`)
  } catch {
    toast.error('导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="operation-log">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryParams" @submit.prevent>
        <el-form-item label="操作者">
          <el-input
            v-model="queryParams.operator"
            placeholder="操作者邮箱"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="queryParams.type" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" :loading="exporting" @click="handleExport">
            <el-icon><Download /></el-icon>&nbsp;导出Excel
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 日志表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="日志ID" prop="id" width="80" />
        <el-table-column label="操作类型" width="130">
          <template #default="{ row }">
            <el-tag :type="typeTagType[row.type as OperationType]" size="small">
              {{ typeTextMap[row.type as OperationType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作者" min-width="180">
          <template #default="{ row }">
            <div class="operator-cell">
              <span class="operator-email">{{ row.operatorEmail }}</span>
              <span class="operator-id">ID: {{ row.operatorId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作详情" min-width="320">
          <template #default="{ row }">
            <span class="log-detail">{{ row.detail }}</span>
          </template>
        </el-table-column>
        <el-table-column label="IP地址" prop="ip" width="130" />
        <el-table-column label="时间" prop="createdAt" width="160" />
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
  </div>
</template>

<style scoped lang="scss">
.operation-log {
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

.operator-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;

  .operator-email {
    font-size: 14px;
    font-weight: 600;
    color: var(--admin-text-primary);
  }

  .operator-id {
    font-size: 12px;
    color: var(--admin-text-muted);
  }
}

.log-detail {
  font-size: 13px;
  color: var(--admin-text-content);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
