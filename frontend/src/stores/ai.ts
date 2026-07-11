import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AiConversation, AiStreamChunk, Message } from '@/types'
import { generateId } from '@/utils/format'

/** AI 状态 store：管理 AI 对话列表与流式回复缓存 */
export const useAiStore = defineStore('ai', () => {
  // AI 对话列表
  const conversations = ref<AiConversation[]>([])
  // 当前激活的 AI 对话 ID
  const currentId = ref<string>('')
  // 流式输出缓存（按会话 ID）
  const streamCache = ref<Record<string, string>>({})

  /** 获取当前对话 */
  function getCurrent(): AiConversation | undefined {
    return conversations.value.find((c) => c.id === currentId.value)
  }

  /** 设置当前 AI 对话 */
  function setCurrent(id: string): void {
    currentId.value = id
  }

  /** 加载 AI 对话列表 */
  function setConversations(list: AiConversation[]): void {
    conversations.value = list
  }

  /** 创建一条 AI 对话 */
  function createConversation(title = '新对话'): AiConversation {
    const conv: AiConversation = {
      id: generateId('ai'),
      title,
      messages: [],
      generating: false
    }
    conversations.value.unshift(conv)
    currentId.value = conv.id
    return conv
  }

  /** 追加一条消息到指定 AI 对话 */
  function appendMessage(conversationId: string, message: Message): void {
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (conv) conv.messages.push(message)
  }

  /** 处理流式回复片段 */
  function applyStreamChunk(chunk: AiStreamChunk): void {
    const { conversationId, delta, done } = chunk
    // 累加流式文本
    if (!streamCache.value[conversationId]) {
      streamCache.value[conversationId] = ''
    }
    streamCache.value[conversationId] += delta

    const conv = conversations.value.find((c) => c.id === conversationId)
    if (!conv) return

    // 查找正在流式输出的 AI 消息
    const lastMsg = conv.messages[conv.messages.length - 1]
    if (lastMsg && lastMsg.sender === 'ai' && lastMsg.streaming) {
      lastMsg.content = streamCache.value[conversationId]
      if (done) {
        lastMsg.streaming = false
        delete streamCache.value[conversationId]
        conv.generating = false
      }
    }
  }

  /** 开始一次 AI 回复：插入一条空的流式消息 */
  function startStreaming(conversationId: string): void {
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (!conv) return
    conv.generating = true
    streamCache.value[conversationId] = ''
    conv.messages.push({
      id: generateId('ai_msg'),
      conversationId,
      sender: 'ai',
      type: 'AI',
      content: '',
      time: new Date().toISOString(),
      streaming: true
    })
  }

  /** 终止流式输出（手动停止） */
  function stopStreaming(conversationId: string): void {
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (!conv) return
    conv.generating = false
    const lastMsg = conv.messages[conv.messages.length - 1]
    if (lastMsg && lastMsg.streaming) {
      lastMsg.streaming = false
    }
    delete streamCache.value[conversationId]
  }

  /** 重置 AI 状态 */
  function reset(): void {
    conversations.value = []
    currentId.value = ''
    streamCache.value = {}
  }

  return {
    conversations,
    currentId,
    streamCache,
    getCurrent,
    setCurrent,
    setConversations,
    createConversation,
    appendMessage,
    applyStreamChunk,
    startStreaming,
    stopStreaming,
    reset
  }
})
