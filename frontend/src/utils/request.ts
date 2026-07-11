import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { toast } from '@/utils/toast'
import type { ApiResponse } from '@/types'

// 从 localStorage 读取 token 的键名
const TOKEN_KEY = 'chatvibe_token'

/** 获取本地存储的 token */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 保存 token */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 清除 token */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器：自动携带 Bearer token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理响应格式与 401 跳转
service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse
    console.log('[Response拦截器] url =', response.config.url, '| 原始 data =', response.data)

    // 非 JSON / 文件流直接返回
    if (!res || typeof res.code === 'undefined') {
      console.log('[Response拦截器] 非标准 ApiResponse，原样返回')
      return response.data
    }

    // code === 200 表示业务成功
    if (res.code === 200) {
      console.log('[Response拦截器] code=200，返回 res.data =', res.data)
      return res.data
    }

    // 1002 未授权：清除 token 并跳转登录
    if (res.code === 1002) {
      removeToken()
      toast.error('登录已过期', '请重新登录')
      // 避免在登录页重复跳转
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message || '未授权'))
    }

    // 2011 账号在其他设备登录：强制下线
    if (res.code === 2011) {
      removeToken()
      toast.error('账号被强制下线', res.message || '当前账号已在其他设备登录，您已被强制下线')
      // 避免在登录页重复跳转
      if (!window.location.pathname.includes('/login')) {
        setTimeout(() => {
          window.location.href = '/login'
        }, 1500)
      }
      return Promise.reject(new Error(res.message || '账号在其他设备登录'))
    }

    // 其他业务错误
    toast.error('请求失败', res.message || '请稍后重试')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // HTTP 错误处理
    const status = error.response?.status
    if (status === 401) {
      removeToken()
      toast.error('登录已过期', '请重新登录')
      window.location.href = '/login'
    } else if (status === 403) {
      toast.error('无权限', '没有权限执行此操作')
    } else if (status >= 500) {
      toast.error('服务器异常', '服务器开小差了，请稍后重试')
    } else {
      toast.error('网络异常', error.response?.data?.message || error.message || '请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default service
