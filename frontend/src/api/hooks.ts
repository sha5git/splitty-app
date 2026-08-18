import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { api } from '@/api/client'
import type {
  AddMemberRequest,
  CreateExpenseRequest,
  CreateGroupRequest,
  CreateSettlementRequest,
  UpdateExpenseRequest,
  UpdateGroupRequest,
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

export function useCurrentUser(enabled = true) {
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: api.getMe,
    enabled,
    retry: false,
  })
}

/** Poll while the tab is focused so Alice's changes show up for Bob without reload. */
const LIVE_REFETCH_MS = 30_000

export function useGroups() {
  return useQuery({
    queryKey: queryKeys.groups,
    queryFn: api.getGroups,
    refetchInterval: LIVE_REFETCH_MS,
  })
}

export function useGroup(id: number) {
  return useQuery({
    queryKey: queryKeys.group(id),
    queryFn: () => api.getGroup(id),
    refetchInterval: LIVE_REFETCH_MS,
  })
}

export function useExpenses(groupId: number) {
  return useQuery({
    queryKey: queryKeys.expenses(groupId),
    queryFn: () => api.getExpenses(groupId),
    refetchInterval: LIVE_REFETCH_MS,
  })
}

export function useExpense(expenseId: number) {
  return useQuery({
    queryKey: queryKeys.expense(expenseId),
    queryFn: () => api.getExpense(expenseId),
    enabled: Number.isFinite(expenseId) && expenseId > 0,
    refetchInterval: LIVE_REFETCH_MS,
  })
}

export function useBalances(groupId: number) {
  return useQuery({
    queryKey: queryKeys.balances(groupId),
    queryFn: () => api.getBalances(groupId),
    refetchInterval: LIVE_REFETCH_MS,
  })
}

export function useGroupNetBalance(groupId: number) {
  return useQuery({
    queryKey: queryKeys.netBalance(groupId),
    queryFn: () => api.getGroupNetBalance(groupId),
    refetchInterval: LIVE_REFETCH_MS,
  })
}

export function useSettlements(groupId: number) {
  return useQuery({
    queryKey: queryKeys.settlements(groupId),
    queryFn: () => api.getSettlements(groupId),
    refetchInterval: LIVE_REFETCH_MS,
  })
}

function invalidateGroup(queryClient: ReturnType<typeof useQueryClient>, groupId: number) {
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
      invalidateGroup(queryClient, groupId)
      toast.success('Settlement recorded')
    },
    onError: (error: Error) => toast.error(error.message),
  })
}
