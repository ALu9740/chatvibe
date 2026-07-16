import request from '@/utils/request'
import type { CreateGroupRequest, Group, GroupMember, GroupRole } from '@/types'

/** 后端 ConversationVO 原始结构 */
interface RawConversation {
  id: number | string
  name?: string
  type: number // 1-私聊 2-群聊 3-AI
  avatar?: string
  ownerId?: number | string
  lastMessage?: string
  lastMessageAt?: string
  memberCount?: number
  unreadCount?: number
}

/** 后端 GroupMemberVO 原始结构（含群内角色） */
interface RawGroupMember {
  id: number | string
  email?: string
  nickname?: string
  avatar?: string
  bio?: string
  status?: number
  /** 群内角色: 0-成员 1-管理员 2-群主 */
  role?: number
  joinTime?: string
}

const COLOR_PALETTE = ['#2563EB', '#7C3AED', '#DB2777', '#DC2626', '#EA580C', '#CA8A04', '#16A34A', '#0891B2', '#4F46E5']
function pickColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash = seed.charCodeAt(i) + ((hash << 5) - hash)
  return COLOR_PALETTE[Math.abs(hash) % COLOR_PALETTE.length]
}

/** ConversationVO → Group */
function mapGroup(c: RawConversation): Group {
  return {
    id: String(c.id),
    name: c.name || '群组',
    avatar: c.avatar,
    color: pickColor(String(c.id)),
    members: c.memberCount || 0,
    ownerId: c.ownerId != null ? String(c.ownerId) : undefined
  }
}

/** 群内角色整数 → 前端 GroupRole */
function mapRole(role?: number): GroupRole {
  if (role === 2) return 'owner'
  if (role === 1) return 'admin'
  return 'member'
}

/** GroupMemberVO → GroupMember（使用后端返回的角色和状态） */
function mapMember(m: RawGroupMember): GroupMember {
  const id = String(m.id)
  const status = Number(m.status ?? 0)
  return {
    id,
    name: m.nickname || m.email || '用户',
    avatar: m.avatar,
    color: pickColor(id),
    role: mapRole(m.role),
    status,
    // 兼容旧字段：非离线即视为"在线展示"
    online: status !== 0
  }
}

/**
 * 获取用户加入的群组列表
 * 后端无独立群列表接口，复用会话列表并过滤群聊类型
 */
export function getGroups() {
  return request
    .get<unknown, RawConversation[]>('/chat/conversations')
    .then((list) => (list || []).filter((c) => Number(c.type) === 2).map(mapGroup))
}

/** 获取群组详情 */
export function getGroupDetail(groupId: string | number) {
  return request.get<unknown, RawConversation>(`/group/${groupId}`).then(mapGroup)
}

/** 获取群成员列表 */
export function getGroupMembers(groupId: string | number) {
  return request
    .get<unknown, RawGroupMember[]>(`/group/${groupId}/members`)
    .then((list) => (list || []).map(mapMember))
}

/** 创建群组（memberIds 字符串数组转数字数组以匹配后端 List<Long>） */
export function createGroup(data: CreateGroupRequest) {
  const payload = {
    name: data.name,
    memberIds: data.memberIds.map((id) => Number(id)).filter((n) => !isNaN(n))
  }
  return request.post<unknown, RawConversation>('/group', payload).then(mapGroup)
}

/** 退出群组 */
export function leaveGroup(groupId: string | number) {
  return request.delete<unknown, boolean>(`/group/${groupId}/leave`)
}

/** 移除群成员（仅群主可操作） */
export function removeMember(groupId: string | number, userId: string | number) {
  return request.delete<unknown, boolean>(`/group/${groupId}/members/${userId}`)
}

/** 邀请成员加入群组（后端接收 List<Long> 请求体） */
export function inviteMembers(groupId: string | number, memberIds: string[]) {
  const ids = memberIds.map((id) => Number(id)).filter((n) => !isNaN(n))
  return request.post<unknown, boolean>(`/group/${groupId}/members`, ids)
}
