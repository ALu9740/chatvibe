<script setup lang="ts">
/** 快速开始区 —— 3 步流程 + 流动粒子连接线 + 注册 CTA */
const emit = defineEmits<{
  (e: 'cta'): void
}>()

interface Step {
  no: number
  title: string
  desc: string
}

const steps: Step[] = [
  { no: 1, title: '注册账号', desc: '邮箱验证码快速注册，10 秒创建你的专属账号。' },
  { no: 2, title: '添加好友', desc: '通过邮箱搜索添加好友，或创建群组邀请伙伴加入。' },
  { no: 3, title: '开启对话', desc: '私聊、群聊、@AI 召唤，开始有温度的智能沟通。' }
]
</script>

<template>
  <section class="landing-section" id="quickstart">
    <div class="section-head">
      <div class="section-tag">快速开始</div>
      <h2>三步开启，<span class="ai-grad-text">即刻体验</span></h2>
      <p>简单几步即可开始你的有温度的沟通，注册即送完整体验</p>
    </div>

    <!-- 步骤卡片 -->
    <div class="steps">
      <div v-for="(step, idx) in steps" :key="idx" class="step-wrapper">
        <div class="step-card">
          <div class="step-no">{{ step.no }}</div>
          <div class="step-title">{{ step.title }}</div>
          <div class="step-desc">{{ step.desc }}</div>
        </div>
        <!-- 连接线 + 流动粒子 -->
        <div v-if="idx < steps.length - 1" class="step-connector">
          <div class="connector-line"></div>
          <div class="connector-particle"></div>
          <div class="connector-particle" style="animation-delay: 1s"></div>
          <div class="connector-particle" style="animation-delay: 2s"></div>
        </div>
      </div>
    </div>

    <!-- 注册 CTA -->
    <div class="qs-cta">
      <a href="javascript:void(0)" class="btn btn-primary btn-lg" @click="emit('cta')">
        立即免费注册
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <line x1="5" y1="12" x2="19" y2="12"></line>
          <polyline points="12 5 19 12 12 19"></polyline>
        </svg>
      </a>
      <a href="javascript:void(0)" class="qs-login" @click="emit('cta')">已有账号？去登录 →</a>
    </div>
  </section>
</template>

<style scoped lang="scss">
.steps {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 64px;
}

.step-wrapper {
  display: flex;
  align-items: center;
}

.step-card {
  text-align: center;
  padding: 32px 24px;
  background: var(--landing-card);
  border: 1px solid var(--landing-border);
  border-radius: $r-xl;
  min-width: 220px;
  transition: all $dur-normal $ease-out-expo;

  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-md;
  }
}

.step-no {
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
  border-radius: $r-full;
  background: $grad-primary;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: $font-display;
  font-size: 20px;
  font-weight: 700;
}

.step-title {
  font-family: $font-display;
  font-size: 16px;
  font-weight: 700;
  color: var(--landing-text);
  margin-bottom: 8px;
}

.step-desc {
  font-size: 13px;
  color: var(--landing-text-soft);
  line-height: 1.6;
}

/* 连接线 + 流动粒子 */
.step-connector {
  position: relative;
  width: 80px;
  height: 2px;
  flex-shrink: 0;
}

.connector-line {
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, $c-primary, $c-ai);
  opacity: 0.3;
  border-radius: $r-full;
}

.connector-particle {
  position: absolute;
  top: 50%;
  width: 8px;
  height: 8px;
  border-radius: $r-full;
  background: $grad-ai;
  transform: translateY(-50%);
  animation: flowParticle 3s linear infinite;
  box-shadow: 0 0 8px rgba(124, 58, 237, 0.4);
}

/* CTA */
.qs-cta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.qs-login {
  font-size: 14px;
  color: var(--landing-text-soft);
  text-decoration: none;
  transition: color $dur-fast $ease-smooth;

  &:hover {
    color: $c-primary;
  }
}

@media (max-width: 1024px) {
  .steps {
    flex-direction: column;
    gap: 0;
  }

  .step-wrapper {
    flex-direction: column;

    .step-connector {
      width: 2px;
      height: 40px;

      .connector-line {
        background: linear-gradient(180deg, $c-primary, $c-ai);
      }

      .connector-particle {
        animation: flowParticleV 3s linear infinite;
      }
    }
  }
}

@keyframes flowParticleV {
  0% { top: 0%; opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}
</style>
