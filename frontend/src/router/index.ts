import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'landing',
    component: () => import('@/views/landing/LandingView.vue'),
    meta: { title: 'ChatVibe · 让沟通更有温度', requiresAuth: false }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: '注册', requiresAuth: false }
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/auth/ForgotPasswordView.vue'),
    meta: { title: '找回密码', requiresAuth: false }
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/views/chat/ChatView.vue'),
    meta: { title: '聊天', requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/profile/ProfileView.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  // 兜底 404
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 切换路由时滚动到顶部
  scrollBehavior() {
    return { top: 0 }
  }
})

// 全局前置守卫：登录态校验
router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} · ChatVibe`
  }
  // 需要登录但未登录 → 跳登录页
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }
  // 已登录访问登录/注册/找回密码页 → 跳聊天页
  if ((to.name === 'login' || to.name === 'register' || to.name === 'forgot-password') && authStore.isLoggedIn) {
    next({ name: 'chat' })
    return
  }
  next()
})

export default router
