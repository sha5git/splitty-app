import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { useAuth } from '@/auth/AuthProvider'
import type { ExpenseDto, UserDto } from '@/api/types'
import { useCreateExpense, useUpdateExpense } from '@/api/hooks'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
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

const expenseSchema = z.object({
  description: z.string().min(1, 'Description is required').max(200),
  amount: z.coerce.number().positive('Amount must be greater than zero'),
  paidById: z.coerce.number().optional(),
})

type ExpenseFormValues = z.infer<typeof expenseSchema>

interface ExpenseFormDialogProps {
  groupId: number
  members: UserDto[]
  mode: 'create' | 'edit'
  expense?: ExpenseDto
  open?: boolean
  onOpenChange?: (open: boolean) => void
  trigger?: ReactNode
}

export function ExpenseFormDialog({
  groupId,
  members,
  mode,
  expense,
  open: controlledOpen,
  onOpenChange,
  trigger,
}: ExpenseFormDialogProps) {
  const { user } = useAuth()
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false)
  const open = controlledOpen ?? uncontrolledOpen
  const setOpen = onOpenChange ?? setUncontrolledOpen

  const createExpense = useCreateExpense(groupId)
  const updateExpense = useUpdateExpense(groupId, expense?.id ?? 0)

  const [splitWith, setSplitWith] = useState<number[]>(() => defaultSplitIds(members, mode, expense))

  function emptyFormValues(): ExpenseFormValues {
    return {
      description: '',
      amount: '' as unknown as number,
      paidById: user?.id,
    }
  }

  function editFormValues(): ExpenseFormValues {
    return {
      description: expense?.description ?? '',
      amount: expense?.amount ?? ('' as unknown as number),
      paidById: expense?.paidBy?.id ?? user?.id,
    }
  }

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ExpenseFormValues>({
    resolver: zodResolver(expenseSchema),
    defaultValues: mode === 'edit' ? editFormValues() : emptyFormValues(),
  })

  function resetForm() {
    if (mode === 'edit') {
      reset(editFormValues())
      setSplitWith(defaultSplitIds(members, mode, expense))
    } else {
      reset(emptyFormValues())
      setSplitWith(members.map((m) => m.id!).filter(Boolean))
    }
  }

  useEffect(() => {
    if (open) resetForm()
  }, [open, mode, expense?.id])

  function toggleMember(memberId: number) {
    setSplitWith((current) =>
      current.includes(memberId) ? current.filter((id) => id !== memberId) : [...current, memberId],
    )
  }

  async function onSubmit(values: ExpenseFormValues) {
    if (splitWith.length === 0) return

    const allSelected = splitWith.length === members.length
    const body = {
      description: values.description,
      amount: values.amount,
      paidById: values.paidById ?? user?.id,
      splitWithUserIds: allSelected ? undefined : splitWith,
    }

    if (mode === 'edit') {
      if (!expense?.id) return
      await updateExpense.mutateAsync(body)
    } else {
      await createExpense.mutateAsync(body)
    }

    resetForm()
    setOpen(false)
  }

  const pending = mode === 'edit' ? updateExpense.isPending : createExpense.isPending
  const title = mode === 'edit' ? 'Edit expense' : 'Add expense'
  const submitLabel = mode === 'edit' ? 'Save changes' : 'Add expense'
  const pendingLabel = mode === 'edit' ? 'Saving…' : 'Adding…'

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) resetForm()
      }}
    >
      {trigger ? <DialogTrigger asChild>{trigger}</DialogTrigger> : null}
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
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

          <Button type="submit" className="w-full" disabled={isSubmitting || pending || splitWith.length === 0}>
            {isSubmitting || pending ? pendingLabel : submitLabel}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function defaultSplitIds(members: UserDto[], mode: 'create' | 'edit', expense?: ExpenseDto) {
  if (mode === 'edit' && expense?.splits?.length) {
    return expense.splits.map((s) => s.user?.id).filter((id): id is number => id != null)
  }
  return members.map((m) => m.id!).filter(Boolean)
}
