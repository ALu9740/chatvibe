import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { toast } from '@/utils/toast'
import type { ApiResponse } from '@/types'

// C 端用户 token
const TOKEN_KEY = 'chatvibe_token'
// 管理员后台 token（与 C 端隔离）
const ADMIN_TOKEN_KEY = 'chatvibe_admin_token'

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

// 请求拦截器：按请求路径选择对应 token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const url = config.url
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

    // 1002 未授权：清除对应 token 并跳转登录
    if (res.code === 1002) {
      if (isAdmin) {
        removeAdminToken()
        toast.error('登录已过期', '请重新登录')
        if (!window.location.pathname.includes('/admin/login')) {
          window.location.href = '/admin/login'
        }
      } else {
        removeToken()
        toast.error('登录已过期', '请重新登录')
        if (!window.location.pathname.includes('/login')) {
          window.location.href = '/login'
        }
      }
      return Promise.reject(new Error(res.message || '未授权'))
    }

    // 2011 账号在其他设备登录：强制下线
    if (res.code === 2011) {
      if (isAdmin) {
        removeAdminToken()
      } else {
        removeToken()
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
      } else {
        removeToken()
      }
      toast.error('账号已被封禁', res.message || '账号已被封禁，请联系管理员')
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
      if (isAdmin) {
        removeAdminToken()
        toast.error('登录已过期', '请重新登录')
        window.location.href = '/admin/login'
      } else {
        removeToken()
        toast.error('登录已过期', '请重新登录')
        window.location.href = '/login'
      }
    } else if (status === 403) {
      if (isAdmin) {
        removeAdminToken()
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
