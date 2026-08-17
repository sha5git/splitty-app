import { zodResolver } from '@hookform/resolvers/zod'
import { format } from 'date-fns'
import { Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { useAuth } from '@/auth/AuthProvider'
import type { ExpenseDto, UserDto } from '@/api/types'
import { useCreateExpense, useDeleteExpense, useExpenses } from '@/api/hooks'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { getExpenseImpact } from '@/lib/expenseImpact'
import {
  formatExpenseDayParts,
  formatInr,
  formatMonthYear,
  parseAppDate,
} from '@/lib/format'
import { cn } from '@/lib/utils'

const expenseSchema = z.object({
  description: z.string().min(1, 'Description is required').max(200),
  amount: z.coerce.number().positive('Amount must be greater than zero'),
  paidById: z.coerce.number().optional(),
})

type ExpenseForm = z.infer<typeof expenseSchema>

interface ExpensesTabProps {
  groupId: number
  members: UserDto[]
}

function AddExpenseDialog({ groupId, members }: ExpensesTabProps) {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const [splitWith, setSplitWith] = useState<number[]>(members.map((m) => m.id!))
  const createExpense = useCreateExpense(groupId)

  function emptyFormValues(): ExpenseForm {
    return {
      description: '',
      amount: '' as unknown as number,
      paidById: user?.id,
    }
  }

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ExpenseForm>({
    resolver: zodResolver(expenseSchema),
    defaultValues: emptyFormValues(),
  })

  function resetForm() {
    reset(emptyFormValues())
    setSplitWith(members.map((m) => m.id!))
  }

  function toggleMember(memberId: number) {
    setSplitWith((current) =>
      current.includes(memberId) ? current.filter((id) => id !== memberId) : [...current, memberId],
    )
  }

  async function onSubmit(values: ExpenseForm) {
    if (splitWith.length === 0) return

    const allSelected = splitWith.length === members.length
    await createExpense.mutateAsync({
      description: values.description,
      amount: values.amount,
      paidById: values.paidById ?? user?.id,
      // Omit date — backend uses LocalDateTime.now() (avoids UTC/IST skew)
      splitWithUserIds: allSelected ? undefined : splitWith,
    })
    resetForm()
    setOpen(false)
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) resetForm()
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus className="h-4 w-4" />
          Add expense
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Add expense</DialogTitle>
          <DialogDescription>Split equally among selected members.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="description">Description</Label>
            <Input id="description" placeholder="Dinner at Mainland China" {...register('description')} />
            {errors.description ? <p className="text-sm text-destructive">{errors.description.message}</p> : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="amount">Amount (₹)</Label>
            <Input id="amount" type="number" step="0.01" min="0" placeholder="1250.00" {...register('amount')} />
            {errors.amount ? <p className="text-sm text-destructive">{errors.amount.message}</p> : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="paidById">Paid by</Label>
            <select
              id="paidById"
              className="flex h-10 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm"
              {...register('paidById')}
            >
              {members.map((member) => (
                <option key={member.id} value={member.id}>
                  {member.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label>Split with</Label>
            <div className="space-y-2 rounded-lg border p-3">
              {members.map((member) => (
                <label key={member.id} className="flex cursor-pointer items-center gap-3">
                  <Checkbox
                    checked={splitWith.includes(member.id!)}
                    onCheckedChange={() => toggleMember(member.id!)}
                  />
                  <UserAvatar user={member} className="h-7 w-7" />
                  <span className="text-sm">{member.name}</span>
                </label>
              ))}
            </div>
            {splitWith.length === 0 ? (
              <p className="text-sm text-destructive">Select at least one member</p>
            ) : null}
          </div>

          <Button
            type="submit"
            className="w-full"
            disabled={isSubmitting || createExpense.isPending || splitWith.length === 0}
          >
            {isSubmitting || createExpense.isPending ? 'Adding…' : 'Add expense'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
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

  // Keep Undated at the bottom even if it appeared mid-list
  const undatedIndex = sections.findIndex((s) => s.key === 'undated')
  if (undatedIndex >= 0 && undatedIndex < sections.length - 1) {
    const [undated] = sections.splice(undatedIndex, 1)
    sections.push(undated)
  }

  return sections
}

function ExpenseRow({
  expense,
  currentUserId,
  onDelete,
  deleting,
}: {
  expense: ExpenseDto
  currentUserId?: number
  onDelete: () => void
  deleting: boolean
}) {
  const impact = getExpenseImpact(expense, currentUserId)
  const dayParts = expense.date ? formatExpenseDayParts(expense.date) : null
  const iPaid = expense.paidBy?.id === currentUserId
  const payerLabel = iPaid ? 'You' : (expense.paidBy?.name ?? 'Unknown')

  return (
    <div className="flex items-center gap-2 border-b border-border/60 py-2 last:border-b-0">
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

      <div className="min-w-0 flex-1 select-text">
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

      <Button
        variant="ghost"
        size="icon"
        className="h-7 w-7 shrink-0 text-muted-foreground hover:text-destructive"
        onClick={onDelete}
        disabled={deleting}
        aria-label="Delete expense"
      >
        <Trash2 className="h-3.5 w-3.5" />
      </Button>
    </div>
  )
}

export function ExpensesTab({ groupId, members }: ExpensesTabProps) {
  const { user } = useAuth()
  const deleteExpense = useDeleteExpense(groupId)
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
        <AddExpenseDialog groupId={groupId} members={members} />
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
                    expense={expense}
                    currentUserId={user?.id}
                    onDelete={() => expense.id && deleteExpense.mutate(expense.id)}
                    deleting={deleteExpense.isPending}
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
