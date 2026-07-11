<script setup lang="ts">
// 登录页：居中卡片布局，邮箱密码表单
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/utils/toast'
import type { LoginRequest } from '@/types'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

// === 登录表单 ===
const form = reactive<LoginRequest>({
  email: '',
  password: ''
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authStore.login(form)
      toast.success('登录成功', '欢迎回来 ChatVibe')
      const redirect = (route.query.redirect as string) || '/chat'
      router.replace(redirect)
    } catch (e) {
      console.error('[LoginView.handleLogin] 登录失败:', e)
    } finally {
      loading.value = false
    }
  })
}

function goRegister() {
  router.push('/register')
}

function goForgotPassword() {
  router.push('/forgot-password')
}

function goLanding() {
  router.push('/')
}
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
        <div class="tagline">让沟通更有温度，让协作更有智能</div>
      </div>

      <!-- ====== 登录表单 ====== -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            size="large"
            placeholder="请输入邮箱"
            :prefix-icon="'Message'"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            placeholder="请输入密码"
            show-password
            :prefix-icon="'Lock'"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <div style="display: flex; justify-content: flex-end; margin: -8px 0 18px">
          <el-link type="primary" :underline="false" style="font-size: 12px; font-weight: 600; color: var(--c-primary)" @click="goForgotPassword">
            忘记密码？
          </el-link>
        </div>

        <el-button
          type="primary"
          size="large"
          class="btn-block"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>

      <div class="auth-footer">
        还没有账号？<el-link type="primary" :underline="false" @click="goRegister">立即注册</el-link>
      </div>
    </div>
  </div>
</template>
