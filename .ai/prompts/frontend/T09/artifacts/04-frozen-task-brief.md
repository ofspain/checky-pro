# frontend · T09 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |
| **Status** | **FROZEN** — implementation follows this document, not the Phase 2 brief. |

### Task
T09 — **Denylisted navigations bypass the worker.** Navigations to the paths in `agents.md` are never answered from
cache or with the shell. Verifies `sasNavigationBypassesServiceWorker`. Cites R57, L17.

---

## Gate decisions

### D1 — T09 ships the navigation policy module and its test; it does not register or emit a worker. **(Open Question 1, settled on evidence.)**
`tasks.md:18-21` shows that **none** of tasks 9, 10 or 11 is a "turn the worker on" task: 9 is the navigation
denylist, 10 is the `/connect/logout` case specifically, 11 is API NetworkOnly. Task **12** — "a new build waits for
a user reload" — is the only one of the four that cannot be satisfied without a registered, updating worker, so it is
the task that wires `vite-plugin-pwa`, emits `sw.js` and the manifest, registers it, and updates T01's three
absence assertions (`shellBuildsAndMounts.test.tsx:74-80, 103-116`). T09–T11 build the worker's policy as pure,
independently tested modules. This preserves L12 (no T01 edit here) and matches how T05–T08 built session policy
before any screen consumed it.

*This corrects the Phase 2 brief, which attributed precaching and the shell to "tasks 10 and 12": task 10 is the
end-session navigation case, not precaching. T09 therefore must leave `/connect/logout`'s dedicated test to task 10
— it is covered here only as one of the eleven, by the general rule.*

### D2 — Worker scope is recorded, not relied on. **(Open Question 2, settled.)**
`infra/local/edge/Caddyfile:27-29` routes only `/app*` to the SPA, so a worker served from `/app/sw.js` has default
scope `/app/` and would never see a navigation to `/login` or `/oauth2/authorize`; root scope would need the script
served from `/` with `Service-Worker-Allowed: /`, an infra change outside a frontend task. The denylist is therefore
a **scope-independent** guarantee: correct, cheap, and the thing that holds if the scope ever widens — but not what
keeps sign-in working today. Task 12 owns the scope/serving decision. No code here depends on it.

### D3 — Module interface is fixed, in both shapes Workbox needs. **(Finding 1, accepted.)**
A bare boolean predicate cannot be passed to `navigateFallbackDenylist` or `new NavigationRoute(handler, { denylist })`,
which take `RegExp[]`. The module therefore exports the verbatim list, the derived `RegExp[]`, and the two pure
predicates:

```ts
export const DENYLISTED_NAVIGATION_PATHS: readonly string[];   // verbatim, in agents.md order
export const NAVIGATION_DENYLIST: readonly RegExp[];           // derived; navigateFallbackDenylist / NavigationRoute denylist
export const SHELL_NAVIGATION_PREFIX: string;                  // '/app'
export function isDenylistedNavigation(url: URL, mode: string): boolean;
export function isShellNavigation(url: URL, mode: string): boolean;
```
Task 12 confirms the exact option name against the installed `vite-plugin-pwa` version; the `RegExp[]` shape is what
both its `workbox.navigateFallbackDenylist` and Workbox's `NavigationRoute` accept, so no breaking change is needed
when the worker is turned on.

### D4 — Matching is path-segment prefix. **(Findings 3 and 4, accepted.)**
Each entry's trailing `/` is stripped, the remainder is regex-escaped (needed for `/.well-known/` and `/api-keys`),
and the boundary is `(?:/|$)`:

```
/api  →  /^\/api(?:\/|$)/     matches /api, /api/v1/accounts   does NOT match /apikeys
```
So `/accountsxyz` is **not** denylisted, `/application` is **not** the shell, and `/app` (no trailing slash) **is**
the shell, exactly as `/app/`. Query strings and hashes are ignored — the decision reads `url.pathname` only.

### D5 — The cache-order guarantee is proven by a test harness, not by a cache reference in production. **(Finding 2, resolved as option (a) plus a harness.)**
The cache-order mutation break has no production target while the module is pure, and growing the module to hold a
`caches` reference would pull task 11's and task 12's work forward and require an `.oxlintrc.json` override. Instead
the test file carries `simulateNavigationFallback(request, fakeCache, shell)` — a local harness mirroring Workbox's
`navigateFallback` semantics: denylisted → return nothing, never touch the cache; shell navigation → return the shell
from the cache. The fake cache's `match`/`put` are spied, so "never answered from cache or with the shell" is
asserted observably, against the real exported policy, with production code that stays pure. The harness carries a
comment that task 12 wires the genuine Workbox route against the same exports. The mutation break moves from
"cache read before the denylist check" (in the harness, still demonstrable) to the policy mutants in AC-M below.

### D6 — Non-navigation requests always decline. **(Finding 6, accepted.)**
Both predicates return `false` for `mode !== 'navigate'`, so neither can be repurposed as the API policy that task 11
owns.

### D7 — The drift parser is specified. **(Finding 5, accepted.)**
The guard locates the paragraph beginning `Navigation requests to these paths are never answered from cache` in
`spec/frontend/agents.md`, extracts every backtick-delimited token starting with `/` from that paragraph only, and
compares the sequence — order included — with `DENYLISTED_NAVIGATION_PATHS`. A rewrap of the paragraph does not break
it; a changed, added, removed or reordered path does.

