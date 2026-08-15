import { useAuth } from '@/auth/AuthProvider'
import { AuthLayout, LoadingScreen } from '@/components/layout/AppShell'

export function AuthRouteLayout() {
  const { status } = useAuth()

  if (status === 'loading') {
    return <LoadingScreen />
  }

  return <AuthLayout />
}
