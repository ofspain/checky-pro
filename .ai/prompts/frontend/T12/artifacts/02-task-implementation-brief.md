# frontend · T12 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` — **the only specification phases 5–13 may use** |

## Task

`tasks.md:21`, verbatim:

> **Prompt-to-reload update policy.** The worker never calls `skipWaiting` or `clients.claim`, and a new build waits
> for a user reload. Verifies `serviceWorkerUpdateWaitsForReload`. Cites L17, `package.md` §10.

## Purpose

Register the service worker for the first time, under an update policy that never takes effect behind the user's
back. T09, T10 and T11 wrote the worker's *policy* as pure predicates; nothing registers a worker, so none of it
runs yet. This task makes the policy real and fixes how a new build arrives: it waits.

The reason is correctness, not freshness. This SPA's worker decides whether an identity navigation is answered from
cache and whether an API response is cached at all (R57, L17). A worker that activates mid-session — which is
exactly what `skipWaiting` + `clients.claim` cause — swaps those rules underneath a page already holding tokens in
memory, with a half-applied session. Waiting for a reload makes the switch happen at the one moment the page's
in-memory state is being rebuilt anyway.

## Scope

**In**

- Add `vite-plugin-pwa` (Workbox) to `frontend/package.json` and wire it in `frontend/vite.config.ts` (L13).
- One worker source under `frontend/src/pwa/`, deriving its navigation denylist from T09 and its request policy
  from T11 **by import**.
- Exactly one registration site, with worker scope confined to the SPA's own prefix.
- The update policy: no `skipWaiting`, no `clients.claim`, no auto-reload; a new build reaches `waiting` and stays
  there until the user reloads.
- A programmatic hook that reports "an update is waiting", so a later screen can render a prompt without changing
  this task's policy.
- The named test `serviceWorkerUpdateWaitsForReload`, plus the boundary tests listed below.
- The three minimal edits to T01's absence assertions, each justified in the phase 6 notes.

**Out**

- **The visible reload prompt** (banner, dialog, copy). Pending OQ-A. No requirement describes one, no catalogue
  string exists for one, and L11 would impose focus management and a live region. The programmatic hook is in
  scope; the UI is not.
- **"Forced update for rollback"** (`package.md:321-323`) — the remote configuration document, the build-time flag,
  the kill switch, and the build-identifier denylist that forces a reload. Pending OQ-B. Unowned by any task in
  `tasks.md`, and in direct tension with waiting for a user reload.
- Offline behaviour as a feature: caching beyond the static shell and assets, offline fallbacks, background sync,
  push notifications, periodic sync.
- A web app manifest's *contents* and icons as a product decision (the plugin may emit a manifest; naming the
  product's icons and install experience is not this task).
- Any change to the eleven navigation paths or the twelve network-only prefixes. They are imported as given.
- Any route, screen, auth or API behaviour.

## Business Rules

| ID | Rule |
|---|---|
| **R57** | A navigation to an identity or backend path is never answered from cache and never gets the shell — the policy this task first makes live; it must not be weakened by registering a worker. |
| **R58** | The production bundle contains no secret and no environment key outside the §4c public list. |
| **`agents.md:91`** (LOCKED, no `R`) | One worker-wide update policy: prompt-to-reload; `skipWaiting` and `clients.claim` are not used. Restated at `package.md:320`. **This is the rule under test** — `package.md:172` maps the named test to R57, which does not describe it (OQ, phase 1 OQ-1; non-blocking, no spec file is modified here). |

## Locked Decisions

`L17` (scoped — worker boundaries), `L13` (`vite-plugin-pwa`/Workbox is the stack; a hand-rolled worker is not an
option), `L14` + `L9` (SPA under `/app`, single origin through the edge — bounds worker scope), `L10`/`R58` (no env
keys in the bundle), `L11` (anything user-facing is accessible and catalogue-only), `L12` (phase additivity),
`L3`/`L16` (no new client-side storage; cache is static shell and assets only, `design.md:194`), `L6` (backend calls
go through the generated client; two oxlint rules ban `fetch`/`XMLHttpRequest`).

## Dependencies

- **New package:** `vite-plugin-pwa` + `workbox-*` transitives. **Not installable on this host** — see OQ-C.
- **Existing modules:** `src/pwa/navigationDenylist.ts` (`NAVIGATION_DENYLIST`, `isShellNavigation`),
  `src/pwa/apiRequestPolicy.ts` (`NETWORK_ONLY_PATH_PREFIXES`, `isCacheableRequest`).
- **Browser APIs:** `navigator.serviceWorker.register`, registration lifecycle (`installing`, `waiting`, `active`,
  `updatefound`, `statechange`, `controllerchange`). **Forbidden:** `skipWaiting`, `clients.claim`.
