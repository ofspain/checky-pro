# frontend · T09 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

### Task
T09 — **Denylisted navigations bypass the worker.** Navigations to the paths in `agents.md` are never answered from
cache or with the shell. Verifies `sasNavigationBypassesServiceWorker`. Cites R57, L17.

### Purpose
The SPA is about to acquire a service worker with an offline shell. A shell fallback that answers *every* navigation
would break the identity flow outright: a browser navigating to `/oauth2/authorize`, `/login` or `/connect/logout`
would be handed the SPA's `index.html` instead of reaching the authorization server, and the user could never sign in
or out. R57 and L17 name the eleven paths that must always reach the network. This task writes that boundary as the
worker's navigation rule and pins it with the named test, before any task turns the worker on.

### Scope
**In**
- A single-purpose module that decides, for one request, whether the worker handles it: the eleven denylisted path
  prefixes from `agents.md`, applied to **navigation** requests only.
- The worker's navigation behaviour expressed through that module: a denylisted navigation is declined — no cache
  read, no shell — and an `/app` navigation gets the shell.
- `sasNavigationBypassesServiceWorker`, exercising the rule against stubbed worker globals (jsdom has no
  `ServiceWorkerGlobalScope`, `FetchEvent` or `caches`), plus a drift guard that parses the list out of
  `spec/frontend/agents.md`.
- Mutation breaks: a dropped path, loosened matching, an unconditional shell fallback, a cache read before the
  denylist check.

**Out**
- **Registering the worker, emitting `sw.js`, or wiring `vite-plugin-pwa`.** T01 asserts over a real build that no
  `sw.js` and no `manifest.webmanifest` exist, that no built asset mentions `serviceWorker`, and that mounting
  registers nothing (`shellBuildsAndMounts.test.tsx:74-80, 103-116`). Under L12 those assertions are not this task's
  to edit; emission and registration belong with the task that owns the update policy (task 12). **This is Open
  Question 1 and the Phase 4 gate's call.**
- **API request handling / NetworkOnly** — `agents.md:90`, task 11.
- **Precaching, the offline shell's contents, the manifest, update prompts, `skipWaiting`/`clients.claim`** — tasks
  10 and 12.
- **Any change to the edge** (`infra/local/edge/Caddyfile`) or to `spec/`, both read-only here.
- **Caching any API or identity response** (§5, L17).
- **T01–T08 source and tests** (L12).

### Business Rules
- **R57** — a navigation to `/oauth2/`, `/connect/logout`, `/login`, `/logout`, `/error`, `/.well-known/`,
  `/userinfo`, `/accounts`, `/api-keys`, `/admin` or `/api/` is never answered from cache and never answered with the
  shell.
- **`agents.md:88-89`** — the same eleven paths, as the service's standing rule.
- **§5** — the worker cache holds the static shell and assets only.

### Locked Decisions
- **L17** — service-worker boundaries: those navigations bypass the fallback; API routes are NetworkOnly (task 11).
- **L13** — `vite-plugin-pwa` (Workbox) is the chosen mechanism, so the module must be shaped to plug into a Workbox
  navigation route (a `denylist`-style predicate), not into a hand-rolled `fetch` listener.
- **L14** — every SPA route lives under `/app`, disjoint from the backend prefixes.
- **L12** — phase additivity.
- **L6 / L10 / L3 / L16** — generated client only; no env keys or absolute URLs in app source; no token storage.

### Dependencies
- Vitest + jsdom, with hand-stubbed worker globals (the T05 Web Locks and T06 BroadcastChannel precedent).
- `spec/frontend/agents.md` read from disk for the drift guard (T05/T08 precedent).
- **No** `vite-plugin-pwa` install while Open Question 1 stands: the decision module is plain TypeScript over a URL
  and a request mode, and needs no Workbox at runtime or in tests.
- No contract, no config key, no backend dependency.

### Inputs
A request's URL path and its `mode` (`'navigate'` or not). Nothing else — no headers, no body, no cookies.

### Outputs
A boolean decision (handle / decline), and the worker's navigation route built from it.

