import { Link, Outlet, useNavigate } from '@tanstack/react-router'
import { LogOut, Receipt, Users } from 'lucide-react'

import { useAuth } from '@/auth/AuthProvider'
import { ThemeToggle } from '@/components/ThemeToggle'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'

export function AppShell() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()

  async function handleSignOut() {
    await signOut()
    navigate({ to: '/login' })
  }

  return (
    <div className="min-h-dvh bg-background">
      <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Link to="/groups" className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <Receipt className="h-4 w-4" />
            </div>
            <span className="text-lg font-semibold tracking-tight">Splitty</span>
          </Link>

          {user ? (
            <div className="flex items-center gap-1 sm:gap-2">
              <div className="hidden items-center gap-2 sm:flex">
                <UserAvatar user={user} className="h-8 w-8" />
                <div className="text-sm">
                  <p className="font-medium leading-none">{user.name}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">{user.email}</p>
                </div>
              </div>
              <ThemeToggle />
              <Button variant="ghost" size="icon" onClick={handleSignOut} aria-label="Sign out">
                <LogOut className="h-4 w-4" />
              </Button>
            </div>
          ) : (
            <ThemeToggle />
          )}
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6 pb-24">
        <Outlet />
      </main>
    </div>
  )
}

export function AuthLayout() {
  return (
    <div className="relative min-h-dvh overflow-hidden bg-background">
      <div className="absolute right-4 top-4 z-10">
        <ThemeToggle />
      </div>
      <div
        aria-hidden
        className="pointer-events-none absolute -left-32 top-0 h-96 w-96 rounded-full bg-primary/10 blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -right-24 bottom-0 h-80 w-80 rounded-full bg-teal-500/10 blur-3xl"
      />
      <div className="relative flex min-h-dvh flex-col items-center justify-center px-4 py-12">
        <div className="mb-8 flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
            <Users className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Splitty</h1>
            <p className="text-sm text-muted-foreground">Split bills, not friendships</p>
          </div>
        </div>
        <Outlet />
      </div>
    </div>
  )
}

export function SyncErrorScreen() {
  const { syncError, refreshUser, signOut } = useAuth()

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-background px-4 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
        <Receipt className="h-6 w-6" />
      </div>
      <div className="max-w-sm space-y-1">
        <p className="font-semibold">Could not reach the server</p>
        <p className="text-sm text-muted-foreground">
          {syncError ?? 'Make sure the Spring Boot backend is running on port 8080.'}
        </p>
      </div>
      <div className="flex gap-3">
        <Button onClick={() => refreshUser()}>Retry</Button>
        <Button variant="outline" onClick={() => signOut()}>
          Sign out
        </Button>
      </div>
    </div>
  )
}

export function LoadingScreen() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-background">
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
        <Receipt className="h-6 w-6 animate-pulse" />
      </div>
      <p className="text-sm text-muted-foreground">Loading Splitty…</p>
    </div>
  )
}
