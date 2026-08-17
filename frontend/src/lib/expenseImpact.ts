import type { ExpenseDto } from '@/api/types'

export type ExpenseImpact =
  | { type: 'lent'; amount: number }
  | { type: 'borrowed'; amount: number }

/** Personal balance change from one expense (Splitwise-style). */
export function getExpenseImpact(expense: ExpenseDto, currentUserId?: number): ExpenseImpact | null {
  if (currentUserId == null) return null

  const total = expense.amount ?? 0
  const myShare =
    expense.splits?.find((split) => split.user?.id === currentUserId)?.amountOwed ?? 0
  const iPaid = expense.paidBy?.id === currentUserId

  if (iPaid) {
    const lent = total - myShare
    if (lent <= 0) return null
    return { type: 'lent', amount: lent }
  }

  if (myShare > 0) {
    return { type: 'borrowed', amount: myShare }
  }

  return null
}
