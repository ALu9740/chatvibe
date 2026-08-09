// ============================================================
// ChatVibe · 管理员后台 API 层
// 所有请求走真实后端接口，后端前缀 /api/admin
// ============================================================
import request from '@/utils/request'
import type {
  DashboardMetrics,
  UserGrowthTrend,
  MessageTrend,
  AiUsageTrend,
  SystemHealth,
  TrendRange,
  AdminUser,
  SystemUser,
  UserQueryParams,
  AuditMessage,
  MessageSearchParams,
  SystemGroup,
  GroupQueryParams,
  AiProviderStatus,
  AiProviderType,
  SaveAiProviderRequest,
  FailoverConfig,
  AiConversationRecord,
  AiConversationMessage,
  Announcement,
  CreateAnnouncementRequest,
  NotificationRecord,
  NotificationRecordQueryParams,
  RateLimiterConfig,
  CircuitBreakerConfig,
  CacheStat,
  EmailConfig,
  OperationLog,
  LogQueryParams
} from '@/types/admin'
import type { PageResult } from '@/types'

// ============================================================
// 仪表盘 & 管理员信息
// ============================================================

/** 获取仪表盘核心指标 */
export function getDashboardMetrics(): Promise<DashboardMetrics> {
  return request.get<unknown, DashboardMetrics>('/admin/dashboard/metrics')
}

/** 获取用户增长趋势 */
export function getUserGrowthTrend(days: TrendRange = 30): Promise<UserGrowthTrend> {
  return request.get<unknown, UserGrowthTrend>('/admin/dashboard/user-growth', { params: { days } })
}

/** 获取消息量与 AI 调用量趋势 */
export function getMessageTrend(days: TrendRange = 7): Promise<MessageTrend> {
  return request.get<unknown, MessageTrend>('/admin/dashboard/message-trend', { params: { days } })
}

/** 获取 AI 用量趋势与供应商占比 */
export function getAiUsageTrend(days: TrendRange = 7): Promise<AiUsageTrend> {
  return request.get<unknown, AiUsageTrend>('/admin/dashboard/ai-usage', { params: { days } })
}

/** 获取系统中间件健康状态 */
export function getSystemHealth(): Promise<SystemHealth> {
  return request.get<unknown, SystemHealth>('/admin/dashboard/health')
}

/** 获取当前管理员信息 */
export function getAdminInfo(): Promise<AdminUser> {
  return request.get<unknown, AdminUser>('/admin/me')
}

// ============================================================
// 用户管理
// ============================================================

export function getUserList(params: UserQueryParams): Promise<PageResult<SystemUser>> {
  return request.get<unknown, PageResult<SystemUser>>('/admin/users', { params })
}

export function banUser(userId: number, type: 'temp' | 'permanent', duration: string, reason: string): Promise<boolean> {
  return request.post<unknown, boolean>(`/admin/users/${userId}/ban`, { type, duration, reason })
}

export function unbanUser(userId: number): Promise<boolean> {
  return request.post<unknown, boolean>(`/admin/users/${userId}/unban`)
}

export function changeUserRole(userId: number, role: string, reason?: string): Promise<boolean> {
  return request.put<unknown, boolean>(`/admin/users/${userId}/role`, { role, reason })
}

export function resetUserPassword(userId: number): Promise<boolean> {
  return request.post<unknown, boolean>(`/admin/users/${userId}/reset-password`)
}

// ============================================================
// 消息审计
// ============================================================

export function searchMessages(params: MessageSearchParams): Promise<PageResult<AuditMessage>> {
  return request.get<unknown, PageResult<AuditMessage>>('/admin/messages/search', { params })
}

export function deleteMessage(messageId: number, reason: string): Promise<boolean> {
  return request.delete<unknown, boolean>(`/admin/messages/${messageId}`, { data: { reason } })
}

// ============================================================
// 群组管理
// ============================================================

export function getGroupList(params: GroupQueryParams): Promise<PageResult<SystemGroup>> {
  return request.get<unknown, PageResult<SystemGroup>>('/admin/groups', { params })
}

export function dissolveGroup(groupId: number, reason: string): Promise<boolean> {
  return request.post<unknown, boolean>(`/admin/groups/${groupId}/dissolve`, { reason })
}

// ============================================================
// AI 服务管理
// ============================================================

