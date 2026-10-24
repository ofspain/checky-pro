# frontend · T11 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |
| **Status** | **FROZEN** — implementation follows this document. |

### Task
T11 — **API routes are NetworkOnly.** No API response is served from cache. Verifies
`serviceWorkerNeverCachesApiResponses`. Cites R57, L17.

---

## Gate decisions

### D1 — Default-deny, with the prefix list as an explicit matcher set. **(Open Question 1, settled; Kimi agrees.)**
`design.md:194` states the cache's contents positively — "static shell and assets only. No API responses." The
policy is therefore written that way round: **cacheable is the narrow case**, everything else is NetworkOnly. The
prefix list is exported for task 12's matchers and for the drift guard, not as the decision's basis, so a thirteenth
backend prefix added at the edge is NetworkOnly without an edit here.

### D2 — The twelve prefixes, enumerated here. **(Finding 1, accepted.)**
From `design.md:158-160`'s edge map, minus `/app` (the SPA's own), with `/api/v1` normalised to `/api` per D3:

```ts
export const NETWORK_ONLY_PATH_PREFIXES = [
  '/oauth2', '/login', '/logout', '/connect', '/.well-known',
  '/userinfo', '/error', '/accounts', '/admin', '/api-keys',
  '/api',          // normalised from the map's /api/v1, so /api/v2 is safe by default (D3)
  '/notifications',
] as const;
```

### D3 — The prefix is `/api`, not `/api/v1`. **(Finding 4, accepted.)**
The map says `/api/v1` → payment-service; R57 says `/api/`. `/api` with a segment boundary covers `/api/v1`, any
future version, and the bare path, and it agrees with R57's wording. The drift guard normalises the map's `/api/v1`
to `/api` before comparing, and this brief is where that normalisation is recorded.

### D4 — Module interface, fixed. **(Finding 2, accepted.)**
```ts
export const NETWORK_ONLY_PATH_PREFIXES: readonly string[];
export const SHELL_DOCUMENT_PATHS: readonly string[];   // '/app', '/app/', '/app/index.html'
export const ASSET_PATH_PREFIX: string;                 // '/app/assets'
export function isCacheableRequest(url: URL): boolean;   // the narrow positive case
export function isNetworkOnlyRequest(url: URL): boolean; // everything else
```
Both predicates take only a `URL` — no method, no mode (D6). `isNetworkOnlyRequest` is exactly
`!isCacheableRequest`, asserted as such, so the two can never drift apart.

### D5 — Cacheable means the shell document and `/app/assets/*`, with a segment boundary. **(Finding 5, accepted; row list refined.)**
Kimi's boundary point is right and `/application` and `/app2` must be NetworkOnly. Its proposed row list is refined
in one place: **`/app/sign-in` is NOT cacheable here.** An SPA route is served by the *navigation* fallback (T09's
`isShellNavigation`), not by this request policy, and `design.md` §5 says the cache holds the shell and assets only.
So the cacheable set is exactly `/app`, `/app/`, `/app/index.html` and `/app/assets/<anything>`; a non-navigation
request for `/app/sign-in` is NetworkOnly. That is not a contradiction with T09 — the two modules answer different
questions, and AC5 asserts the pairing explicitly.

`segmentPrefixPattern` is module-private in `navigationDenylist.ts:40`, so the ten-line helper is **duplicated**
into this module with a comment naming T09's copy. Exporting it from T09 would edit a frozen file (L12), as with
the test harness in T10.

### D6 — Method and mode are not inputs. **(Findings 2 and 9, accepted.)**
The decision reads `url.pathname` only. The test asserts method-independence by exercising `POST
/accounts/password-reset` alongside `GET /accounts/me` **through the harness**, which is where a method could
plausibly be consulted — the predicates themselves cannot see one, which is the strongest form of the guarantee.

### D7 — The drift parser is specified. **(Finding 3, accepted.)**
Locate the paragraph containing `**Edge path map**` in `spec/frontend/design.md`; take lines until the first that
does not begin with `>`; strip the leading `> `; join; extract every backtick-delimited token beginning with `/`;
drop `/app` (the SPA's own) and any token that is not a path (`server.proxy` is backticked in the same paragraph, so
only tokens starting with `/` count); normalise `/api/v1` to `/api`. Compare the result, order included, with
`NETWORK_ONLY_PATH_PREFIXES`. A clear failure message if the paragraph is not found.

### D8 — Agreement with T09 is asserted by importing it. **(Finding 6, accepted.)**
The T11 test imports `isDenylistedNavigation` and `isShellNavigation` from `navigationDenylist.ts` and asserts, for
each overlapping path, that T09 declines the navigation **and** T11 marks the request NetworkOnly. That reads T09
without modifying it, and it is the only honest way to show the two policies compose.

