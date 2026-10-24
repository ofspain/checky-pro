# frontend · T12 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Model** | Claude Sonnet |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

No code written in this phase.

## 1. Architecture summary

`frontend/` is a single-package React SPA, not a backend service: no persistence, no outbox, no events, no
resource-server security. The four spec headings map onto it as follows.

- **Modules.** `src/app/` (shell, router), `src/api/` (generated-client wrapper, 401/429 handling, cross-tab
  renewal), `src/i18n/` (catalogue), `src/pwa/` (service-worker policy), `src/main.tsx` (a single `mountShell` call).
  `src/auth/` and `src/features/*` from `design.md` §6 do not exist yet.
- **Persistence.** None server-side. Client-side storage is deliberately almost empty: tokens in memory only (L3),
  sessionStorage limited to the L16 carve-out, localStorage for theme and locale only, and the service-worker cache
  for **static shell and assets only** (`design.md:194`, L17).
- **Events.** No Kafka, no outbox. The nearest analogue is cross-tab coordination (`src/api/crossTabRenewal.ts`,
  L18) and, for this task, the service-worker lifecycle — `installing` → `waiting` → `activated` — which is where
  an update either waits for the user or does not.
- **Security.** The SPA is a public OIDC/PKCE client behind a single origin (L9). Every backend and identity route
  is owned by the edge (`infra/local/edge/Caddyfile`), and the SPA is served only under `/app*`. The
  service-worker rules in `agents.md:87-92` are a security boundary, not a performance tactic: an identity
  navigation answered from cache would be a stale-session bug.

## 2. Existing code this task touches

**Already exists (T09–T11 built the policy layer; nothing registers a worker yet):**

| File | What it gives T12 |
|---|---|
| `frontend/src/pwa/navigationDenylist.ts` | `DENYLISTED_NAVIGATION_PATHS` (the eleven `agents.md:88-89` paths), `NAVIGATION_DENYLIST: readonly RegExp[]`, `SHELL_NAVIGATION_PREFIX`, `isDenylistedNavigation`, `isShellNavigation` |
| `frontend/src/pwa/apiRequestPolicy.ts` | `NETWORK_ONLY_PATH_PREFIXES` (twelve), `SHELL_DOCUMENT_PATHS`, `ASSET_PATH_PREFIX`, `isCacheableRequest`, `isNetworkOnlyRequest` |
| `frontend/vite.config.ts` | `base: '/app/'`, `plugins: [react()]`, dev server on 5173, no proxy; one config for dev, build and test |
| `frontend/package.json` | scripts `dev`/`build`/`lint`/`test`/`typecheck`; **no `vite-plugin-pwa`, no `workbox-*`** |
| `frontend/index.html` | the only HTML entry; no manifest link, no worker registration |
| `frontend/src/main.tsx` | three lines: `mountShell(document.getElementById('root'))` |
| `frontend/.oxlintrc.json` | all categories off; exactly two rules, both banning `fetch`/`XMLHttpRequest` (L6) |

**New in T12:** a worker entry (`design.md:220` places it in `src/pwa/`), the `vite-plugin-pwa` wiring in
`vite.config.ts`, a registration call site, and the named test `serviceWorkerUpdateWaitsForReload`.

**Existing tests T12 must edit — the first time in this spec package that a frozen test is re-opened.**
`frontend/src/app/shellBuildsAndMounts.test.tsx` (T01) asserts the worker's *absence*, in its own words
*"No worker yet (T09-T12 own it)"*:

- `:75-76` — `sw.js` and `manifest.webmanifest` do **not** exist in `dist/`.
- `:77-80` — no built asset `.js` contains the string `serviceWorker`.
- `:103-116` — mounting the shell calls neither `fetch`, nor `Storage.setItem`, nor `navigator.serviceWorker.register`.
- `:121-127` — `productionSourceFiles()` walks **all** of `src/**` recursively, so any new worker source is
  automatically subject to the "no `import.meta.env`, no `https?://`" scan (R58, L10).

## 3. Established patterns to follow

- **Policy as pure, side-effect-free modules.** T09–T11 put every rule in exported constants and pure predicates
  with no `self`, no `caches`, no `fetch`, so the suite can drive them under jsdom. T12 is the first file that must
  touch real worker globals, so the pattern to preserve is: keep the *decision* pure, keep the *plumbing* thin.
- **Derive, never restate.** `NAVIGATION_DENYLIST` is derived from one list of paths; `apiRequestPolicy` states the
  cacheable set and negates it. Each list has exactly one drift guard that reads `spec/frontend/agents.md` or
  `design.md` and fails if the spec and the code diverge.
- **Spec citation in-source.** Every file names the decisions it implements (`L17`, `R57`, `agents.md:88-89`) in a
  header comment, and the tests cite acceptance criteria as `(AC3)` in their names.
- **Configuration.** One `vite.config.ts` for dev, build and test; `base: '/app/'` is load-bearing for L14. No
  environment keys reach the bundle (R58), which constrains how a build identifier may be injected.
- **Error handling.** Not applicable to a policy module; the relevant convention is that the SPA never invents a
  user-facing string outside the i18n catalogue (`agents.md`, L11) — which bears on *prompting* a reload.

