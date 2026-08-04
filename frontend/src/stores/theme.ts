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
    const hour = new Date().getHours()
    return hour < LIGHT_START || hour >= DARK_START
  })

  /** 应用主题到 document */
  function applyTheme(): void {
    const root = document.documentElement
    root.setAttribute('data-theme', isDark.value ? 'dark' : 'light')
    root.classList.toggle('dark', isDark.value)
  }

  /** 设置主题模式 */
  function setMode(newMode: ThemeMode): void {
    mode.value = newMode
    localStorage.setItem(STORAGE_KEY, newMode)
    applyTheme()
  }

  /** 在 light / dark 之间直接切换（用于 Switch 组件） */
  function toggleDark(): void {
    setMode(isDark.value ? 'light' : 'dark')
  }

  /** 初始化主题 */
  function initTheme(): void {
    applyTheme()
    setInterval(() => {
      if (mode.value === 'auto') applyTheme()
    }, 60000)
  }

  return { mode, isDark, setMode, toggleDark, initTheme }
})
