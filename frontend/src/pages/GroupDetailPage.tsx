import { Link, useNavigate } from '@tanstack/react-router'
import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowLeft, Pencil } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { useGroup, useUpdateGroup } from '@/api/hooks'
import { BalancesTab } from '@/components/groups/BalancesTab'
import { ExpensesTab } from '@/components/groups/ExpensesTab'
import { MembersTab } from '@/components/groups/MembersTab'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { UserAvatar } from '@/components/UserAvatar'

interface GroupDetailPageProps {
  groupId: number
  initialTab: GroupTab
}

type GroupTab = 'expenses' | 'balances' | 'members'

const renameGroupSchema = z.object({
  name: z.string().min(2, 'Group name must be at least 2 characters').max(80),
})

type RenameGroupForm = z.infer<typeof renameGroupSchema>

function EditGroupNameDialog({
  groupId,
  currentName,
  open,
  onOpenChange,
}: {
  groupId: number
  currentName: string
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const updateGroup = useUpdateGroup(groupId)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<RenameGroupForm>({
    resolver: zodResolver(renameGroupSchema),
    defaultValues: { name: currentName },
  })

  useEffect(() => {
    if (open) reset({ name: currentName })
  }, [open, currentName, reset])

  async function onSubmit(values: RenameGroupForm) {
    await updateGroup.mutateAsync({ name: values.name.trim() })
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit group name</DialogTitle>
          <DialogDescription>Choose a clear name so everyone recognizes this group.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="edit-group-name">Group name</Label>
            <Input id="edit-group-name" autoFocus {...register('name')} />
            {errors.name ? <p className="text-sm text-destructive">{errors.name.message}</p> : null}
          </div>
          <Button type="submit" className="w-full" disabled={isSubmitting || updateGroup.isPending}>
            {isSubmitting || updateGroup.isPending ? 'Saving…' : 'Save name'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export function GroupDetailPage({ groupId, initialTab }: GroupDetailPageProps) {
  const navigate = useNavigate()
  const activeTab = initialTab
  const [editNameOpen, setEditNameOpen] = useState(false)

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
            <div className="flex items-center gap-1.5">
              <h1 className="truncate text-2xl font-bold tracking-tight">{group.name}</h1>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 shrink-0 text-muted-foreground"
                aria-label="Edit group name"
                onClick={() => setEditNameOpen(true)}
              >
                <Pencil className="h-4 w-4" />
              </Button>
            </div>
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
        <TabsList className="grid w-full grid-cols-3 sm:w-auto sm:inline-flex">
          <TabsTrigger value="expenses">Expenses</TabsTrigger>
          <TabsTrigger value="balances">Balances</TabsTrigger>
          <TabsTrigger value="members">Members</TabsTrigger>
        </TabsList>

        <TabsContent value="expenses">
          <ExpensesTab groupId={groupId} members={members} />
        </TabsContent>
        <TabsContent value="balances">
          <BalancesTab groupId={groupId} members={members} />
        </TabsContent>
        <TabsContent value="members">
          <MembersTab groupId={groupId} members={members} />
        </TabsContent>
      </Tabs>

      <EditGroupNameDialog
        groupId={groupId}
        currentName={group.name ?? ''}
        open={editNameOpen}
        onOpenChange={setEditNameOpen}
      />
    </div>
  )
}
