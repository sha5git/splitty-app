import { format } from 'date-fns'
import { Plus } from 'lucide-react'
import { Link } from '@tanstack/react-router'

import { useAuth } from '@/auth/AuthProvider'
import type { ExpenseDto, UserDto } from '@/api/types'
import { useExpenses } from '@/api/hooks'
import { ExpenseFormDialog } from '@/components/groups/ExpenseFormDialog'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { getExpenseImpact } from '@/lib/expenseImpact'
import {
  formatExpenseDayParts,
  formatInr,
  formatMonthYear,
  parseAppDate,
} from '@/lib/format'
import { cn } from '@/lib/utils'

interface ExpensesTabProps {
  groupId: number
  members: UserDto[]
}

function groupExpensesByMonth(expenses: ExpenseDto[]) {
  const sections: { key: string; title: string; expenses: ExpenseDto[] }[] = []

  for (const expense of expenses) {
    if (!expense.date) {
      const undated = sections.find((s) => s.key === 'undated')
      if (undated) {
        undated.expenses.push(expense)
      } else {
        sections.push({ key: 'undated', title: 'Undated', expenses: [expense] })
      }
      continue
    }

    const key = format(parseAppDate(expense.date), 'yyyy-MM')
    const last = sections[sections.length - 1]
    if (last && last.key === key) {
      last.expenses.push(expense)
    } else {
      sections.push({
        key,
        title: formatMonthYear(expense.date),
        expenses: [expense],
      })
    }
  }

  const undatedIndex = sections.findIndex((s) => s.key === 'undated')
  if (undatedIndex >= 0 && undatedIndex < sections.length - 1) {
    const [undated] = sections.splice(undatedIndex, 1)
    sections.push(undated)
  }

  return sections
}

function ExpenseRow({
  groupId,
  expense,
  currentUserId,
}: {
  groupId: number
  expense: ExpenseDto
  currentUserId?: number
}) {
  const impact = getExpenseImpact(expense, currentUserId)
  const dayParts = expense.date ? formatExpenseDayParts(expense.date) : null
  const iPaid = expense.paidBy?.id === currentUserId
  const payerLabel = iPaid ? 'You' : (expense.paidBy?.name ?? 'Unknown')

  return (
    <Link
      to="/groups/$groupId/expenses/$expenseId"
      params={{ groupId: String(groupId), expenseId: String(expense.id) }}
      className="flex items-center gap-2 border-b border-border/60 py-2 last:border-b-0 hover:bg-muted/40"
    >
      <div className="w-7 shrink-0 text-center leading-tight text-muted-foreground">
        {dayParts ? (
          <>
            <p className="text-[10px] font-medium uppercase tracking-wide">{dayParts.month}</p>
            <p className="text-sm font-semibold tabular-nums text-foreground">{dayParts.day}</p>
          </>
        ) : (
          <p className="text-[10px]">—</p>
        )}
      </div>

      <UserAvatar user={expense.paidBy ?? { name: '?' }} className="h-7 w-7 shrink-0 text-[10px]" />

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium leading-snug">{expense.description}</p>
        <p className="mt-0.5 truncate text-xs text-muted-foreground">
          {payerLabel} paid {formatInr(expense.amount ?? 0)}
        </p>
      </div>

      <div className="shrink-0 text-right">
        {impact ? (
          <>
            <p
              className={cn(
                'text-[10px] font-medium leading-tight',
                impact.type === 'lent'
                  ? 'text-emerald-700 dark:text-emerald-400'
                  : 'text-destructive',
              )}
            >
              {impact.type === 'lent' ? 'you lent' : 'you borrowed'}
            </p>
            <p
              className={cn(
                'font-mono text-xs font-semibold tabular-nums',
                impact.type === 'lent'
                  ? 'text-emerald-700 dark:text-emerald-400'
                  : 'text-destructive',
              )}
            >
              {formatInr(impact.amount)}
            </p>
          </>
        ) : null}
      </div>
    </Link>
  )
}

export function ExpensesTab({ groupId, members }: ExpensesTabProps) {
  const { user } = useAuth()
  const { data: expenses, isLoading, isError, error } = useExpenses(groupId)

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-16 rounded-xl" />
        ))}
      </div>
    )
  }

  if (isError) {
    return (
      <Card className="border-destructive/30">
        <CardContent className="py-8 text-center text-sm text-destructive">
          {(error as Error).message || 'Failed to load expenses'}
        </CardContent>
      </Card>
    )
  }

  const sections = expenses?.length ? groupExpensesByMonth(expenses) : []

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <ExpenseFormDialog
          groupId={groupId}
          members={members}
          mode="create"
          trigger={
            <Button size="sm">
              <Plus className="h-4 w-4" />
              Add expense
            </Button>
          }
        />
      </div>

      {!expenses?.length ? (
        <Card className="border-dashed">
          <CardContent className="py-12 text-center">
            <p className="font-medium">No expenses yet</p>
            <p className="mt-1 text-sm text-muted-foreground">Add the first expense to start tracking.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-6">
          {sections.map((section) => (
            <section key={section.key}>
              <h3 className="sticky top-0 z-10 bg-background/95 py-1.5 text-xs font-semibold text-muted-foreground backdrop-blur supports-[backdrop-filter]:bg-background/80">
                {section.title}
              </h3>
              <div>
                {section.expenses.map((expense) => (
                  <ExpenseRow
                    key={expense.id}
                    groupId={groupId}
                    expense={expense}
                    currentUserId={user?.id}
                  />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
