<template>
  <div class="chat-page">
    <div class="chat-layout">
      <ChatSessionSidebar
        :sessions="sessions"
        :active-session-id="activeSessionId"
        :streaming-session-id="streamingSessionId"
        :is-streaming="isStreaming"
        :user-avatar-url="userAvatarUrl"
        :user-display-name="userDisplayName"
        :sidebar-mode="sidebarMode"
        @create="createSession"
        @select="selectSession"
        @rename="renameSession"
        @pin="pinSession"
        @share="shareSession"
        @delete="deleteSession"
        @collapse="collapseSidebar"
        @expand="expandSidebar"
      />

      <div class="chat-content">
        <header class="chat-topbar glass-panel">
          <div class="chat-topbar__left">
            <button
              v-if="sidebarMode === 'hidden'"
              class="btn-pill sidebar-expand-btn"
              type="button"
              @click="expandSidebar"
              :aria-label="brandCopy.chat.sidebarShow"
            >
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M9 6L15 12L9 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <span>{{ brandCopy.chat.sidebarShow }}</span>
            </button>
            <button class="btn-pill nav-back" @click="goBack" aria-label="返回首页">
            <span class="nav-back__icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M11 6L5 12L11 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                <path d="M6 12H19" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </span>
            <span>返回</span>
          </button>
          </div>
          <div class="mode-switcher" role="tablist" aria-label="对话模式">
            <button
              type="button"
              role="tab"
              :aria-selected="mode === 'coach'"
              :class="['mode-switcher__btn', { active: mode === 'coach' }]"
              @click="switchMode('coach')"
            >
              <span class="mode-switcher__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none">
                  <path d="M12 3L14.2 8.8L20 11L14.2 13.2L12 19L9.8 13.2L4 11L9.8 8.8L12 3Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
                </svg>
              </span>
              <span>{{ brandCopy.chat.modeCoach }}</span>
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="mode === 'planner'"
              :class="['mode-switcher__btn', { active: mode === 'planner' }]"
              @click="switchMode('planner')"
            >
              <span class="mode-switcher__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none">
                  <path d="M9 5H7.5A2.5 2.5 0 0 0 5 7.5v9A2.5 2.5 0 0 0 7.5 19h9a2.5 2.5 0 0 0 2.5-2.5V15" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                  <path d="M9 5h6v4H9V5Zm0 6h6v4H9v-4Zm10-1.5V5h-4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <span>{{ brandCopy.chat.modePlanner }}</span>
            </button>
          </div>

          <button class="btn-pill export-btn" type="button" @click="exportLatestPlan">导出最近计划PDF</button>
        </header>

        <main class="chat-main glass-scrollbar">
          <div class="chat-center">
            <p v-if="toastMessage" class="session-toast glass-panel">{{ toastMessage }}</p>
            <p v-if="isBackgroundStreaming" class="stream-hint glass-panel">{{ brandCopy.chat.streamBackgroundHint }}</p>
            <ChatPanel
              :messages="messages"
              :connection-status="activeConnectionStatus"
              :input-disabled="isStreaming"
              :can-stop="canStopActiveStream"
              :input-placeholder="inputPlaceholder"
              :stop-text="brandCopy.chat.stopText"
              :user-avatar-url="userAvatarUrl"
              :user-display-name="userDisplayName"
              :show-empty-state="showEmptyState"
              :empty-state-title="brandCopy.chat.emptyStateTitle"
              :empty-state-hint="brandCopy.chat.emptyStateHint"
              @send-message="sendMessage"
              @stop-message="stopMessage"
            />
          </div>
        </main>
      </div>
    </div>
    <GlassDialog />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatPanel from '../components/ChatPanel.vue'
import ChatSessionSidebar from '../components/ChatSessionSidebar.vue'
import GlassDialog from '../components/GlassDialog.vue'
import { openGlassConfirm, openGlassPrompt } from '../composables/useGlassDialog'
import {
  streamMentorChat,
  stopMentorChat,
  listChatSessions,
  createChatSession,
  updateChatSession,
  deleteChatSession,
  listSessionMessages,
  exportLatestPlanPdf,
  downloadExportPdf,
  getProfile
} from '../api'
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
const streamingSessionId = ref('')
const mode = ref(route.query.mode === 'planner' ? 'planner' : 'coach')
const activeStreamType = ref('')
const activeStreamMsgIndex = ref(-1)
const streamEndedGracefully = ref(false)
const sessions = ref([])
const activeSessionId = ref('')
const userAvatarUrl = ref('')
const userDisplayName = ref('ME')
const toastMessage = ref('')
const sessionMessageCache = new Map()
let toastTimer = null

