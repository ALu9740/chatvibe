import request from '@/utils/request'
import type { Conversation, Message, SendMessageRequest } from '@/types'

/** 后端消息类型整数 ↔ 前端字符串映射 */
const TYPE_TO_INT: Record<string, number> = {
  TEXT: 0,
  IMAGE: 1,
  FILE: 3,
  SYSTEM: 4,
  AI: 0
}

/** 获取会话列表 */
export function getConversations() {
  return request.get<unknown, Conversation[]>('/chat/conversations')
}

/** 获取指定会话的消息记录 */
export function getMessages(conversationId: string | number, lastId?: number, size = 20) {
  return request.get<unknown, Message[]>(`/chat/messages/${conversationId}`, {
    params: { lastId, size }
  })
}

/** 发送消息 */
export function sendMessage(data: SendMessageRequest) {
  const extra = data.extra
    ? data.mentionAI
      ? JSON.stringify({ ...JSON.parse(data.extra), mentionAI: true })
      : data.extra
    : data.mentionAI
      ? JSON.stringify({ mentionAI: true })
      : undefined
  const payload = {
    conversationId: data.conversationId,
    type: TYPE_TO_INT[data.type] ?? 0,
    content: data.content,
    extra
  }
  return request.post<unknown, Message>('/chat/message', payload)
}

/** 标记会话已读 */
export function markRead(conversationId: string | number) {
  return request.put<unknown, boolean>(`/chat/read/${conversationId}`)
}

/** 切换会话消息免打扰（返回切换后的状态：true=已免打扰） */
export function toggleMute(conversationId: string | number) {
  return request.put<unknown, boolean>(`/chat/mute/${conversationId}`)
}

/** 切换会话置顶（返回切换后的状态：true=已置顶） */
export function togglePin(conversationId: string | number) {
  return request.put<unknown, boolean>(`/chat/pin/${conversationId}`)
}

/** 删除会话 */
export function deleteConversation(conversationId: string) {
  return request.delete<unknown, boolean>(`/chat/conversations/${conversationId}`)
}

/** 隐藏（删除）单条消息（仅对当前用户隐藏） */
export function hideMessage(messageId: string) {
  return request.delete<unknown, boolean>(`/chat/messages/${messageId}`)
}

/** 获取当前用户的群组会话列表（含已从会话列表删除但未退出群组的） */
export function getMyGroupConversations() {
  return request.get<unknown, Conversation[]>('/chat/conversations/groups/my')
}

/** 重新加入群聊会话（恢复会话列表显示） */
export function rejoinConversation(conversationId: string) {
  return request.post<unknown, Conversation>(`/chat/conversations/${conversationId}/rejoin`)
}

/** 创建私聊会话 */
export function createPrivateConversation(targetUserId: string) {
  return request.post<unknown, Conversation>('/chat/conversations/private', { targetUserId })
}

/** 清空当前用户在指定会话中的聊天记录（仅对当前用户隐藏，其他成员仍可见） */
export function clearHistory(conversationId: string) {
  return request.delete<unknown, boolean>(`/chat/conversations/${conversationId}/history`)
}
