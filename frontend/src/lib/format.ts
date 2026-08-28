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
 * Local wall-clock datetime for Create/Update expense and settlement.
 * Never use Date.toISOString() — that is UTC and can shift the calendar day.
 */
export function toLocalDateTimeString(date = new Date()) {
  return toLocalDateTimeStringFromCalendarDate(toCalendarDateString(date), date)
}

export function toCalendarDateString(date = new Date()) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function calendarDateFromIso(iso: string) {
  return toCalendarDateString(parseAppDate(iso))
}

/** Combine a YYYY-MM-DD picker value with a wall-clock time (no Z). */
export function toLocalDateTimeStringFromCalendarDate(yyyyMmDd: string, timeSource = new Date()) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${yyyyMmDd}` +
    `T${pad(timeSource.getHours())}:${pad(timeSource.getMinutes())}:${pad(timeSource.getSeconds())}`
  )
}

export function formatDate(iso: string) {
  return format(parseAppDate(iso), 'd MMM yyyy')
}

export function formatMonthYear(iso: string) {
  return format(parseAppDate(iso), 'MMMM yyyy')
}

export function formatExpenseDayParts(iso: string) {
  const date = parseAppDate(iso)
  return {
    month: format(date, 'MMM'),
    day: format(date, 'd'),
  }
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
