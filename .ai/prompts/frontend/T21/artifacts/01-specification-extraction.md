# frontend · T21 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

Extraction only — no design. Every cited line was read.

---

## Business Rules

- **R7** (`requirements.md:23-24`) — on a callback with a matching `state`, the SPA validates `nonce`, exchanges
  the code at `/oauth2/token` with the PKCE verifier, **and SHALL hold the access token in memory only (L3)**.
  The first two clauses are built (T19, T20). **The third clause is this task, and it is the whole task.**
- **R8** (`requirements.md:25-26`) — a missing or mismatched `state` discards, completes no exchange, and returns
  the visitor to the sign-in start with neutral copy. T21 must leave this intact: a token that is never obtained
  must never be held.
- **R21** (`requirements.md:68-71`) — at most one renewal per tab, shared among parallel 401s and sibling tabs,
  routing to clean re-auth on failure. **Already built** (tasks 5 and 6) against an *injected* session. T21
  supplies the thing that was injected, so R21 constrains the holder's shape without being T21's deliverable.
- **R22** (`requirements.md:72-75`) — a failed renewal transitions to clean re-auth with no half-authenticated
  screen. Task 33's. Relevant here only because "no half-authenticated screen" means the holder must have a way
  to *not* hold a token, and that state must be the default.

## Locked Decisions

- **L3** (`design.md:13-20`) — **the decision this task exists to implement.** "Access tokens are never written to
  storage, logs, or analytics." Renewal is silent re-authentication only: no refresh-token grant, no rotation, no
  reuse detection, because SAS issues this client no refresh token (measured). So the holder holds **one access
  token and nothing durable** — there is no refresh token for it to hold even if L3 allowed one.
- **L16** (`design.md:58-60`) — the sessionStorage carve-out is **exhaustive**: `state`, `nonce`, `code_verifier`,
  the post-login return target, and non-sensitive mid-task form input. "This list is the entire carve-out."
  An access token is not on it, which is what makes "in memory only" enforceable rather than aspirational.
- **L18** (`design.md:63-64`) — single-flight renewal and cross-tab coherence. The holder is the `LocalSession`
  that `shareRenewalAcrossTabs` drives, so `adopt()` exists for L18's sake: a sibling's renewed token is taken
  into this tab's memory and nowhere else.
- **L10** (`design.md:46`) — no secret and no non-public environment key in the bundle. T21 reads no config.
- **L13** (`design.md:51-52`) — confirmed stack; **no new runtime dependency.** Note that TanStack Query and
  Zustand are named in L13 but **not installed**, so "module-scoped variable" cannot quietly become a store.
- **L4** (`design.md:21-26`) — the visitor may not learn which failure occurred. T21 adds no copy; it binds only
  because a holder that threw or rendered on absence would create a new way to distinguish failures.
- **L11** (`design.md:47-48`) — catalogue-only strings. T21 should add none.
- **L9** (`design.md:44-45`) and **L6** (`design.md:38-39`) — not engaged: T21 makes no backend call. The call it
  feeds from was built in T20 through `createOidcClient()`.
- **L14** (`design.md:53-56`) — not engaged; T21 adds no route.

## Files involved

**Existing, to read**

| File | Why |
|---|---|
| `src/api/sessionBoundary.ts:11-14` | `SessionTokens` — the interface the holder must satisfy, and `:100`, `:118`, `:127` the places `current()` is called. |
| `src/api/crossTabRenewal.ts:4-6` | `LocalSession extends SessionTokens` with `adopt(token: string): void`. |
| `src/api/crossTabRenewal.ts:43-45`, `:72-75` | `SharedRenewal` and `shareRenewalAcrossTabs`, the L18 consumer. |
| `src/api/client.ts:20-27` | `createAuthApi`, which puts the boundary's middleware on the generated client. |
| `src/auth/codeExchange.ts:35-38`, `:103` | `TokenResponse` and the function that produces the token. |
| `src/auth/CallbackScreen.tsx:122-135` | The only caller; `response.accessToken` is currently unread. |
| `src/auth/signInNotice.ts` | The sanctioned module-scoped-state pattern in this package, with its justification in the doc comment. |

**Existing, to extend**

- `src/auth/CallbackScreen.tsx` — the fulfilment handler must put the token somewhere.
- `src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` — the named test, 69 cases from T20's
  verifier half; T21 adds the in-memory half to the same file.

