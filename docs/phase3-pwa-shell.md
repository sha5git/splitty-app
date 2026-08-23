# Phase 3 — PWA shell

**Depends on:** [Phase 2](phase2-realtime-updates.md) for live in-app data. This phase does **not** implement push notifications.

**Status:** plan only — implement after Phase 2 is shipped.

## Goal for Phase 3

Make Splitty installable (home screen) and introduce a service worker that Phase 4 can use for background messages.

This does **not** notify Bob when the app is closed or backgrounded. That is [Phase 4](phase4-push-notifications.md).

---

## 1. Scope

- Add `vite-plugin-pwa` to the frontend.
- Web app manifest: name **Splitty**, theme color matching brand teal in `frontend/src/index.css` (`primary` / accent).
- Icons derived from `frontend/public/favicon.svg` (and raster sizes the plugin/PWA needs).
- Precache **static** assets (JS, CSS, icons).
- Do **not** aggressively cache authenticated `/api` responses. Live data stays on the network + TanStack Query + Phase 2 SSE.

## 2. Out of scope

- Firebase Cloud Messaging
- OS notifications
- Offline-first expense writes
- Background sync of mutations

## 3. Build order / checklist

- [ ] 1. Install and configure `vite-plugin-pwa`
- [ ] 2. Manifest (name, short_name, theme_color, display, start_url)
- [ ] 3. Icons (SVG + generated PNGs as required)
- [ ] 4. Precache static assets; leave API traffic uncached (or network-first)
- [ ] 5. Verify install prompt / “Add to Home Screen” on desktop and Android
- [ ] 6. Confirm Phase 2 SSE still works with the service worker in front of navigations
