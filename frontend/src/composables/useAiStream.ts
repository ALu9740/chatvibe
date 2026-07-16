import { ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import { getToken } from '@/utils/request'
import type { Message } from '@/types'
import { generateId } from '@/utils/format'

/** AI 流式输出 composable：直接消费后端 /ai/chat 的 SSE 流，消息写入 chatStore */
export function useAiStream() {
  const chatStore = useChatStore()
  const streaming = ref(false)

  /**
   * 发送 AI 提问并消费服务端 SSE 流式回复
   * 调用前，调用方应已通过 chatStore.sendMessage 持久化用户提问
   * @param conversationId 会话 ID（用于 chatStore 内消息归集 + 后端 chatConversationId）
   * @param question 用户提问内容（已去除 @AI 前缀）
   */
  async function ask(conversationId: string, question: string): Promise<void> {
    if (streaming.value) return
    streaming.value = true

    // 插入 AI 流式回复占位消息到 chatStore（确保在视图中可见）
    const aiMsgId = generateId('ai_msg')
    const aiMsg: Message = {
      id: aiMsgId,
      conversationId,
      sender: 'ai',
      type: 'AI',
      content: '',
      time: new Date().toISOString(),
      streaming: true,
      name: 'Vibe助手',
      avatar: '🤖'
    }
    if (!chatStore.messageMap[conversationId]) chatStore.messageMap[conversationId] = []
    chatStore.messageMap[conversationId].push(aiMsg)

    try {
      await streamFromServer(conversationId, question, aiMsgId)
    } finally {
      streaming.value = false
      // 确保流式标记关闭
      updateAiMessage(conversationId, aiMsgId, '', true)
    }
  }

  /** 调用后端 /ai/chat SSE 流式接口并逐片段投递到 chatStore */
  async function streamFromServer(conversationId: string, question: string, aiMsgId: string): Promise<void> {
    const base = import.meta.env.VITE_API_BASE
    const resp = await fetch(`${base}/ai/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${getToken() || ''}`,
        Accept: 'text/event-stream'
      },
      body: JSON.stringify({ prompt: question, chatConversationId: conversationId })
    })
    if (!resp.ok || !resp.body) {
      throw new Error(`AI 请求失败: HTTP ${resp.status}`)
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let finished = false

    while (!finished) {
      const { value, done: readerDone } = await reader.read()
      if (readerDone) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 事件以空行(\n\n)分隔
      let sep: number
      while ((sep = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        const { event, data } = parseSse(rawEvent)
        if (event === 'done' || data === '[DONE]') {
          finished = true
          break
        }
        if (event === 'error') {
          throw new Error(data || 'AI 服务异常')
        }
        if (data) {
          updateAiMessage(conversationId, aiMsgId, data, false)
        }
      }
    }
  }

  /** 更新 chatStore 中的 AI 流式消息内容 */
  function updateAiMessage(conversationId: string, msgId: string, delta: string, done: boolean) {
    const list = chatStore.messageMap[conversationId]
    if (!list) return
    const msg = list.find((m) => m.id === msgId)
    if (!msg) return
    if (delta) msg.content += delta
    if (done) msg.streaming = false
  }

  /** 解析单个 SSE 事件块 */
  function parseSse(raw: string): { event: string; data: string } {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of raw.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5))
      }
    }
    // SSE 规范：data 行去掉一个前导空格后拼接
    const data = dataLines.map((l) => (l.startsWith(' ') ? l.slice(1) : l)).join('\n')
    return { event, data }
  }

  /** 手动停止流式输出 */
  function stop(conversationId: string): void {
    streaming.value = false
    // 标记当前会话最后一条 AI 消息为非流式
    const list = chatStore.messageMap[conversationId]
    if (list) {
      const lastMsg = list[list.length - 1]
      if (lastMsg && lastMsg.streaming) {
        lastMsg.streaming = false
      }
    }
  }

  return {
    streaming,
    ask,
    stop
  }
}
