import request from '@/utils/request'
import type {
  LoginRequest,
  LoginResult,
  RegisterRequest,
  SendCodeRequest,
  VerifyCodeRequest
} from '@/types'

/** 用户登录 */
export function login(data: LoginRequest) {
  return request.post<unknown, LoginResult>('/auth/login', data)
}

/** 用户注册 */
export function register(data: RegisterRequest) {
  return request.post<unknown, LoginResult>('/auth/register', data)
}

/** 发送邮箱验证码 */
export function sendCode(data: SendCodeRequest) {
  return request.post<unknown, boolean>('/auth/code', { email: data.email })
}

/** 校验验证码 */
export function verifyCode(data: VerifyCodeRequest) {
  return request.get<unknown, boolean>('/auth/code/verify', {
    params: { email: data.email, code: data.code }
  })
}

/** 退出登录 */
export function logout() {
  return request.post<unknown, boolean>('/auth/logout')
}

/** 重置密码（忘记密码流程第 3 步调用，需携带验证码以通过后端二次校验） */
export function resetPassword(data: { email: string; code: string; password: string }) {
  return request.post<unknown, boolean>('/auth/password/reset', data)
}
