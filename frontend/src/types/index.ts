/* ============================================================
 * ChatVibe · 全局类型定义
 * 涵盖用户、会话、消息、好友、群组、AI 等核心模型
 * ============================================================ */

/** 统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页响应 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 用户信息 */
export interface User {
  id: string | number
  nickname: string
  email: string
  avatar?: string
  bio?: string
  /** 兼容字段：与 bio 同义 */
  signature?: string
  status?: number
  role?: string
  online?: boolean
  emailVerified?: boolean
  createdAt?: string
}

/** 登录请求 */
export interface LoginRequest {
  email: string
  password: string
}

/** 注册请求 */
export interface RegisterRequest {
  email: string
  password: string
  code: string
}

/** 登录响应 */
export interface LoginResult {
  accessToken: string
  refreshToken?: string
  tokenType?: string
  expiresIn?: number
  user: User
}

/** 验证码发送请求 */
export interface SendCodeRequest {
  email: string
  scene?: 'register' | 'reset'
}

/** 验证码校验请求 */
export interface VerifyCodeRequest {
  email: string
  code: string
}

/** 会话类型 */
export type ConversationType = 'private' | 'group'

/** 会话项 */
export interface Conversation {
  id: string
  type: ConversationType
  name: string
  avatar?: string
  /** 头像背景色（无图片时使用首字母占位） */
  color?: string
  lastMessage?: string
  lastTime?: string
  /** 最后一条消息类型: 0-文本 1-图片 3-文件 4-系统 */
  lastMessageType?: number
  unread: number
  online?: boolean
  /** 群成员数量（群聊） */
  members?: number
  /** 是否为 AI 助手会话 */
  isAI?: boolean
  /** 目标用户 ID（私聊，即好友用户ID） */
  targetId?: string
  /** 群主用户ID（仅群聊，用于判断当前用户是否为群主） */
  ownerId?: string
  /** 私聊对方在线状态: 0-离线 1-在线 2-忙碌 3-离开 */
  peerStatus?: number
  /** 当前用户在该会话中的角色: 0-成员 1-管理员 2-群主 */
  myRole?: number
  /** 是否消息免打扰: 0-否 1-是 */
  muted?: number
  /** 是否置顶: 0-否 1-是 */
  pinned?: number
  /** 群组是否已被解散（仅群聊，前端临时状态） */
  dissolved?: boolean
}

/** 消息发送者类型 */
export type MessageSender = 'self' | 'other' | 'ai' | 'system'

/** 消息类型 */
export type MessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM' | 'AI'

/** 消息项 */
export interface Message {
  id: string
  /** 会话 ID */
  conversationId: string
  /** 发送者类型（用于渲染） */
  sender: MessageSender
  /** 发送者用户 ID */
  senderId?: string
  /** 发送者昵称（群聊场景） */
  name?: string
  /** 头像首字母 */
  avatar?: string
  /** 头像颜色 */
  color?: string
  /** 消息类型 */
  type: MessageType
  /** 消息内容 */
  content: string
  /** 扩展信息（JSON 字符串，如文件名等） */
  extra?: string
  /** 发送时间 */
  time: string
  /** 是否正在流式输出（AI） */
  streaming?: boolean
  /** 发送状态：sending(发送中) | sent(已送达) | failed(发送失败) */
  sendStatus?: 'sending' | 'sent' | 'failed'
}

/** 发送消息请求 */
export interface SendMessageRequest {
  conversationId: string
  type: MessageType
  content: string
  /** 扩展信息（JSON 字符串，如文件名等） */
  extra?: string
  /** 是否 @AI */
  mentionAI?: boolean
}

/** 好友请求状态 */
export type FriendRequestStatus = 'pending' | 'accepted' | 'rejected'

/** 好友请求项 */
export interface FriendRequest {
  id: string
  /** 请求发起方信息 */
  name: string
  avatar?: string
  color?: string
  info: string
  status?: FriendRequestStatus
}

/** 好友项 */
export interface Friend {
  id: string
  name: string
  avatar?: string
  color?: string
  online?: boolean
  signature?: string
  email?: string
}

/** 群组角色 */
export type GroupRole = 'owner' | 'admin' | 'member' | 'AI'

/** 群成员 */
export interface GroupMember {
  id: string
  name: string
  avatar?: string
  color?: string
  role: GroupRole
  online?: boolean
  /** 在线状态: 0-离线 1-在线 2-忙碌 3-离开 */
  status?: number
  isAI?: boolean
}

/** 群组信息 */
export interface Group {
  id: string
  name: string
  avatar?: string
  color?: string
  members: number
  ownerId?: string
  notice?: string
}

/** 创建群组请求 */
export interface CreateGroupRequest {
  name: string
  avatar?: string
  memberIds: string[]
}

/** AI 对话会话 */
export interface AiConversation {
  id: string
  title: string
  messages: Message[]
  /** 是否正在生成回复 */
  generating: boolean
}

/** AI 流式回复片段 */
export interface AiStreamChunk {
  conversationId: string
  delta: string
  done: boolean
}

/** WebSocket 接收的实时消息 */
export interface WsMessage {
  conversationId: string
  message: Message
}

/** 通知偏好设置 */
export interface NotificationPreferences {
  desktop: boolean
  sound: boolean
  aiAlert: boolean
}

/** 消息通知类型 */
export type NotificationType =
  | 'SYSTEM'
  | 'FRIEND_REQUEST'
  | 'FRIEND_ACCEPT'
  | 'FRIEND_DELETE'
  | 'GROUP_INVITE'
  | 'GROUP_REMOVE'
  | 'GROUP_DISSOLVE'
  | 'GROUP_TRANSFER'

/** 消息通知项 */
export interface NotificationItem {
  id: string
  type: NotificationType
  typeDesc: string
  title: string
  content: string
  extra?: string
  isRead: boolean
  createdAt: string
}
