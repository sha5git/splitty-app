import { createContext, useContext } from 'react'

export const LiveSyncContext = createContext(false)

export function useLiveSyncConnected() {
  return useContext(LiveSyncContext)
}
