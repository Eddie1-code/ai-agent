<template>
  <div class="receipt-page section-shell">
    <section class="panel receipt-card">
      <p class="eyebrow">Issue Legacy / Progress Receipt</p>
      <h1>Mentor Progress Sheet</h1>
      <p>这是导师周期的可视化收据页。你可以用它来展示一个阶段内的计划执行与复盘结果。</p>
      <ul>
        <li v-for="item in items" :key="item">{{ item }}</li>
      </ul>
      <router-link class="btn-pill btn-pill--primary" to="/chat?mode=planner">回到计划台</router-link>
    </section>
  </div>
</template>

<script setup>
const items = [
  '目标拆解完成率：82%',
  '本周关键任务完成：6 / 8',
  '复盘记录更新：4 条',
  '下周期重点：作息稳定 + 高价值任务优先'
]
</script>

<style scoped>
.receipt-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
}

.receipt-card {
  max-width: 760px;
  padding: 32px;
}

h1 {
  margin: 12px 0;
}

p,
li {
  color: var(--ink-soft);
}

ul {
  margin: 14px 0 18px;
  padding-left: 18px;
  display: grid;
  gap: 8px;
}
</style>
