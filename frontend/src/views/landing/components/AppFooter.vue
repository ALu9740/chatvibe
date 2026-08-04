<script setup lang="ts">
/** 页脚 —— Logo + 版权信息 */
</script>

<template>
  <footer class="landing-footer">
    <div class="footer-inner">
      <div class="footer-logo">
        <div class="logo-mark">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
          </svg>
        </div>
        <span class="footer-logo-text">Chat<span class="accent">Vibe</span></span>
      </div>
      <div class="footer-copy">© 2026 ChatVibe · 让沟通更有温度</div>
    </div>
  </footer>
</template>

<style scoped lang="scss">
.landing-footer {
  padding: 40px 48px;
  background: rgba(15, 23, 42, 0.03);
  border-top: 1px solid var(--landing-border);
}

.footer-inner {
  max-width: $landing-max-w;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: $font-display;
  font-weight: 700;
  font-size: 18px;
  color: var(--landing-text);

  .logo-mark {
    width: 32px;
    height: 32px;
    border-radius: $r-sm;
    background: $grad-brand;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
  }

  .accent {
    background: $grad-primary;
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

.footer-copy {
  font-size: 14px;
  color: var(--landing-text-muted);
}

@media (max-width: 640px) {
  .landing-footer {
    padding: 24px 16px;
  }

  .footer-inner {
    flex-direction: column;
    gap: 12px;
    text-align: center;
  }
}
</style>
