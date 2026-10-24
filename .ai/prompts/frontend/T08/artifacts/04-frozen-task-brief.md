# frontend · T08 · Phase 4 — Frozen Task Brief (Human Approval gate)

STATUS: FROZEN (2026-10-24)

Consumes `02-task-implementation-brief.md` and `03-design-challenge.md` (Kimi 2.7: 8 findings; 1 Medium, 6 Low, 1
Info). Each was checked against source before it was decided. **Two questions went to the owner** — both were live
decisions, not restatements — and both were answered; the rest are settled here, as at T07's gate.

## Owner decisions (2026-10-24)

1. **R23's no-replay clause is 429-scoped; T05's single re-send stays.** R23's `WHEN` is the 429 case, and
   `agents.md:64-65` places the sentence inside the 429-handling rule. A 401'd request was rejected before business
   logic, and the one re-send is the mechanism L18/R21 require. **AC3 pins that boundary**, and the case carries the
   rationale as a comment. If a later reader disputes it, it is a spec question, not a T08 change.
2. **POST coverage is a named subset end-to-end plus a contract-derived drift guard** (test-only; no production
   code). This also answers Kimi's standing-guard question.

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| 1 | The POST inventory would be hand-maintained and drift from the contract | **ACCEPTED, but its premise is wrong and its fix is superseded.** Kimi says "the test file can only read the compiled client"; T05's suite already reads `contracts/api/auth.yaml` from disk (`parallel401sTriggerExactlyOneRenewal.test.ts:513`) and asserts `AUTH_PUBLIC_OPERATIONS` equals the contract's `security: []` operations. So neither CI machinery (a) nor a stale "derived at commit X" comment (b) is needed: **the drift guard is an in-test assertion** that every POST operation parsed from `auth.yaml` appears in T08's case table, failing the moment a POST is added. |
| 2 | The uncloneable-body case has no stated technique | **ACCEPTED; the technique already exists in-repo.** `vi.spyOn(Request.prototype, 'clone').mockImplementation(() => { throw … })` (T05 test `:532`). Copied locally, as the house rule requires, with a comment next to the AC4 case. |
| 3 | The standing-guard question is unresolved | **RESOLVED by owner decision 2: cases only, with the contract drift guard as the standing part.** No production code, so the "no new production output" scope holds. |
| 4 | The T05/R23 boundary needs pinning | **ACCEPTED** — owner decision 1. Recorded here and as a comment on the AC3 case. |
| 5 | AC5 is an inspection note, not a test | **ACCEPTED.** `Object.keys(notice.middleware)` must equal `['onResponse']`, which fails if an `onRequest` or `onError` is ever added to the 429 path. The behavioural half (non-401 statuses untouched) is already covered by T07's cases `:463` and `:478`, so it is not duplicated. |
| 6 | The file is becoming a monolith | **ACCEPTED.** T08's cases go in a nested `describe('no POST replay', …)` inside the existing `describe`, which leaves the named test string and T07's 29 cases untouched. |
| 7 | No privacy assertion on a POST body | **ACCEPTED.** The POST cases send a body containing an obvious marker string and assert it reaches no console level, neither storage, nor cookies, nor the URL. Stronger than T07's AC8, which used a GET. |
| 8 | "Representative set" is undefined | **ACCEPTED; two of its four suggested operations do not exist.** `/accounts/me/sessions` has only a `get` (`auth.yaml:129`) and the confirm path is `/accounts/me/mfa/totp/confirm` (`:193`), not `/accounts/totp/confirm`. The set is now named below from the real contract, chosen to cover every request shape. |

Open Questions: none. The Phase 1/2 interpretations stand, with Open Question 2 now an owner decision.

---

## Frozen brief

