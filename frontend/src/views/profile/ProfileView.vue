<script setup lang="ts">
// 个人中心：头像上传 + 昵称修改 + 密码修改 + 邮箱绑定 + 通知偏好
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  updateProfile,
  uploadAvatar,
  changePassword,
  changeEmail,
  getNotificationPreferences,
  updateNotificationPreferences
} from '@/api/user'
import { sendCode } from '@/api/auth'
import { getAvatarText, isAvatarUrl, resolveUploadUrl } from '@/utils/format'
import { toast } from '@/utils/toast'
import type { NotificationPreferences } from '@/types'
import { updatePreferences } from '@/utils/notify'

const router = useRouter()
const authStore = useAuthStore()

// 资料表单
const profileForm = reactive({
  nickname: authStore.user?.nickname || '',
  signature: authStore.user?.bio || authStore.user?.signature || ''
})

// 密码表单
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  newPassword2: ''
})

const savingProfile = ref(false)
const savingPwd = ref(false)

// 通知偏好
const notifications = reactive<NotificationPreferences>({
  desktop: true,
  sound: true,
  aiAlert: false
})

async function toggleNotification(key: keyof NotificationPreferences) {
  notifications[key] = !notifications[key]
  try {
    await updateNotificationPreferences({ ...notifications })
    // 同步更新本地缓存
    updatePreferences({ ...notifications })
  } catch {
    notifications[key] = !notifications[key]
    toast.error('保存失败', '通知偏好更新失败，请稍后重试')
  }
}

// 加载通知偏好
async function loadNotifications() {
  try {
    const data = await getNotificationPreferences()
    notifications.desktop = data.desktop
    notifications.sound = data.sound
    notifications.aiAlert = data.aiAlert
  } catch {
    // 加载失败保持默认值
  }
}

onMounted(() => {
  loadNotifications()
})

// === 更换邮箱 ===
const emailModalVisible = ref(false)
const emailForm = reactive({ newEmail: '', code: '' })
const codeCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

function openEmailModal() {
  emailForm.newEmail = ''
  emailForm.code = ''
  codeCountdown.value = 0
  emailModalVisible.value = true
}

function closeEmailModal() {
  emailModalVisible.value = false
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  codeCountdown.value = 0
}

async function handleSendCode() {
  const email = emailForm.newEmail.trim()
  if (!email) {
    toast.warning('请输入新邮箱', '请先填写新邮箱地址')
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    toast.warning('邮箱格式错误', '请输入正确的邮箱地址')
    return
  }
  if (email === authStore.user?.email) {
    toast.warning('邮箱未变更', '新邮箱与当前邮箱相同')
    return
  }
  try {
    await sendCode({ email })
    toast.success('验证码已发送', `验证码已发送至 ${email}，请查收`)
    codeCountdown.value = 60
    countdownTimer = setInterval(() => {
      codeCountdown.value--
      if (codeCountdown.value <= 0) {
        clearInterval(countdownTimer!)
        countdownTimer = null
      }
    }, 1000)
  } catch {
    // 错误提示由响应拦截器处理
  }
}

async function handleChangeEmail() {
  const email = emailForm.newEmail.trim()
  const code = emailForm.code.trim()
  if (!email) {
    toast.warning('请输入新邮箱')
    return
  }
  if (!code) {
    toast.warning('请输入验证码')
    return
  }
  try {
    await changeEmail(email, code)
    closeEmailModal()
    toast.success('邮箱更换成功', `已绑定新邮箱 ${email}，请重新登录`)
    // 邮箱是 JWT 身份凭证的一部分，更换后旧 Token 立即失效。
    // 后端已在 changeEmail 中将用户置为离线并广播，无需再调登出接口（否则会因 Token 失效而报错）。
    authStore.logoutLocal()
    router.push('/login')
  } catch {
    // 错误提示由响应拦截器处理
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

// 头像上传（后端 /user/avatar 接收 base64 字符串）
async function handleAvatarChange(file: File) {
  try {
    // 将 File 转为 base64 data URL
    const base64 = await fileToBase64(file)
    const url = await uploadAvatar(base64)
    if (authStore.user) {
      authStore.setUser({ ...authStore.user, avatar: url })
    }
    toast.success('头像更新成功')
  } catch {
    toast.error('头像上传失败', '请检查网络或文件大小后重试')
  }
}

/** 读取文件为 base64 data URL */
function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

// 触发文件选择
function triggerAvatarUpload() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = (e: Event) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (file) handleAvatarChange(file)
  }
  input.click()
}

