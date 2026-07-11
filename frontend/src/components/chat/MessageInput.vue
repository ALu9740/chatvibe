<script setup lang="ts">
// 消息输入区组件：支持文本输入、@AI 激活（图标点击与输入@AI统一）、Enter 发送、emoji 选择器
import { ref, nextTick } from 'vue'
import { toast } from '@/utils/toast'
import EmojiPicker from './EmojiPicker.vue'

const props = defineProps<{
  /** 是否禁用发送（AI 正在生成时） */
  disabled?: boolean
  /** 占位提示 */
  placeholder?: string
  /** 群组是否已解散（true 时显示"此群组已被解散"，禁止输入） */
  dissolved?: boolean
}>()

const emit = defineEmits<{
  (e: 'send', content: string, mentionAI: boolean): void
  (e: 'upload-image', file: File): void
  (e: 'upload-file', file: File): void
}>()

const text = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
// 是否检测到 @AI（单一数据源：输入框中有 @AI 即为激活）
const mentionAI = ref(false)
// emoji 面板开关
const showEmojiPicker = ref(false)

// 自动调整输入框高度
function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

// 检测 @AI 关键词（不区分大小写）
function detectMention() {
  mentionAI.value = /@ai/i.test(text.value)
}

function onInput() {
  autoResize()
  detectMention()
}

// 发送消息
function handleSend() {
  const content = text.value.trim()
  if (!content) {
    toast.warning('请输入消息内容', '消息不能为空')
    return
  }
  if (props.disabled) {
    toast.info('AI 生成中', 'AI 正在生成回复，请稍候')
    return
  }
  const wasMentionAI = mentionAI.value
  // 保留 @AI 前缀在消息内容中（便于其他成员识别是谁向 AI 提问）
  // 仅校验 @AI 后是否有实际问题内容
  if (wasMentionAI) {
    const questionOnly = content.replace(/@AI\s*/gi, '').trim()
    if (!questionOnly) {
      toast.warning('请输入问题', '@AI 后请输入具体问题')
      return
    }
  }
  emit('send', content, wasMentionAI)
  text.value = ''
  mentionAI.value = false
  nextTick(autoResize)
}

// 键盘事件：Enter 发送，Shift+Enter 换行
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

// 触发图片上传
function triggerImageUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    if (!file.type.startsWith('image/')) {
      toast.warning('请选择图片文件', '仅支持 JPG / PNG / GIF 等图片格式')
      return
    }
    emit('upload-image', file)
  }
  input.value = ''
}

// 触发文件上传
function triggerFileUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    emit('upload-file', file)
  }
  input.value = ''
}

// 切换 @AI 激活状态
// 激活时：在文本开头插入 "@AI "
// 取消时：移除文本中的 "@AI " 前缀
function toggleAI() {
  if (mentionAI.value) {
    // 已激活 → 移除 @AI
    text.value = text.value.replace(/@AI\s*/i, '')
    detectMention()
  } else {
    // 未激活 → 在开头插入 @AI
    text.value = '@AI ' + text.value
    detectMention()
  }
  nextTick(() => {
    autoResize()
    textareaRef.value?.focus()
    // 光标移到末尾
    const el = textareaRef.value
    if (el) {
      const len = text.value.length
      el.setSelectionRange(len, len)
    }
  })
}

// 移除 @AI 标记（点击芯片上的 ×）
function removeMentionAI() {
  text.value = text.value.replace(/@AI\s*/i, '')
  detectMention()
  nextTick(() => textareaRef.value?.focus())
}

// 切换 emoji 面板
function toggleEmojiPicker() {
  showEmojiPicker.value = !showEmojiPicker.value
}

// 选中 emoji 时插入到光标位置
function onEmojiSelect(emoji: string) {
  if (!emoji) return
  const el = textareaRef.value
  if (!el) {
    text.value += emoji
    return
  }
  const start = el.selectionStart ?? text.value.length
  const end = el.selectionEnd ?? text.value.length
  text.value = text.value.slice(0, start) + emoji + text.value.slice(end)
  nextTick(() => {
    el.focus()
    const pos = start + emoji.length
    el.setSelectionRange(pos, pos)
    autoResize()
    detectMention()
  })
}

// 点击外部关闭 emoji 面板
function onEmojiPanelBlur() {
  showEmojiPicker.value = false
}

