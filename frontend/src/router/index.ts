import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { getAdminToken } from '@/utils/request'
import type { AdminRole } from '@/types/admin'

// 路由 meta 类型声明
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    requiresAdmin?: boolean
    adminPage?: boolean
    /** 允许访问的管理员角色列表，未设置则所有管理员可访问 */
    roles?: AdminRole[]
  }
}

// 各角色允许访问的路由路径（用于无权限时重定向到第一个可用页）
const ROLE_HOME: Record<AdminRole, string> = {
  SUPER_ADMIN: '/admin',
  ADMIN: '/admin',
  OPERATOR: '/admin'
}

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
  // 管理员登录页（独立于 C 端登录）
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/admin/AdminLoginView.vue'),
    meta: { title: '管理员登录', requiresAuth: false, adminPage: false }
  },
  // 管理员后台
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { title: '管理后台', requiresAuth: false, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '数据概览', requiresAuth: false, requiresAdmin: true, roles: ['SUPER_ADMIN', 'ADMIN', 'OPERATOR'] }
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/admin/UserManageView.vue'),
        meta: { title: '用户管理', requiresAuth: false, requiresAdmin: true, roles: ['SUPER_ADMIN', 'ADMIN'] }
      },
      {
        path: 'messages',
        name: 'admin-messages',
        component: () => import('@/views/admin/MessageAuditView.vue'),
        meta: { title: '消息审计', requiresAuth: false, requiresAdmin: true, roles: ['SUPER_ADMIN', 'ADMIN', 'OPERATOR'] }
      },
      {
        path: 'groups',
        name: 'admin-groups',
        component: () => import('@/views/admin/GroupManageView.vue'),
        meta: { title: '群组管理', requiresAuth: false, requiresAdmin: true, roles: ['SUPER_ADMIN', 'ADMIN'] }
      },
      {
        path: 'ai',
        name: 'admin-ai',
        component: () => import('@/views/admin/AiServiceView.vue'),
        meta: { title: 'AI 服务', requiresAuth: false, requiresAdmin: true, roles: ['SUPER_ADMIN', 'ADMIN'] }
      },
      {
        path: 'notifications',
        name: 'admin-notifications',
        component: () => import('@/views/admin/NotificationView.vue'),
        meta: { title: '通知公告', requiresAuth: false, requiresAdmin: true, roles: ['SUPER_ADMIN', 'ADMIN', 'OPERATOR'] }
      },
      {
        path: 'config',
        name: 'admin-config',
        component: () => import('@/views/admin/SystemConfigView.vue'),
        meta: { title: '系统配置', requiresAuth: false, requiresAdmin: true, roles: ['SUPER_ADMIN'] }
      },
      {
        path: 'logs',
        name: 'admin-logs',
        component: () => import('@/views/admin/OperationLogView.vue'),
        meta: { title: '操作日志', requiresAuth: false, requiresAdmin: true, roles: ['SUPER_ADMIN'] }
      }
    ]
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

// 全局前置守卫：登录态校验 + 管理员鉴权
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()
  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} · ChatVibe`
  }

  // ====== 管理员后台路由鉴权 ======
  // requiresAdmin = true 的路由需要管理员 token
  if (to.meta.requiresAdmin) {
    const adminToken = getAdminToken()
    if (!adminToken) {
      // 未登录 → 跳管理员登录页
      next({ name: 'admin-login', query: { redirect: to.fullPath } })
      return
    }
    // 拉取管理员信息（如尚未加载）
    const adminAuthStore = useAdminAuthStore()
    if (!adminAuthStore.admin) {
      try {
        await adminAuthStore.fetchAdmin()
      } catch {
        adminAuthStore.logoutLocal()
        next({ name: 'admin-login', query: { redirect: to.fullPath } })
        return
      }
    }
    // 角色权限校验：路由 meta.roles 未设置则不限制
    const role = adminAuthStore.admin?.role
    if (role && to.meta.roles && !to.meta.roles.includes(role)) {
      next(ROLE_HOME[role])
      return
    }
    next()
    return
  }

  // 管理员已登录访问管理员登录页 → 跳管理后台首页
  if (to.name === 'admin-login') {
    const adminToken = getAdminToken()
    if (adminToken) {
      next({ name: 'admin-dashboard' })
      return
    }
  }

  // ====== C 端路由鉴权 ======
  // 需要登录但未登录 → 跳登录页
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }
  // 已登录但用户信息缺失（页面刷新后 Pinia 状态丢失）→ 拉取用户信息
  if (authStore.isLoggedIn && !authStore.user && to.meta.requiresAuth) {
    try {
      await authStore.fetchUser()
    } catch {
      // 拉取失败（token 过期等）→ 清除登录态并跳登录页
      authStore.logoutLocal()
      next({ name: 'login', query: { redirect: to.fullPath } })
      return
    }
  }
  // 已登录访问登录/注册/找回密码页 → 跳聊天页
  if ((to.name === 'login' || to.name === 'register' || to.name === 'forgot-password') && authStore.isLoggedIn) {
    next({ name: 'chat' })
    return
  }
  next()
})

export default router
