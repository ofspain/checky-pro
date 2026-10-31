# frontend · T31 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

Read the authenticated caller's own account — and its `status` — from **`GET /accounts/me`** through the
generated auth client, and prove that neither `status` nor `emailVerified` is ever read from a token instead.
R20's second clause, and an extension of its third.

## Purpose

`GET /accounts/me` is the SPA's **only** truthful source of account state, and Phase 0 measured why that
matters rather than taking it on faith: suspending an account through the admin endpoint does **not**
invalidate its access token. The same token then returns `200` with `status: "SUSPENDED"`, and **no 401 ever
arrives**. For up to the token's fifteen minutes a tab holds a working session for an account the platform has
suspended, and this read is the one thing that can say so.

A token-borne shortcut would therefore be worse than merely wrong: the access token was minted while the
account was `ACTIVE` and keeps asserting that until it expires. Reading status from it returns a stale answer
that looks current, which is the same failure mode R20(c) exists to prevent for `email_verified`.

## Scope

### In

- One read of `GET /accounts/me` through `createAuthApi`'s generated client, returning a typed account value
  to a caller in memory.
- The account's `status` reported **exactly as the server sent it** — never defaulted, coerced, narrowed to a
  boolean, or inferred.
- A neutral, detail-free failure for every way the read can fail, including a **401 whose body is empty and
  whose detail sits in a `WWW-Authenticate` header**.
- Extending T30's two composed token scans to cover `status` and `emailVerified`.
- `describe('T31: …')` blocks in the existing named test.

### Out

- **What the SPA DOES with a non-ACTIVE status.** Owner decision, 2026-10-31: T31 is the **read**; the gap is
  logged as OQ-T31-3 and assigned to no unit. T31 invents no route, no copy key and no redirect, because
  **L14** fixes the route set and **L11** requires catalogue copy.
- **Any change to `contracts/api/auth.yaml`.** Owner decision, 2026-10-31: the missing root-level `security`
  requirement and the undocumented 401 are **logged, not fixed** — they affect every operation in a contract
  shared by every service, and T31's code is identical either way.
- Client regeneration. `operations["getOwnAccount"]` already exists.
- `emailVerified` driving any verification UI — **T32's** (`emailVerifiedClaimIsNotAccountState`). T31 must
  not pre-empt it, and must not leave `emailVerified` so unconstrained that T32 becomes unprovable.
- A failed-renewal route to clean re-auth — **task 33's** (R22).
- Any change to `api/sessionBoundary.ts`, `api/crossTabRenewal.ts` or `api/rateLimitNotice.ts`.
- Any screen. `/app/account` does not exist.

## Business Rules

- **R20(b)** — account status is read from `GET /accounts/me`.
- **R20(c)** — PII and verification state are never read from the access token; for T31 that is `status` and
  `emailVerified` specifically.
- **R21** — a 401 runs at most one renewal per tab, shared across parallel 401s and sibling tabs. This
  endpoint answers a real 401, so the rule applies to it directly.
- **R22** — no half-authenticated screen, neutral copy. T31 contributes the neutral failure; the routing is
  task 33's.
- **R23** — a 429 shows fixed generic copy with no countdown.
- **R28** — no new route, so no change to what is served while unauthenticated.
- **R30** — no status-dependent error surface that would reopen the uniformity T29 established.

## Locked Decisions

