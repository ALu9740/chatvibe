import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useNotificationStore } from '@/stores/notification'
import { getToken, removeToken } from '@/utils/request'
import type { WsMessage } from '@/types'
import { toast } from '@/utils/toast'
import { USE_MOCK } from '@/mock/data'

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

/** WebSocket 连接管理 composable */
export function useWebSocket() {
  const wsUrl = import.meta.env.VITE_WS_URL
  const connected = ref(false)
  let client: Client | null = null
  // 订阅缓存，便于断线重连后恢复
  const subscriptions = ref<Map<string, { id: string }>>(new Map())

  /** 建立 STOMP 连接 */
  function connect(): void {
    // Mock 模式：跳过真实 WebSocket 连接
    if (USE_MOCK) return
    const authStore = useAuthStore()
    if (!authStore.isLoggedIn) return

    // 后端 JwtHandshakeInterceptor 从 URL query 参数 ?token=xxx 提取 JWT
    // SockJS 握手发生在 STOMP CONNECT 之前，connectHeaders 中的 Authorization 无法被握手拦截器读取
    const token = getToken() || ''
    const sockjsUrl = `${wsUrl}?token=${encodeURIComponent(token)}`

    client = new Client({
      // 使用 SockJS 作为传输层，token 通过 query 参数传递以通过握手鉴权
      webSocketFactory: () => new SockJS(sockjsUrl),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {
        // 生产环境关闭调试日志
      },
      onConnect: () => {
        connected.value = true
        subscribeAll()
      },
      onDisconnect: () => {
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
    subscriptions.value.set('user', { id: userSub.id })

    // 订阅用户状态变更广播（上线/下线/忙碌/离开）
    const statusSub = client.subscribe('/topic/status', (message) => {
      try {
        const payload = JSON.parse(message.body) as { userId: number | string; status: number }
        chatStore.handleStatusChange(payload)
      } catch (e) {
        console.error('[ChatVibe WS] 状态消息解析失败', e)
      }
    })
    subscriptions.value.set('status', { id: statusSub.id })

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
      subscriptions.value.set('force-logout', { id: kickSub.id })

      // 订阅消息通知（好友请求/群邀请/系统消息等）
      const notifSub = client.subscribe(`/topic/user.${userId}.notification`, (message) => {
        try {
          const payload = JSON.parse(message.body)
          notificationStore.handleIncomingNotification(payload)
        } catch (e) {
          console.error('[ChatVibe WS] 通知消息解析失败', e)
        }
      })
      subscriptions.value.set('notification', { id: notifSub.id })
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
    if (subscriptions.value.has(key)) return

    const chatStore = useChatStore()
    // 后端 ChatWebSocketHandler 广播路径为 /topic/conversation.{conversationId}
    const sub = client.subscribe(`/topic/conversation.${conversationId}`, (message) => {
      try {
        // 后端直接广播 Message 实体（裸结构），非 WsMessage 包装
        const payload = JSON.parse(message.body) as RawMessage
        chatStore.handleIncomingMessage(payload)
      } catch (e) {
        console.error('[ChatVibe WS] 会话消息解析失败', e)
      }
    })
    subscriptions.value.set(key, { id: sub.id })
  }

  /** 取消订阅会话频道 */
  function unsubscribeConversation(conversationId: string): void {
    const key = `conv_${conversationId}`
    const sub = subscriptions.value.get(key)
    if (sub && client) {
      client.unsubscribe(sub.id)
      subscriptions.value.delete(key)
    }
  }

  /** 发送消息到后端 */
  function send(destination: string, body: unknown): void {
    if (!client || !client.connected) {
      toast.warning('连接已断开', '请稍后重试')
      return
    }
    client.publish({
      destination,
      body: JSON.stringify(body)
    })
  }

  /** 发送聊天消息（快捷方法） */
  function sendMessage(body: unknown): void {
    send('/app/chat.send', body)
  }

  /** 断开连接 */
  function disconnect(): void {
    if (client) {
      client.deactivate()
      client = null
      connected.value = false
      subscriptions.value.clear()
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
    unsubscribeConversation,
    sendMessage,
    send
  }
}
