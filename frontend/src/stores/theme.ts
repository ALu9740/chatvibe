import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

type ThemeMode = 'light' | 'dark' | 'auto'

const STORAGE_KEY = 'chatvibe-theme-mode'
const LIGHT_START = 6 // 6:00 开始白天
const DARK_START = 18 // 18:00 开始黑夜

/** 主题 store：白天/黑夜/自动切换 */
export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>((localStorage.getItem(STORAGE_KEY) as ThemeMode) || 'auto')

  /** 当前是否为暗色主题 */
  const isDark = computed(() => {
    if (mode.value === 'dark') return true
    if (mode.value === 'light') return false
    // auto：根据当前小时判断
    const hour = new Date().getHours()
    return hour < LIGHT_START || hour >= DARK_START
  })

  /** 应用主题到 document（同时切换 data-theme 属性与 .dark 类）*/
  function applyTheme() {
    const root = document.documentElement
    root.setAttribute('data-theme', isDark.value ? 'dark' : 'light')
    // .dark 类启用 Element Plus 官方暗色 css-vars
    root.classList.toggle('dark', isDark.value)
  }

  /** 设置主题模式 */
  function setMode(newMode: ThemeMode) {
    mode.value = newMode
    localStorage.setItem(STORAGE_KEY, newMode)
    applyTheme()
  }

  /** 初始化主题（应用启动时调用） */
  function initTheme() {
    applyTheme()
    // auto 模式下每分钟检查是否需要切换
    setInterval(() => {
      if (mode.value === 'auto') applyTheme()
    }, 60000)
  }

  return { mode, isDark, setMode, initTheme }
})
