# frontend · T12 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code in this phase. Every file below traces to the frozen brief's Files sections; nothing is added.

## One decision the brief left to this phase

**Registration is called from `src/main.tsx`, outside `mountShell`, and is not guarded by an environment check.**

Two consequences, both deliberate:

1. **T01's `:103-116` assertion stays untouched.** That test imports `mountShell` directly and never executes
   `main.tsx`, so mounting still registers nothing and the spy assertion remains true as written. Under D-6 this was
   the preferred outcome; it is now the chosen one. Only edits 1 and 2 of D-6 are needed.
2. **There is no `import.meta.env.PROD` guard, because there cannot be one.** T01 `:123` asserts no production
   source contains `import.meta` + `.env` (R58, L10), and that scan covers all of `src/**`. So registration runs in
   dev too, where no worker is emitted (`devOptions` left disabled, D-7's sibling default) and the request for
   `/app/serviceWorker.js` 404s. `registerServiceWorker` therefore **must** treat a failed registration as a normal
   outcome and resolve `undefined` rather than throw — which D-3 already requires for the jsdom and
   insecure-origin cases. This is a derivation from R58, not a workaround.

## Files to create

| # | Path | Purpose |
|---|---|---|
| 1 | `frontend/src/pwa/serviceWorker.ts` | the worker entry (D-1 `filename`, D-4 wiring). Precache + navigation route with T09's denylist + `NetworkOnly` default. No `skipWaiting`, no `clients.claim`. |
| 2 | `frontend/src/pwa/registerServiceWorker.ts` | the single registration site (D-2 scope, D-3 surface). |
| 3 | `frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts` | the named test (`package.md:172`) and its boundary cases. |

## Files to modify

| # | Path | Change |
|---|---|---|
| 4 | `frontend/package.json` | add the four pinned `devDependencies` of D-1 |
| 5 | `frontend/vite.config.ts` | add `VitePWA({...})` per D-1 and D-7 |
| 6 | `frontend/src/main.tsx` | one added import and one added call |
| 7 | `frontend/src/app/shellBuildsAndMounts.test.tsx` | D-6 edits 1 and 2 only, plus the `:74` comment |

Also written, by the package manager rather than by hand: `pnpm-lock.yaml` (workspace root).

## Public surface (signatures)

**`registerServiceWorker.ts`** — exactly D-3's two functions, plus the scope constant the test asserts against:

```ts
export const SERVICE_WORKER_SCOPE = '/app/';
export const SERVICE_WORKER_URL = '/app/serviceWorker.js';

export function registerServiceWorker(): Promise<ServiceWorkerRegistration | undefined>;
export function onUpdateWaiting(listener: () => void): () => void;
```

- `registerServiceWorker` — idempotent (a module-level promise is returned on a second call); resolves `undefined`
  when `navigator.serviceWorker` is absent or registration rejects; never throws; registers with
  `{ scope: SERVICE_WORKER_SCOPE }`.
- `onUpdateWaiting` — subscribes to "a new build is waiting"; returns an unsubscribe. Fires if a worker is
  **already** waiting at subscribe time, so a late subscriber is not missed.

**`serviceWorker.ts`** — no exports; it is an entry, not a module. It declares `self` as
`ServiceWorkerGlobalScope & { __WB_MANIFEST: PrecacheEntry[] }` and carries the D-8 scope statement in its header.

**`apiRequestPolicy.ts`** — one addition, if phase 6 confirms it is needed for D-4/D-5: a single exported shell-URL
constant derived from the existing `SHELL_DOCUMENT_PATHS`, so the worker binds the fallback to the same source of
truth the policy already states. **No existing export changes**, and T11's suite must stay green untouched. If the
existing constants suffice, nothing is added.

## Private helpers

In `registerServiceWorker.ts` only:

- `trackWaiting(registration)` — wires `updatefound` → the new worker's `statechange` → notify when it reaches
  `waiting`; also notifies for an already-`waiting` worker found at registration time.
- `notify()` — fan-out to the listener set, each call guarded so one throwing listener cannot stop the others.

Nothing private in `serviceWorker.ts`: D-4's wiring is six statements with no branching, which is the point —
every decision it makes is imported.

## Entities · repositories · services used

None. This task has no persistence, no repositories and no services; the backend-shaped sections of the phase
prompt do not apply to the SPA. The nearest equivalents are the two existing policy modules
(`navigationDenylist.ts`, `apiRequestPolicy.ts`), consumed by import and not modified beyond the one optional
constant above.

## Tests required

All in `frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts` unless stated.

| Group | Cases | AC |
|---|---|---|
| **Forbidden calls — source** | the worker source, the registration source, and `vite.config.ts` contain no `skipWaiting`, `clients.claim`, `self.clients.claim`, or `skipWaiting` inside an `install` listener; the plugin config enables no `skipWaiting`-equivalent option | AC1, AC2 |
| **Forbidden calls — emitted bundle** | real `vite build` into a temp dir (T01's pattern); the emitted `serviceWorker.js` and every `assets/*.js` contain neither string | AC1 |
| **Waiting lifecycle** | a fake registration: a second worker installs → sits in `waiting` → `navigator.serviceWorker.controller` is unchanged; `onUpdateWaiting` fires once per new build; an already-waiting worker notifies a late subscriber; nothing in the module calls `postMessage`, `reload`, or `location.assign` | AC3 |
| **One policy, one site** | scanning `src/**`, exactly one file calls `.register(`; exactly one worker entry exists; no per-route or per-asset update behaviour | AC4 |
| **Scope** | the registration is called with `{ scope: '/app/' }`; the emitted worker URL is under `/app/`; `SERVICE_WORKER_SCOPE` is `'/app/'` | AC5 |
| **Composition with T09/T11** | the `NavigationRoute`'s denylist **is** `NAVIGATION_DENYLIST` (`toBe`, identity); the fallback's shell URL derives from `apiRequestPolicy`'s constants, not a literal; a request outside the cacheable set reaches the `NetworkOnly` default; the twelve prefixes are all non-cacheable | AC6 |
| **Precache manifest, both halves** | over the real build: **no** manifest entry's URL matches any pattern in `NAVIGATION_DENYLIST`; the manifest contains the shell URL the worker binds to (D-5); `/app/signed-out` is still answered from the shell | AC7 |
| **Bundle hygiene** | no `import.meta.env` and no absolute URL in the new sources (already enforced globally by T01, asserted here for the worker specifically); registration writes no storage; the precache holds only the shell and `/app/assets/*` | AC8 |
| **No new copy** | no user-facing string is introduced; `src/i18n/catalogue.ts` is unchanged | AC9 |
| **Drift guards** | `agents.md:91` still forbids both calls (whitespace-collapsed match, per T11's lesson); `package.md:320` still states prompt-to-reload; both fail if softened or deleted | AC10 |
| **Elsewhere** | `shellBuildsAndMounts.test.tsx` passes in full after D-6's two edits; the T09, T10 and T11 suites pass unchanged; whole suite at the 314/316 baseline | AC11 |

Mutation pass (phase 10), carrying T09's and T11's lessons: commit the tests **before** running mutants, because
the revert discards uncommitted work; and for each drift guard, mutate the guarded source rather than the
assertion, since a weakened guard can never be caught.

## Execution order

The phase prompt's order is schema → dao → service → api → tests. Mapped onto a build-time concern, the dependency
order is: dependencies → build config → worker → registration → entry → test edits → new tests.

1. **Install** the four pinned packages with `npx -y pnpm@12.14.0 --filter @themistra/frontend add -D ...` (D-0),
   then confirm `vite-plugin-pwa@2.0.0` really exposes `strategies`, `srcDir`, `filename`, `injectManifest` and
   `manifest` under those names, from its own types rather than from assumption (D-1's open requirement).
2. **Wire the plugin** in `vite.config.ts`. Build once with a stub worker and read the actual output: the emitted
   worker's filename and URL, and the exact precache manifest entries — this is what settles D-5's constant and
   confirms the shell URL. Nothing downstream is written against a guess.
3. **Write the worker** (`serviceWorker.ts`) per D-4, importing both policy modules; add `apiRequestPolicy.ts`'s
   shell constant only if step 2 shows it is needed.
4. **Write the registration** (`registerServiceWorker.ts`) per D-2 and D-3, with the waiting-lifecycle tracking.
5. **Call it** from `main.tsx`.
6. **Edit T01** — D-6 edits 1 and 2, and the `:74` comment. Run that file alone and confirm it passes.
7. **Write the new test file**, in the order of the table above, finishing with the real-build cases, which are the
   slowest and depend on everything else being right.
8. **Run** `tsc --noEmit`, `oxlint` (expecting no `overrides` and still 2 rules), the `src/pwa` suite, then the
   whole suite against the 314/316 baseline.

Steps 1 and 2 are the only ones that can invalidate a later decision, which is why they come first and why both end
in "read what actually happened" rather than "apply the config".
