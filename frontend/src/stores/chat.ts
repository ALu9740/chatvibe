import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as chatApi from '@/api/chat'
import { useAuthStore } from '@/stores/auth'
import { isWsConnected, wsSendMessage, wsSendReadReceipt } from '@/composables/useWebSocket'
import type {
  Conversation,
  ConversationType,
  Message,
  MessageType,
  SendMessageRequest,
  WsMessage
} from '@/types'
import { generateId } from '@/utils/format'
import { notifyChatMessage } from '@/utils/notify'

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
  /** 私聊对方用户ID（仅 type=1） */
  peerId?: number | string
  /** 私聊对方在线状态（仅 type=1）: 0-离线 1-在线 2-忙碌 3-离开 */
  peerStatus?: number
  /** 当前用户在该会话中的角色: 0-成员 1-管理员 2-群主 */
  myRole?: number
  /** 是否免打扰: 0-否 1-是 */
  muted?: number
  /** 是否置顶: 0-否 1-是 */
  pinned?: number
  /** 群组是否已解散: 0-否 1-是 */
  dissolved?: number
  /** 最后一条消息类型: 0-文本 1-图片 3-文件 4-系统 */
  lastMessageType?: number
}

/** 后端 Message 实体原始结构 */
interface RawMessage {
  id: number | string
  conversationId: number | string
  senderId?: number | string
  type?: number // 0-文本 1-图片 2-语音 3-文件 4-系统
  content?: string
  extra?: string
  status?: number
  createdAt?: string
  created_at?: string
  /** 发送者昵称（JOIN user 表，群聊展示用） */
  senderName?: string
  /** 发送者头像（JOIN user 表，群聊展示用） */
  senderAvatar?: string
}

const INT_TO_TYPE: Record<number, MessageType> = {
  0: 'TEXT',
  1: 'IMAGE',
  2: 'FILE',
  3: 'FILE',
  4: 'SYSTEM'
}

/** 前端消息类型字符串 → 后端整数（与 api/chat.ts 的 TYPE_TO_INT 一致） */
function typeToInt(type?: string): number {
  const map: Record<string, number> = { TEXT: 0, IMAGE: 1, FILE: 3, SYSTEM: 4, AI: 0 }
  return type ? (map[type] ?? 0) : 0
}

/**
 * 构建会话列表最后一条消息的预览文本。
 * - IMAGE: [图片]
 * - FILE: [文件]文件名（从 extra JSON 解析）
 * - 其他: 原始内容
 */
function buildPreviewText(type?: string, content?: string, extra?: string): string {
  if (type === 'IMAGE') return '[图片]'
  if (type === 'FILE') {
    let fileName = '未知文件'
    if (extra) {
      try {
        const data = JSON.parse(extra)
        if (data.fileName) fileName = data.fileName
      } catch { /* ignore */ }
    } else if (content) {
      const parts = content.split('/')
      fileName = parts[parts.length - 1] || fileName
    }
    return '[文件]' + fileName
  }
  return content || ''
}

/** 将后端 ConversationVO 映射为前端 Conversation */
function mapConversation(raw: RawConversation): Conversation {
  const typeNum = Number(raw.type)
  const type: ConversationType = typeNum === 2 ? 'group' : 'private'
  const peerStatus = typeNum === 1 ? (raw.peerStatus ?? 0) : undefined
  return {
    id: String(raw.id),
    type,
    name: raw.name || '',
    avatar: raw.avatar,
    lastMessage: raw.lastMessage || '',
    lastTime: raw.lastMessageAt || '',
    unread: raw.unreadCount || 0,
    members: raw.memberCount,
    isAI: typeNum === 3,
    // 私聊: online 由对方状态推断（非 0 即在线展示）
    online: typeNum === 1 ? peerStatus !== 0 : undefined,
    peerStatus,
    // 私聊: targetId 为对方（好友）用户ID；群聊/AI: 不设置，调用方回退使用会话ID
    targetId: typeNum === 1 && raw.peerId != null ? String(raw.peerId) : undefined,
    // 群聊: 记录群主用户ID，用于判断当前用户是否为群主
    ownerId: typeNum === 2 && raw.ownerId != null ? String(raw.ownerId) : undefined,
    // 当前用户在该会话中的角色
    myRole: raw.myRole,
    // 免打扰 / 置顶
    muted: Number(raw.muted ?? 0),
    pinned: Number(raw.pinned ?? 0),
    // 群组是否已解散
    dissolved: Number(raw.dissolved ?? 0) === 1,
    // 最后一条消息类型
    lastMessageType: Number(raw.lastMessageType ?? 0)
  }
}