const SIDEBAR_MODE_KEY = 'chat-sidebar-mode'
const VALID_SIDEBAR_MODES = ['expanded', 'rail', 'hidden']
const savedSidebarMode = localStorage.getItem(SIDEBAR_MODE_KEY)
const sidebarMode = ref(VALID_SIDEBAR_MODES.includes(savedSidebarMode) ? savedSidebarMode : 'expanded')

const persistSidebarMode = () => {
  localStorage.setItem(SIDEBAR_MODE_KEY, sidebarMode.value)
}

const collapseSidebar = () => {
  if (sidebarMode.value === 'expanded') {
    sidebarMode.value = 'rail'
  } else if (sidebarMode.value === 'rail') {
    sidebarMode.value = 'hidden'
  }
  persistSidebarMode()
}

const expandSidebar = () => {
  sidebarMode.value = 'expanded'
  persistSidebarMode()
}

const isStreaming = computed(() => connectionStatus.value === 'connecting')
const isViewingStreamingSession = computed(
  () => isStreaming.value && activeSessionId.value === streamingSessionId.value
)
const isBackgroundStreaming = computed(
  () => isStreaming.value && activeSessionId.value !== streamingSessionId.value
)
const activeConnectionStatus = computed(() => (isViewingStreamingSession.value ? 'connecting' : 'disconnected'))
const canStopActiveStream = computed(() => isViewingStreamingSession.value)
const inputPlaceholder = computed(() =>
  isBackgroundStreaming.value ? brandCopy.chat.streamBackgroundHint : brandCopy.chat.inputPlaceholder
)
const showEmptyState = computed(() => !messages.value.some(item => item.isUser))

const goBack = () => router.push('/')

const showToast = (text) => {
  toastMessage.value = text
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
  }, 2600)
}

const sortSessions = (items) =>
  [...items].sort((a, b) => {
    const pinDiff = Number(Boolean(b.pinned)) - Number(Boolean(a.pinned))
    if (pinDiff !== 0) return pinDiff
    const aTime = new Date(a.updatedAt || a.createdAt || 0).getTime()
    const bTime = new Date(b.updatedAt || b.createdAt || 0).getTime()
    return bTime - aTime
  })

const isSessionEmpty = (sessionId) => {
  const msgs = getSessionMessages(sessionId)
  return !msgs.some(item => item.isUser)
}

const findReusableEmptySession = async () => {
  for (const session of sessions.value) {
    if (session.title && session.title !== '新会话') continue
    if (!sessionMessageCache.has(session.id)) {
      await loadMessages(session.id)
    }
    if (isSessionEmpty(session.id)) return session
  }
  return null
}

const getSessionMessages = (sessionId) => {
  if (!sessionMessageCache.has(sessionId)) {
    sessionMessageCache.set(sessionId, [])
  }
  return sessionMessageCache.get(sessionId)
}

const syncActiveMessages = (sessionId) => {
  if (activeSessionId.value === sessionId) {
    messages.value = [...getSessionMessages(sessionId)]
  }
}

const setSessionMessages = (sessionId, nextMessages) => {
  sessionMessageCache.set(sessionId, nextMessages)
  syncActiveMessages(sessionId)
}

