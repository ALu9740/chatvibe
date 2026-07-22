<script setup lang="ts">
// 消息气泡组件：根据发送者类型渲染不同样式
import { computed } from 'vue'
import type { Message } from '@/types'
import { getAvatarText, isAvatarUrl, resolveUploadUrl, truncateFileNameMiddle } from '@/utils/format'

const props = defineProps<{
  message: Message
  /** 是否显示头像（群聊场景） */
  showAvatar?: boolean
}>()

const emit = defineEmits<{
  (e: 'contextmenu', payload: { event: MouseEvent; message: Message }): void
  (e: 'retry', payload: { message: Message }): void
}>()

// 头像首字母
const avatarText = computed(() => props.message.avatar || getAvatarText(props.message.name || 'U'))
// 头像是否为图片 URL
const isAvatarImg = computed(() => isAvatarUrl(props.message.avatar))

// 是否为系统消息
const isSystem = computed(() => props.message.sender === 'system' || props.message.type === 'SYSTEM')

// 是否为 AI 消息
const isAI = computed(() => props.message.sender === 'ai' || props.message.type === 'AI')

// 是否为图片消息
const isImage = computed(() => props.message.type === 'IMAGE')

// 是否为文件消息
const isFile = computed(() => props.message.type === 'FILE')

// 是否为当前用户发送
const isSelf = computed(() => props.message.sender === 'self')

// AI 流式刚刚开始：内容为空但仍在 streaming
const aiTyping = computed(() => isAI.value && props.message.streaming && !props.message.content)

// AI 消息展示文本
const aiContent = computed(() => props.message.content)

// 解析 extra JSON
const extraData = computed(() => {
  if (!props.message.extra) return null
  try {
    return JSON.parse(props.message.extra)
  } catch {
    return null
  }
})

// 文件名
const fileName = computed(() => {
  if (extraData.value?.fileName) return extraData.value.fileName
  // 从 URL 中提取文件名
  const url = props.message.content || ''
  const parts = url.split('/')
  return parts[parts.length - 1] || '未知文件'
})

// 格式化文件大小
const fileSizeText = computed(() => {
  const size = extraData.value?.fileSize
  if (!size) return ''
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
})

// 文件扩展名（用于图标显示）
const fileExt = computed(() => {
  const name = fileName.value
  const dot = name.lastIndexOf('.')
  if (dot < 0) return ''
  return name.substring(dot + 1).toUpperCase()
})

// 聊天窗口中显示的文件名（过长则中间省略：前部分...后部分.后缀）
const displayName = computed(() => truncateFileNameMiddle(fileName.value))

function onContextmenu(e: MouseEvent) {
  if (isSystem.value) return
  e.preventDefault()
  emit('contextmenu', { event: e, message: props.message })
}

function onRetry() {
  emit('retry', { message: props.message })
}
</script>

<template>
  <!-- 系统消息：居中显示 -->
  <div v-if="isSystem" class="msg-system">
    {{ message.content }}
  </div>

  <!-- 普通消息行 -->
  <div
    v-else
    class="msg-row"
    :class="{ self: isSelf, ai: isAI, other: !isSelf && !isAI }"
    @contextmenu="onContextmenu"
  >
    <!-- 头像（自己消息不显示在左，AI 始终显示紫色头像） -->
    <div
      v-if="(showAvatar || isAI) && !isSelf"
      class="avatar msg-avatar"
      :class="{ 'size-md': true, 'ai-avatar': isAI }"
      :style="!isAI && !isAvatarImg && message.color ? { background: message.color } : {}"
    >
      <img v-if="isAvatarImg" :src="resolveUploadUrl(message.avatar)" alt="头像" />
      <template v-else>{{ avatarText }}</template>
    </div>

    <div class="flex-col" style="max-width: 100%">
      <!-- 发送者昵称（群聊他人 / AI 助手） -->
      <div
        v-if="message.name && !isSelf"
        class="text-xs text-muted"
        style="margin-bottom: 2px; padding: 0 4px"
      >
        {{ message.name }}
      </div>

      <!-- 气泡 -->
      <div class="msg-bubble">
        <!-- 图片消息 -->
        <div v-if="isImage" class="msg-image">
          <a :href="resolveUploadUrl(message.content)" target="_blank">
            <img :src="resolveUploadUrl(message.content)" alt="图片消息" />
          </a>
        </div>
        <!-- 文件消息 -->
        <div v-else-if="isFile" class="msg-file">
          <a :href="resolveUploadUrl(message.content)" :download="fileName" target="_blank" class="file-card">
            <div class="file-icon">{{ fileExt || 'FILE' }}</div>
            <div class="file-info">
              <div class="file-name" :title="fileName">{{ displayName }}</div>
              <div class="file-size">{{ fileSizeText }}</div>
            </div>
            <svg class="file-download-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
              <polyline points="7 10 12 15 17 10"></polyline>
              <line x1="12" y1="15" x2="12" y2="3"></line>
            </svg>
          </a>
        </div>
        <!-- AI 正在输入指示器（流式刚开始，内容为空） -->
        <div v-else-if="aiTyping" class="ai-typing">
          <span></span>
          <span></span>
          <span></span>
        </div>
        <!-- 文本 / AI 消息 -->
        <template v-else>
          {{ aiContent }}
          <span v-if="isAI && message.streaming" class="typing-cursor"></span>
        </template>
      </div>
    </div>

    <!-- 发送状态指示器（仅自己消息，在气泡左侧） -->
    <div v-if="isSelf && message.sendStatus === 'sending'" class="msg-status-sending" title="发送中">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="sending-icon">
        <circle cx="12" cy="12" r="10"></circle>
        <polyline points="12 6 12 12 16 14"></polyline>
      </svg>
    </div>
    <div v-if="isSelf && message.sendStatus === 'failed'" class="msg-status-failed" title="发送失败，点击重试" @click.stop="onRetry">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
        <circle cx="12" cy="12" r="10"></circle>
        <path d="M12 7v6" stroke="#fff" stroke-width="2" stroke-linecap="round"></path>
        <circle cx="12" cy="16.5" r="1" fill="#fff"></circle>
      </svg>
    </div>
  </div>
</template>

<style scoped>
/* 样式继承自全局 style.scss，组件内不再重复定义 */
.flex-col { display: flex; flex-direction: column; }
.text-xs { font-size: 12px; }
.text-muted { color: var(--c-text-muted); }

/* 文件消息卡片 */
.msg-file {
  min-width: 220px;
}

.file-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  text-decoration: none;
  color: inherit;
  transition: background 0.15s;
  cursor: pointer;
}

.file-card:hover {
  background: rgba(0, 0, 0, 0.08);
}

.file-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: var(--c-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
}

.file-size {
  font-size: 11px;
  color: var(--c-text-muted);
  margin-top: 2px;
}

.file-download-icon {
  flex-shrink: 0;
  color: var(--c-text-muted);
}

/* 发送状态指示器 */
.msg-status-sending {
  display: flex;
  align-items: center;
  color: var(--c-text-muted);
  flex-shrink: 0;
  align-self: center;
}

.sending-icon {
  animation: msg-spin 1s linear infinite;
}

@keyframes msg-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.msg-status-failed {
  display: flex;
  align-items: center;
  color: #f56c6c;
  flex-shrink: 0;
  align-self: center;
  cursor: pointer;
  transition: opacity 0.15s;
}

.msg-status-failed:hover {
  opacity: 0.7;
}
</style>
