import {
  createRootRouteWithContext,
  createRoute,
  createRouter,
  Outlet,
  redirect,
} from '@tanstack/react-router'

import type { AuthStatus } from '@/auth/AuthProvider'
import { AuthRouteLayout } from '@/components/layout/AuthRouteLayout'
import { AuthRouteShell } from '@/components/layout/AuthRouteShell'
import { LoadingScreen } from '@/components/layout/AppShell'
import { GroupDetailPage } from '@/pages/GroupDetailPage'
import { GroupsPage } from '@/pages/GroupsPage'
import { LoginPage } from '@/pages/LoginPage'
import { SignupPage } from '@/pages/SignupPage'

export interface RouterContext {
  auth: {
    status: AuthStatus
    isAuthenticated: boolean
  }
}

const rootRoute = createRootRouteWithContext<RouterContext>()({
  component: () => <Outlet />,
})

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  beforeLoad: ({ context }) => {
    if (context.auth.status === 'loading') return
    throw redirect({
      to: context.auth.isAuthenticated ? '/groups' : '/login',
    })
  },
  component: LoadingScreen,
})

const authLayoutRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'auth',
  beforeLoad: ({ context }) => {
    if (context.auth.isAuthenticated) {
      throw redirect({ to: '/groups' })
    }
  },
  component: AuthRouteLayout,
})

const loginRoute = createRoute({
  getParentRoute: () => authLayoutRoute,
  path: '/login',
  component: LoginPage,
})

const signupRoute = createRoute({
  getParentRoute: () => authLayoutRoute,
  path: '/signup',
  component: SignupPage,
})

const appRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'app',
  beforeLoad: ({ context, location }) => {
    if (context.auth.status === 'unauthenticated') {
      throw redirect({
        to: '/login',
        search: { redirect: location.href },
      })
    }
  },
  component: AuthRouteShell,
})

const groupsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/groups',
  component: GroupsPage,
})

const groupDetailRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/groups/$groupId',
  validateSearch: (search: Record<string, unknown>) => ({
    tab: (search.tab as 'expenses' | 'balances' | 'settlements' | 'members' | undefined) ?? 'expenses',
  }),
  component: function GroupDetailRoute() {
    const { groupId } = groupDetailRoute.useParams()
    const { tab } = groupDetailRoute.useSearch()
    return <GroupDetailPage groupId={Number(groupId)} initialTab={tab ?? 'expenses'} />
  },
})

const routeTree = rootRoute.addChildren([
  indexRoute,
  authLayoutRoute.addChildren([loginRoute, signupRoute]),
  appRoute.addChildren([groupsRoute, groupDetailRoute]),
])

export const router = createRouter({
  routeTree,
  context: {
    auth: {
      status: 'loading',
      isAuthenticated: false,
    },
  },
  defaultPreload: 'intent',
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