const appendStreamMessage = (type, content, payload = {}) => {
  const sessionId = streamingSessionId.value
  if (!sessionId) return

  const now = new Date().toISOString()
  const text = content || ''
  const cached = [...getSessionMessages(sessionId)]
  const index = activeStreamMsgIndex.value
  const canMerge =
    index >= 0 &&
    index < cached.length &&
    !cached[index].isUser &&
    cached[index].type === type

  if (canMerge) {
    cached[index] = {
      ...cached[index],
      content: `${cached[index].content}${text}`,
      toolName: payload.toolName || cached[index].toolName,
      toolArgs: payload.toolArgs || cached[index].toolArgs,
      toolResult: payload.toolResult || cached[index].toolResult,
      images: payload.images || cached[index].images || [],
      time: now
    }
    setSessionMessages(sessionId, cached)
    return
  }

  cached.push({
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
  activeStreamMsgIndex.value = cached.length - 1
  setSessionMessages(sessionId, cached)
}

const resetStreamState = () => {
  connectionStatus.value = 'disconnected'
  streamEndedGracefully.value = true
  streamingSessionId.value = ''
  activeStreamType.value = ''
  activeStreamMsgIndex.value = -1
  currentRequestId.value = ''
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

const finalizeStreamForSession = (sessionId, { appendMessage } = {}) => {
  if (!sessionId) return
  resetStreamState()
  if (appendMessage) {
    const cached = [...getSessionMessages(sessionId)]
    cached.push(appendMessage)
    setSessionMessages(sessionId, cached)
  }
}

const sendMessage = (message) => {
  if (!activeSessionId.value || connectionStatus.value === 'connecting') return
  const normalizedMessage = mode.value === 'planner' && ['需要', '继续', '好的', '好', '是的'].includes((message || '').trim())
    ? `请继续基于当前会话上下文输出下一步可执行计划。用户补充：${message}`
    : message
  const session = sessions.value.find(item => item.id === activeSessionId.value)
  if (session && (!session.title || session.title === '新会话')) {
    session.title = normalizedMessage.length <= 16 ? normalizedMessage : normalizedMessage.slice(0, 16)
    sessions.value = sortSessions(sessions.value)
  }
  const cached = [...getSessionMessages(activeSessionId.value)]
  cached.push({ content: message, isUser: true, time: new Date().toISOString() })
  setSessionMessages(activeSessionId.value, cached)

  if (eventSource) eventSource.close()
  connectionStatus.value = 'connecting'
  activeStreamType.value = ''
  activeStreamMsgIndex.value = -1
  streamEndedGracefully.value = false
  streamingSessionId.value = activeSessionId.value

  const stream = streamMentorChat(
    { message: normalizedMessage, chatId: activeSessionId.value, mode: mode.value },
    (raw) => {
      const streamSessionId = streamingSessionId.value
      if (!streamSessionId) return
      let payload
      try {
        payload = JSON.parse(raw)
      } catch {
        payload = { eventType: 'answer', content: raw, done: false }
      }
      if (payload.done && payload.eventType === 'done') {
        finalizeStreamForSession(streamSessionId)
        return
      }
      if (payload.done && payload.eventType === 'cancelled') {
        finalizeStreamForSession(streamSessionId, {
          appendMessage: {
            content: payload.content || brandCopy.chat.streamInterrupted,
            isUser: false,
            type: 'cancelled',
            time: new Date().toISOString()
          }
        })
        return
      }
      if (payload.eventType === 'meta') return
      const renderType = payload.eventType || 'answer'
      if (activeStreamType.value !== renderType) {
        activeStreamType.value = renderType
        activeStreamMsgIndex.value = -1
      }
      appendStreamMessage(renderType, payload.content || '', payload)
    },
    () => {
      if (streamEndedGracefully.value) return
      const streamSessionId = streamingSessionId.value
      if (!streamSessionId) return
      finalizeStreamForSession(streamSessionId, {
        appendMessage: {
          content: brandCopy.chat.streamDisconnected,
          isUser: false,
          type: 'error',
          time: new Date().toISOString()
        }
      })
    }
  )
  currentRequestId.value = stream.requestId
  eventSource = stream.eventSource
}

const stopMessage = async () => {
  if (!currentRequestId.value || !streamingSessionId.value) return
  const streamSessionId = streamingSessionId.value
  streamEndedGracefully.value = true
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  await stopMentorChat(currentRequestId.value)
  finalizeStreamForSession(streamSessionId, {
    appendMessage: {
      content: brandCopy.chat.streamInterrupted,
      isUser: false,
      type: 'system',
      time: new Date().toISOString()
    }
  })
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
  if (connectionStatus.value === 'connecting' && streamingSessionId.value === sessionId) {
    syncActiveMessages(sessionId)
    return
  }
  const res = await listSessionMessages(sessionId, 100)
  const loaded = ((res && res.data) || []).map(normalizeMessage)
  setSessionMessages(sessionId, loaded)
}

const loadSessions = async () => {
  try {
    const res = await listChatSessions()
    sessions.value = sortSessions((res && res.data) || [])
    if (!sessions.value.length) {
      await createSession(true)
      return
    }
    if (!activeSessionId.value) {
      const reusable = await findReusableEmptySession()
      activeSessionId.value = reusable?.id || sessions.value[0].id
      if (sessionMessageCache.has(activeSessionId.value)) {
        syncActiveMessages(activeSessionId.value)
      } else {
        await loadMessages(activeSessionId.value)
      }
    }
  } catch {
    sessions.value = []
    try {
      await createSession(true)
    } catch {
      /* 后端不可用或仍 401 时避免 mounted 抛错导致白屏 */
    }
  }
}

const createSession = async (forceCreate = false) => {
  if (!forceCreate) {
    const reusable = await findReusableEmptySession()
    if (reusable) {
      if (activeSessionId.value !== reusable.id) {
        await selectSession(reusable.id)
      }
      return
    }
  }
  const res = await createChatSession({ title: '新会话', mode: mode.value })
  if (!res || !res.data) return
  sessions.value = sortSessions([res.data, ...sessions.value])
  activeSessionId.value = res.data.id
  const welcome = [{ content: brandCopy.chat.welcome, isUser: false, time: new Date().toISOString() }]
  setSessionMessages(res.data.id, welcome)
}

const selectSession = async (sessionId) => {
  if (activeSessionId.value === sessionId) return
  activeSessionId.value = sessionId
  if (sessionMessageCache.has(sessionId)) {
    syncActiveMessages(sessionId)
    return
  }
  await loadMessages(sessionId)
}

const renameSession = async (sessionId) => {
  const session = sessions.value.find(item => item.id === sessionId)
  if (!session) return
  const nextTitle = await openGlassPrompt({
    title: brandCopy.chat.sessionRenameTitle,
    message: brandCopy.chat.sessionRenamePrompt,
    defaultValue: session.title || '新会话',
    inputPlaceholder: brandCopy.chat.sessionRenamePrompt,
    confirmText: brandCopy.chat.sessionRenameAction,
    cancelText: brandCopy.chat.sessionCancel
  })
  if (nextTitle == null) return
  const trimmed = nextTitle.trim()
  if (!trimmed) return
  try {
    const res = await updateChatSession(sessionId, { title: trimmed })
    if (res?.data) {
      sessions.value = sortSessions(sessions.value.map(item => (item.id === sessionId ? res.data : item)))
    }
  } catch {
    showToast('重命名失败，请稍后重试')
  }
}

const pinSession = async (sessionId, pinned) => {
  try {
    const res = await updateChatSession(sessionId, { pinned })
    if (res?.data) {
      sessions.value = sortSessions(sessions.value.map(item => (item.id === sessionId ? res.data : item)))
    }
  } catch {
    showToast('置顶操作失败，请稍后重试')
  }
}

const buildShareText = (session, sessionMessages) => {
  const lines = [`# ${session.title || '新会话'}`, '']
  for (const msg of sessionMessages) {
    if (msg.type === 'thinking' || msg.type === 'tool_call' || msg.type === 'tool_result') continue
    const role = msg.isUser ? '用户' : 'AI导师'
    lines.push(`## ${role}`)
    lines.push(msg.content || '')
    lines.push('')
  }
  return lines.join('\n').trim()
}

const shareSession = async (sessionId) => {
  const session = sessions.value.find(item => item.id === sessionId)
  if (!session) return
  try {
    if (!sessionMessageCache.has(sessionId)) {
      await loadMessages(sessionId)
    }
    const shareText = buildShareText(session, getSessionMessages(sessionId))
    await navigator.clipboard.writeText(shareText)
    showToast(brandCopy.chat.sessionShareCopied)
  } catch {
    showToast(brandCopy.chat.sessionShareFailed)
  }
}

const deleteSession = async (sessionId) => {
  const confirmed = await openGlassConfirm({
    title: brandCopy.chat.sessionDeleteTitle,
    message: brandCopy.chat.sessionDeleteConfirm,
    confirmText: brandCopy.chat.sessionDeleteAction,
    cancelText: brandCopy.chat.sessionCancel,
    danger: true,
    dismissOnOverlay: false
  })
  if (!confirmed) return
  if (connectionStatus.value === 'connecting' && streamingSessionId.value === sessionId) {
    await stopMessage()
  }
  try {
    await deleteChatSession(sessionId)
    sessionMessageCache.delete(sessionId)
    const remaining = sessions.value.filter(item => item.id !== sessionId)
    sessions.value = remaining
    if (activeSessionId.value !== sessionId) return
    if (remaining.length) {
      activeSessionId.value = remaining[0].id
      if (sessionMessageCache.has(remaining[0].id)) {
        syncActiveMessages(remaining[0].id)
      } else {
        await loadMessages(remaining[0].id)
      }
      return
    }
    activeSessionId.value = ''
    messages.value = []
    await createSession(true)
  } catch {
    showToast('删除失败，请稍后重试')
  }
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
    const cached = [...getSessionMessages(activeSessionId.value)]
    cached.push({
      content: '导出失败，请确认当前会话中已有 AI 计划回复。',
      isUser: false,
      type: 'error',
      time: new Date().toISOString()
    })
    setSessionMessages(activeSessionId.value, cached)
  }
}

onMounted(async () => {
  try {
    const profileRes = await getProfile()
    const profile = profileRes?.data || profileRes || {}
    userAvatarUrl.value = profile.avatarUrl || ''
    userDisplayName.value = (profile.nickname || profile.username || 'ME').trim()
  } catch {
    userAvatarUrl.value = ''
    userDisplayName.value = 'ME'
  }
  await loadSessions()
  if (activeSessionId.value && !getSessionMessages(activeSessionId.value).length) {
    setSessionMessages(activeSessionId.value, [
      { content: brandCopy.chat.welcome, isUser: false, time: new Date().toISOString() }
    ])
  }
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
  if (toastTimer) clearTimeout(toastTimer)
})
</script>

<style scoped>
.chat-page {
  height: 100vh;
  overflow: hidden;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
}

.chat-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  width: 100%;
}

.chat-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chat-topbar {
  flex-shrink: 0;
  z-index: 20;
  margin: 12px 16px 0;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 16px;
}

.chat-topbar__left {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.sidebar-expand-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border-color: var(--glass-border);
  background: rgba(255, 255, 255, 0.06);
  white-space: nowrap;
  font-size: 0.84rem;
}

.sidebar-expand-btn svg {
  width: 14px;
  height: 14px;
}

.mode-switcher {
  display: flex;
  align-items: stretch;
  gap: 4px;
  max-width: 360px;
  width: 100%;
  margin: 0 auto;
  padding: 4px;
  border-radius: 14px;
  border: 1px solid var(--glass-border);
  background:
    var(--glass-highlight),
    rgba(255, 255, 255, 0.04);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.mode-switcher__btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--ink-muted);
  font-size: 0.86rem;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.mode-switcher__btn:hover:not(.active) {
  color: var(--ink-soft);
  background: rgba(255, 255, 255, 0.05);
}

.mode-switcher__btn.active {
  color: #e8f8ff;
  border-color: rgba(127, 231, 255, 0.42);
  background:
    linear-gradient(135deg, rgba(127, 231, 255, 0.22), rgba(47, 156, 255, 0.14)),
    rgba(255, 255, 255, 0.08);
  box-shadow:
    0 0 18px rgba(127, 231, 255, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.16);
}

.mode-switcher__icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.mode-switcher__icon svg {
  width: 16px;
  height: 16px;
  display: block;
}

.export-btn {
  white-space: nowrap;
  font-size: 0.84rem;
  padding: 10px 14px;
  border-color: var(--glass-border);
  background: rgba(255, 255, 255, 0.04);
}

.nav-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding-left: 10px;
  white-space: nowrap;
  border-color: var(--glass-border);
  background: rgba(255, 255, 255, 0.06);
  color: var(--ink);
}

.nav-back__icon {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: 1px solid var(--glass-border);
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.06);
}

.nav-back__icon svg {
  width: 14px;
  height: 14px;
}

.switchers {
  display: none;
}

@media (max-width: 960px) {
  .chat-layout {
    flex-direction: column;
  }

  .chat-topbar {
    grid-template-columns: 1fr;
    margin: 8px;
    gap: 10px;
  }

  .mode-switcher {
    max-width: none;
    order: 2;
  }

  .export-btn {
    order: 3;
    width: 100%;
    text-align: center;
  }

  .nav-back {
    order: 1;
    width: fit-content;
  }
}

.chat-main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  padding: 12px 8px 16px 16px;
}

.chat-center {
  flex: 1;
  min-height: min-content;
  max-width: 820px;
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  padding-right: 8px;
}

.stream-hint,
.session-toast {
  flex-shrink: 0;
  margin: 0 0 10px;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 0.88rem;
  line-height: 1.5;
}

.stream-hint {
  color: #ffd9a8;
}

.session-toast {
  color: #d7efff;
}
</style>
