<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员登录页
// 复用 C 端 .auth-page / .auth-card / .auth-brand 全局样式
// 独立 token 存入 chatvibe_admin_token
// ============================================================
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { type FormInstance, type FormRules } from 'element-plus'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { toast } from '@/utils/toast'
import ThemeSwitcher from '@/components/common/ThemeSwitcher.vue'
import type { LoginRequest } from '@/types'

const router = useRouter()
const route = useRoute()
const adminAuthStore = useAdminAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

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
      await adminAuthStore.login(form)
      toast.success('登录成功', '欢迎进入管理后台')
      const redirect = (route.query.redirect as string) || '/admin'
      router.replace(redirect)
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '登录失败'
      toast.error('登录失败', msg)
    } finally {
      loading.value = false
    }
  })
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
        <div class="tagline">管理后台控制系统</div>
      </div>

      <!-- 登录表单 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="管理员邮箱" prop="email">
          <el-input
            v-model="form.email"
            size="large"
            placeholder="请输入管理员邮箱"
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

        <el-button
          type="primary"
          size="large"
          class="btn-block"
          :loading="loading"
          @click="handleLogin"
          style="width: 100%; margin-top: 8px"
        >
          进入管理后台
        </el-button>
      </el-form>

      <div class="auth-footer">
        <el-link :underline="false" style="font-size: 13px" @click="goLanding">返回首页</el-link>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 复用 C 端全局 .auth-page / .auth-card / .auth-brand 样式，无额外自定义 */
</style>
