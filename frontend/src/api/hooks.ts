import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { api } from '@/api/client'
import { useLiveSyncConnected } from '@/api/live-sync-context'
import type {
  AddMemberRequest,
  CreateExpenseRequest,
  CreateGroupRequest,
  CreateSettlementRequest,
  GroupChangeEvent,
  UpdateExpenseRequest,
  UpdateGroupRequest,
  UpdateSettlementRequest,
} from '@/api/types'

export const queryKeys = {
  me: ['user', 'me'] as const,
  groups: ['groups'] as const,
  group: (id: number) => ['groups', id] as const,
  expenses: (groupId: number) => ['groups', groupId, 'expenses'] as const,
  expense: (expenseId: number) => ['expenses', expenseId] as const,
  balances: (groupId: number) => ['groups', groupId, 'balances'] as const,
  netBalance: (groupId: number) => ['groups', groupId, 'net-balance'] as const,
  settlements: (groupId: number) => ['groups', groupId, 'settlements'] as const,
}

/** Slow poll only while the SSE stream is down. */
const SSE_FALLBACK_MS = 5 * 60 * 1000

function useLiveRefetchInterval() {
  const connected = useLiveSyncConnected()
  return connected ? false : SSE_FALLBACK_MS
}

export function useCurrentUser(enabled = true) {
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: api.getMe,
    enabled,
    retry: false,
  })
}

export function useGroups() {
  const refetchInterval = useLiveRefetchInterval()
  return useQuery({
    queryKey: queryKeys.groups,
    queryFn: api.getGroups,
    refetchInterval,
  })
}

export function useGroup(id: number) {
  const refetchInterval = useLiveRefetchInterval()
  return useQuery({
    queryKey: queryKeys.group(id),
    queryFn: () => api.getGroup(id),
    refetchInterval,
  })
}

export function useExpenses(groupId: number) {
  const refetchInterval = useLiveRefetchInterval()
  return useQuery({
    queryKey: queryKeys.expenses(groupId),
    queryFn: () => api.getExpenses(groupId),
    refetchInterval,
  })
}

export function useExpense(expenseId: number) {
  const refetchInterval = useLiveRefetchInterval()
  return useQuery({
    queryKey: queryKeys.expense(expenseId),
    queryFn: () => api.getExpense(expenseId),
    enabled: Number.isFinite(expenseId) && expenseId > 0,
    refetchInterval,
  })
}

export function useBalances(groupId: number) {
  const refetchInterval = useLiveRefetchInterval()
  return useQuery({
    queryKey: queryKeys.balances(groupId),
    queryFn: () => api.getBalances(groupId),
    refetchInterval,
  })
}

export function useGroupNetBalance(groupId: number) {
  const refetchInterval = useLiveRefetchInterval()
  return useQuery({
    queryKey: queryKeys.netBalance(groupId),
    queryFn: () => api.getGroupNetBalance(groupId),
    refetchInterval,
  })
}

export function useSettlements(groupId: number) {
  const refetchInterval = useLiveRefetchInterval()
  return useQuery({
    queryKey: queryKeys.settlements(groupId),
    queryFn: () => api.getSettlements(groupId),
    refetchInterval,
  })
}

export function applyGroupChangeEvent(queryClient: QueryClient, event: GroupChangeEvent) {
  if (event.groupId == null) {
    return
  }

  invalidateGroup(queryClient, event.groupId)

  if (event.type === 'EXPENSE_DELETED' && event.entityId != null) {
    queryClient.removeQueries({ queryKey: queryKeys.expense(event.entityId) })
  } else if (
    (event.type === 'EXPENSE_UPDATED' || event.type === 'EXPENSE_CREATED') &&
    event.entityId != null
  ) {
    queryClient.invalidateQueries({ queryKey: queryKeys.expense(event.entityId) })
  }
}

function invalidateGroup(queryClient: QueryClient, groupId: number) {
  queryClient.invalidateQueries({ queryKey: queryKeys.group(groupId) })
  queryClient.invalidateQueries({ queryKey: queryKeys.expenses(groupId) })
  queryClient.invalidateQueries({ queryKey: queryKeys.balances(groupId) })
  queryClient.invalidateQueries({ queryKey: queryKeys.netBalance(groupId) })
  queryClient.invalidateQueries({ queryKey: queryKeys.settlements(groupId) })
  queryClient.invalidateQueries({ queryKey: queryKeys.groups })
}

export function useCreateGroup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateGroupRequest) => api.createGroup(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.groups })
      toast.success('Group created')
    },
    onError: (error: Error) => toast.error(error.message),
  })
}

export function useUpdateGroup(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateGroupRequest) => api.updateGroup(groupId, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.group(groupId), updated)
      queryClient.invalidateQueries({ queryKey: queryKeys.groups })
      toast.success('Group updated')
    },
    onError: (error: Error) => toast.error(error.message),
  })
}

export function useAddMember(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: AddMemberRequest) => api.addMember(groupId, body),
    onSuccess: () => {
      invalidateGroup(queryClient, groupId)
      toast.success('Member added')
    },
    onError: (error: Error) => toast.error(error.message),
  })
}

export function useRemoveMember(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: number) => api.removeMember(groupId, userId),
    onSuccess: () => {
      invalidateGroup(queryClient, groupId)
      toast.success('Member removed')
    },
    onError: (error: Error) => toast.error(error.message),
  })
}

export function useCreateExpense(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateExpenseRequest) => api.createExpense(groupId, body),
    onSuccess: () => {
      invalidateGroup(queryClient, groupId)
      toast.success('Expense added')
    },
    onError: (error: Error) => toast.error(error.message),
  })
}

export function useUpdateExpense(groupId: number, expenseId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateExpenseRequest) => api.updateExpense(expenseId, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.expense(expenseId), updated)
      invalidateGroup(queryClient, groupId)
      toast.success('Expense updated')
    },
    onError: (error: Error) => toast.error(error.message),
  })
}

export function useDeleteExpense(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (expenseId: number) => api.deleteExpense(expenseId),
    onSuccess: (_data, expenseId) => {
      queryClient.removeQueries({ queryKey: queryKeys.expense(expenseId) })
      invalidateGroup(queryClient, groupId)
      toast.success('Expense deleted')
    },
    onError: (error: Error) => toast.error(error.message),
  })
}

export function useCreateSettlement(groupId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateSettlementRequest) => api.createSettlement(groupId, body),
    onSuccess: () => {
      invalidateSettlementRelated(queryClient, groupId)
      toast.success('Settlement recorded')
    },
    onError: (error: Error) => toast.error(error.message),
  })
}

export function useUpdateSettlement(groupId: number, settlementId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateSettlementRequest) => api.updateSettlement(settlementId, body),
    onSuccess: () => {
      invalidateSettlementRelated(queryClient, groupId)
      toast.success('Settlement updated')
    },
    onError: (error: Error) => toast.error(error.message),
  })
}

function invalidateSettlementRelated(queryClient: QueryClient, groupId: number) {
  queryClient.invalidateQueries({ queryKey: queryKeys.settlements(groupId) })
  queryClient.invalidateQueries({ queryKey: queryKeys.balances(groupId) })
  queryClient.invalidateQueries({ queryKey: queryKeys.netBalance(groupId) })
  queryClient.invalidateQueries({ queryKey: queryKeys.group(groupId) })
  queryClient.invalidateQueries({ queryKey: queryKeys.groups })
}