defineExpose({ focus: () => textareaRef.value?.focus() })
</script>

<template>
  <div class="chat-input-area">
    <!-- 群组已解散提示：替代输入区，禁止发送任何消息 -->
    <div v-if="dissolved" class="dissolved-banner">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10"></circle>
        <line x1="15" y1="9" x2="9" y2="15"></line>
        <line x1="9" y1="9" x2="15" y2="15"></line>
      </svg>
      此群组已被解散
    </div>

    <template v-else>
      <!-- @AI 高亮提示条 -->
      <div class="ai-chip-bar">
        <span v-if="mentionAI" class="ai-chip">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <rect x="3" y="11" width="18" height="10" rx="2"></rect>
            <circle cx="12" cy="5" r="2"></circle>
            <path d="M12 7v4"></path>
          </svg>
          @AI 已激活
          <span class="remove" @click="removeMentionAI">×</span>
        </span>
      </div>

    <!-- emoji 面板 -->
    <div v-if="showEmojiPicker" class="emoji-panel-wrapper">
      <div class="emoji-backdrop" @click="onEmojiPanelBlur"></div>
      <div class="emoji-panel">
        <EmojiPicker @select="onEmojiSelect" @close="onEmojiPanelBlur" />
      </div>
    </div>

    <!-- 输入工具栏 -->
    <div class="input-toolbar">
      <!-- 图片上传 -->
      <label class="input-tool-btn" title="发送图片">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
          <circle cx="8.5" cy="8.5" r="1.5"></circle>
          <polyline points="21 15 16 10 5 21"></polyline>
        </svg>
        <input type="file" accept="image/*" style="display: none" @change="triggerImageUpload" />
      </label>

      <!-- 文件上传 -->
      <label class="input-tool-btn" title="发送文件">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"></path>
        </svg>
        <input type="file" style="display: none" @change="triggerFileUpload" />
      </label>

      <!-- 表情 -->
      <button class="input-tool-btn" :class="{ active: showEmojiPicker }" title="表情" @click="toggleEmojiPicker">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"></circle>
          <path d="M8 14s1.5 2 4 2 4-2 4-2"></path>
          <line x1="9" y1="9" x2="9.01" y2="9"></line>
          <line x1="15" y1="9" x2="15.01" y2="9"></line>
        </svg>
      </button>

      <!-- @AI 快捷按钮：点击切换 @AI 激活状态 -->
      <button class="input-tool-btn ai-btn" :class="{ active: mentionAI }" title="@AI 召唤助手" @click="toggleAI">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="11" width="18" height="10" rx="2"></rect>
          <circle cx="12" cy="5" r="2"></circle>
          <path d="M12 7v4"></path>
        </svg>
      </button>

      <!-- 文本输入 -->
      <textarea
        ref="textareaRef"
        v-model="text"
        class="msg-textarea"
        :placeholder="placeholder || '输入消息，Enter 发送，Shift+Enter 换行；输入 @AI 召唤智能助手'"
        rows="1"
        :disabled="disabled"
        @input="onInput"
        @keydown="onKeydown"
      ></textarea>

      <!-- 发送按钮 -->
      <button class="send-btn" :disabled="disabled || !text.trim()" title="发送" @click="handleSend">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13"></line>
          <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
        </svg>
      </button>
    </div>
    </template>
  </div>
</template>

<style scoped>
.input-tool-btn.active {
  color: var(--c-primary);
  background: rgba(37, 99, 235, 0.08);
}

/* 群组已解散提示横幅：替代输入区，居中显示禁言提示 */
.dissolved-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 20px;
  margin: 12px 20px 16px;
  background: var(--c-bg-soft);
  color: var(--c-text-muted);
  border: 1px dashed var(--c-border);
  border-radius: var(--r-md);
  font-size: 13px;
  font-weight: 500;
  cursor: not-allowed;
  user-select: none;
}

/* emoji 面板：定位在输入区上方 */
.emoji-panel-wrapper {
  position: absolute;
  bottom: 100%;
  left: 0;
  right: 0;
  z-index: 999;
  pointer-events: none;
}

.emoji-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 998;
  pointer-events: auto;
}

.emoji-panel {
  position: absolute;
  bottom: 8px;
  left: 22px;
  z-index: 999;
  pointer-events: auto;
}
</style>