**New, expected by the spec**

- A module in `src/auth` holding the access token. `package.md` §6's file map does not name it, and `tasks.md`
  task 21 says only "a module-scoped variable", so the filename is Phase 2's to choose.

**Must not be touched**

- Anything under `spec/`. `src/api/sessionBoundary.ts`, `crossTabRenewal.ts`, `client.ts` — all three are built,
  tested and define the contract T21 fills; changing them would be rewriting tasks 5 and 6.
- `src/auth/idTokenNonce.ts` (T19, settled) and `contracts/**` (T21 adds no endpoint).

## Dependencies

- **`SessionTokens`** = `{ current(): string | null; renew(): Promise<string> }`.
- **`LocalSession`** = `SessionTokens` + `adopt(token: string): void`.
- **`TokenResponse`** = `{ idToken: string; accessToken: string }` — note what it does **not** carry: `expires_in`,
  `scope`, `token_type` are on the wire and typed in `contracts/api/oidc.yaml` but dropped at
  `codeExchange.ts:133`, because T20 recorded expiry as T21's question.
- **Config keys: none.** `src/auth` reads no `import.meta.env` and must keep reading none (L10).
- **Contracts:** none newly engaged. `contracts/api/token-claims.md` is listed in the header but describes claims,
  and R20 (profile from `/userinfo`) is task 30's — not this task's.
- **Measured facts that bind the holder** (from T20, against the running service): the token response carries
  `access_token`, `expires_in` (299), `id_token`, `scope`, `token_type`, and **no refresh token**.

## Acceptance Criteria

Mapped to requirement IDs. Phase 2 will narrow the wording; this is the extracted set.

| # | Criterion | From |
|---|---|---|
| AC1 | The access token from a successful exchange is retained in a module-scoped variable. | R7, task 21 |
| AC2 | Retention is proven over **two unrelated token values**, so nothing passes against a constant. | R7 |
| AC3 | `current()` returns `null` before any exchange, and after a failed one. | R7, R22 |
| AC4 | `current()` returns the token after a successful exchange, and the same value on repeated reads. | R7 |
| AC5 | The holder satisfies `SessionTokens` structurally, so `createSessionBoundary` accepts it unchanged. | R21, L18 |
| AC6 | The holder satisfies `LocalSession`, so `shareRenewalAcrossTabs` accepts it unchanged. | R21, L18 |
| AC7 | `adopt()` replaces the held token, and a sibling's token becomes this tab's `current()`. | L18 |
| AC8 | No token value or segment reaches `sessionStorage`, `localStorage`, IndexedDB, a cookie, a URL, the DOM, the tab title or any console channel. | R7, L3, L16 |
| AC9 | The token is held **once** — no second copy in React state, a prop, a `globalThis` property, a closure in another module, or an error message. | task 21's "and nowhere else" |
| AC10 | A discarded callback (R8) holds nothing: a bad `state`, a bad nonce and a refused exchange all leave `current()` null. | R8, R22 |
| AC11 | The three L16 sessionStorage items are unaffected; T21 writes, deletes and clears nothing there. | L16 |
| AC12 | No new user-visible string; no catalogue key added. | L11, L4 |
| AC13 | No `import.meta.env`, no absolute address, no new runtime dependency, and no state library. | L10, L13 |
| AC14 | T19's nonce check and R8's ordering are unchanged: a token is held only after both have passed. | R7, R8 |
| AC15 | The whole suite passes with the one known environmental failure and no other, **verified by the run's exit code**. | — |

## Tests required

**Named:** `callbackValidatesNonceAndKeepsAccessTokenInMemory` (`package.md:111` → **R7**). T20 built the verifier
half (69 cases); T21 builds the in-memory half in the same file. The pairing is the point: the test name asserts
both halves of one sentence in R7.

**Also engaged, and must keep passing unchanged:** `parallel401sTriggerExactlyOneRenewal`,
`siblingTabsShareOneRenewal` (both construct fakes of the very interfaces T21 now implements —
`parallel401sTriggerExactlyOneRenewal.test.ts:80`, `siblingTabsShareOneRenewal.test.ts:114`),
`callbackValidatesNonce`, `callbackWithMismatchedStateIsDiscarded`.

**Boundary tests implied**

