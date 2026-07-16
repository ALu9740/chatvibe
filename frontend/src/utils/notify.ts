import { getNotificationPreferences } from '@/api/user'

let audio: HTMLAudioElement | null = null
let preferences = { desktop: true, sound: true, aiAlert: false }
let permissionRequested = false

/** 初始化：加载用户偏好 + 请求浏览器通知权限 */
export async function initNotify() {
  try {
    const data = await getNotificationPreferences()
    preferences = data
  } catch {
    // 加载失败保持默认
  }
  // 请求桌面通知权限
  if (preferences.desktop && 'Notification' in window && !permissionRequested) {
    permissionRequested = true
    if (Notification.permission === 'default') {
      Notification.requestPermission()
    }
  }
}

/** 更新偏好缓存（用户切换开关时调用） */
export function updatePreferences(prefs: { desktop: boolean; sound: boolean; aiAlert: boolean }) {
  preferences = prefs
  // 如果开启了桌面通知，请求权限
  if (prefs.desktop && 'Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission()
  }
}

/**
 * 播放提示音
 */
function playSound() {
  if (!preferences.sound) return
  if (!audio) {
    audio = new Audio('/sounds/notification.wav')
  }
  audio.currentTime = 0
  audio.play().catch(() => {
    // 浏览器可能阻止自动播放，忽略
  })
}

/**
 * 显示桌面通知
 */
function showDesktopNotification(title: string, body: string) {
  if (!preferences.desktop) return
  if (!('Notification' in window)) return
  if (Notification.permission !== 'granted') return
  new Notification(title, {
    body,
    icon: '/favicon.ico',
    tag: 'chatvibe-message'
  })
}

/**
 * 综合通知：同时触发桌面弹窗 + 声音
 * @param title 通知标题
 * @param body 通知内容
 */
export function notify(title: string, body: string) {
  showDesktopNotification(title, body)
  playSound()
}

/**
 * 聊天消息通知（带静音判断 + AI 消息特别提醒）
 * @param senderName 发送者名称
 * @param preview 消息预览
 * @param silent 是否静音（来自后端 extra 中的 silent 标记）
 * @param isAI 是否为 AI 消息（为 true 时受 aiAlert 偏好控制）
 */
export function notifyChatMessage(senderName: string, preview: string, silent = false, isAI = false) {
  // AI 消息受 aiAlert 偏好控制：未开启则不通知
  if (isAI && !preferences.aiAlert) return
  const title = isAI ? `${senderName}（AI 助手）` : `${senderName} 发来消息`
  showDesktopNotification(title, preview)
  if (!silent) {
    playSound()
  }
}