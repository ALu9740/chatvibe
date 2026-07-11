import request from '@/utils/request'
import type { NotificationItem } from '@/types'

/** 获取通知列表 */
export function getNotificationList() {
  return request.get<unknown, NotificationItem[]>('/notification/list')
}

/** 获取未读通知数 */
export function getUnreadCount() {
  return request.get<unknown, number>('/notification/unread-count')
}

/** 标记单条通知为已读 */
export function markAsRead(id: string) {
  return request.put(`/notification/${id}/read`)
}

/** 全部标记已读 */
export function markAllAsRead() {
  return request.put('/notification/read-all')
}

/** 删除通知 */
export function deleteNotification(id: string) {
  return request.delete(`/notification/${id}`)
}
