import axios from 'axios'

// 根据环境变量设置 API 基础 URL
const API_BASE_URL = process.env.NODE_ENV === 'production' 
 ? '/api' // 生产环境使用相对路径，适用于前后端部署在同一域名下
 : 'http://localhost:8123/api' // 开发环境指向本地后端服务

// 创建axios实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

const API_KEY = import.meta.env.VITE_APP_API_KEY || 'demo-local-key'
const AUTH_TOKEN_KEY = 'ai_agent_token'
const AUTH_MODE_KEY = 'ai_agent_auth_mode'
const AUTH_MODE_JWT_ONLY = 'jwt_only'

export const setAuthToken = (token) => {
  if (token) {
    localStorage.setItem(AUTH_TOKEN_KEY, token)
    // 登录成功后锁定为 JWT 模式，避免再次回退到 API Key 身份。
    localStorage.setItem(AUTH_MODE_KEY, AUTH_MODE_JWT_ONLY)
  }
}

export const getAuthToken = () => localStorage.getItem(AUTH_TOKEN_KEY) || ''
export const clearAuthToken = () => localStorage.removeItem(AUTH_TOKEN_KEY)

/** 退出 JWT 独占模式：无 token 时仍允许用开发 API Key 访问（否则 jwt_only + 空 token 会不发任何凭证 → 401） */
export const clearJwtOnlyMode = () => localStorage.removeItem(AUTH_MODE_KEY)

request.interceptors.request.use((config) => {
  const token = getAuthToken()
  config.headers = config.headers || {}
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
    delete config.headers['X-API-Key']
  } else {
    config.headers['X-API-Key'] = API_KEY
    delete config.headers.Authorization
  }
  return config
})

request.interceptors.response.use(
  (res) => res,
  async (error) => {
    const status = error?.response?.status
    if (status === 401 && getAuthToken()) {
      clearAuthToken()
      clearJwtOnlyMode()
    }
    return Promise.reject(error)
  }
)

// 封装SSE连接
export const connectSSE = (url, params, onMessage, onError) => {
  // 构建带参数的URL
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  
  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  
  // 创建EventSource
  const eventSource = new EventSource(fullUrl, {
    withCredentials: false
  })
  
  eventSource.onmessage = event => {
    let data = event.data
    
    // 检查是否是特殊标记
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      // 处理普通消息
      if (onMessage) onMessage(data)
    }
  }
  
  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }
  
  // 返回eventSource实例，以便后续可以关闭连接
  return eventSource
}

export const streamMentorChat = (payload, onMessage, onError) => {
  const requestId = payload.requestId || crypto.randomUUID()
  const token = getAuthToken()
  const ssePayload = { ...payload, requestId }
  if (token) {
    ssePayload.token = token
  } else {
    ssePayload.apiKey = API_KEY
  }
  const eventSource = connectSSE('/ai/mentor/chat/sse', ssePayload, onMessage, onError)
  return {
    requestId,
    eventSource
  }
}

export const stopMentorChat = async (requestId) => {
  const { data } = await request.post('/ai/mentor/chat/stop', { requestId })
  return data
}

export const listChatSessions = async () => {
  const { data } = await request.get('/chat/sessions')
  return data
}

export const createChatSession = async (payload = {}) => {
  const { data } = await request.post('/chat/sessions', payload)
  return data
}

export const updateChatSession = async (sessionId, payload) => {
  const { data } = await request.put(`/chat/sessions/${sessionId}`, payload)
  return data
}

export const deleteChatSession = async (sessionId) => {
  const { data } = await request.delete(`/chat/sessions/${sessionId}`)
  return data
}

export const listSessionMessages = async (sessionId, limit = 50) => {
  const { data } = await request.get(`/chat/sessions/${sessionId}/messages`, { params: { limit } })
  return data
}

export const exportLatestPlanPdf = async (sessionId) => {
  const { data } = await request.post(`/chat/sessions/${sessionId}/export-plan`)
  return data
}

export const downloadExportPdf = async (exportId) => {
  const response = await request.get(`/chat/exports/${exportId}/download`, {
    responseType: 'blob'
  })
  return response.data
}

export const login = async (payload) => {
  const { data } = await request.post('/auth/login', payload)
  return data
}

export const register = async (payload) => {
  const { data } = await request.post('/auth/register', payload)
  return data
}

export const getProfile = async () => {
  const { data } = await request.get('/user/me')
  return data
}

export const updateProfile = async (payload) => {
  const { data } = await request.put('/user/me', payload)
  return data
}

export const changePassword = async (payload) => {
  const { data } = await request.post('/user/password', payload)
  return data
}

export default {
  streamMentorChat,
  stopMentorChat,
  listChatSessions,
  createChatSession,
  updateChatSession,
  deleteChatSession,
  listSessionMessages,
  exportLatestPlanPdf,
  downloadExportPdf,
  login,
  register,
  getProfile,
  updateProfile,
  changePassword,
  setAuthToken,
  getAuthToken,
  clearAuthToken,
  clearJwtOnlyMode
} 