<template>
  <aside
    ref="sidebarRef"
    :class="['session-sidebar', 'glass-panel', `session-sidebar--${sidebarMode}`]"
    :aria-hidden="sidebarMode === 'hidden'"
  >
    <div class="session-sidebar__header">
      <template v-if="sidebarMode === 'rail'">
        <button
          class="session-sidebar__toggle"
          type="button"
          :aria-label="copy.sidebarExpand"
          :title="copy.sidebarExpand"
          @click="emit('expand')"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M9 6L15 12L9 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
        <button
          class="session-sidebar__toggle session-sidebar__toggle--ghost"
          type="button"
          :aria-label="copy.sidebarHide"
          :title="copy.sidebarHide"
          @click="emit('collapse')"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M15 6L9 12L15 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </template>
      <button
        v-else-if="sidebarMode === 'expanded'"
        class="session-sidebar__toggle"
        type="button"
        :aria-label="copy.sidebarCollapse"
        :title="copy.sidebarCollapse"
        @click="emit('collapse')"
      >
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M15 6L9 12L15 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </div>

    <button
      class="session-sidebar__new"
      type="button"
      :title="copy.newSession"
      @click="emit('create')"
    >
      <span class="session-sidebar__new-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
      </span>
      <span v-if="sidebarMode === 'expanded'" class="session-sidebar__new-label">{{ copy.newSession }}</span>
    </button>

    <div v-if="sidebarMode === 'expanded'" class="session-sidebar__search">
      <svg class="session-sidebar__search-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <circle cx="11" cy="11" r="6.5" stroke="currentColor" stroke-width="1.6" />
        <path d="M16 16L20 20" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      </svg>
      <input
        v-model="searchInput"
        type="search"
        :placeholder="copy.sessionSearchPlaceholder"
        autocomplete="off"
        aria-label="搜索会话"
      />
    </div>

    <div
      v-if="sidebarMode === 'expanded'"
      class="session-sidebar__list glass-scrollbar"
      role="navigation"
      aria-label="历史会话"
    >
      <section v-for="group in groupedSessions" :key="group.key" class="session-group">
        <h3 class="session-group__title">{{ group.label }}</h3>
        <ul class="session-group__items">
          <li
            v-for="item in group.items"
            :key="item.id"
            :class="['session-item', { active: item.id === activeSessionId, streaming: isStreaming && streamingSessionId === item.id }]"
          >
            <button class="session-item__main" type="button" @click="emit('select', item.id)">
              <span v-if="item.pinned" class="session-item__pin" aria-hidden="true">📌</span>
              <span class="session-item__title">{{ item.title || '新会话' }}</span>
              <span v-if="isStreaming && streamingSessionId === item.id" class="session-item__badge">生成中</span>
            </button>
            <button
              :class="['session-item__menu-btn', { visible: item.id === activeSessionId || openMenuId === item.id }]"
              type="button"
              aria-label="会话操作"
              @click.stop="toggleMenu(item.id, $event)"
            >
              ···
            </button>
          </li>
        </ul>
      </section>
      <p v-if="!sessions.length" class="session-sidebar__empty">{{ copy.sessionEmpty }}</p>
      <p v-else-if="searchInput.trim() && !groupedSessions.length" class="session-sidebar__empty">{{ copy.sessionSearchEmpty }}</p>
    </div>

    <div v-else-if="sidebarMode === 'rail'" class="session-sidebar__rail-list">
      <button
        v-for="item in railSessions"
        :key="item.id"
        :class="['session-rail-item', { active: item.id === activeSessionId }]"
        type="button"
        :title="item.title || '新会话'"
        @click="emit('select', item.id)"
      >
        {{ sessionInitial(item.title) }}
      </button>
    </div>

    <div v-if="userDisplayName && sidebarMode !== 'hidden'" class="session-sidebar__user">
      <span class="session-sidebar__avatar">
        <img v-if="resolvedAvatarUrl" :src="resolvedAvatarUrl" alt="" @error="avatarLoadFailed = true" />
        <template v-else>{{ userInitial }}</template>
      </span>
      <span v-if="sidebarMode === 'expanded'" class="session-sidebar__name">{{ userDisplayName }}</span>
    </div>

    <Teleport to="body">
      <div
        v-if="openMenuId"
        ref="menuRef"
        class="session-menu glass-menu"
        :style="menuStyle"
        role="menu"
        @mousedown.stop
        @click.stop
      >
        <button type="button" role="menuitem" @click="handleRename">
          <span aria-hidden="true">✎</span>
          {{ copy.sessionRename }}
        </button>
        <button type="button" role="menuitem" @click="handlePin">
          <span aria-hidden="true">📌</span>
          {{ activeMenuSession?.pinned ? copy.sessionUnpin : copy.sessionPinned }}
        </button>
        <button type="button" role="menuitem" @click="handleShare">
          <span aria-hidden="true">↗</span>
          {{ copy.sessionShare }}
        </button>
        <button type="button" role="menuitem" class="danger" @click="handleDelete">
          <span aria-hidden="true">🗑</span>
          {{ copy.sessionDelete }}
        </button>
      </div>
    </Teleport>
  </aside>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { brandCopy } from '../constants/copy'
