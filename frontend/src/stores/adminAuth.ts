import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { getAdminInfo } from '@/api/admin'
import { getAdminToken, setAdminToken, removeAdminToken, getAdminRefreshToken, setAdminRefreshToken, removeAdminRefreshToken } from '@/utils/request'
import type { LoginRequest } from '@/types'
import type { AdminUser } from '@/types/admin'

/**
 * 管理员认证 store
 * 与 C 端 auth store 完全隔离，使用独立的 token 存储键
 */
export const useAdminAuthStore = defineStore('adminAuth', () => {
  // 状态
  const token = ref<string>(getAdminToken() || '')
  const refreshToken = ref<string>(getAdminRefreshToken() || '')
  const admin = ref<AdminUser | null>(null)

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)

  /** 登录（调用 C 端同一登录接口，但 token 存入管理员独立键） */
  async function login(payload: LoginRequest): Promise<void> {
    const result = await authApi.login(payload)
    // 校验是否为管理角色 (SUPER_ADMIN / ADMIN / OPERATOR)
    const adminRoles = ['SUPER_ADMIN', 'ADMIN', 'OPERATOR']
    if (!result.user.role || !adminRoles.includes(result.user.role)) {
      throw new Error('非管理员账号')
    }
    token.value = result.accessToken
    refreshToken.value = result.refreshToken || ''
    setAdminToken(result.accessToken)
    setAdminRefreshToken(result.refreshToken || '')
  }

  /** 拉取管理员信息 */
  async function fetchAdmin(): Promise<AdminUser | null> {
    if (!token.value) return null
    const data = await getAdminInfo()
    admin.value = data
    return data
  }

  /** 仅本地清理 */
  function logoutLocal(): void {
    token.value = ''
    refreshToken.value = ''
    admin.value = null
    removeAdminToken()
    removeAdminRefreshToken()
  }

  return {
    token,
    admin,
    isLoggedIn,
    login,
    fetchAdmin,
    logoutLocal
  }
})
