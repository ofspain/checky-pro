# frontend · T12 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

## Result

| Check | Result |
|---|---|
| `vitest run src/pwa` | **214/214** (167 from T09–T11, unchanged + 47 new) |
| `vitest run` (whole suite) | **361/363** — the only failures are the two known T07 environmental ones |
| `tsc --noEmit` | clean |
| `oxlint --no-ignore -c .oxlintrc.json src` | exit 0, no violations, `.oxlintrc.json` untouched |
| `vite build` | clean; worker emitted, no web app manifest |

Baseline held: the suite went from 314/316 to 361/363 — the same two failures
(`lintRejectsHandWrittenBackendFetch` AC4's missing oxlint summary line and AC2's `spawn pnpm ENOENT`), plus 47
passing cases.

## What was built

| File | |
|---|---|
| `frontend/src/pwa/serviceWorker.ts` | **new.** Three statements of wiring, by design: `precacheAndRoute`, then a `NavigationRoute` carrying T09's denylist, then `setDefaultHandler(new NetworkOnly())`. It decides nothing itself. |
| `frontend/src/pwa/registerServiceWorker.ts` | **new.** `SERVICE_WORKER_SCOPE`, `SERVICE_WORKER_URL`, `registerServiceWorker()`, `onUpdateWaiting()`. No function promotes a waiting worker — there deliberately isn't one. |
| `frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts` | **new**, 47 cases. |
| `frontend/src/pwa/apiRequestPolicy.ts` | the one authorized addition: `SHELL_DOCUMENT_URL`, with `SHELL_DOCUMENT_PATHS` redefined in terms of it. Same value, no export removed; T11's 83 cases pass unchanged. |
| `frontend/vite.config.ts` | `VitePWA({ strategies: 'injectManifest', srcDir: 'src/pwa', filename: 'serviceWorker.ts', injectRegister: null, manifest: false })`. |
| `frontend/src/main.tsx` | one import, one `void registerServiceWorker()` after the mount. |
| `frontend/src/app/shellBuildsAndMounts.test.tsx` | **three sites edited** — see below. |
| `frontend/package.json`, `pnpm-lock.yaml` | the four pinned packages. |

Execution followed the plan's order. Steps 1 and 2 were run first precisely because they could invalidate later
decisions, and step 2 did.

## Step 1 — the install, and D-1 verified rather than assumed

`npx -y pnpm@12.14.0 --filter @themistra/frontend add -D ...` installed `vite-plugin-pwa@2.0.0`,
`workbox-routing@7.4.1`, `workbox-strategies@7.4.1`, `workbox-precaching@7.4.1`. 309 packages resolved, no global
install, lockfile written by the workspace's own pinned package manager (`pnpm-lock.yaml`, +3,734 lines).

D-1 required confirming the option names from the plugin's own types rather than from assumption. Done:
`node_modules/vite-plugin-pwa/dist/index.d.ts` declares `srcDir` (`:291`), `filename` (`:299`),
`strategies: 'generateSW' | 'injectManifest'` (`:307`), `injectManifest` (`:368`), `manifest` (`:355`),
`injectRegister` (`:332`) and `devOptions` (`:404`). The config is written against the real surface.

## Step 2 — what the build actually does, which changed one decision

The probe build (stub worker, `precacheAndRoute(self.__WB_MANIFEST)` only) settled three things:

1. **`filename: 'serviceWorker.ts'` emits `/app/serviceWorker.js`.** `sw.js` is not produced, so T01 can keep an
   assertion about it — and now does, as proof the option took effect.
2. **`injectRegister: null` injects nothing** into `index.html`. Verified by reading the emitted HTML: the only
   script is `/app/assets/index-<hash>.js`.
3. **The precache manifest URLs are base-relative, not absolute** — `index.html` and `assets/index-<hash>.js`, not
   `/app/index.html`. Kimi's finding 6 was right to flag this, and the consequence is sharper than the finding:

   > Matching those raw strings against `NAVIGATION_DENYLIST`, whose patterns are anchored at `^/`, **passes
   > vacuously**. An AC7 test written the obvious way would assert nothing at all.

   So the test resolves each entry against the worker's own location (`new URL(url, `${ORIGIN}/app/`)`) before
   matching, and additionally asserts each resolved path lands under `/app/` — which is *why* none of them can be
   denylisted. Workbox resolves precache URLs the same way at runtime, so `createHandlerBoundToURL('/app/index.html')`
   does match the stored key; D-5's constant is correct as written.

**Honest limit on AC7's first half.** While `base` is `/app/` and the plugin precaches only build output, the build
*cannot* emit a denylisted precache entry — `/app` is not in the denylist. The assertion is therefore a **guard
against a future change** (a different `base`, a public asset served outside `/app`, a `manifestTransforms` that
rewrites URLs), not a live catch today. Phase 10 will mutate `base` to prove the guard bites rather than leaving
that claim untested.