// 保存资料
async function handleSaveProfile() {
  if (!profileForm.nickname.trim()) {
    toast.warning('请输入昵称', '昵称不能为空')
    return
  }
  savingProfile.value = true
  try {
    const data = await updateProfile({
      nickname: profileForm.nickname,
      signature: profileForm.signature
    })
    authStore.setUser(data)
    toast.success('资料已保存', '个人资料更新成功')
  } finally {
    savingProfile.value = false
  }
}

// 修改密码
async function handleChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    toast.warning('请填写完整密码信息', '旧密码与新密码均不能为空')
    return
  }
  if (pwdForm.newPassword.length < 6) {
    toast.warning('新密码至少 6 位', '请设置更长的密码以保证安全')
    return
  }
  if (pwdForm.newPassword !== pwdForm.newPassword2) {
    toast.warning('两次输入的新密码不一致', '请重新确认新密码')
    return
  }
  savingPwd.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    toast.success('密码修改成功', '请使用新密码重新登录其他设备')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.newPassword2 = ''
  } finally {
    savingPwd.value = false
  }
}

// 退出登录
async function handleLogout() {
  await authStore.logout()
  router.replace('/login')
}

// 返回聊天
function goChat() {
  router.push('/chat')
}
</script>

<template>
  <div class="profile-page">
    <div class="profile-shell">
      <!-- 顶部 -->
      <div class="profile-header">
        <a class="back-link" title="返回聊天" @click="goChat">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <line x1="19" y1="12" x2="5" y2="12"></line>
            <polyline points="12 19 5 12 12 5"></polyline>
          </svg>
        </a>
        <div style="flex: 1">
          <h1>个人中心</h1>
          <div class="sub">管理你的账号信息与安全设置</div>
        </div>
        <el-button type="info" plain size="small" @click="handleLogout">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
          退出登录
        </el-button>
      </div>

      <!-- 个人资料 -->
      <div class="profile-card">
        <div class="profile-card-title">
          <el-icon><User /></el-icon>
          个人资料
        </div>
        <div class="profile-card-desc">头像和昵称将展示给你的好友与群组成员</div>

        <!-- 头像上传 -->
        <div class="avatar-upload">
          <div class="preview">
            <div class="avatar size-xl" :style="!isAvatarUrl(authStore.user?.avatar) ? { background: '#2563EB' } : {}">
              <img v-if="isAvatarUrl(authStore.user?.avatar)" :src="resolveUploadUrl(authStore.user?.avatar)" alt="头像" />
              <template v-else>{{ getAvatarText(authStore.user?.nickname || 'U') }}</template>
            </div>
            <div class="edit-badge">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
              </svg>
            </div>
          </div>
          <div class="upload-info">
            <h4>头像设置</h4>
            <p>支持 JPG / PNG 格式，建议尺寸 256×256，文件不超过 2MB</p>
            <el-button size="small" @click="triggerAvatarUpload">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
              上传新头像
            </el-button>
          </div>
        </div>

        <!-- 昵称 / 签名 -->
        <div class="form-group">
          <label class="form-label">昵称</label>
          <el-input v-model="profileForm.nickname" size="large" maxlength="20" show-word-limit placeholder="请输入昵称" />
        </div>
        <div class="form-group">
          <label class="form-label">个性签名</label>
          <el-input v-model="profileForm.signature" size="large" maxlength="50" show-word-limit placeholder="介绍一下自己" />
        </div>

        <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px"><polyline points="20 6 9 17 4 12"></polyline></svg>
          保存修改
        </el-button>
      </div>

      <!-- 账号安全 -->
      <div class="profile-card">
        <div class="profile-card-title">
          <el-icon><Lock /></el-icon>
          账号安全
        </div>
        <div class="profile-card-desc">保护你的账号，定期更新密码与绑定信息</div>

        <!-- 邮箱绑定 -->
        <div class="form-group">
          <label class="form-label">邮箱绑定</label>
          <div class="email-status">
            <div class="check">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
            </div>
            <div class="info">
              <div class="label">已绑定邮箱</div>
              <div class="value">{{ authStore.user?.email || '未绑定' }}</div>
            </div>
            <el-button text size="small" @click="openEmailModal">更换</el-button>
          </div>
        </div>

        <!-- 修改密码 -->
        <div class="form-group" style="margin-top: 24px">
          <label class="form-label">修改密码</label>
          <div style="margin-bottom: 12px">
            <el-input v-model="pwdForm.oldPassword" type="password" size="large" placeholder="请输入旧密码" show-password />
          </div>
          <div style="margin-bottom: 12px">
            <el-input v-model="pwdForm.newPassword" type="password" size="large" placeholder="设置新密码（至少 6 位）" show-password />
          </div>
          <div style="margin-bottom: 16px">
            <el-input v-model="pwdForm.newPassword2" type="password" size="large" placeholder="再次输入新密码" show-password />
          </div>
          <el-button type="primary" :loading="savingPwd" @click="handleChangePassword">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px"><polyline points="20 6 9 17 4 12"></polyline></svg>
            确认修改
          </el-button>
        </div>
      </div>

      <!-- 通知偏好 -->
      <div class="profile-card">
        <div class="profile-card-title">
          <el-icon><Bell /></el-icon>
          通知偏好
        </div>
        <div class="profile-card-desc">控制消息提醒的接收方式</div>

        <div class="member-list">
          <div class="member-item" style="cursor: pointer" @click="toggleNotification('desktop')">
            <el-icon style="color: var(--c-text-muted)"><ChatDotRound /></el-icon>
            <div class="member-name">桌面通知</div>
            <span class="toggle" :class="{ on: notifications.desktop }"></span>
          </div>
          <div class="member-item" style="cursor: pointer" @click="toggleNotification('sound')">
            <el-icon style="color: var(--c-text-muted)"><Bell /></el-icon>
            <div class="member-name">声音提醒</div>
            <span class="toggle" :class="{ on: notifications.sound }"></span>
          </div>
          <div class="member-item" style="cursor: pointer" @click="toggleNotification('aiAlert')">
            <el-icon style="color: var(--c-ai)"><MagicStick /></el-icon>
            <div class="member-name">AI 消息特别提醒</div>
            <span class="toggle" :class="{ on: notifications.aiAlert }"></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 更换邮箱弹窗 -->
    <div v-if="emailModalVisible" class="modal-overlay" @click.self="closeEmailModal">
      <div class="modal" style="width: 400px">
        <div class="modal-header">
          <h3>更换绑定邮箱</h3>
          <button class="modal-close" @click="closeEmailModal">×</button>
        </div>
        <div class="modal-body" style="padding: 20px 24px">
          <div class="form-group" style="margin-bottom: 16px">
            <label class="form-label" style="font-size: 13px; margin-bottom: 6px">新邮箱地址</label>
            <el-input v-model="emailForm.newEmail" size="large" placeholder="请输入新邮箱" :disabled="codeCountdown > 0" />
          </div>
          <div class="form-group" style="margin-bottom: 8px">
            <label class="form-label" style="font-size: 13px; margin-bottom: 6px">验证码</label>
            <div style="display: flex; gap: 10px">
              <el-input v-model="emailForm.code" size="large" placeholder="请输入 6 位验证码" maxlength="6" style="flex: 1" />
              <el-button size="large" :disabled="codeCountdown > 0" @click="handleSendCode" style="width: 120px">
                {{ codeCountdown > 0 ? `${codeCountdown}s 后重发` : '发送验证码' }}
              </el-button>
            </div>
          </div>
        </div>
        <div class="modal-footer" style="display: flex; justify-content: flex-end; gap: 10px; padding: 0 24px 20px">
          <el-button size="large" @click="closeEmailModal">取消</el-button>
          <el-button type="primary" size="large" @click="handleChangeEmail">确认更换</el-button>
        </div>
      </div>
    </div>
  </div>
</template>
