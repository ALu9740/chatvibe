<script setup lang="ts">
// ChatVibe 应用根组件
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { initNotify } from '@/utils/notify'


const authStore = useAuthStore()

// 应用启动时尝试拉取用户信息（若已登录）
onMounted(async () => {
  if (authStore.isLoggedIn) {
    await initNotify()
  }
})
</script>

<template>
  <router-view />
</template>

<style scoped>
/* 根组件无需额外样式，全部使用全局 style.scss */
</style>
