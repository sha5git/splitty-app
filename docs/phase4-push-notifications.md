# Phase 4 — Push notifications

**Depends on:** [Phase 2](phase2-realtime-updates.md) event publisher and [Phase 3](phase3-pwa-shell.md) service worker.

**Status:** plan only — implement after Phases 2 and 3.

This is the “Push notifications” item deferred from [Phase 1](phase1-backend-plan.md).

## Goal for Phase 4

When Bob’s phone is locked or Splitty is in the background, he still learns that Alice added an expense (OS notification). Tapping it opens the group. Foreground tabs keep using Phase 2 SSE.

---

## 1. Backend

- Store FCM device tokens: `POST /api/users/me/fcm-token` (plus delete/replace on logout).
- Flyway: token table or column keyed by user (a user may have more than one device).
- On the same post-commit hook as Phase 2, send FCM via existing `firebase-admin` (`FirebaseMessaging`).
- Payload: group id, event type, short title/body. Do not put secrets in the payload.
- Skip the actor’s tokens if the acting device is already in the foreground (optional; sending anyway is acceptable if the client dedupes).

## 2. Frontend

- `firebase/messaging`: request notification permission after login; register the token with the API.
- Service worker: `onBackgroundMessage` → system notification; click → group route (`/groups/$groupId`).
- Foreground: `onMessage` → toast + same `invalidateQueries` as SSE. Dedupe if SSE and FCM both fire.

## 3. Explicitly deferred

- Email / SMS fallback
- Notification preferences (mute group, quiet hours)
- Rich notification actions (settle from the shade)

## 4. Build order / checklist

- [ ] 1. Token storage + `POST /api/users/me/fcm-token` + OpenAPI
- [ ] 2. Publisher hook sends FCM to group members
- [ ] 3. Web Push / VAPID + `firebase/messaging` on the client
- [ ] 4. Service worker background handler + notification click routing
- [ ] 5. Foreground handler + SSE/FCM invalidation dedupe
- [ ] 6. Manual: Alice adds expense while Bob’s PWA is backgrounded — OS notification appears
