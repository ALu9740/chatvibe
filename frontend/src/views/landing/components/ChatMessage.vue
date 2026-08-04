<script setup lang="ts">
/** 聊天消息气泡组件 —— 用于 HeroSection 聊天模拟器和 AIDemo */
export interface ChatMsg {
  id: number
  sender: string
  avatar: string
  content: string
  isAI?: boolean
  isTodoList?: boolean
  mention?: string
  timestamp?: string
}

const props = withDefaults(defineProps<{
  msg: ChatMsg
  align?: 'left' | 'right'
}>(), {
  align: 'left'
})

/** 格式化文本：@提及高亮、**加粗** */
function formatLine(text: string): string {
  return text
    .replace(/@(\S+)/g, '<span class="mention">@$1</span>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
}
</script>

<template>
  <div class="chat-msg" :class="{ 'is-ai': props.msg.isAI, 'is-self': props.align === 'right' }">
    <div class="chat-msg__avatar" :class="{ 'avatar-ai': props.msg.isAI }">
      <span v-if="props.msg.isAI">AI</span>
      <span v-else>{{ props.msg.avatar }}</span>
    </div>

    <div class="chat-msg__body">
      <div class="chat-msg__meta">
        <span class="chat-msg__name">{{ props.msg.sender }}</span>
        <span v-if="props.msg.isAI" class="chat-msg__badge">智能助手</span>
        <span v-if="props.msg.timestamp" class="chat-msg__time">{{ props.msg.timestamp }}</span>
      </div>
      <div class="chat-msg__bubble" :class="{ 'bubble-todo': props.msg.isTodoList }">
        <template v-if="props.msg.isTodoList">
          <div class="todo-header">{{ props.msg.content.split('\n')[0] }}</div>
          <ul class="todo-list">
            <li v-for="(line, i) in props.msg.content.split('\n').slice(1)" :key="i">
              <span class="todo-check"></span>
              <span v-html="formatLine(line)"></span>
            </li>
          </ul>
        </template>
        <p v-else v-html="formatLine(props.msg.content)"></p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-msg {
  display: flex;
  gap: 10px;
  align-items: flex-start;

  &.is-self {
    flex-direction: row-reverse;

    .chat-msg__bubble {
      background: $c-primary;
      color: #fff;
    }
  }

  &__avatar {
    width: 36px;
    height: 36px;
    border-radius: $r-full;
    background: $grad-primary;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 700;
    flex-shrink: 0;

    &.avatar-ai {
      background: $grad-ai;
      box-shadow: $shadow-glow-ai;
      animation: floatGlow 3s ease-in-out infinite;
    }
  }

  &__body {
    max-width: 75%;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }

  &__name {
    font-size: 13px;
    font-weight: 600;
    color: var(--landing-text);
  }

  &__badge {
    font-size: 11px;
    padding: 2px 8px;
    border-radius: $r-full;
    background: rgba(124, 58, 237, 0.12);
    color: $c-ai;
    font-weight: 600;
  }

  &__time {
    font-size: 11px;
    color: var(--landing-text-muted);
  }

  &__bubble {
    padding: 10px 14px;
    border-radius: $r-md $r-lg $r-lg $r-sm;
    background: #F1F5F9;
    color: var(--landing-text);
    font-size: 14px;
    line-height: 1.6;
    word-break: break-word;

    .is-ai & {
      background: linear-gradient(135deg, rgba(124, 58, 237, 0.06), rgba(167, 139, 250, 0.04));
      border: 1px solid rgba(124, 58, 237, 0.15);
      box-shadow: $shadow-glow-ai;
    }
  }
}

/* 暗色模式：气泡背景改为深色，确保文字可见 */
[data-theme='dark'] {
  .chat-msg__bubble {
    background: rgba(30, 41, 59, 0.6);
    color: #E2E8F0;

    &.is-self,
    .is-self & {
      background: $c-primary;
      color: #fff;
    }
  }
}

:deep(.mention) {
  color: $c-ai;
  font-weight: 600;
  background: rgba(124, 58, 237, 0.08);
  padding: 1px 6px;
  border-radius: 4px;
}

:deep(strong) {
  font-weight: 700;
}

.bubble-todo {
  .todo-header {
    font-weight: 700;
    margin-bottom: 8px;
    font-size: 14px;
  }

  .todo-list {
    list-style: none;
    padding: 0;
    margin: 0;

    li {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      padding: 4px 0;
      font-size: 13px;
      line-height: 1.5;
    }

    .todo-check {
      width: 16px;
      height: 16px;
      border-radius: 4px;
      border: 1.5px solid rgba(124, 58, 237, 0.3);
      flex-shrink: 0;
      margin-top: 2px;
      position: relative;

      &::after {
        content: '';
        position: absolute;
        left: 4px;
        top: 1px;
        width: 5px;
        height: 9px;
        border: solid $c-ai;
        border-width: 0 2px 2px 0;
        transform: rotate(45deg);
      }
    }
  }
}
</style>
