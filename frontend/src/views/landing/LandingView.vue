<script setup lang="ts">
/**
 * LandingView · 官网首页主容器
 *
 * 组合所有子组件，管理：
 * - body 滚动约束（官网可滚动，聊天页锁定）
 * - 锚点平滑滚动
 * - CTA 路由跳转
 */
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

import AppNav from './components/AppNav.vue'
import HeroSection from './components/HeroSection.vue'
import AIDemo from './components/AIDemo.vue'
import Capabilities from './components/Capabilities.vue'
import Architecture from './components/Architecture.vue'
import TechStack from './components/TechStack.vue'
import QuickStart from './components/QuickStart.vue'
import AppFooter from './components/AppFooter.vue'

const router = useRouter()
const authStore = useAuthStore()

/** 锚点平滑滚动 */
function scrollTo(id: string): void {
  if (id === 'top') {
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** CTA 路由跳转 */
function goAuth(): void {
  router.push('/login')
}

function goChat(): void {
  if (authStore.isLoggedIn) {
    router.push('/chat')
  } else {
    router.push({ name: 'login', query: { redirect: '/chat' } })
  }
}

/** Hero CTA 处理 */
function handleHeroCta(action: 'start' | 'demo'): void {
  if (action === 'start') {
    goAuth()
  } else {
    goChat()
  }
}

onMounted(() => {
  document.body.style.overflow = 'auto'
  document.body.style.height = 'auto'
})

onUnmounted(() => {
  document.body.style.overflow = ''
  document.body.style.height = ''
})
</script>

<template>
  <div class="landing" id="top">
    <!-- 导航栏 -->
    <AppNav @nav="scrollTo" @cta="goAuth" />

    <!-- Hero 主视觉区 -->
    <HeroSection @cta="handleHeroCta" />

    <!-- 核心能力区 -->
    <Capabilities />

    <!-- @AI 召唤交互演示区 -->
    <AIDemo />

    <!-- 系统架构区 -->
    <Architecture />

    <!-- 技术栈区 -->
    <TechStack />

    <!-- 快速开始区 -->
    <QuickStart @cta="goAuth" />

    <!-- 页脚 -->
    <AppFooter />
  </div>
</template>
