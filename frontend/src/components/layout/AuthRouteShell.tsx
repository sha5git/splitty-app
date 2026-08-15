import { useAuth } from '@/auth/AuthProvider'
import { AppShell, LoadingScreen, SyncErrorScreen } from '@/components/layout/AppShell'

export function AuthRouteShell() {
  const { status } = useAuth()

  if (status === 'loading') {
    return <LoadingScreen />
  }

  if (status === 'sync-error') {
    return <SyncErrorScreen />
  }

  return <AppShell />
}
