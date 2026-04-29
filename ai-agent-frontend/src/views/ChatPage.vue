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
      <div class="session-picker">
        <select class="session-picker__select" v-model="activeSessionId" @change="handleSessionChange">
          <option v-for="item in sessions" :key="item.id" :value="item.id">
            {{ item.title }}
          </option>
        </select>
        <button class="btn-pill" @click="createSession">新建会话</button>
        <button class="btn-pill" @click="exportLatestPlan">导出最近计划PDF</button>
      </div>
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
import { streamMentorChat, stopMentorChat, listChatSessions, createChatSession, listSessionMessages, exportLatestPlanPdf, downloadExportPdf } from '../api'
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
const activeStreamType = ref('')
const activeStreamMsgIndex = ref(-1)
const streamEndedGracefully = ref(false)
const sessions = ref([])
const activeSessionId = ref('')

const goBack = () => router.push('/')

const appendStreamMessage = (type, content, payload = {}) => {
  const now = new Date().toISOString()
  const text = content || ''
  const index = activeStreamMsgIndex.value
  const canMerge =
    index >= 0 &&
    index < messages.value.length &&
    !messages.value[index].isUser &&
    messages.value[index].type === type

  if (canMerge) {
    messages.value[index] = {
      ...messages.value[index],
      content: `${messages.value[index].content}${text}`,
      toolName: payload.toolName || messages.value[index].toolName,
      toolArgs: payload.toolArgs || messages.value[index].toolArgs,
      toolResult: payload.toolResult || messages.value[index].toolResult,
      images: payload.images || messages.value[index].images || [],
      time: now
    }
    return
  }

  messages.value.push({
    content: text,
    isUser: false,
    type,
    toolName: payload.toolName || '',
    toolArgs: payload.toolArgs || '',
    toolResult: payload.toolResult || '',
    images: payload.images || [],
    time: now
  })
  activeStreamType.value = type
  activeStreamMsgIndex.value = messages.value.length - 1
}

const sendMessage = (message) => {
  if (!activeSessionId.value) return
  const normalizedMessage = mode.value === 'planner' && ['需要', '继续', '好的', '好', '是的'].includes((message || '').trim())
    ? `请继续基于当前会话上下文输出下一步可执行计划。用户补充：${message}`
    : message
  const session = sessions.value.find(item => item.id === activeSessionId.value)
  if (session && (!session.title || session.title === '新会话')) {
    session.title = normalizedMessage.length <= 16 ? normalizedMessage : normalizedMessage.slice(0, 16)
    sessions.value = [...sessions.value]
  }
  messages.value.push({ content: message, isUser: true, time: new Date().toISOString() })
  if (eventSource) eventSource.close()
  connectionStatus.value = 'connecting'
  activeStreamType.value = ''
  activeStreamMsgIndex.value = -1
  streamEndedGracefully.value = false

  const stream = streamMentorChat(
    { message: normalizedMessage, chatId: activeSessionId.value, mode: mode.value },
    (raw) => {
      let payload
      try {
        payload = JSON.parse(raw)
      } catch {
        payload = { eventType: 'answer', content: raw, done: false }
      }
      if (payload.done && payload.eventType === 'done') {
        connectionStatus.value = 'disconnected'
        streamEndedGracefully.value = true
        if (eventSource) {
          eventSource.close()
          eventSource = null
        }
        return
      }
      if (payload.done && payload.eventType === 'cancelled') {
        connectionStatus.value = 'disconnected'
        streamEndedGracefully.value = true
        messages.value.push({ content: payload.content || brandCopy.chat.stopDone, isUser: false, type: 'cancelled', time: new Date().toISOString() })
        if (eventSource) {
          eventSource.close()
          eventSource = null
        }
        return
      }
      if (payload.eventType === 'meta') return
      const renderType = payload.eventType || 'answer'
      if (activeStreamType.value !== renderType) {
        activeStreamType.value = renderType
        activeStreamMsgIndex.value = -1
      }
      if (renderType === 'tool_result') {
        activeStreamMsgIndex.value = -1
      }
      appendStreamMessage(renderType, payload.content || '', payload)
    },
    () => {
      if (streamEndedGracefully.value) return
      connectionStatus.value = 'disconnected'
      messages.value.push({ content: '连接中断了，我们继续。', isUser: false, type: 'error', time: new Date().toISOString() })
      activeStreamType.value = ''
      activeStreamMsgIndex.value = -1
    }
  )
  currentRequestId.value = stream.requestId
  eventSource = stream.eventSource
}

const stopMessage = async () => {
  if (!currentRequestId.value) return
  await stopMentorChat(currentRequestId.value)
  connectionStatus.value = 'disconnected'
  streamEndedGracefully.value = true
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

const normalizeMessage = (item) => {
  let images = []
  try {
    if (item.metadataJson) {
      const metadata = JSON.parse(item.metadataJson)
      if (Array.isArray(metadata.images)) {
        images = metadata.images
      }
    }
  } catch {
    images = []
  }
  return {
    content: item.content || '',
    isUser: item.role === 'user',
    type: item.eventType || (item.role === 'assistant' ? 'answer' : 'text'),
    toolName: '',
    toolArgs: '',
    toolResult: '',
    images,
    time: item.createdAt || new Date().toISOString()
  }
}

const loadMessages = async (sessionId) => {
  const res = await listSessionMessages(sessionId, 100)
  messages.value = ((res && res.data) || []).map(normalizeMessage)
}

const loadSessions = async () => {
  const res = await listChatSessions()
  sessions.value = (res && res.data) || []
  if (!sessions.value.length) {
    await createSession()
    return
  }
  if (!activeSessionId.value) {
    activeSessionId.value = sessions.value[0].id
    await loadMessages(activeSessionId.value)
  }
}

const createSession = async () => {
  const res = await createChatSession({ title: '新会话', mode: mode.value })
  if (!res || !res.data) return
  sessions.value = [res.data, ...sessions.value]
  activeSessionId.value = res.data.id
  messages.value = [{ content: brandCopy.chat.welcome, isUser: false, time: new Date().toISOString() }]
}

const handleSessionChange = async () => {
  if (!activeSessionId.value) return
  await loadMessages(activeSessionId.value)
}

const exportLatestPlan = async () => {
  if (!activeSessionId.value) return
  try {
    const res = await exportLatestPlanPdf(activeSessionId.value)
    const exportId = res?.data?.exportId
    if (exportId) {
      const blob = await downloadExportPdf(exportId)
      const objectUrl = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }))
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = `plan-${activeSessionId.value}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(objectUrl)
    }
  } catch {
    messages.value.push({
      content: '导出失败，请确认当前会话中已有 AI 计划回复。',
      isUser: false,
      type: 'error',
      time: new Date().toISOString()
    })
  }
}

onMounted(async () => {
  await loadSessions()
  if (!messages.value.length) {
    messages.value.push({ content: brandCopy.chat.welcome, isUser: false, time: new Date().toISOString() })
  }
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

.session-picker {
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-picker__select {
  min-width: 180px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid rgba(143, 191, 255, 0.32);
  background: rgba(5, 13, 26, 0.7);
  color: #e9f3ff;
  padding: 0 10px;
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
  .session-picker {
    width: 100%;
  }
  .session-picker__select {
    flex: 1;
    min-width: 0;
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