import { isValidAvatarSrc } from '../utils/avatar'

const props = defineProps({
  sessions: { type: Array, default: () => [] },
  activeSessionId: { type: String, default: '' },
  streamingSessionId: { type: String, default: '' },
  isStreaming: { type: Boolean, default: false },
  userAvatarUrl: { type: String, default: '' },
  userDisplayName: { type: String, default: '' },
  sidebarMode: { type: String, default: 'expanded' }
})

const emit = defineEmits(['create', 'select', 'rename', 'pin', 'share', 'delete', 'collapse', 'expand'])

const copy = brandCopy.chat
const openMenuId = ref('')
const menuStyle = ref({ top: '0px', left: '0px' })
const sidebarRef = ref(null)
const menuRef = ref(null)
const searchInput = ref('')
const debouncedSearch = ref('')
const avatarLoadFailed = ref(false)
let searchTimer = null

watch(searchInput, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    debouncedSearch.value = value.trim().toLowerCase()
  }, 200)
})

watch(() => props.userAvatarUrl, () => {
  avatarLoadFailed.value = false
})

const userInitial = computed(() => (props.userDisplayName || 'ME').slice(0, 1).toUpperCase())

const resolvedAvatarUrl = computed(() => {
  if (avatarLoadFailed.value) return ''
  return isValidAvatarSrc(props.userAvatarUrl) ? props.userAvatarUrl : ''
})

const activeMenuSession = computed(() => props.sessions.find(item => item.id === openMenuId.value) || null)

const filteredSessions = computed(() => {
  const keyword = debouncedSearch.value
  if (!keyword) return props.sessions
  return props.sessions.filter((session) =>
    String(session.title || '新会话').toLowerCase().includes(keyword)
  )
})

const railSessions = computed(() => {
  const pinned = props.sessions.filter((item) => item.pinned)
  const rest = props.sessions.filter((item) => !item.pinned)
  return [...pinned, ...rest].slice(0, 8)
})

const startOfDay = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate())

const getSessionDate = (session) => {
  const raw = session.updatedAt || session.createdAt
  return raw ? new Date(raw) : new Date()
}

const getMonthLabel = (date) => {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  return `${year}-${month}`
}

const groupedSessions = computed(() => {
  const now = new Date()
  const todayStart = startOfDay(now)
  const weekStart = new Date(todayStart)
  weekStart.setDate(weekStart.getDate() - 7)
  const monthStart = new Date(todayStart)
  monthStart.setDate(monthStart.getDate() - 30)

  const pinned = []
  const today = []
  const week = []
  const month = []
  const olderMap = new Map()

  for (const session of filteredSessions.value) {
    if (session.pinned) {
      pinned.push(session)
      continue
    }
    const date = getSessionDate(session)
    if (date >= todayStart) {
      today.push(session)
    } else if (date >= weekStart) {
      week.push(session)
    } else if (date >= monthStart) {
      month.push(session)
    } else {
      const label = getMonthLabel(date)
      if (!olderMap.has(label)) olderMap.set(label, [])
      olderMap.get(label).push(session)
    }
  }

  const groups = []
  if (pinned.length) groups.push({ key: 'pinned', label: copy.sessionGroupPinned, items: pinned })
  if (today.length) groups.push({ key: 'today', label: copy.sessionGroupToday, items: today })
  if (week.length) groups.push({ key: 'week', label: copy.sessionGroupWeek, items: week })
  if (month.length) groups.push({ key: 'month', label: copy.sessionGroupMonth, items: month })

  Array.from(olderMap.entries())
    .sort((a, b) => b[0].localeCompare(a[0]))
    .forEach(([label, items]) => {
      groups.push({ key: `older-${label}`, label, items })
    })

  return groups
})

