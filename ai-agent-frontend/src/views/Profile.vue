<template>
  <div class="profile-page">
    <section class="section-shell profile-shell">
      <header class="panel profile-hero">
        <div>
          <p class="profile-eyebrow">Account Center</p>
          <h1>个人中心</h1>
          <p class="profile-subtitle">管理你的公开资料与账号安全，保持一致、克制、清晰的个人形象。</p>
        </div>
        <button class="btn-pill" @click="goChat">返回聊天</button>
      </header>

      <div class="profile-grid">
        <section class="panel profile-card">
          <div class="card-title-wrap">
            <h2>基础资料</h2>
            <span class="card-badge">PROFILE</span>
          </div>

          <div class="avatar-row">
            <div class="avatar-preview">
              <img v-if="profile.avatarUrl" :src="profile.avatarUrl" alt="头像预览" />
              <span v-else>{{ avatarFallback }}</span>
            </div>
            <div class="avatar-actions">
              <input ref="avatarInput" class="avatar-input" type="file" accept="image/*" @change="onAvatarFileChange" />
              <button type="button" class="btn-pill" @click="triggerAvatarUpload">上传头像</button>
              <button
                v-if="profile.avatarUrl"
                type="button"
                class="btn-pill"
                @click="removeAvatar"
              >
                移除头像
              </button>
              <p class="avatar-tip">建议使用 1:1 比例，JPG/PNG/WebP，大小不超过 2MB。</p>
            </div>
          </div>

          <form class="profile-form" @submit.prevent="saveProfile">
            <label>
              <span>账号</span>
              <input v-model.trim="profile.username" disabled />
            </label>
            <label>
              <span>昵称</span>
              <input v-model.trim="profile.nickname" placeholder="请输入昵称" />
            </label>
            <button class="btn-pill btn-pill--primary" :disabled="loadingProfile">
              {{ loadingProfile ? '保存中...' : '保存资料' }}
            </button>
          </form>
        </section>

        <section class="panel profile-card">
          <div class="card-title-wrap">
            <h2>账号安全</h2>
            <span class="card-badge">SECURITY</span>
          </div>
          <p class="profile-tip">定期更新密码，避免与其他平台复用。</p>
          <form class="profile-form profile-form--pwd" @submit.prevent="savePassword">
            <label>
              <span>旧密码</span>
              <input v-model="passwordForm.oldPassword" type="password" placeholder="请输入旧密码" />
            </label>
            <label>
              <span>新密码</span>
              <input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" />
            </label>
            <button class="btn-pill" :disabled="loadingPwd">
              {{ loadingPwd ? '提交中...' : '修改密码' }}
            </button>
          </form>

          <button class="btn-pill profile-logout" @click="logout">退出登录</button>
        </section>
      </div>
      <p v-if="msg" class="profile-msg">{{ msg }}</p>
      <p v-if="error" class="profile-error">{{ error }}</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword, clearAuthToken, getProfile, updateProfile } from '../api'
import { compressAvatarFile } from '../utils/avatar'

const router = useRouter()
const loadingProfile = ref(false)
const loadingPwd = ref(false)
const msg = ref('')
const error = ref('')
const avatarInput = ref(null)
const profile = reactive({
  username: '',
  nickname: '',
  avatarUrl: ''
})
const passwordForm = reactive({
  oldPassword: '',
  newPassword: ''
})

const loadProfile = async () => {
  error.value = ''
  const res = await getProfile()
  const data = res?.data || {}
  profile.username = data.username || ''
  profile.nickname = data.nickname || ''
  profile.avatarUrl = data.avatarUrl || ''
}

const avatarFallback = computed(() => {
  const source = profile.nickname || profile.username || 'U'
  return String(source).trim().slice(0, 1).toUpperCase()
})

const triggerAvatarUpload = () => {
  avatarInput.value?.click()
}

const removeAvatar = () => {
  profile.avatarUrl = ''
}

const onAvatarFileChange = async (event) => {
  const file = event?.target?.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    error.value = '请选择图片文件'
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    error.value = '图片不能超过 2MB'
    return
  }
  error.value = ''
  try {
    const dataUrl = await compressAvatarFile(file, 200, 0.82)
    profile.avatarUrl = dataUrl
  } catch {
    error.value = '头像读取失败，请重试'
  } finally {
    if (event?.target) event.target.value = ''
  }
}

const readFileAsDataUrl = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('file read failed'))
    reader.readAsDataURL(file)
  })

const saveProfile = async () => {
  loadingProfile.value = true
  msg.value = ''
  error.value = ''
  try {
    await updateProfile({ nickname: profile.nickname, avatarUrl: profile.avatarUrl })
    msg.value = '资料已保存'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '保存失败'
  } finally {
    loadingProfile.value = false
  }
}

const savePassword = async () => {
  loadingPwd.value = true
  msg.value = ''
  error.value = ''
  try {
    await changePassword(passwordForm)
    msg.value = '密码已更新'
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '修改密码失败'
  } finally {
    loadingPwd.value = false
  }
}

const logout = () => {
  clearAuthToken()
  router.replace('/login')
}

const goChat = () => router.push('/chat')

onMounted(async () => {
  try {
    await loadProfile()
  } catch {
    clearAuthToken()
    router.replace('/login')
  }
})
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  padding: 22px 0 28px;
}

.profile-shell {
  display: grid;
  gap: 14px;
}

.profile-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 20px 22px;
}

.profile-eyebrow {
  margin: 0 0 8px;
  color: var(--ink-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: 11px;
}

h1 {
  margin: 0;
  font-size: clamp(1.5rem, 2.6vw, 2rem);
}

.profile-subtitle {
  margin: 10px 0 0;
  color: var(--ink-soft);
  max-width: 620px;
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(0, 1fr);
  gap: 14px;
}

.profile-card {
  padding: 20px;
  min-width: 0;
}

.card-title-wrap {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

h2 {
  margin: 0;
  font-size: 1.1rem;
}

.card-badge {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #9fd9ff;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.03);
  margin-bottom: 14px;
}

.avatar-preview {
  width: 72px;
  height: 72px;
  border-radius: 999px;
  border: 1px solid rgba(143, 191, 255, 0.32);
  background: linear-gradient(145deg, rgba(39, 74, 125, 0.6), rgba(14, 27, 49, 0.6));
  display: grid;
  place-items: center;
  font-weight: 700;
  color: #d8eeff;
  flex-shrink: 0;
  overflow: hidden;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.avatar-input {
  display: none;
}

.avatar-tip {
  width: 100%;
  margin: 2px 0 0;
  color: var(--ink-muted);
  font-size: 12px;
}

.profile-tip {
  margin: 0 0 12px;
  color: var(--ink-soft);
}

.profile-form {
  display: grid;
  gap: 10px;
}

.profile-form label {
  display: grid;
  gap: 6px;
}

.profile-form label span {
  font-size: 12px;
  color: var(--ink-muted);
}

input {
  height: 42px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.06);
  color: var(--ink);
  padding: 0 12px;
}

input:focus {
  outline: none;
  border-color: var(--cyan);
}

.profile-form--pwd {
  margin-bottom: 12px;
}

.profile-msg {
  color: #8af1b6;
  margin: 4px 2px 0;
}

.profile-error {
  color: #ff8ea1;
  margin: 4px 2px 0;
}

.profile-logout {
  margin-top: 4px;
}

@media (max-width: 900px) {
  .profile-hero {
    flex-direction: column;
  }

  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
