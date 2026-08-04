<script setup lang="ts">
/** 核心能力区 —— 3x2 网格，6 张卡片，hover 上浮 + 图标微光 */
import { ref, onMounted, onUnmounted } from 'vue'

interface Capability {
  icon: string
  iconClass: string
  title: string
  desc: string
  tags: string[]
}

const capabilities: Capability[] = [
  {
    icon: 'M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z',
    iconClass: 'icon-blue',
    title: '即时通讯',
    desc: '文字、图片、表情实时收发，消息秒级送达，在线状态一目了然。',
    tags: ['实时消息', '在线状态', '已读回执']
  },
  {
    icon: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 7a4 4 0 1 0 0 8 4 4 0 0 0 0-8z M23 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75',
    iconClass: 'icon-green',
    title: '群组协作',
    desc: '一键建群，群主邀请与成员管理，团队讨论、兴趣社群尽在掌握。',
    tags: ['群聊', '群主管理', '@提及']
  },
  {
    icon: 'M12 2L2 7l10 5 10-5-10-5z M2 17l10 5 10-5 M2 12l10 5 10-5',
    iconClass: 'icon-ai',
    title: 'AI 智能召唤',
    desc: '群内 @AI 即可提问，流式回复秒出答案，无需切换应用。',
    tags: ['@AI', '流式回复', '多场景']
  },
  {
    icon: 'M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z M22 6l-10 7L2 6',
    iconClass: 'icon-amber',
    title: '邮箱好友体系',
    desc: '通过邮箱精准搜索添加好友，验证码注册与密码找回，安全可靠。',
    tags: ['邮箱搜索', '好友请求', '验证码']
  },
  {
    icon: 'M3 11h18v10H3z M7 11V7a5 5 0 0 1 10 0v4',
    iconClass: 'icon-violet',
    title: '安全加密',
    desc: '账号密码加密存储，会话数据安全隔离，用心守护你的隐私。',
    tags: ['加密存储', '数据隔离', '隐私保护']
  },
  {
    icon: 'M2 3h20v14H2z M8 21h8 M12 17v4',
    iconClass: 'icon-cyan',
    title: '三栏专注体验',
    desc: '会话、消息、详情三栏布局，右键菜单与快捷操作，工作流不打断。',
    tags: ['三栏布局', '右键菜单', '快捷操作']
  }
]

const visibleCards = ref(0)
let observer: IntersectionObserver | null = null

onMounted(() => {
  const el = document.querySelector('#capabilities')
  if (el) {
    observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) {
        capabilities.forEach((_, i) => {
          setTimeout(() => visibleCards.value++, i * 100)
        })
        observer?.disconnect()
      }
    }, { threshold: 0.2 })
    observer.observe(el)
  }
})

onUnmounted(() => observer?.disconnect())
</script>

<template>
  <section class="landing-section" id="capabilities">
    <div class="section-head">
      <div class="section-tag">核心能力</div>
      <h2>企业级功能，<span class="ai-grad-text">开箱即用</span></h2>
      <p>从私聊到群组、从真人到 AI，一站式满足协作需求</p>
    </div>

    <div class="cap-grid">
      <TransitionGroup name="card-stagger">
        <div
          v-for="(cap, idx) in capabilities"
          v-show="idx < visibleCards"
          :key="idx"
          class="cap-card"
        >
          <div class="cap-icon" :class="cap.iconClass">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path :d="cap.icon"></path>
            </svg>
          </div>
          <div class="cap-title">{{ cap.title }}</div>
          <div class="cap-desc">{{ cap.desc }}</div>
          <div class="cap-tags">
            <span v-for="tag in cap.tags" :key="tag" class="cap-tag">{{ tag }}</span>
          </div>
        </div>
      </TransitionGroup>
    </div>
  </section>
</template>

<style scoped lang="scss">
.cap-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.cap-card {
  background: var(--landing-card);
  border: 1px solid var(--landing-border);
  border-radius: $r-xl;
  padding: 32px 28px;
  transition: all $dur-normal $ease-out-expo;
  position: relative;
  overflow: hidden;

  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-md;
    border-color: rgba(37, 99, 235, 0.2);

    .cap-icon {
      &::after {
        opacity: 1;
      }
    }
  }
}

.cap-icon {
  width: 48px;
  height: 48px;
  border-radius: $r-md;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-bottom: 20px;
  position: relative;
  overflow: hidden;

  /* hover 时的微光扫过效果 */
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(110deg, transparent 30%, rgba(255, 255, 255, 0.3) 50%, transparent 70%);
    opacity: 0;
    transition: opacity $dur-normal $ease-smooth;
  }

  &.icon-blue { background: $grad-primary; }
  &.icon-green { background: linear-gradient(135deg, #059669, #10B981); }
  &.icon-ai { background: $grad-ai; box-shadow: $shadow-glow-ai; }
  &.icon-amber { background: linear-gradient(135deg, #D97706, #F59E0B); }
  &.icon-violet { background: linear-gradient(135deg, #7C3AED, #6366F1); }
  &.icon-cyan { background: linear-gradient(135deg, #0891B2, #06B6D4); }
}

.cap-title {
  font-family: $font-display;
  font-size: 18px;
  font-weight: 700;
  color: var(--landing-text);
  margin-bottom: 8px;
}

.cap-desc {
  font-size: 14px;
  color: var(--landing-text-soft);
  line-height: 1.6;
  margin-bottom: 16px;
}

.cap-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cap-tag {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: $r-full;
  background: rgba(37, 99, 235, 0.06);
  color: var(--landing-text-soft);
  font-weight: 500;
}

@media (max-width: 1024px) {
  .cap-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 640px) {
  .cap-grid { grid-template-columns: 1fr; }
}
</style>
