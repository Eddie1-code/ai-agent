<template>
  <div class="chat-panel">
    <div class="messages" ref="messagesContainer">
      <div v-for="(msg, index) in displayMessages" :key="index" class="row">
        <div v-if="!msg.isUser" class="bubble-wrap bubble-wrap--ai" :class="[msg.type]">
          <span class="avatar">AI</span>
          <div class="bubble">
            <div v-if="msg.type === 'thinking'" class="thinking-block">
              <details>
                <summary>AI思考过程</summary>
                <div class="rich-content" v-html="renderAiMessage(msg.content)"></div>
              </details>
            </div>
            <template v-else>
              <div v-if="typeTitle(msg.type)" class="bubble-type">{{ typeTitle(msg.type) }}</div>
              <div v-if="msg.type === 'tool_call'" class="tool-card">
                <div class="tool-card__title">调用工具：{{ msg.toolName || '未识别工具' }}</div>
                <div v-if="msg.toolArgs" class="tool-card__summary">{{ summarizeToolText(msg.toolArgs) }}</div>
                <details v-if="msg.toolArgs" class="tool-card__details">
                  <summary>查看参数原文</summary>
                  <div class="rich-content" v-html="renderAiMessage(msg.toolArgs)"></div>
                </details>
              </div>
              <div v-else-if="msg.type === 'tool_result'" class="tool-card tool-card--result">
                <div class="tool-card__title">{{ msg.toolName ? `工具 ${msg.toolName} 执行结果` : '工具执行结果' }}</div>
                <div v-if="msg.images && msg.images.length" class="tool-card__badge">自动配图</div>
                <div class="tool-card__summary">{{ summarizeToolText(msg.toolResult || msg.content) }}</div>
                <details class="tool-card__details">
                  <summary>查看结果原文</summary>
                  <div class="rich-content" v-html="renderAiMessage(msg.toolResult || msg.content)"></div>
                </details>
              </div>
              <div v-else class="rich-content" v-html="renderAiMessage(sanitizeAssistantMessage(msg.content))"></div>
              <div v-if="msg.images && msg.images.length" class="image-list">
                <a
                  v-for="(img, i) in msg.images"
                  :key="i"
                  :href="resolveImageUrl(img)"
                  target="_blank"
                  rel="noreferrer"
                  class="image-link"
                >
                  <img :src="resolveImageUrl(img)" :alt="`生成图片${i + 1}`" />
                  <span>查看原图</span>
                </a>
              </div>
            </template>
            <p v-if="connectionStatus === 'connecting' && index === displayMessages.length - 1">
              <span class="typing">▋</span>
            </p>
            <small>{{ formatTime(msg.time) }}</small>
          </div>
        </div>
        <div v-else class="bubble-wrap bubble-wrap--user">
          <div class="bubble">
            <p>{{ msg.content }}</p>
            <small>{{ formatTime(msg.time) }}</small>
          </div>
          <span class="avatar">ME</span>
        </div>
      </div>
    </div>

    <div v-if="isImageGenerating" class="image-progress-card" role="status" aria-live="polite">
      <div class="image-progress-card__text">正在生成图片，请稍候...</div>
      <div class="image-progress-track">
        <span class="image-progress-bar"></span>
      </div>
    </div>

    <div class="composer">
      <textarea
        v-model="inputMessage"
        @keydown.enter.prevent="sendMessage"
        :placeholder="inputPlaceholder"
        :disabled="connectionStatus === 'connecting'"
      />
      <button class="btn-pill btn-pill--primary" :disabled="connectionStatus === 'connecting' || !inputMessage.trim()" @click="sendMessage">发送</button>
      <button v-if="canStop" class="btn-pill" @click="emit('stop-message')">{{ stopText }}</button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, nextTick, watch } from 'vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  connectionStatus: { type: String, default: 'disconnected' },
  canStop: { type: Boolean, default: false },
  inputPlaceholder: { type: String, default: '请输入你的问题...' },
  stopText: { type: String, default: '停止' }
})

const emit = defineEmits(['send-message', 'stop-message'])
const inputMessage = ref('')
const messagesContainer = ref(null)
const IMAGE_URL_FALLBACK_HINT = '当前无法提供可验证的外链图片URL，请改为本地上传或让我输出拍摄/配图提示词'