### D8 — Order and citation are asserted. **(Finding 8, accepted.)**
D7's comparison is order-sensitive, and one case asserts the module source contains a comment citing R57 and
`agents.md`, pinning the "verbatim list, cited" constraint.

### D9 — No worker globals are stubbed. **(Finding 9, accepted.)**
The tests need a `URL`, a mode string, a `Request`, and the fake cache of D5. No `ServiceWorkerGlobalScope`,
`FetchEvent` or `caches` stub is installed.

### D10 — `/notifications` stays out, by rule. **(Finding 7, accepted and locked.)**
The R57 list is copied verbatim. `/notifications` and every other API route are handled by task 11's NetworkOnly
policy, not by this denylist, and must not be added here even though `BACKEND_PREFIXES` in T02 includes it.

---

## Scope

**In** — `frontend/src/pwa/navigationDenylist.ts` with the D3 exports under the D4/D6 semantics, and
`frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts` with the D5 harness and the drift guard of D7.

**Out** — registering or emitting a worker, `vite-plugin-pwa` install or `vite.config.ts` change (D1);
`/connect/logout`'s dedicated test (task 10); API NetworkOnly (task 11); precaching, the manifest, the update prompt,
`skipWaiting`/`clients.claim` (task 12); any `infra/**` or `spec/**` change; any T01–T08 source or test edit (L12);
caching any API or identity response (§5, L17).

## Business rules and locked decisions
R57 (`requirements.md:203-205`), `agents.md:88-89`; L17 (`design.md:61-62`), L13, L14, L12, L6, L10, L3, L16, §5
(`design.md:194`), §6 (`design.md:220` — `pwa/ service worker, denylist (L17, R57)`).

## Dependencies
Vitest + jsdom; `spec/frontend/agents.md` read from disk. No new package, no contract, no config key, no backend.

## Inputs / Outputs / State
In: `url.pathname` and the request mode. Out: two booleans, the verbatim list, the derived `RegExp[]`. State: none —
pure functions, no cache, no storage, no channel.

## Files
**Create** `frontend/src/pwa/navigationDenylist.ts`, `frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts`.
**Modify** none. **Do not touch** `sessionBoundary.ts`, `crossTabRenewal.ts`, `rateLimitNotice.ts`,
`RateLimitNotice.tsx`, `client.ts`, `i18n/*`, `AppShell.tsx`, `mountShell.tsx`, `routes.tsx`, `main.tsx`,
`vite.config.ts`, `.oxlintrc.json`, any T01–T08 test, `libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`.

## Acceptance Criteria

- **AC1 (R57, L17)** — each of the eleven paths, and a sub-path of each, is a denylisted navigation: through the D5
  harness it is answered neither from cache nor with the shell, and the network sees it.
- **AC2 (R57, L17)** — `/app`, `/app/`, `/app/sign-in` and a deep `/app/...` route are answered with the shell, so
  AC1 is not vacuous.
- **AC3 (R57, D5)** — for a denylisted navigation the fake cache's `match` is **never** called: the decision reads
  `url.pathname` and the mode only.
- **AC4 (R57 verbatim, D4)** — boundaries, positive and negative: `/api/v1/accounts` and bare `/api` denylisted;
  `/accounts/me` denylisted; `/accountsxyz`, `/apikeys` and `/logouts` **not** denylisted; `/connect/logout`
  denylisted while `/connect/register` is not covered by this clause; `/application` **not** the shell;
  `/app/accounts` the shell, not denylisted; a query string and a hash change nothing.
- **AC5 (L17, §5)** — the harness's `put` is never called for any denylisted request; nothing is cached.
- **AC6 (D6)** — both predicates return `false` for `mode !== 'navigate'`, including for a denylisted path.
- **AC7 (D7, D8)** — the exported list equals, in order, the list parsed from `agents.md`.
- **AC8 (D8)** — the module source cites R57 and `agents.md` in a comment.
- **AC9 (T06 carry-forward, L3, L6, L10)** — the module opens no `BroadcastChannel`, references no
  `RENEWAL_CHANNEL`, and contains no `fetch`, `caches`, `self`, `import.meta.env` or absolute URL.
- **AC10 (L12)** — the full frontend suite stays green with no T01–T08 file edited.

## Required test
`sasNavigationBypassesServiceWorker` in `frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts`, one group per
AC.

**AC-M — mutation breaks to demonstrate in Phase 10:** drop a path from the list; reorder the list; loosen matching to
`startsWith` (breaks AC4's `/accountsxyz`); loosen to `includes` (breaks `/app/accounts`); drop the `(?:/|$)`
boundary; make the shell matcher unconditional (breaks AC1); drop the `mode === 'navigate'` check (breaks AC6);
make the harness read the cache before the denylist check (breaks AC3); weaken the drift parser to ignore order
(breaks AC7).

## Open questions carried forward (not blocking)
- The `.oxlintrc.json` override a real worker will need for `self`/`fetch` — anticipated by T04, needed by task 12,
  avoided here by D3's pure design.
- Worker scope and serving (D2) — task 12, with an infra follow-up if root scope is wanted.
