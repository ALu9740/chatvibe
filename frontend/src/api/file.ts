import request from '@/utils/request'

/** 文件上传返回结构 */
export interface FileUploadResult {
  url: string
  fileName: string
  fileSize: number
  fileType: string
}

/**
 * 上传文件(聊天图片/文件)
 * 后端保存到 /uploads/file/{uuid}.ext
 */
export function uploadFile(file: File): Promise<FileUploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
