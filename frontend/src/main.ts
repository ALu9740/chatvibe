import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 官方暗色模式 CSS 变量（通过 <html>.dark 类启用，覆盖所有 el-* 组件）
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'
import './style.scss'

const app = createApp(App)

// 注册 Pinia
app.use(createPinia())
// 注册路由
app.use(router)
// 注册 Element Plus
app.use(ElementPlus)

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 初始化主题（在 mount 前应用，避免主题闪烁；同时启动 auto 模式定时检查）
useThemeStore().initTheme()

app.mount('#app')
