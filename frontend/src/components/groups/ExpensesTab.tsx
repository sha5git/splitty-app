import { format } from 'date-fns'
import { IndianRupee, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link } from '@tanstack/react-router'

import { useAuth } from '@/auth/AuthProvider'
import type { ExpenseDto, SettlementDto, UserDto } from '@/api/types'
import { useExpenses, useSettlements } from '@/api/hooks'
import { ExpenseFormDialog } from '@/components/groups/ExpenseFormDialog'
import { SettlementFormDialog } from '@/components/groups/SettlementFormDialog'
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

type ActivityItem =
  | { kind: 'expense'; date?: string; expense: ExpenseDto }
  | { kind: 'settlement'; date?: string; settlement: SettlementDto }

function toActivityItems(expenses: ExpenseDto[] | undefined, settlements: SettlementDto[] | undefined): ActivityItem[] {
  const items: ActivityItem[] = []
  for (const expense of expenses ?? []) {
    items.push({ kind: 'expense', date: expense.date, expense })
  }
  for (const settlement of settlements ?? []) {
    items.push({ kind: 'settlement', date: settlement.date, settlement })
  }
  items.sort((a, b) => {
    if (!a.date && !b.date) return 0
    if (!a.date) return 1
    if (!b.date) return -1
    return parseAppDate(b.date).getTime() - parseAppDate(a.date).getTime()
  })
  return items
}

function groupActivityByMonth(items: ActivityItem[]) {
  const sections: { key: string; title: string; items: ActivityItem[] }[] = []

  for (const item of items) {
    if (!item.date) {
      const undated = sections.find((s) => s.key === 'undated')
      if (undated) {
        undated.items.push(item)
      } else {
        sections.push({ key: 'undated', title: 'Undated', items: [item] })
      }
      continue
    }

    const key = format(parseAppDate(item.date), 'yyyy-MM')
    const last = sections[sections.length - 1]
    if (last && last.key === key) {
      last.items.push(item)
    } else {
      sections.push({
        key,
        title: formatMonthYear(item.date),
        items: [item],
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

function SettlementRow({
  settlement,
  currentUserId,
  onClick,
}: {
  settlement: SettlementDto
  currentUserId?: number
  onClick: () => void
}) {
  const dayParts = settlement.date ? formatExpenseDayParts(settlement.date) : null
  const fromName =
    settlement.fromUser?.id === currentUserId ? 'You' : (settlement.fromUser?.name ?? 'Unknown')
  const toName =
    settlement.toUser?.id === currentUserId ? 'You' : (settlement.toUser?.name ?? 'Unknown')

  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center gap-2 border-b border-border/60 py-2 text-left last:border-b-0 hover:bg-muted/40"
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

      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent text-primary">
        <IndianRupee className="h-3.5 w-3.5" aria-hidden />
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium leading-snug">
          {fromName} paid {toName} {formatInr(settlement.amount ?? 0)}
        </p>
      </div>
    </button>
  )
}

export function ExpensesTab({ groupId, members }: ExpensesTabProps) {
  const { user } = useAuth()
  const expensesQuery = useExpenses(groupId)
  const settlementsQuery = useSettlements(groupId)
  const [editingSettlement, setEditingSettlement] = useState<SettlementDto | undefined>()

  const toolbar = (
    <div className="flex justify-end gap-2">
      <SettlementFormDialog
        groupId={groupId}
        members={members}
        trigger={
          <Button size="sm" variant="outline">
            Settle
          </Button>
        }
      />
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
  )

  if (expensesQuery.isLoading || settlementsQuery.isLoading) {
    return (
      <div className="space-y-4">
        {toolbar}
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-16 rounded-xl" />
          ))}
        </div>
      </div>
    )
  }

  const expensesError = expensesQuery.isError
  const settlementsError = settlementsQuery.isError
  const items = toActivityItems(expensesQuery.data, settlementsQuery.data)
  const sections = items.length ? groupActivityByMonth(items) : []

  return (
    <div className="space-y-4">
      {toolbar}

      {expensesError ? (
        <Card className="border-destructive/30">
          <CardContent className="py-8 text-center text-sm text-destructive">
            {(expensesQuery.error as Error).message || 'Failed to load expenses'}
          </CardContent>
        </Card>
      ) : null}

      {settlementsError ? (
        <Card className="border-destructive/30">
          <CardContent className="py-8 text-center text-sm text-destructive">
            {(settlementsQuery.error as Error).message || 'Failed to load settlements'}
          </CardContent>
        </Card>
      ) : null}

      {!items.length && !expensesError && !settlementsError ? (
        <Card className="border-dashed">
          <CardContent className="py-12 text-center">
            <p className="font-medium">No expenses yet</p>
            <p className="mt-1 text-sm text-muted-foreground">Add the first expense to start tracking.</p>
          </CardContent>
        </Card>
      ) : null}

      {sections.length ? (
        <div className="space-y-6">
          {sections.map((section) => (
            <section key={section.key}>
              <h3 className="sticky top-0 z-10 bg-background/95 py-1.5 text-xs font-semibold text-muted-foreground backdrop-blur supports-[backdrop-filter]:bg-background/80">
                {section.title}
              </h3>
              <div>
                {section.items.map((item) =>
                  item.kind === 'expense' ? (
                    <ExpenseRow
                      key={`expense-${item.expense.id}`}
                      groupId={groupId}
                      expense={item.expense}
                      currentUserId={user?.id}
                    />
                  ) : (
                    <SettlementRow
                      key={`settlement-${item.settlement.id}`}
                      settlement={item.settlement}
                      currentUserId={user?.id}
                      onClick={() => setEditingSettlement(item.settlement)}
                    />
                  ),
                )}
              </div>
            </section>
          ))}
        </div>
      ) : null}

      <SettlementFormDialog
        groupId={groupId}
        members={members}
        settlement={editingSettlement}
        open={editingSettlement != null}
        onOpenChange={(next) => {
          if (!next) setEditingSettlement(undefined)
        }}
      />
    </div>
  )
}
