import { Link } from '@tanstack/react-router'
import { ArrowRight, Plus, Users } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'

import { useCreateGroup, useGroupNetBalance, useGroups } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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
import { UserAvatar } from '@/components/UserAvatar'
import { formatInr, formatRelative } from '@/lib/format'

const createGroupSchema = z.object({
  name: z.string().min(2, 'Group name must be at least 2 characters').max(80),
})

type CreateGroupForm = z.infer<typeof createGroupSchema>

function CreateGroupDialog() {
  const [open, setOpen] = useState(false)
  const createGroup = useCreateGroup()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateGroupForm>({
    resolver: zodResolver(createGroupSchema),
  })

  async function onSubmit(values: CreateGroupForm) {
    await createGroup.mutateAsync(values)
    reset()
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="h-4 w-4" />
          New group
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create a group</DialogTitle>
          <DialogDescription>Trip, flatmates, dinner club — give your group a name.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="group-name">Group name</Label>
            <Input id="group-name" placeholder="Weekend in Goa" autoFocus {...register('name')} />
            {errors.name ? <p className="text-sm text-destructive">{errors.name.message}</p> : null}
          </div>
          <Button type="submit" className="w-full" disabled={isSubmitting || createGroup.isPending}>
            {isSubmitting || createGroup.isPending ? 'Creating…' : 'Create group'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function GroupsSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {Array.from({ length: 4 }).map((_, i) => (
        <Skeleton key={i} className="h-36 rounded-xl" />
      ))}
    </div>
  )
}

function GroupNetBalance({ groupId }: { groupId: number }) {
  const { data: summary, isLoading } = useGroupNetBalance(groupId)

  if (isLoading) {
    return (
      <div className="space-y-2 rounded-lg bg-muted/50 px-4 py-3">
        <Skeleton className="h-3 w-16" />
        <Skeleton className="h-8 w-32" />
      </div>
    )
  }

  const net = summary?.netBalance ?? 0

  if (net === 0) {
    return (
      <div className="rounded-lg bg-muted/50 px-4 py-3">
        <p className="text-sm text-muted-foreground">All settled up 🎉</p>
      </div>
    )
  }

  if (net < 0) {
    return (
      <div className="rounded-lg bg-destructive/10 px-4 py-3">
        <p className="text-sm font-medium text-destructive/80">You owe</p>
        <p className="mt-0.5 font-mono text-2xl font-bold tabular-nums tracking-tight text-destructive">
          {formatInr(Math.abs(net))}
        </p>
      </div>
    )
  }

  return (
    <div className="rounded-lg bg-emerald-500/10 px-4 py-3">
      <p className="text-sm font-medium text-emerald-700 dark:text-emerald-400">You are owed</p>
      <p className="mt-0.5 font-mono text-2xl font-bold tabular-nums tracking-tight text-emerald-700 dark:text-emerald-400">
        {formatInr(net)}
      </p>
    </div>
  )
}

export function GroupsPage() {
  const { data: groups, isLoading, isError, error } = useGroups()

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Your groups</h1>
          <p className="text-muted-foreground">Track shared expenses and settle up.</p>
        </div>
        <CreateGroupDialog />
      </div>

      {isLoading ? <GroupsSkeleton /> : null}

      {isError ? (
        <Card className="border-destructive/30">
          <CardContent className="py-8 text-center text-sm text-destructive">
            {(error as Error).message || 'Failed to load groups'}
          </CardContent>
        </Card>
      ) : null}

      {!isLoading && !isError && groups?.length === 0 ? (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center py-14 text-center">
            <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-accent">
              <Users className="h-7 w-7 text-accent-foreground" />
            </div>
            <h2 className="text-lg font-semibold">No groups yet</h2>
            <p className="mt-1 max-w-sm text-sm text-muted-foreground">
              Create a group to start logging expenses and splitting bills equally.
            </p>
            <div className="mt-6">
              <CreateGroupDialog />
            </div>
          </CardContent>
        </Card>
      ) : null}

      {!isLoading && groups && groups.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2">
          {groups.map((group) => (
            <Link
              key={group.id}
              to="/groups/$groupId"
              params={{ groupId: String(group.id) }}
              search={{ tab: 'expenses' }}
              className="group block"
            >
              <Card className="h-full transition-colors hover:border-primary/40 hover:bg-accent/30">
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <CardTitle className="text-base">{group.name}</CardTitle>
                      {group.createdAt ? (
                        <CardDescription className="mt-1">Created {formatRelative(group.createdAt)}</CardDescription>
                      ) : null}
                    </div>
                    <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  {group.id != null ? <GroupNetBalance groupId={group.id} /> : null}
                  <div className="flex items-center justify-between pt-1">
                    <div className="flex -space-x-2">
                      {group.members?.slice(0, 4).map((member) => (
                        <UserAvatar
                          key={member.id}
                          user={member}
                          className="h-8 w-8 border-2 border-card"
                        />
                      ))}
                    </div>
                    <span className="text-xs text-muted-foreground">
                      {group.members?.length ?? 0} member{(group.members?.length ?? 0) === 1 ? '' : 's'}
                    </span>
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      ) : null}
    </div>
  )
}
