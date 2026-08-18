import { Link, useNavigate } from '@tanstack/react-router'
import { ArrowLeft, Pencil, Trash2 } from 'lucide-react'
import { useState } from 'react'

import { useAuth } from '@/auth/AuthProvider'
import { useDeleteExpense, useExpense, useGroup } from '@/api/hooks'
import { ExpenseFormDialog } from '@/components/groups/ExpenseFormDialog'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { getExpenseImpact } from '@/lib/expenseImpact'
import { formatDate, formatInr } from '@/lib/format'
import { cn } from '@/lib/utils'

interface ExpenseDetailPageProps {
  groupId: number
  expenseId: number
}

export function ExpenseDetailPage({ groupId, expenseId }: ExpenseDetailPageProps) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data: group } = useGroup(groupId)
  const { data: expense, isLoading, isError, error } = useExpense(expenseId)
  const deleteExpense = useDeleteExpense(groupId)

  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const members = group?.members ?? []
  const impact = expense ? getExpenseImpact(expense, user?.id) : null
  const iPaid = expense?.paidBy?.id === user?.id
  const payerLabel = iPaid ? 'You' : (expense?.paidBy?.name ?? 'Unknown')

  async function handleDelete() {
    await deleteExpense.mutateAsync(expenseId)
    navigate({
      to: '/groups/$groupId',
      params: { groupId: String(groupId) },
      search: { tab: 'expenses' },
    })
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-5 w-32" />
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    )
  }

  if (isError || !expense || (expense.groupId != null && expense.groupId !== groupId)) {
    return (
      <div className="space-y-4 py-12 text-center">
        <p className="text-destructive">
          {(error as Error)?.message || 'Expense not found'}
        </p>
        <Link
          to="/groups/$groupId"
          params={{ groupId: String(groupId) }}
          search={{ tab: 'expenses' }}
          className="text-sm font-medium text-primary hover:underline"
        >
          Back to expenses
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <Link
            to="/groups/$groupId"
            params={{ groupId: String(groupId) }}
            search={{ tab: 'expenses' }}
            className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Expenses
          </Link>
          <h1 className="truncate text-2xl font-bold tracking-tight">{expense.description}</h1>
          {expense.date ? (
            <p className="mt-1 text-sm text-muted-foreground">{formatDate(expense.date)}</p>
          ) : null}
        </div>

        <div className="flex shrink-0 items-center gap-1">
          <Button variant="ghost" size="icon" aria-label="Edit expense" onClick={() => setEditOpen(true)}>
            <Pencil className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="text-muted-foreground hover:text-destructive"
            aria-label="Delete expense"
            onClick={() => setDeleteOpen(true)}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="space-y-4 py-5">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3 min-w-0">
              <UserAvatar user={expense.paidBy ?? { name: '?' }} className="h-10 w-10" />
              <div className="min-w-0">
                <p className="text-sm text-muted-foreground">Paid by</p>
                <p className="truncate font-medium">{payerLabel}</p>
              </div>
            </div>
            <p className="shrink-0 font-mono text-xl font-semibold tabular-nums">
              {formatInr(expense.amount ?? 0)}
            </p>
          </div>

          {impact ? (
            <div
              className={cn(
                'rounded-lg px-3 py-2 text-sm',
                impact.type === 'lent'
                  ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
                  : 'bg-destructive/10 text-destructive',
              )}
            >
              <span className="capitalize">{impact.type === 'lent' ? 'You lent' : 'You borrowed'}</span>{' '}
              <span className="font-mono font-semibold tabular-nums">{formatInr(impact.amount)}</span>
            </div>
          ) : null}
        </CardContent>
      </Card>

      {expense.splits?.length ? (
        <div className="space-y-3">
          <h2 className="text-sm font-semibold text-muted-foreground">Split</h2>
          <Card>
            <CardContent className="divide-y divide-border/60 py-0">
              {expense.splits.map((split) => {
                const isYou = split.user?.id === user?.id
                return (
                  <div key={split.user?.id} className="flex items-center gap-3 py-3">
                    <UserAvatar user={split.user ?? { name: '?' }} className="h-8 w-8" />
                    <p className="min-w-0 flex-1 truncate text-sm font-medium">
                      {isYou ? 'You' : (split.user?.name ?? 'Unknown')}
                    </p>
                    <p className="font-mono text-sm tabular-nums text-muted-foreground">
                      {formatInr(split.amountOwed ?? 0)}
                    </p>
                  </div>
                )
              })}
            </CardContent>
          </Card>
        </div>
      ) : null}

      <ExpenseFormDialog
        groupId={groupId}
        members={members}
        mode="edit"
        expense={expense}
        open={editOpen}
        onOpenChange={setEditOpen}
      />

      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete expense?</DialogTitle>
            <DialogDescription>
              This removes “{expense.description}” and updates balances. This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variant="outline" onClick={() => setDeleteOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteExpense.isPending}
              onClick={() => void handleDelete()}
            >
              {deleteExpense.isPending ? 'Deleting…' : 'Delete'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
