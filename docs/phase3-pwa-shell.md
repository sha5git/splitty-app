# Phase 3 — PWA shell

**Depends on:** [Phase 2](phase2-realtime-updates.md) for live in-app data. This phase does **not** implement push notifications.

**Status: complete** (installable shell). Push: [Phase 4 — Push notifications](phase4-push-notifications.md).

## Goal for Phase 3

Make Splitty installable (home screen) and introduce a service worker that Phase 4 can use for background messages.

This does **not** notify Bob when the app is closed or backgrounded. That is [Phase 4](phase4-push-notifications.md).

---

## 1. Scope

- Add `vite-plugin-pwa` to the frontend (`injectManifest` so Phase 4 can extend the same worker).
- Web app manifest: name **Splitty**, theme color `#0f766e`.
- Icons derived from `frontend/public/favicon.svg` (192, 512, maskable 512, apple-touch 180).
- Precache **static** assets (JS, CSS, icons). Service worker is **not** registered during `npm run dev`.
- Do **not** cache authenticated `/api` responses. Navigation fallback denylists `/api`. Live data stays on the network + TanStack Query + Phase 2 SSE.

## 2. Out of scope

- Firebase Cloud Messaging
- OS notifications
- Offline-first expense writes
- Background sync of mutations
- Custom in-app install banner (browser native install only)

## 3. Build order / checklist

- [x] 1. Install and configure `vite-plugin-pwa`
- [x] 2. Manifest (name, short_name, theme_color, display, start_url)
- [x] 3. Icons (SVG + generated PNGs as required)
- [x] 4. Precache static assets; leave API traffic uncached (or network-first)
- [ ] 5. Verify install prompt / “Add to Home Screen” on desktop and Android (after deploy to HTTPS)
- [x] 6. Confirm SW navigation denylist includes `/api` so SSE is not intercepted (`dist/sw.js`); local `npm run dev` does not register a SW
