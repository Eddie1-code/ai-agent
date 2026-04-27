<template>
  <div class="chat-page">
    <header class="section-shell chat-topbar panel">
      <button class="btn-pill nav-back" @click="goBack" aria-label="返回首页">
        <span class="nav-back__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path d="M11 6L5 12L11 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M6 12H19" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </span>
        <span>返回</span>
      </button>
      <p class="eyebrow">Issue Console / Live Mentor</p>
      <div class="switchers">
        <button :class="['btn-pill', { active: mode === 'coach' }]" @click="switchMode('coach')">{{ brandCopy.chat.modeCoach }}</button>
        <button :class="['btn-pill', { active: mode === 'planner' }]" @click="switchMode('planner')">{{ brandCopy.chat.modePlanner }}</button>
      </div>
    </header>

    <main class="section-shell chat-main">
      <section class="panel chat-shell">
        <div class="chat-shell__head">
          <h1>{{ brandCopy.chat.title }}</h1>
          <span class="impact-word">TALK!</span>
        </div>
        <ChatPanel
          :messages="messages"
          :connection-status="connectionStatus"
          :can-stop="connectionStatus === 'connecting'"
          :input-placeholder="brandCopy.chat.inputPlaceholder"
          :stop-text="brandCopy.chat.stopText"
          @send-message="sendMessage"
          @stop-message="stopMessage"
        />
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatPanel from '../components/ChatPanel.vue'
import { streamMentorChat, stopMentorChat } from '../api'
import { brandCopy } from '../constants/copy'

useHead({
  title: 'AI生活导师 - 会话控制台',
  meta: [{ name: 'description', content: '进入个性化AI生活导师会话，获取可执行的下一步建议。' }]
})

const router = useRouter()
const route = useRoute()
const messages = ref([])
const connectionStatus = ref('disconnected')
let eventSource = null
const currentRequestId = ref('')
const mode = ref(route.query.mode === 'planner' ? 'planner' : 'coach')

const goBack = () => router.push('/')

const sendMessage = (message) => {
  messages.value.push({ content: message, isUser: true, time: new Date().toISOString() })
  if (eventSource) eventSource.close()
  connectionStatus.value = 'connecting'

  const stream = streamMentorChat(
    { message, chatId: 'web-session', mode: mode.value },
    (raw) => {
      let payload
      try {
        payload = JSON.parse(raw)
      } catch {
        payload = { eventType: 'answer', content: raw, done: false }
      }
      if (payload.done && payload.eventType === 'done') {
        connectionStatus.value = 'disconnected'
        return
      }
      if (payload.eventType === 'meta') return
      const renderType = payload.eventType === 'thinking' ? 'thinking' : payload.eventType
      messages.value.push({ content: payload.content || '', isUser: false, type: renderType, time: new Date().toISOString() })
    },
    () => {
      connectionStatus.value = 'disconnected'
      messages.value.push({ content: '连接中断了，我们继续。', isUser: false, type: 'error', time: new Date().toISOString() })
    }
  )
  currentRequestId.value = stream.requestId
  eventSource = stream.eventSource
}

const stopMessage = async () => {
  if (!currentRequestId.value) return
  await stopMentorChat(currentRequestId.value)
  connectionStatus.value = 'disconnected'
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  messages.value.push({ content: brandCopy.chat.stopDone, isUser: false, type: 'system', time: new Date().toISOString() })
}

const switchMode = (nextMode) => {
  mode.value = nextMode
  router.replace({ path: '/chat', query: { mode: nextMode } })
}

onMounted(() => {
  messages.value.push({ content: brandCopy.chat.welcome, isUser: false, time: new Date().toISOString() })
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})
</script>

<style scoped>
.chat-page {
  min-height: 100vh;
  padding-top: 10px;
  position: relative;
  z-index: 1;
}

.chat-topbar {
  position: sticky;
  top: 8px;
  z-index: 30;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 18px;
}

.nav-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding-left: 10px;
  border-color: rgba(143, 191, 255, 0.32);
  background:
    radial-gradient(circle at 10% 20%, rgba(84, 138, 255, 0.2), transparent 46%),
    linear-gradient(140deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.02));
  color: #e9f3ff;
}

.nav-back__icon {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: 1px solid rgba(162, 204, 255, 0.42);
  display: grid;
  place-items: center;
  background: rgba(16, 38, 68, 0.5);
  box-shadow: inset 0 0 14px rgba(112, 171, 255, 0.22);
}

.nav-back__icon svg {
  width: 14px;
  height: 14px;
}

.nav-back:hover {
  transform: translateY(-1px);
  border-color: rgba(143, 191, 255, 0.48);
  box-shadow: 0 8px 24px rgba(16, 59, 122, 0.24);
}

.switchers {
  display: flex;
  gap: 8px;
}

.switchers .active {
  background: linear-gradient(130deg, #2f9cff, #48f5ff);
  color: #fff;
}

.chat-main {
  padding-bottom: 22px;
}

.chat-shell {
  padding: 20px;
}

.chat-shell__head {
  position: relative;
  margin-bottom: 10px;
  min-height: 54px;
}

h1 {
  font-size: clamp(1.6rem, 3.3vw, 2.5rem);
}

@media (max-width: 760px) {
  .chat-topbar {
    flex-direction: column;
    align-items: flex-start;
  }
  .switchers {
    width: 100%;
  }
  .switchers .btn-pill {
    flex: 1;
    text-align: center;
  }
}
</style>
