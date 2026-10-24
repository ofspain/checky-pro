# frontend · T12 — Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T12 — Prompt-to-reload update policy**. Findings are against the brief itself; no code was changed.

---

## Findings

### 1. `vite-plugin-pwa` cannot be installed on this host — phase 6 is blocked
**Issue:** Open Question C states `pnpm` is not on PATH, so the package cannot be installed and the worker cannot be emitted. This is an absolute blocker for implementation, testing against `dist/`, and verifying the emitted bundle for `skipWaiting`/`clients.claim`.  
**Severity:** High — implementation cannot proceed without owner input.  
**Evidence ·** Brief §Dependencies: "`vite-plugin-pwa` + `workbox-*` transitives. **Not installable on this host**"; §Open Questions OQ-C.  
**Recommendation ·** Resolve before Phase 6 by one of: installing `pnpm` (preferred), running the install inside the `frontend` Docker container, or accepting an `npm`-written lockfile with documented risk. Record the chosen path in the frozen brief.

### 2. The Workbox strategy (`generateSW` vs `injectManifest`) is not specified
**Issue:** The brief says "one worker source under `frontend/src/pwa/`" that imports T09/T11 policy modules. `vite-plugin-pwa`'s default `generateSW` strategy produces the worker from a config object and does **not** allow importing project source modules into the generated service worker. To write a worker source that imports `navigationDenylist.ts` and `apiRequestPolicy.ts`, the plugin must be configured for `injectManifest`.  
**Severity:** High — wrong strategy means AC6 (import identity) is impossible and the policy modules are dead code.  
**Evidence ·** Brief §Scope: "One worker source under `frontend/src/pwa/`, deriving its navigation denylist from T09 and its request policy from T11 **by import**"; §Files to Create `serviceWorker.ts`.  
**Recommendation ·** Lock the frozen brief to `injectManifest` with `srcDir: 'src/pwa'`, `filename: 'serviceWorker.ts'`, and `injectManifest` mode. The worker entry file will import Workbox strategies/routing and the two policy modules; the plugin injects `self.__WB_MANIFEST` at build time.

### 3. The worker scope is not explicitly configured
**Issue:** AC5 requires the worker scope not to exceed the SPA's own prefix. With `base: '/app/'`, `vite-plugin-pwa` will emit the worker under `/app/` and default its scope to the script's directory (`/app/`). If the registration call does not pass an explicit `scope`, a base-path mismatch or a future move could widen scope silently.  
**Severity:** Medium — security boundary risk.  
**Evidence ·** Brief §AC5; §Files to Modify `frontend/src/main.tsx` or `index.html`.  
**Recommendation ·** Add a locked decision: register with `{ scope: '/app/' }` and assert in tests that the registration scope is exactly `/app/`. Also configure the plugin so the emitted worker script URL is under `/app/`.

### 4. The registration API / "update waiting" hook shape is not defined
**Issue:** The brief says expose "a programmatic hook that reports 'an update is waiting'", but it does not specify the API. A later screen will consume it, so the contract matters now.  
**Severity:** Medium — integration boundary risk.  
**Evidence ·** Brief §Scope: "A programmatic hook that reports 'an update is waiting'"; §Files to Create `registerServiceWorker.ts`.  
**Recommendation ·** Define the frozen API, for example:
```ts
export function registerServiceWorker(): Promise<void>;
export function onUpdateWaiting(listener: () => void): () => void;
export function skipWaitingAndReload(): void; // for the future prompt UI
```
Keep `registerServiceWorker` idempotent and quiet when `navigator.serviceWorker` is absent.

### 5. It is unclear how the navigation fallback is wired
**Issue:** T09 exports `isShellNavigation` and `NAVIGATION_DENYLIST`; T11 exports `isCacheableRequest` and `NETWORK_ONLY_PATH_PREFIXES`. The brief says the worker derives its navigation denylist and request policy from these, but it does not say how Workbox's navigation fallback is configured. With `injectManifest`, the worker source must create a `NavigationRoute` with a shell handler and the denylist.  
**Severity:** Medium — if left unspecified, task 12 may produce a worker that serves the shell for denylisted navigations or fails to serve `/app/signed-out`.  
**Evidence ·** Brief §Files to Create `serviceWorker.ts`; AC6, AC7.  
**Recommendation ·** Add to the frozen brief:
```ts
import { NavigationRoute, registerRoute, setDefaultHandler } from 'workbox-routing';
import { NetworkOnly } from 'workbox-strategies';
import { createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching';
import { NAVIGATION_DENYLIST } from './navigationDenylist';

precacheAndRoute(self.__WB_MANIFEST);
registerRoute(new NavigationRoute(createHandlerBoundToURL('/app/index.html'), { denylist: NAVIGATION_DENYLIST }));
setDefaultHandler(new NetworkOnly());
```
This pins AC6, AC7, and the NetworkOnly default in one place.

