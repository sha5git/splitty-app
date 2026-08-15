import { RouterProvider } from '@tanstack/react-router'
import { useEffect } from 'react'

import { useAuth } from '@/auth/AuthProvider'
import { router } from '@/router'

function clearTextFocusArtifacts() {
  window.getSelection()?.removeAllRanges()

  const active = document.activeElement
  if (
    active instanceof HTMLElement &&
    !active.matches('input, textarea, select, [contenteditable="true"]')
  ) {
    active.blur()
  }
}

export function AppRouter() {
  const { status } = useAuth()

  useEffect(() => {
    router.invalidate()
  }, [status])

  useEffect(() => {
    return router.subscribe('onResolved', clearTextFocusArtifacts)
  }, [])

  return (
    <RouterProvider
      router={router}
      context={{
        auth: {
          status,
          isAuthenticated: status === 'authenticated',
        },
      }}
    />
  )
}
