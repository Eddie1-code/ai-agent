<template>
  <div class="legacy-page section-shell">
    <section class="panel legacy-card">
      <p class="eyebrow">Issue Legacy / Coach Console</p>
      <h1>导师模式入口</h1>
      <p>历史入口已和新版视觉同构。你可以直接从这里进入导师模式，继续当前目标推进。</p>
      <router-link class="btn-pill btn-pill--primary" to="/chat?mode=coach">进入导师模式</router-link>
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
