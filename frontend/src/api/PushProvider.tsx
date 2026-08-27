import { applyGroupChangeEvent } from '@/api/hooks'
import { registerPushNotifications, subscribeForegroundMessages } from '@/api/push-notifications'
import { useLiveSyncConnected } from '@/api/live-sync-context'
import type { GroupChangeEvent } from '@/api/types'
import { useAuth } from '@/auth/AuthProvider'
import { queryClient } from '@/lib/query-client'
import { useEffect, useRef, type ReactNode } from 'react'
import { toast } from 'sonner'

function toGroupChangeEvent(data: Record<string, string>): GroupChangeEvent | null {
  const groupId = Number(data.groupId)
  if (!Number.isFinite(groupId)) {
    return null
  }
  const entityId = data.entityId ? Number(data.entityId) : undefined
  return {
    groupId,
    type: data.type as GroupChangeEvent['type'],
    entityId: Number.isFinite(entityId) ? entityId : undefined,
  }
}

export function PushProvider({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  const sseConnected = useLiveSyncConnected()
  const sseConnectedRef = useRef(sseConnected)
  sseConnectedRef.current = sseConnected

  useEffect(() => {
    if (status !== 'authenticated') {
      return
    }

    let cancelled = false
    let unsubscribe: ReturnType<typeof subscribeForegroundMessages> = null

    void (async () => {
      await registerPushNotifications()
      if (cancelled) {
        return
      }
      unsubscribe = subscribeForegroundMessages((payload) => {
        const event = toGroupChangeEvent(payload.data)
        if (event) {
          applyGroupChangeEvent(queryClient, event)
        }
        if (!sseConnectedRef.current && payload.body) {
          toast(payload.title ?? 'Splitty', { description: payload.body })
        }
      })
    })()

    return () => {
      cancelled = true
      unsubscribe?.()
    }
  }, [status])

  return children
}
