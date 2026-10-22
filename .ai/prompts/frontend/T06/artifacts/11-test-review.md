# frontend · T06 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T06 — Shared renewal across tabs |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **Shared renewal across tabs.** Sibling tabs wait on the one renewal instead of starting their own. Verifies `siblingTabsShareOneRenewal`. Cites R21, L18.

---

## Method

Reviewed the Phase 10 test manifest for `siblingTabsShareOneRenewal` (15 cases, 12 mutation breaks) against the frozen T06 acceptance criteria (AC1–AC8) and the issues raised in Phase 8. No code was rewritten.

The manifest already covers the two main Phase 8 findings: case 6 and the two "grace wait ignores the deadline" / "follower leads after its deadline" mutation breaks address P8-1, and case 14 plus the "adopt before counting the outcome" mutation break address P8-3.

---

## Gaps

### 1. The actual grace-before-lock path is not directly exercised
**Gap ·** The manifest notes that Node's `BroadcastChannel` delivers the leader's message before the fake lock manager grants the follower the lock, so cases 1–4 pass even if the grace wait is removed. The only thing exercising the grace timer is case 6, which relies on the deadline cutting the grace short. There is no test that artificially delays the outcome message until *after* the follower has acquired the lock, which is the real scenario the grace period exists for.  
**Why it matters ·** A bug where the follower ignores an outcome that arrives during the grace wait (as opposed to before it) would not be caught. The ordering failure was demonstrated in Phase 2 with a probe, but the named test suite does not reproduce it.  
**Suggested test ·** Inject a `BroadcastChannel` wrapper that delays post delivery by a configurable time. Hold the lock with a vanishing leader, let the follower acquire the lock, then deliver the leader's outcome mid-grace and assert the follower uses it without renewing. This may require widening the `channel` option type or casting in tests.

### 2. A leader whose `local.renew()` succeeds after the deadline is not covered
**Gap ·** Case 7 covers a hung leader whose renewal never settles, but there is no case where `local.renew()` resolves *after* the deadline fires. The Phase 8 self-review identified this as the "late token never shared" behavior.  
**Why it matters ·** The implementation posts `renewal:failed` at the deadline and does not post a later success. This is a deliberate design choice, but it should be asserted so a future refactor cannot accidentally post the late success or leak it.  
**Suggested test ·** Hold the lock, start a leader renewal, fire the deadline, then resolve the deferred renewal. Assert the channel carried exactly `[{ type: 'renewal:failed' }]` and that the leader's own promise rejected with `RenewalTimeoutError` (not the success token).

### 3. `close()` timer cleanup is not asserted
**Gap ·** Case 11 verifies that an in-flight renewal still resolves locally after `close()` and that nothing is posted. It does not verify the deadline timer is cleared. If the timer were left running, the test would still pass as long as the renewal resolves before the timeout.  
**Why it matters ·** The Phase 8 finding P8-2 noted that `close()` does not clear the per-renewal timer. A test that explicitly advances time past the deadline after `close()` would confirm whether the in-flight promise is wrongly aborted.  
**Suggested test ·** Start a leader renewal with a short deadline, call `close()` shortly before the deadline, advance timers past the deadline, then resolve the deferred renewal. Assert the promise resolves with the token (not `RenewalTimeoutError`) and no message was posted.

### 4. Privacy checks are only on the success path
**Gap ·** Case 10 checks local/session storage, cookies, and console output while sharing a successful renewal. The failure and timeout paths (cases 2 and 7) do not repeat these checks.  
**Why it matters ·** A failure is exactly when implementation might log the rejection reason or the token as part of diagnostics. The `RenewalTimeoutError` message includes the timeout value but should be checked to confirm it contains no token.  
**Suggested test ·** Extend cases 2 and 7 to assert the same storage/cookie/console invariants as case 10, and assert that any thrown/captured error message does not contain the token.

### 5. A throwing `adopt` on a non-waiting receiver is not covered
**Gap ·** Case 14 covers a waiting follower whose `adopt` throws. It does not cover a third tab (or a later tab) that receives the success message while not in the middle of `renew()` and whose `adopt` throws.  
**Why it matters ·** `onMessage` calls `local.adopt` before counting the outcome. A throwing adopt on a non-waiting tab would drop the outcome entirely, leaving that tab with the old token until its next 401.  
**Suggested test ·** Open tab A as leader, tab B as a waiting follower, and tab C as a passive listener with `adopt` throwing. Resolve A's renewal. Assert B still resolves with the token (proving the outcome was counted despite C's adopt failure) and that C's error is surfaced.

### 6. No test for `renew()` called after `close()`
**Gap ·** The behavior of calling `renew()` after `close()` is undefined in the brief and untested. The implementation will still attempt to acquire the lock and may hang or race with the closed channel.  
**Why it matters ·** Production code may call `close()` during a component unmount; any in-flight boundary promise is already held, but a subsequent call could be started by a late effect.  
**Suggested test ·** Optional: assert that `renew()` after `close()` rejects with a clear error or delegates to `local.renew()`, depending on the intended contract. Document whichever contract is chosen.

### 7. No test for `current()` delegation
**Gap ·** `current()` simply delegates to `local.current()`. There is no unit test for this.  
**Why it matters ·** A regression that forgot to delegate would break every screen that reads the token outside of a request.  
**Suggested test ·** Trivial one-liner: assert `shared.current()` returns `local.current()` after `local.adopt('x')`.

### 8. No test for custom `timeoutMs` or `graceMs` options
**Gap ·** The options are part of the public API but only the defaults are exercised (case 15 checks the default grace).  
**Why it matters ·** A typo in option wiring would only be visible if a caller overrides the defaults.  
**Suggested test ·** Add a case with `timeoutMs: 50` and `graceMs: 10` and assert the follower rejects at roughly 50 ms even when the lock is released at 40 ms (i.e., the custom deadline is honored). This also indirectly exercises the option plumbing.

### 9. No explicit test that the lock is held during post
**Gap ·** The implementation posts the outcome inside the lock callback. The tests assert the channel contents, but they do not assert that the lock is still held at post time. A regression that moved the post after the lock release could allow a follower to acquire the lock, see no outcome, and start its own renewal before the post lands.  
**Why it matters ·** This is the core ordering guarantee.  
**Suggested test ·** In a leader success case, assert `fakeLocks.isHeld(RENEWAL_LOCK)` is true at the moment the observer records the post. This requires the observer to inspect lock state synchronously on message receipt.

---

## Non-gaps (acknowledged, no change needed)

- **AC1–AC8 are all represented** in the 15 cases.
- **12 mutation breaks, all caught**, including the added breaks for P8-1 and P8-3.
- **End-to-end with T05 boundary** is covered by case 8.
- **Fallback to local-only** is covered by case 12.
- **`close()` mid-renewal** is covered by case 11.
- **Malformed/foreign messages** are covered by case 9.
- **Three-tab scenario** is covered by case 4.
- **Later episodes** are covered by case 13.
- **`adopt` throwing on a waiting follower** is covered by case 14.

---

## Summary

The T06 test manifest is strong: 15 cases, 12 mutation breaks, and direct regression coverage for the Phase 8 findings about deadline-aware grace and adopt-before-counting. The main remaining gaps are the true grace-before-lock ordering path (which requires a delaying channel wrapper), the late-leader-success-after-deadline case, explicit timer cleanup after `close()`, privacy invariants on failure, and a handful of edge cases around passive receivers, post-close calls, and lock-held-during-post. Adding these would make the suite a complete guard for the cross-tab protocol.
