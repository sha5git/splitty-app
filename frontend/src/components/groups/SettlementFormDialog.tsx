import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import type { SettlementDto, UserDto } from '@/api/types'
import { useCreateSettlement, useUpdateSettlement } from '@/api/hooks'
import { Button } from '@/components/ui/button'
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

const settlementSchema = z.object({
  fromUserId: z.coerce.number(),
  toUserId: z.coerce.number(),
  amount: z.coerce.number().positive('Amount must be greater than zero'),
})

type SettlementForm = z.infer<typeof settlementSchema>

export interface SettlementFormDefaults {
  fromUserId?: number
  toUserId?: number
  amount?: number
}

interface SettlementFormDialogProps {
  groupId: number
  members: UserDto[]
  settlement?: SettlementDto
  defaults?: SettlementFormDefaults
  open?: boolean
  onOpenChange?: (open: boolean) => void
  trigger?: ReactNode
}

export function SettlementFormDialog({
  groupId,
  members,
  settlement,
  defaults,
  open: controlledOpen,
  onOpenChange,
  trigger,
}: SettlementFormDialogProps) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false)
  const open = controlledOpen ?? uncontrolledOpen
  const setOpen = onOpenChange ?? setUncontrolledOpen
  const isEdit = settlement?.id != null

  const createSettlement = useCreateSettlement(groupId)
  const updateSettlement = useUpdateSettlement(groupId, settlement?.id ?? 0)

  function formValues(): SettlementForm {
    return {
      fromUserId: settlement?.fromUser?.id ?? defaults?.fromUserId ?? members[0]?.id ?? 0,
      toUserId: settlement?.toUser?.id ?? defaults?.toUserId ?? members[1]?.id ?? members[0]?.id ?? 0,
      amount: settlement?.amount ?? defaults?.amount ?? ('' as unknown as number),
    }
  }

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<SettlementForm>({
    resolver: zodResolver(settlementSchema),
    defaultValues: formValues(),
  })

  useEffect(() => {
    if (!open) return
    reset(formValues())
  }, [
    open,
    settlement?.id,
    settlement?.fromUser?.id,
    settlement?.toUser?.id,
    settlement?.amount,
    defaults?.fromUserId,
    defaults?.toUserId,
    defaults?.amount,
    members,
    reset,
  ])

  const fromUserId = watch('fromUserId')
  const toUserId = watch('toUserId')

  async function onSubmit(values: SettlementForm) {
    if (values.fromUserId === values.toUserId) return
    if (isEdit) {
      if (!settlement?.id) return
      await updateSettlement.mutateAsync(values)
    } else {
      await createSettlement.mutateAsync(values)
    }
    setOpen(false)
  }

  const pending = isSubmitting || (isEdit ? updateSettlement.isPending : createSettlement.isPending)
  const samePerson = fromUserId === toUserId
  const fieldPrefix = isEdit ? 'edit-settlement' : 'record-settlement'

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      {trigger ? <DialogTrigger asChild>{trigger}</DialogTrigger> : null}
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Edit settlement' : 'Record a payment'}</DialogTitle>
          <DialogDescription>
            {isEdit
              ? 'Update who paid whom and the amount.'
              : 'Log when someone pays another member back.'}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor={`${fieldPrefix}-fromUserId`}>From</Label>
              <select
                id={`${fieldPrefix}-fromUserId`}
                className="flex h-10 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm"
                {...register('fromUserId')}
              >
                {members.map((member) => (
                  <option key={member.id} value={member.id}>
                    {member.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor={`${fieldPrefix}-toUserId`}>To</Label>
              <select
                id={`${fieldPrefix}-toUserId`}
                className="flex h-10 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm"
                {...register('toUserId')}
              >
                {members.map((member) => (
                  <option key={member.id} value={member.id}>
                    {member.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {samePerson ? (
            <p className="text-sm text-destructive">Payer and recipient must be different people.</p>
          ) : null}

          <div className="space-y-2">
            <Label htmlFor={`${fieldPrefix}-amount`}>Amount (₹)</Label>
            <Input
              id={`${fieldPrefix}-amount`}
              type="number"
              step="0.01"
              min="0"
              placeholder="500.00"
              {...register('amount')}
            />
            {errors.amount ? <p className="text-sm text-destructive">{errors.amount.message}</p> : null}
          </div>

          <Button type="submit" className="w-full" disabled={pending || samePerson}>
            {pending ? (isEdit ? 'Saving…' : 'Recording…') : isEdit ? 'Save changes' : 'Record settlement'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
