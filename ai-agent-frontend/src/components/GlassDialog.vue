<template>
  <Teleport to="body">
    <Transition name="glass-dialog-fade">
      <div v-if="state.visible" class="glass-dialog-root" role="presentation">
        <div
          class="glass-dialog-overlay"
          @click="handleOverlayClick"
        />
        <div
          class="glass-dialog glass-panel"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="state.title ? 'glass-dialog-title' : undefined"
          @keydown.esc="handleCancel"
        >
          <h2 v-if="state.title" id="glass-dialog-title" class="glass-dialog__title">{{ state.title }}</h2>
          <p v-if="state.message" class="glass-dialog__message">{{ state.message }}</p>

          <input
            v-if="state.mode === 'prompt'"
            ref="inputRef"
            v-model="state.inputValue"
            class="glass-dialog__input"
            type="text"
            :placeholder="state.inputPlaceholder"
            @keydown.enter.prevent="handleConfirm"
          />

          <div class="glass-dialog__actions">
            <button type="button" class="glass-dialog__btn glass-dialog__btn--cancel" @click="handleCancel">
              {{ state.cancelText }}
            </button>
            <button
              type="button"
              :class="['glass-dialog__btn', state.danger ? 'glass-dialog__btn--danger' : 'glass-dialog__btn--primary']"
              @click="handleConfirm"
            >
              {{ state.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { closeGlassDialog, useGlassDialogState } from '../composables/useGlassDialog'

const state = useGlassDialogState()
const inputRef = ref(null)

watch(
  () => state.visible,
  async (visible) => {
    if (!visible || state.mode !== 'prompt') return
    await nextTick()
    inputRef.value?.focus()
    inputRef.value?.select()
  }
)

const handleCancel = () => closeGlassDialog(state.mode === 'prompt' ? null : false)

const handleConfirm = () => {
  if (state.mode === 'prompt') {
    const trimmed = String(state.inputValue || '').trim()
    if (!trimmed) return
    closeGlassDialog(trimmed)
    return
  }
  closeGlassDialog(true)
}

const handleOverlayClick = () => {
  if (!state.dismissOnOverlay) return
  handleCancel()
}
</script>

<style scoped>
.glass-dialog-root {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: grid;
  place-items: center;
  padding: 24px;
}

.glass-dialog-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.glass-dialog {
  position: relative;
  z-index: 1;
  width: min(420px, 100%);
  padding: 22px 22px 18px;
  border-radius: 18px;
}

.glass-dialog__title {
  margin: 0 0 10px;
  font-size: 1.08rem;
  font-weight: 700;
  color: var(--ink);
  line-height: 1.4;
}

.glass-dialog__message {
  margin: 0 0 18px;
  font-size: 0.92rem;
  line-height: 1.65;
  color: var(--ink-soft);
}

.glass-dialog__input {
  width: 100%;
  height: 42px;
  margin-bottom: 18px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid var(--glass-border);
  background: rgba(255, 255, 255, 0.06);
  color: var(--ink);
  font-size: 0.92rem;
}

.glass-dialog__input:focus {
  outline: none;
  border-color: rgba(127, 231, 255, 0.45);
  box-shadow: 0 0 0 2px rgba(127, 231, 255, 0.12);
}

.glass-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.glass-dialog__btn {
  border-radius: 999px;
  padding: 9px 18px;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.glass-dialog__btn:hover {
  transform: translateY(-1px);
}

.glass-dialog__btn--cancel {
  border: 1px solid var(--glass-border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--ink-soft);
}

.glass-dialog__btn--primary {
  border: 0;
  background: linear-gradient(135deg, #2f9cff, #48f5ff);
  color: #fff;
}

.glass-dialog__btn--danger {
  border: 1px solid rgba(255, 143, 143, 0.45);
  background: rgba(255, 80, 80, 0.18);
  color: #ffb4b4;
}

.glass-dialog-fade-enter-active,
.glass-dialog-fade-leave-active {
  transition: opacity 0.2s ease;
}

.glass-dialog-fade-enter-active .glass-dialog,
.glass-dialog-fade-leave-active .glass-dialog {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.glass-dialog-fade-enter-from,
.glass-dialog-fade-leave-to {
  opacity: 0;
}

.glass-dialog-fade-enter-from .glass-dialog,
.glass-dialog-fade-leave-to .glass-dialog {
  transform: translateY(8px) scale(0.98);
  opacity: 0;
}
</style>
