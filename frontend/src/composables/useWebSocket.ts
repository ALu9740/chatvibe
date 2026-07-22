import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useNotificationStore } from '@/stores/notification'
import { getToken, removeToken } from '@/utils/request'
import type { WsMessage } from '@/types'
import { toast } from '@/utils/toast'

/**
 * 后端广播的 Message 实体原始结构（与 stores/chat.ts 中的 RawMessage 一致）
 * 后端 ChatWebSocketHandler 直接将 Message 实体广播到 /topic/conversation.{id}
 */
interface RawMessage {
  id: number | string
  conversationId: number | string
  senderId?: number | string
  type?: number
  content?: string
  extra?: string
  status?: number
  createdAt?: string
}

// === 模块级状态（跨 composable 调用共享） ===
let client: Client | null = null
const connected = ref(false)
const subscriptions = new Map<string, { id: string }>()

/** WebSocket 是否已连接（可在 store / 任意模块中调用） */
export function isWsConnected(): boolean {
  return connected.value
}

/** 通过 WebSocket 发布 STOMP 消息，返回是否成功发送 */
export function wsPublish(destination: string, body: unknown): boolean {
  if (!client) {
    console.warn('[ChatVibe WS] wsPublish 失败: client 为 null（connect 未调用）')
    return false
  }
  if (!client.connected) {
    console.warn('[ChatVibe WS] wsPublish 失败: STOMP 未连接')
    return false
  }
  client.publish({
    destination,
    body: JSON.stringify(body)
  })
  return true
}

/** 通过 WebSocket 发送聊天消息 */
export function wsSendMessage(body: unknown): boolean {
  const ok = wsPublish('/app/chat.send', body)
  console.log('[ChatVibe WS] SEND /app/chat.send:', ok, JSON.stringify(body).substring(0, 200))
  return ok
}

/** 通过 WebSocket 发送已读回执 */
export function wsSendReadReceipt(conversationId: number | string): boolean {
  return wsPublish('/app/chat.read', { conversationId: Number(conversationId) })
}

