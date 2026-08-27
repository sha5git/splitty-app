import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Toaster } from 'sonner'

import { AuthProvider } from '@/auth/AuthProvider'
import { LiveSyncProvider } from '@/api/LiveSyncProvider'
import { PushProvider } from '@/api/PushProvider'
import { AppRouter } from '@/components/layout/AppRouter'
import { queryClient } from '@/lib/query-client'
import { applyTheme, getTheme } from '@/lib/theme'
import { registerSW } from 'virtual:pwa-register'

import './index.css'

applyTheme(getTheme())

if (import.meta.env.PROD) {
  registerSW({ immediate: true })
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <LiveSyncProvider>
          <PushProvider>
            <AppRouter />
            <Toaster richColors closeButton position="top-center" />
          </PushProvider>
        </LiveSyncProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
