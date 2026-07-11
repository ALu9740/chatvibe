<script setup lang="ts">
// 聊天主布局：负责建立 WebSocket 连接，提供插槽给具体页面
import { onMounted, onUnmounted } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const { connect, disconnect } = useWebSocket()

onMounted(async () => {
  // 拉取会话列表后建立 WebSocket
  try {
    await chatStore.fetchConversations()
  } catch (e) {
    // 拉取失败不阻塞页面渲染，用户仍可看到聊天界面
    console.error('[ChatLayout] 拉取会话列表失败:', e)
  }
  try {
    connect()
  } catch (e) {
    console.error('[ChatLayout] WebSocket 连接失败:', e)
  }
})

onUnmounted(() => {
  disconnect()
  chatStore.reset()
})
</script>

<template>
  <slot />
</template>
