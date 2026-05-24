/** 判断头像 URL 是否可用（过滤被数据库截断的 base64 等无效值） */
export const isValidAvatarSrc = (url = '') => {
  const s = String(url).trim()
  if (!s) return false
  if (s.startsWith('data:image/')) {
    return s.length > 120 && s.includes(',')
  }
  return /^https?:\/\//i.test(s) || s.startsWith('/')
}

/** 压缩头像为 JPEG data URL，默认最长边 200px */
export const compressAvatarFile = (file, maxSize = 200, quality = 0.82) =>
  new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(objectUrl)
      let { width, height } = img
      const scale = Math.min(1, maxSize / Math.max(width, height, 1))
      width = Math.max(1, Math.round(width * scale))
      height = Math.max(1, Math.round(height * scale))
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')
      ctx.drawImage(img, 0, 0, width, height)
      resolve(canvas.toDataURL('image/jpeg', quality))
    }
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('image load failed'))
    }
    img.src = objectUrl
  })
