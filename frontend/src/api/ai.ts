import request from '@/utils/request'
import type { AiConversation, Message } from '@/types'

/** 获取 AI 对话列表 */
export function getAiConversations() {
  return request.get<unknown, AiConversation[]>('/ai/conversations')
}

/** 创建新的 AI 对话 */
export function createAiConversation(title?: string) {
  return request.post<unknown, AiConversation>('/ai/conversations', { title })
}

/** 获取 AI 对话历史消息 */
export function getAiMessages(conversationId: string) {
  return request.get<unknown, Message[]>(`/ai/conversations/${conversationId}/messages`)
}

/** 发送 AI 对话请求（非流式，作为兜底） */
export function sendAiMessage(conversationId: string, content: string) {
  return request.post<unknown, Message>(`/ai/conversations/${conversationId}/messages`, { content })
}

/** 删除 AI 对话 */
export function deleteAiConversation(conversationId: string) {
  return request.delete<unknown, boolean>(`/ai/conversations/${conversationId}`)
}
