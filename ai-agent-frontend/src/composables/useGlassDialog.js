import { reactive } from 'vue'

const state = reactive({
  visible: false,
  mode: 'confirm',
  title: '',
  message: '',
  inputValue: '',
  inputPlaceholder: '',
  confirmText: '确认',
  cancelText: '取消',
  danger: false,
  dismissOnOverlay: true,
  resolve: null
})

const resetState = () => {
  state.visible = false
  state.resolve = null
}

export const useGlassDialogState = () => state

export const openGlassConfirm = ({
  title = '确认删除',
  message = '',
  confirmText = '确认删除',
  cancelText = '取消',
  danger = true,
  dismissOnOverlay = false
} = {}) =>
  new Promise((resolve) => {
    Object.assign(state, {
      visible: true,
      mode: 'confirm',
      title,
      message,
      inputValue: '',
      confirmText,
      cancelText,
      danger,
      dismissOnOverlay,
      resolve
    })
  })

export const openGlassPrompt = ({
  title = '',
  message = '',
  defaultValue = '',
  inputPlaceholder = '',
  confirmText = '确认',
  cancelText = '取消'
} = {}) =>
  new Promise((resolve) => {
    Object.assign(state, {
      visible: true,
      mode: 'prompt',
      title,
      message,
      inputValue: defaultValue,
      inputPlaceholder,
      confirmText,
      cancelText,
      danger: false,
      dismissOnOverlay: true,
      resolve
    })
  })

export const closeGlassDialog = (result) => {
  if (state.resolve) state.resolve(result)
  resetState()
}
