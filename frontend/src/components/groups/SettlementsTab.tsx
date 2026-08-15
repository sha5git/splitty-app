import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import type { UserDto } from '@/api/types'
import { useCreateSettlement, useSettlements } from '@/api/hooks'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate, formatInr, formatRelative } from '@/lib/format'

const settlementSchema = z.object({
  fromUserId: z.coerce.number(),
  toUserId: z.coerce.number(),
  amount: z.coerce.number().positive('Amount must be greater than zero'),
})

type SettlementForm = z.infer<typeof settlementSchema>

interface SettlementsTabProps {
  groupId: number
  members: UserDto[]
}

export function SettlementsTab({ groupId, members }: SettlementsTabProps) {
  const createSettlement = useCreateSettlement(groupId)
  const { data: settlements, isLoading, isError, error } = useSettlements(groupId)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<SettlementForm>({
    resolver: zodResolver(settlementSchema),
    defaultValues: {
      fromUserId: members[0]?.id,
      toUserId: members[1]?.id ?? members[0]?.id,
    },
  })

  const fromUserId = watch('fromUserId')
  const toUserId = watch('toUserId')

  async function onSubmit(values: SettlementForm) {
    if (values.fromUserId === values.toUserId) return
    await createSettlement.mutateAsync({
      ...values,
      // Omit date — backend uses LocalDateTime.now() (avoids UTC/IST skew)
    })
    reset({
      fromUserId: values.fromUserId,
      toUserId: values.toUserId,
      amount: undefined,
    })
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardContent className="space-y-4 py-5">
          <div>
            <h3 className="font-medium">Record a payment</h3>
            <p className="text-sm text-muted-foreground">Log when someone pays another member back.</p>
          </div>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="fromUserId">From</Label>
                <select
                  id="fromUserId"
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
                <Label htmlFor="toUserId">To</Label>
                <select
                  id="toUserId"
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

            {fromUserId === toUserId ? (
              <p className="text-sm text-destructive">Payer and recipient must be different people.</p>
            ) : null}

            <div className="space-y-2">
              <Label htmlFor="settlement-amount">Amount (₹)</Label>
              <Input id="settlement-amount" type="number" step="0.01" min="0" placeholder="500.00" {...register('amount')} />
              {errors.amount ? <p className="text-sm text-destructive">{errors.amount.message}</p> : null}
            </div>

            <Button
              type="submit"
              disabled={isSubmitting || createSettlement.isPending || fromUserId === toUserId}
            >
              {isSubmitting || createSettlement.isPending ? 'Recording…' : 'Record settlement'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <div className="space-y-3">
        <h3 className="text-sm font-medium text-muted-foreground">History</h3>

        {isLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-16 rounded-xl" />
            ))}
          </div>
        ) : null}

        {isError ? (
          <Card className="border-destructive/30">
            <CardContent className="py-8 text-center text-sm text-destructive">
              {(error as Error).message || 'Failed to load settlements'}
            </CardContent>
          </Card>
        ) : null}

        {!isLoading && !settlements?.length ? (
          <Card className="border-dashed">
            <CardContent className="py-10 text-center text-sm text-muted-foreground">
              No settlements recorded yet.
            </CardContent>
          </Card>
        ) : null}

        {settlements?.map((settlement) => (
          <Card key={settlement.id}>
            <CardContent className="flex items-center gap-3 py-4">
              {settlement.fromUser ? <UserAvatar user={settlement.fromUser} className="h-9 w-9" /> : null}
              <ArrowRight className="h-4 w-4 text-muted-foreground" />
              {settlement.toUser ? <UserAvatar user={settlement.toUser} className="h-9 w-9" /> : null}
              <div className="ml-auto text-right">
                <p className="font-mono font-semibold tabular-nums">{formatInr(settlement.amount ?? 0)}</p>
                <p className="text-xs text-muted-foreground">
                  {settlement.date ? formatRelative(settlement.date) : formatDate(new Date().toISOString())}
                </p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
