<template>
  <div class="legacy-page section-shell">
    <section class="panel legacy-card">
      <p class="eyebrow">Issue Legacy / Planner Console</p>
      <h1>规划模式入口</h1>
      <p>这里保留历史路径，交互与视觉已统一到新版体系。建议直接进入计划台继续你的执行路径。</p>
      <router-link class="btn-pill btn-pill--primary" to="/chat?mode=planner">进入规划模式</router-link>
    </section>
  </div>
</template>

<style scoped>
.legacy-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
}

.legacy-card {
  max-width: 740px;
  padding: 32px;
}

h1 {
  margin: 12px 0;
}

p {
  color: var(--ink-soft);
  margin-bottom: 16px;
}
</style>
