<template>
  <div class="chat-panel">
    <div class="messages" ref="messagesContainer">
      <div v-if="showEmptyState" class="empty-state">
        <h2 class="empty-state__title">{{ emptyStateTitle }}</h2>
        <p class="empty-state__hint">{{ emptyStateHint }}</p>
      </div>
      <div v-for="(msg, index) in visibleMessages" :key="index" class="row">
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
                <div v-if="isImageToolCall(msg)" class="tool-card__progress">正在检索并整理图片，请稍候...</div>
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
            <p v-if="connectionStatus === 'connecting' && index === visibleMessages.length - 1">
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
          <span class="avatar avatar--user" :title="userDisplayName">
            <img
              v-if="resolvedAvatarUrl"
              :src="resolvedAvatarUrl"
              alt="用户头像"
              @error="avatarLoadFailed = true"
            />
            <template v-else>{{ userInitial }}</template>
          </span>
        </div>
      </div>
    </div>

    <div v-if="isImageGenerating" class="image-progress-card" role="status" aria-live="polite">
      <div class="image-progress-card__text">{{ imageProgressHint }}</div>
      <div class="image-progress-card__subtext">已自动过滤无效图片符号，完成后会直接展示可点击预览。</div>
      <div class="image-progress-track">
        <span class="image-progress-bar"></span>
      </div>
    </div>

    <div class="composer glass-composer">
      <div class="composer__inner">
        <textarea
          v-model="inputMessage"
          @keydown.enter.prevent="sendMessage"
          :placeholder="inputPlaceholder"
          :disabled="connectionStatus === 'connecting' || inputDisabled"
          rows="1"
        />
        <div class="composer__actions">
          <button v-if="canStop" class="composer__stop" type="button" @click="emit('stop-message')">{{ stopText }}</button>
          <button
            class="composer__send"
            type="button"
            :disabled="connectionStatus === 'connecting' || inputDisabled || !inputMessage.trim()"
            @click="sendMessage"
            aria-label="发送"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 19V5M12 5L6 11M12 5L18 11" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, nextTick, watch } from 'vue'
import { isValidAvatarSrc } from '../utils/avatar'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  connectionStatus: { type: String, default: 'disconnected' },
  inputDisabled: { type: Boolean, default: false },
  canStop: { type: Boolean, default: false },
  inputPlaceholder: { type: String, default: '请输入你的问题...' },
  stopText: { type: String, default: '停止' },
  userAvatarUrl: { type: String, default: '' },
  userDisplayName: { type: String, default: 'ME' },
  showEmptyState: { type: Boolean, default: false },
  emptyStateTitle: { type: String, default: '' },
  emptyStateHint: { type: String, default: '' }
})

const emit = defineEmits(['send-message', 'stop-message'])
const inputMessage = ref('')
const messagesContainer = ref(null)
const avatarLoadFailed = ref(false)
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

const visibleMessages = computed(() => (props.showEmptyState ? [] : displayMessages.value))

const userInitial = computed(() => {
  const source = String(props.userDisplayName || 'ME').trim()
  return source ? source.slice(0, 1).toUpperCase() : 'M'
})

const resolvedAvatarUrl = computed(() => {
  if (avatarLoadFailed.value) return ''
  return isValidAvatarSrc(props.userAvatarUrl) ? props.userAvatarUrl : ''
})

