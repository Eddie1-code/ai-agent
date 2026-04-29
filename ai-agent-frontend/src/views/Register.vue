<template>
  <div class="auth-page section-shell">
    <section class="panel auth-card">
      <h1>注册账号</h1>
      <p class="auth-tip">创建账号后即可使用多会话和个人中心功能。</p>
      <form @submit.prevent="handleRegister" class="auth-form">
        <input v-model.trim="form.username" placeholder="用户名" required />
        <input v-model="form.password" type="password" placeholder="密码（至少 6 位）" required />
        <button class="btn-pill btn-pill--primary" :disabled="loading">
          {{ loading ? '注册中...' : '注册并登录' }}
        </button>
      </form>
      <p v-if="error" class="auth-error">{{ error }}</p>
      <p class="auth-link">
        已有账号？
        <router-link to="/login">去登录</router-link>
      </p>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register, setAuthToken } from '../api'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const form = reactive({
  username: '',
  password: ''
})

const handleRegister = async () => {
  loading.value = true
  error.value = ''
  try {
    if (form.password.length < 6) {
      throw new Error('密码至少 6 位')
    }
    const res = await register(form)
    const token = res?.data?.token
    if (!token) throw new Error('注册成功但未获取到 token')
    setAuthToken(token)
    router.replace('/chat')
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; display: grid; place-items: center; }
.auth-card { width: min(520px, 92vw); padding: 28px; }
.auth-tip { margin: 8px 0 18px; color: var(--ink-soft); }
.auth-form { display: grid; gap: 10px; }
input {
  height: 42px; border-radius: 12px; border: 1px solid var(--line-soft);
  background: rgba(255,255,255,.06); color: var(--ink); padding: 0 12px;
}
.auth-error { margin-top: 10px; color: #ff8ea1; }
.auth-link { margin-top: 12px; color: var(--ink-muted); }
</style>