const sessionInitial = (title = '') => {
  const text = String(title || '新').trim()
  return text ? text.slice(0, 1).toUpperCase() : '新'
}

const closeMenu = () => {
  openMenuId.value = ''
}

const toggleMenu = (sessionId, event) => {
  if (openMenuId.value === sessionId) {
    closeMenu()
    return
  }
  openMenuId.value = sessionId
  const rect = event.currentTarget.getBoundingClientRect()
  const menuWidth = 168
  menuStyle.value = {
    top: `${rect.bottom + 6}px`,
    left: `${Math.min(window.innerWidth - menuWidth - 12, Math.max(12, rect.right - menuWidth))}px`
  }
}

const handleRename = () => {
  if (!openMenuId.value) return
  emit('rename', openMenuId.value)
  closeMenu()
}

const handlePin = () => {
  if (!activeMenuSession.value) return
  emit('pin', activeMenuSession.value.id, !activeMenuSession.value.pinned)
  closeMenu()
}

const handleShare = () => {
  if (!openMenuId.value) return
  emit('share', openMenuId.value)
  closeMenu()
}

const handleDelete = () => {
  if (!openMenuId.value) return
  emit('delete', openMenuId.value)
  closeMenu()
}

const handleDocumentPointerDown = (event) => {
  if (!openMenuId.value) return
  const target = event.target
  if (menuRef.value?.contains(target)) return
  if (sidebarRef.value?.contains(target)) return
  closeMenu()
}

onMounted(() => document.addEventListener('pointerdown', handleDocumentPointerDown))
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<style scoped>
.session-sidebar {
  width: 260px;
  min-width: 260px;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 12px;
  border-right: 1px solid var(--glass-border);
  border-radius: 0;
  overflow: hidden;
  transition: width 0.22s ease, min-width 0.22s ease, padding 0.22s ease, opacity 0.22s ease;
}

.session-sidebar--rail {
  width: 56px;
  min-width: 56px;
  padding: 12px 8px;
}

.session-sidebar--hidden {
  width: 0;
  min-width: 0;
  padding: 0;
  border-right: 0;
  opacity: 0;
  pointer-events: none;
  overflow: hidden;
}

.session-sidebar__header {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-bottom: 8px;
}

.session-sidebar--rail .session-sidebar__header {
  flex-direction: column;
  align-items: center;
  margin-bottom: 10px;
}

.session-sidebar__toggle {
  width: 32px;
  height: 32px;
  border: 1px solid var(--glass-border);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--ink-muted);
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}

.session-sidebar__toggle:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--ink);
}

.session-sidebar__toggle--ghost {
  opacity: 0.72;
}

.session-sidebar__toggle svg {
  width: 16px;
  height: 16px;
}

.session-sidebar__new {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 11px 14px;
  border: 1px solid var(--glass-border);
  border-radius: 999px;
  background:
    var(--glass-highlight),
    var(--glass-bg);
  color: var(--ink);
  font-weight: 600;
  font-size: 0.92rem;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.session-sidebar--rail .session-sidebar__new {
  width: 40px;
  height: 40px;
  padding: 0;
  margin-bottom: 10px;
}

.session-sidebar__new:hover {
  border-color: rgba(255, 255, 255, 0.28);
  box-shadow: 0 0 0 1px rgba(127, 231, 255, 0.12);
}

.session-sidebar__new-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--ink);
}

.session-sidebar__new-icon svg {
  width: 16px;
  height: 16px;
  display: block;
}

