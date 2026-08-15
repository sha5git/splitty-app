import { zodResolver } from '@hookform/resolvers/zod'
import { Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { useAuth } from '@/auth/AuthProvider'
import type { UserDto } from '@/api/types'
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
import { formatInr, formatRelative } from '@/lib/format'

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

export function ExpensesTab({ groupId, members }: ExpensesTabProps) {
  const deleteExpense = useDeleteExpense(groupId)
  const { data: expenses, isLoading, isError, error } = useExpenses(groupId)

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-20 rounded-xl" />
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
        <div className="space-y-3">
          {expenses.map((expense) => (
            <Card key={expense.id}>
              <CardContent className="flex items-start gap-4 py-4">
                <UserAvatar user={expense.paidBy ?? { name: '?' }} className="h-10 w-10 shrink-0" />
                <div className="min-w-0 flex-1 select-text">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-medium">{expense.description}</p>
                      <p className="mt-0.5 text-sm text-muted-foreground">
                        Paid by {expense.paidBy?.name ?? 'Unknown'}
                        {expense.date ? ` · ${formatRelative(expense.date)}` : null}
                      </p>
                    </div>
                    <p className="shrink-0 font-mono font-semibold tabular-nums">{formatInr(expense.amount ?? 0)}</p>
                  </div>
                  {expense.splits?.length ? (
                    <p className="mt-2 text-xs text-muted-foreground">
                      {formatInr((expense.amount ?? 0) / expense.splits.length)} each · {expense.splits.length}{' '}
                      people
                    </p>
                  ) : null}
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  className="shrink-0 text-muted-foreground hover:text-destructive"
                  onClick={() => expense.id && deleteExpense.mutate(expense.id)}
                  disabled={deleteExpense.isPending}
                  aria-label="Delete expense"
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
