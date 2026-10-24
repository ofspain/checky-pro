# frontend · T12 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` |

Findings only; nothing is rewritten here. Fixes land in phase 9, after Kimi's phase 8. Reviewed against the frozen
brief, `agents.md` and the phase-7 checklist. The money-type and transaction-boundary headings do not apply to this
task; enumeration-safety is read as secret-handling, which does.

## S1 · A subscriber that arrives before registration finishes is notified twice

**Severity: medium** (a correctness bug in the only behaviour this module exports).

**Evidence:** `frontend/src/pwa/registerServiceWorker.ts:42` and `:75-77`.

`trackWaiting` notifies the whole listener set when a worker is already waiting (`:42`), and `onUpdateWaiting`
*also* notifies its own listener once registration resolves with a waiting worker (`:76`). Those are two different
paths to the same fact, and both run when a listener subscribes **before** registration settles:

1. `onUpdateWaiting` adds the listener to the set, then calls `registerServiceWorker()`.
2. The registration promise resolves → `trackWaiting` runs → `:42` fires → the listener is in the set, so it is
   called.
3. The `.then()` at `:75` runs → `registration.waiting` is still set → the listener is called **again**.

For a reload prompt that means a banner could be raised twice, or a counter double-counted. It is latent today only
because nothing subscribes yet.

**Why the suite missed it:** the late-subscriber case
(`serviceWorkerUpdateWaitsForReload.test.ts:166-175`) asserts `toHaveBeenCalled()`, not
`toHaveBeenCalledTimes(1)` — and it subscribes *after* `registerServiceWorker()` has resolved, which is the one
ordering that cannot double-fire. The adjacent case for a *new* build does assert `toHaveBeenCalledTimes(1)`
(`:153-164`), which is exactly why that one path is safe and this one is not.

**Recommendation:** make the immediate notification the responsibility of one path only. Either drop `:42` and let
each subscriber discover an already-waiting worker through its own `.then()`, or keep `:42` and have
`onUpdateWaiting` notify only when registration has *already* settled. Then add a case that subscribes before
registration resolves and asserts exactly one call — the ordering that currently has no coverage.

## S2 · A failed registration is memoised forever, so there is no retry

**Severity: low** (deliberate, but undocumented and asserted only in the happy direction).

**Evidence:** `registerServiceWorker.ts:58` (`registering ??=`), `:65-67`.

The memo is assigned before the result is known, so a rejected registration is remembered as a resolved
`undefined`. Every later call returns that. In development, where no worker is emitted and the request 404s, that
is the right outcome. In production a transient failure (offline at first load) means the worker is never
registered again for the life of the page.

**Recommendation:** state the choice in the doc comment — one attempt per page load, no retry — or clear the memo
on failure so a later call can try again. Either is defensible; what is not defensible is leaving a reader to
infer it. If the memo is kept, add a case asserting a second call after a failure does **not** re-register, so the
behaviour is pinned rather than incidental.

## S3 · Subscribing has the side effect of registering

**Severity: low** (design smell; a boundary question, not a bug).

**Evidence:** `registerServiceWorker.ts:75`.

`onUpdateWaiting` calls `registerServiceWorker()`. A screen that only wants to *observe* update state therefore
causes registration as a side effect. It is convenient — a subscriber works without the caller having sequenced
the register call — but it means the module has two entry points that both register, and a test that imports
`onUpdateWaiting` alone will see a registration attempt.

**Recommendation:** keep it, but say so in the doc comment, or split observation from registration. Worth Kimi's
opinion in phase 8: the alternative couples every consumer to calling `registerServiceWorker()` first, which is
its own trap.

## S4 · `trackWaiting` can miss an update whose `installing` worker is not yet attached

**Severity: low** (defensive gap, not demonstrated).

**Evidence:** `registerServiceWorker.ts:44-50`.

The `updatefound` handler reads `registration.installing` and **returns silently** if it is null (`:46`). Per the
lifecycle it is set when `updatefound` fires, so this should not happen; but if it ever does, the update is never
reported and nothing records that it was dropped. The handler also only observes the worker captured at that
moment, so a second `updatefound` arriving before the first finishes installing is tracked independently, which is
correct but untested.

**Recommendation:** on the null branch, fall back to checking `registration.waiting` on the next tick rather than
returning. Add a case for two overlapping `updatefound` events.

## S5 · The forbidden-call bundle assertion depends on minification stripping comments

**Severity: low** (brittle coupling between prose and a build setting).

**Evidence:** `serviceWorker.ts:9-11` (the doc comment names `skipWaiting()` and `clients.claim()` to explain their
absence); `serviceWorkerUpdateWaitsForReload.test.ts:366-371` (the emitted worker must not contain those strings).

The source scan strips comment lines deliberately, so the explanation is allowed. The *bundle* scan does not, and
passes only because the production build minifies the worker and drops comments. `vite-plugin-pwa` exposes
`injectManifest.minify = false`; setting it would make the comment ship and fail AC1 for a reason that has nothing
to do with the policy.

