// ============================================================
// ChatVibe · 管理员后台类型定义
// ============================================================

/** 管理员角色 */
export type AdminRole = 'SUPER_ADMIN' | 'ADMIN' | 'OPERATOR'

/** 管理员用户信息 */
export interface AdminUser {
  id: string | number
  email: string
  nickname: string
  avatar?: string
  role: AdminRole
  lastLoginAt?: string
  lastLoginIp?: string
}

/** 仪表盘核心指标 */
export interface DashboardMetrics {
  totalUsers: number
  onlineUsers: number
  todayNewUsers: number
  todayMessages: number
  todayAiCalls: number
  activeGroups: number
  apiAvailability: number
  avgResponseTime: number
}

/** 趋势图数据点 */
export interface TrendPoint {
  date: string
  value: number
}

/** 用户增长趋势数据 */
export interface UserGrowthTrend {
  dates: string[]
  cumulative: number[]
}

/** 消息量趋势数据 */
export interface MessageTrend {
  dates: string[]
  messages: number[]
  aiCalls: number[]
}

/** AI 用量趋势数据 */
export interface AiUsageTrend {
  dates: string[]
  calls: number[]
  providerBreakdown: { name: string; value: number }[]
}

/** 中间件健康状态 */
export type HealthStatus = 'healthy' | 'warning' | 'error' | 'checking'

/** 单个中间件健康信息 */
export interface MiddlewareHealth {
  name: string
  status: HealthStatus
  /** 关键指标键值对 */
  metrics: { label: string; value: string; warning?: boolean }[]
  /** 状态文案 */
  statusText: string
}

/** 系统健康状态聚合 */
export interface SystemHealth {
  mysql: MiddlewareHealth
  redis: MiddlewareHealth
  rabbitmq: MiddlewareHealth
  minio: MiddlewareHealth
}

/** 指标卡片定义（用于渲染） */
export interface MetricCard {
  key: keyof DashboardMetrics
  label: string
  value: number | string
  unit?: string
  icon: string
  /** 卡片渐变色 */
  gradient: string
  /** 点击跳转路由 */
  link?: string
  /** 数据是否获取失败 */
  failed?: boolean
}

/** 时间范围选项 */
export type TrendRange = 7 | 30 | 90

// ============================================================
// 用户管理
// ============================================================

/** 系统用户状态 */
export type UserStatus = 'normal' | 'banned'

/** 系统用户角色 */
export type UserRole = 'USER' | 'OPERATOR' | 'ADMIN' | 'SUPER_ADMIN'

/** 用户列表项 */
export interface SystemUser {
  id: number
  email: string
  nickname: string
  avatar?: string
  status: UserStatus
  role: UserRole
  createdAt: string
  lastActiveAt?: string
  online?: boolean
  bio?: string
}

/** 用户列表查询参数 */
export interface UserQueryParams {
  keyword?: string
  status?: UserStatus | ''
  role?: UserRole | ''
  page: number
  size: number
}

// ============================================================
// 消息审计
// ============================================================

/** 管理端消息类型 */
export type AdminMessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM' | 'AI'

/** 消息审计列表项 */
export interface AuditMessage {
  id: number
  conversationId: number
  conversationName: string
  conversationType: string
  senderId: number
  senderName: string
  type: AdminMessageType
  content: string
  createdAt: string
  hidden?: boolean
}

/** 消息检索参数 */
export interface MessageSearchParams {
  keyword?: string
  senderId?: string
  conversationId?: string
  type?: AdminMessageType | ''
  startDate?: string
  endDate?: string
  page: number
  size: number
}

// ============================================================
// 群组管理
// ============================================================

/** 群组状态 */
export type GroupStatus = 'normal' | 'dissolved'

/** 群组列表项 */
export interface SystemGroup {
  id: number
  name: string
  avatar?: string
  ownerId: number
  ownerName: string
  memberCount: number
  status: GroupStatus
  createdAt: string
  lastMessageAt?: string
}

/** 群组查询参数 */
export interface GroupQueryParams {
  keyword?: string
  ownerId?: string
  status?: GroupStatus | ''
  page: number
  size: number
}

// ============================================================
// AI 服务管理
// ============================================================

/** AI 供应商类型 */
export type AiProviderType = 'local' | 'cloud'

/** AI 供应商状态 */
export interface AiProviderStatus {
  id: number
  name: string
  type: AiProviderType
  status: 'online' | 'offline' | 'checking'
  model: string
  baseUrl: string
  apiKey: string
  latency: number
  priorityDev: number
  priorityProd: number
  createdAt: string
}

/** 添加/编辑 AI 供应商请求 */
export interface SaveAiProviderRequest {
  id?: number
  name: string
  type: AiProviderType
  model: string
  baseUrl: string
  apiKey: string
  priorityDev: number
  priorityProd: number
}

/** 故障转移配置 */
export interface FailoverConfig {
  enabled: boolean
  devPriority: string[]
  prodPriority: string[]
}

/** AI 对话监控列表项 */
export interface AiConversationRecord {
  id: number
  userId: number
  userNickname: string
  title: string
  provider: string
  model: string
  lastMessageAt: string
  messageCount: number
}

// ============================================================
// 通知公告
// ============================================================

/** 公告状态 */
export type AnnouncementStatus = 'published' | 'withdrawn'

/** 公告列表项 */
export interface Announcement {
  id: number
  title: string
  content: string
  scope: 'all' | 'specified'
  targetCount: number
  status: AnnouncementStatus
  createdAt: string
  createdBy: string
}

/** 创建公告请求 */
export interface CreateAnnouncementRequest {
  title: string
  content: string
  scope: 'all' | 'specified'
  targetUserIds?: number[]
}

// ============================================================
// 系统配置
// ============================================================

/** 限流器配置 */
export interface RateLimiterConfig {
  name: string
  limitForPeriod: number
  limitRefreshPeriod: string
  timeoutDuration: string
}

/** 熔断器配置 */
export interface CircuitBreakerConfig {
  name: string
  failureRateThreshold: number
  slowCallRateThreshold: number
  slowCallDurationThreshold: string
  waitDurationInOpenState: string
  permittedNumberOfCallsInHalfOpenState: number
}

/** 缓存统计 */
export interface CacheStat {
  name: string
  hitRate: number
  size: number
  ttl: string
}

/** 邮件配置 */
export interface EmailConfig {
  host: string
  port: number
  username: string
  fromEmail: string
}

// ============================================================
// 操作日志
// ============================================================

/** 操作类型 */
export type OperationType =
  | 'LOGIN' | 'LOGOUT' | 'USER_BAN' | 'USER_UNBAN'
  | 'ROLE_CHANGE' | 'PASSWORD_RESET' | 'MESSAGE_DELETE'
  | 'GROUP_DISSOLVE' | 'GROUP_TRANSFER' | 'ANNOUNCEMENT_PUBLISH'
  | 'ANNOUNCEMENT_WITHDRAW' | 'RATE_LIMIT_CONFIG' | 'CIRCUIT_BREAKER_CONFIG'
  | 'CACHE_CLEAR' | 'ADMIN_ACCOUNT_MANAGE'

/** 操作日志项 */
export interface OperationLog {
  id: number
  operatorId: number
  operatorEmail: string
  type: OperationType
  detail: string
  ip: string
  createdAt: string
}

/** 日志查询参数 */
export interface LogQueryParams {
  operator?: string
  type?: OperationType | ''
  startDate?: string
  endDate?: string
  page: number
  size: number
}
