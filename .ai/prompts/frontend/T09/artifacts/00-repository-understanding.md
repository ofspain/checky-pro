# frontend · T09 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim):**
> **Denylisted navigations bypass the worker.** Navigations to the paths in `agents.md` are never answered from cache or with the shell. Verifies `sasNavigationBypassesServiceWorker`. Cites R57, L17.

Read the repository and `spec/frontend/` (five files). No code written.

## 1. Architecture summary

- **Modules** (`frontend/src/`): `api/` is the client boundary (T03–T08: generated client, session boundary,
  cross-tab renewal, the 429 notice), `app/` is the shell and routing (T01–T02), `i18n/` the catalogue (T07),
  `main.tsx` the entry. **There is no `pwa/` module** — `design.md` §6 plans
  `pwa/ service worker, denylist (L17, R57)`, and tasks 9–12 create it.
- **Build**: one `vite.config.ts` — `base: '/app/'`, `@vitejs/plugin-react`, a dev server on 5173 with no proxy, and
  `test: { environment: 'jsdom' }`. **`vite-plugin-pwa` is not installed** and `frontend/package.json` has no
  Workbox-family dependency. `index.html` loads `/src/main.tsx` and nothing else.
- **Persistence**: none today. `design.md` §5 scopes the future worker cache to "static shell and assets only (L17).
  No API responses."
- **Events**: one same-origin `BroadcastChannel` for cross-tab renewal (T06).
- **Security**: public OIDC client; interactive login is SAS-hosted, which is exactly why the identity paths must
  never be answered by a worker — a cached or shell-served `/oauth2/authorize` or `/login` would break or hijack the
  real sign-in.

## 2. Existing code this task touches

- **New: `frontend/src/pwa/`** — the worker source and its denylist. Nothing exists yet.
- **`frontend/vite.config.ts`** — would gain the plugin if this task is the one that emits a worker (see §5).
- **`frontend/src/app/shellBuildsAndMounts.test.tsx` (T01) — the constraint that shapes this task.** Over a real
  production build it asserts `sw.js` **does not exist** (`:75`), `manifest.webmanifest` does not exist (`:76`), and
  **no built JS asset contains the string `serviceWorker`** (`:79`); a separate case asserts mounting the shell never
  calls `navigator.serviceWorker.register` (`:103-116`). Its own comment at `:74` reads "No worker yet (T09-T12 own
  it)". So the earlier task asserts the absence of the thing tasks 9–12 add, and L12 plus every task brief so far
  forbids editing an earlier test.
- **`frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` (T02)** — holds `BACKEND_PREFIXES`, the twelve edge
  prefixes, as the precedent for keeping such a list in one place with a comment pointing at its source. Its list is
  **not** identical to R57's eleven navigation paths (see §5).
- **`frontend/.oxlintrc.json` (T04)** — bans `fetch` as a global and off `window`/`globalThis`/`self`/… across
  `src/**`. A service worker necessarily uses `self`, the `fetch` event and `fetch()`, so the lint rules and this
  task meet head-on. T04's own artifact anticipated "R59 stream task adds the one override", so an override is the
  established answer, but `.oxlintrc.json` is listed as *not to modify* in T05–T08's briefs.
- **`frontend/src/api/crossTabRenewal.ts` (T06)** — carries a standing carry-forward: **the worker must never open
  `RENEWAL_CHANNEL`**, or it would receive access tokens over BroadcastChannel. T06 phase 13 asks for a guard test in
  tasks 9–12.

## 3. Established patterns to follow

- **Factories, no module-level mutable state**: every unit so far is `createX()`, one instance per tab.
- **A verbatim list with a cited source**: T02's `BACKEND_PREFIXES` and T08's `EXERCISED/UNEXERCISED_POST_PATHS`,
  each with a comment naming where the list comes from and a guard that fails when the source changes.
- **Drift guards in tests rather than CI machinery**: T05 parses `contracts/api/auth.yaml` from disk
  (`parallel401sTriggerExactlyOneRenewal.test.ts:513`); T08 parses every POST out of it. The same technique can read
  `spec/frontend/agents.md`'s denylist, which is the authoritative source R57 points to.
