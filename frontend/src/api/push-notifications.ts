import {
  deleteToken,
  getMessaging,
  getToken,
  isSupported,
  onMessage,
  type Messaging,
  type Unsubscribe,
} from 'firebase/messaging'

import { api } from '@/api/client'
import { getFirebaseApp } from '@/auth/firebase'

const FCM_TOKEN_KEY = 'splitty-fcm-token'

function vapidKey() {
  return import.meta.env.VITE_FIREBASE_VAPID_KEY
}

export async function registerPushNotifications(): Promise<void> {
  if (!import.meta.env.PROD || !vapidKey()) {
    return
  }
  if (!('Notification' in window) || !('serviceWorker' in navigator)) {
    return
  }

  try {
    if (!(await isSupported())) {
      return
    }

    const permission = await Notification.requestPermission()
    if (permission !== 'granted') {
      return
    }

    const registration = await navigator.serviceWorker.ready
    const messaging = getMessaging(getFirebaseApp())
    const token = await getToken(messaging, {
      vapidKey: vapidKey(),
      serviceWorkerRegistration: registration,
    })
    if (!token) {
      return
    }

    sessionStorage.setItem(FCM_TOKEN_KEY, token)
    await api.registerFcmToken({ token })
  } catch (error) {
    console.warn('Splitty push registration failed', error)
  }
}

export function subscribeForegroundMessages(
  handler: (payload: { title?: string; body?: string; data: Record<string, string> }) => void,
): Unsubscribe | null {
  if (!import.meta.env.PROD) {
    return null
  }

  let messaging: Messaging
  try {
    messaging = getMessaging(getFirebaseApp())
  } catch {
    return null
  }

  return onMessage(messaging, (payload) => {
    const data: Record<string, string> = {}
    if (payload.data) {
      for (const [key, value] of Object.entries(payload.data)) {
        if (value != null) data[key] = String(value)
      }
    }
    handler({
      title: payload.notification?.title,
      body: payload.notification?.body,
      data,
    })
  })
}

export async function unregisterPushNotifications() {
  const token = sessionStorage.getItem(FCM_TOKEN_KEY)
  if (token) {
    try {
      await api.deleteFcmToken({ token })
    } catch (error) {
      console.warn('Splitty push token delete failed', error)
    }
    sessionStorage.removeItem(FCM_TOKEN_KEY)
  }

  try {
    if (await isSupported()) {
      await deleteToken(getMessaging(getFirebaseApp()))
    }
  } catch (error) {
    console.warn('Splitty FCM deleteToken failed', error)
  }
}
