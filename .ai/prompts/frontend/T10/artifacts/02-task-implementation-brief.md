# frontend · T10 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

### Task
T10 — **End-session navigation bypasses the worker.** `/connect/logout` is never answered from cache. Verifies
`endSessionNavigationBypassesServiceWorker`. Cites R57.

### Purpose
R19 ends the SAS session by navigating the browser to `/connect/logout` with an ID token in the query string and an
allowlisted return address, and says in its own words that this navigation *"is served by SAS, not the SPA shell
(R57)"*. Two things follow that T09's general denylist did not pin: a cached end-session response would show the
signed-out page while the SAS session is still alive — the one worker failure that lies to the user instead of
breaking visibly — and a cached one would write **an ID token into Cache Storage**, outliving the tab that owns it
(L3, §5). This task pins both, and pins the other half of the flow: the `302`'s target, `/app/signed-out`, *is* the
shell's to answer, so sign-out still renders.

### Scope
**In**
- `frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts`: the real end-session URL shape taken from
  the backend's green test, its variants, the token-never-cached assertions, the two-step
  `/connect/logout` → `/app/signed-out` sequence, repetition across tabs, and a drift guard.
- Mutation breaks that would reintroduce a cached end-session response.

**Out**
- **Any change to `navigationDenylist.ts` or T09's test** (L12). If the policy needs a change to pass this task,
  that is a finding, not a licence.
- **Registering or emitting a worker** — gate decision D1; task 12.
- **The precache manifest guard** — task 12 owns the manifest (carried forward from T09 phase 13).
- **API NetworkOnly** — task 11.
- **Building the sign-out control**, clearing token state, or cross-tab propagation — later tasks own R19/R22's
  behaviour; this task only pins the worker's part.
- **Any backend, contract, `spec/` or `infra/` change.**

### Business Rules
R57 (`requirements.md:203-205`); R19 (`:62-65`), whose last sentence is the task's subject; R22 (`:72-75`);
`agents.md:35-36` (sign-out) and `:88-89` (the eleven paths).

### Locked Decisions
L17, L3 (`design.md:191`), §5 (`:194`), L14 (`/app/signed-out` public, `routes.tsx:16`), L18 (`:63`), L12.

### Dependencies
Vitest + jsdom; T09's exports (`isDenylistedNavigation`, `isShellNavigation`, `DENYLISTED_NAVIGATION_PATHS`);
`spec/frontend/agents.md` and `frontend/src/app/routes.tsx` read from disk for AC7. No new package.

### Inputs / Outputs / State
Inputs: URL shapes and a request mode. Outputs: assertions. State: none; the module under test is pure and this task
adds no production code.

### Files to Create
`frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts`.

### Files to Modify
**None expected.** T10 is a verification task over T09's policy: `isDenylistedNavigation` reads `url.pathname`
(`navigationDenylist.ts:55-58`) and `/connect/logout` is in the list, so every end-session URL shape is already
declined. The task's value is proving the flow R19 depends on, and catching it if a later change narrows the policy
to a path without a query string. **If any acceptance criterion below fails against the current module, that is a
genuine defect and phase 6 reports it instead of editing the module.**

### Files NOT to Modify
`navigationDenylist.ts`, `sasNavigationBypassesServiceWorker.test.ts`, every T01–T08 file, `vite.config.ts`,
`.oxlintrc.json`, `libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`.

### Acceptance Criteria
As enumerated in `artifacts/01-specification-extraction.md` — AC1 (the full URL declined), AC2 (the token never
reaches a cache), AC3 (`/app/signed-out` answered by the shell), AC4 (shape invariance across six variants),
AC5 (the two-step sequence, shell key only), AC6 (repetition), AC7 (drift against `agents.md` and `routes.tsx`),
AC8 (suite green, no T01–T09 edit).

### Required Tests
`endSessionNavigationBypassesServiceWorker`, one group per AC, with a JWT-shaped `id_token_hint` so AC2's
"token never appears in a cache key" assertion is about a realistic value.

**Mutation breaks:** remove `/connect/logout` from the list; decide on `url.href` or `url.search` instead of
`url.pathname`; let the shell answer `/connect/logout`; read the cache before the denylist check (harness);
make the shell matcher reject `/app/signed-out`.

### Constraints
- **A local harness copy.** `simulateNavigationFallback` is duplicated into this file (about twelve lines) with a
  comment pointing at T09's, rather than exporting it from T09's frozen test file. Task 12 replaces both with the
  real Workbox route.
- **No token in a snapshot or a failure message.** The fake ID token is a clearly synthetic JWT-shaped literal, so
  nothing resembling a real credential is committed.
- **Honesty about the result.** If T10 ends with no production change, the artifacts say so plainly: the task
  demonstrates that T09's policy covers R19's flow. That is the expected outcome, not a shortfall.

### Open Questions
1. **Test-only?** Default **yes** — cases only, no production change, the T08-over-T07 precedent. Recorded for the
   gate because it determines whether this task has a production footprint at all.
2. **Harness duplication vs sharing** — leaning duplicate, per the constraint above. A gate may prefer a shared
   `src/pwa/__tests__` helper; that would touch T09's file and L12 argues against it.
3. **The `400` non-allowlisted case** — included as one assertion for the *reason* (an error response must not be
   cached either), not as a flow the SPA constructs.
4. **The precache hole** — restated here, still task 12's: `/app/signed-out` is a precached shell route and a
   precache route matches by URL ahead of the navigation fallback.