- **Config keys:** none. **Contracts:** none.
- **Edge:** `infra/local/edge/Caddyfile:28` routes only `/app*` to the SPA.

## Inputs

The two policy modules' exports; the Vite build's own asset graph (for the precache manifest); the browser's
registration lifecycle events. No user input, no network input, no configuration input.

## Outputs

A registered worker; an emitted worker bundle and precache manifest in `dist/`; a programmatic "update waiting"
signal. No API calls, no events, no messages to other services.

## State Changes

- **Client-side:** the Workbox precache — **static shell and assets only** (`design.md:194`). No sessionStorage, no
  localStorage, no IndexedDB entry is added by this task.
- **Server-side:** none.
- **Lifecycle:** a new build's worker occupies `waiting` indefinitely; the controlling worker is unchanged until the
  user reloads.

## Files to Create

| Path | Contents |
|---|---|
| `frontend/src/pwa/serviceWorker.ts` *(name to be settled in phase 5)* | the worker entry: navigation route with T09's denylist, NetworkOnly for everything not cacheable, precache of the shell and assets, **no** `skipWaiting`, **no** `clients.claim` |
| `frontend/src/pwa/registerServiceWorker.ts` *(name to be settled in phase 5)* | the single registration site, scope-confined, exposing the "update waiting" hook |
| `frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts` | the named test and its boundary cases |

## Files to Modify

| Path | Change |
|---|---|
| `frontend/package.json` | add `vite-plugin-pwa` to `devDependencies` |
| `frontend/vite.config.ts` | add the plugin; configure it so nothing self-activates |
| `frontend/src/main.tsx` *or* `index.html` | the one registration call (phase 5 decides which) |
| `frontend/src/app/shellBuildsAndMounts.test.tsx` | **only** the three absence assertions at `:75-76`, `:77-80` and `:103-116`, each edit justified; everything else in the file untouched |

## Files NOT to Modify

- `frontend/src/pwa/navigationDenylist.ts`, `frontend/src/pwa/apiRequestPolicy.ts` and their three test files —
  imported, never edited. (One exception is *permitted but not required*: collapsing the duplicated
  `segmentPrefixPattern` helper, carried forward from T11. If taken, the T09 and T11 suites must stay green
  unchanged; if not taken, say so.)
- `frontend/.oxlintrc.json` — T07 asserts it has **no `overrides`** key and that the lint run uses exactly 2 rules
  (`lintRejectsHandWrittenBackendFetch.test.ts:194-195`, AC4). The worker must therefore avoid the restricted
  `fetch`/`XMLHttpRequest` forms in `src/**` rather than being exempted.
- Everything under `spec/`, `contracts/`, `services/`, `infra/`, `libs/`.
- Every other T01–T11 test file and source file.

## Acceptance Criteria

| # | Criterion |
|---|---|
| **AC1** | Neither `skipWaiting()` nor `clients.claim()` appears in the worker source **or in the emitted worker bundle**, in any spelling (`self.skipWaiting`, inside an `install` listener, `self.clients.claim`). Asserted against the real build output. |
| **AC2** | The plugin is configured so the generated worker never self-activates: no `skipWaiting`-equivalent option is enabled and the registration performs no automatic reload. |
| **AC3** | While a new worker is `waiting`, the controlling worker is still the previous one; only a reload promotes it. This is the named test's core. |
| **AC4** | Exactly one worker and one registration site exist, found by scanning `src/**`, with no per-route or per-asset update behaviour. |
| **AC5** | Worker scope does not exceed the SPA's own prefix, so no denylisted navigation can reach it even in principle. |
| **AC6** | The worker's navigation denylist **is** `NAVIGATION_DENYLIST` (identity, not a copy) and its request policy is derived from `apiRequestPolicy`; both are imported, neither is restated. |
| **AC7** | The precache manifest contains **no** URL matching `NAVIGATION_DENYLIST`, **and** `/app/signed-out` is still answered from the shell. Both halves, because a precache route matches by URL ahead of the navigation fallback. |
| **AC8** | No `import.meta.env` and no absolute `http(s)` URL in any new source (R58, already enforced over all of `src/**` by T01 `:121-124`); no new client-side storage; the cache holds static shell and assets only. |
| **AC9** | No user-facing string is introduced outside `src/i18n/catalogue.ts`. The task states plainly that the visible prompt is out of scope and which later task owes it. |
| **AC10** | A drift guard fails if `agents.md:91` or `package.md:320` is softened, deleted, or begins to permit `skipWaiting`/`clients.claim`. |
| **AC11** | The suite is green against the recorded baseline (**314/316**, both known failures in `lintRejectsHandWrittenBackendFetch.test.ts`: AC4's missing oxlint summary line and AC2's `spawn pnpm ENOENT`), and the only pre-existing file edited is `shellBuildsAndMounts.test.tsx`, limited to the three absence assertions. |

