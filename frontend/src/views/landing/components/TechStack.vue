<script setup lang="ts">
/** 技术栈区 —— 3x4 网格，12 张技术卡片，品牌色图标 */
interface TechItem {
  abbr: string
  name: string
  desc: string
  color: string
}

const techs: TechItem[] = [
  { abbr: 'Vue', name: 'Vue 3.5', desc: '渐进式前端框架，Composition API + SFC', color: '#42B883' },
  { abbr: 'TS', name: 'TypeScript 5.6', desc: '前后端类型安全，接口与实体严格定义', color: '#3178C6' },
  { abbr: 'Vite', name: 'Vite 5', desc: '极速冷启动 + HMR 热更新，原生 ESM', color: '#646CFF' },
  { abbr: 'Pinia', name: 'Pinia 2', desc: '轻量级状态管理，组合式 API 友好', color: '#FFD859' },
  { abbr: 'EP', name: 'Element Plus 2.9', desc: '企业级 UI 组件库，按需自动导入', color: '#409EFF' },
  { abbr: 'WS', name: 'SockJS + STOMP', desc: 'WebSocket 实时通信，消息订阅推送', color: '#2563EB' },
  { abbr: 'SB', name: 'Spring Boot 3.2', desc: '后端框架，Java 17，自动配置', color: '#6DB33F' },
  { abbr: 'Sec', name: 'Spring Security', desc: 'JWT 认证授权，接口级权限控制', color: '#6DB33F' },
  { abbr: 'MP', name: 'MyBatis-Plus', desc: '增强 ORM，CRUD 自动生成，分页', color: '#BA372A' },
  { abbr: 'SQL', name: 'MySQL 8.0', desc: '主数据库，结构化持久存储', color: '#4479A1' },
  { abbr: 'RDS', name: 'Redis 8.0', desc: '缓存与会话存储，在线状态/限流', color: '#DC382D' },
  { abbr: 'AI', name: 'Ollama AI', desc: '本地 AI 推理，流式回复生成', color: '#7C3AED' }
]
</script>

<template>
  <section class="landing-section" id="techstack">
    <div class="section-head">
      <div class="section-tag">技术栈</div>
      <h2>前后端分离，<span class="ai-grad-text">全栈技术</span></h2>
      <p>前端 Vue 3 全家桶 + 后端 Spring Boot 3.2，涵盖认证、实时通信、数据持久化与 AI 智能推理</p>
    </div>

    <div class="tech-grid">
      <div v-for="(tech, idx) in techs" :key="idx" class="tech-card">
        <div class="tech-icon" :style="{ background: `linear-gradient(135deg, ${tech.color}, ${tech.color}DD)` }">
          {{ tech.abbr }}
        </div>
        <div class="tech-info">
          <div class="tech-name">{{ tech.name }}</div>
          <div class="tech-desc">{{ tech.desc }}</div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.tech-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.tech-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  background: var(--landing-card);
  border: 1px solid var(--landing-border);
  border-radius: $r-lg;
  transition: all $dur-normal $ease-out-expo;

  &:hover {
    transform: translateY(-3px);
    box-shadow: $shadow-md;

    .tech-icon {
      box-shadow: 0 0 16px rgba(0, 0, 0, 0.12);
      transform: scale(1.05);
    }
  }
}

.tech-icon {
  width: 48px;
  height: 48px;
  border-radius: $r-md;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-family: $font-display;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
  transition: all $dur-normal $ease-out-expo;
}

.tech-info {
  min-width: 0;
}

.tech-name {
  font-family: $font-display;
  font-size: 14px;
  font-weight: 600;
  color: var(--landing-text);
  margin-bottom: 4px;
}

.tech-desc {
  font-size: 12px;
  color: var(--landing-text-soft);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

@media (max-width: 1024px) {
  .tech-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 640px) {
  .tech-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