### D9 — SAS-hosted assets are NetworkOnly, by consequence. **(Finding 7, accepted and documented.)**
`infra/local/edge/Caddyfile:12` routes `/default-ui.css` to auth-service — a SAS UI stylesheet, absent from both R57
and `design.md`'s map. Under D1 it is NetworkOnly. That is correct (it is not the SPA's asset, and the SPA never
needs it offline) and it is recorded here so nobody later reads it as an oversight. The same holds for anything else
the edge may route to a service outside the twelve prefixes.

### D10 — The `put` guarantee is the test's, and task 12's. **(Finding 8, accepted, stated plainly.)**
A pure policy cannot stop a cache write at runtime. T11 demonstrates the policy and asserts, through a harness, that
a NetworkOnly decision touches neither `match` nor `put`. **Task 12 must wire Workbox so API routes use a true
`NetworkOnly` strategy that neither reads nor writes the cache** — written into phase 13's carry-forwards.

---

## Scope
**In** — `frontend/src/pwa/apiRequestPolicy.ts` (D2–D6) and
`frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts` (AC1–AC9, the D7 drift guard, the D8 agreement
cases, and the mutation breaks).
**Out** — registering or emitting a worker (T09 D1 — task 12); the precache manifest guard and the real NetworkOnly
wiring (task 12, D10); the update policy (task 12); the SSE reader itself (R59); any edit to
`navigationDenylist.ts` or the T09/T10 tests (L12); `spec/`, `contracts/`, `services/`, `infra/`,
`.oxlintrc.json`, `vite.config.ts`.

## Business rules and locked decisions
`agents.md:90` (NetworkOnly), `:84-85` (sensitive bodies never cached), R57 (`requirements.md:203-205`),
`design.md:194` (§5), §4c (2) (`:158-160`); L17 (`:61-62`), L9, L14, L3, L16, L12, L6.

## Files
**Create** the two files above. **Modify** none. **Do not touch** the frozen list in D-Scope-Out.

## Acceptance Criteria
- **AC1** — each of the twelve prefixes, bare and with a representative sub-path, is NetworkOnly: the harness
  touches neither `match` nor `put`.
- **AC2** — `/app`, `/app/`, `/app/index.html` and `/app/assets/main-abc123.js|css` **are** cacheable, so AC1 is not
  vacuous.
- **AC3** — the four bodies `agents.md:84-85` names are covered by name: `/accounts/me` (account),
  `/oauth2/token` (token), `/api/v1/receipts/<uuid>` (receipt) and an evidence path (`/api/v1/...`).
- **AC4 (D6)** — `POST /accounts/password-reset` and `GET /accounts/me` get the same answer; the predicates take no
  method or mode.
- **AC5 (D5, D8)** — the two policies compose: for `/accounts`, `/admin`, `/api-keys`, `/userinfo` and `/api/v1/...`,
  T09 declines the navigation and T11 marks the request NetworkOnly; and `/app/sign-in` is T09's shell but **not**
  cacheable here.
- **AC6 (D1)** — default-deny: an unrecognised path (`/thirteenth-service`, `/default-ui.css`) is NetworkOnly.
- **AC7 (D5)** — boundaries: `/application`, `/app2`, `/appfoo` are NetworkOnly; `/app/assetsx/y.js` is **not** an
  asset path; `/accountsxyz` is not a backend prefix but is still NetworkOnly under default-deny.
- **AC8 (D1, AC6 for SSE)** — `/notifications` and `/notifications/stream` are NetworkOnly, asserted in their own
  case with the reason recorded: a buffered or replayed stream is broken, not stale.
- **AC9 (D7)** — the exported list equals the parsed edge map, order included.
- **AC10 (D4, purity)** — `isNetworkOnlyRequest` is exactly the negation of `isCacheableRequest` for every path in
  the file; the module references no `caches`, `fetch`, `self`, storage or cross-tab global.
- **AC11 (L12)** — the whole suite stays green, no T01–T10 file edited.

## Required test
`serviceWorkerNeverCachesApiResponses`, one group per AC.

**AC-M — mutation breaks:** drop a prefix from the list; invert the default so unknown paths become cacheable; make
the decision read the method; loosen the asset match so `/app/assetsx/...` or a backend path is cacheable; drop the
segment boundary so `/application` is cacheable; let `/notifications` be cacheable; have the harness `put` a
NetworkOnly response; weaken the drift parser to ignore order.
