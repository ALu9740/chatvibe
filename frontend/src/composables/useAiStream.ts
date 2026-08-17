import { ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import { getToken } from '@/utils/request'
import type { Message } from '@/types'
import { generateId } from '@/utils/format'

/** AI 流式输出 composable：直接消费后端 /ai/chat 的 SSE 流，消息写入 chatStore
 *
 * 多消息回复支持：
 * 后端将 AI 回复按 \n\n 拆分为多条独立消息，通过 SSE segment 事件通知分段边界。
 * 前端收到 segment 事件时，结束当前消息气泡（去除尾部空白、关闭流式标记），
 * 并创建新的占位消息接收下一段内容，实现类似真人 IM 聊天的多条消息效果。
 */
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
      // 确保所有 AI 消息的流式标记关闭（兜底，防止竞态导致遗漏）
      const list = chatStore.messageMap[conversationId]
      if (list) {
        for (let i = list.length - 1; i >= 0; i--) {
          if (list[i].sender === 'ai' && list[i].streaming) {
            list[i].streaming = false
          }
        }
      }
    }
  }

  /** 调用后端 /ai/chat SSE 流式接口并逐片段投递到 chatStore
   *
   * SSE 事件类型：
   * - replace: 当前消息段的清洗后完整内容（全量替换，非追加），保证流式过程中不闪现 Markdown 符号
   * - segment: 消息段边界，结束当前气泡并创建新气泡
   * - done: 整个 AI 回复结束
   * - error: AI 服务异常
   */
  async function streamFromServer(conversationId: string, question: string, initialMsgId: string): Promise<void> {
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
    // 当前消息段的占位 ID，segment 事件时会切换到新的占位
    let currentMsgId = initialMsgId

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
          // done 事件前，后端已通过 replace 事件展示了最后一段的清洗内容
          // segment 处理时会创建一个新占位接收下一段，但 done 表示回复结束，
          // 该占位内容为空，需移除以避免显示空气泡
          finalizeSegment(conversationId, currentMsgId)
          removeTrailingEmptySegment(conversationId)
          finished = true
          break
        }
        if (event === 'error') {
          throw new Error(data || 'AI 服务异常')
        }
        if (event === 'segment') {
          // segment 事件携带后端清洗后的完整段落内容（已去除 Markdown 语法）
          // 用清洗内容替换流式累积的原始内容，确保用户看到的最终文本无 ** ` # 等符号
          replaceSegmentContent(conversationId, currentMsgId, data)
          finalizeSegment(conversationId, currentMsgId)
          // 创建新的占位消息接收下一段
          currentMsgId = generateId('ai_msg')
          createNewSegment(conversationId, currentMsgId)
          continue
        }
        if (event === 'replace') {
          // replace 事件携带当前消息段的清洗后完整内容
          // 直接替换消息内容（非追加），确保流式过程中不闪现 ** # ` 等 Markdown 符号
          replaceSegmentContent(conversationId, currentMsgId, data)
          continue
        }
      }
    }
  }

  /** 用后端清洗后的内容替换流式累积的原始内容 */
  function replaceSegmentContent(conversationId: string, msgId: string, cleanedContent: string) {
    const list = chatStore.messageMap[conversationId]
    if (!list) return
    const msg = list.find((m) => m.id === msgId)
    if (!msg) return
    msg.content = cleanedContent
  }

  /** 移除尾部内容为空的 AI 占位消息
   * segment 事件会创建新占位接收下一段，但如果 AI 回复以 \n\n 结尾，
   * 最后一个占位无内容填充。done 时需清理这类空气泡。
   * 从末尾往前扫描，跳过已替换为 DB ID 的消息，移除连续的空占位。
   */
  function removeTrailingEmptySegment(conversationId: string) {
    const list = chatStore.messageMap[conversationId]
    if (!list) return
    for (let i = list.length - 1; i >= 0; i--) {
      const m = list[i]
      if (m.sender !== 'ai') break
      // 已替换为 DB 数字 ID 的消息（已落库）不处理
      if (!isNaN(Number(m.id))) break
      // 占位消息（ai_msg_*）且内容为空 → 移除，继续往前检查
      if (!m.content || m.content.trim() === '') {
        list.splice(i, 1)
      } else {
        break // 遇到有内容的消息，停止
      }
    }
  }

  /** 结束当前消息段：去除尾部空白，关闭流式标记 */
  function finalizeSegment(conversationId: string, msgId: string) {
    const list = chatStore.messageMap[conversationId]
    if (!list) return
    const msg = list.find((m) => m.id === msgId)
    if (!msg) return
    // 去除尾部 \n\n 等 whitespace（后端落库的段落内容已 trim，需保持一致以便去重匹配）
    msg.content = msg.content.trim()
    msg.streaming = false
  }

  /** 创建新的消息段占位 */
  function createNewSegment(conversationId: string, msgId: string) {
    if (!chatStore.messageMap[conversationId]) chatStore.messageMap[conversationId] = []
    chatStore.messageMap[conversationId].push({
      id: msgId,
      conversationId,
      sender: 'ai',
      type: 'AI',
      content: '',
      time: new Date().toISOString(),
      streaming: true,
      name: 'Vibe助手',
      avatar: '🤖'
    })
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
    // 标记当前会话所有仍在流式的 AI 消息为非流式
    const list = chatStore.messageMap[conversationId]
    if (list) {
      for (let i = list.length - 1; i >= 0; i--) {
        if (list[i].sender === 'ai' && list[i].streaming) {
          list[i].streaming = false
        }
      }
    }
  }

  return {
    streaming,
    ask,
    stop
  }
}