const displayMessages = computed(() => {
  const grouped = []
  for (const msg of props.messages) {
    if (!msg.isUser && msg.type === 'thinking' && grouped.length > 0) {
      const last = grouped[grouped.length - 1]
      if (!last.isUser && last.type === 'thinking') {
        last.content += `\n${msg.content}`
        last.time = msg.time
        continue
      }
    }
    grouped.push({ ...msg })
  }
  return grouped
})

const sendMessage = () => {
  if (!inputMessage.value.trim()) return
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

const formatTime = (timestamp) => new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const typeTitle = (type) => {
  if (type === 'tool_call') return '工具调用'
  if (type === 'tool_result') return '工具结果'
  if (type === 'error') return '错误'
  if (type === 'cancelled') return '已停止'
  return ''
}

const summarizeToolText = (text = '') => {
  const normalized = String(text).replace(/\s+/g, ' ').trim()
  if (!normalized) return '无可展示结果'
  if (normalized.length <= 120) return normalized
  return `${normalized.slice(0, 120)} ...`
}

const resolveImageUrl = (url = '') => {
  if (!url) return ''
  if (url.startsWith('//')) return `https:${url}`
  if (/^https?:\/\//i.test(url)) return url
  if (url.startsWith('/api/')) {
    const base = import.meta.env.PROD ? '' : 'http://localhost:8123'
    return `${base}${url}`
  }
  return url
}

const escapeHtml = (text = '') =>
  text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')

const renderAiMessage = (content = '') => {
  const normalized = normalizeStructuredText(content)
  const safe = escapeHtml(normalized)
  const lines = safe.split(/\r?\n/)
  let html = ''

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) {
      html += '<div class="line line-empty"></div>'
      continue
    }

    let withBold = line.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    withBold = withBold.replace(/^step\s+(\d+)\s*:\s*/i, '第$1步：')
    if (/^###\s+/.test(withBold)) {
      html += `<h4 class="md-h3">${withBold.replace(/^###\s+/, '')}</h4>`
      continue
    }
    if (/^##\s+/.test(withBold)) {
      html += `<h3 class="md-h2">${withBold.replace(/^##\s+/, '')}</h3>`
      continue
    }
    if (/^#\s+/.test(withBold)) {
      html += `<h2 class="md-h1">${withBold.replace(/^#\s+/, '')}</h2>`
      continue
    }
    const stepMatched = withBold.match(/^(?:\d+[\.、\)]\s*)?第\s*[0-9一二三四五六七八九十百]+\s*步[：:]\s*(.+)$/)
    if (stepMatched) {
      html += `<h4 class="step-title">第${withBold.match(/第\s*[0-9一二三四五六七八九十百]+\s*步/)?.[0]?.replace(/\s+/g, '')?.replace('第', '').replace('步', '') || ''}步：${stepMatched[1]}</h4>`
      continue
    }

    if (/^[-*•]\s+/.test(withBold)) {
      html += `<div class="line line-bullet">• ${withBold.replace(/^[-*•]\s+/, '')}</div>`
      continue
    }
    if (/^\d+[\.、]\s+/.test(withBold)) {
      html += `<div class="line line-ordered">${withBold}</div>`
      continue
    }
    html += `<div class="line">${withBold}</div>`
  }
  return html
}

const isImageToolCall = (msg = {}) => {
  const raw = `${msg.toolName || ''} ${msg.toolArgs || ''} ${msg.content || ''}`.toLowerCase()
  return raw.includes('generateimage')
    || raw.includes('tencent-aiart')
    || raw.includes('图片生成')
    || raw.includes('生图')
}

const isImageGenerating = computed(() => {
  if (props.connectionStatus !== 'connecting') return false
  for (let i = displayMessages.value.length - 1; i >= 0; i -= 1) {
    const msg = displayMessages.value[i]
    if (msg?.isUser) continue
    if (msg?.images?.length) return false
    if (msg?.type === 'tool_result') return false
    if (msg?.type === 'tool_call' && isImageToolCall(msg)) return true
  }
  return false
})

const sanitizeAssistantMessage = (content = '') => {
  if (!isImageGenerating.value) return content || ''
  return String(content || '').replaceAll(IMAGE_URL_FALLBACK_HINT, '图片生成中，正在回传结果...')
}

