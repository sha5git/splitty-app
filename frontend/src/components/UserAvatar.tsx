import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { avatarColor, getInitials } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { UserDto } from '@/api/types'

interface UserAvatarProps {
  user: Pick<UserDto, 'name' | 'avatarUrl'>
  className?: string
  fallbackClassName?: string
}

export function UserAvatar({ user, className, fallbackClassName }: UserAvatarProps) {
  const name = user.name ?? '?'

  return (
    <Avatar className={className}>
      {user.avatarUrl ? <AvatarImage src={user.avatarUrl} alt={name} /> : null}
      <AvatarFallback className={cn(avatarColor(name), fallbackClassName)}>
        {getInitials(name)}
      </AvatarFallback>
    </Avatar>
  )
}
