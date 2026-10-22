# frontend · T06 · Phase 12 — Specification Verification

Consumes `04-frozen-task-brief.md`, `09-review-resolution.md`, `10-test-generation.md` and `11-test-review.md` (Kimi
2.7: 9 gaps). Date: 2026-10-22. All runs were in `themistra-local-frontend-1`. Only the test file changed in this
phase; `crossTabRenewal.ts` is as resolved at phase 9.

## Phase 11 gaps (Kimi), resolved before verification

Line numbers refer to `siblingTabsShareOneRenewal.test.ts`.

| Gap | Decision | Change | Break caught |
|---|---|---|---|
| 1 The grace-before-lock path isn't exercised | **ACCEPTED, with no cast** | `ObservedChannel extends BroadcastChannel` is a real channel subclass, so it fits the `channel` option's `BroadcastChannel` type and needs no widening or cast. It can hold back delivery. `:532`: A's outcome reaches B 30 ms after A releases the lock, inside B's 200 ms grace. B resolves `fresh` without renewing. This closes the phase 10 coverage caveat. | **`no-grace`** now also fails this case (before, only case 6 caught it) |
| 2 A leader succeeding after its deadline | **ACCEPTED** | `:567`: A rejects `RenewalTimeoutError`. Its `local.renew` then resolves `too-late`, and the channel still carries exactly `[{ type: 'renewal:failed' }]`. B never adopts. | `failure-not-broadcast`, `reason-on-wire`, `leader-unbounded` |
| 3 `close()` should clear the deadline, so an in-flight renewal resolves after it | **REJECTED, as at phase 9 (P8-2); the opposite contract is pinned** | The deadline is what guarantees the lock is released (frozen finding 4), and `close()` must not remove it. `:584` asserts the contract: `close()` lands well inside a 150 ms deadline with nothing posted yet. The pending renewal then rejects `RenewalTimeoutError`, the lock is free, and nothing is posted. Case 11 (`:405`) still covers a renewal that finishes inside the deadline after `close()` and resolves. | `leader-unbounded` |
| 4 Privacy only on the success path | **ACCEPTED** | A `watchForTokenLeaks` helper (storage, cookies, all console levels, and the surfaced error's text) now also runs in the failure case (`:196`) and the hung-leader case (`:293`). | — |
| 5 A throwing `adopt` on a tab that isn't waiting | **ACCEPTED (premise outdated)** | Kimi's reason, "adopt runs before counting", was fixed at phase 9 (P8-3). The case is still worth having. `:498`: an idle tab whose `adopt` throws receives two episodes. `adopt` is called both times, so the listener survives, both defects surface uncaught, and the idle tab never renews. | `adopt-before-count`, **new `adopt-throw-detaches`** |
| 6 `renew()` after `close()` | **ACCEPTED; contract chosen** | Contract: after `close()` the instance acts as a tab on its own. It still takes the lock, so it never renews concurrently with a sibling, renews locally and posts nothing. `:603` pins it. | `no-post-guard` |
| 7 `current()` delegation | **ACCEPTED** | `:618`: `current()` gives `old`, then `fresh` after a renewal. | **new `current-broken`** (also fails case 8) |
| 8 Custom `timeoutMs` / `graceMs` honored | **Already covered** | Cases 6, 7 and the new cases at `:532`, `:567`, `:584` run with non-default values, and each fails if the option isn't wired. Case 6, for example, needs an 80 ms deadline to cut a 200 ms grace short. No new case. | `grace-ignores-deadline`, `lead-after-deadline` |
| 9 The lock is held at post time | **ACCEPTED** | `ObservedChannel` reports each post synchronously as it is made. `:549` records `locks.isHeld()` there for a success and a failure: `[true, true]`. | **new `post-after-release`** |

**A test defect found while adding gap 3, and fixed.** The first draft used a 40 ms deadline and failed: a
`renewal:failed` was posted. A scratch probe (deleted after the run) showed the post guard holds. The cause was the
test: `vi.waitFor` polls every 50 ms, so the deadline fired **before** the test called `close()`. The case now uses
150 ms, `waitFor` with `interval: 1`, and asserts that nothing had been posted at the moment `close()` is called.

## Mutation breaks (15)

Each break was applied to a backup of `crossTabRenewal.ts`, run with `--testTimeout=3000`, then restored by copying
the backup back. `git status` afterwards shows only the test file.

| Mutation | Cases failed |
|---|---|
| naive design (no `ifAvailable`) | 10 |
| grace wait removed | 2 (case 6, delayed grace `:532`) |
| `adopt` not called | 7 |
| failure not broadcast | 4 |
| reason on the wire | 3 |
| validation removed | 1 |
| leader not bounded by the deadline | 3 |
| no post guard after `close()` | 2 |
| grace ignores the deadline (P8-1) | 1 |
| follower leads after its deadline (P8-1) | 1 |
| adopt before counting (P8-3) | 2 |
| stale outcome (ignores mark) | 1 |
| **new:** post after the lock is released | 1 (`:549`) |
| **new:** `current()` broken | 2 |
| **new:** a throwing `adopt` detaches the listener | 1 (`:498`) |

**15 of 15 caught.**

## Final suite
- `siblingTabsShareOneRenewal`: **22/22**, 10 runs in a row green.
- `pnpm -C frontend test`: 6 files, **99/99**, 3 runs in a row green.
- `typecheck` exit 0. `lint`: 0 errors, 2 rules. `build` exit 0.
- No production code changed in phases 10–12.

## Traceability matrix

Code lines refer to `crossTabRenewal.ts`; test lines to `siblingTabsShareOneRenewal.test.ts`.

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| tasks.md 6: sibling tabs wait on the one renewal instead of starting their own | Yes | the leader attempt with `ifAvailable` (`:171`); the follower path with `outcomeSince` | `:174`, `:219`, `:232`, `:532` | No | None |
| R21, sibling half: one renewal shared across tabs | Yes | as above; posting the outcome inside the lock (`:135`, `:138`) | `:219`, `:232`, `:313` (end to end), `:443` | No | Residual extra renewal when a message is in transit (frozen finding 1; P7-3) |
| R21: on failure, no loop | Yes | `renewal:failed` → `SiblingRenewalFailedError`, no local renewal (`:161`) | `:196`, `:293` | No | None |
| L18: a Web Locks leader with a BroadcastChannel outcome | Yes | `RENEWAL_LOCK` / `RENEWAL_CHANNEL` (`:18–19`); `navigator.locks` default (`:78`) | `:174`, `:549` | No | None |
| L3 as interpreted: a token may cross same-origin tabs, never stored, logged or in a URL; failures carry no reason | Yes | `post` only on the dedicated channel (`:114`); `renewal:failed` has only `type` | `:196`, `:293`, `:385`, `:567` | No | **The interpretation itself** (frozen finding 2), flagged for an ADR in phase 13 |
| L16 / §5: no storage for coordination | Yes | coordination is the lock plus the channel only | `:385`, `:196`, `:293` | No | None |
| AC5: deadlines bound the follower's wait and the leader's hold | Yes | `renew` deadline controller (`:166`); `untilAborted` (`:119`); `outcomeSince` stops on abort (`:144`) | `:263`, `:293`, `:567`, `:584` | No | None |
| AC7: malformed or foreign messages ignored | Yes | `parseOutcome` (`:50`) | `:355` | No | None |
| AC7: `close()` | Yes | post guard (`:114`); `close()` (`:194`) | `:405`, `:584`, `:603` | No | The post-`close()` `renew()` contract was chosen in this phase (gap 6) |
| AC8: local-only without the APIs | Yes | `:78–85` | `:420` | No | None |
| `adopt` defects surface without losing the outcome | Yes | count, store and wake before `adopt`; rethrow in a microtask (`:95` onward) | `:466`, `:498` | No | None |
| L12: T05 untouched; no global `fetch`; no module-level mutable state | Yes | the diff is `crossTabRenewal.ts` and the test | `lint` gate; `:313` uses T05 unchanged | No | None |
| package.md §8 named test | Yes | `frontend/src/api/siblingTabsShareOneRenewal.test.ts` | itself | No | None |

## Principal-engineer review

1. **Is the task fully complete?** Yes. While any tab renews, siblings wait on that renewal and use its outcome, and
   every listening tab adopts the fresh token. Each wait and each lock hold is bounded by the deadline.
2. **Does it satisfy every acceptance criterion?** Yes, AC1–AC8, each with a direct test. The grace wait, the reason
   the design works across real tabs, is now exercised directly (`:532`) instead of only argued.
3. **Does it violate any LOCKED decision?** No. L18 is followed literally. L3 is followed **as interpreted** in the
   frozen brief: a token crosses the SPA's own tabs in memory. It is surfaced again in phase 13 for an ADR.
4. **Remaining risks:**
   - **A late leader result** (after its deadline) isn't shared. It heals itself on the sibling's next 401 (P7-1;
     pinned at `:567`).
   - **The service worker** (tasks 9–12) must never open `RENEWAL_CHANNEL`, with a guard test there (P7-2).
   - **A residual extra renewal** when a message is in transit as a new tab arrives. It is correct, just redundant
     (P7-3).
   - **Wiring:** production creates one coordinator per tab, and the real `LocalSession` comes with tasks 14–23.
   - **jsdom has no Web Locks:** the cross-tab path is tested against a fake lock manager with Web Locks semantics.
     A real-browser check belongs with the E2E tasks.

**PASS:** sibling tabs share one renewal. The named test covers AC1–AC8 and catches 15 of 15 mutation breaks.