.session-sidebar__new-label {
  line-height: 1;
  white-space: nowrap;
}

.session-sidebar__search {
  position: relative;
  margin-bottom: 12px;
}

.session-sidebar__search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 15px;
  height: 15px;
  color: var(--ink-muted);
  pointer-events: none;
}

.session-sidebar__search input {
  width: 100%;
  height: 38px;
  padding: 0 12px 0 34px;
  border-radius: 12px;
  border: 1px solid var(--glass-border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--ink);
  font-size: 0.86rem;
}

.session-sidebar__search input:focus {
  outline: none;
  border-color: rgba(127, 231, 255, 0.35);
}

.session-sidebar__search input::placeholder {
  color: var(--ink-muted);
}

.session-sidebar__list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 2px;
  min-height: 0;
}

.session-sidebar__rail-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 2px 0;
}

.session-rail-item {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  border: 1px solid var(--glass-border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--ink-soft);
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease;
}

.session-rail-item:hover,
.session-rail-item.active {
  background: rgba(127, 231, 255, 0.14);
  border-color: rgba(127, 231, 255, 0.35);
  color: #e8f8ff;
}

.session-group {
  min-width: 0;
  overflow: hidden;
}

.session-group + .session-group {
  margin-top: 16px;
}

.session-group__title {
  margin: 0 0 8px;
  padding: 0 10px;
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  color: var(--ink-muted);
}

.session-group__items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 2px;
  min-width: 0;
  overflow: hidden;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 2px;
  border-radius: 12px;
  min-width: 0;
  overflow: hidden;
  transition: background 0.18s ease, box-shadow 0.18s ease;
}

.session-item:hover,
.session-item.active {
  background: rgba(255, 255, 255, 0.08);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06);
}

.session-item.streaming .session-item__title {
  color: var(--cyan);
}

.session-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px;
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  font-size: 0.9rem;
  overflow: hidden;
}

.session-item__pin {
  flex-shrink: 0;
  font-size: 0.72rem;
}

.session-item__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__badge {
  flex-shrink: 0;
  font-size: 0.7rem;
  color: var(--cyan);
}

.session-item__menu-btn {
  width: 28px;
  height: 28px;
  margin-right: 4px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink-muted);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.18s ease, background 0.18s ease;
  flex-shrink: 0;
}

.session-item:hover .session-item__menu-btn,
.session-item__menu-btn.visible {
  opacity: 1;
}

.session-item__menu-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--ink);
}

.session-sidebar__empty {
  margin: 16px 10px 0;
  color: var(--ink-muted);
  font-size: 0.88rem;
}

.session-sidebar__user {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid var(--glass-border);
  background: rgba(255, 255, 255, 0.04);
  min-width: 0;
  overflow: hidden;
}

.session-sidebar--rail .session-sidebar__user {
  justify-content: center;
  padding: 8px;
}

.session-sidebar__avatar {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 1px solid var(--glass-border);
  display: grid;
  place-items: center;
  overflow: hidden;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--ink);
  background: rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.session-sidebar__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.session-sidebar__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.88rem;
  color: var(--ink-soft);
}

@media (max-width: 960px) {
  .session-sidebar,
  .session-sidebar--rail,
  .session-sidebar--hidden {
    width: 100%;
    min-width: 0;
    height: auto;
    max-height: 240px;
    border-right: 0;
    border-bottom: 1px solid var(--glass-border);
    opacity: 1;
    pointer-events: auto;
    padding: 12px;
  }

  .session-sidebar__rail-list {
    display: none;
  }
}
</style>

<style>
.session-menu.glass-menu {
  position: fixed;
  z-index: 1200;
  width: 168px;
  padding: 6px;
  border-radius: 14px;
  border: 1px solid var(--glass-border);
  background: rgba(12, 14, 20, 0.82);
  box-shadow: var(--glass-shadow);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
}

.session-menu.glass-menu button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink);
  font-size: 0.88rem;
  text-align: left;
  cursor: pointer;
}

.session-menu.glass-menu button:hover {
  background: rgba(255, 255, 255, 0.08);
}

.session-menu.glass-menu button.danger {
  color: #ff8f8f;
}
</style>
