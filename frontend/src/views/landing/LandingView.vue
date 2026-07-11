<script setup lang="ts">
// 官网首页：严格对齐 prototype/index.html
// 分层架构与技术栈区块基于全栈技术架构文档 TechnicalArchitecture.md
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import ThemeSwitcher from '@/components/common/ThemeSwitcher.vue'

const router = useRouter()
const authStore = useAuthStore()

// === 产品预览轮播（使用原型截图） ===
interface PreviewSlide {
  tab: string
  alt: string
  src: string
}
const slides: PreviewSlide[] = [
  { tab: '首页概述', alt: 'ChatVibe 首页概述 - 聊天工作台', src: '/p1.png' },
  { tab: '好友管理', alt: 'ChatVibe 好友管理 - 邮箱搜索添加好友', src: '/p2.png' },
  { tab: '创建群组', alt: 'ChatVibe 创建群组 - 邀请成员建群', src: '/p3.png' }
]

const currentSlide = ref(0)
let carouselTimer: ReturnType<typeof setInterval> | null = null
const INTERVAL = 4500
const carouselRef = ref<HTMLElement | null>(null)
const headerRef = ref<HTMLElement | null>(null)

/** 滚动时切换导航栏紧凑状态 */
function onScroll(): void {
  if (headerRef.value) {
    headerRef.value.classList.toggle('scrolled', window.scrollY > 10)
  }
}

function goToSlide(idx: number): void {
  currentSlide.value = (idx + slides.length) % slides.length
  restartAutoplay()
}
function nextSlide(): void {
  goToSlide(currentSlide.value + 1)
}
function prevSlide(): void {
  goToSlide(currentSlide.value - 1)
}
function startAutoplay(): void {
  if (carouselTimer) clearInterval(carouselTimer)
  carouselTimer = setInterval(nextSlide, INTERVAL)
}
function stopAutoplay(): void {
  if (carouselTimer) {
    clearInterval(carouselTimer)
    carouselTimer = null
  }
}
function restartAutoplay(): void {
  stopAutoplay()
  startAutoplay()
}

// === 路由跳转 ===
function goAuth(): void {
  router.push('/login')
}
function goChat(): void {
  if (authStore.isLoggedIn) {
    router.push('/chat')
  } else {
    router.push({ name: 'login', query: { redirect: '/chat' } })
  }
}

