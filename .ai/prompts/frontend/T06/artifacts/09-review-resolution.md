# frontend · T06 · Phase 9 — Review Resolution (Human Approval gate)

STATUS: RESOLVED (2026-10-22)

Consumes `07-self-review.md` (6 findings, all Low or Info) and `08-independent-review.md` (Kimi 2.7: 3 new findings
plus corroboration of the 6). Each claim was checked against `crossTabRenewal.ts` and, where behaviour was in
question, a scratch probe. None needs an owner trade-off, so they are settled here, as at the earlier gates.

## Resolution log

| Source | Finding | Decision | Change |
|---|---|---|---|
| P8-1 | The follower's grace wait ignores the deadline | **ACCEPTED, and it is worse than reported.** Reading the phase 6 code: a follower that gets the lock near its deadline waits the full grace period, then calls `lead()` with an already-aborted signal. `untilAborted` rejects at once, but `Promise.resolve().then(() => local.renew())` has **already started a real renewal** that nobody awaits, and `lead()` then **posts `renewal:failed` to the siblings**. So it was a late rejection, a wasted silent re-auth, and a misleading failure broadcast. | `outcomeSince(mark, signal)` (line 144) also finishes, with `null`, when the deadline aborts, and removes its listener and timer on every path. In the follower callback (lines 176–179), if no outcome arrived and the deadline has passed, it throws the deadline's `RenewalTimeoutError` **without** calling `lead()`: no local renewal and no post. |
| P8-3 | A throwing `local.adopt` drops the outcome | **ACCEPTED** | `onMessage` now counts the outcome, stores it and wakes the waiters **before** adopting (line 103). A throw from `adopt` is rethrown in a microtask, so it surfaces as uncaught and isn't swallowed, the same policy as T05's failure hook. Kimi's `console.error` option is rejected for the same reason as at T05: it would hide a defect. |
| P8-2 | `close()` doesn't clear the deadline timers, so an in-flight renewal may reject with a timeout | **REJECTED (by design)** | None. The frozen brief: "An in-flight `renew()` still **settles** locally." Settling includes the deadline's rejection. The deadline is what guarantees the lock is released (frozen finding 4), and `close()` must not remove that guarantee, or a hung leader in a closing tab could hold the lock. If `local.renew()` finishes within the deadline, the call resolves normally after `close()` (phase 6 probe: `CLOSE z`). |
| P7-1 / P8 corr. 1 | A late leader result isn't shared | **No code change; documented** | Kimi suggests a code comment. The existing doc comment on `shareRenewalAcrossTabs` covers the deadline. The behaviour is recorded here and in phase 12 risks: it is self-healing on the sibling's next 401. |
| P7-2 / P8 corr. 2 | A service worker could listen on `RENEWAL_CHANNEL` | **No T06 change; carried forward** | Tasks 9–12 (service worker) must not open `RENEWAL_CHANNEL`, with a guard test there. It is recorded in phase 12/13. |
| P7-3, P7-4, P7-5, P7-6 | The residual extra renewal; closing an injected channel; bfcache; density | **No change** | Confirmed acceptable by both reviews. |

## Verification after the changes
- **Scratch probe** (deleted after the run):
  - **Deadline case:** a vanished leader holds the lock for 90 ms; the follower has `timeoutMs` 100 and `graceMs` 50.
    The follower rejects `RenewalTimeoutError` after **101 ms**, with **0** local renewals and **nothing posted** on
    the channel (a third listener recorded `[]`). By the code path above, the phase 6 version would have waited until
    about 140 ms, started a local renewal and posted `renewal:failed`.
  - **Throwing `adopt`:** A leads to `fresh`. The sibling C, whose `adopt` throws, still resolves `fresh`, with **0**
    local renewals, and the `adopt` error surfaced through the microtask.
- `typecheck` exit 0. `lint`: 0 errors, 2 rules. `build` exit 0. `test` 77/77.
- `git status`: only `crossTabRenewal.ts` modified (and the user's `prompt`).

## For phase 10 (on top of the frozen cases)
- **The P8-1 regression case:** a follower granted near the deadline rejects at the deadline, with no local renewal and
  no post.
- **The P8-3 case:** a throwing `adopt` still hands the waiting follower the sibling's token, and its error surfaces.
