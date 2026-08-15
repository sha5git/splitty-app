import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { User } from 'firebase/auth'

import { ApiError } from '@/api/types'
import { api } from '@/api/client'
import { queryKeys } from '@/api/hooks'
import type { UserDto } from '@/api/types'
import { logOut, subscribeToAuth } from '@/auth/firebase'
import { queryClient } from '@/lib/query-client'

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated' | 'sync-error'

interface AuthContextValue {
  status: AuthStatus
  firebaseUser: User | null
  user: UserDto | null
  syncError: string | null
  signOut: () => Promise<void>
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [firebaseUser, setFirebaseUser] = useState<User | null>(null)
  const [user, setUser] = useState<UserDto | null>(null)
  const [syncError, setSyncError] = useState<string | null>(null)

  const syncUser = useCallback(async (fbUser: User) => {
    await fbUser.getIdToken(true)
    const profile = await api.getMe()
    setUser(profile)
    setSyncError(null)
    queryClient.setQueryData(queryKeys.me, profile)
    setStatus('authenticated')
  }, [])

  useEffect(() => {
    let cancelled = false

    const unsubscribe = subscribeToAuth(async (fbUser) => {
      if (cancelled) return

      setFirebaseUser(fbUser)

      if (!fbUser) {
        setUser(null)
        setSyncError(null)
        queryClient.removeQueries({ queryKey: queryKeys.me })
        setStatus('unauthenticated')
        return
      }

      setStatus('loading')
      setSyncError(null)
      try {
        await syncUser(fbUser)
      } catch (error) {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 401) {
          await logOut()
          return
        }
        const message =
          error instanceof Error ? error.message : 'Could not connect to the server. Is the backend running?'
        console.error('Failed to sync user profile', error)
        setSyncError(message)
        setStatus('sync-error')
      }
    })

    return () => {
      cancelled = true
      unsubscribe()
    }
  }, [syncUser])

  const signOut = useCallback(async () => {
    await logOut()
    queryClient.clear()
    setUser(null)
    setFirebaseUser(null)
    setSyncError(null)
    setStatus('unauthenticated')
  }, [])

  const refreshUser = useCallback(async () => {
    if (!firebaseUser) return
    setStatus('loading')
    setSyncError(null)
    try {
      await syncUser(firebaseUser)
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        await logOut()
        return
      }
      const message =
        error instanceof Error ? error.message : 'Could not connect to the server. Is the backend running?'
      setSyncError(message)
      setStatus('sync-error')
    }
  }, [firebaseUser, syncUser])

  const value = useMemo(
    () => ({ status, firebaseUser, user, syncError, signOut, refreshUser }),
    [status, firebaseUser, user, syncError, signOut, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
