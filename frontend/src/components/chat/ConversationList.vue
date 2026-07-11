<script setup lang="ts">
// 会话列表组件：渲染会话项，支持点击选中、右键菜单
import type { Conversation } from '@/types'
import { getAvatarText, isAvatarUrl, resolveUploadUrl, formatMessageTime } from '@/utils/format'

const props = defineProps<{
  conversations: Conversation[]
  activeId?: string
}>()

const emit = defineEmits<{
  (e: 'select', id: string): void
  (e: 'contextmenu', payload: { event: MouseEvent; conversation: Conversation }): void
}>()

function onSelect(conv: Conversation) {
  emit('select', conv.id)
}

function onContextmenu(e: MouseEvent, conv: Conversation) {
  e.preventDefault()
  emit('contextmenu', { event: e, conversation: conv })
}

// 预览文本：后端已将图片/文件消息的 last_message 存为预览文本（如 [图片]、[文件]文件名）
// 直接展示即可；@AI 标签由模板单独处理
function preview(conv: Conversation): string {
  if (!conv.lastMessage) return ''
  // 兼容 mock 模式：若 lastMessage 是 URL 且无类型信息，尝试转换
  if (conv.lastMessageType === 1) return '[图片]'
  if (conv.lastMessageType === 3) {
    // 后端已存为 [文件]文件名 格式，直接返回
    if (conv.lastMessage.startsWith('[文件]')) return conv.lastMessage
    return '[文件]'
  }
  return conv.lastMessage
}

// 私聊会话根据对方状态返回状态点样式类
function statusDotClass(conv: Conversation): string | null {
  if (conv.type !== 'private' || conv.peerStatus === undefined) return null
  switch (conv.peerStatus) {
    case 1: return 'online'
    case 2: return 'busy'
    case 3: return 'away'
    default: return 'offline'
  }
}

// 格式化会话时间（兼容 "yyyy-MM-dd HH:mm:ss" 与 ISO 格式）
function formatTime(time?: string): string {
  if (!time) return ''
  // 后端 Jackson 返回 "yyyy-MM-dd HH:mm:ss"，替换空格为 T 以兼容 Safari
  const normalized = time.includes(' ') && !time.includes('T') ? time.replace(' ', 'T') : time
  return formatMessageTime(normalized)
}
</script>

<template>
  <div class="conversation-list scroll-area">
    <div
      v-for="conv in props.conversations"
      :key="conv.id"
      class="conv-item"
      :class="{ active: conv.id === props.activeId, 'is-pinned': conv.pinned }"
      @click="onSelect(conv)"
      @contextmenu="onContextmenu($event, conv)"
    >
      <!-- 头像 -->
      <div
        class="avatar size-md"
        :class="{ 'ai-avatar': conv.isAI }"
        :style="!conv.isAI && !isAvatarUrl(conv.avatar) && conv.color ? { background: conv.color } : {}"
      >
        <img v-if="isAvatarUrl(conv.avatar)" :src="resolveUploadUrl(conv.avatar)" alt="头像" />
        <template v-else>{{ conv.avatar || getAvatarText(conv.name) }}</template>
        <span
          v-if="statusDotClass(conv)"
          class="status-dot"
          :class="statusDotClass(conv)"
        ></span>
      </div>

      <!-- 会话信息 -->
      <div class="conv-meta">
        <div class="conv-top">
          <span class="conv-name">
            {{ conv.name }}
            <span v-if="conv.isAI" class="chip chip-ai" style="margin-left: 4px">AI</span>
          </span>
          <span class="conv-time">{{ formatTime(conv.lastTime) }}</span>
        </div>
        <div class="conv-preview">
          <span v-if="conv.lastMessage?.includes('@AI')" class="ai-tag">@AI </span>
          {{ preview(conv) }}
          <!-- 免打扰图标：显示在预览/时间下方 -->
          <span v-if="conv.muted" class="mute-icon-inline" title="消息免打扰">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
              <line x1="3" y1="3" x2="21" y2="21"></line>
            </svg>
          </span>
        </div>
      </div>

      <!-- 未读计数：免打扰会话只显示红点，否则显示数字 -->
      <span v-if="conv.muted && conv.unread > 0" class="badge badge-dot" title="有新消息"></span>
      <span v-else-if="!conv.muted && conv.unread > 0" class="badge">{{ conv.unread > 99 ? '99+' : conv.unread }}</span>
    </div>

    <!-- 空状态 -->
    <div v-if="props.conversations.length === 0" class="text-center text-muted" style="padding: 40px 12px">
      暂无会话
    </div>
  </div>
</template>

<style scoped>
.text-center { text-align: center; }
.text-muted { color: var(--c-text-muted); }

/* 置顶会话视觉提示：左侧蓝色条 + 轻微背景 */
.conv-item.is-pinned {
  background: linear-gradient(90deg, rgba(37, 99, 235, 0.07), rgba(37, 99, 235, 0.01) 70%);
  box-shadow: inset 3px 0 0 var(--c-primary);
}

/* 免打扰内联图标：显示在消息预览旁 */
.mute-icon-inline {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
  margin-left: 4px;
  color: var(--c-text-muted);
  opacity: 0.8;
}

/* 免打扰会话只显示红点（无数字） */
.badge-dot {
  width: 8px !important;
  height: 8px !important;
  min-width: 8px !important;
  padding: 0 !important;
  font-size: 0;
  background: var(--c-danger, #ef4444);
  border-radius: 50%;
}
</style>
