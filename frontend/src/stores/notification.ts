import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as notificationApi from '@/api/notification'
import type { NotificationItem } from '@/types'
import { notify } from '@/utils/notify'

/** 后端 NotificationVO 原始结构 */
interface RawNotification {
  id: number | string
  type: number
  typeDesc?: string
  title: string
  content?: string
  extra?: string
  isRead: number
  createdAt: string
}

const TYPE_MAP: Record<number, NotificationItem['type']> = {
  1: 'SYSTEM',
  2: 'FRIEND_REQUEST',
  3: 'FRIEND_ACCEPT',
  4: 'FRIEND_DELETE',
  5: 'GROUP_INVITE',
  6: 'GROUP_REMOVE',
  7: 'GROUP_DISSOLVE'
}

function mapNotification(raw: RawNotification): NotificationItem {
  return {
    id: String(raw.id),
    type: TYPE_MAP[Number(raw.type)] || 'SYSTEM',
    typeDesc: raw.typeDesc || '',
    title: raw.title || '',
    content: raw.content || '',
    extra: raw.extra,
    isRead: Number(raw.isRead) === 1,
    createdAt: raw.createdAt || ''
  }
}

/** 通知 store：管理消息信封的通知列表与未读数 */
export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<NotificationItem[]>([])
  const unreadCount = ref(0)
  const panelVisible = ref(false)

  /** 拉取通知列表 */
  async function fetchList() {
    const list = await notificationApi.getNotificationList()
    notifications.value = (list as unknown as RawNotification[]).map(mapNotification)
    await fetchUnreadCount()
  }

  /** 拉取未读数 */
  async function fetchUnreadCount() {
    const count = await notificationApi.getUnreadCount()
    unreadCount.value = (count as unknown as number) || 0
  }

  /** 标记单条已读 */
  async function markAsRead(id: string) {
    await notificationApi.markAsRead(id)
    const item = notifications.value.find((n) => n.id === id)
    if (item && !item.isRead) {
      item.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  /** 全部已读 */
  async function markAllAsRead() {
    await notificationApi.markAllAsRead()
    notifications.value.forEach((n) => (n.isRead = true))
    unreadCount.value = 0
  }

  /** 删除通知 */
  async function deleteNotification(id: string) {
    const item = notifications.value.find((n) => n.id === id)
    await notificationApi.deleteNotification(id)
    notifications.value = notifications.value.filter((n) => n.id !== id)
    if (item && !item.isRead) {
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  /** WebSocket 收到新通知时添加到列表头部 */
  function handleIncomingNotification(raw: RawNotification) {
    const item = mapNotification(raw)
    if (notifications.value.some((n) => n.id === item.id)) return
    notifications.value.unshift(item)
    if (!item.isRead) unreadCount.value++
    // 触发桌面通知 + 声音
    notify(item.title, item.content || '')
  }

  function togglePanel() {
    panelVisible.value = !panelVisible.value
  }

  function reset() {
    notifications.value = []
    unreadCount.value = 0
    panelVisible.value = false
  }

  return {
    notifications,
    unreadCount,
    panelVisible,
    fetchList,
    fetchUnreadCount,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    handleIncomingNotification,
    togglePanel,
    reset
  }
})
