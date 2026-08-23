import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Toaster } from 'sonner'

import { AuthProvider } from '@/auth/AuthProvider'
import { LiveSyncProvider } from '@/api/LiveSyncProvider'
import { AppRouter } from '@/components/layout/AppRouter'
import { queryClient } from '@/lib/query-client'
import { applyTheme, getTheme } from '@/lib/theme'

import './index.css'

applyTheme(getTheme())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <LiveSyncProvider>
          <AppRouter />
          <Toaster richColors closeButton position="top-center" />
        </LiveSyncProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
