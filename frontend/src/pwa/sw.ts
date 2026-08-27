/// <reference lib="webworker" />

import { initializeApp, getApps } from 'firebase/app'
import { getMessaging, onBackgroundMessage } from 'firebase/messaging/sw'
import { clientsClaim } from 'workbox-core'
import {
  cleanupOutdatedCaches,
  createHandlerBoundToURL,
  precacheAndRoute,
} from 'workbox-precaching'
import { NavigationRoute, registerRoute } from 'workbox-routing'

declare let self: ServiceWorkerGlobalScope

self.skipWaiting()
clientsClaim()

precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

const navigationHandler = createHandlerBoundToURL('/index.html')
registerRoute(
  new NavigationRoute(navigationHandler, {
    denylist: [/^\/api\//],
  }),
)

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
}

const firebaseApp = getApps().length > 0 ? getApps()[0] : initializeApp(firebaseConfig)
const messaging = getMessaging(firebaseApp)

onBackgroundMessage(messaging, async (payload) => {
  if (payload.notification?.title) {
    return
  }
  const title = 'Splitty'
  const body = typeof payload.data?.body === 'string' ? payload.data.body : ''
  await self.registration.showNotification(title, {
    body,
    icon: '/icons/icon-192.png',
    data: payload.data ?? {},
  })
})

self.addEventListener('notificationclick', (event: NotificationEvent) => {
  event.notification.close()
  const rawUrl = (event.notification.data as { url?: string } | undefined)?.url
  const target = rawUrl && rawUrl.startsWith('/') ? rawUrl : '/'

  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      for (const client of windows) {
        const windowClient = client as WindowClient
        if ('focus' in windowClient) {
          await windowClient.focus()
          if ('navigate' in windowClient) {
            await windowClient.navigate(target)
          }
          return
        }
      }
      await self.clients.openWindow(target)
    })(),
  )
})
