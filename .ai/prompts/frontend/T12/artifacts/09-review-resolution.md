# frontend · T12 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Gate** | Human Approval |
| **Consumes** | `artifacts/08-independent-review.md`, `artifacts/07-self-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Nine findings: my eight, all corroborated by Kimi, plus one fresh from Kimi. **Eight applied, one accepted as a
fact with its recommendation rejected.** Suite **370/372**, i.e. 56 cases in this task's file
(47 + 9 new), `src/pwa` **223/223**, tsc and oxlint clean, the same two environmental T07 failures and nothing else.

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| **S1** | early subscriber notified twice | **Fixed** — one source of the fact |
| **S2** | failed registration memoised forever | **Fixed** — documented and pinned |
| **S3** | subscribing registers as a side effect | **Fixed** — removed the side effect |
| **S4** | `updatefound` with null `installing` drops the update | **Fixed** — microtask fallback, two cases |
| **S5** | bundle assertion depends on minification | **Fixed** — documented at the assertion |
| **S6** | `main.tsx` outside the fast source scan | **Fixed** — added |
| **S7** | scope/URL literals restate the build config | **Fixed** — guard case |
| **S8** | AC4 asserted less than it reads | **Fixed** — two more assertions |
| **K1** | `/app/` scope leaves `/app` uncontrolled | **Accepted as fact; recommendation (a) rejected** |

## S1 — the one real bug, and what replaced it

Two paths reported the same fact, so they intersected for a subscriber that arrived before registration settled.
The fix is not to guard one path but to remove the duplication: a module-level `updateIsWaiting` is now **the**
record that a build is waiting, `notifyUpdateWaiting()` returns early once it is set, and `onUpdateWaiting`
reports from that flag synchronously instead of racing a promise.

Three orderings now have cases, where before only the safe one did: subscribe after registration
(`:166-176`), **subscribe before registration settles** (`:178-193`, the ordering that used to double-fire), and
two lifecycle events announcing one build (`:195-207`). All three assert `toHaveBeenCalledTimes(1)`.

## S3 — removed rather than documented

Both reviews offered "document it or split it". Splitting turned out to be free once S1 was fixed: with the fact in
a flag rather than behind a promise, `onUpdateWaiting` has no reason to call `registerServiceWorker()` at all.
Observation is now pure, `main.tsx` remains the single registration site, and the surprising side effect is simply
gone. A case asserts that subscribing registers nothing (`:223-231`).

## S2 and S4 — decisions made explicit

**S2.** The memo is kept: one attempt per page load, nothing retries behind the user's back. That is now stated in
the module header and pinned by a case asserting a second call after a failure does not re-register (`:347-357`).

**S4.** The null-`installing` branch no longer returns silently; it re-checks `registration.waiting` on a
microtask. Two cases cover it — the fallback itself, and that two overlapping `updatefound` events still produce
one notification, which the S1 flag makes true for free.

## K1 — Kimi's new finding: right about the fact, wrong about the fix

**The fact is real.** Service-worker scope matching is a prefix match over the whole URL string, so a client at
exactly `/app` is **not** within scope `/app/`.

**Recommendation (a) — drop the trailing slash — is rejected, because it widens scope.** A scope of `/app` would
claim `/apple.html` as well: prefix matching is not segment matching. That is strictly more than the SPA's own
prefix, which **AC5 and L14 forbid**, and it is the exact trap `segmentPrefixPattern` exists to avoid in T09 and
T11. Kimi called (a) "safer if the plugin supports it"; the plugin would support it, and it would be less safe.

**And the scenario does not arise.** Checked against the running stack rather than argued:

```
GET http://localhost:5173/app   → 404
GET http://localhost:5173/app/  → 200
GET http://localhost:5173/app/sign-in → 200
```

`/app` is not a URL the edge serves, so there is no uncontrolled shell load to worry about; every URL that does
serve the shell is under `/app/`.

**Recommendation (b) applied**: the trade-off is documented at the constant, and a case pins all four of its
consequences — the slash is kept, `/apple.html` is out of scope, `/app` is out of scope (the accepted cost, stated
rather than hidden), and `/app/sign-in` is in.

**One thing to pass on, outside this task.** `apiRequestPolicy.ts:46` lists `/app` in `SHELL_DOCUMENT_PATHS`
(T11), and `/app` 404s in the running stack. Harmless — nothing requests it, so nothing can be cached from it — and
it may well be served in a production static host where T11's inclusion is right. It is T11's constant and
`design.md`'s route list, not T12's, so it is recorded here rather than changed.

## S5, S6, S7, S8 — assertions and notes

- **S5.** A comment above the emitted-bundle case names its dependency: the production build minifies the worker,
  which is why the doc comment that *names* `skipWaiting` to explain its absence does not ship. A loud failure is
  the right outcome if someone sets `injectManifest.minify = false`; now its cause is findable.
- **S6.** `src/main.tsx` joins the fast source scan, so a misplaced call fails before the slow build case.
- **S7.** A case parses `base` and `filename` out of `vite.config.ts` and asserts `SERVICE_WORKER_SCOPE === base`
  and `SERVICE_WORKER_URL === base + filename` with `.ts → .js`. These two literals cannot be *derived* at runtime
  without naming an env key (R58), so a guard is the right instrument — the same conclusion T09 reached for the
  denylist.
- **S8.** Exactly one file in `src/**` may declare `__WB_MANIFEST`, and `vite.config.ts` may state exactly one
  `filename`. With the existing single-registration-site case, "one worker-wide policy" is now asserted rather
  than merely written.

## Carried into phase 10

1. **Prove the S1 fix.** The regression case must be shown to fail against the old two-path code, not just to pass
   against the new. A mutant restoring the promise-path notification is the check.
2. **Prove AC7's first half bites** by mutating `base`, since it cannot fail today while `base` is `/app/`
   (phase 6's recorded limit).
3. **Commit before mutating** — the harness revert discards uncommitted work (T09's lesson).
4. **Mutate guarded sources, not guards** — a weakened assertion can never be caught (T11's lesson). That applies
   to the two drift guards and to the new S7 guard, whose guarded source is `vite.config.ts`.

## Still open, unchanged

**OQ-1** (frozen brief): `tasks.md:21`'s "waits for a user reload" is not achievable by a refresh, and the only
mechanism that would make it so is forbidden by `agents.md:91`. The code is identical under every reading. What a
future prompt may honestly promise the user is an owner decision, needed before the prompt UI is built.

The duplicated `segmentPrefixPattern` is still not collapsed — permitted but not required by the frozen brief.