## Deviations from the plan, and three defects found on the way

### 1. T01 needed **three** edits, not two — and the third was not the one predicted

D-6 named three candidate sites and phase 5 argued only two would be needed, because registering from `main.tsx`
leaves the register-spy assertion (`:103-116`) untouched. That part held: **the spy assertion is untouched and
still passes.**

But a fourth T01 assertion, which neither D-6 nor the plan listed, broke immediately:

```
it('keeps the entry to a single mountShell call (shell code lives in src/app/)')
  expect(imports).toEqual(['./app/mountShell']);
  expect(statements).toEqual(["mountShell(document.getElementById('root'));"]);
```

Registering from `main.tsx` adds exactly one import and one statement, so this is the real cost of that choice —
the plan traded a predicted edit for an unpredicted one. It is edited rather than deleted, and its intent (a thin
entry, shell code living in `src/app/`) is preserved: two statements, each a call into a module.

The two planned edits were applied as D-6 specified, and both **keep a real guarantee instead of losing one**:
`sw.js`/`manifest.webmanifest` absence is kept and `serviceWorker.js` presence added; the "no asset names
`serviceWorker`" loop becomes "no asset names `skipWaiting` or `clients.claim`", which is the stronger property and
the one this task is about.

### 2. A real bug in my own production code, caught by the test for it

`onUpdateWaiting` notifies a late subscriber from inside a `.then()`. That path called the listener **directly**,
bypassing the `try`/`catch` in `notifyUpdateWaiting`, so a throwing listener escaped as an unhandled promise
rejection — vitest reported it as an unhandled error. Fixed by extracting `callListener()` and routing both the
fan-out and the immediate notification through it. The "keeps reporting to the other subscribers when one throws"
case is what surfaced it.

### 3. Two test defects from module identity under `vi.resetModules()`

The AC6 cases import the worker with a reset module registry, so the worker's `NetworkOnly` class and its
`NAVIGATION_DENYLIST` array are **different objects** from the test file's top-level imports. Both
`toBeInstanceOf(NetworkOnly)` and `toBe(NAVIGATION_DENYLIST)` failed with "serializes to the same string".

Fixed by capturing both from the same registry the worker imports from, inside `beforeEach`. **The identity
assertion is still the real one**: a worker that spread, filtered or rebuilt the eleven paths would hand over a new
array and fail against the fresh module's array too. What was wrong was the comparison's reference, not the
property.

### 4. One `as RegExp[]` cast, deliberately

Workbox types `NavigationRoute`'s `denylist` as mutable `RegExp[]`; T09 exports `readonly RegExp[]`, so `tsc`
rejected the direct pass (`TS4104`). Spreading into a copy would have satisfied the type and **broken AC6** — the
route would then hold a snapshot rather than the module's list. The cast keeps identity; Workbox only ever reads
the array. Commented at the call site.

### 5. My own comment tripped T01's bundle scan

The doc comment in `registerServiceWorker.ts` explaining *why* there is no environment guard contained the literal
string it was describing, and T01's `src/**` scan failed the file. Reworded to name Vite's compile-time environment
object without spelling it. Worth recording because the scan is doing exactly its job: it does not care whether the
occurrence is code or prose, and T01's own assertion concatenates the string to dodge itself.

## Confirmations worth stating

- **No `skipWaiting`, no `clients.claim`** — not in either source, not in `vite.config.ts`, and **not in the
  emitted `serviceWorker.js` or any app chunk**. Verified against the real build output, which also confirms
  Workbox 7.4.1's precaching runtime does not reference them on its own.
- **Registration is scope-stated**: `register('/app/serviceWorker.js', { scope: '/app/' })`, and no pattern in
  `NAVIGATION_DENYLIST` matches `/app/`.
- **`.oxlintrc.json` untouched**, no `overrides`, still exactly the two L6 rules; the worker names no `fetch` or
  `XMLHttpRequest`. T07's AC5 passes unchanged.
- **The duplicated `segmentPrefixPattern`** (T11's carried-forward item) was **not** collapsed. The frozen brief
  made it permitted but not required, and collapsing it would mean editing `navigationDenylist.ts`, which this task
  is otherwise able to leave alone. Stated here rather than quietly skipped; it remains open.
- **`registerType`** is absent from the plugin config. It is `generateSW`'s switch for auto-update behaviour; under
  `injectManifest` there is no such option, and the test asserts the string's absence so a later edit cannot
  introduce it unnoticed.

## Open, unchanged

**OQ-1 stands** (frozen brief): `tasks.md:21`'s "waits for a user reload" is not achievable by a refresh, and the
mechanism that would make it so is forbidden by `agents.md:91`. The code is identical under every reading and is
what shipped; what a future prompt may honestly promise the user is an owner decision, needed before the prompt UI
is built, not before this task merges. AC3 is asserted as written in phase 4, not as `tasks.md` words it.