### 6. The shell precache key may not be `/app/index.html`
**Issue:** `createHandlerBoundToURL('/app/index.html')` assumes the precache manifest contains exactly that URL. With `base: '/app/'`, `vite-plugin-pwa` may emit the shell as `/app/index.html` or `/index.html` depending on plugin version and `manifestTransforms`. A mismatch would make the navigation fallback fail.  
**Severity:** Medium — runtime failure.  
**Evidence ·** Brief §Constraints: "`base: '/app/'` must keep producing `/app/assets/...` URLs"; AC7.  
**Recommendation ·** Add an AC7 assertion that the emitted precache manifest contains `/app/index.html` (or whatever path the worker uses) and no denylisted path. Derive the expected shell path from the worker source constants so the test follows a single source of truth.

### 7. The three T01 edits are described by line numbers but not by new expected values
**Issue:** The brief says edit `shellBuildsAndMounts.test.tsx` at `:75-76`, `:77-80`, `:103-116`, but it does not say what the assertions should become. A reviewer cannot verify the edits are minimal without reading the file.  
**Severity:** Low — implementation detail, but should be locked.  
**Evidence ·** Brief §Files to Modify.  
**Recommendation ·** Include the exact new assertions in the frozen brief, e.g.:
- Remove or invert "no `sw.js` exists in `dist/`" to assert the worker is emitted.
- Remove or invert "no built asset mentions `serviceWorker`" to allow the registration module.
- Remove or invert "mounting registers nothing" to assert `registerServiceWorker` is called (or that `navigator.serviceWorker.register` is invoked).

### 8. The plugin may emit a `manifest.webmanifest` that T01 currently forbids
**Issue:** T01 likely asserts no `manifest.webmanifest` is emitted. `vite-plugin-pwa` can emit one. The brief mentions three edits to T01 but does not mention the manifest.  
**Severity:** Low — may require a fourth T01 edit or a plugin config to disable the manifest.  
**Evidence ·** Brief §Files to Modify; T01 absence assertions.  
**Recommendation ·** Decide in the frozen brief: either (a) disable manifest emission in the plugin config (`manifest: false`), or (b) add a T01 edit to allow the emitted manifest. Since manifest contents are out of scope, disabling it is simpler.

### 9. Forced-update/rollback is out of scope but still cited
**Issue:** The brief correctly notes that forced update for rollback (`package.md:321-323`) is out of scope, but `package.md:320` is cited as the rule under test and the paragraph also describes rollback. A future reader may assume T12 implements both.  
**Severity:** Info.  
**Evidence ·** Brief §Business Rules R58/`agents.md:91`/`package.md:320`; §Open Questions OQ-B.  
**Recommendation ·** Add an explicit locked decision: "T12 implements only the prompt-to-reload policy; the forced-update/rollback mechanism is unowned and out of scope."

### 10. No design for how the worker source stays lint-clean
**Issue:** The worker source will import Workbox and the policy modules. `.oxlintrc.json` has no overrides and bans `fetch`/`XMLHttpRequest` in `src/**`. Workbox's `NetworkOnly` strategy uses `fetch` internally in `node_modules`, which is fine, but the worker source itself must not name `fetch`.  
**Severity:** Info — manageable if the source uses only Workbox APIs.  
**Evidence ·** Brief §Files NOT to Modify `.oxlintrc.json`; §Constraints.  
**Recommendation ·** Add a constraint: the worker source may only use Workbox's typed APIs (`workbox-routing`, `workbox-strategies`, `workbox-precaching`) and the policy imports; no direct `fetch`, `XMLHttpRequest`, or `self.fetch`.

---

## Open Questions

- **OQ-C is the only true blocker** and must close before Phase 6.
- **OQ-A and OQ-B** are scope decisions that should be locked in the frozen brief: visible prompt out of scope, forced rollback out of scope.
- **Workbox strategy, scope, registration API, and navigation-fallback wiring** are architectural decisions that should also be locked before Phase 6.
