<template>
  <div class="auth-page">
    <div class="auth-grid section-shell">
      <section class="auth-intro">
        <p class="auth-intro__eyebrow">MentorVerse Access</p>
        <h1>连接你的 AI 生活导师</h1>
        <p class="auth-intro__desc">
          登录后继续你的个人成长会话，查看历史记录，保持规划节奏与复盘连续性。
        </p>
        <ul class="auth-intro__points">
          <li>多会话历史同步</li>
          <li>个性化导师上下文记忆</li>
          <li>计划导出与持续追踪</li>
        </ul>
      </section>

      <section class="panel auth-card">
        <div class="auth-card__glow" aria-hidden="true"></div>
        <p class="auth-card__tag">Secure Sign In</p>
        <h2>登录 AI 生活导师</h2>
        <p class="auth-tip">输入账号信息继续你的导师会话。</p>
        <form @submit.prevent="handleLogin" class="auth-form">
          <label>
            <span>用户名</span>
            <input v-model.trim="form.username" placeholder="请输入用户名" required />
          </label>
          <label>
            <span>密码</span>
            <input v-model="form.password" type="password" placeholder="请输入密码" required />
          </label>
          <button class="btn-pill btn-pill--primary auth-submit" :disabled="loading">
            {{ loading ? '登录中...' : '进入控制台' }}
          </button>
        </form>
        <p v-if="error" class="auth-error">{{ error }}</p>
        <p class="auth-link">
          还没有账号？
          <router-link to="/register">去注册</router-link>
        </p>
      </section>
    </div>
    <div class="auth-noise" aria-hidden="true"></div>
  </div>
 </template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, setAuthToken } from '../api'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const error = ref('')
const form = reactive({
  username: '',
  password: ''
})

const handleLogin = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await login(form)
    const token = res?.data?.token
    if (!token) throw new Error('未获取到 token')
    setAuthToken(token)
    router.replace(route.query.redirect || '/chat')
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px 0;
  position: relative;
}

.auth-grid {
  width: min(1080px, 100% - 24px);
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
  gap: 20px;
  align-items: stretch;
}

.auth-intro {
  position: relative;
  border: 1px solid rgba(127, 231, 255, 0.16);
  border-radius: 28px;
  padding: 34px 28px;
  background:
    radial-gradient(circle at 12% 16%, rgba(127, 231, 255, 0.16), transparent 32%),
    linear-gradient(160deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.02));
  overflow: hidden;
}

.auth-intro::after {
  content: '';
  position: absolute;
  inset: auto -20% -40% auto;
  width: 260px;
  height: 260px;
  background: radial-gradient(circle, rgba(239, 46, 53, 0.22), transparent 62%);
  filter: blur(18px);
}

.auth-intro__eyebrow {
  margin: 0;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: #9fd9ff;
}

.auth-intro h1 {
  margin: 10px 0 0;
  font-size: clamp(1.7rem, 3vw, 2.35rem);
  line-height: 1.2;
}

.auth-intro__desc {
  margin: 14px 0 0;
  color: var(--ink-soft);
  max-width: 560px;
}

.auth-intro__points {
  margin: 20px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.auth-intro__points li {
  position: relative;
  padding-left: 16px;
  color: #d5e8fb;
}

.auth-intro__points li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.7em;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: linear-gradient(135deg, #7fe7ff, #ff5b62);
  box-shadow: 0 0 10px rgba(127, 231, 255, 0.5);
}

.auth-card {
  position: relative;
  border-radius: 28px;
  padding: 24px;
  overflow: hidden;
}

.auth-card__glow {
  position: absolute;
  width: 220px;
  height: 220px;
  right: -60px;
  top: -80px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(127, 231, 255, 0.36), transparent 62%);
  filter: blur(12px);
  pointer-events: none;
}

.auth-card__tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  border-radius: 999px;
  border: 1px solid rgba(143, 191, 255, 0.35);
  padding: 0 10px;
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #a9dcff;
}

.auth-card h2 {
  margin: 12px 0 0;
  font-size: 1.6rem;
}

.auth-tip {
  margin: 6px 0 16px;
  color: var(--ink-soft);
}

.auth-form {
  display: grid;
  gap: 10px;
}

.auth-form label {
  display: grid;
  gap: 6px;
}

.auth-form label span {
  font-size: 12px;
  color: var(--ink-muted);
}

input {
  height: 44px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.06);
  color: var(--ink);
  padding: 0 12px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

input:focus {
  outline: none;
  border-color: rgba(127, 231, 255, 0.8);
  box-shadow: 0 0 0 3px rgba(127, 231, 255, 0.15);
}

.auth-submit {
  margin-top: 4px;
}

.auth-error {
  margin-top: 10px;
  color: #ff8ea1;
}

.auth-link {
  margin-top: 12px;
  color: var(--ink-muted);
}

.auth-link a {
  color: #9fd9ff;
}

.auth-noise {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image: radial-gradient(rgba(255, 255, 255, 0.07) 0.7px, transparent 0.7px);
  background-size: 3px 3px;
  opacity: 0.1;
}

@media (max-width: 900px) {
  .auth-grid {
    grid-template-columns: 1fr;
  }

  .auth-intro {
    padding: 24px 20px;
  }

  .auth-card {
    padding: 20px;
  }
}
</style>
