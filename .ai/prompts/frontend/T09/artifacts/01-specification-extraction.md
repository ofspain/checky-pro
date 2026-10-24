# frontend · T09 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement (verbatim):**
> **Denylisted navigations bypass the worker.** Navigations to the paths in `agents.md` are never answered from cache or with the shell. Verifies `sasNavigationBypassesServiceWorker`. Cites R57, L17.

## Business Rules

- **R57 (`requirements.md:203-205`):** when a navigation request targets an identity or backend path — `/oauth2/`,
  `/connect/logout`, `/login`, `/logout`, `/error`, `/.well-known/`, `/userinfo`, `/accounts`, `/api-keys`,
  `/admin`, `/api/` — the service worker **shall not** answer it from cache and **shall not** serve the application
  shell.
- **`agents.md:88-89`** states the same eleven paths and is the authoritative list the task statement points at.
- **`agents.md:90`:** API routes use NetworkOnly — **task 11**, not this task.
- **`agents.md:91-92`:** one worker-wide update policy, prompt-to-reload, no `skipWaiting`/`clients.claim` —
  **task 12**.
- **`design.md` §5:** the worker cache holds static shell and assets only; no API responses.

## Locked Decisions

- **L17 (`design.md:61-62`):** service-worker boundaries — the `agents.md` navigation paths bypass the fallback and
  are never answered from cache; API routes are NetworkOnly.
- **L13:** the stack is confirmed as `vite-plugin-pwa` (Workbox) — so the worker is generated/injected by that
  plugin, not hand-rolled from scratch.
- **L14:** every SPA route is under `/app`, disjoint from the edge-mapped backend prefixes.
- **L12:** phase additivity — T01–T08 are consumed, never rewritten.
- **L10 / R58:** no secret or non-public env key in the bundle; T01 also asserts no `import.meta.env` and no absolute
  URL in app source, which binds any new source file.
- **L3 / L16 / §5:** tokens never persisted; the only client-side storage carve-outs are listed; the worker cache is
  shell and assets only.
- **L6:** backend calls go through the generated client — relevant because the worker is a `fetch`-handling surface
  and `.oxlintrc.json` enforces L6 by banning `fetch`/`self.fetch` in `src/**`.

## Files involved

**Existing, to read:**
- `frontend/vite.config.ts` — where `vite-plugin-pwa` would be registered.
- `frontend/src/app/shellBuildsAndMounts.test.tsx` — T01's assertions that **no** worker is emitted or registered
  (`:74-80`, `:103-116`). The blocker recorded below.
- `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` — `BACKEND_PREFIXES`, the precedent for a cited list.
- `frontend/.oxlintrc.json` — the `fetch`/`self` bans.
- `frontend/src/api/crossTabRenewal.ts` — `RENEWAL_CHANNEL`, which the worker must never open (T06 carry-forward).
- `infra/local/edge/Caddyfile` — the edge routes **only `/app*`** to the SPA (`:27-29`); everything else goes to the
  services.

**New files the spec expects** (`design.md` §6: `pwa/ service worker, denylist (L17, R57)`):
- `frontend/src/pwa/<worker>.ts` — the worker source, or the navigation-denylist module it uses;
- `frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts` — the named test.

**Files that must not change:** `sessionBoundary.ts`, `crossTabRenewal.ts`, `rateLimitNotice.ts`,
`RateLimitNotice.tsx`, `client.ts`, `i18n/*`, `AppShell.tsx`, `mountShell.tsx`, `routes.tsx`, `main.tsx`; every
T01–T08 test; `libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`. `.oxlintrc.json` and
`vite.config.ts` are **open questions**, not assumptions.

## Dependencies

- **`vite-plugin-pwa` (Workbox)** per L13 — not installed; an exact, ≥2-week-old pin is needed if this task wires it.
- Vitest + jsdom. **jsdom provides no `ServiceWorkerGlobalScope`, `caches` or `FetchEvent`**, so the worker's logic
  must be exercised through stubbed worker globals, as T05 stubbed Web Locks and T06 stubbed BroadcastChannel.
- `spec/frontend/agents.md` read from disk, if the denylist gets a drift guard (T05/T08 precedent).
- No contract, no config key, no backend dependency. **No `contracts/api/*.yaml` involvement** despite the header's
  standing list: this task concerns navigation requests, not API operations.

## Acceptance Criteria

- **AC1 (R57, L17):** for each of the eleven denylisted paths, and for a representative sub-path of each, a
  **navigation** request is neither answered from cache nor answered with the application shell — the worker declines
  it and the network sees it.