/** 将后端 Message 实体映射为前端 Message */
function mapMessage(raw: RawMessage, currentUserId?: string | number): Message {
  const senderId = raw.senderId != null ? String(raw.senderId) : ''
  let sender: Message['sender'] = 'other'
  let name = raw.senderName
  let avatar = raw.senderAvatar
  if (senderId === '0') {
    // senderId=0: 系统消息(type=4)显示为 system，其余为 AI 回复
    const typeNum = Number(raw.type ?? 0)
    if (typeNum === 4) {
      sender = 'system'
    } else {
      sender = 'ai'
      // AI 回复无 user 记录，使用统一昵称/头像
      if (!name) name = 'Vibe助手'
      if (!avatar) avatar = '🤖'
    }
  } else if (currentUserId != null && senderId === String(currentUserId)) {
    sender = 'self'
  }
  return {
    id: String(raw.id ?? generateId('m')),
    conversationId: String(raw.conversationId ?? ''),
    sender,
    senderId,
    type: INT_TO_TYPE[Number(raw.type ?? 0)] || 'TEXT',
    content: raw.content || '',
    extra: raw.extra,
    time: raw.createdAt || raw.created_at || new Date().toISOString(),
    // 发送者昵称和头像（群聊展示用）
    name,
    avatar
  }
}

