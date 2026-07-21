import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import * as userApi from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/request'
import { resetNotify } from '@/utils/notify'
import type { LoginRequest, User } from '@/types'

/** 认证状态 store：管理 token 与用户信息 */
export const useAuthStore = defineStore('auth', () => {
  // 状态
  const token = ref<string>(getToken() || '')
  const user = ref<User | null>(null)

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  const nickname = computed(() => user.value?.nickname || '游客')

  /** 从后端拉取当前用户信息 */
  async function fetchUser(): Promise<User | null> {
    if (!token.value) return null
    const data = await userApi.getCurrentUser()
    user.value = data
    return data
  }

  /** 登录 */
  async function login(payload: LoginRequest): Promise<void> {
    const result = await authApi.login(payload)
    token.value = result.accessToken
    user.value = result.user
    setToken(result.accessToken)
  }

  /** 退出登录（调用后端 + 本地清理） */
  async function logout(): Promise<void> {
    await authApi.logout()
    logoutLocal()
  }

  /** 仅本地清理（token 失效时调用） */
  function logoutLocal(): void {
    token.value = ''
    user.value = null
    removeToken()
    // 重置通知模块状态，使下次登录时 initNotify 重新拉取偏好
    resetNotify()
  }

  /** 更新本地缓存的用户信息 */
  function setUser(data: User): void {
    user.value = data
  }

  return {
    token,
    user,
    isLoggedIn,
    nickname,
    fetchUser,
    login,
    logout,
    logoutLocal,
    setUser
  }
})