/** 锚点滚动 */
function scrollTo(id: string): void {
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(() => {
  // 官网首页需要可滚动：覆盖全局 body overflow:hidden
  document.body.style.overflow = 'auto'
  document.body.style.height = 'auto'
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
  startAutoplay()
  const el = carouselRef.value
  if (el) {
    el.addEventListener('mouseenter', stopAutoplay)
    el.addEventListener('mouseleave', startAutoplay)
  }
})
onUnmounted(() => {
  // 离开官网时恢复 body 约束（聊天页等需要 overflow:hidden）
  document.body.style.overflow = ''
  document.body.style.height = ''
  window.removeEventListener('scroll', onScroll)
  stopAutoplay()
  const el = carouselRef.value
  if (el) {
    el.removeEventListener('mouseenter', stopAutoplay)
    el.removeEventListener('mouseleave', startAutoplay)
  }
})
</script>

<template>
  <div class="landing">
    <!-- 顶部导航（粘性定位，滚动时始终可见） -->
    <header ref="headerRef" class="landing-header">
      <a class="logo" href="javascript:void(0)" @click="scrollTo('top')">
        <div class="logo-mark">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
          </svg>
        </div>
        <span class="logo-text">Chat<span class="accent">Vibe</span></span>
      </a>
      <nav class="nav-links">
        <a href="#capabilities" @click.prevent="scrollTo('capabilities')">核心能力</a>
        <a href="#architecture" @click.prevent="scrollTo('architecture')">系统架构</a>
        <a href="#techstack" @click.prevent="scrollTo('techstack')">技术栈</a>
        <a href="#quickstart" @click.prevent="scrollTo('quickstart')">快速开始</a>
        <ThemeSwitcher class="nav-theme-switcher" />
        <a href="https://github.com/" target="_blank" rel="noopener" class="nav-github" title="GitHub 仓库">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0 1 12 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222 0 1.606-.014 2.898-.014 3.293 0 .322.216.694.825.576C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"></path>
          </svg>
        </a>
        <a href="javascript:void(0)" class="btn btn-primary btn-sm" style="color:#fff" @click="goAuth">快速开始</a>
      </nav>
    </header>

    <!-- 主视觉区 -->
    <section class="landing-hero" id="top">
      <div class="hero-eyebrow">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
        </svg>
        v1.0.0 正式发布
      </div>
      <h1>
        让每一次对话<br>
        都有 <span class="grad">温度</span>，也有 <span class="ai-grad">智能</span>
      </h1>
      <p class="hero-sub">
        ChatVibe 是一款轻量级聊天平台，将真人实时通讯与 AI 智能对话融为一体。
        私聊、群聊、@AI 召唤，一个界面完成所有协作。
      </p>
      <div class="hero-cta">
        <a href="javascript:void(0)" class="btn btn-primary" @click="goAuth">
          快速开始
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="5" y1="12" x2="19" y2="12"></line>
            <polyline points="12 5 19 12 12 19"></polyline>
          </svg>
        </a>
        <a href="javascript:void(0)" class="btn btn-secondary" @click="goChat">在线体验</a>
      </div>

      <!-- Hero 技术指标行 -->
      <div class="hero-metrics">
        <div class="metric">
          <div class="metric-val">实时</div>
          <div class="metric-label">消息通讯</div>
        </div>
        <div class="metric-sep"></div>
        <div class="metric">
          <div class="metric-val">AI</div>
          <div class="metric-label">智能召唤</div>
        </div>
        <div class="metric-sep"></div>
        <div class="metric">
          <div class="metric-val">群组</div>
          <div class="metric-label">团队协作</div>
        </div>
        <div class="metric-sep"></div>
        <div class="metric">
          <div class="metric-val">加密</div>
          <div class="metric-label">端到端安全</div>
        </div>
      </div>

      <!-- 产品预览（三图轮播） -->
      <div class="product-preview">
        <div class="preview-bar">
          <span class="dot red"></span><span class="dot yellow"></span><span class="dot green"></span>
          <div class="preview-tabs">
            <button
              v-for="(slide, idx) in slides"
              :key="idx"
              class="preview-tab"
              :class="{ active: currentSlide === idx }"
              @click="goToSlide(idx)"
            >
              {{ slide.tab }}
            </button>
          </div>
        </div>

        <!-- 轮播轨道 -->
        <div class="preview-carousel" ref="carouselRef">
          <button class="carousel-arrow prev" aria-label="上一张" @click="prevSlide">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="15 18 9 12 15 6"></polyline>
            </svg>
          </button>
          <div class="preview-track">
            <div
              v-for="(slide, idx) in slides"
              :key="idx"
              class="preview-slide"
              :class="{ active: currentSlide === idx }"
            >
              <img :src="slide.src" :alt="slide.alt" />
            </div>
          </div>
          <button class="carousel-arrow next" aria-label="下一张" @click="nextSlide">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="9 18 15 12 9 6"></polyline>
            </svg>
          </button>

          <!-- 指示点 -->
          <div class="carousel-dots">
            <button
              v-for="(_, idx) in slides"
              :key="idx"
              class="carousel-dot"
              :class="{ active: currentSlide === idx }"
              :aria-label="`第 ${idx + 1} 张`"
              @click="goToSlide(idx)"
            ></button>
          </div>
        </div>
      </div>
    </section>

    <!-- 核心能力 -->
    <section class="capabilities" id="capabilities">
      <div class="section-head">
        <div class="section-tag">核心能力</div>
        <h2>企业级功能，开箱即用</h2>
        <p>覆盖即时通讯所需的全部能力，从私聊到群组、从真人到 AI，一站式满足协作需求</p>
      </div>
      <div class="cap-grid">
        <div class="cap-card">
          <div class="cap-icon icon-blue">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
            </svg>
          </div>
          <div class="cap-title">即时通讯</div>
          <div class="cap-desc">文字、图片、表情实时收发，消息秒级送达，在线状态一目了然。</div>
          <div class="cap-tags"><span>实时消息</span><span>在线状态</span><span>已读回执</span></div>
        </div>
        <div class="cap-card">
          <div class="cap-icon icon-green">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
          </div>
          <div class="cap-title">群组协作</div>
          <div class="cap-desc">一键建群，群主邀请与成员管理，团队讨论、兴趣社群尽在掌握。</div>
          <div class="cap-tags"><span>群聊</span><span>群主管理</span><span>@提及</span></div>
        </div>
        <div class="cap-card">
          <div class="cap-icon icon-ai">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="11" width="18" height="10" rx="2"></rect>
              <circle cx="12" cy="5" r="2"></circle>
              <path d="M12 7v4"></path>
              <line x1="8" y1="16" x2="8" y2="16"></line>
              <line x1="16" y1="16" x2="16" y2="16"></line>
            </svg>
          </div>
          <div class="cap-title">AI 智能召唤</div>
          <div class="cap-desc">群内 @AI 即可提问，流式回复秒出答案，无需切换应用。</div>
          <div class="cap-tags"><span>@AI</span><span>流式回复</span><span>多场景</span></div>
        </div>
        <div class="cap-card">
          <div class="cap-icon icon-amber">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
              <polyline points="22,6 12,13 2,6"></polyline>
            </svg>
          </div>
          <div class="cap-title">邮箱好友体系</div>
          <div class="cap-desc">通过邮箱精准搜索添加好友，验证码注册与密码找回，安全可靠。</div>
          <div class="cap-tags"><span>邮箱搜索</span><span>好友请求</span><span>验证码</span></div>
        </div>
        <div class="cap-card">
          <div class="cap-icon icon-violet">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
            </svg>
          </div>
          <div class="cap-title">安全加密</div>
          <div class="cap-desc">账号密码加密存储，会话数据安全隔离，用心守护你的隐私。</div>
          <div class="cap-tags"><span>加密存储</span><span>数据隔离</span><span>隐私保护</span></div>
        </div>
        <div class="cap-card">
          <div class="cap-icon icon-cyan">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
              <line x1="8" y1="21" x2="16" y2="21"></line>
              <line x1="12" y1="17" x2="12" y2="21"></line>
            </svg>
          </div>
          <div class="cap-title">三栏专注体验</div>
          <div class="cap-desc">会话、消息、详情三栏布局，右键菜单与快捷操作，工作流不打断。</div>
          <div class="cap-tags"><span>三栏布局</span><span>右键菜单</span><span>快捷操作</span></div>
        </div>
      </div>
    </section>

    <!-- 系统架构 -->
    <section class="architecture" id="architecture">
      <div class="section-head">
        <div class="section-tag">系统架构</div>
        <h2>前后端分离，分层架构</h2>
        <p>前端 Vue 3 SPA 通过 REST API + WebSocket 与 Spring Boot 后端通信，自上而下四层架构，职责单一、边界清晰</p>
      </div>
      <div class="arch-stack">
        <div class="arch-layer">
          <div class="arch-layer-head">
            <div class="arch-layer-no">01</div>
            <div>
              <div class="arch-layer-name">前端展示层 · Vue 3 SPA</div>
              <div class="arch-layer-desc">Vue 3 + TypeScript + Vite，Axios HTTP + SockJS/STOMP 实时通信</div>
            </div>
          </div>
          <div class="arch-modules">
            <span class="arch-module">官网首页</span>
            <span class="arch-module">登录 / 注册</span>
            <span class="arch-module">聊天工作台</span>
            <span class="arch-module">个人中心</span>
          </div>
        </div>
        <div class="arch-connector"><span></span></div>
        <div class="arch-layer">
          <div class="arch-layer-head">
            <div class="arch-layer-no">02</div>
            <div>
              <div class="arch-layer-name">网关安全层 · Spring Security</div>
              <div class="arch-layer-desc">Spring Boot 3.2 + Spring Security + JWT，统一鉴权与 CORS 路由</div>
            </div>
          </div>
          <div class="arch-modules">
            <span class="arch-module">JWT 认证</span>
            <span class="arch-module">接口鉴权</span>
            <span class="arch-module">CORS 配置</span>
            <span class="arch-module">WebSocket 握手</span>
          </div>
        </div>
        <div class="arch-connector"><span></span></div>
        <div class="arch-layer">
          <div class="arch-layer-head">
            <div class="arch-layer-no">03</div>
            <div>
              <div class="arch-layer-name">业务服务层 · Controller / Service</div>
              <div class="arch-layer-desc">模块化业务逻辑，Controller → Service → MyBatis-Plus Mapper 分层调用</div>
            </div>
          </div>
          <div class="arch-modules">
            <span class="arch-module">认证 / 用户</span>
            <span class="arch-module">好友 / 群组</span>
            <span class="arch-module">聊天消息</span>
            <span class="arch-module">AI 对话</span>
          </div>
        </div>
        <div class="arch-connector"><span></span></div>
        <div class="arch-layer">
          <div class="arch-layer-head">
            <div class="arch-layer-no">04</div>
            <div>
              <div class="arch-layer-name">数据基础设施 · MySQL / Redis / AI</div>
              <div class="arch-layer-desc">MySQL 持久化 + Redis 缓存会话 + 163 邮件 + Ollama/OpenAI 智能推理</div>
            </div>
          </div>
          <div class="arch-modules">
            <span class="arch-module">MySQL 8.0</span>
            <span class="arch-module">Redis 8.0</span>
            <span class="arch-module">Spring Mail</span>
            <span class="arch-module">Ollama AI</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 技术栈 -->
    <section class="techstack" id="techstack">
      <div class="section-head">
        <div class="section-tag">技术栈</div>
        <h2>前后端分离，全栈技术</h2>
        <p>前端 Vue 3 全家桶 + 后端 Spring Boot 3.2，涵盖认证、实时通信、数据持久化与 AI 智能推理</p>
      </div>
      <div class="tech-grid">
        <div class="tech-card">
          <div class="tech-icon tech-vue">Vue</div>
          <div class="tech-name">Vue 3.5</div>
          <div class="tech-desc">渐进式前端框架，Composition API + 单文件组件，响应式驱动</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-ts">TS</div>
          <div class="tech-name">TypeScript 5.6</div>
          <div class="tech-desc">前后端类型安全，接口与实体严格定义，提升可维护性</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-vite">Vite</div>
          <div class="tech-name">Vite 5</div>
          <div class="tech-desc">极速冷启动 + HMR 热更新，原生 ESM 开发体验</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-pinia">Pinia</div>
          <div class="tech-name">Pinia 2</div>
          <div class="tech-desc">轻量级状态管理，组合式 API 友好，DevTools 调试便捷</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-ep">EP</div>
          <div class="tech-name">Element Plus 2.9</div>
          <div class="tech-desc">企业级 UI 组件库，按需自动导入，主题可定制</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-stomp">WS</div>
          <div class="tech-name">SockJS + STOMP</div>
          <div class="tech-desc">WebSocket 实时通信，消息订阅推送，断线自动重连</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-spring">SB</div>
          <div class="tech-name">Spring Boot 3.2</div>
          <div class="tech-desc">后端应用框架，Java 17，自动配置与 starter 依赖管理</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-security">Sec</div>
          <div class="tech-name">Spring Security</div>
          <div class="tech-desc">认证授权框架，JWT 令牌签发与校验，接口级权限控制</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-mybatis">MP</div>
          <div class="tech-name">MyBatis-Plus</div>
          <div class="tech-desc">增强版 ORM，CRUD 自动生成，分页插件与代码生成器</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-mysql">SQL</div>
          <div class="tech-name">MySQL 8.0</div>
          <div class="tech-desc">主数据库，用户/会话/消息/好友关系结构化持久存储</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-redis">RDS</div>
          <div class="tech-name">Redis 8.0</div>
          <div class="tech-desc">缓存与会话存储，在线状态、未读计数、验证码、限流</div>
        </div>
        <div class="tech-card">
          <div class="tech-icon tech-ollama">AI</div>
          <div class="tech-name">Ollama AI</div>
          <div class="tech-desc">本地 AI 推理（dev: deepseek-r1:8b），prod 切换 OpenAI 兼容接口</div>
        </div>
      </div>
    </section>

    <!-- 快速开始 -->
    <section class="quickstart" id="quickstart">
      <div class="section-head">
        <div class="section-tag">快速开始</div>
        <h2>三步开启，即刻体验</h2>
        <p>简单几步即可开始你的有温度的沟通，注册即送完整体验</p>
      </div>
      <div class="steps">
        <div class="step">
          <div class="step-no">1</div>
          <div class="step-title">注册账号</div>
          <div class="step-desc">邮箱验证码快速注册，10 秒创建你的专属账号。</div>
        </div>
        <div class="step-line"></div>
        <div class="step">
          <div class="step-no">2</div>
          <div class="step-title">添加好友</div>
          <div class="step-desc">通过邮箱搜索添加好友，或创建群组邀请伙伴加入。</div>
        </div>
        <div class="step-line"></div>
        <div class="step">
          <div class="step-no">3</div>
          <div class="step-title">开启对话</div>
          <div class="step-desc">私聊、群聊、@AI 召唤，开始有温度的智能沟通。</div>
        </div>
      </div>
      <div class="qs-cta">
        <a href="javascript:void(0)" class="btn btn-primary btn-lg" @click="goAuth">
          立即免费注册
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="5" y1="12" x2="19" y2="12"></line>
            <polyline points="12 5 19 12 12 19"></polyline>
          </svg>
        </a>
        <a href="javascript:void(0)" class="qs-login" @click="goAuth">已有账号？去登录 →</a>
      </div>
    </section>

    <!-- 页脚 -->
    <footer class="landing-footer">
      <div class="logo">
        <div class="logo-mark">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
          </svg>
        </div>
        <span class="logo-text">Chat<span class="accent">Vibe</span></span>
      </div>
      <div class="footer-copy">© 2026 ChatVibe · 让沟通更有温度 </div>
    </footer>
  </div>
</template>