/** 聊天状态 store：管理会话列表、当前会话、消息记录与未读计数 */
export const useChatStore = defineStore('chat', () => {
  const authStore = useAuthStore()

  // 会话列表
  const conversations = ref<Conversation[]>([])
  // 当前激活的会话
  const currentConversation = ref<Conversation | null>(null)
  // 消息记录，按会话 ID 分组
  const messageMap = ref<Record<string, Message[]>>({})
  // 各会话是否还有更多历史消息可加载
  const hasMoreMap = ref<Record<string, boolean>>({})
  // 是否正在加载更多历史消息
  const loadingMore = ref(false)

  // 计算属性：未读总数
  const totalUnread = computed(() =>
    conversations.value.reduce((sum, c) => sum + (c.unread || 0), 0)
  )

  // 当前会话的消息列表
  const currentMessages = computed(() => {
    if (!currentConversation.value) return []
    return messageMap.value[currentConversation.value.id] || []
  })

  // 私聊会话
  const privateConversations = computed(() =>
    conversations.value.filter((c) => c.type === 'private')
  )

  // 群聊会话
  const groupConversations = computed(() =>
    conversations.value.filter((c) => c.type === 'group')
  )

  /** 拉取会话列表 */
  async function fetchConversations(): Promise<void> {
    const list = await chatApi.getConversations()
    conversations.value = (list as unknown as RawConversation[]).map(mapConversation)
  }

  /** 切换当前会话并拉取历史消息 */
  async function selectConversation(conversationId: string): Promise<void> {
    const target = conversations.value.find((c) => c.id === conversationId)
    if (!target) return
    currentConversation.value = target
    // 已读消息清零
    target.unread = 0
    if (!messageMap.value[conversationId]) {
      await fetchMessages(conversationId)
    }
    // 通知后端已读（WebSocket 优先，REST 降级）
    if (isWsConnected()) {
      wsSendReadReceipt(conversationId)
    } else {
      await chatApi.markRead(conversationId)
    }
  }

  /** 拉取某个会话的历史消息（初始加载，获取最新一页） */
  async function fetchMessages(conversationId: string): Promise<void> {
    const size = 20
    const list = await chatApi.getMessages(conversationId, undefined, size)
    const uid = authStore.user?.id
    // 后端返回 ORDER BY id DESC（最新在前），反转为时间正序（最旧在前）
    messageMap.value[conversationId] = (list as unknown as RawMessage[])
      .map((r) => mapMessage(r, uid))
      .reverse()
    // 返回满页说明可能还有更旧的消息
    hasMoreMap.value[conversationId] = (list as unknown[]).length >= size
  }

  /** 上拉加载更多历史消息，返回新加载的消息条数 */
  async function loadMoreMessages(conversationId: string): Promise<number> {
    if (loadingMore.value) return 0
    if (!hasMoreMap.value[conversationId]) return 0
    const messages = messageMap.value[conversationId]
    if (!messages || messages.length === 0) return 0
    // 取当前最早的消息 ID 作为游标
    const oldestId = Number(messages[0].id)
    if (!oldestId) return 0

    loadingMore.value = true
    try {
      const size = 20
      const list = await chatApi.getMessages(conversationId, oldestId, size)
      const uid = authStore.user?.id
      const older = (list as unknown as RawMessage[])
        .map((r) => mapMessage(r, uid))
        .reverse()
      if (older.length > 0) {
        // 向消息列表头部插入更旧的消息
        messageMap.value[conversationId] = [...older, ...messages]
      }
      // 不足一页说明已到底
      hasMoreMap.value[conversationId] = (list as unknown[]).length >= size
      return older.length
    } finally {
      loadingMore.value = false
    }
  }

  /** 发送消息（乐观更新，WebSocket 优先，REST 降级） */
  async function sendMessage(payload: SendMessageRequest): Promise<void> {
    const tempId = generateId('m')
    const tempMessage: Message = {
      id: tempId,
      conversationId: payload.conversationId,
      sender: 'self',
      type: payload.type,
      content: payload.content,
      extra: payload.extra,
      time: new Date().toISOString(),
      sendStatus: 'sending'
    }
    // 立即插入到消息列表
    if (!messageMap.value[payload.conversationId]) {
      messageMap.value[payload.conversationId] = []
    }
    messageMap.value[payload.conversationId].push(tempMessage)
    // 更新会话最后一条消息预览
    updateLastMessage(payload.conversationId, buildPreviewText(payload.type, payload.content, payload.extra), typeToInt(payload.type))

    // 构建 WebSocket / REST 共用的 payload
    const extra = payload.extra
      ? payload.mentionAI
        ? JSON.stringify({ ...JSON.parse(payload.extra), mentionAI: true })
        : payload.extra
      : payload.mentionAI
        ? JSON.stringify({ mentionAI: true })
        : undefined
    const body = {
      conversationId: Number(payload.conversationId),
      type: typeToInt(payload.type),
      content: payload.content,
      extra
    }

    // 1. WebSocket 优先
    const wsOk = isWsConnected()
    if (import.meta.env.DEV) {
      console.log('[ChatStore] sendMessage: WS连接状态 =', wsOk, '| 会话:', payload.conversationId)
    }
    if (wsOk) {
      const sent = wsSendMessage(body)
      if (sent) {
        // 等待广播回环确认（10s 超时）
        // handleIncomingMessage 的去重逻辑会替换临时消息为真实消息
        setTimeout(() => {
          const list = messageMap.value[payload.conversationId]
          if (!list) return
          const idx = list.findIndex((m) => m.id === tempId)
          if (idx !== -1 && list[idx].sendStatus === 'sending') {
            // 仍为 sending 状态 → 未收到广播确认 → 标记失败
            list[idx].sendStatus = 'failed'
          }
        }, 10000)
        return
      }
    }

    // 2. WebSocket 不可用 → 降级走 REST
    try {
      const real = await chatApi.sendMessage(payload) as unknown as RawMessage
      const mapped = mapMessage(real, authStore.user?.id)
      mapped.sendStatus = 'sent'
      const list = messageMap.value[payload.conversationId]
      const idx = list.findIndex((m) => m.id === tempId)
      if (idx !== -1) list[idx] = mapped
    } catch (err) {
      console.error('[ChatStore] REST 发送消息失败:', err)
      const list = messageMap.value[payload.conversationId]
      const idx = list.findIndex((m) => m.id === tempId)
      if (idx !== -1) list[idx].sendStatus = 'failed'
    }
  }

  /** 重试发送失败的消息 */
  async function retrySendMessage(failedMessage: Message): Promise<void> {
    const list = messageMap.value[failedMessage.conversationId]
    if (!list) return
    const idx = list.findIndex((m) => m.id === failedMessage.id)
    if (idx === -1) return

    // 重置为发送中
    list[idx].sendStatus = 'sending'

    const body = {
      conversationId: Number(failedMessage.conversationId),
      type: typeToInt(failedMessage.type),
      content: failedMessage.content,
      extra: failedMessage.extra
    }

    // 1. WebSocket 优先
    if (isWsConnected()) {
      const sent = wsSendMessage(body)
      if (sent) {
        const tempId = failedMessage.id
        setTimeout(() => {
          const l = messageMap.value[failedMessage.conversationId]
          if (!l) return
          const i = l.findIndex((m) => m.id === tempId)
          if (i !== -1 && l[i].sendStatus === 'sending') {
            l[i].sendStatus = 'failed'
          }
        }, 10000)
        return
      }
    }

    // 2. 降级 REST
    try {
      const payload: SendMessageRequest = {
        conversationId: failedMessage.conversationId,
        type: failedMessage.type,
        content: failedMessage.content,
        extra: failedMessage.extra
      }
      const real = await chatApi.sendMessage(payload) as unknown as RawMessage
      const mapped = mapMessage(real, authStore.user?.id)
      mapped.sendStatus = 'sent'
      const i = list.findIndex((m) => m.id === failedMessage.id)
      if (i !== -1) list[i] = mapped
    } catch (err) {
      console.error('[ChatStore] 重试发送失败:', err)
      const i = list.findIndex((m) => m.id === failedMessage.id)
      if (i !== -1) list[i].sendStatus = 'failed'
    }
  }

  /** 处理 WebSocket 收到的新消息（兼容包装结构与裸 Message） */
  function handleIncomingMessage(payload: WsMessage | RawMessage | { event?: string; conversationId?: number | string; message?: string }): void {
    // 1. 处理特殊事件（如群组解散）
    const eventPayload = payload as { event?: string; conversationId?: number | string; message?: string }
    if (eventPayload.event === 'dissolved') {
      const cid = eventPayload.conversationId != null ? String(eventPayload.conversationId) : ''
      if (cid) {
        const conv = conversations.value.find((c) => c.id === cid)
        if (conv) {
          conv.dissolved = true
          conv.lastMessage = eventPayload.message || '群组已被解散'
          conv.lastTime = new Date().toISOString()
        }
      }
      return
    }

    let raw: RawMessage
    let conversationId: string
    const p = payload as WsMessage & RawMessage
    if (p.message && typeof p.message === 'object') {
      raw = p.message as unknown as RawMessage
      conversationId = p.conversationId != null ? String(p.conversationId) : String(raw.conversationId ?? '')
    } else {
      raw = p
      conversationId = String(raw.conversationId ?? '')
    }
    const uid = authStore.user?.id
    const conv = conversations.value.find((c) => c.id === conversationId)
    const message = mapMessage(raw, uid)
    if (!messageMap.value[conversationId]) {
      messageMap.value[conversationId] = []
    }
    // 去重：避免自己发送的消息通过 WebSocket 回环导致重复显示
    //       AI 回复(本地流式占位 vs 后端广播)也按内容+时间去重
    const list = messageMap.value[conversationId]
    const exists = list.some(
      (m) => m.id === message.id ||
      (message.sender === 'self' && m.sender === 'self' && m.content === message.content && Math.abs(new Date(m.time).getTime() - new Date(message.time).getTime()) < 5000) ||
      (message.sender === 'ai' && m.sender === 'ai' && m.content === message.content && message.content !== '' && Math.abs(new Date(m.time).getTime() - new Date(message.time).getTime()) < 30000)
    )
    if (exists) {
      // AI 流式占位消息已存在，用后端真实消息 id 替换占位 id（便于后续操作）
      if (message.sender === 'ai') {
        const placeholder = list.find((m) => m.sender === 'ai' && m.content === message.content)
        if (placeholder) placeholder.id = message.id
      } else if (message.sender === 'self') {
        // WebSocket 广播回环：用后端真实消息（含 DB ID）替换临时消息，标记为已送达
        const tempIdx = list.findIndex(
          (m) => m.sender === 'self' && m.content === message.content && m.id !== message.id &&
          (m.sendStatus === 'sending' || m.sendStatus === 'failed')
        )
        if (tempIdx !== -1) {
          message.sendStatus = 'sent'
          list[tempIdx] = message
        }
      }
      return
    }
    list.push(message)
    // 更新会话预览与未读
    if (conv) {
      const msgTypeNum = Number(raw.type ?? 0)
      conv.lastMessage = buildPreviewText(message.type, message.content, message.extra)
      conv.lastTime = message.time
      conv.lastMessageType = msgTypeNum
      // 群组解散系统消息：标记会话为已解散
      if (message.sender === 'system' && message.content === '群组已被解散') {
        conv.dissolved = true
      }
      // 非当前会话累加未读
      if (currentConversation.value?.id !== conversationId) {
        conv.unread = (conv.unread || 0) + 1
      } else {
        // 当前会话：后端 sendMessage 已对除发送者外所有成员 unread_count + 1，
        // 需立即通知后端重置未读数，避免刷新后未读数重现（WebSocket 优先，REST 降级）
        if (isWsConnected()) {
          wsSendReadReceipt(conversationId)
        } else {
          chatApi.markRead(conversationId).catch(() => {})
        }
      }
    } else {
      // 会话不在本地列表中（用户曾删除会话，但对方发了新消息，后端已恢复成员记录）。
      // 刷新会话列表以获取恢复的会话，避免遗漏新消息。
      fetchConversations().catch(() => {})
    }
    // 只在非当前会话、且非自己发送的消息时通知
    if (conv && currentConversation.value?.id !== conversationId && message.sender !== 'self') {
      const senderName = raw.senderName || '未知'
      const preview = message.type === 'IMAGE' ? '[图片]'
        : message.type === 'FILE' ? '[文件]'
        : message.content || ''
      notifyChatMessage(senderName, preview, false, message.sender === 'ai')
    }
  }

  /** 更新会话最后一条消息 */
  function updateLastMessage(conversationId: string, content: string, messageType?: number): void {
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (conv) {
      conv.lastMessage = content
      conv.lastTime = new Date().toISOString()
      if (messageType !== undefined) {
        conv.lastMessageType = messageType
      }
    }
  }

  /** 处理 WebSocket 收到的用户状态变更：更新私聊会话的对方状态，同时同步当前用户自身状态 */
  function handleStatusChange(payload: { userId: number | string; status: number }): void {
    const targetId = String(payload.userId)
    const status = Number(payload.status)
    // 如果是当前用户自己的状态变更（如刷新后 WebSocket 重连），同步到 authStore
    const authStore = useAuthStore()
    if (authStore.user && String(authStore.user.id) === targetId) {
      authStore.setUser({ ...authStore.user, status })
    }
    // 更新私聊会话中对方的在线状态
    for (const conv of conversations.value) {
      if (conv.type === 'private' && conv.targetId === targetId) {
        conv.peerStatus = status
        conv.online = status !== 0
      }
    }
  }

  /** 拉取当前用户的所有群聊会话（含已从会话列表删除但未退出群组的，用于群组管理弹窗） */
  async function fetchAllGroupConversations(): Promise<Conversation[]> {
    const list = await chatApi.getMyGroupConversations()
    return (list as unknown as RawConversation[]).map(mapConversation)
  }

  /** 将指定会话加入本地列表（若已存在则跳过），用于重新打开已删除的会话 */
  function ensureConversation(conv: Conversation): void {
    const exist = conversations.value.find((c) => c.id === conv.id)
    if (!exist) {
      conversations.value.unshift(conv)
    }
  }

  /** 切换会话消息免打扰（调用后端 + 同步本地状态） */
  async function toggleMute(conversationId: string): Promise<boolean> {
    const muted = await chatApi.toggleMute(conversationId)
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (conv) conv.muted = muted ? 1 : 0
    return muted
  }

  /** 切换会话置顶（调用后端 + 同步本地状态） */
  async function togglePin(conversationId: string): Promise<boolean> {
    const pinned = await chatApi.togglePin(conversationId)
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (conv) conv.pinned = pinned ? 1 : 0
    return pinned
  }

  /** 删除会话（调用后端持久化 + 从本地列表移除） */
  async function deleteConversation(conversationId: string): Promise<void> {
    await chatApi.deleteConversation(conversationId)
    const idx = conversations.value.findIndex((c) => c.id === conversationId)
    if (idx !== -1) conversations.value.splice(idx, 1)
    if (currentConversation.value?.id === conversationId) {
      currentConversation.value = null
    }
    // 清除缓存的消息记录，重新加入时会重新拉取（按新加入时间过滤）
    delete messageMap.value[conversationId]
    delete hasMoreMap.value[conversationId]
  }

  /** 隐藏（删除）单条消息（仅对当前用户隐藏，持久化到后端） */
  async function hideMessage(conversationId: string, messageId: string): Promise<void> {
    await chatApi.hideMessage(messageId)
    const list = messageMap.value[conversationId]
    if (list) {
      const idx = list.findIndex((m) => m.id === messageId)
      if (idx !== -1) list.splice(idx, 1)
    }
  }

  /** 重新加入群聊会话（恢复会话列表显示） */
  async function rejoinGroup(conversationId: string): Promise<Conversation | null> {
    const raw = await chatApi.rejoinConversation(conversationId)
    const conv = mapConversation(raw as unknown as RawConversation)
    // 清除旧消息缓存，重新拉取时按新加入时间过滤（无法看到加入前的历史消息）
    delete messageMap.value[conversationId]
    ensureConversation(conv)
    return conv
  }

  /** 通过好友创建/恢复私聊会话（删除会话后重新创建，无法看到旧消息） */
  async function createPrivateConversationWith(targetUserId: string): Promise<Conversation | null> {
    const raw = await chatApi.createPrivateConversation(targetUserId)
    const conv = mapConversation(raw as unknown as RawConversation)
    // 清除旧消息缓存，确保恢复后按新加入时间过滤（看不到删除前的历史）
    delete messageMap.value[conv.id]
    // 同步隐藏左侧会话栏的旧最后一条消息与时间：
    // 后端 conversation 表仍保留旧的 lastMessage/lastMessageAt，
    // 但当前用户已删除会话且无法看到旧消息，故本地置空避免显示陈旧预览。
    conv.lastMessage = ''
    conv.lastTime = ''
    ensureConversation(conv)
    return conv
  }

  /** 清空当前会话的聊天记录（仅对当前用户隐藏，其他成员仍可见） */
  async function clearHistory(conversationId: string): Promise<void> {
    await chatApi.clearHistory(conversationId)
    // 清除前端消息缓存，重新拉取时后端按新 created_at 过滤（返回空列表）
    delete messageMap.value[conversationId]
    messageMap.value[conversationId] = []
    hasMoreMap.value[conversationId] = false
    // 同步隐藏左侧会话栏的最后一条消息与时间（仅本地视图，不影响对方）
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (conv) {
      conv.lastMessage = ''
      conv.lastTime = ''
    }
  }

  /** 重置全部状态（退出登录时） */
  function reset(): void {
    conversations.value = []
    currentConversation.value = null
    messageMap.value = {}
    hasMoreMap.value = {}
    loadingMore.value = false
  }

  return {
    conversations,
    currentConversation,
    messageMap,
    hasMoreMap,
    loadingMore,
    totalUnread,
    currentMessages,
    privateConversations,
    groupConversations,
    fetchConversations,
    selectConversation,
    fetchMessages,
    loadMoreMessages,
    sendMessage,
    retrySendMessage,
    handleIncomingMessage,
    handleStatusChange,
    fetchAllGroupConversations,
    ensureConversation,
    updateLastMessage,
    toggleMute,
    togglePin,
    deleteConversation,
    hideMessage,
    rejoinGroup,
    createPrivateConversationWith,
    clearHistory,
    reset
  }
})
