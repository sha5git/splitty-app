import { format, formatDistanceToNow, isToday, isYesterday, parseISO } from 'date-fns'

const inrFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function formatInr(amount: number) {
  return inrFormatter.format(amount)
}

/**
 * Backend uses LocalDateTime (no timezone). Jackson serializes it as
 * "2026-08-05T00:29:00" without Z/offset. date-fns treats that as local.
 *
 * If a value still has Z (legacy client payloads stored as UTC wall-clock),
 * strip the offset and treat the clock face as local — matching how
 * LocalDateTime was persisted.
 */
export function parseAppDate(iso: string) {
  const withoutZone = iso.replace(/([zZ]|[+-]\d{2}:?\d{2})$/, '')
  return parseISO(withoutZone)
}

/**
 * Local wall-clock datetime for CreateExpense / CreateSettlement.
 * Prefer omitting `date` and letting the backend use LocalDateTime.now();
 * use this only when the client must send an explicit time.
 */
export function toLocalDateTimeString(date = new Date()) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

export function formatDate(iso: string) {
  return format(parseAppDate(iso), 'd MMM yyyy')
}

export function formatRelative(iso: string) {
  const date = parseAppDate(iso)

  if (isToday(date)) {
    return formatDistanceToNow(date, { addSuffix: true })
  }

  if (isYesterday(date)) {
    return `Yesterday · ${format(date, 'h:mm a')}`
  }

  return format(date, 'd MMM · h:mm a')
}

export function getInitials(name: string) {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

const AVATAR_COLORS = [
  'bg-teal-600',
  'bg-amber-600',
  'bg-rose-600',
  'bg-indigo-600',
  'bg-emerald-600',
  'bg-orange-600',
  'bg-violet-600',
  'bg-cyan-600',
]

export function avatarColor(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}
