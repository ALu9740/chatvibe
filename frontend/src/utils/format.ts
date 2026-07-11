// ============================================================
// ChatVibe · 格式化工具
// ============================================================
import { pinyin } from 'pinyin-pro'
import type { Friend } from '@/types'

/**
 * 格式化时间显示（参考微信会话列表规则）
 * - 当天：HH:mm
 * - 昨天：昨天
 * - 本周（前2-6天）：周X
 * - 今年更早：M月D日
 * - 跨年：yyyy/M/D
 */
export function formatMessageTime(time: string | number | Date): string {
  const date = new Date(time)
  if (isNaN(date.getTime())) return String(time)

  const now = new Date()
  const isSameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()

  if (isSameDay) {
    const h = String(date.getHours()).padStart(2, '0')
    const m = String(date.getMinutes()).padStart(2, '0')
    return `${h}:${m}`
  }

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  const isYesterday =
    date.getFullYear() === yesterday.getFullYear() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getDate() === yesterday.getDate()
  if (isYesterday) return '昨天'

  const diffDays = Math.floor((now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24))
  if (diffDays < 7) {
    const weekMap = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return weekMap[date.getDay()]
  }

  // 今年更早：M月D日
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }
  // 跨年：yyyy/M/D
  return `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()}`
}

/** 提取消息预览文本 */
export function getMessagePreview(content: string, type?: string): string {
  if (!content) return ''
  if (type === 'IMAGE') return '[图片]'
  if (type === 'FILE') return '[文件]'
  if (type === 'SYSTEM') return content
  // 截断过长内容
  return content.length > 30 ? content.slice(0, 30) + '...' : content
}

/**
 * 格式化聊天窗口中的消息时间（用于时间分隔符）。
 * - 当天：HH:MM
 * - 昨天：昨天 HH:MM
 * - 今年内：mm月dd日 HH:MM
 * - 非今年：yyyy年mm月dd日 HH:MM
 */
export function formatChatTime(time: string | number | Date): string {
  const date = new Date(time)
  if (isNaN(date.getTime())) return String(time)

  const now = new Date()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  const timeStr = `${hh}:${mm}`

  const isSameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (isSameDay) return timeStr

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  const isYesterday =
    date.getFullYear() === yesterday.getFullYear() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getDate() === yesterday.getDate()
  if (isYesterday) return `昨天 ${timeStr}`

  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  if (date.getFullYear() === now.getFullYear()) {
    return `${month}月${day}日 ${timeStr}`
  }
  return `${date.getFullYear()}年${month}月${day}日 ${timeStr}`
}

/**
 * 格式化消息通知时间显示。
 * - 当天：HH:MM
 * - 昨天：昨天
 * - 一周内：周几
 * - 今年内：mm月dd日
 * - 其他年：yyyy年mm月dd日
 */
export function formatNotifTime(time: string | number | Date): string {
  const date = new Date(time)
  if (isNaN(date.getTime())) return String(time)

  const now = new Date()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  const isSameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (isSameDay) return `${hh}:${mm}`

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  const isYesterday =
    date.getFullYear() === yesterday.getFullYear() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getDate() === yesterday.getDate()
  if (isYesterday) return '昨天'

  const diffDays = Math.floor((now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24))
  if (diffDays < 7) {
    const weekMap = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return weekMap[date.getDay()]
  }

  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  if (date.getFullYear() === now.getFullYear()) {
    return `${month}月${day}日`
  }
  return `${date.getFullYear()}年${month}月${day}日`
}

/**
 * 文件名中间省略（适用于聊天窗口文件卡片）。
 * 规则：文件名前部分...后部分.文件名后缀
 * 如 "非常长的文件名报告文档.pdf" → "非常长的文...档.pdf"
 * @param fileName 原始文件名
 * @param maxLen 最大显示长度（不含后缀），默认 16
 */