- Retention across two unrelated values; absence before, and after every failure path.
- `adopt()` over a held token, and `adopt()` with no token held.
- Structural conformance to `SessionTokens` and `LocalSession` — ideally by *passing the real holder into the real
  `createSessionBoundary` and `shareRenewalAcrossTabs`*, not by a type assertion, since a type assertion proves
  only what the compiler already checked.
- Non-leakage sweeps across both storages, IndexedDB, `document.cookie`, the URL, the DOM, the title and every
  console channel.
- Source scans: no storage API named in the holder, no `console.`, exactly one module-level mutable binding, no
  token in any thrown message.
- **A control in every block** proving the assertion is reached, not merely satisfied.

**`tokensAreNeverWrittenToAnyStorage`** is in the header's named-test list but belongs to **task 22**
(`tasks.md:34`). T21 should not pre-empt it; the overlap is handled under OQ-1.

## Open Questions

**OQ-1 · Where does T21 stop and task 22 begin?** Task 21 says "held in a module-scoped variable **and nowhere
else**"; task 22 says "no token is written to local storage, session storage, IndexedDB, a cookie, a URL, or a
log", with its own named test. Nothing I read enumerates "nowhere else". The reading that makes both tasks
non-empty: **T21 owns singleness** (one copy, in one module variable, with no second home inside the app) and
**T22 owns the storage surfaces** (an exhaustive sweep, including a service-worker cache and the bundle). On that
reading T21 still asserts non-leakage, because a test that proves retention without proving non-leakage would pass
against a holder that also wrote to `localStorage` — but T21's sweep is incidental and T22's is the systematic
one. **Not a blocker**: Phase 2 states the line, Phase 3 challenges it.

**OQ-2 · Does T21 implement `renew()`, when no task owns the renewal mechanism?** This is the real scope question.
`LocalSession` requires `renew(): Promise<string>`, and the mechanism — re-opening `/oauth2/authorize` on the SAS
session cookie in a hidden iframe or background navigation (`agents.md:25-34`, R21) — is implemented by **no task
in `tasks.md` 1-34**. Task 5 built single-flight, task 6 built cross-tab sharing, task 33 handles a *failed*
renewal; all three take an injected session. Three exits:

1. **A throwing seam**, exactly as `codeExchange.ts` was from T17 until T20 filled it. The holder satisfies
   `LocalSession` today, the boundary can be wired, and `renew()` states the truth — the mechanism is not built.
   This is the house pattern and it is already proven in this package.
2. **Implement silent re-authentication inside T21.** Satisfies the interface completely, and takes on an iframe
   flow, a second nonce and state pair, a timeout and a failure route — work no task scopes and that dwarfs the
   task statement.
3. **Narrow the holder to `current()` + a setter**, leaving `LocalSession` unimplemented. Cheapest, but it means
   nothing can be wired to the boundary, and three built modules keep waiting.

**OQ-3 · Does T21 wire the composition root?** Nothing in production calls `createSessionBoundary`,
`createAuthApi` or `shareRenewalAcrossTabs`; `mountShell.tsx:12-30` builds i18n, the shell and the router and
stops. Their doc comments describe "production wiring" that does not exist, and no task in `tasks.md` is visibly
the wiring task. T21 is the first task with something to wire. **Not necessarily T21's** — the task statement is
about where the token lives, not about assembling the app — but if T21 declines it, the holder has exactly one
real consumer (the callback screen) and the boundary stays unwired for a task that does not exist yet.

**OQ-4 · Does the holder keep anything besides the access token?** `expires_in` is dropped at
`codeExchange.ts:133` and L18's renewal will eventually need it; the ID token is dropped after T19's nonce check.
`LocalSession.adopt(token: string)` and `current(): string | null` both model a **bare string**, so the built
consumers assume no expiry and no identity. Adding either would widen an interface tasks 5 and 6 already depend
on.

**None of these cites a `package.md` §11 open question**, and none is blocked by one: Phase 1a's own blockers
(Q1, Q5, Q6, Q10, Q12, Q13, Q14, Q15) are all resolved. Q5 is the nearest relative — it is where the owner
accepted the silent re-authentication model with no refresh token — and it is **closed**. The gap OQ-2 names is
not an unanswered question in the spec; it is a task that was never written down.

---

## Next — Phase 2 — task implementation brief.
