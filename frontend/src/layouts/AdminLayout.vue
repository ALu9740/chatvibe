<script setup lang="ts">
// ============================================================
// ChatVibe · 管理员后台布局
// 左侧固定导航栏 + 顶部用户栏（含折叠按钮 + 主题切换）+ 主内容区
// ============================================================
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAdminInfo } from '@/api/admin'
import { useThemeStore } from '@/stores/theme'
import type { AdminUser, AdminRole } from '@/types/admin'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()

const admin = ref<AdminUser | null>(null)
const sidebarCollapsed = ref(false)

/** 侧边栏菜单项 */
const menuItems = [
  { index: '/admin', title: '数据概览', icon: 'DataAnalysis' },
  { index: '/admin/users', title: '用户管理', icon: 'User' },
  { index: '/admin/messages', title: '消息审计', icon: 'ChatDotRound' },
  { index: '/admin/groups', title: '群组管理', icon: 'UserFilled' },
  { index: '/admin/ai', title: 'AI 服务', icon: 'MagicStick' },
  { index: '/admin/notifications', title: '通知公告', icon: 'Bell' },
  { index: '/admin/config', title: '系统配置', icon: 'Setting' },
  { index: '/admin/logs', title: '操作日志', icon: 'Document' }
]

/** 当前激活菜单 */
const activeMenu = computed(() => route.path)

/** 角色显示文案 */
const roleText = computed(() => {
  const map: Record<AdminRole, string> = {
    SUPER_ADMIN: '超级管理员',
    ADMIN: '管理员',
    OPERATOR: '运营'
  }
  return map[admin.value?.role || 'OPERATOR']
})

/** 主题图标 */
const themeIconName = computed(() => {
  if (themeStore.mode === 'light') return 'Sunny'
  if (themeStore.mode === 'dark') return 'Moon'
  return 'Monitor'
})

/** 主题菜单文案 */
const themeText = computed(() => {
  if (themeStore.mode === 'light') return '白天'
  if (themeStore.mode === 'dark') return '黑夜'
  return '自动'
})

function handleThemeChange(mode: 'light' | 'dark' | 'auto') {
  themeStore.setMode(mode)
}

function handleMenuSelect(index: string) {
  router.push(index)
}

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    localStorage.removeItem('chatvibe_token')
    router.push('/login')
  } else if (cmd === 'back') {
    router.push('/chat')
  }
}

onMounted(async () => {
  try {
    admin.value = await getAdminInfo()
  } catch {
    admin.value = null
  }
})
</script>

<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <aside class="admin-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="logo">
          <el-icon size="24" color="#fff"><ChatDotRound /></el-icon>
          <span v-show="!sidebarCollapsed" class="logo-text">ChatVibe</span>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        background-color="transparent"
        text-color="#94A3B8"
        active-text-color="#FFFFFF"
        class="sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.index"
          :index="item.index"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>

      <div v-show="!sidebarCollapsed" class="sidebar-footer">
        <span class="version-tag">v1.0.0</span>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="admin-main">
      <!-- 顶部栏 -->
      <header class="admin-header">
        <div class="header-left">
          <el-icon
            class="collapse-btn"
            size="20"
            @click="sidebarCollapsed = !sidebarCollapsed"
          >
            <Fold v-if="!sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <span class="page-title">{{ route.meta.title || '管理后台' }}</span>
        </div>
        <div class="header-right">
          <!-- 主题切换 -->
          <el-dropdown trigger="click" @command="handleThemeChange">
            <div class="theme-trigger">
              <el-icon size="18"><component :is="themeIconName" /></el-icon>
              <span class="theme-label">{{ themeText }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="light">
                  <el-icon><Sunny /></el-icon>&nbsp;白天
                </el-dropdown-item>
                <el-dropdown-item command="dark">
                  <el-icon><Moon /></el-icon>&nbsp;黑夜
                </el-dropdown-item>
                <el-dropdown-item command="auto">
                  <el-icon><Monitor /></el-icon>&nbsp;自动
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- 分隔线 -->
          <div class="header-divider"></div>

          <!-- 管理员信息 -->
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="admin-info">
              <el-avatar :size="32" class="admin-avatar">
                {{ admin?.nickname?.charAt(0) || 'A' }}
              </el-avatar>
              <div class="admin-detail">
                <span class="admin-name">{{ admin?.nickname || '管理员' }}</span>
                <span class="admin-role">{{ roleText }}</span>
              </div>
              <el-icon size="14"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="back">
                  <el-icon><Back /></el-icon>&nbsp;返回客户端
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>&nbsp;退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--admin-page-bg);
  transition: background 0.3s;
}

// ---------- 侧边栏 ----------
.admin-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: #0F172A;
  display: flex;
  flex-direction: column;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;

  &.collapsed {
    width: 64px;
  }
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.1);

  .logo {
    display: flex;
    align-items: center;
    gap: 10px;
    overflow: hidden;
  }

  .logo-text {
    font-size: 18px;
    font-weight: 700;
    color: #FFFFFF;
    white-space: nowrap;
    letter-spacing: -0.02em;
  }
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
  padding: 12px 8px;

  :deep(.el-menu-item) {
    height: 44px;
    line-height: 44px;
    border-radius: 8px;
    margin-bottom: 4px;
    font-size: 14px;

    &:hover {
      background: rgba(148, 163, 184, 0.1) !important;
      color: #E2E8F0 !important;
    }

    &.is-active {
      background: linear-gradient(135deg, #2563EB 0%, #3B82F6 100%) !important;
      color: #FFFFFF !important;
      box-shadow: 0 4px 12px rgba(37, 99, 235, 0.35);
    }
  }
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.1);

  .version-tag {
    font-size: 12px;
    color: #64748B;
    font-family: 'JetBrains Mono', monospace;
  }
}

// ---------- 主区域 ----------
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.admin-header {
  height: 60px;
  flex-shrink: 0;
  background: var(--admin-header-bg);
  border-bottom: 1px solid var(--admin-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: var(--admin-shadow-sm);
  transition: background 0.3s, border-color 0.3s;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;

  .collapse-btn {
    cursor: pointer;
    color: var(--admin-text-secondary);
    transition: color 0.2s;
    flex-shrink: 0;

    &:hover {
      color: #2563EB;
    }
  }

  .page-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--admin-text-primary);
    transition: color 0.3s;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.theme-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 8px;
  transition: background 0.2s;
  color: var(--admin-text-secondary);

  &:hover {
    background: var(--admin-hover-bg);
  }

  .theme-label {
    font-size: 13px;
  }
}

.header-divider {
  width: 1px;
  height: 24px;
  background: var(--admin-border);
  margin: 0 8px;
  transition: background 0.3s;
}

.admin-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 8px;
  transition: background 0.2s;

  &:hover {
    background: var(--admin-hover-bg);
  }
}

.admin-avatar {
  background: linear-gradient(135deg, #2563EB 0%, #0EA5E9 100%);
  color: #FFFFFF;
  font-weight: 600;
  font-size: 14px;
}

.admin-detail {
  display: flex;
  flex-direction: column;
  line-height: 1.4;

  .admin-name {
    font-size: 13px;
    font-weight: 600;
    color: var(--admin-text-primary);
    transition: color 0.3s;
  }

  .admin-role {
    font-size: 11px;
    color: var(--admin-text-muted);
  }
}

.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
</style>