watch(() => props.userAvatarUrl, () => {
  avatarLoadFailed.value = false
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

const removeLinkHints = (content = '') =>
  String(content)
    .replaceAll('[图片链接已折叠，见下方预览]', '')
    .replaceAll('【图片直链暂不可用，请改为文字描述或本地上传图片】', '')

const compactLongLinks = (content = '') =>
  String(content).replace(/https?:\/\/\S+/gi, (url) => {
    const lower = url.toLowerCase()
    const isLikelyImage =
      lower.includes('myqcloud.com') ||
      lower.includes('imgur.com') ||
      /\.(png|jpg|jpeg|webp|gif)(\?|$)/i.test(lower)
    const isLongOrSigned =
      url.length > 90 ||
      lower.includes('q-signature=') ||
      lower.includes('x-amz-signature=')
    if (isLikelyImage && isLongOrSigned) return ''
    return url
  })

const PLANNER_FIELD_LABELS =
  '核心需求|关键矛盾|具体规避|附加条件|时间窗口|预算约束|人流风险|时间风险|安全风险|预算风险|' +
  '时间策略|路线策略|氛围策略|备份策略|动线|亮点|便利性|风险提示|推荐地点说明|' +
  '本轮完成标准|下一步可选项|下一步可执行项'

const isPlannerReportMarkdown = (text = '') => {
  const t = String(text).trim()
  if (/^#?\s*行程规划报告/.test(t) || /^行程规划报告/.test(t)) return true
  if (t.includes('## 一、目标理解')) return true
  return (
    t.includes('行程规划报告') &&
    /[一二三四五][、．.]/.test(t) &&
    (t.includes('具体方案') || t.includes('目标理解'))
  )
}

/** 去掉仅含 # / ## / ### 的废行（模型或断行产生）。 */
const stripPlannerOrphanHashLines = (text = '') => {
  if (!isPlannerReportMarkdown(text)) return text
  return text
    .split('\n')
    .filter((line) => !/^#{1,3}\s*$/.test(line.trim()))
    .join('\n')
}

const RECO_PLACE_LABEL = /^(?:-\s*)?推荐地点说明[：:]/

/** 一行内多个「地点：说明」粘连时拆成多条列表项。 */
const splitMergedPlaceLine = (s) => {
  const str = String(s).trim()
  const re =
    /([\u4e00-\u9fa5「」·A-Za-z0-9·\-][\u4e00-\u9fa5「」·A-Za-z0-9·\s]{0,40}?)[：:]\s*/g
  const matches = [...str.matchAll(re)]
  if (matches.length <= 1) return null
  const out = []
  for (let i = 0; i < matches.length; i += 1) {
    const start = matches[i].index + matches[i][0].length
    const end = i + 1 < matches.length ? matches[i + 1].index : str.length
    const name = matches[i][1].trim().replace(/\s+/g, '')
    const body = str.slice(start, end).trim()
    if (name.length >= 2 && body) out.push(`- **${name}**：${body}`)
  }
  return out.length ? out : null
}

const convertTailSummaryLine = (t) => {
  const s = String(t).trim()
  if (s.length < 6) return null
  if (/^[-*•>#]/.test(s) || /^#{1,3}\s/.test(s)) return null
  const colon = s.match(/^(.{2,42})[：:]\s*(.+)$/)
  if (colon) return `- **${colon[1].trim()}**：${colon[2].trim()}`
  const parts = s.split(/\s+/).filter(Boolean)
  if (parts.length >= 2 && parts[0].length >= 2) {
    return `- **${parts[0]}** ${parts.slice(1).join(' ')}`
  }
  return null
}

const convertPlaceBodyLine = (t) => {
  const s = String(t).trim()
  if (!s) return null
  if (/^-\s+/.test(s)) {
    const rest = s.replace(/^-\s+/, '').trim()
    if (/^\*\*.+\*\*/.test(rest)) return `- ${rest}`
    const m = rest.match(/^(.{2,45}?)[：:]\s*(.+)$/)
    if (m) return `- **${m[1].trim()}**：${m[2].trim()}`
    return `- ${rest}`
  }
  const m = s.match(/^(.{2,45}?)[：:]\s*(.+)$/)
  if (m) return `- **${m[1].trim()}**：${m[2].trim()}`
  return convertTailSummaryLine(s)
}

const pushPlaceLinesFromSegment = (segment, out) => {
  const seg = String(segment).trim()
  if (!seg) return
  const merged = splitMergedPlaceLine(seg)
  if (merged) {
    merged.forEach((l) => out.push(l))
    return
  }
  const one = convertPlaceBodyLine(seg)
  if (one) out.push(one)
  else out.push(`- ${seg}`)
}

/** 推荐地点说明块 + 「五、」后地点速览转为无序列表，并为地点名加 **（渲染为高亮）。 */
const enhancePlannerPlaceLists = (text) => {
  if (!isPlannerReportMarkdown(text)) return text
  const lines = text.split('\n')
  const out = []
  let inReco = false
  let pastNextSteps = false

  for (let i = 0; i < lines.length; i += 1) {
    const raw = lines[i]
    const t = raw.trim()

    if (/^###\s+方案[一二三四]/.test(t)) inReco = false

    if (/^(##\s*)?五[、．.]复盘/.test(t)) {
      pastNextSteps = false
    }
    if (/下一步可执行项/.test(t)) pastNextSteps = true
    if (pastNextSteps && /^>\s*/.test(t)) pastNextSteps = false

    if (RECO_PLACE_LABEL.test(t)) {
      inReco = true
      const after = t.replace(RECO_PLACE_LABEL, '').trim()
      out.push('- 推荐地点说明：')
      if (after) pushPlaceLinesFromSegment(after, out)
      continue
    }

    if (inReco) {
      if (!t) {
        inReco = false
        out.push(raw)
        continue
      }
      if (
        /^###\s/.test(t) ||
        /^##\s*[一二三四五六七八九十]/.test(t) ||
        /^[一二三四五][、．.](?:目标理解|约束与风险|计划策略|具体方案|复盘)/
          .test(t)
      ) {
        inReco = false
        out.push(raw)
        continue
      }
      if (
        /^-\s+(动线|亮点|风险提示|便利性|推荐地点)/.test(t) ||
        /^方案[一二三四]/.test(t)
      ) {
        inReco = false
        out.push(raw)
        continue
      }
      const merged = splitMergedPlaceLine(t)
      if (merged) merged.forEach((l) => out.push(l))
      else {
        const one = convertPlaceBodyLine(t)
        if (typeof one === 'string') out.push(one)
        else out.push(raw)
      }
      continue
    }

    if (
      pastNextSteps &&
      t &&
      !/^[-*•>📍]/.test(t) &&
      !/^#{1,3}\s/.test(t) &&
      !/^\d+[\.、]\s*/.test(t) &&
      !/^①/.test(t) &&
      !t.startsWith('本轮') &&
      !t.startsWith('下一步') &&
      !/完成标准/.test(t)
    ) {
      const conv = convertTailSummaryLine(t)
      if (conv) {
        out.push(conv)
        continue
      }
    }

    out.push(raw)
  }

  return out.join('\n')
}

/** 规划报告：保留成对 **（用于地点名），去掉落单 **；其它对话仍做残缺修复。 */
const fixBrokenBoldMarkers = (text = '') => {
  const t = String(text)
  if (isPlannerReportMarkdown(t)) {
    const s = '\uE000'
    const e = '\uE001'
    return t
      .replace(/\*\*([^*]+?)\*\*/g, `${s}$1${e}`)
      .replace(/\*\*/g, '')
      .replace(/\uE000/g, '**')
      .replace(/\uE001/g, '**')
  }
  const s = '\uE000'
  const e = '\uE001'
  return t
    .replace(/\*\*([^*]+?)\*\*/g, `${s}$1${e}`)
    .replace(/\*\*([：:])/g, '$1')
    .replace(/([：:])\*\*/g, '$1')
    .replace(/\*\*/g, '')
    .replace(/\uE000/g, '**')
    .replace(/\uE001/g, '**')
}

const normalizePlannerHeadingsAndFields = (text = '') => {
  let t = String(text || '').replace(/\r\n/g, '\n').trim()
  if (!t) return t

  t = t.replace(/(^|\n)(#{1,3})(?=\S)/g, '$1$2 ')

  if (/^行程规划报告/.test(t) && !/^#\s/.test(t)) {
    t = t.replace(/^([^\n]+)/, '# $1')
  }

  t = t.replace(/([。！？])\s*([一二三四五六七八九十]+[、．.])/g, '$1\n$2')

  t = t.replace(/(^|\n)(方案[一二三四][^：\n]{0,20}?)([：:])/g, '$1### $2$3')

  const fieldReMid = new RegExp(`([^\\n])\\s*(${PLANNER_FIELD_LABELS})([：:])`, 'g')
  t = t.replace(fieldReMid, '$1\n- $2$3')
  const fieldReStart = new RegExp(`^(${PLANNER_FIELD_LABELS})([：:])`, 'gm')
  t = t.replace(fieldReStart, '- $1$2')

  const sectionIntroRe =
    /^([一二三四五六七八九十]+[、．.][^\n：]{2,28}?)\s+([^\n：]{2,16}[：:])\s*(.*)$/gm
  t = t.replace(sectionIntroRe, '## $1\n- $2$3')

  t = t.replace(/\*\*((?:动线|亮点|便利性|风险提示)[：:])/g, '\n- $1')

  t = t.split('\n').map((line) => {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('>')) return line
    if (trimmed.startsWith('- ')) return line
    const m = trimmed.match(new RegExp(`^(${PLANNER_FIELD_LABELS})([：:])`))
    if (m) return `- ${trimmed}`
    return line
  }).join('\n')

  t = t.replace(/^\*\*\s*/gm, '').replace(/\n\*\*\s*/g, '\n')

  if (isPlannerReportMarkdown(t)) {
    t = stripPlannerOrphanHashLines(t)
  }
  t = t.replace(/\n{3,}/g, '\n\n').trim()
  t = t.replace(/\n\d{1,2}:\d{2}\s*$/m, '')
  return t.trim()
}

const removeMarkdownImageArtifacts = (content = '') => {
  const cleaned = String(content)
    .replace(/!\[[^\]]*]\((https?:\/\/\S+)\)/gi, '')
    .replace(/!\[[^\]]*]\([^)]*\)/g, '')
    .replace(/!\[[^\]]*]\(/g, '')
    .replace(/!\[\s*]/g, '')
    .replace(/\[图片链接已折叠，见下方预览]/g, '')
    .replace(/【图片直链暂不可用，请改为文字描述或本地上传图片】/g, '')

  return cleaned
    .split(/\r?\n/)
    .map((line) => {
      const normalized = line
        .replace(/^[!\[\]\(\)`'"|]+(?=📍)/g, '')
        .replace(/^▋+$/g, '')
        .replace(/[ \t]{2,}/g, ' ')
      if (/^[!\[\]\(\){}<>_—~.,:;，。！？、：；\-\s|]+$/.test(normalized)) {
        return ''
      }
      return normalized
    })
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

const enforceMarkdownLineBreaks = (content = '') => {
  let text = String(content || '').replace(/\r\n/g, '\n')
  text = text
    .replace(/\s*(###\s*)/g, '\n$1')
    .replace(/\s*(##\s*)/g, '\n$1')
    .replace(/\s*(#\s*)/g, '\n$1')
    .replace(/([^\n])\s*(>\s+)/g, '$1\n$2')
    .replace(/([^\n])\s*(-\s+)/g, '$1\n$2')
    .replace(/([^\n])\s*(\d+[\.、]\s+)/g, '$1\n$2')
    .replace(/([^\n])\s*(?=##\s)/g, '$1\n')
    .replace(/([^\n])\s*(?=###\s)/g, '$1\n')
    .replace(/(^|\n)(#{1,3})([^\s#])/g, '$1$2 $3')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
  return text
}

const improveTextWrap = (content = '') => {
  const lines = String(content).split(/\r?\n/)
  return lines
    .map((line) => {
      const plain = line.trim()
      if (plain.length <= 72) return line
      // 对超长行按中文标点做断句，提升阅读性。
      return line.replace(/([。；！？])/g, '$1\n')
    })
    .join('\n')
}

const normalizeLineForDetect = (line = '') =>
  String(line)
    .replace(/^[-*•>\s]+/, '')
    .replace(/^#+\s+/, '')
    .replace(/^[`'"|]+|[`'"|]+$/g, '')
    .trim()

const isSectionHeading = (line = '') => {
  const normalized = normalizeLineForDetect(line)
  return /^(?:第[一二三四五六七八九十百0-9]+[章节部分]|[一二三四五六七八九十百0-9]+[、.．])[^\n]{0,36}[：:]?$/.test(normalized)
}

const isFieldHeading = (line = '') => {
  const normalized = normalizeLineForDetect(line)
  return normalized.length <= 28 && /[：:]$/.test(normalized)
}

const extractInlineFieldPrefix = (line = '') => {
  const normalized = normalizeLineForDetect(line)
  const matched = normalized.match(/^((?:[一二三四五六七八九十百0-9]+[、.．])?[^：:\n]{2,28}[：:])\s*(.+)$/)
  if (!matched) return null
  return { prefix: matched[1], rest: matched[2] }
}

const renderAiMessage = (content = '') => {
  const raw = compactLongLinks(removeLinkHints(content))
  const afterFields = normalizePlannerHeadingsAndFields(fixBrokenBoldMarkers(raw))
  const afterBreaks = enforceMarkdownLineBreaks(afterFields)
  const afterOrphans = stripPlannerOrphanHashLines(afterBreaks)
  const afterPlaces = enhancePlannerPlaceLists(afterOrphans)
  const afterOrphans2 = stripPlannerOrphanHashLines(afterPlaces)
  const normalized = normalizeStructuredText(
    improveTextWrap(removeMarkdownImageArtifacts(afterOrphans2))
  )
  const safe = escapeHtml(normalized)
  const lines = safe.split(/\r?\n/)
  let html = ''

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) {
      html += '<div class="line line-empty"></div>'
      continue
    }
    const detectLine = normalizeLineForDetect(line)

    let withBold = line.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    withBold = withBold.replace(/\*\*/g, '')
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

    if (isSectionHeading(detectLine)) {
      html += `<h4 class="section-heading">${detectLine}</h4>`
      continue
    }
    if (isFieldHeading(detectLine)) {
      html += `<div class="line line-field-heading">${detectLine}</div>`
      continue
    }
    const inlineField = extractInlineFieldPrefix(detectLine)
    if (inlineField) {
      html += `<div class="line line-field-inline"><strong>${inlineField.prefix}</strong> ${inlineField.rest}</div>`
      continue
    }
    if (/^[-*•]\s+/.test(withBold)) {
      html += `<div class="line line-bullet">${withBold.replace(/^[-*•]\s+/, '')}</div>`
      continue
    }
    const orderedMatched = withBold.match(/^(\d+)[\.、]\s+(.+)$/)
    if (orderedMatched) {
      html += `<div class="line line-ordered"><span class="line-index">${orderedMatched[1]}</span><span>${orderedMatched[2]}</span></div>`
      continue
    }
    if (/^📍\s*/.test(withBold)) {
      html += `<div class="line line-location">${withBold}</div>`
      continue
    }
    if (/^>\s*/.test(withBold)) {
      html += `<div class="line line-tip">${withBold.replace(/^>\s*/, '💡 ')}</div>`
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
    || raw.includes('searchimage')
    || raw.includes('image-search-mcp-server')
    || raw.includes('pexels')
    || raw.includes('检索图片')
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

const imageProgressHint = computed(() => {
  if (!isImageGenerating.value) return '正在处理图片任务，请稍候...'
  for (let i = displayMessages.value.length - 1; i >= 0; i -= 1) {
    const msg = displayMessages.value[i]
    if (!msg || msg.isUser || msg.type !== 'tool_call' || !isImageToolCall(msg)) continue
    const raw = `${msg.toolName || ''} ${msg.toolArgs || ''} ${msg.content || ''}`.toLowerCase()
    if (raw.includes('searchimage') || raw.includes('pexels') || raw.includes('检索图片')) {
      return '正在为你检索高质量实景图，请稍候...'
    }
    if (raw.includes('generateimage') || raw.includes('tencent-aiart') || raw.includes('图片生成') || raw.includes('生图')) {
      return '正在为你生成配图，请稍候...'
    }
  }
  return '正在整理图文结果并回传，请稍候...'
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
  const main = messagesContainer.value?.closest('.chat-main')
  if (main) {
    main.scrollTop = main.scrollHeight
    return
  }
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(() => displayMessages.value.length, scrollToBottom)
watch(() => displayMessages.value.map((m) => m.content).join(''), scrollToBottom)
onMounted(scrollToBottom)
</script>

<style scoped>
.chat-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 0;
  border-radius: 0;
  background: transparent;
  overflow: hidden;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 24px;
  min-height: 200px;
}

.empty-state__title {
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 12px;
}

.empty-state__hint {
  max-width: 420px;
  font-size: 0.95rem;
  line-height: 1.6;
  color: var(--ink-muted);
}

.messages {
  flex: 1;
  min-height: 0;
  overflow: visible;
  padding: 16px 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bubble-wrap {
  display: flex;
  align-items: flex-start;
  max-width: 86%;
  min-width: 0;
}

.bubble-wrap--user {
  margin-left: auto;
  flex-direction: row;
  min-width: 0;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  border: 1px solid var(--line-soft);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0 6px;
  font-size: 11px;
  overflow: hidden;
  flex: 0 0 36px;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bubble {
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  padding: 12px 12px;
  background: rgba(255, 255, 255, 0.06);
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.bubble-wrap--user .bubble {
  background: rgba(239, 46, 53, 0.2);
  border-top-right-radius: 8px;
}

.bubble-wrap--ai .bubble {
  border-top-left-radius: 8px;
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
  line-height: 1.72;
  margin: 0 0 6px;
}

.rich-content :deep(.line-empty) {
  height: 6px;
  margin: 0;
}

.rich-content :deep(.line-bullet),
.rich-content :deep(.line-ordered) {
  padding-left: 4px;
}

.rich-content :deep(.section-heading) {
  margin: 14px 0 8px;
  padding: 0 0 6px;
  border-radius: 0;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.14);
  background: transparent;
  font-size: 1.22rem;
  line-height: 1.45;
  font-weight: 800;
  color: #f2f7ff;
}

.rich-content :deep(.line-field-heading) {
  margin-top: 5px;
  font-weight: 700;
  color: #f2f7ff;
}

.rich-content :deep(.line-field-inline) {
  padding: 2px 0;
  border-radius: 0;
  background: transparent;
}

.rich-content :deep(.line-field-inline strong) {
  color: #f2f7ff;
}

.rich-content :deep(.line-bullet) {
  position: relative;
  padding-left: 18px;
}

.rich-content :deep(.line-bullet)::before {
  content: '•';
  position: absolute;
  left: 4px;
  color: rgba(255, 255, 255, 0.55);
}

.rich-content :deep(.line-bullet strong) {
  font-weight: 700;
  color: #9fd9ff;
  letter-spacing: 0.02em;
}

.rich-content :deep(.line-ordered) {
  display: grid;
  grid-template-columns: 22px 1fr;
  column-gap: 8px;
  align-items: start;
}

.rich-content :deep(.line-index) {
  display: inline-grid;
  place-items: center;
  width: 18px;
  height: 18px;
  margin-top: 3px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  color: #f5fbff;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.28);
}

.rich-content :deep(.line-location) {
  padding: 4px 0;
  border-radius: 0;
  border: none;
  border-left: 2px solid rgba(255, 255, 255, 0.22);
  padding-left: 10px;
  background: transparent;
}

.rich-content :deep(.line-tip) {
  padding: 6px 0 6px 10px;
  border-radius: 0;
  border-left: 2px solid rgba(255, 255, 255, 0.22);
  background: transparent;
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
  color: #f2f7ff;
}

.rich-content :deep(.md-h1) {
  margin: 12px 0 10px;
  font-size: 1.38rem;
  line-height: 1.4;
  font-weight: 800;
  color: #f5fbff;
}

.rich-content :deep(.md-h2) {
  margin: 14px 0 8px;
  font-size: 1.24rem;
  line-height: 1.4;
  font-weight: 800;
  color: #f2f7ff;
}

.rich-content :deep(.md-h3) {
  margin: 10px 0 6px;
  font-size: 1.1rem;
  line-height: 1.45;
  font-weight: 700;
  color: #f2f7ff;
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
  border-radius: 12px;
  padding: 10px 11px;
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

.tool-card__progress {
  font-size: 12px;
  line-height: 1.5;
  color: #bfe6ff;
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

.thinking-block .rich-content.glass-scrollbar,
.messages.glass-scrollbar {
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.14) transparent;
}

small {
  display: block;
  margin-top: 5px;
  color: var(--ink-muted);
  font-size: 11px;
  text-align: right;
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
  padding: 12px 12px;
  border: 1px solid rgba(122, 203, 255, 0.36);
  border-radius: 12px;
  background:
    radial-gradient(circle at 0% 0%, rgba(71, 157, 255, 0.18), transparent 46%),
    rgba(20, 41, 66, 0.38);
}

.image-progress-card__text {
  font-size: 12px;
  color: #bfe6ff;
  margin-bottom: 4px;
}

.image-progress-card__subtext {
  font-size: 11px;
  color: rgba(191, 230, 255, 0.8);
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

.composer.glass-composer {
  position: sticky;
  bottom: 0;
  z-index: 10;
  flex-shrink: 0;
  padding: 8px 0 8px;
  border: 0;
  background:
    linear-gradient(to top, rgba(3, 3, 3, 0.92) 55%, rgba(3, 3, 3, 0));
}

.composer__inner {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 24px;
  border: 1px solid var(--glass-border);
  background:
    var(--glass-highlight),
    var(--glass-bg);
  box-shadow: var(--glass-shadow);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
}

.composer__inner textarea {
  flex: 1;
  min-height: 24px;
  max-height: 120px;
  resize: none;
  border: 0;
  background: transparent;
  color: var(--ink);
  padding: 4px 0;
  font-size: 0.95rem;
  line-height: 1.5;
}

.composer__inner textarea:focus {
  outline: none;
}

.composer__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.composer__stop {
  border: 1px solid var(--glass-border);
  border-radius: 999px;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--ink-soft);
  font-size: 0.82rem;
  cursor: pointer;
}

.composer__send {
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #2f9cff, #48f5ff);
  color: #fff;
  cursor: pointer;
  transition: transform 0.18s ease, opacity 0.18s ease;
}

.composer__send svg {
  width: 20px;
  height: 20px;
}

.composer__send:hover:not(:disabled) {
  transform: scale(1.05);
}

.composer__send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 760px) {
  .bubble-wrap {
    max-width: 92%;
  }
}
</style>