export function truncateFileNameMiddle(fileName: string, maxLen = 16): string {
  if (!fileName) return '未知文件'
  if (fileName.length <= maxLen) return fileName

  const dotIdx = fileName.lastIndexOf('.')
  if (dotIdx < 0 || dotIdx === 0) {
    // 无后缀，直接截断前+后
    const keep = Math.floor((maxLen - 3) / 2)
    return fileName.slice(0, keep) + '...' + fileName.slice(-keep)
  }

  const name = fileName.substring(0, dotIdx)
  const ext = fileName.substring(dotIdx) // 含 "."
  const nameMaxLen = maxLen - ext.length
  if (nameMaxLen < 4) return fileName // 后缀太长，不截断

  if (name.length <= nameMaxLen) return fileName

  const keep = Math.floor((nameMaxLen - 3) / 2)
  return name.slice(0, keep) + '...' + name.slice(-keep) + ext
}

/** 生成简易唯一 ID */
export function generateId(prefix = 'id'): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

/** 从昵称提取首字母用于头像占位 */
export function getAvatarText(name: string): string {
  if (!name) return 'U'
  // 中文取第一个字；英文取首字母
  const first = name.trim().charAt(0)
  return first.toUpperCase()
}

/** 判断 avatar 字符串是否为图片 URL（而非首字母文本） */
export function isAvatarUrl(avatar?: string): boolean {
  if (!avatar) return false
  return avatar.startsWith('/') || avatar.startsWith('http') || avatar.startsWith('data:')
}

/**
 * 解析上传文件的访问 URL
 * 开发环境下 VITE_API_BASE 为后端绝对地址(https://localhost:8080/api)，
 * 需将 /uploads/... 拼接后端 origin；生产环境下为相对路径 /api，保持 /uploads/... 不变。
 */
export function resolveUploadUrl(url?: string): string {
  if (!url) return ''
  // 已经是绝对地址或 data URI，直接返回
  if (url.startsWith('http') || url.startsWith('data:')) return url
  // /uploads/... 等相对路径：拼接后端 origin
  if (url.startsWith('/')) {
    const apiBase = import.meta.env.VITE_API_BASE || ''
    const origin = apiBase.replace(/\/api\/?$/, '')
    return origin ? origin + url : url
  }
  return url
}

/** 文件大小格式化 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/**
 * 取单个字符的拼音首字母（A-Z），非汉字/非字母返回 '#'
 *
 * 实现原理：基于 pinyin-pro 库（覆盖 2 万+ 汉字，含多音字、生僻字），
 * 通过 pattern: 'first' 提取声母首字母。
 *
 * 注：拼音声母不含 I / U / V，故汉字不会返回这三项；
 * 多音字取 pinyin-pro 给出的首选读音。
 */
export function getPinyinFirstLetter(ch: string): string {
  if (!ch) return '#'
  const c = ch.charAt(0)
  // 数字或符号归入 #
  if (/[0-9]/.test(c)) return '#'
  // 英文字母直接返回大写
  if (/^[A-Za-z]$/.test(c)) return c.toUpperCase()
  // 非中文字符归入 #
  if (!/[\u4e00-\u9fa5]/.test(c)) return '#'
  // 汉字：交给 pinyin-pro 处理（pattern: 'first' 取声母首字母，toneType: 'none' 去声调）
  const first = pinyin(c, { pattern: 'first', toneType: 'none', type: 'array' })
  // pinyin-pro 对非汉字返回原字符；多音字返回数组，取第一个
  const letter = Array.isArray(first) ? first[0] : first
  if (typeof letter !== 'string' || !/^[A-Za-z]$/.test(letter)) return '#'
  return letter.toUpperCase()
}

/** 按名字首字母分组（A-Z + #），字母段按 A→Z→# 顺序返回 */
export interface FriendGroup {
  letter: string
  friends: Friend[]
}
export function groupFriendsByLetter(friends: Friend[]): FriendGroup[] {
  const buckets: Record<string, Friend[]> = {}
  friends.forEach((f) => {
    const letter = getPinyinFirstLetter(f.name)
    if (!buckets[letter]) buckets[letter] = []
    buckets[letter].push(f)
  })
  // 组内按名字稳定排序
  Object.keys(buckets).forEach((k) => {
    buckets[k].sort((a, b) => a.name.localeCompare(b.name, 'zh'))
  })
  // 字母段排序：A-Z 在前，# 在最后
  const letters = Object.keys(buckets).sort((a, b) => {
    if (a === '#') return 1
    if (b === '#') return -1
    return a.localeCompare(b)
  })
  return letters.map((letter) => ({ letter, friends: buckets[letter] }))
}
