import { getIdToken } from '@/auth/firebase'
import type { GroupChangeEvent } from '@/api/types'

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

export function consumeSseBuffer(buffer: string): {
  rest: string
  messages: Array<{ event: string; data: string }>
} {
  const messages: Array<{ event: string; data: string }> = []
  let rest = buffer.replace(/\r\n/g, '\n')

  while (true) {
    const idx = rest.indexOf('\n\n')
    if (idx === -1) {
      break
    }
    const block = rest.slice(0, idx)
    rest = rest.slice(idx + 2)

    let event = 'message'
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (!line || line.startsWith(':')) {
        continue
      }
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    }
    if (dataLines.length > 0) {
      messages.push({ event, data: dataLines.join('\n') })
    }
  }

  return { rest, messages }
}

export async function connectGroupEventStream(options: {
  signal: AbortSignal
  onOpen: () => void
  onEvent: (event: GroupChangeEvent) => void
}): Promise<void> {
  const token = await getIdToken()
  if (!token) {
    throw new Error('Not authenticated')
  }

  const response = await fetch(`${baseUrl}/api/events/stream`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
      'Cache-Control': 'no-cache',
    },
    signal: options.signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`Event stream failed (${response.status})`)
  }

  options.onOpen()

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const consumed = consumeSseBuffer(buffer)
      buffer = consumed.rest
      for (const message of consumed.messages) {
        if (message.event !== 'group-change') {
          continue
        }
        try {
          options.onEvent(JSON.parse(message.data) as GroupChangeEvent)
        } catch {
          // Ignore malformed payloads; the next event or a refetch will recover.
        }
      }
    }
  } finally {
    reader.releaseLock()
  }
}
