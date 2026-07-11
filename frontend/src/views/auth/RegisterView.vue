<script setup lang="ts">
// 注册页：三步注册流程（邮箱 → 验证码 → 密码）
import { ref, computed, nextTick, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { sendCode, verifyCode, register } from '@/api/auth'
import { toast } from '@/utils/toast'

const router = useRouter()

// 当前步骤（0/1/2）
const activeStep = ref(0)
const loading = ref(false)

// 表单数据
const email = ref('')
const code = ref(['', '', '', '', '', ''])
const password = ref('')
const password2 = ref('')

// 验证码输入框引用
const codeInputs = ref<HTMLInputElement[]>([])

// 倒计时
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const codeStr = computed(() => code.value.join(''))

// 发送验证码
async function handleSendCode() {
  if (!email.value || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value)) {
    toast.warning('邮箱无效', '请输入有效的邮箱地址')
    return
  }
  loading.value = true
  try {
    await sendCode({ email: email.value, scene: 'register' })
    toast.success('验证码已发送', '请注意查收')
    activeStep.value = 1
    startCountdown()
    await nextTick()
    codeInputs.value[0]?.focus()
  } finally {
    loading.value = false
  }
}

// 开始倒计时
function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

// 验证码输入处理：自动跳到下一个
function handleCodeInput(index: number, e: Event) {
  const input = e.target as HTMLInputElement
  const value = input.value.replace(/\D/g, '').slice(-1)
  code.value[index] = value
  if (value && index < 5) {
    codeInputs.value[index + 1]?.focus()
  }
}

// 验证码退格处理
function handleCodeKeydown(index: number, e: KeyboardEvent) {
  if (e.key === 'Backspace' && !code.value[index] && index > 0) {
    codeInputs.value[index - 1]?.focus()
  }
}

// 验证码粘贴处理
function handleCodePaste(e: ClipboardEvent) {
  e.preventDefault()
  const text = (e.clipboardData?.getData('text') || '').replace(/\D/g, '').slice(0, 6)
  if (!text) return
  for (let i = 0; i < 6; i++) {
    code.value[i] = text[i] || ''
  }
  const lastIdx = Math.min(text.length, 5)
  codeInputs.value[lastIdx]?.focus()
}

// 校验验证码
async function handleVerify() {
  if (codeStr.value.length !== 6) {
    toast.warning('请输入完整验证码', '请填写完整的 6 位验证码')
    return
  }
  loading.value = true
  try {
       const ok = await verifyCode({ email: email.value, code: codeStr.value })
    if (!ok) {
      toast.error('验证码不正确', '请检查后重新输入')
      return
    }
    toast.success('验证成功', '请设置登录密码')
    activeStep.value = 2
  } catch (e) {
    // 验证码过期/失效等后端异常（响应拦截器已弹出 toast，这里仅兜底防止未捕获拒绝）
    console.error('[RegisterView.handleVerify] 校验失败:', e)
  } finally {
    loading.value = false
  }
}

// 完成注册
async function handleRegister() {
  if (!password.value || password.value.length < 6) {
    toast.warning('密码至少 6 位', '请设置更长的密码以保证安全')
    return
  }
  if (password.value !== password2.value) {
    toast.warning('两次输入的密码不一致', '请重新确认密码')
    return
  }
  loading.value = true
  try {
    await register({
      email: email.value,
      password: password.value,
      code: code.value.join('')
    })
    toast.success('注册成功', '请使用新账号登录')
    router.replace('/login')
  } catch (e) {
    console.error('[RegisterView.handleRegister] 注册失败:', e)
    toast.error('注册失败', '请稍后重试，或重新获取验证码')
  } finally {
    loading.value = false
  }
}

// 返回登录
function goLogin() {
  router.push('/login')
}

// 返回首页
function goLanding() {
  router.push('/')
}

//资源清理
onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<template>
  <div class="auth-page">
    <ThemeSwitcher class="auth-theme-switcher" />
    <div class="auth-card">
      <!-- 品牌 -->
      <div class="auth-brand">
        <div class="logo-mark" style="cursor: pointer" @click="goLanding">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
          </svg>
        </div>
        <span class="logo-text">Chat<span class="accent">Vibe</span></span>
        <div class="tagline">创建账号，开启智能沟通之旅</div>
      </div>

      <!-- 步骤指示 -->
      <el-steps :active="activeStep" align-center finish-status="success" style="margin-bottom: 28px">
        <el-step title="邮箱" />
        <el-step title="验证码" />
        <el-step title="密码" />
      </el-steps>

      <!-- 步骤 1：邮箱 -->
      <div v-show="activeStep === 0">
        <div class="form-group">
          <label class="form-label">邮箱地址</label>
          <el-input
            v-model="email"
            size="large"
            placeholder="请输入邮箱"
            :prefix-icon="'Message'"
            @keyup.enter="handleSendCode"
          />
        </div>
        <el-button
          type="primary"
          size="large"
          class="btn-block"
          :loading="loading"
          @click="handleSendCode"
        >
          发送验证码
        </el-button>
      </div>

      <!-- 步骤 2：验证码 -->
      <div v-show="activeStep === 1">
        <div style="text-align: center; margin-bottom: 18px">
          <div style="font-size: 13px; color: var(--c-text-soft); margin-bottom: 4px">
            验证码已发送至邮箱
          </div>
          <div style="font-weight: 700; color: var(--c-primary)">{{ email }}</div>
        </div>

        <!-- 6 格验证码输入 -->
        <div class="code-inputs" @paste="handleCodePaste">
          <input
            v-for="(_, i) in 6"
            :key="i"
            ref="codeInputs"
            :value="code[i]"
            type="text"
            maxlength="1"
            class="code-input"
            :class="{ filled: code[i] }"
            inputmode="numeric"
            @input="handleCodeInput(i, $event)"
            @keydown="handleCodeKeydown(i, $event)"
            @paste="handleCodePaste"
          />
        </div>

        <div style="text-align: center; font-size: 12px; color: var(--c-text-muted); margin: 14px 0">
          <template v-if="countdown > 0">{{ countdown }} 秒后可重新发送</template>
          <el-link v-else type="primary" :underline="false" @click="handleSendCode">重新发送</el-link>
        </div>

        <el-button
          type="primary"
          size="large"
          class="btn-block"
          :loading="loading"
          @click="handleVerify"
        >
          验证
        </el-button>
      </div>

      <!-- 步骤 3：设置密码 -->
      <div v-show="activeStep === 2">
        <div class="form-group">
          <label class="form-label">设置密码</label>
          <el-input
            v-model="password"
            type="password"
            size="large"
            placeholder="至少 6 位密码"
            show-password
            :prefix-icon="'Lock'"
          />
        </div>
        <div class="form-group">
          <label class="form-label">确认密码</label>
          <el-input
            v-model="password2"
            type="password"
            size="large"
            placeholder="再次输入密码"
            show-password
            :prefix-icon="'Lock'"
            @keyup.enter="handleRegister"
          />
        </div>
        <el-button
          type="primary"
          size="large"
          class="btn-block"
          :loading="loading"
          @click="handleRegister"
        >
          注册
        </el-button>
      </div>

      <div class="auth-footer">
        已有账号？<el-link type="primary" :underline="false" @click="goLogin">返回登录</el-link>
      </div>
    </div>
  </div>
</template>
