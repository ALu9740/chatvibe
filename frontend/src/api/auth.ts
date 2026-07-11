import request from '@/utils/request'
import type {
  LoginRequest,
  LoginResult,
  RegisterRequest,
  SendCodeRequest,
  VerifyCodeRequest
} from '@/types'
import { USE_MOCK, DEMO_CODE, MOCK_USER } from '@/mock/data'

/** 用户登录 */
export function login(data: LoginRequest) {
  if (USE_MOCK) {
    return Promise.resolve<LoginResult>({
      accessToken: 'mock_token_' + Date.now(),
      tokenType: 'Bearer',
      expiresIn: 86400,
      user: { ...MOCK_USER, email: data.email || MOCK_USER.email }
    })
  }
  return request.post<unknown, LoginResult>('/auth/login', data)
}

/** 用户注册 */
export function register(data: RegisterRequest) {
  if (USE_MOCK) {
    return Promise.resolve<LoginResult>({
      accessToken: 'mock_token_' + Date.now(),
      tokenType: 'Bearer',
      expiresIn: 86400,
      user: { ...MOCK_USER, email: data.email }
    })
  }
  return request.post<unknown, LoginResult>('/auth/register', data)
}

/** 发送邮箱验证码 */
export function sendCode(data: SendCodeRequest) {
  if (USE_MOCK) {
    return Promise.resolve(true)
  }
  return request.post<unknown, boolean>('/auth/code', { email: data.email })
}

/** 校验验证码 */
export function verifyCode(data: VerifyCodeRequest) {
  if (USE_MOCK) {
    return Promise.resolve(data.code === DEMO_CODE)
  }
  return request.get<unknown, boolean>('/auth/code/verify', {
    params: { email: data.email, code: data.code }
  })
}

/** 退出登录 */
export function logout() {
  if (USE_MOCK) {
    return Promise.resolve(true)
  }
  return request.post<unknown, boolean>('/auth/logout')
}

/** 重置密码（忘记密码流程第 3 步调用，需携带验证码以通过后端二次校验） */
export function resetPassword(data: { email: string; code: string; password: string }) {
  if (USE_MOCK) {
    return Promise.resolve(true)
  }
  return request.post<unknown, boolean>('/auth/password/reset', data)
}
