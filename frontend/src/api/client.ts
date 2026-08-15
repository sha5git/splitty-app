import { ApiError } from '@/api/types'
import { getIdToken } from '@/auth/firebase'

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

async function parseError(response: Response): Promise<string> {
  const text = await response.text()
  if (!text) return response.statusText || 'Request failed'

  try {
    const json = JSON.parse(text) as { error?: string; message?: string }
    return json.error ?? json.message ?? text
  } catch {
    return text
  }
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = await getIdToken()
  if (!token) {
    throw new ApiError(401, 'Not authenticated')
  }

  const headers = new Headers(options.headers)
  headers.set('Authorization', `Bearer ${token}`)
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers,
  })

  if (!response.ok) {
    throw new ApiError(response.status, await parseError(response))
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type')
  if (contentType?.includes('application/json')) {
    return response.json() as Promise<T>
  }

  return undefined as T
}

export const api = {
  getMe: () => apiFetch<import('@/api/types').UserDto>('/api/users/me'),
  getGroups: () => apiFetch<import('@/api/types').GroupDto[]>('/api/groups'),
  getGroup: (id: number) => apiFetch<import('@/api/types').GroupDto>(`/api/groups/${id}`),
  createGroup: (body: import('@/api/types').CreateGroupRequest) =>
    apiFetch<import('@/api/types').GroupDto>('/api/groups', { method: 'POST', body: JSON.stringify(body) }),
  addMember: (groupId: number, body: import('@/api/types').AddMemberRequest) =>
    apiFetch<void>(`/api/groups/${groupId}/members`, { method: 'POST', body: JSON.stringify(body) }),
  removeMember: (groupId: number, userId: number) =>
    apiFetch<void>(`/api/groups/${groupId}/members/${userId}`, { method: 'DELETE' }),
  getExpenses: (groupId: number) =>
    apiFetch<import('@/api/types').ExpenseDto[]>(`/api/groups/${groupId}/expenses`),
  createExpense: (groupId: number, body: import('@/api/types').CreateExpenseRequest) =>
    apiFetch<import('@/api/types').ExpenseDto>(`/api/groups/${groupId}/expenses`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  deleteExpense: (expenseId: number) =>
    apiFetch<void>(`/api/expenses/${expenseId}`, { method: 'DELETE' }),
  getBalances: (groupId: number) =>
    apiFetch<import('@/api/types').BalanceDto[]>(`/api/groups/${groupId}/balances`),
  getGroupNetBalance: (groupId: number) =>
    apiFetch<import('@/api/types').GroupNetBalanceDto>(`/api/groups/${groupId}/net-balance`),
  getSettlements: (groupId: number) =>
    apiFetch<import('@/api/types').SettlementDto[]>(`/api/groups/${groupId}/settlements`),
  createSettlement: (groupId: number, body: import('@/api/types').CreateSettlementRequest) =>
    apiFetch<import('@/api/types').SettlementDto>(`/api/groups/${groupId}/settlements`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
}
