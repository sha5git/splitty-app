# Phase 2 — Real-time in-app updates

**Stack:** existing Spring Boot REST API + in-memory `SseEmitter` fan-out; React + TanStack Query. No Redis, no WebSockets, no PWA, no FCM.

## Goal for Phase 2

Bob sees Alice’s group changes while both have Splitty open, without a 30-second poll. REST stays the source of truth. SSE events are small hints that tell the client to `invalidateQueries` and refetch.

---

## 1. Event model

JSON payload on SSE event name `group-change`:

| Field | Type | Notes |
|---|---|---|
| `groupId` | number | Group that changed |
| `type` | string | See types below |
| `entityId` | number, optional | Expense, settlement, or member user id |
| `actorUserId` | number, optional | Who performed the write |

**Types:** `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `SETTLEMENT_CREATED`, `SETTLEMENT_UPDATED`, `GROUP_CREATED`, `GROUP_UPDATED`, `MEMBER_ADDED`, `MEMBER_REMOVED`.

Fan-out to **all current group members**, including the actor (so extra tabs for the same user stay in sync). On `MEMBER_REMOVED`, also notify the removed user so their group list updates.

Emit **after the write transaction commits** so Bob’s refetch does not race an uncommitted row.

## 2. Backend

```
com.expensesplit
├── dto/GroupChangeEvent.java
├── service/GroupEventPublisher.java   → in-memory emitters keyed by user id
└── controller/EventsController.java   → GET /api/events/stream
```

**`GroupEventPublisher`**

- Registry: user id → list of `SseEmitter` (multiple tabs).
- `subscribe(userId)` returns an emitter with no timeout; comment heartbeats keep proxies alive.
- `publishAfterCommit(event, extraUserIds…)` looks up members via `GroupMemberRepository`.
- Single JVM only. Redis is out of scope (Termux already runs Postgres + Spring Boot).

**Endpoint**

- `GET /api/events/stream` — `text/event-stream`, Firebase Bearer auth.
- Headers: `Cache-Control: no-cache`, `X-Accel-Buffering: no`, `Connection: keep-alive`.
- Clients must use **fetch + `Authorization`**. Native `EventSource` cannot set Bearer headers.

**Writes that publish:** group create/update, add/remove member, expense create/update/delete, settlement create/update.

**Deploy:** if nginx sits in front of the API, disable buffering for this path (`proxy_buffering off;` / `X-Accel-Buffering: no`). Vite’s `/api` proxy should not time out the stream.

## 3. Frontend

- `GET /api/events/stream` via fetch `ReadableStream`; reconnect with backoff on drop.
- Map each `group-change` to the same TanStack Query keys as local mutations (`invalidateGroup` / expense / groups list).
- Remove `refetchInterval: 30_000`. Keep `refetchOnWindowFocus`.
- While SSE is disconnected, fall back to a slow poll (5 minutes).
- Connect only when the user is authenticated.
- No in-app “Alice added Lunch” toast in this phase.

## 4. Explicitly deferred

- PWA / service worker — [Phase 3](phase3-pwa-shell.md)
- Push notifications / FCM — [Phase 4](phase4-push-notifications.md)
- Redis pub/sub (only if multiple API processes exist)
- WebSockets / STOMP
- Optimistic merge of remote rows without refetch

## 5. Build order / checklist

- [x] 1. `GroupChangeEvent` DTO + OpenAPI schema
- [x] 2. `GroupEventPublisher` (subscribe, heartbeat, after-commit fan-out)
- [x] 3. `GET /api/events/stream` + CORS / proxy headers
- [x] 4. Publish from group, expense, and settlement mutations
- [x] 5. Fetch-based SSE client + query invalidation; drop 30s polling
- [x] 6. Regenerate frontend types (`npm run generate:api`)
- [x] 7. Backend unit tests still pass with publisher mocked
- [ ] 8. Manual: two browsers, two users, same group — add expense on one, list updates on the other without reload
