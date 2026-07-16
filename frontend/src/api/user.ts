import request from '@/utils/request'
import type { NotificationPreferences, User } from '@/types'

/** 获取当前用户信息 */
export function getCurrentUser() {
  return request.get<unknown, User>('/user/me')
}

/** 更新个人资料（昵称、签名、头像） */
export function updateProfile(data: Partial<Pick<User, 'nickname' | 'signature' | 'avatar' | 'bio'>>) {
  const { signature, ...rest } = data
  const payload = { ...rest }
  if (signature !== undefined) payload.bio = signature
  return request.put<unknown, User>('/user/profile', payload)
}

/** 上传头像（base64 字符串） */
export function uploadAvatar(base64: string) {
  return request.post<unknown, string>('/user/avatar', { base64 })
}

/** 修改密码 */
export function changePassword(oldPassword: string, newPassword: string) {
  return request.put<unknown, boolean>('/user/password', { oldPassword, newPassword })
}

/** 更换绑定邮箱 */
export function changeEmail(newEmail: string, code: string) {
  return request.put<unknown, boolean>('/user/email', { newEmail, code })
}

/** 获取通知偏好 */
export function getNotificationPreferences() {
  return request.get<unknown, NotificationPreferences>('/user/notifications')
}

/** 更新通知偏好 */
export function updateNotificationPreferences(data: NotificationPreferences) {
  return request.put<unknown, boolean>('/user/notifications', data)
}

/**
 * 更新当前用户在线状态
 * @param status 0-离线 1-在线 2-忙碌 3-离开
 */
export function updateStatus(status: number) {
  return request.put<unknown, number>('/user/status', null, { params: { status } })
}