## Required Tests

**Named:** `serviceWorkerUpdateWaitsForReload` → `frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts`
(`package.md:172`).

1. Source **and** emitted-bundle scan for every `skipWaiting`/`clients.claim` spelling, plus the plugin options that
   would inject them (AC1, AC2).
2. Lifecycle simulation with a fake registration: a second worker installs, sits in `waiting`, the controller is
   unchanged; a reload — and only a reload — promotes it (AC3).
3. Exactly one registration site, located by scanning `src/**` (AC4).
4. Scope asserted on the registration call and on the emitted worker's URL (AC5).
5. Composition with T09/T11: identity check that the generated denylist **is** `NAVIGATION_DENYLIST`, and that a
   request outside the cacheable set is NetworkOnly (AC6).
6. Precache manifest, both halves (AC7).
7. Real-build assertions over `dist/` in T01's style — temp dir, real `vite build` — the only way AC1, AC5 and AC7
   can be checked against what ships.
8. Drift guards over `agents.md:91` and `package.md:320` (AC10).
9. `shellBuildsAndMounts.test.tsx` passes in full after its three edits, with its other assertions intact (AC11).

## Constraints

- **Security.** The worker is a security boundary: it must not cache an identity or API response, must not answer a
  denylisted navigation, and must not activate mid-session. No token, PIN, recovery code, API key, password or PII
  may be cached, logged or sent to analytics (`agents.md:78-85`). Scope is the first line of defence (AC5).
- **Module boundaries.** Policy stays in `src/pwa/`; the worker imports the policy modules and adds no rules of its
  own. Nothing in `src/app/` or `src/api/` learns about caching. The registration site is the only bridge from the
  page into the worker.
- **Lint boundary.** `src/**` may not name `fetch`, `XMLHttpRequest`, or `self.fetch` in a restricted form, and
  `.oxlintrc.json` may not be given an `overrides` block. Workbox's own network call lives in `node_modules`, which
  the lint script does not scan.
- **Thread-safety / concurrency.** A worker runs in a separate thread from every page and is shared by all tabs:
  one worker-wide policy means a waiting update must not be applied just because *one* tab reloaded while others
  still hold the old controller. Lifecycle state belongs to the registration, not to a page.
- **Transactions.** None.
- **Null handling.** `navigator.serviceWorker` is absent in jsdom and on unsupported or insecure origins;
  `registration.waiting` and `registration.installing` are legitimately `null`. Registration must degrade quietly
  and must never throw into the mount path — T01 `:103-116` asserts the shell mounts with no worker at all.
- **Performance.** Precache only what the shell needs; a first visit must not be slowed by caching assets a user
  may never request.
- **Build.** `vite build` must stay green and `base: '/app/'` must keep producing `/app/assets/...` URLs (T01
  `:68-72`).

## Open Questions

Three, two of them scope decisions for the phase 4 gate and one an environment blocker. None prevents phases 3–5.

- **OQ-A — does T12 own the visible reload prompt, or only the policy?** "Prompt-to-reload" names a UI, but the
  task statement is satisfiable with no UI (the default lifecycle already waits), no requirement describes a
  prompt, no catalogue string exists, no later task in `tasks.md` claims one, and L11 would impose focus management
  and a live region. **This brief assumes policy + programmatic hook, UI out of scope.** If that is wrong, AC9
  changes from "no new strings" to a full accessible-prompt criterion and the task roughly doubles.
- **OQ-B — does T12 own "Forced update for rollback" (`package.md:321-323`)?** The task cites §10 as a whole but
  states only its update-policy bullet. The remote configuration document, the build-time flag, the kill switch and
  the build-identifier denylist are cited by no task in `tasks.md`; `agents.md:92` says only "Rollback is handled as
  stated in `package.md` §10", which reads as a pointer. A forced reload is also the opposite of waiting for a user
  reload, and would need a build identifier in the bundle, which R58 and T01 `:121-124` constrain. **This brief
  assumes out of scope**, and records the forced-rollback half as unowned spec surface for the owner to assign.
- **OQ-C — `vite-plugin-pwa` cannot be installed on this host (blocks phase 6, not 3–5).** It is absent from
  `package.json`, from `pnpm-lock.yaml` (no `vite-plugin-pwa`, no `workbox-*`) and from the local store. The
  registry is reachable (HTTP 200), but **`pnpm` is not installed**: `packageManager` declares `pnpm@12.14.0`,
  `~/Library/pnpm/store` exists, and no `pnpm` or `corepack` binary is on PATH in any shell — only `npm`, whose
  lockfile would be wrong for this workspace. Owner input needed: install `pnpm`, run the install inside the
  `frontend` container, or accept an `npm`-written lockfile. Same root cause as T07's failing AC2 end-to-end case.