/** WebSocket 连接管理 composable */
export function useWebSocket() {
  const wsUrl = import.meta.env.VITE_WS_URL

  /** 建立 STOMP 连接 */
  function connect(): void {
    const authStore = useAuthStore()
    if (!authStore.isLoggedIn) {
      console.warn('[ChatVibe WS] 用户未登录，跳过 WebSocket 连接')
      return
    }

    // 后端 JwtHandshakeInterceptor 从 URL query 参数 ?token=xxx 提取 JWT
    // SockJS 握手发生在 STOMP CONNECT 之前，connectHeaders 中的 Authorization 无法被握手拦截器读取
    const token = getToken() || ''
    const sockjsUrl = `${wsUrl}?token=${encodeURIComponent(token)}`
    console.log('[ChatVibe WS] 开始连接:', sockjsUrl)

    client = new Client({
      // 使用 SockJS 作为传输层，token 通过 query 参数传递以通过握手鉴权
      webSocketFactory: () => new SockJS(sockjsUrl),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      // 开发环境启用 STOMP 协议调试日志
      debug: import.meta.env.DEV
        ? (msg) => console.log('[STOMP]', msg)
        : () => {},
      onConnect: () => {
        console.log('[ChatVibe WS] ✅ 连接成功')
        connected.value = true
        subscribeAll()
      },
      onDisconnect: () => {
        console.log('[ChatVibe WS] ❌ 连接断开')
        connected.value = false
      },
      onWebSocketError: (event) => {
        console.error('[ChatVibe WS] WebSocket 传输层错误:', event)
        connected.value = false
      },
      onStompError: (frame) => {
        console.error('[ChatVibe WS] STOMP 错误:', frame.headers['message'])
        toast.error('连接异常', '实时连接异常，正在重试...')
      }
    })

    client.activate()
  }

  /** 订阅所有需要的频道：私人队列 + 全部会话广播 + 强制下线通知 */
  function subscribeAll(): void {
    if (!client || !client.connected) return
    const chatStore = useChatStore()
    const authStore = useAuthStore()
    const notificationStore = useNotificationStore()

    // 订阅私人消息队列（点对点推送，后端预留通道）
    const userSub = client.subscribe('/user/queue/messages', (message) => {
      try {
        const payload = JSON.parse(message.body) as WsMessage & RawMessage
        chatStore.handleIncomingMessage(payload)
      } catch (e) {
        console.error('[ChatVibe WS] 消息解析失败', e)
      }
    })
    subscriptions.set('user', { id: userSub.id })

    // 订阅用户状态变更广播（上线/下线/忙碌/离开）
    const statusSub = client.subscribe('/topic/status', (message) => {
      try {
        const payload = JSON.parse(message.body) as { userId: number | string; status: number }
        chatStore.handleStatusChange(payload)
      } catch (e) {
        console.error('[ChatVibe WS] 状态消息解析失败', e)
      }
    })
    subscriptions.set('status', { id: statusSub.id })

    // 订阅强制下线通知（账号在其他设备登录时触发）
    const userId = authStore.user?.id
    if (userId) {
      const kickSub = client.subscribe(`/topic/user.${userId}.force-logout`, (message) => {
        try {
          const payload = JSON.parse(message.body) as { message: string }
          console.warn('[ChatVibe WS] 收到强制下线通知:', payload.message)
          // 断开 WebSocket
          disconnect()
          // 清除本地登录状态
          removeToken()
          authStore.logoutLocal()
          // 提示用户
          toast.error('账号被强制下线', payload.message || '当前账号已在其他设备登录，您已被强制下线')
          // 延迟跳转登录页（让用户看到提示）
          if (!window.location.pathname.includes('/login')) {
            setTimeout(() => {
              window.location.href = '/login'
            }, 1500)
          }
        } catch (e) {
          console.error('[ChatVibe WS] 强制下线消息解析失败', e)
        }
      })
      subscriptions.set('force-logout', { id: kickSub.id })

      // 订阅消息通知（好友请求/群邀请/系统消息等）
      const notifSub = client.subscribe(`/topic/user.${userId}.notification`, (message) => {
        try {
          const payload = JSON.parse(message.body)
          notificationStore.handleIncomingNotification(payload)
        } catch (e) {
          console.error('[ChatVibe WS] 通知消息解析失败', e)
        }
      })
      subscriptions.set('notification', { id: notifSub.id })
    }

    // 订阅全部会话广播频道，确保任意会话的新消息都能实时接收
    for (const conv of chatStore.conversations) {
      subscribeConversation(conv.id)
    }
  }

  /** 订阅指定会话频道（后端 topic 使用 . 分隔符：/topic/conversation.{id}） */
  function subscribeConversation(conversationId: string): void {
    if (!client || !client.connected) return
    const key = `conv_${conversationId}`
    if (subscriptions.has(key)) return

    console.log(`[ChatVibe WS] 订阅会话频道: /topic/conversation.${conversationId}`)

    const chatStore = useChatStore()
    // 后端 ChatWebSocketHandler 广播路径为 /topic/conversation.{conversationId}
    const sub = client.subscribe(`/topic/conversation.${conversationId}`, (message) => {
      console.log(`[ChatVibe WS] 📨 收到会话 ${conversationId} 的 MESSAGE 帧:`, message.body?.substring(0, 200))
      try {
        // 后端直接广播 Message 实体（裸结构），非 WsMessage 包装
        const payload = JSON.parse(message.body) as RawMessage
        chatStore.handleIncomingMessage(payload)
      } catch (e) {
        console.error('[ChatVibe WS] 会话消息解析失败', e)
      }
    })
    subscriptions.set(key, { id: sub.id })
  }

  /** 取消订阅会话频道 */
  function unsubscribeConversation(conversationId: string): void {
    const key = `conv_${conversationId}`
    const sub = subscriptions.get(key)
    if (sub && client) {
      client.unsubscribe(sub.id)
      subscriptions.delete(key)
    }
  }

  /** 断开连接 */
  function disconnect(): void {
    if (client) {
      client.deactivate()
      client = null
      connected.value = false
      subscriptions.clear()
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    connect,
    disconnect,
    subscribeConversation,
    unsubscribeConversation
  }
}