const isStepTitle = (line = '') =>
  /^(?:\d+[\.、\)]\s*)?第\s*[0-9一二三四五六七八九十百]+\s*步[：:]/.test(line) ||
  /^step\s+\d+\s*:/i.test(line)

const isBulletLine = (line = '') =>
  /^[-*•]\s+/.test(line) || /^\d+[\.、\)]\s+/.test(line)

const isMarkdownHeading = (line = '') =>
  /^#{1,3}\s+/.test(line)

const isLikelyTitle = (line = '') =>
  line.length <= 20 && /[：:]$/.test(line) && !isBulletLine(line)

const endsWithStrongPunctuation = (line = '') =>
  /[。！？!?；;：:]$/.test(line)

const normalizeStructuredText = (content = '') => {
  const rawLines = String(content).replace(/\r\n/g, '\n').split('\n')
  const merged = []

  for (const raw of rawLines) {
    const line = raw.trim()
    if (!line) {
      if (merged.length > 0 && merged[merged.length - 1] !== '') merged.push('')
      continue
    }

    if (isMarkdownHeading(line) || isStepTitle(line) || isBulletLine(line) || isLikelyTitle(line)) {
      merged.push(line)
      continue
    }

    const lastIndex = merged.length - 1
    if (lastIndex >= 0) {
      const prev = merged[lastIndex]
      if (
        prev &&
        !isMarkdownHeading(prev) &&
        !isStepTitle(prev) &&
        !isBulletLine(prev) &&
        !isLikelyTitle(prev) &&
        (!endsWithStrongPunctuation(prev) || line.length <= 16 || prev.length <= 16)
      ) {
        merged[lastIndex] = `${prev}${line}`
        continue
      }
    }
    merged.push(line)
  }

  return merged.join('\n').replace(/\n{3,}/g, '\n\n')
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
}

watch(() => displayMessages.value.length, scrollToBottom)
watch(() => displayMessages.value.map((m) => m.content).join(''), scrollToBottom)
onMounted(scrollToBottom)
</script>

<style scoped>
.chat-panel {
  border: 1px solid var(--line-soft);
  border-radius: 24px;
  background: var(--panel-strong);
  overflow: hidden;
}

