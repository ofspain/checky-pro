# frontend · T11 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

### Task
T11 — **API routes are NetworkOnly.** No API response is served from cache. Verifies
`serviceWorkerNeverCachesApiResponses`. Cites R57, L17.

### Purpose
T09 and T10 settled what the worker does with *navigations*; both modules return `false` for anything else by
design, leaving this task the whole of the request side. The rule is sharper than staleness: the Cache Storage API is
client-side storage, and `agents.md:84-85` names the bodies that must never enter it — account, token, receipt,
evidence. An API response in a cache is therefore a storage violation against L3 and L16, readable by any later
script in scope and surviving the tab that fetched it. This task writes the policy that keeps them out, and the rule
that makes a *new* backend prefix safe by default rather than safe only if someone remembers it.

### Scope
**In**
- `frontend/src/pwa/apiRequestPolicy.ts`: the twelve edge prefixes as a verbatim, drift-guarded list; the positive
  cacheability rule (`/app` shell and `/app/assets/*` only); and the pure predicates task 12 will hand to Workbox.
- `frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts`: AC1–AC8, with a harness that simulates a
  *request* route (NetworkOnly must touch no cache; a static asset may).
- Mutation breaks that would let an API response into a cache.

**Out**
- **Registering or emitting a worker** — T09's gate decision D1; task 12.
- **Any change to `navigationDenylist.ts` or the T09/T10 tests** (L12). The navigation side is settled; if the two
  policies disagree, that is a finding for the gate, not a licence to edit.
- **The precache manifest guard** — task 12 owns the manifest (carried from T09/T10).
- **The update policy** — task 12.
- **Anything about the SSE reader itself** — R59's stream task owns the client; this task only classifies the path.
- **`spec/`, `contracts/`, `services/`, `infra/`, `.oxlintrc.json`, `vite.config.ts`.**

### Business Rules
`agents.md:90` (NetworkOnly), `:84-85` (sensitive bodies never cached), R57 (`requirements.md:203-205`),
`design.md:194` (§5: static shell and assets only).

### Locked Decisions
L17 (`design.md:61-62`), L9 (one origin, §4c (2) edge map), L14, L3, L16, L12, L6.

### Dependencies
Vitest + jsdom; `spec/frontend/design.md` read from disk for AC7. No new package, no contract, no config key.

### Inputs / Outputs / State
Input: a request's URL and, for AC4's sake, its method and mode — none of which may change the answer for an API
path. Output: a cacheability decision plus the exported prefix list. State: none; the module is pure, which also
keeps `.oxlintrc.json` untouched.

### Files to Create
`frontend/src/pwa/apiRequestPolicy.ts`, `frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts`.

### Files to Modify
None expected.

### Files NOT to Modify
`navigationDenylist.ts`, `sasNavigationBypassesServiceWorker.test.ts`,
`endSessionNavigationBypassesServiceWorker.test.ts`, every T01–T08 file, `vite.config.ts`, `.oxlintrc.json`,
`libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`.

### Acceptance Criteria
AC1–AC9 exactly as enumerated in `artifacts/01-specification-extraction.md`: the twelve prefixes and their
sub-paths NetworkOnly (AC1); `/app` shell and assets cacheable so AC1 is not vacuous (AC2); the four named sensitive
bodies covered by name (AC3); method- and mode-independence, including agreement with T09 where an API path is also
a navigation (AC4); default-deny for anything unrecognised (AC5); `/notifications` explicitly, because a buffered
stream is broken rather than stale (AC6); the drift guard against `design.md`'s edge map (AC7); purity (AC8); suite
green with no T01–T10 edit (AC9).

### Required Tests
`serviceWorkerNeverCachesApiResponses`, one group per AC.

**Mutation breaks:** drop a prefix; invert the default so unknown paths become cacheable; make the decision depend
on the method; loosen `/app/assets` matching so it swallows a backend path; let `/notifications` be cacheable;
have the harness `put` an API response after fetching it.

### Constraints
- **Default-deny.** Cacheable is the narrow case (`/app` shell and `/app/assets/*`); everything else is NetworkOnly.
  The prefix list exists for task 12's matchers and for the drift guard, not as the decision's basis — so a
  thirteenth prefix at the edge is safe without an edit here.
- **Agree with T09, never contradict it.** For a path that is both an API route and in R57's navigation list
  (`/accounts`, `/api-keys`, `/admin`, `/userinfo`, `/api/`), the two modules must give compatible answers: declined
  for navigation, NetworkOnly for a request. One AC4 case asserts that pairing directly.
- **Pure.** No `fetch`, `caches`, `self`, storage or cross-tab global in the module.
- **`put` matters as much as `match`.** `agents.md:84-85` forbids *storing* those bodies, so the test asserts
  nothing is written, not only that nothing is read. The pure policy cannot enforce that at runtime — task 12's
  wiring must, and that is a carry-forward, stated plainly rather than implied.

### Open Questions
1. **Positive rule or prefix list?** Recommending default-deny *plus* the list as an explicit matcher set — stronger
   than the task statement's literal words, and safe when the edge map grows. **Gate's call**, because it goes
   beyond the statement.
2. **`/notifications`** is in the edge map but not in R57; T09 deferred it here, and as an API route it is
   NetworkOnly. Recorded as that observation's resolution.
3. **Dev-only `/app/@vite/client` and `/app/@react-refresh`** are excluded by treating only `/app/assets/*` and the
   shell as cacheable, without naming them. They do not exist in a production build.
4. **Duplicating T02's `BACKEND_PREFIXES`** — unavoidable under L12; mitigated by AC7's drift guard against the
   spec's own map rather than against T02's copy.