- **Source scanning as a structural guard**: T01 scans built assets and app sources; T04 scans `src` for inline
  lint-disable comments.
- **Exact, ≥2-week-old dependency pins**, installed with `--save-exact` (T07 added two).
- **Configuration**: none in the bundle. T01 asserts no `import.meta.env` and no absolute URL in app source, so a
  worker must not introduce either.

## 4. Testing conventions

- **Vitest + jsdom**; `agents.md` names Vitest and Playwright, but **Playwright is not installed and there is no
  `e2e/` directory**, so a real-browser navigation test is not available in this repo today.
- **jsdom has no `ServiceWorkerGlobalScope`**, no `caches`, and no `FetchEvent`. A worker's behaviour is therefore
  testable here only by importing its module and driving it with stubbed worker globals — the same shape as T05's
  fake lock manager and T06's `ObservedChannel`, which stood in for Web Locks and BroadcastChannel.
- **Helpers are local to each test file** and copied, never imported across tasks (L12).
- **Mutation breaks are part of the deliverable** (T07: 14, T08: 20), applied to real files and reverted with
  `git checkout --`.
- **No ArchUnit equivalent**; the structural guards are the lint rules and in-test source scans.
- Host caveat: the Docker daemon is down, so runs are on the macOS host, where
  `lintRejectsHandWrittenBackendFetch` AC4 fails environmentally (recorded at T07 phase 9).

## 5. Known gaps / unknowns

- **The T01 collision is the real question of this task, and I cannot settle it here.** Either T09 writes the worker
  and its denylist *without* wiring `vite-plugin-pwa` (nothing emitted, nothing registered, T01 stays green, the
  logic unit-tested against stubbed globals), or T09 wires the plugin and T01's three build assertions have to
  change — which edits an earlier task's test. T01's comment names "T09-T12" as the owners without saying which one
  emits the worker. **Phase 1 records it; the phase 4 gate decides.**
- **Which task registers the worker.** Task 12 is the update policy (`skipWaiting` / `clients.claim` never used,
  prompt-to-reload), which only means something once a worker is registered. I do not know whether the author
  intends registration at task 9 or task 12.
- **The lint override.** A worker cannot be written without `self` and `fetch`, so either `.oxlintrc.json` gains a
  scoped override (T04's anticipated answer) or the worker lives outside `src/`. I do not know which the author
  wants; it is a dependency of any task in 9–12.
- **R57's list and T02's list differ.** R57 and `agents.md` name eleven paths: `/oauth2/`, `/connect/logout`,
  `/login`, `/logout`, `/error`, `/.well-known/`, `/userinfo`, `/accounts`, `/api-keys`, `/admin`, `/api/`. T02's
  `BACKEND_PREFIXES` has twelve, and the differences are real: R57 denylists **`/connect/logout` only**, not all of
  `/connect`; it writes `/api/`, which covers `/api/v1`; and it **omits `/notifications`**, the SSE stream. As an API
  route the stream is covered by "API routes use NetworkOnly" (task 11), but it is absent from the navigation
  denylist. Recorded, not resolved — `spec/` is read-only here.
- **No SW design beyond L17 and §5.** `design.md` has no service-worker section: no precache manifest shape, no
  runtime-caching table, no `navigateFallback` value, no scope. The scope question matters, because the SPA is served
  under `/app/` while the denylisted paths are outside it — a worker registered with the default scope `/app/` would
  never see a navigation to `/login` at all. I do not know whether the author intends a root-scoped worker (which
  needs a `Service-Worker-Allowed` header from the edge) or an `/app/`-scoped one (where the denylist is belt-and-
  braces). **This is the single most load-bearing unknown for the task's design.**
- **`vite-plugin-pwa` version.** Not installed; I have not checked which versions are ≥2 weeks old. Phase 5's job.
- **No live verification available.** Docker is down, so the edge's behaviour for `/app/sw.js` and any
  `Service-Worker-Allowed` header cannot be probed this session.
