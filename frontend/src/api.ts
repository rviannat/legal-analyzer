const API = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '')

async function request(path: string, init?: RequestInit) {
  const response = await fetch(`${API}${path}`, { ...init, headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) } })
  if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`)
  if (response.status === 204) return undefined
  return response.json()
}

export async function uploadProcesso(file: File) {
  const form = new FormData(); form.append('arquivo', file)
  const response = await fetch(`${API}/api/v1/processos/analisar`, { method: 'POST', body: form })
  if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`)
  return response.json()
}
export const getJob = (id: string) => request(`/api/v1/processos/analises/${id}`)
export const getDataJud = (id: string) => request(`/api/v1/processos/analises/${id}/datajud`)
export const getDataJudAudit = (id: string) => request(`/api/v1/processos/analises/${id}/datajud/auditoria`)
export const getTimelineAudit = (id: string) => request(`/api/v1/processos/analises/${id}/datajud/timeline`)
export const getDataJudInsights = (id: string) => request(`/api/v1/processos/analises/${id}/datajud/insights`)
export const getBriefing = (id: string) => request(`/api/v1/processos/analises/${id}/briefing`)
export const getIndexInfo = (id: string) => request(`/api/v1/processos/analises/${id}/indice`)
export const getSpecializedLatest = (id: string) => request(`/api/v1/processos/analises/${id}/especializada/ultima`)
export const getSpecializedJob = (id: string) => request(`/api/v1/processos/analises-especializadas/${id}`)
export const getChatHistory = (id: string) => request(`/api/v1/processos/chats/${id}`)
export const startSpecialized = (id: string, body: unknown) => request(`/api/v1/processos/analises/${id}/especializada`, { method: 'POST', body: JSON.stringify(body) })
export const askChat = (id: string, pergunta: string, sessaoId?: string) => request(`/api/v1/processos/analises/${id}/chat`, { method: 'POST', body: JSON.stringify({ pergunta, sessaoId }) })
export const dataJudSearch = (numero: string) => request(`/api/v1/datajud/processos/cnj?numeroProcesso=${encodeURIComponent(numero)}`)