**L3** tokens in memory only · **L4** enumeration-safe without carve-out (no enumeration surface here: the
backend derives the account from the principal's `sub` and exposes no path parameter) · **L6** generated
client only · **L9** single origin · **L11** catalogue copy, so an internal error message must not be
renderable · **L14** fixed route set · **L16** `sessionStorage` carve-out only · **L18** single-flight
renewal, consumed and not reimplemented.

## Dependencies

| Kind | Item |
|---|---|
| Contract | `contracts/api/auth.yaml:34` → `AccountResponse` (`:659`), `required: [accountUuid, email, emailVerified, status, createdAt]`, `status ∈ {PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DELETED}` |
| Generated type | `components['schemas']['AccountResponse']` from `@themistra/api-client` — **derive, never restate** |
| Generated operation | `libs/ts/api-client/src/generated/auth.ts` — `operations["getOwnAccount"]`, already present |
| Factory | `createAuthApi(boundary, rateLimit?)` — `frontend/src/api/client.ts:33` |
| Public ops | `AUTH_PUBLIC_OPERATIONS` — `client.ts:6`; `GET /accounts/me` is correctly **absent** |
| Boundary | `SessionTokens.current()` / `renew()`, `boundary.middleware(publicOps)` |
| Backend | `AccountController.me(Authentication)`, `@PreAuthorize("isAuthenticated()")` |

## Inputs

The generated auth client and the tab's token accessor, **both as parameters** — no module-level
construction, so the composition root stays as unwired as it already is.

## Outputs

A typed account value on success. On failure, a rejected promise carrying a neutral error whose message
**cannot** hold detail — the structural property, not a habit.

## State Changes

**None.** No storage write on any path, success or failure. No navigation. No DOM.

## Files to Create

- A reader for the account, under `frontend/src/profile/`. **One module or an addition to `readProfile.ts` is
  Phase 5's decision**, not this brief's.

## Files to Modify

- `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` — **`describe('T31: …')`
  blocks only.** T30 owns the imports, the setup, every helper and every T30 block; a T30 block may be touched
  only to repair a compilation error caused by a renamed export.

## Files NOT to Modify

`contracts/api/**` · `libs/ts/api-client/src/generated/**` · `spec/**` · `services/**` ·
`frontend/src/api/sessionBoundary.ts` · `crossTabRenewal.ts` · `rateLimitNotice.ts` · `client.ts` ·
`frontend/src/profile/readProfile.ts` beyond what sharing a module would require · any routing or screen file.

## Acceptance Criteria

- **AC1** — the account is read through the generated client at `GET /accounts/me`, typed by the schema path:
  one call, no hand-written transport, the path a quoted literal argument and never assembled. (R20b, L6)
- **AC2** — the returned type is **derived** from the generated `AccountResponse`. (L6)
- **AC3** — the call carries the tab's in-memory token as a bearer **via the boundary**, and
  `GET /accounts/me` is absent from `AUTH_PUBLIC_OPERATIONS`, asserted as an upper **and** lower bound.
  (R21, L18)
- **AC4** — a 401 is **the boundary's**: one renewal, one re-send, the reader never renews; a final 401 is a
  neutral failure. (R21, L18)
- **AC5** — **neither `status` nor `emailVerified` is read from any token**, anywhere in the comment-stripped
  tree, proven by composing T30's decode scan (which bounds the file set to one, asserted) with a claim scan
  over only that set. (**R20c**)
- **AC6** — a complete body is read faithfully and **all five statuses** survive the read unchanged: never
  defaulted, coerced, narrowed to a boolean, or inferred from another field.
- **AC7** — an **incomplete** body is a failure, not a partial account: each of the five required fields,
  missing or empty in turn, is rejected. The contract requires all five, unlike `UserInfoResponse`.
- **AC8** — an **unknown** status string is not silently treated as ACTIVE.
- **AC9** — a failed read surfaces no detail, no stack and no cause, **including from `WWW-Authenticate`**,
  which is where the measured 401 puts it with an empty body. A rule written about response bodies misses it.
- **AC10** — the account body never persists and is never logged: absent from `sessionStorage`,
  `localStorage`, `document.title`, `window.name`, the URL and all six console channels, raw and
  percent-decoded. (L3, L16)
- **AC11** — R23 preserved: a 429 shows the notice, **including a 429 on the boundary's re-send after a
  renewal**, which is the only case that distinguishes middleware registration order. (R23)
- **AC12** — every instrument is shown to report DIRTY first, and a scan that reads nothing is **refused**
  rather than reported clean. (T22-D14)

## Required Tests

- `profileIsReadFromUserinfoAndAccountStatusFromMe` — T31's half, as `describe('T31: …')` blocks.
- `generatedClientCompiles` — must stay green (10/10). A guard, not a deliverable.
- `emailVerifiedClaimIsNotAccountState` — **T32's**; must remain provable.
- Boundary cases the measurements require: a real 401 with an empty body and header-borne detail; a 401 then a
  successful re-send; a final 401; a 429 on the re-send; **a transport that throws** (openapi-fetch rejects
  rather than resolving); each required field missing in turn; an unknown status string; and a **non-ACTIVE
  status with a still-valid token**, which is measured-reachable rather than hypothetical.

## Constraints

- **Security.** The account body carries `email` and `accountUuid`. Treat it as PII: no storage, no logging,
  no analytics, no URL. The error type must be unable to carry server detail by construction, not by
  discipline — a message looked up by key from a closed record, as `ReadProfileError` does.
- **Transport.** `openapi-fetch` resolves an HTTP error status but **rejects** when the underlying `fetch`
  throws. The call needs a `try/catch`; T30 shipped without one and a raw `TypeError` carrying an internal
  hostname escaped.
- **Middleware order.** If a client is constructed anywhere in T31's own code, the rate-limit notice is
  registered **first** so it runs **last**. `onResponse` runs in reverse registration order.
- **Module boundaries.** The reader owns neither the token's lifetime nor renewal. Both arrive as parameters.
- **Null handling.** All five fields are contract-required, so absence is an error — the opposite of T30,
  where absence was normal. A body that is JSON `null` must not reach a property access: that exact shape
  threw a raw `TypeError` out of `readProfile` and was a shipped defect found in T30 phase 11.
- **Thread-safety / transactions.** Not applicable. One async call, no shared mutable state of its own.
- **No route, no screen, no copy.** Out of scope by owner decision.

## Open Questions

**No blockers.** Both substantive questions were decided by the owner on 2026-10-31: T31 is **read-only** with
the non-ACTIVE gap logged as **OQ-T31-3**, and `auth.yaml`'s missing root-level `security` requirement plus its
undocumented 401 are **logged, not fixed** (OQ-T31-1, OQ-T31-2).

One caution carried into implementation rather than left as a question: **AC1 must not assert a security
property the contract does not express.** Because `auth.yaml` declares no global `security`, `GET /accounts/me`
is nominally unauthenticated in the document while the server requires a bearer. T30 phase 6 made exactly this
mistake in `oidc.yaml` and the assertion survived a review because it was only ever textual. AC1 asserts the
**operation and its wiring**; the bearer is asserted behaviourally by AC3, through a real boundary.

---

**Phase 2 complete. This brief is the only specification Phases 5–13 use.** Phase 3 is Kimi 2.7's design
challenge.
