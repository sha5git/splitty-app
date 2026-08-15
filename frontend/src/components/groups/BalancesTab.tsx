import { Link } from '@tanstack/react-router'
import { ArrowRight } from 'lucide-react'

import { useAuth } from '@/auth/AuthProvider'
import type { BalanceDto, UserDto } from '@/api/types'
import { useBalances } from '@/api/hooks'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatInr } from '@/lib/format'

interface BalancesTabProps {
  groupId: number
  members: UserDto[]
}

export function BalancesTab({ groupId }: BalancesTabProps) {
  const { user } = useAuth()
  const { data: balances, isLoading, isError, error } = useBalances(groupId)

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-20 rounded-xl" />
        ))}
      </div>
    )
  }

  if (isError) {
    return (
      <Card className="border-destructive/30">
        <CardContent className="py-8 text-center text-sm text-destructive">
          {(error as Error).message || 'Failed to load balances'}
        </CardContent>
      </Card>
    )
  }

  if (!balances?.length) {
    return (
      <Card className="border-dashed">
        <CardContent className="py-12 text-center">
          <p className="font-medium">All settled up</p>
          <p className="mt-1 text-sm text-muted-foreground">No outstanding balances in this group.</p>
        </CardContent>
      </Card>
    )
  }

  const myDebts = balances.filter(
    (b) => b.fromUser?.id === user?.id || b.toUser?.id === user?.id,
  )
  const displayBalances = myDebts.length > 0 ? myDebts : balances

  return (
    <div className="space-y-4">
      {displayBalances.map((balance, index) => (
        <BalanceRow key={`${balance.fromUser?.id}-${balance.toUser?.id}-${index}`} balance={balance} currentUserId={user?.id} />
      ))}

      <div className="pt-2">
        <Button asChild variant="outline" className="w-full sm:w-auto">
          <Link to="/groups/$groupId" params={{ groupId: String(groupId) }} search={{ tab: 'settlements' }}>
            Record a settlement
          </Link>
        </Button>
      </div>
    </div>
  )
}

function BalanceRow({ balance, currentUserId }: { balance: BalanceDto; currentUserId?: number }) {
  const from = balance.fromUser
  const to = balance.toUser
  const amount = balance.amount ?? 0

  if (!from || !to) return null

  const youOwe = from.id === currentUserId
  const owedToYou = to.id === currentUserId

  return (
    <Card>
      <CardContent className="flex items-center gap-4 py-4">
        <UserAvatar user={from} className="h-10 w-10" />
        <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground" />
        <UserAvatar user={to} className="h-10 w-10" />

        <div className="ml-auto text-right">
          <p className="font-mono text-lg font-semibold tabular-nums">{formatInr(amount)}</p>
          <p className="text-xs text-muted-foreground">
            {youOwe ? (
              <>You owe {to.name}</>
            ) : owedToYou ? (
              <>{from.name} owes you</>
            ) : (
              <>
                {from.name} → {to.name}
              </>
            )}
          </p>
        </div>
      </CardContent>
    </Card>
  )
}
