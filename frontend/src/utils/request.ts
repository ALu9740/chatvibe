import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { toast } from '@/utils/toast'
import type { ApiResponse, LoginResult } from '@/types'

// C 端用户 token
const TOKEN_KEY = 'chatvibe_token'
const REFRESH_TOKEN_KEY = 'chatvibe_refresh_token'
// 管理员后台 token（与 C 端隔离）
const ADMIN_TOKEN_KEY = 'chatvibe_admin_token'
const ADMIN_REFRESH_TOKEN_KEY = 'chatvibe_admin_refresh_token'

// ============================================================
// C 端 token 存取
// ============================================================

/** 获取 C 端 token */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 保存 C 端 token */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 清除 C 端 token */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 获取 C 端 refreshToken */
export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

/** 保存 C 端 refreshToken */
export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

/** 清除 C 端 refreshToken */
export function removeRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

// ============================================================
// 管理员 token 存取
// ============================================================

/** 获取管理员 token */
export function getAdminToken(): string | null {
  return localStorage.getItem(ADMIN_TOKEN_KEY)
}

/** 保存管理员 token */
export function setAdminToken(token: string): void {
  localStorage.setItem(ADMIN_TOKEN_KEY, token)
}

/** 清除管理员 token */
export function removeAdminToken(): void {
  localStorage.removeItem(ADMIN_TOKEN_KEY)
}

/** 获取管理员 refreshToken */
export function getAdminRefreshToken(): string | null {
  return localStorage.getItem(ADMIN_REFRESH_TOKEN_KEY)
}

/** 保存管理员 refreshToken */
export function setAdminRefreshToken(token: string): void {
  localStorage.setItem(ADMIN_REFRESH_TOKEN_KEY, token)
}

/** 清除管理员 refreshToken */
export function removeAdminRefreshToken(): void {
  localStorage.removeItem(ADMIN_REFRESH_TOKEN_KEY)
}

// ============================================================
// Axios 实例
// ============================================================

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

/** 判断请求是否为管理员后台接口 */
function isAdminRequest(url: string | undefined): boolean {
  return !!url && url.startsWith('/admin/')
}

/** 判断当前页面是否在管理员后台 */
function isAdminPage(): boolean {
  return window.location.pathname.startsWith('/admin')
}

/** 判断请求是否为认证接口（登录/注册等公开接口，无需携带 token） */
function isAuthEndpoint(url: string | undefined): boolean {
  return !!url && url.startsWith('/auth/')
}

// ============================================================
// Token 自动刷新机制
// ============================================================

let isRefreshing = false
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (error: unknown) => void
}> = []

/** 处理排队中的请求：刷新成功后重试，失败后拒绝 */
function processQueue(error: unknown, token: string | null = null): void {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error)
    } else {
      resolve(token!)
    }
  })
  failedQueue = []
}

/** 使用 refreshToken 获取新的 accessToken（绕过拦截器，使用原始 axios） */
async function doRefreshToken(isAdmin: boolean): Promise<string> {
  const stored = isAdmin ? getAdminRefreshToken() : getRefreshToken()
  if (!stored) {
    throw new Error('No refresh token available')
  }

  const response = await axios.post<ApiResponse<LoginResult>>(
    `${import.meta.env.VITE_API_BASE}/auth/refresh`,
    null,
    {
      headers: { Authorization: `Bearer ${stored}` },
      timeout: 10000
    }
  )

  const res = response.data
  if (res.code !== 200 || !res.data?.accessToken) {
    throw new Error(res.message || 'Token refresh failed')
  }

  const { accessToken, refreshToken: newRefresh } = res.data
  if (isAdmin) {
    setAdminToken(accessToken)
    if (newRefresh) setAdminRefreshToken(newRefresh)
  } else {
    setToken(accessToken)
    if (newRefresh) setRefreshToken(newRefresh)
  }

  return accessToken
}

/** 清除 token 并跳转登录页 */
function redirectToLogin(isAdmin: boolean): void {
  if (isAdmin) {
    removeAdminToken()
    removeAdminRefreshToken()
    if (!window.location.pathname.includes('/admin/login')) {
      window.location.href = '/admin/login'
    }
  } else {
    removeToken()
    removeRefreshToken()
    if (!window.location.pathname.includes('/login')) {
      window.location.href = '/login'
    }
  }
}

