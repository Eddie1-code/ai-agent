<template>
  <div class="app-shell">
    <router-view />
    <div class="page-noise"></div>
  </div>
</template>

<script setup>
// 全局应用逻辑
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  position: relative;
}

.page-noise {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image: radial-gradient(rgba(255, 255, 255, 0.08) 0.6px, transparent 0.6px);
  background-size: 2px 2px;
  opacity: 0.08;
  z-index: 0;
}
</style>
