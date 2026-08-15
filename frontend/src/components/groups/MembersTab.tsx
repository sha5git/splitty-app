import { zodResolver } from '@hookform/resolvers/zod'
import { Mail, UserMinus } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { useAuth } from '@/auth/AuthProvider'
import type { UserDto } from '@/api/types'
import { useAddMember, useRemoveMember } from '@/api/hooks'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const addMemberSchema = z.object({
  email: z.string().email('Enter a valid email'),
})

type AddMemberForm = z.infer<typeof addMemberSchema>

interface MembersTabProps {
  groupId: number
  members: UserDto[]
}

export function MembersTab({ groupId, members }: MembersTabProps) {
  const { user } = useAuth()
  const addMember = useAddMember(groupId)
  const removeMember = useRemoveMember(groupId)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AddMemberForm>({
    resolver: zodResolver(addMemberSchema),
  })

  async function onSubmit(values: AddMemberForm) {
    await addMember.mutateAsync({ email: values.email })
    reset()
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardContent className="space-y-4 py-5">
          <div>
            <h3 className="font-medium">Invite by email</h3>
            <p className="text-sm text-muted-foreground">
              They need a Splitty account registered with this email.
            </p>
          </div>
          <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3 sm:flex-row">
            <div className="flex-1 space-y-2">
              <Label htmlFor="member-email" className="sr-only">
                Email
              </Label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="member-email"
                  type="email"
                  placeholder="friend@example.com"
                  className="pl-9"
                  {...register('email')}
                />
              </div>
              {errors.email ? <p className="text-sm text-destructive">{errors.email.message}</p> : null}
            </div>
            <Button type="submit" disabled={isSubmitting || addMember.isPending} className="sm:self-end">
              {isSubmitting || addMember.isPending ? 'Adding…' : 'Add member'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <div className="space-y-2">
        <h3 className="text-sm font-medium text-muted-foreground">{members.length} members</h3>
        <div className="divide-y rounded-xl border">
          {members.map((member) => {
            const isSelf = member.id === user?.id
            return (
              <div key={member.id} className="flex items-center gap-3 px-4 py-3">
                <UserAvatar user={member} className="h-10 w-10" />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">
                    {member.name}
                    {isSelf ? <span className="ml-2 text-xs text-muted-foreground">(you)</span> : null}
                  </p>
                  <p className="truncate text-sm text-muted-foreground">{member.email}</p>
                </div>
                {!isSelf ? (
                  <Button
                    variant="ghost"
                    size="icon"
                    className="text-muted-foreground hover:text-destructive"
                    onClick={() => member.id && removeMember.mutate(member.id)}
                    disabled={removeMember.isPending}
                    aria-label={`Remove ${member.name}`}
                  >
                    <UserMinus className="h-4 w-4" />
                  </Button>
                ) : null}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
