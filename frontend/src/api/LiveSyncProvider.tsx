import { connectGroupEventStream } from '@/api/live-events'
import { LiveSyncContext } from '@/api/live-sync-context'
import { applyGroupChangeEvent } from '@/api/hooks'
import { useAuth } from '@/auth/AuthProvider'
import { queryClient } from '@/lib/query-client'

import { useEffect, useState, type ReactNode } from 'react'

function sleep(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(resolve, ms)
    const onAbort = () => {
      window.clearTimeout(timer)
      reject(signal.reason ?? new DOMException('Aborted', 'AbortError'))
    }
    if (signal.aborted) {
      onAbort()
      return
    }
    signal.addEventListener('abort', onAbort, { once: true })
  })
}

export function LiveSyncProvider({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (status !== 'authenticated') {
      setConnected(false)
      return
    }

    const abort = new AbortController()
    let stopped = false
    let delayMs = 1000

    async function loop() {
      while (!stopped && !abort.signal.aborted) {
        try {
          await connectGroupEventStream({
            signal: abort.signal,
            onOpen: () => {
              setConnected(true)
              delayMs = 1000
            },
            onEvent: (event) => applyGroupChangeEvent(queryClient, event),
          })
        } catch (error) {
          if (abort.signal.aborted) {
            return
          }
          console.warn('Splitty live updates disconnected', error)
        }

        setConnected(false)
        if (stopped || abort.signal.aborted) {
          return
        }

        try {
          await sleep(delayMs, abort.signal)
        } catch {
          return
        }
        delayMs = Math.min(delayMs * 2, 15_000)
      }
    }

    void loop()

    return () => {
      stopped = true
      abort.abort()
    }
  }, [status])

  return <LiveSyncContext.Provider value={connected}>{children}</LiveSyncContext.Provider>
}