- **AC2 (R57, L17):** a navigation to an `/app` route **is** answered with the shell, so AC1 is not vacuously true.
- **AC3 (R57):** the denylist decision reads **only** the request's URL path and `mode === 'navigate'`; no cache
  lookup happens for a denylisted path (not even a miss).
- **AC4 (R57, exact wording):** matching follows the list verbatim — `/connect/logout` is denylisted but another
  `/connect/...` path is not covered by this clause; `/api/` covers `/api/v1/...`; `/accounts` covers
  `/accounts/me`; `/.well-known/` and `/oauth2/` cover their sub-paths. Query strings and trailing slashes do not
  change the decision.
- **AC5 (L17, §5):** no API or identity response is written to a cache by this task's code.
- **AC6 (T06 carry-forward, L3):** the worker never opens `RENEWAL_CHANNEL`, so a token broadcast cannot reach it.
- **AC7 (drift):** the denylist in code equals the list in `agents.md`, asserted by parsing that file, so a change to
  the standing rules fails this test.
- **AC8 (L12):** T01–T08 suites stay green and unedited.

## Tests required

**`sasNavigationBypassesServiceWorker`** (Vitest, jsdom, with stubbed worker globals):
1. AC1 — each denylisted path and a sub-path of each: the navigation handler declines, and no cache read occurs.
2. AC2 — `/app/`, `/app/sign-in` and a deep `/app/...` route are answered with the shell.
3. AC3 — a denylisted navigation performs no `caches.match`; the spy is never called.
4. AC4 — the boundary cases: `/connect/logout` vs another `/connect/...`; `/api/v1/...`; `/accounts/me`; a query
   string; a trailing slash; a path that merely *contains* a denylisted segment (for example `/app/accounts`) is
   **not** treated as denylisted, because it is an SPA route.
5. AC5 — nothing is written to any cache for those requests.
6. AC6 — the worker module does not reference `RENEWAL_CHANNEL`, asserted structurally.
7. AC7 — the drift guard against `agents.md`.
8. AC8 — covered by running the whole suite.

**Mutation breaks:** a path dropped from the denylist; prefix matching loosened to `includes()`; the shell fallback
applied to everything; a cache lookup added before the denylist check; matching made case-sensitive or
case-insensitive incorrectly; the drift guard's parse weakened.

## Open Questions

1. **T01 asserts the absence of what tasks 9–12 add, and this task cannot both wire the plugin and leave T01
   green.** `shellBuildsAndMounts` asserts over a real build that `sw.js` and `manifest.webmanifest` are absent and
   that no built asset contains `serviceWorker` (`:74-80`), plus that mounting registers no worker (`:103-116`). Its
   comment says "No worker yet (T09-T12 own it)" without naming which task flips it. Either (a) T09 delivers the
   denylist logic and its test with **no** plugin wiring, nothing emitted and nothing registered — T01 stays green,
   and emission/registration arrive with task 12, whose update policy presumes a registered worker; or (b) T09 wires
   the plugin and T01's three build assertions are edited, which no task has been allowed to do so far. **(a) is
   what I would proceed with; it needs the gate's confirmation because it narrows what "the worker" means in this
   task.**
2. **Worker scope makes R57 belt-and-braces today, and that should be stated rather than assumed.** The edge routes
   only `/app*` to the SPA (`infra/local/edge/Caddyfile:27-29`), so a worker served from `/app/sw.js` has scope
   `/app/` and **never sees** a navigation to `/login`, `/oauth2/authorize` or `/accounts`. Root scope would need the
   script served from `/` with a `Service-Worker-Allowed: /` header, which is an infra change and out of scope for a
   frontend task. So the denylist is a guarantee that holds **regardless of scope** — correct and worth having,
   especially if the scope ever widens — but it is not what keeps those paths working today. Recorded so the task is
   not sold as something it is not.
3. **The lint override.** A worker needs `self` and `fetch`, both banned in `src/**` by `.oxlintrc.json` (T04). T04's
   own artifact anticipated an override for the R59 stream task. T09 needs the same, or the worker source must live
   outside `src/`. Not a blocker for the denylist *logic*, which is a pure function over a URL and can be written and
   tested without touching `fetch`.
4. **`/notifications` is absent from R57's list** while T02's `BACKEND_PREFIXES` includes it. As an API route it
   falls under "API routes use NetworkOnly" (task 11), so this task follows R57 verbatim and does not add it.
   Recorded; `spec/` is read-only here.
