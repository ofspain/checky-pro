# frontend · T11 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Model** | Claude Sonnet |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim):**
> **API routes are NetworkOnly.** No API response is served from cache. Verifies
> `serviceWorkerNeverCachesApiResponses`. Cites R57, L17.

## 1. What T09 and T10 deliberately left here

`frontend/src/pwa/navigationDenylist.ts` decides **navigations** only: both predicates return `false` when
`mode !== 'navigate'` (`:56`, `:66`), and T09's AC6 and T10's new non-navigate case exist precisely to keep that
module out of this task's way. So nothing in the repo currently says anything about a `fetch`/XHR request to an API
path — which is what `agents.md:90` ("API routes use NetworkOnly") governs. **T11 is the first task that needs new
production code in `src/pwa/` since T09.**

The gate decision from T09 still holds: no worker is registered or emitted (D1); task 12 wires `vite-plugin-pwa`.
T11 therefore delivers policy plus its test, in the same shape — exports that task 12 hands to Workbox.

## 2. What counts as an API route — the three candidate lists in the repo

This is the question of the task, and the repo gives three different answers:

| Source | List | Note |
|---|---|---|
| `agents.md:88-89` / R57 | eleven **navigation** paths: `/oauth2/`, `/connect/logout`, `/login`, `/logout`, `/error`, `/.well-known/`, `/userinfo`, `/accounts`, `/api-keys`, `/admin`, `/api/` | navigation-scoped, and **omits `/notifications`** |
| `design.md` §4c (2) — the edge path map | twelve prefixes: `/oauth2`, `/login`, `/logout`, `/connect`, `/.well-known`, `/userinfo`, `/error`, `/accounts`, `/admin`, `/api-keys` → auth; `/api/v1` → payment; `/notifications` → notification; `/app` → the SPA | the authoritative "who owns which prefix" map |
| `routesResolveUnderAppPrefixOnly.test.tsx:18-31` | the same twelve, as `BACKEND_PREFIXES` | already a **test-local** copy, which T11 must not edit (L12) |

Two things fall out. First, `/notifications` is in the edge map but not in R57's navigation list — which is exactly
what T09 recorded as an open observation and deferred to *this* task. Second, `/.well-known/openid-configuration` is
fetched as a **non-navigation** request if the SPA ever reads discovery, and a stale cached copy of it would break
sign-in; so the API policy cannot simply be "R57's list minus the HTML endpoints".

## 3. The positive rule the spec actually implies

`design.md:194` (§5) says the worker cache holds **"static shell and assets only. No API responses."** That is a
*positive* statement about what may be cached, not a list of what may not. Stating the policy that way round —
cacheable only if it is the SPA's own shell or a static asset under `/app`, NetworkOnly otherwise — is strictly
stronger than any prefix list, and it fails safe: a thirteenth backend prefix added at the edge tomorrow is
NetworkOnly without anyone remembering to update a list. The prefix list is still worth exporting, because task 12
needs concrete matchers and because a drift guard against the edge map is the only thing that will notice if the two
diverge.

## 4. Things in the repo that constrain the shape

- `.oxlintrc.json` bans `fetch` and `self` in `src/**` (T04), so the policy must stay a pure decision, as T09's did.
  Task 12 owns the override a real worker needs.
- `agents.md:84-85` adds a second, sharper rule than "NetworkOnly": *"Sensitive response bodies (account, token,
  receipt, evidence) are never cached."* Those are API responses, so the policy must cover them, and the test should
  name them rather than only test prefixes.
- The SPA's own asset paths are `/app/assets/<name>-<hash>.js|css` (T01 asserts the script src pattern at
  `shellBuildsAndMounts.test.tsx:71`) and the dev server serves `/app/@vite/client` and `/app/@react-refresh`,
  which exist only in development.
- `/notifications` is an **SSE stream** (the edge sets `flush_interval -1` for it). A worker that buffered or
  replayed a stream would break it, so "NetworkOnly" for that path is not merely a caching choice.
- No frontend code calls `/notifications` yet; R59's stream task is later. The policy still has to cover it.

## 5. What I could not settle here

1. **Which list is authoritative for "API route"** — the edge map's twelve, or a narrower data-API set. Leaning to
   the positive rule of §4c/§5 (everything but the SPA's own static assets) *plus* the twelve as an explicit,
   drift-guarded matcher set for task 12's benefit.
2. **Whether to state the rule positively.** It is a stronger guarantee than the task statement's words, and the
   gate should confirm that going beyond "API routes use NetworkOnly" in the safe direction is wanted.
3. **How to treat dev-only paths** (`/app/@vite/client`, `/app/@react-refresh`). They are under `/app` but are not
   build assets; they never exist in a production build, so a policy that calls them cacheable is harmless in
   production but would be wrong if anyone ran the worker against a dev server.
4. **Whether T11 exports a prefix list that duplicates T02's `BACKEND_PREFIXES`.** A shared constant would be
   better, but it lives in a frozen test file; duplicating with a drift guard against `design.md`'s edge map is the
   L12-compatible option.
