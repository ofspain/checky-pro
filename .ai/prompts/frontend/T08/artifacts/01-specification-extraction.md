# frontend · T08 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T08 — No POST replay |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement (verbatim):**
> **No POST replay.** A 429 on a non-idempotent POST is never retried automatically. Verifies `rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.

## Business Rules

- **R23 (the clause this task owns):** when an SPA-originated call returns 429, the SPA shall **not** replay
  non-idempotent POST requests automatically (`requirements.md:76-79`).
- **R23 (sibling clauses, already built by T07, context only):** the 429 shows fixed generic copy with no countdown or
  interval, and work resumes only on user action. SAS-hosted page 429s are out of SPA scope.
- **R23 (`Retry-After`):** the header is not in `auth.yaml`; `package.md` §11 Q12(b) resolved this — detection is by
  **status alone**, with no dependence on the body or `Retry-After`.
- **`agents.md`, API access:** the client boundary owns fixed generic 429 handling, and that handling has "no
  automatic replay of non-idempotent POST requests"; screens do not reimplement it (`agents.md:64-65`). The
  no-replay sentence sits **inside** the 429-handling rule.

## Locked Decisions

- **`agents.md` API access (LOCKED):** every backend call goes through the generated client; the boundary owns 401
  renewal, 429 handling and network-failure states; screens don't reimplement them.
- **`agents.md` errors (LOCKED):** RFC 9457 problem documents; the UI maps problem types to copy and never shows
  server text.
- **L6:** generated client only; no hand-written `fetch` to a backend route (enforced by the two lint rules, T04).
- **L18 / R21:** at most one renewal per tab, shared across tabs. T05's single re-send after a renewal is part of
  this, and it is the **only** re-send in the SPA.
- **L12:** phase additivity — T05, T06 and T07 behaviour and their suites stay unchanged.
- **L11 / O3:** catalogue-only strings and a live region (already satisfied by T07; T08 adds no copy).
- **§5 / L16:** nothing persisted client-side; no retry queue may be introduced to satisfy anything here.
- **Q12(b) (resolved):** no dependence on the 429 body or `Retry-After`.

## Files involved

**Existing, to read:**
- `frontend/src/api/sessionBoundary.ts` — the only re-send in the SPA (`:118` the 401 guard, `:127-129` the one
  re-send through `clientOptions.fetch`, `:101` the clone, `:104` the `null`-clone case).
- `frontend/src/api/rateLimitNotice.ts` — the 429 middleware: no `onRequest`, no `onError`, `onResponse` returns
  `undefined` (`:40-43`).
- `frontend/src/api/client.ts` — registration order (`:24-25`) and `AUTH_PUBLIC_OPERATIONS` (`:6-12`).
- `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` — **the file this task extends**
  (T07's 29 cases; AC6 already pins one transport call per call on a 429).
- `contracts/api/auth.yaml` — 19 POST operations, 5 public.

**New files the spec expects:** none. `tasks.md` task 8 names the same test as task 7, and `package.md` §8 maps that
one test to R23, so the deliverable is cases in the existing file. Whether a standing structural guard also belongs
is an open question below, not an assumption.

**Files that must not change:** `sessionBoundary.ts`, `crossTabRenewal.ts`, `rateLimitNotice.ts`,
`RateLimitNotice.tsx`, `client.ts`, `i18n/*`, `AppShell.tsx`, `mountShell.tsx`, `routes.tsx`, `main.tsx`, every
T01–T07 test other than the shared named-test file, `.oxlintrc.json`, `libs/**`, `contracts/**`, `spec/**`,
`services/**`, `infra/**`.

## Dependencies

- `@themistra/api-client` → `createAuthClient()`; the `Middleware` type via `sessionBoundary.ts`.
- `createSessionBoundary(tokens, onRenewalFailed, { timeoutMs })`, `SessionTokens`, `AUTH_PUBLIC_OPERATIONS`,
  `createAuthApi(boundary, rateLimit?)`, `createRateLimitNotice()`.
- Vitest + jsdom; `react-dom/client` + `act` and `<I18nextProvider>` only if a case renders the notice.
- No new dependency, no config key, no contract change.

## Acceptance Criteria

- **AC1 (R23):** a 429 on a non-idempotent POST results in **exactly one** transport call for that call — no retry,
  for a public POST (`POST /accounts/password-reset`) and a protected one (`POST /api-keys`), with and without a
  notice passed to the client.
- **AC2 (R23):** the POST body is sent once and never re-sent after a 429: no second request carries the same body.
- **AC3 (R23, L18):** on the one sequence where a POST legitimately goes twice — 401, renewal, then the single
  re-send — a 429 **on that re-send** ends the call: exactly two transport calls in total, never three, and the 429
  reaches the caller.
- **AC4 (R23):** a 429 on a POST whose body cannot be cloned is returned as-is, with nothing re-sent.
- **AC5 (R23, `agents.md` API access):** no middleware registered on the client re-sends anything on a 429 — the
  notice middleware has no `onRequest`/`onError` and returns `undefined`, and the boundary returns early for any
  status other than 401.
- **AC6 (R23):** nothing resumes by itself after a 429: no timer, and dismissing the notice re-sends nothing.
- **AC7 (L12):** T05–T07 behaviour is unchanged — a 401 on a POST still renews once and re-sends once, and the
  T01–T07 suites stay green and unedited.

## Tests required

**`rateLimitedResponseShowsGenericCopyWithoutReplay`** (the existing file; T08 adds its POST cases):
1. AC1, a public non-idempotent POST gets a 429 → one transport call, no retry.
2. AC1, a protected non-idempotent POST gets a 429 → one transport call, with and without a notice.
3. AC1, an inventory case: every POST operation the SPA can reach (or a named representative set, drawn from
   `auth.yaml`) returns 429 → one call each.
4. AC2, the body is sent exactly once (assert on the recorded bodies, not just the count).
5. AC3, 401 → renewal → re-send → 429: exactly two calls, the second carrying the fresh token and the original body,
   and no third call.
6. AC4, an uncloneable POST body with a 429.
7. AC5, a structural assertion that the 429 path has no `onRequest`/`onError` and returns `undefined`.
8. AC6, a 429 followed by dismissing the notice and by a long span of faked time → no further transport call.
9. AC7, the T05 POST renewal path still works (one renewal, one re-send, 200).

**Mutation breaks to prove the cases have teeth** (the T07 set already covers `M7`, "a replay on 429"): a replay on
429 inside the notice middleware; a replay inside the boundary (dropping its `!== 401` guard); re-sending the clone
twice; a `setTimeout`-driven retry after the 429.

## Open Questions

1. **Does T08 have a production deliverable, or is it cases only?** T07's frozen brief assigns "the POST no-replay
   guarantee and its cases" to task 8, and the behaviour is already true of today's code (`sessionBoundary.ts:118`,
   `rateLimitNotice.ts:40-43`). Cases-only is the reading I would proceed with; a standing guard (a source scan like
   T04's, or an assertion over the registered middleware) is the alternative. **Gate decision, Phase 4.** Not a
   blocker: the cases are the same either way.
2. **Does T05's 401-triggered re-send of a POST fall under R23's no-replay clause?** R23's `WHEN` is the 429 case and
   `agents.md:64-65` places the sentence inside 429 handling, so my reading is **no** — a 401'd request was rejected
   before it reached business logic, and the re-send is the mechanism L18/R21 require. AC3 pins the boundary of that
   reading (the re-send happens once, and a 429 on it ends the call). **If the author intends R23 to forbid
   re-sending a POST even after a 401, T05's design changes and that is not a T08 change** — it would be a spec
   question. Flagged here rather than decided silently.
3. **No live re-probe available.** The Docker daemon is down, so the 429 surface cannot be re-probed this session; the
   T07 phase 4 probe stands (`POST /accounts/password-reset`, `Retry-After: 11`). Not a blocker for cases that stub
   the transport.