export function getAiProviders(): Promise<AiProviderStatus[]> {
  return request.get<unknown, AiProviderStatus[]>('/admin/ai/providers')
}

export function addAiProvider(data: SaveAiProviderRequest): Promise<boolean> {
  return request.post<unknown, boolean>('/admin/ai/providers', data)
}

export function updateAiProvider(id: number, data: SaveAiProviderRequest): Promise<boolean> {
  return request.put<unknown, boolean>(`/admin/ai/providers/${id}`, data)
}

export function deleteAiProvider(id: number): Promise<boolean> {
  return request.delete<unknown, boolean>(`/admin/ai/providers/${id}`)
}

export function testAiProvider(id: number): Promise<{ success: boolean; latency?: number; message: string }> {
  return request.post<unknown, { success: boolean; latency?: number; message: string }>(`/admin/ai/providers/${id}/test`)
}

export function getFailoverConfig(): Promise<FailoverConfig> {
  return request.get<unknown, FailoverConfig>('/admin/ai/failover')
}

export function updateFailoverConfig(config: FailoverConfig): Promise<boolean> {
  return request.put<unknown, boolean>('/admin/ai/failover', config)
}

export function getAiConversations(
  page = 1,
  size = 20,
  search?: string,
  provider?: string
): Promise<PageResult<AiConversationRecord>> {
  return request.get<unknown, PageResult<AiConversationRecord>>('/admin/ai/conversations', {
    params: { page, size, search, provider }
  })
}

export function getAiConversationMessages(conversationId: number): Promise<AiConversationMessage[]> {
  return request.get<unknown, AiConversationMessage[]>(`/admin/ai/conversations/${conversationId}/messages`)
}

// ============================================================
// 通知公告
// ============================================================

export function getAnnouncementList(
  page = 1,
  size = 20,
  keyword?: string
): Promise<PageResult<Announcement>> {
  return request.get<unknown, PageResult<Announcement>>('/admin/announcements', {
    params: { page, size, keyword }
  })
}

export function createAnnouncement(data: CreateAnnouncementRequest): Promise<boolean> {
  return request.post<unknown, boolean>('/admin/announcements', data)
}

export function withdrawAnnouncement(id: number): Promise<boolean> {
  return request.post<unknown, boolean>(`/admin/announcements/${id}/withdraw`)
}

// ============================================================
// 通知发送记录 (5.8.3)
// ============================================================

export function getNotificationRecords(
  params: NotificationRecordQueryParams
): Promise<PageResult<NotificationRecord>> {
  return request.get<unknown, PageResult<NotificationRecord>>('/admin/notifications', { params })
}

// ============================================================
// 系统配置
// ============================================================

export function getRateLimiters(): Promise<RateLimiterConfig[]> {
  return request.get<unknown, RateLimiterConfig[]>('/admin/config/rate-limiters')
}

export function updateRateLimiter(name: string, config: Partial<RateLimiterConfig>): Promise<boolean> {
  return request.put<unknown, boolean>(`/admin/config/rate-limiters/${name}`, config)
}

export function getCircuitBreakers(): Promise<CircuitBreakerConfig[]> {
  return request.get<unknown, CircuitBreakerConfig[]>('/admin/config/circuit-breakers')
}

export function updateCircuitBreaker(name: string, config: Partial<CircuitBreakerConfig>): Promise<boolean> {
  return request.put<unknown, boolean>(`/admin/config/circuit-breakers/${name}`, config)
}

export function getCacheStats(): Promise<CacheStat[]> {
  return request.get<unknown, CacheStat[]>('/admin/config/caches')
}

export function clearCache(name: string): Promise<boolean> {
  return request.delete<unknown, boolean>(`/admin/config/caches/${name}`)
}

export function getEmailConfig(): Promise<EmailConfig> {
  return request.get<unknown, EmailConfig>('/admin/config/email')
}

export function updateEmailConfig(config: EmailConfig): Promise<boolean> {
  return request.put<unknown, boolean>('/admin/config/email', config)
}

export function sendTestEmail(config: EmailConfig): Promise<boolean> {
  return request.post<unknown, boolean>('/admin/config/email/test', config)
}

// ============================================================
// 操作日志
// ============================================================

export function getOperationLogs(params: LogQueryParams): Promise<PageResult<OperationLog>> {
  return request.get<unknown, PageResult<OperationLog>>('/admin/logs', { params })
}