.messages {
  min-height: 56vh;
  max-height: 62vh;
  overflow: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bubble-wrap {
  display: flex;
  align-items: flex-start;
  max-width: 84%;
  min-width: 0;
}

.bubble-wrap--user {
  margin-left: auto;
  flex-direction: row-reverse;
  min-width: 0;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid var(--line-soft);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0 8px;
  font-size: 11px;
}

.bubble {
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.06);
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.bubble-wrap--user .bubble {
  background: rgba(239, 46, 53, 0.2);
}

.bubble-wrap.thinking .bubble { border-left: 3px solid var(--cyan); }
.bubble-wrap.tool_call .bubble { border-left: 3px solid #34d399; background: rgba(52, 211, 153, 0.12); }
.bubble-wrap.tool_result .bubble { border-left: 3px solid #22c55e; background: rgba(34, 197, 94, 0.12); }
.bubble-wrap.error .bubble { border-left: 3px solid var(--red); }
.bubble-wrap.system .bubble { border-left: 3px solid var(--yellow); }
.bubble-wrap.cancelled .bubble { border-left: 3px solid #60a5fa; background: rgba(96, 165, 250, 0.12); }

p {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.rich-content {
  color: var(--ink);
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.rich-content :deep(.line) {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.7;
  margin: 0 0 6px;
}

.rich-content :deep(.line-empty) {
  height: 6px;
  margin: 0;
}

.rich-content :deep(.line-bullet),
.rich-content :deep(.line-ordered) {
  padding-left: 2px;
}

.rich-content :deep(strong) {
  font-weight: 700;
  color: #f8fbff;
}

.rich-content :deep(.step-title) {
  margin: 8px 0 6px;
  font-size: 1.12rem;
  line-height: 1.45;
  font-weight: 800;
  color: #8fd8ff;
}

.rich-content :deep(.md-h1) {
  margin: 10px 0 8px;
  font-size: 1.18rem;
  line-height: 1.45;
  font-weight: 800;
  color: #f5fbff;
}

.rich-content :deep(.md-h2) {
  margin: 10px 0 6px;
  font-size: 1.08rem;
  line-height: 1.45;
  font-weight: 800;
  color: #9fddff;
}

.rich-content :deep(.md-h3) {
  margin: 8px 0 6px;
  font-size: 1rem;
  line-height: 1.45;
  font-weight: 700;
  color: #c8e8ff;
}

.bubble-type {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  margin-bottom: 8px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.08);
}

.tool-meta {
  font-size: 12px;
  color: #98f1c1;
  margin-bottom: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.tool-card {
  border: 1px solid rgba(95, 219, 183, 0.35);
  background: rgba(24, 74, 62, 0.25);
  border-radius: 10px;
  padding: 8px 10px;
}

.tool-card--result {
  border-color: rgba(111, 187, 255, 0.35);
  background: rgba(28, 58, 92, 0.25);
}

.tool-card__title {
  font-size: 12px;
  font-weight: 700;
  color: #ccf8e8;
  margin-bottom: 6px;
}

.tool-card--result .tool-card__title {
  color: #cce8ff;
}

.tool-card__summary {
  font-size: 12px;
  line-height: 1.6;
  color: #d9f5eb;
  margin-bottom: 6px;
}

.tool-card__badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  border-radius: 999px;
  padding: 0 8px;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 700;
  color: #e7f5ff;
  background: rgba(64, 171, 255, 0.25);
  border: 1px solid rgba(64, 171, 255, 0.45);
}

.tool-card--result .tool-card__summary {
  color: #deefff;
}

.tool-card__details summary {
  cursor: pointer;
  font-size: 12px;
  color: #8fd8ff;
}

.image-list {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.image-link {
  width: 124px;
  border-radius: 10px;
  border: 1px solid rgba(143, 216, 255, 0.35);
  background: rgba(14, 38, 62, 0.38);
  overflow: hidden;
  color: #8fd8ff;
  font-size: 12px;
  display: block;
}

.image-link img {
  width: 100%;
  height: 92px;
  object-fit: cover;
  display: block;
}

.image-link span {
  display: block;
  text-align: center;
  padding: 4px 2px;
}

.thinking-block details {
  border: 1px dashed rgba(143, 191, 255, 0.38);
  border-radius: 10px;
  padding: 6px 10px 2px;
  background: rgba(23, 46, 79, 0.22);
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.thinking-block summary {
  cursor: pointer;
  font-size: 12px;
  color: #8fd8ff;
  font-weight: 700;
  margin-bottom: 6px;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

.thinking-block .rich-content {
  max-height: 220px;
  overflow: auto;
  padding-right: 4px;
}

small {
  display: block;
  margin-top: 6px;
  color: var(--ink-muted);
  font-size: 11px;
}

.typing {
  margin-left: 4px;
  animation: pulse 1.2s infinite;
}

@keyframes pulse {
  0% { opacity: 0.3; }
  50% { opacity: 1; }
  100% { opacity: 0.3; }
}

.image-progress-card {
  margin: 8px 12px 0;
  padding: 10px 12px;
  border: 1px solid rgba(122, 203, 255, 0.36);
  border-radius: 12px;
  background: rgba(20, 41, 66, 0.34);
}

.image-progress-card__text {
  font-size: 12px;
  color: #bfe6ff;
  margin-bottom: 7px;
}

.image-progress-track {
  height: 6px;
  border-radius: 999px;
  background: rgba(163, 210, 255, 0.16);
  overflow: hidden;
}

.image-progress-bar {
  display: block;
  width: 38%;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(71, 157, 255, 0.45), rgba(83, 231, 255, 0.92), rgba(71, 157, 255, 0.45));
  animation: imageProgressSlide 1.15s ease-in-out infinite;
}

@keyframes imageProgressSlide {
  0% { transform: translateX(-115%); }
  100% { transform: translateX(270%); }
}

.composer {
  border-top: 1px solid var(--line-soft);
  padding: 12px;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}


textarea {
  flex: 1;
  min-height: 48px;
  max-height: 120px;
  resize: none;
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--ink);
  padding: 12px;
}

textarea:focus {
  outline: none;
  border-color: var(--cyan);
}

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 760px) {
  .messages { min-height: 60vh; }
  .composer {
    flex-wrap: wrap;
  }
  .composer .btn-pill {
    width: 100%;
  }
}
</style>