### State Changes
None. The decision is a pure function; no cache is written, no storage touched, no channel opened.

### Files to Create
- `frontend/src/pwa/navigationDenylist.ts` — the eleven prefixes and the decision function (`design.md` §6:
  `pwa/ service worker, denylist (L17, R57)`).
- `frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts` — the named test.

### Files to Modify
None expected. `.oxlintrc.json` only if the module is found to need a banned global, which the pure-function design
avoids.

### Files NOT to Modify
`sessionBoundary.ts`, `crossTabRenewal.ts`, `rateLimitNotice.ts`, `RateLimitNotice.tsx`, `client.ts`, `i18n/*`,
`AppShell.tsx`, `mountShell.tsx`, `routes.tsx`, `main.tsx`, `vite.config.ts`; every T01–T08 test;
`libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`.

### Acceptance Criteria
- **AC1 (R57, L17)** — each of the eleven paths, and a sub-path of each, is declined when navigated to.
- **AC2 (R57, L17)** — `/app/`, `/app/sign-in` and a deep `/app` route are answered with the shell, so AC1 is not
  vacuous.
- **AC3 (R57)** — the decision reads only the path and `mode === 'navigate'`; a denylisted navigation triggers no
  cache lookup, asserted with a spy that is never called.
- **AC4 (R57, verbatim)** — boundaries: `/connect/logout` is denylisted while another `/connect/...` path is not
  covered by this clause; `/api/` covers `/api/v1/...`; `/accounts` covers `/accounts/me`; query strings and trailing
  slashes do not change the decision; `/app/accounts` is **not** denylisted, because it is an SPA route.
- **AC5 (L17, §5)** — no cache write happens for any denylisted request.
- **AC6 (T06 carry-forward, L3)** — the module never references `RENEWAL_CHANNEL` and opens no `BroadcastChannel`.
- **AC7 (drift)** — the list in code equals the list parsed from `agents.md`.
- **AC8 (L12)** — T01–T08 stay green and unedited.

### Required Tests
`sasNavigationBypassesServiceWorker` — eight groups, one per AC, as enumerated in
`artifacts/01-specification-extraction.md` §Tests required. Each mutation break listed there must fail at least one
case, demonstrated in Phase 10.

### Constraints
- **Verbatim list.** The prefixes are copied from `agents.md` exactly, in order, with a comment citing R57 and the
  file. No path is added (not `/notifications`) and none is reworded, even where a different set would be defensible.
- **Navigation-only.** A non-navigation request is outside this task's rule; the module must not quietly become the
  API policy that task 11 owns.
- **Pure.** No `fetch`, no `caches`, no `self` in the decision module, which also keeps `.oxlintrc.json` untouched.
- **Scope honesty.** The brief records (Open Question 2) that under the current edge a worker served from `/app/`
  could not see these navigations anyway; the denylist is the guarantee that survives a scope change, and must not be
  described as what keeps sign-in working today.

### Open Questions
1. **Does T09 wire the worker, or only its denylist?** T01 asserts the absence of an emitted and registered worker
   and names "T09-T12" collectively. Proposed: T09 delivers the denylist module and its test with no plugin wiring,
   leaving T01 green; emission and registration land with task 12. The alternative is editing three T01 assertions,
   which no task has yet been permitted to do. **Gate's call.**
2. **Worker scope.** The edge routes only `/app*` to the SPA (`Caddyfile:27-29`), so the default scope of a worker
   served from `/app/` is `/app/`, which already excludes every denylisted path; root scope would need the script
   served from `/` with `Service-Worker-Allowed: /`, an infra change. Proposed: build the denylist as a
   scope-independent guarantee, record the scope finding, and leave the infra decision to the task or ticket that
   turns the worker on. **Gate's call to confirm; no code depends on it.**
3. **The lint override** for a later task's real worker (`self`, `fetch`) — anticipated by T04, not needed here.
   Recorded, not resolved.
4. **`/notifications` is absent from R57** though T02's `BACKEND_PREFIXES` includes it. It is an API route, so task
   11's NetworkOnly covers it. Following R57 verbatim; `spec/` is read-only.
