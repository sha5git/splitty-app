# Phase 4 — Push notifications

**Depends on:** [Phase 2](phase2-realtime-updates.md) event publisher and [Phase 3](phase3-pwa-shell.md) service worker.

**Status: complete** (OS notifications when Splitty is backgrounded). Foreground tabs keep using Phase 2 SSE.

This is the “Push notifications” item deferred from [Phase 1](phase1-backend-plan.md).

## Goal for Phase 4

When Bob’s phone is locked or Splitty is in the background, he still learns that Alice added an expense (OS notification). Tapping it opens the group. Foreground tabs keep using Phase 2 SSE.

---

## 1. Backend

- Store FCM device tokens: `POST /api/users/me/fcm-token` (plus `DELETE` on logout).
- Flyway `V2__user_fcm_tokens.sql`: tokens keyed by user (a user may have more than one device). Shared-device tokens are reassigned on register.
- On the same post-commit hook as Phase 2, `FcmPushService` sends FCM asynchronously via `FirebaseMessaging` (no-op when `firebase.mock=true`).
- Payload: notification title/body plus data `groupId`, `type`, `entityId`, `url`. Do not put secrets in the payload.
- Unregistered / invalid tokens are deleted.
- Skip FCM for `GROUP_CREATED` when the only recipient is the actor.

**Copy:** group name as title (or “Splitty” for rename/create). Body uses **You** when the recipient is the actor, payer, or payee. `EXPENSE_DELETED` is generic because the row is gone.

## 2. Frontend

- `firebase/messaging`: request notification permission after login; register the token with the API. Production only (dev does not register a service worker).
- Logout: `DELETE` token while still authenticated, then `deleteToken()`, then Firebase sign-out.
- Service worker: `onBackgroundMessage` + `notificationclick` → `/groups/$groupId?tab=expenses`.
- Foreground: `onMessage` → `applyGroupChangeEvent`; toast only when SSE is disconnected.

**iOS:** Web Push works only for a **Safari** PWA added to the Home Screen (iOS 16.4+). Chrome on iOS cannot install. Android Chrome PWA is the primary path.

## 3. Explicitly deferred

- Email / SMS fallback
- Notification preferences (mute group, quiet hours)
- Rich notification actions (settle from the shade)

## 4. Build order / checklist

- [x] 1. Token storage + `POST /api/users/me/fcm-token` + OpenAPI
- [x] 2. Publisher hook sends FCM to group members
- [x] 3. Web Push / VAPID + `firebase/messaging` on the client
- [x] 4. Service worker background handler + notification click routing
- [x] 5. Foreground handler + SSE/FCM invalidation dedupe
- [ ] 6. Manual: Alice adds expense while Bob’s PWA is backgrounded — OS notification appears

**Deploy notes:** set GitHub secret `VITE_FIREBASE_VAPID_KEY` (Firebase Console → Cloud Messaging → Web Push certificates) so production builds include the VAPID key.
