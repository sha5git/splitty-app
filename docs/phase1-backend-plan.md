# Phase 1 Backend Plan — Expense Splitting App

**Stack:** Spring Boot 3.x (Java 17+), PostgreSQL, Spring Data JPA, Spring Security (Firebase JWT verification), Maven, Lombok, Flyway (DB migrations)

## Goal for Phase 1

A working REST API that supports the core Splitwise loop: create a group, add members, log expenses split equally, and see who owes whom. No auth UI, no notifications, no multi-currency — just the backbone.

---

## 1. Project Structure

```
com.expensesplit
├── config/          → SecurityConfig, FirebaseConfig, CorsConfig
├── security/         → FirebaseTokenFilter, CurrentUser resolver
├── entity/            → User, Group, GroupMember, Expense, ExpenseSplit, Settlement
├── repository/        → Spring Data JPA interfaces
├── service/          → business logic (BalanceCalculationService is the key one)
├── controller/       → REST endpoints
├── dto/               → request/response DTOs (never expose entities directly)
├── exception/         → GlobalExceptionHandler, custom exceptions
└── ExpenseSplitApplication.java
```

## 2. Entities (Phase 1 scope)

| Entity | Key fields |
|---|---|
| `User` | id, firebaseUid, name, email, avatarUrl |
| `Group` | id, name, createdBy, createdAt |
| `GroupMember` | groupId, userId, joinedAt |
| `Expense` | id, groupId, paidBy, amount (`BigDecimal`), description, date, splitType (`EQUAL` only for now) |
| `ExpenseSplit` | expenseId, userId, amountOwed (`BigDecimal`) |
| `Settlement` | id, groupId, fromUser, toUser, amount, date |

**Money rule:** all monetary fields are `BigDecimal`, never `float`/`double`. This matters a lot once you get to splitting logic — floating point rounding errors will silently break balances.

## 3. Endpoints (Phase 1)

**Users**
- `GET /api/users/me` — sync/fetch current user from verified Firebase token (creates user row on first login)

**Groups**
- `POST /api/groups` — create group
- `GET /api/groups` — list groups for current user
- `GET /api/groups/{id}` — group details + members
- `POST /api/groups/{id}/members` — add member (by email or user id)
- `DELETE /api/groups/{id}/members/{userId}` — remove member

**Expenses**
- `POST /api/groups/{id}/expenses` — add expense (equal split only for Phase 1)
- `GET /api/groups/{id}/expenses` — list expenses for group
- `DELETE /api/expenses/{id}` — delete expense

**Balances**
- `GET /api/groups/{id}/balances` — computed "who owes whom" for the group

**Settlements**
- `POST /api/groups/{id}/settlements` — record a payment between two members
- `GET /api/groups/{id}/settlements` — settlement history

## 4. The one piece of real logic: Balance Calculation

For each group:
1. Sum all `ExpenseSplit` rows per user (what they owe in total).
2. Sum all amounts each user paid (from `Expense.paidBy`).
3. Net position per user = paid − owed.
4. Subtract settlements already made.
5. (Optional, can defer to Phase 2) Run a debt-simplification pass so balances collapse to the minimum number of transactions instead of a tangle of pairwise debts.

This lives in `BalanceCalculationService` and is the one part worth writing carefully and testing well — everything else is fairly standard CRUD.

## 5. Explicitly deferred to later phases

- Percentage / exact-amount / share-based splits (Phase 1 = equal split only)
- Multi-currency support
- Recurring expenses
- Push notifications
- Comments / activity feed
- Receipt photo uploads
- Debt simplification algorithm (nice-to-have, not required for a usable app)

## 6. Build order / checklist

- [x] 1. Spring Boot project init (Spring Initializr: Web, JPA, PostgreSQL driver, Security, Validation, Lombok)
- [x] 2. PostgreSQL setup + Flyway migration for the 6 tables above
- [x] 3. Entities + repositories
- [x] 4. Firebase Admin SDK integration + `FirebaseTokenFilter` (Spring Security filter that verifies the `Authorization: Bearer <token>` header)
- [x] 5. `GET /api/users/me` — first endpoint, proves auth works end-to-end
- [x] 6. Group CRUD endpoints
- [x] 7. Expense creation with equal-split logic + `ExpenseSplit` generation
- [x] 8. `BalanceCalculationService` + `GET /api/groups/{id}/balances`
- [x] 9. Settlement recording
- [x] 10. Unit tests for `BalanceCalculationService` (this is the part most worth testing thoroughly)
- [x] 11. Postman collection or OpenAPI/Swagger docs for the API surface, so React Native side has a clear contract

---

**Next step:** scaffold the actual project — `pom.xml`, entities, repositories, the Firebase security filter, and the first working endpoint — so you have something running locally.