**Recommendation:** either assert the property against code rather than text (hard, and probably not worth it), or
keep the text assertion and add a comment at the test saying what it depends on, so a future reader who flips
`minify` understands the failure. I lean to the latter — a loud failure is better than a silent one, as long as its
cause is documented.

## S6 · `main.tsx` is outside the forbidden-call source scan

**Severity: low** (coverage gap, closed by the bundle scan).

**Evidence:** `serviceWorkerUpdateWaitsForReload.test.ts:101-117` scans `src/pwa/serviceWorker.ts`,
`src/pwa/registerServiceWorker.ts` and `vite.config.ts`.

The entry is the one other file that touches the worker, and it is not in the list. A `skipWaiting` call added
there would be caught by the emitted-bundle case, but only after a full build, with a worse error message.

**Recommendation:** add `src/main.tsx` to the scanned set. Cheap, and it puts the fast assertion in front of the
slow one.

## S7 · Scope and worker URL are literals, not derived from Vite's `base`

**Severity: low** (the kind of drift this package otherwise guards against).

**Evidence:** `registerServiceWorker.ts:17`, `:20`; `vite.config.ts:11` (`base: '/app/'`), `:19`
(`filename: 'serviceWorker.ts'`).

`SERVICE_WORKER_SCOPE = '/app/'` and `SERVICE_WORKER_URL = '/app/serviceWorker.js'` restate what the build config
decides. T01 pins `base` to `/app/`, and this task's own case pins the URL by reading the built file, so a mismatch
*would* be caught — but by a build-dependent case, not by a direct one. Every other list in this directory is
derived from a single source or guarded against drift; these two are not.

**Recommendation:** add a case asserting `SERVICE_WORKER_SCOPE` equals the `base` in `vite.config.ts` and that
`SERVICE_WORKER_URL` is `base + filename` with `.ts` → `.js`. It cannot reasonably be *derived* at runtime without
naming an env key (R58 forbids it), so a guard is the right instrument — the same conclusion T09 reached about the
denylist.

## S8 · AC4's "no per-route or per-asset update behaviour" is only partly asserted

**Severity: low** (criterion weaker than it reads).

**Evidence:** `serviceWorkerUpdateWaitsForReload.test.ts:209-221` asserts exactly one registration site;
`vite.config.ts` is asserted not to contain `registerType`.

Together these cover the realistic ways a second policy could appear, but nothing asserts there is exactly **one**
worker entry, or that no other file under `src/pwa` declares a `__WB_MANIFEST`. The phrase "one worker-wide policy"
in `agents.md:91` is doing more work than the test does.

**Recommendation:** assert exactly one file in `src/**` references `__WB_MANIFEST`, and that the plugin config
names exactly one `filename`.

## What I checked and found sound

- **Null-safety.** `globalThis.navigator?.serviceWorker` (`:59`) handles jsdom and insecure origins; `register`
  rejection is caught (`:65`); `installing`/`waiting` nulls are handled (`:46`, `:48`). `registerServiceWorker`
  cannot reject, so `main.tsx`'s `void` call cannot break the mount — T01's own assertions still pass.
- **Module boundaries.** The worker imports every rule and states none: `NAVIGATION_DENYLIST` by identity,
  `SHELL_DOCUMENT_URL` from T11. Nothing in `src/app/` or `src/api/` learns about caching; `registerServiceWorker`
  is the only bridge from page to worker. The one addition to `apiRequestPolicy.ts` removed no export and left
  T11's 83 cases passing unchanged.
- **Idempotency.** `register` is called exactly once across concurrent and sequential calls — asserted at `:228`.
- **Secret handling.** Neither new file names a token, a storage API, an absolute URL or an env key; the precache
  holds only paths `isCacheableRequest` accepts, asserted over the real manifest. `agents.md:84-85` is satisfied
  structurally, by `NetworkOnly` being the default rather than by an enumeration.
- **Thread-safety.** The listener set is only touched on the main thread; the fan-out iterates a copy (`:37`), so a
  listener that unsubscribes during notification cannot corrupt the iteration.
- **The policy itself.** No `skipWaiting`, no `clients.claim`, no `postMessage`, no `location` write — asserted in
  source and in the shipped bundle. There is deliberately no function that promotes a waiting worker, which is the
  frozen brief's D-3 and the reason Kimi's `skipWaitingAndReload` was rejected.
- **Readability and complexity.** The worker is three statements; the registration module has one small private
  helper each for notification and tracking. No branching beyond the null guards.

## Still open, unchanged

- **OQ-1** (frozen brief): "waits for a user reload" is not achievable by a refresh and the mechanism that would
  make it so is forbidden. Code is identical under every reading; the prompt's honest wording is an owner decision.
- **AC7's first half is a guard, not a live catch** while `base` is `/app/` — recorded in phase 6; phase 10 owes a
  mutant on `base` to prove it bites.
- **The duplicated `segmentPrefixPattern`** was not collapsed. Permitted but not required by the frozen brief.