/** 处理 token 过期：尝试刷新并重试原始请求 */
async function handleTokenExpired(
  originalRequest: InternalAxiosRequestConfig & { _retry?: boolean },
  isAdmin: boolean
): Promise<unknown> {
  // 已重试过，不再刷新，直接跳转登录
  if (originalRequest._retry) {
    redirectToLogin(isAdmin)
    return Promise.reject(new Error('Token expired after retry'))
  }

  // 已有刷新请求进行中，排队等待
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      failedQueue.push({
        resolve: (token: string) => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          resolve(service(originalRequest))
        },
        reject
      })
    })
  }

  originalRequest._retry = true
  isRefreshing = true

  try {
    const newToken = await doRefreshToken(isAdmin)
    processQueue(null, newToken)
    originalRequest.headers.Authorization = `Bearer ${newToken}`
    return service(originalRequest)
  } catch (refreshError) {
    processQueue(refreshError, null)
    toast.error('登录已过期', '请重新登录')
    redirectToLogin(isAdmin)
    return Promise.reject(refreshError)
  } finally {
    isRefreshing = false
  }
}

// 请求拦截器：按请求路径选择对应 token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const url = config.url
    // 认证接口（登录/注册等）不携带任何 token，避免过期 token 干扰
    if (isAuthEndpoint(url)) {
      return config
    }
    if (isAdminRequest(url)) {
      // 管理员接口使用管理员 token
      const adminToken = getAdminToken()
      if (adminToken) {
        config.headers.Authorization = `Bearer ${adminToken}`
      }
    } else {
      // C 端接口使用 C 端 token
      const token = getToken()
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理响应格式与 401 跳转
service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse
    const isAdmin = isAdminRequest(response.config.url) || isAdminPage()

    // 非 JSON / 文件流直接返回
    if (!res || typeof res.code === 'undefined') {
      return response.data
    }

    // code === 200 表示业务成功
    if (res.code === 200) {
      return res.data
    }

    // 1002 未授权：尝试刷新 token 并重试请求
    if (res.code === 1002) {
      // 认证接口不刷新 token，直接拒绝（由调用方处理错误提示）
      if (isAuthEndpoint(response.config.url)) {
        return Promise.reject(new Error(res.message || '请求失败'))
      }
      return handleTokenExpired(response.config, isAdmin)
    }

    // 2011 账号在其他设备登录：强制下线
    if (res.code === 2011) {
      if (isAdmin) {
        removeAdminToken()
        removeAdminRefreshToken()
      } else {
        removeToken()
        removeRefreshToken()
      }
      toast.error('账号被强制下线', res.message || '当前账号已在其他设备登录，您已被强制下线')
      const target = isAdmin ? '/admin/login' : '/login'
      if (!window.location.pathname.includes(target)) {
        setTimeout(() => {
          window.location.href = target
        }, 1500)
      }
      return Promise.reject(new Error(res.message || '账号在其他设备登录'))
    }

    // 2009 账号已被封禁：强制下线
    if (res.code === 2009) {
      if (isAdmin) {
        removeAdminToken()
        removeAdminRefreshToken()
      } else {
        removeToken()
        removeRefreshToken()
      }
      if (!(response.config as Record<string, unknown>)?._skipToast) {
        toast.error('账号已被封禁', res.message || '账号已被封禁，请联系管理员')
      }
      const target = isAdmin ? '/admin/login' : '/login'
      if (!window.location.pathname.includes(target)) {
        setTimeout(() => {
          window.location.href = target
        }, 1500)
      }
      return Promise.reject(new Error(res.message || '账号已被封禁'))
    }

    // 其他业务错误（支持 _skipToast 跳过拦截器 toast，由调用方自行处理）
    if (!(response.config as Record<string, unknown>)?._skipToast) {
      toast.error('请求失败', res.message || '请稍后重试')
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const isAdmin = isAdminRequest(error.config?.url) || isAdminPage()

    if (status === 401) {
      // 认证接口不刷新 token，直接拒绝
      if (error.config && !isAuthEndpoint(error.config?.url)) {
        return handleTokenExpired(error.config, isAdmin)
      }
      redirectToLogin(isAdmin)
      return Promise.reject(error)
    } else if (status === 403) {
      if (isAdmin) {
        removeAdminToken()
        removeAdminRefreshToken()
        toast.error('无管理员权限', '该账号无权访问管理后台')
        setTimeout(() => {
          window.location.href = '/admin/login'
        }, 1500)
      } else {
        toast.error('无权限', '没有权限执行此操作')
      }
    } else if (status >= 500) {
      toast.error('服务器异常', '服务器开小差了，请稍后重试')
    } else {
      // 支持 _skipToast 跳过拦截器 toast，由调用方自行处理
      if (!(error.config as Record<string, unknown>)?._skipToast) {
        toast.error('网络异常', error.response?.data?.message || error.message || '请检查网络连接')
      }
    }
    return Promise.reject(error)
  }
)

export default service
