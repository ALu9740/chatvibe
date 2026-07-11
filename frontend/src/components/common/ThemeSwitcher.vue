<script setup lang="ts">
// 主题切换器：白天/黑夜/自动 三选一下拉菜单
// 复用全局 .icon-btn-wrap / .icon-btn / .theme-menu 样式（定义在 style.scss）
import { ref, computed } from 'vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
const menuVisible = ref(false)

function toggleMenu() {
  menuVisible.value = !menuVisible.value
}

function handleChange(mode: 'light' | 'dark' | 'auto') {
  themeStore.setMode(mode)
  menuVisible.value = false
}

const themeIconName = computed(() => {
  if (themeStore.mode === 'light') return 'Sunny'
  if (themeStore.mode === 'dark') return 'Moon'
  return 'Monitor'
})
</script>

<template>
  <div class="icon-btn-wrap theme-trigger" @click.stop="toggleMenu">
    <a class="icon-btn" title="切换主题">
      <el-icon size="18"><component :is="themeIconName" /></el-icon>
    </a>
    <!-- 透明遮罩：点击外部关闭菜单 -->
    <div v-if="menuVisible" class="theme-switcher-mask" @click.stop="menuVisible = false"></div>
    <div v-if="menuVisible" class="theme-menu" @click.stop>
      <div class="theme-menu-item" :class="{ active: themeStore.mode === 'light' }" @click="handleChange('light')">
        <el-icon size="16"><Sunny /></el-icon>
        <span>白天</span>
      </div>
      <div class="theme-menu-item" :class="{ active: themeStore.mode === 'dark' }" @click="handleChange('dark')">
        <el-icon size="16"><Moon /></el-icon>
        <span>黑夜</span>
      </div>
      <div class="theme-menu-item" :class="{ active: themeStore.mode === 'auto' }" @click="handleChange('auto')">
        <el-icon size="16"><Monitor /></el-icon>
        <span>自动</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 透明遮罩：覆盖全屏，点击关闭下拉菜单 */
.theme-switcher-mask {
  position: fixed;
  inset: 0;
  z-index: 39;
  background: transparent;
}
</style>
