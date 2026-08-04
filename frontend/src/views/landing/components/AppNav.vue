<script setup lang="ts">
/**
 * AppNav · 顶部导航栏
 *
 * 玻璃态 sticky 导航。滚动时整体向中间收缩（max-width 收窄），
 * 导航链接始终居中展示，方便用户点击跳转。
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import ThemeSwitcher from '@/components/common/ThemeSwitcher.vue'

const emit = defineEmits<{
  (e: 'nav', target: string): void
  (e: 'cta'): void
}>()

const themeStore = useThemeStore()
const isScrolled = ref(false)
const scrollProgress = ref(0)

function onScroll(): void {
  isScrolled.value = window.scrollY > 50
  const scrollTop = window.scrollY
  const scrollHeight = document.documentElement.scrollHeight - window.innerHeight
  scrollProgress.value = scrollHeight > 0 ? Math.min((scrollTop / scrollHeight) * 100, 100) : 0
}

function scrollTo(id: string): void {
  emit('nav', id)
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <header class="landing-nav" :class="{ scrolled: isScrolled }">
    <div class="landing-nav__inner">
      <!-- Logo -->
      <a class="nav-logo" href="javascript:void(0)" @click.prevent="scrollTo('top')">
        <div class="logo-mark">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
          </svg>
        </div>
        <span class="logo-text">Chat<span class="accent">Vibe</span></span>
      </a>

      <!-- 中间锚点链接 -->
      <nav class="nav-links">
        <a class="nav-link" href="javascript:void(0)" @click.prevent="scrollTo('capabilities')">核心能力</a>
        <a class="nav-link" href="javascript:void(0)" @click.prevent="scrollTo('ai-demo')">AI 召唤</a>
        <a class="nav-link" href="javascript:void(0)" @click.prevent="scrollTo('architecture')">系统架构</a>
        <a class="nav-link" href="javascript:void(0)" @click.prevent="scrollTo('techstack')">技术栈</a>
        <a class="nav-link" href="javascript:void(0)" @click.prevent="scrollTo('quickstart')">快速开始</a>
      </nav>

      <!-- 右侧操作区 -->
      <div class="nav-actions">
        <ThemeSwitcher class="nav-theme-switcher" />
        <a href="https://github.com/ALu9740/chatvibe" target="_blank" rel="noopener" class="nav-github" title="GitHub 仓库">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0 1 12 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222 0 1.606-.014 2.898-.014 3.293 0 .322.216.694.825.576C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"></path>
          </svg>
        </a>
        <a href="javascript:void(0)" class="btn btn-primary btn-sm" style="color:#fff" @click="emit('cta')">免费开始</a>
      </div>
    </div>

    <!-- 滚动进度条：品牌主题色渐变，宽度随页面滚动比例变化 -->
    <div class="nav-progress" aria-hidden="true">
      <div class="nav-progress__bar" :style="{ width: scrollProgress + '%' }"></div>
    </div>
  </header>
</template>

<style scoped lang="scss">
.landing-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  width: 100%;
  background: var(--landing-nav-bg);
  backdrop-filter: saturate(180%) blur(14px);
  -webkit-backdrop-filter: saturate(180%) blur(14px);
  border-bottom: 1px solid transparent;
  transition: background $dur-normal $ease-smooth,
              box-shadow $dur-normal $ease-smooth,
              border-color $dur-normal $ease-smooth;

  /* 滚动时：整体向中间靠拢，背景加深，加阴影 */
  &.scrolled {
    background: var(--landing-nav-bg-scrolled);
    box-shadow: $shadow-sm;
    border-bottom-color: var(--landing-border);

    .landing-nav__inner {
      max-width: 860px;
      padding: 10px 24px;
    }

    .nav-logo {
      .logo-mark {
        width: 30px;
        height: 30px;
      }

      .logo-text {
        font-size: 17px;
      }
    }
  }

  /* 内层容器：控制最大宽度实现「向中间靠拢」效果 */
  &__inner {
    max-width: $landing-max-w;
    margin: 0 auto;
    padding: 20px 48px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    transition: max-width $dur-normal $ease-smooth,
                padding $dur-normal $ease-smooth;
  }
}

/* Logo */
.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  text-decoration: none;
  color: var(--landing-text);
  font-family: $font-display;
  font-weight: 700;
  font-size: 20px;
  flex-shrink: 0;
  transition: font-size $dur-normal $ease-smooth;

  .logo-mark {
    width: 36px;
    height: 36px;
    border-radius: $r-md;
    background: $grad-brand;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    position: relative;
    overflow: hidden;
    transition: width $dur-normal $ease-smooth,
                height $dur-normal $ease-smooth;

    &::after {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(110deg, transparent 30%, rgba(255, 255, 255, 0.4) 50%, transparent 70%);
      animation: shimmer 3.5s ease-in-out infinite;
    }
  }

  .accent {
    background: $grad-primary;
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

/* 中间导航链接 */
.nav-links {
  display: flex;
  align-items: center;
  gap: 28px;
  flex: 1;
  justify-content: center;

  .nav-link {
    color: var(--landing-text-soft);
    text-decoration: none;
    font-size: 14px;
    font-weight: 500;
    transition: color $dur-fast $ease-smooth;
    cursor: pointer;
    white-space: nowrap;

    &:hover {
      color: var(--landing-text);
    }
  }
}

/* 右侧操作区 */
.nav-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.nav-github {
  color: var(--landing-text-soft);
  display: flex;
  align-items: center;
  cursor: pointer;
  transition: color $dur-fast $ease-smooth;

  &:hover {
    color: var(--landing-text);
  }
}

/* 滚动进度条 */
.nav-progress {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  overflow: hidden;
  pointer-events: none;
}

.nav-progress__bar {
  height: 100%;
  background: $grad-primary;
  box-shadow: 0 0 8px rgba(37, 99, 235, 0.4);
  border-radius: 0 $r-full $r-full 0;
  transition: width 0.1s linear;
}

@media (max-width: 1024px) {
  .landing-nav__inner {
    padding: 16px 24px;
  }

  .nav-links {
    gap: 20px;
  }
}

@media (max-width: 768px) {
  .nav-links .nav-link {
    display: none;
  }

  .landing-nav.scrolled .landing-nav__inner {
    max-width: 100%;
  }
}

@media (max-width: 640px) {
  .landing-nav__inner {
    padding: 12px 16px;
    gap: 12px;
  }

  .nav-actions .nav-github {
    display: none;
  }
}
</style>
