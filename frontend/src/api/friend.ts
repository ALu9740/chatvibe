import request from '@/utils/request'
import type { Friend, FriendRequest, FriendRequestStatus } from '@/types'

/** 后端 UserVO 原始结构 */
interface RawUser {
  id: number | string
  email?: string
  nickname?: string
  avatar?: string
  bio?: string
  status?: number
  role?: string
  createdAt?: string
}

/** 后端 FriendRequestVO 原始结构 */
interface RawFriendRequest {
  id: number | string
  fromUid: number | string
  toUid: number | string
  message?: string
  status: number // 0-待处理 1-已接受 2-已拒绝
  createdAt?: string
  fromUser?: RawUser
  toUser?: RawUser
}

/** 头像背景色板 */
const COLOR_PALETTE = ['#2563EB', '#7C3AED', '#DB2777', '#DC2626', '#EA580C', '#CA8A04', '#16A34A', '#0891B2', '#4F46E5']
function pickColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash = seed.charCodeAt(i) + ((hash << 5) - hash)
  return COLOR_PALETTE[Math.abs(hash) % COLOR_PALETTE.length]
}

/** UserVO → Friend */
function mapFriend(u: RawUser): Friend {
  return {
    id: String(u.id),
    name: u.nickname || u.email || '用户',
    avatar: u.avatar,
    color: pickColor(String(u.id)),
    online: u.status === 1,
    signature: u.bio,
    email: u.email
  }
}

const STATUS_MAP: FriendRequestStatus[] = ['pending', 'accepted', 'rejected']

/** 将后端时间字符串格式化为简洁显示（MM-DD HH:mm） */
function formatRequestTime(raw?: string): string {
  if (!raw) return ''
  const d = new Date(raw.replace(' ', 'T'))
  if (isNaN(d.getTime())) return raw
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi}`
}

/**
 * FriendRequestVO → FriendRequest
 * @param type 'received' 收到的请求（展示发起方信息）| 'sent' 已发送的请求（展示接收方信息）
 */
function mapFriendRequest(r: RawFriendRequest, type: 'received' | 'sent'): FriendRequest {
  const counterpart = type === 'received' ? r.fromUser : r.toUser
  const counterpartUid = type === 'received' ? r.fromUid : r.toUid
  const email = counterpart?.email || ''
  const time = formatRequestTime(r.createdAt)
  const info = type === 'received'
    ? `来自 ${email} · ${time}`
    : `请求添加 ${email} · ${time}`
  return {
    id: String(r.id),
    name: counterpart?.nickname || email || '用户',
    avatar: counterpart?.avatar,
    color: pickColor(String(counterpartUid)),
    info,
    status: STATUS_MAP[r.status] || 'pending'
  }
}

/** 获取好友列表 */
export function getFriends() {
  return request
    .get<unknown, RawUser[]>('/friend/list')
    .then((list) => (list || []).map(mapFriend))
}

/** 通过邮箱搜索用户 */
export function searchUser(keyword: string) {
  return request
    .get<unknown, RawUser[]>('/friend/search', { params: { keyword } })
    .then((list) => (list || []).map(mapFriend))
}

/** 发送好友请求（toUid 为目标用户 ID） */
export function sendFriendRequest(targetUserId: string | number, message?: string) {
  return request.post<unknown, boolean>('/friend/request', null, {
    params: { toUid: targetUserId, message }
  })
}

/** 获取收到的好友请求 */
export function getPendingRequests() {
  return request
    .get<unknown, RawFriendRequest[]>('/friend/requests/received')
    .then((list) => (list || []).map((r) => mapFriendRequest(r, 'received')))
}

/** 获取已发送的好友请求 */
export function getSentRequests() {
  return request
    .get<unknown, RawFriendRequest[]>('/friend/requests/sent')
    .then((list) => (list || []).map((r) => mapFriendRequest(r, 'sent')))
}

/** 接受好友请求 */
export function acceptFriendRequest(requestId: string | number) {
  return request.put<unknown, boolean>(`/friend/request/${requestId}/accept`)
}

/** 拒绝好友请求 */
export function rejectFriendRequest(requestId: string | number) {
  return request.put<unknown, boolean>(`/friend/request/${requestId}/reject`)
}

/** 删除好友 */
export function deleteFriend(friendId: string | number) {
  return request.delete<unknown, boolean>(`/friend/${friendId}`)
}
