<template>
  <div class="chat-panel">
    <div class="messages" ref="messagesContainer">
      <div v-for="(msg, index) in messages" :key="index" class="row">
        <div v-if="!msg.isUser" class="bubble-wrap bubble-wrap--ai" :class="[msg.type]">
          <span class="avatar">AI</span>
          <div class="bubble">
            <p>
              {{ msg.content }}
              <span v-if="connectionStatus === 'connecting' && index === messages.length - 1" class="typing">▋</span>
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
import { ref, onMounted, nextTick, watch } from 'vue'

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

const sendMessage = () => {
  if (!inputMessage.value.trim()) return
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

const formatTime = (timestamp) => new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
}

watch(() => props.messages.length, scrollToBottom)
watch(() => props.messages.map((m) => m.content).join(''), scrollToBottom)
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
}

.bubble-wrap--user {
  margin-left: auto;
  flex-direction: row-reverse;
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
}

.bubble-wrap--user .bubble {
  background: rgba(239, 46, 53, 0.2);
}

.bubble-wrap.thinking .bubble { border-left: 3px solid var(--cyan); }
.bubble-wrap.error .bubble { border-left: 3px solid var(--red); }
.bubble-wrap.system .bubble { border-left: 3px solid var(--yellow); }

p {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
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