## 4. Testing conventions

- **Vitest + jsdom** (`environment: 'jsdom'`), file naming `<namedTest>.test.ts(x)` matching `package.md` §8
  exactly — so T12's file is `serviceWorkerUpdateWaitsForReload.test.ts`.
- **No Testcontainers, no fixed `Clock`, no ArchUnit** — those are backend conventions. The analogues here are:
  real `vite build` into a temp dir (T01), real `oxlint` subprocess (T07), and **drift guards that parse the spec**
  (T09–T11).
- **`it.each` tables** over URL shapes, with the acceptance criterion in the test name.
- **Mutation testing is a deliverable** (phase 10): apply a mutant to real source, run the suite, revert with
  `git checkout --`. Standing lesson from T09: **commit the tests first**, because the revert discards uncommitted
  work. Standing lesson from T11: a mutant that merely *weakens a guard* can never be caught; for a drift guard the
  mutant belongs on the thing guarded.
- `expect.getState().testPath` is used to locate the repo root, because jsdom gives `import.meta.url` a non-file URL.

## 5. Known gaps / unknowns

**Two hard blockers, both found by inspection in this phase.**

1. **`vite-plugin-pwa` is not installed and cannot be installed on this host.** L13 and `agents.md:11` name
   `vite-plugin-pwa` (Workbox) as confirmed stack, but it is absent from `frontend/package.json`, from
   `pnpm-lock.yaml` (no `vite-plugin-pwa` and no `workbox-*` entries), and from the local store. The registry is
   reachable (HTTP 200 for `vite-plugin-pwa`), but **`pnpm` is not installed**: `package.json` declares
   `packageManager: pnpm@12.14.0`, `~/Library/pnpm/store` exists, yet no `pnpm` or `corepack` binary is on PATH in
   any shell — only `npm`. Running `npm install` in a pnpm workspace would write the wrong lockfile. **This is
   owner input, not a design choice**, and it is the reason the T07 AC2 end-to-end case fails today.
2. **T07's AC5 forbids a lint exemption for the worker.** `lintRejectsHandWrittenBackendFetch.test.ts:194-195`
   asserts `expect(config).not.toHaveProperty('overrides')` and that the rule keys are exactly the two L6 rules;
   AC4 additionally asserts the lint run reports `with 2 rules`. So the earlier assumption that T12 would add an
   `.oxlintrc.json` override for the worker's globals **is not available**. Either the worker never names `fetch`
   or `XMLHttpRequest` in a form the two rules report — Workbox's own network call lives in `node_modules`, which
   the lint script does not scan — or T07's frozen assertions must be deliberately re-opened. For the record, the
   banned forms include bare `fetch(...)` and `self.fetch`, but **not** `self.addEventListener`.

**Scope boundary I cannot resolve alone (for Phase 1/2, flagged, not decided).** `package.md` §10 contains five
bullets. Task 12's statement covers one of them (the update policy). The other four — the build-time flag, the
remote configuration document fetched NetworkOnly, the kill switch, and **"Forced update for rollback"** with its
denylist of build identifiers that *forces* a reload — are cited by **no task in `tasks.md`**: grepping the whole
task list for `remote config`, `kill switch`, `build identifier` and `flag` returns only task 12 itself and three
unrelated Phase-2 payment tasks. `agents.md:91-92` says only *"Rollback is handled as stated in `package.md` §10"*,
which reads as a pointer, not as a T12 obligation. Whether T12 owns the forced-reload half is the central scope
question for this task, and a forced reload is in direct tension with "a new build waits for a user reload".

**Genuine unknowns — I do not know:**

- Whether the prompt UI is T12's or a later task's. `package.md` §8 maps `serviceWorkerUpdateWaitsForReload` to
  **R57**, which is purely about navigation and caching and says nothing about updates; no requirement in
  `requirements.md` describes a reload prompt, and `tasks.md` names no later task for it. Phase 1 must derive this.
- Whether a `manifest.webmanifest` is wanted at all. T01 asserts it is absent; nothing in the spec requires one;
  `vite-plugin-pwa` emits one by default.
- Which worker strategy the plugin should run in (`generateSW` vs `injectManifest`). The spec names the plugin and
  Workbox but never the mode, and the choice decides whether the T09–T11 predicates can be imported into the
  worker at all.
- How a build identifier would be available to the worker without an environment key reaching the bundle (R58,
  enforced by T01 `:121-124`). Relevant only if the forced-reload half is in scope.

## References

`spec/frontend/tasks.md:21`; `agents.md:11`, `:87-92`; `design.md:51` (L13), `:61-62` (L17), `:194` (§5), `:220`
(§6); `requirements.md:203-205` (R57); `package.md:99` (§8), `:172`, `:311-323` (§10), `:467`;
`frontend/src/pwa/*`, `frontend/src/app/shellBuildsAndMounts.test.tsx`,
`frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts`, `frontend/vite.config.ts`,
`frontend/.oxlintrc.json`, `frontend/package.json`, `infra/local/edge/Caddyfile`.
