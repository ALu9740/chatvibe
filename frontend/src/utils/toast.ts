/**
 * Toast 轻提示组件
 * 严格对齐 prototype/assets/app.js 的 showToast 实现
 * 标准的"带图标的顶部通知"组件：固定在右上角，带左侧色条 + 圆形图标
 */

type ToastType = 'success' | 'error' | 'warning' | 'info'

const ICONS: Record<ToastType, string> = {
  success: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>',
  error: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>',
  warning: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path></svg>',
  info: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line><circle cx="12" cy="12" r="10"></circle></svg>'
}

let container: HTMLElement | null = null

function getContainer(): HTMLElement {
  if (container && document.body.contains(container)) return container
  container = document.createElement('div')
  container.className = 'toast-container'
  document.body.appendChild(container)
  return container
}

function show(type: ToastType, title: string, msg?: string): void {
  const c = getContainer()
  const toast = document.createElement('div')
  toast.className = `toast toast-${type}`
  toast.innerHTML = `
    <div class="toast-icon">${ICONS[type] || ICONS.info}</div>
    <div class="toast-content">
      <div class="toast-title">${title || ''}</div>
      ${msg ? `<div class="toast-msg">${msg}</div>` : ''}
    </div>
  `
  c.appendChild(toast)
  setTimeout(() => {
    toast.classList.add('removing')
    setTimeout(() => toast.remove(), 260)
  }, 3000)
}

export const toast = {
  success: (title: string, msg?: string) => show('success', title, msg),
  error: (title: string, msg?: string) => show('error', title, msg),
  warning: (title: string, msg?: string) => show('warning', title, msg),
  info: (title: string, msg?: string) => show('info', title, msg)
}

export default toast
