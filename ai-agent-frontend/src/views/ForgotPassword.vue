<template>
  <div class="auth-page section-shell">
    <section class="panel auth-card">
      <h1>找回密码</h1>
      <p class="auth-tip">输入用户名并通过验证码后即可重置密码。</p>
      <form @submit.prevent="handleReset" class="auth-form">
        <input v-model.trim="form.username" placeholder="用户名" required />
        <div class="captcha-row">
          <input v-model.trim="form.captchaCode" placeholder="验证码" required class="captcha-input" />
          <img v-if="captchaImage" :src="captchaImage" alt="验证码" class="captcha-img" @click="refreshCaptcha" title="点击刷新验证码" />
        </div>
        <input v-model="form.newPassword" type="password" placeholder="新密码（至少 6 位）" required />
        <button class="btn-pill btn-pill--primary" :disabled="loading">
          {{ loading ? '重置中...' : '重置密码' }}
        </button>
      </form>
      <p v-if="success" class="auth-success">{{ success }}</p>
      <p v-if="error" class="auth-error">{{ error }}</p>
      <p class="auth-link">
        <router-link to="/login">返回登录</router-link>
      </p>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { resetPassword, getCaptcha } from '../api'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const success = ref('')
const captchaKey = ref('')
const captchaImage = ref('')
const form = reactive({
  username: '',
  captchaCode: '',
  newPassword: ''
})

const refreshCaptcha = async () => {
  try {
    const res = await getCaptcha()
    captchaKey.value = res?.data?.captchaKey || ''
    captchaImage.value = res?.data?.captchaImage || ''
    form.captchaCode = ''
    error.value = ''
  } catch (e) {
    error.value = '获取验证码失败，请刷新重试'
  }
}

onMounted(refreshCaptcha)

const handleReset = async () => {
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    if (form.newPassword.length < 6) {
      throw new Error('新密码至少 6 位')
    }
    if (!form.captchaCode) {
      throw new Error('请输入验证码')
    }
    await resetPassword({
      username: form.username,
      captchaKey: captchaKey.value,
      captchaCode: form.captchaCode,
      newPassword: form.newPassword
    })
    success.value = '密码重置成功，即将跳转到登录页...'
    setTimeout(() => router.replace('/login'), 2000)
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '重置失败'
    refreshCaptcha()
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
.captcha-row {
  display: flex;
  gap: 10px;
  align-items: center;
}
.captcha-input {
  flex: 1;
  min-width: 0;
}
.captcha-img {
  height: 42px;
  border-radius: 8px;
  cursor: pointer;
  flex-shrink: 0;
}
.auth-error { margin-top: 10px; color: #ff8ea1; }
.auth-success { margin-top: 10px; color: #7fe7ff; }
.auth-link { margin-top: 12px; color: var(--ink-muted); }
</style>
