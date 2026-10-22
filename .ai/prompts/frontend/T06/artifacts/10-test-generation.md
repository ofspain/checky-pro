# frontend · T06 · Phase 10 — Test Manifest

Consumes `04-frozen-task-brief.md` and the resolved implementation (`09-review-resolution.md`). Date: 2026-10-22. All
runs were in `themistra-local-frontend-1`. No production code changed.

## File
`frontend/src/api/siblingTabsShareOneRenewal.test.ts`: Vitest (jsdom), `describe('siblingTabsShareOneRenewal')`, 15
cases.

**Harness:**
- **A tab:** `openTab(locks, …)` is one simulated tab. It is a real `shareRenewalAcrossTabs` with its own real Node 22
  `BroadcastChannel` instance and its own fake `LocalSession`. `renew` is a `vi.fn` that hands the test one `deferred`
  per attempt, and `adopt` is a `vi.fn`.
- **Shared by all tabs in a test:** one channel name, unique per test (`themistra.session.renewal.test-N`), and one
  `fakeLocks()` lock manager.
- **`fakeLocks()`:** Web Locks semantics for one origin. A single holder; a FIFO queue; `ifAvailable` gives
  `callback(null)` when held or queued; `signal` abort while queued withdraws the request and rejects with
  `signal.reason`; `isHeld()` for assertions.
- **`observeChannel()`:** a bystander on the channel that records posts and can post as a foreign sender.
- **`vanishingLeader()`:** holds the lock like a tab mid-renewal, then lets go with no outcome (a closed tab).
- **Timers:** real, with small grace and deadline values (25–200 ms). The file was run 8 times in a row, and the full
  suite 3 times in a row, with no failures.
- **`afterEach`:** every channel and coordinator is closed, mocks are restored and globals unstubbed.

## Mapping

| # | Case | Verifies | Key assertions |
|---|---|---|---|
| 1 | lets a sibling wait on the renewing tab and use its token, adopted before its own call settles | AC1, AC2 (P3-10) | Both tabs resolve `fresh`. B's `local.renew` is never called. The order of events is exactly `['b adopted', 'b settled']`. |
| 2 | hands a sibling the leader's failure, with no renewal of its own and no reason on the wire | AC3, AC7 | A rejects with its own error, and B with `SiblingRenewalFailedError`. B's `local.renew` is not called. The channel carried exactly `[{ type: 'renewal:failed' }]`. |
| 3 | runs one renewal when two tabs ask in the same tick | AC4 | 1 local renewal in total; both resolve `fresh`. |
| 4 | runs one renewal for three tabs, and both followers get the leader's token | AC4 (P3-9) | 1 local renewal. B and C resolve and hold `fresh`. |
| 5 | renews itself once the grace period passes when the renewing tab went away without an outcome | AC5 | No renewal while the lock is held. After the leader vanishes, B renews once and resolves its own token. |
| 6 | gives up at the deadline without renewing or posting, whether still queued or in its grace wait | AC5, **P8-1 regression** | **Queued** past the deadline: `RenewalTimeoutError`, no local renewal. **Granted** 50 ms into an 80 ms deadline with a 200 ms grace: `RenewalTimeoutError` in under 200 ms, no local renewal, and nothing posted. |
| 7 | releases the lock from a hung leader at its deadline and tells the siblings it failed | AC5 | The hung `renew` rejects `RenewalTimeoutError` and `[{ type: 'renewal:failed' }]` is posted. The lock is free, and the next tab's renewal proceeds and resolves. |
| 8 | renews once across two tabs whose session boundaries both see a burst of 401s | AC6, end to end | Two tabs, each with `createSessionBoundary(shared)` + `createAuthApi`, send 4 calls (`/accounts/me`, `/api-keys`, `/accounts/me/sessions`). The result is **1** local renewal, all 200, 4 sends with `Bearer old` then 4 with `Bearer fresh`, and no failure reports. |
| 9 | ignores malformed and foreign messages: no adoption, and no stand-in for a real outcome | AC7 | Seven junk messages are posted while B waits: `null`, a string, a missing token, an empty token, a numeric token, `signout:everywhere`, and a wrong key. B still renews itself after the leader vanishes, and `adopt` isn't called. A valid message afterwards *is* adopted. |
| 10 | writes nothing to storage or cookies and logs no token while sharing a renewal | AC7 (L3) | local/session storage are empty, `document.cookie` is unchanged, and no console call contains `secret-access-token`. |
| 11 | still settles an in-flight renewal locally after close(), and posts nothing | AC7 (P3-3) | It resolves `fresh` after `close()`, and the observer saw no message, so the post guard held. |
| 12 | falls back to the local renewal when Web Locks or BroadcastChannel is missing | AC8 (P3-6) | jsdom's `navigator.locks` is `undefined`, so the default instance is local-only. An injected channel without locks is **closed** (posting on it throws). Locks with `BroadcastChannel` stubbed `undefined` give local-only. Local `renew` ×3. |
| 13 | runs exactly one more renewal for a later episode, and a follower never reuses the earlier episode's outcome | later episodes | Two episodes, each with A leading and B following. B gets `fresh`, then `fresher`. A renews ×2, B ×0. |
| 14 | still gives a waiting sibling the token when its adopt throws, and surfaces that defect | **P8-3 regression** | B's `adopt` throws, yet B resolves `fresh` with no local renewal. The defect is captured through the wrapped `queueMicrotask`. |
| 15 | defaults the grace period to one second | frozen constant | `SIBLING_GRACE_MS === 1_000`. |

## Results
- The new file: **15/15** (about 1.2 s), 8 runs in a row green.
- `pnpm -C frontend test`: 6 files, **92/92** (77 plus 15), 3 runs in a row green.
- `typecheck` exit 0. `lint`: 0 errors over 13 files, 2 rules. `build` exit 0.

## Mutation breaks
Each was applied to a backup of `crossTabRenewal.ts`, run with `--testTimeout=3000`, then restored. `git status` shows
only the new test afterwards.

| Mutation | Caught by (case #) |
|---|---|
| Naive design: no `ifAvailable`, every tab leads in turn | 1, 2, 3, 4, 6, 8, 10, 13, 14 (9 cases) |
| Grace wait removed (timer 0) | 6 |
| `adopt` not called | 1, 4, 8, 9, 13, 14 |
| Failure not broadcast | 2, 7 |
| A reason added to `renewal:failed` | 2, 7 |
| Message validation removed | 9 |
| Leader not bounded by the deadline | 7 |
| No post guard after `close()` | 11 |
| Grace wait ignores the deadline (P8-1) | 6 |
| A follower leads after its deadline (P8-1) | 6 |
| `adopt` before counting the outcome (P8-3) | 14 |
| A follower reuses a stale outcome (ignores its mark) | 13 |

**12 of 12 caught.** The stale-outcome break first **survived**: case 13 originally ran the later episode through the
leader path only. The case was rewritten so a follower waits through both episodes, and the break now fails it.

**A coverage caveat, stated plainly:** the grace wait exists for cross-tab lock-before-message ordering. In this
environment Node's `BroadcastChannel` delivers the leader's message before the fake lock's next grant reaches the
follower's check, so cases 1–4 pass even with a zero grace. The "grace removed" break is caught by case 6, which needs
the grace to be cut short by the deadline, not by an induced delivery delay. A test that forces late delivery would
need a delaying channel wrapper. The `channel` option is typed `BroadcastChannel`, so that wrapper was left out rather
than casting. The ordering failure itself was demonstrated in the phase 2 probe, where the naive design gave 2 local
renewals in every scenario.
