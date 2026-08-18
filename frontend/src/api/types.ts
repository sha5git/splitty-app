import type { components } from '@/api/schema'

export type UserDto = components['schemas']['UserDto']
export type GroupDto = components['schemas']['GroupDto']
export type ExpenseDto = components['schemas']['ExpenseDto']
export type ExpenseSplitDto = components['schemas']['ExpenseSplitDto']
export type BalanceDto = components['schemas']['BalanceDto']
export type GroupNetBalanceDto = components['schemas']['GroupNetBalanceDto']
export type SettlementDto = components['schemas']['SettlementDto']
export type CreateGroupRequest = components['schemas']['CreateGroupRequest']
export type UpdateGroupRequest = components['schemas']['UpdateGroupRequest']
export type AddMemberRequest = components['schemas']['AddMemberRequest']
export type CreateExpenseRequest = components['schemas']['CreateExpenseRequest']
export type UpdateExpenseRequest = components['schemas']['UpdateExpenseRequest']
export type CreateSettlementRequest = components['schemas']['CreateSettlementRequest']

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}
