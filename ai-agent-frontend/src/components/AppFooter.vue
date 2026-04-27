<template>
  <footer class="app-footer panel">
    <div class="footer-grid">
      <section>
        <p class="eyebrow">MentorVerse</p>
        <h3>{{ brandCopy.brandName }}</h3>
        <p class="copy">{{ brandCopy.footer.slogan }}</p>
      </section>
      <section>
        <p class="eyebrow">Policy</p>
        <div class="links">
          <a v-for="item in brandCopy.footer.links" :key="item" href="#">{{ item }}</a>
        </div>
      </section>
      <section>
        <p class="eyebrow">Status</p>
        <p class="copy">{{ brandCopy.footer.note }}</p>
      </section>
    </div>
    <div class="footer-bottom">
      <span>© {{ currentYear }} {{ brandCopy.brandName }}</span>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
import { brandCopy } from '../constants/copy'

const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.app-footer {
  margin-top: 24px;
  padding: 24px;
}

.footer-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr;
  gap: 18px;
}

h3 {
  margin: 8px 0;
}

.copy {
  color: var(--ink-soft);
}

.links {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.links a {
  color: var(--ink-muted);
}

.footer-bottom {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
  color: var(--ink-muted);
  font-size: 13px;
}

@media (max-width: 840px) {
  .footer-grid {
    grid-template-columns: 1fr;
  }
}
</style>
