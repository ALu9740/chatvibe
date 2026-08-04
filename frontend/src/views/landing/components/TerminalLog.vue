<script setup lang="ts">
/** 终端日志打字机组件 —— 用于 Architecture 区域展示 @AI 请求全链路 */
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'

interface LogLine {
  text: string
  type: 'request' | 'detect' | 'route' | 'model' | 'ws' | 'response'
}

const props = withDefaults(defineProps<{
  lines: LogLine[]
  speed?: number // 毫秒/字符
  active: boolean
}>(), {
  speed: 35,
  active: false
})

const emit = defineEmits<{
  (e: 'complete'): void
}>()

const displayedLines = ref<string[]>([])
const currentLine = ref('')
const currentLineIndex = ref(0)
const currentCharIndex = ref(0)
const isTyping = ref(false)
const terminalBody = ref<HTMLElement | null>(null)

let timer: ReturnType<typeof setInterval> | null = null

function typeNext(): void {
  if (currentLineIndex.value >= props.lines.length) {
    isTyping.value = false
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    emit('complete')
    return
  }

  const fullLine = props.lines[currentLineIndex.value]
  const text = formatLine(fullLine)

  if (currentCharIndex.value < text.length) {
    currentLine.value = text.slice(0, currentCharIndex.value + 1)
    currentCharIndex.value++
    scrollToBottom()
  } else {
    displayedLines.value.push(text)
    currentLine.value = ''
    currentLineIndex.value++
    currentCharIndex.value = 0
  }
}

function formatLine(line: LogLine): string {
  const prefix = getPrefix(line.type)
  return `${prefix}${line.text}`
}

function getPrefix(type: LogLine['type']): string {
  const prefixes: Record<LogLine['type'], string> = {
    request: '> ',
    detect: '> ',
    route: '> ',
    model: '> ',
    ws: '> ',
    response: '> '
  }
  return prefixes[type] || '> '
}

function getLineClass(type: LogLine['type']): string {
  return `log-${type}`
}

function start(): void {
  reset()
  isTyping.value = true
  timer = setInterval(typeNext, props.speed)
}

function reset(): void {
  displayedLines.value = []
  currentLine.value = ''
  currentLineIndex.value = 0
  currentCharIndex.value = 0
  isTyping.value = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function scrollToBottom(): void {
  nextTick(() => {
    if (terminalBody.value) {
      terminalBody.value.scrollTop = terminalBody.value.scrollHeight
    }
  })
}

watch(() => props.active, (val) => {
  if (val) {
    start()
  } else {
    reset()
  }
})

onMounted(() => {
  if (props.active) start()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

defineExpose({ reset, start })
</script>

<template>
  <div ref="terminalBody" class="terminal-log">
    <div
      v-for="(line, idx) in displayedLines"
      :key="idx"
      class="terminal-log__line"
      :class="getLineClass(props.lines[idx]?.type || 'request')"
    >
      <span class="terminal-log__prefix">›</span>
      <span class="terminal-log__content">{{ line.replace(/^> /, '') }}</span>
    </div>
    <div v-if="isTyping && currentLine" class="terminal-log__line terminal-log__line--current">
      <span class="terminal-log__prefix">›</span>
      <span class="terminal-log__content">{{ currentLine.replace(/^> /, '') }}</span>
      <span class="terminal-log__cursor">▊</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.terminal-log {
  font-family: $font-mono;
  font-size: 13px;
  line-height: 1.8;
  padding: 20px 24px;
  overflow-y: auto;
  max-height: 360px;
  scrollbar-width: thin;
  scrollbar-color: var(--term-scrollbar) transparent;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--term-scrollbar);
    border-radius: $r-full;
  }

  &__line {
    display: flex;
    align-items: baseline;
    gap: 6px;
    opacity: 0;
    animation: fadeIn 0.3s $ease-smooth forwards;
  }

  &__prefix {
    color: var(--term-prefix);
    font-weight: 600;
  }

  &__content {
    color: var(--term-text);
    white-space: pre-wrap;
    word-break: break-all;
  }

  &__cursor {
    color: var(--term-cursor);
    animation: blink 1s steps(1) infinite;
    margin-left: 2px;
  }

  .log-request { .terminal-log__content { color: var(--term-log-request); } }
  .log-detect { .terminal-log__content { color: var(--term-log-detect); font-weight: 600; } }
  .log-route { .terminal-log__content { color: var(--term-log-route); } }
  .log-model { .terminal-log__content { color: var(--term-log-model); } }
  .log-ws { .terminal-log__content { color: var(--term-log-ws); } }
  .log-response { .terminal-log__content { color: var(--term-log-response); font-weight: 600; } }
}
</style>
