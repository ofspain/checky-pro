# frontend · T08 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T08 — No POST replay |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

### Task
T08 — **No POST replay.** A 429 on a non-idempotent POST is never retried automatically. Verifies
`rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.

### Purpose
Pin the half of R23 that T07 deliberately left out: that a rate-limited **write** is never sent again by the SPA
itself. T07 proved the copy and the one-notification behaviour; this task proves the absence of a retry on the one
request shape where a retry would cause real harm — a non-idempotent POST — and marks the single legitimate re-send
(T05's one re-send after a 401 renewal) as the boundary of that guarantee.

### Scope
**In**
- Cases in the existing `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` covering: a 429 on a public and a
  protected non-idempotent POST; the body sent exactly once; the 401 → renewal → re-send → 429 sequence; an
  uncloneable POST body; no resumption after dismissal or over time; and that the 429 reaches the caller unchanged.
- The POST inventory from `contracts/api/auth.yaml` (19 POST operations, 5 public) as the source for which operations
  the cases name.
- Mutation breaks that would introduce a replay, each shown to fail at least one case.

**Out**
- **Any change to T07's copy, notice state, component or catalogue** — task 7 owns them and they are frozen.
- **Any change to T05's re-send or T06's cross-tab renewal** (L12). The 401 re-send is in scope only as something the
  cases bound, never as something this task edits.
- **A client-side retry, backoff or queue of any kind** — R23 forbids it, and §5 forbids persisting one.
- **`Retry-After` and the 429 body** (Q12(b): status alone).
- **SAS-hosted page 429s** (R23: out of SPA scope).
- **Idempotency keys or server-side change** — `auth.yaml` defines none, and contracts are read-only here.
- **Mounting anything in the running app.** T01 still asserts an empty shell.

### Business Rules
- **R23:** a 429 on a non-idempotent POST is never replayed automatically by the SPA.
- **R23:** work resumes only on user action; nothing resumes on a timer or on a later success.
- **R23 / Q12(b):** detection is by status alone; the 429 body and `Retry-After` are never read.
- **`agents.md` API access:** the boundary owns this; screens never add their own retry.

### Locked Decisions
- `agents.md` API access and errors (LOCKED).
- **L6:** generated client only.
- **L18 / R21:** one renewal per tab, shared across tabs; T05's single re-send is part of that mechanism.
- **L12:** T05–T07 behaviour and suites unchanged.
- **§5 / L16:** nothing persisted; no retry queue.
- **L11 / O3:** catalogue-only strings (T08 adds none).

### Dependencies
`@themistra/api-client` (`createAuthClient`); `createAuthApi`, `AUTH_PUBLIC_OPERATIONS` (`client.ts`);
`createSessionBoundary`, `SessionTokens`, `Middleware` (`sessionBoundary.ts`); `createRateLimitNotice`
(`rateLimitNotice.ts`); `createI18n` + `<I18nextProvider>` and `RateLimitNotice` for any case that renders.
Vitest + jsdom. No new dependency, config key or contract.

### Inputs
HTTP 429 responses to POST calls made through a client carrying the boundary's middleware, with and without the
tab's rate-limit notice; plus a 401-then-429 sequence on the same POST.

### Outputs
No new production output. The observable facts the cases assert: one transport call per rate-limited POST call (two
across a 401-driven renewal, never three), the body sent once, and the 429 returned to the caller unchanged.

### State Changes
None. T07's one `shown` boolean per tab is the only state in the path, and this task does not touch it.

### Files to Create
None expected. (If Phase 3/4 decides a standing guard is wanted, it would be an addition to the existing test file,
not a new production module — see Open Questions.)

### Files to Modify
- `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` — add the T08 cases to the existing
  `describe`, leaving T07's 29 cases as they stand.

### Files NOT to Modify
`sessionBoundary.ts`, `crossTabRenewal.ts`, `rateLimitNotice.ts`, `RateLimitNotice.tsx`, `client.ts`, `i18n/*`,
`AppShell.tsx`, `mountShell.tsx`, `routes.tsx`, `main.tsx`; every T01–T07 test except the shared named-test file;
`.oxlintrc.json`; `frontend/package.json`; `pnpm-lock.yaml`; `libs/**`, `contracts/**`, `spec/**`, `services/**`,
`infra/**`; `prompt`.

### Acceptance Criteria
- **AC1:** a 429 on a public POST (`POST /accounts/password-reset`) and on a protected POST (`POST /api-keys`) gives
  exactly one transport call each, with and without a notice passed to the client.
- **AC2:** across a representative set of `auth.yaml` POST operations, each 429 gives exactly one call, and the
  recorded request bodies show each body sent once.
- **AC3:** 401 → one renewal → one re-send → 429 on the re-send gives exactly **two** transport calls, the second
  carrying the fresh bearer and the original body, with no third call and the 429 returned to the caller.
- **AC4:** a 429 on a POST whose body cannot be cloned returns the 429 unchanged and re-sends nothing.
- **AC5:** the 429 path adds no `onRequest` and no `onError`, and its `onResponse` returns `undefined`; the boundary
  takes no action for any status other than 401.
- **AC6:** after a 429, no further transport call occurs when the notice is dismissed, nor over an hour of faked
  time; no `setTimeout`/`setInterval` is created while handling it.
- **AC7:** the T05 POST path still renews once and re-sends once to a 200; the T01–T07 suites stay green and unedited
  (the shared named-test file keeps T07's 29 cases intact).

### Required Tests
`rateLimitedResponseShowsGenericCopyWithoutReplay` gains the nine cases listed in Phase 1 §"Tests required"
(AC1 ×2, AC2 inventory + bodies, AC3 the renewal sequence, AC4 uncloneable, AC5 structural, AC6 no resumption,
AC7 T05 parity), using the file's existing local helpers. Mutation breaks: a replay inside the notice middleware; the
boundary's `!== 401` guard dropped; the clone re-sent twice; a `setTimeout` retry after the 429.

### Constraints
- `strict` TypeScript; the build, `typecheck` and `lint` stay green; no global `fetch` outside `vi.stubGlobal`.
- No new module-level mutable state; no storage, logging or URL use; no reading of the 429 body or headers.
- Thread-safety / transactions: not applicable (single-threaded browser; no persistence).
- Null handling: an uncloneable body is a real `null` clone path in `sessionBoundary.ts` and AC4 covers it.
- Module boundaries: the cases live in `src/api`; no screen or shell file is touched.
- Performance: not applicable.

### Open Questions
Two, both for the Phase 4 gate; neither blocks the cases, which are identical under either answer.
1. **Cases only, or also a standing guard?** T07's frozen brief assigns task 8 "the POST no-replay guarantee and its
   cases", and today's code already satisfies it. Default reading: cases only, with AC5 as the structural assertion.
2. **Is T05's 401-triggered re-send of a POST within R23's no-replay clause?** Default reading: **no** — R23's `WHEN`
   is the 429 case, `agents.md:64-65` places the sentence inside 429 handling, and a 401'd request never reached
   business logic. AC3 pins that boundary. If the author reads R23 as forbidding it, that changes T05, which is a
   spec question and not a T08 change.
