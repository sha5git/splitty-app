import { Link, useNavigate } from '@tanstack/react-router'
import { ArrowLeft } from 'lucide-react'

import { useGroup } from '@/api/hooks'
import { BalancesTab } from '@/components/groups/BalancesTab'
import { ExpensesTab } from '@/components/groups/ExpensesTab'
import { MembersTab } from '@/components/groups/MembersTab'
import { SettlementsTab } from '@/components/groups/SettlementsTab'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { UserAvatar } from '@/components/UserAvatar'

interface GroupDetailPageProps {
  groupId: number
  initialTab: GroupTab
}

type GroupTab = 'expenses' | 'balances' | 'settlements' | 'members'

export function GroupDetailPage({ groupId, initialTab }: GroupDetailPageProps) {
  const navigate = useNavigate()
  const activeTab = initialTab

  const { data: group, isLoading, isError, error } = useGroup(groupId)
  const members = group?.members ?? []

  function setTab(tab: GroupTab) {
    navigate({
      to: '/groups/$groupId',
      params: { groupId: String(groupId) },
      search: { tab },
      replace: true,
    })
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-10 w-full max-w-md" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    )
  }

  if (isError || !group) {
    return (
      <div className="space-y-4 text-center py-12">
        <p className="text-destructive">{(error as Error)?.message || 'Group not found'}</p>
        <Link to="/groups" className="text-sm font-medium text-primary hover:underline">
          Back to groups
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <Link
          to="/groups"
          className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" />
          All groups
        </Link>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">{group.name}</h1>
            <div className="mt-2 flex items-center gap-2">
              <div className="flex -space-x-2">
                {members.slice(0, 5).map((member) => (
                  <UserAvatar key={member.id} user={member} className="h-7 w-7 border-2 border-background" />
                ))}
              </div>
              <span className="text-sm text-muted-foreground">
                {members.length} member{members.length === 1 ? '' : 's'}
              </span>
            </div>
          </div>
        </div>
      </div>

      <Tabs value={activeTab} onValueChange={(value) => setTab(value as GroupTab)}>
        <TabsList className="grid w-full grid-cols-4 sm:w-auto sm:inline-flex">
          <TabsTrigger value="expenses">Expenses</TabsTrigger>
          <TabsTrigger value="balances">Balances</TabsTrigger>
          <TabsTrigger value="settlements">Settle</TabsTrigger>
          <TabsTrigger value="members">Members</TabsTrigger>
        </TabsList>

        <TabsContent value="expenses">
          <ExpensesTab groupId={groupId} members={members} />
        </TabsContent>
        <TabsContent value="balances">
          <BalancesTab groupId={groupId} members={members} />
        </TabsContent>
        <TabsContent value="settlements">
          <SettlementsTab groupId={groupId} members={members} />
        </TabsContent>
        <TabsContent value="members">
          <MembersTab groupId={groupId} members={members} />
        </TabsContent>
      </Tabs>
    </div>
  )
}