### Task
T08 — **No POST replay.** A 429 on a non-idempotent POST is never retried automatically. Verifies
`rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.

### Purpose
Pin the half of R23 that T07 left out: a rate-limited write is never sent again by the SPA itself. T07 proved the
copy; T08 proves the absence of a retry on the request shape where a retry would cause real harm, and marks T05's one
401-driven re-send as the boundary of that guarantee.

### Scope
**In**
- T08 cases in the existing `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx`, under a nested
  `describe('no POST replay', …)` (finding 6).
- **The named POST set**, all from `contracts/api/auth.yaml`, covering every request shape (finding 8):
  - the five public ones — `POST /accounts`, `/accounts/verify-email`, `/accounts/resend-verification`,
    `/accounts/password-reset-request`, `/accounts/password-reset` (JSON body, no bearer);
  - `POST /api-keys` (protected, required JSON body `{ name }`);
  - `POST /accounts/me/password` (protected, required JSON body `{ currentPassword, newPassword }` — the sensitive
    body for finding 7);
  - `POST /accounts/me/mfa/totp` (protected, **no** request body, `:164`);
  - `POST /admin/accounts/{accountUuid}/activate` (protected, **path parameter**, no body, `:267`).
- **The contract drift guard** (finding 1): parse every `post:` operation out of `auth.yaml` and assert each one is
  either in the named set or in an explicitly listed, commented remainder, so adding a POST to the contract fails this
  test.
- The 401 → renewal → re-send → 429 sequence on a POST (AC3), with the rationale as a comment.
- Mutation breaks that would introduce a replay.

**Out**
- **Any production change.** T08 adds no production file and edits none (owner decision 2).
- **T07's copy, notice state, component, catalogue** — frozen, task 7 owns them.
- **T05's re-send and T06's cross-tab renewal** (L12): bounded by the cases, never edited.
- **Any retry, backoff or queue**, and anything persisted (§5).
- **`Retry-After` and the 429 body** (Q12(b): status alone).
- **SAS-hosted page 429s** (R23).
- **Idempotency keys / server change** — `auth.yaml` defines none; contracts are read-only here.
- **Mounting anything in the running app** (T01 asserts an empty shell).

### Business Rules
- **R23, this task:** a 429 on a non-idempotent POST is never replayed automatically by the SPA.
- **R23:** nothing resumes on a timer or on a later success; only the user's own action resumes work.
- **R23 / Q12(b):** detection by status alone.
- **`agents.md` API access:** the boundary owns 429 handling; screens never add a retry.

### Locked Decisions
- `agents.md` API access and errors (LOCKED).
- **L6** generated client only · **L18 / R21** one renewal per tab, T05's single re-send part of it · **L12** T05–T07
  untouched · **§5 / L16** nothing persisted · **L11 / O3** catalogue-only strings (T08 adds none).
- **R23 read as 429-scoped** (owner decision 1).

### Dependencies
Existing only: `createAuthApi`, `AUTH_PUBLIC_OPERATIONS`, `createSessionBoundary`, `SessionTokens`,
`createRateLimitNotice`, `createI18n` + `RateLimitNotice` for cases that render; Vitest + jsdom; the T05 helpers
copied locally. **No new dependency, config key or contract.**

### Inputs
429 responses to POST calls made through a client carrying the boundary's middleware, with and without the tab's
notice; plus a 401-then-429 sequence on the same POST.

### Outputs
No new production output. Asserted facts: one transport call per rate-limited POST call (two across a 401-driven
renewal, never three), each body sent once, and the 429 returned to the caller unchanged.

### State Changes
None.

### Files to Create
None.

### Files to Modify
- `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` — add the nested `describe`; T07's 29
  cases stay exactly as they are.

### Files NOT to Modify
`sessionBoundary.ts`, `crossTabRenewal.ts`, `rateLimitNotice.ts`, `RateLimitNotice.tsx`, `client.ts`, `i18n/*`,
`AppShell.tsx`, `mountShell.tsx`, `routes.tsx`, `main.tsx`; every T01–T07 test except the shared named-test file;
`.oxlintrc.json`; `frontend/package.json`; `pnpm-lock.yaml`; `libs/**`, `contracts/**`, `spec/**`, `services/**`,
`infra/**`; `prompt`.

### Acceptance Criteria
- **AC1:** a 429 on each operation in the named POST set gives **exactly one** transport call for that call, for the
  public ones with no bearer and the protected ones with one; and with no notice passed, the same holds.
- **AC2:** each recorded body appears exactly once across the whole run of a case — the body is sent once and never
  re-sent.
- **AC3:** 401 → one renewal → one re-send → 429 on the re-send gives exactly **two** transport calls, the second
  carrying the fresh bearer and the original body, with no third call, the 429 returned to the caller, and (with a
  notice) the T07 copy shown.
- **AC4:** a 429 on a POST whose `clone()` throws returns the 429 unchanged, re-sends nothing, and still raises the
  notice.
- **AC5:** `Object.keys(notice.middleware)` is exactly `['onResponse']`, and that `onResponse` returns `undefined`.
- **AC6:** after a 429 on a POST, dismissing the notice and advancing an hour of faked time produce no further
  transport call, and no `setTimeout`/`setInterval` is created while handling it.
- **AC7:** every `post:` operation in `auth.yaml` is accounted for by the case table or its commented remainder (the
  drift guard).
- **AC8:** a POST body containing a marker string never reaches any console level, `localStorage`, `sessionStorage`,
  cookies or the URL.
- **AC9 (L12):** the T05 POST renewal path still ends 200 with one renewal and one re-send; T01–T07 suites stay
  green, and T07's 29 cases are unedited.

### Required Tests
Added to `rateLimitedResponseShowsGenericCopyWithoutReplay`, nested `describe('no POST replay', …)`:
1. AC1/AC2, the five public POSTs (table-driven): one call each, no bearer, body once.
2. AC1/AC2, the four protected shapes: JSON body, sensitive body, no body, path parameter.
3. AC1, the same with no notice passed to `createAuthApi`.
4. AC3, the 401 → renewal → re-send → 429 sequence, with the rationale comment.
5. AC4, `clone()` throwing (T05's `vi.spyOn(Request.prototype, 'clone')` technique, copied locally).
6. AC5, the middleware's own shape.
7. AC6, no resumption after dismissal or over an hour, and no timer.
8. AC7, the contract drift guard, parsing `auth.yaml` the way T05's public-operations case does.
9. AC8, the marker-string body.
10. AC9, T05 POST parity.

**Mutation breaks:** a replay inside the notice middleware (T07's `M7`, re-checked for a POST); the boundary's
`!== 401` guard dropped, so a 429 re-sends; the clone re-sent twice; a `setTimeout` retry after a 429; the drift
guard's parse weakened (e.g. matching only public operations).

### Constraints
`strict` TypeScript; `typecheck`, `lint` and `build` stay green; no global `fetch` outside `vi.stubGlobal`; no new
module-level mutable state; nothing stored or logged; no reading of the 429 body or headers; helpers stay local to the
file; T07's cases untouched.

### Open Questions
No blockers.
