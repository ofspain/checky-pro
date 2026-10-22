# frontend · T06 · Phase 6 — Implementation Notes

Consumes `04-frozen-task-brief.md` and `05-implementation-plan.md`. Date: 2026-10-22. All runs were in
`themistra-local-frontend-1`.

## What changed

| File | Content | Plan / AC |
|---|---|---|
| `frontend/src/api/crossTabRenewal.ts` (new, 186 lines) | See below. | AC1–AC8 |

`crossTabRenewal.ts` contains:
- `LocalSession`, `TabLockOptions` and `TabLocks`;
- `RENEWAL_CHANNEL`, `RENEWAL_LOCK` and `SIBLING_GRACE_MS = 1_000`;
- `SiblingRenewalFailedError`, a local `RenewalTimeoutError`, and `SharedRenewal`;
- `parseOutcome`, which accepts exactly `renewal:succeeded` with a non-empty string token, or `renewal:failed`;
- `shareRenewalAcrossTabs(local, { channel, locks, timeoutMs, graceMs })`:
  - **local-only** when no locks or no channel; a channel is created only when locks exist;
  - **otherwise** a leader attempt with `ifAvailable`, then a follower request with a deadline `signal`, then
    `outcomeSince(mark)` with the grace wait, then `use` or `lead`;
  - `lead` races `local.renew()` against the deadline and posts the outcome;
  - posting is guarded after `close()`;
  - `onMessage` validates, adopts on `succeeded`, counts and wakes the waiters.

No other file changed. `sessionBoundary.ts`, `client.ts` and the T01–T05 tests are untouched. No dependency was added.
The test file comes in phase 10.

**Type fit:** TS 7's `lib.dom` `navigator.locks` (`LockManager`) is assignable to `TabLocks` as is, so the plan's
fallback adapter was not needed and there is no cast.

## Verification
- **`typecheck`** exit 0. **`lint`:** 0 errors over 12 files, 2 rules. **`build`** exit 0.
- **`test`:** 77/77, unchanged.
- **A self-caught defect while writing the probe.** The first draft's `close()` woke the grace waiters. A woken waiter
  resolves with `lastOutcome`, which could be a **stale outcome from an earlier episode**, so a follower could have
  returned an old token. The wake was removed. A closed tab's waiters now run out their grace period, then lead
  locally, and posting after close is skipped.
- **Scratch probe** (`src/api/zzProbe.test.ts`, deleted after the run):
  - two or three coordinators on one channel name stand in for tabs;
  - a fake lock manager with Web Locks semantics (single holder, FIFO, `ifAvailable` → `cb(null)`, and `signal`
    abort while queued, rejecting with `signal.reason`);
  - `graceMs` 30 ms.

| Scenario | Observed |
|---|---|
| 3 tabs: A leads (20 ms), B and C ask 5 ms later | All three resolve `fresh`; **1** local renewal in total. B's `current()` was `fresh` by 22 ms (adopted from the message). |
| Two tabs in the same tick | Both `a`; **1** local renewal. |
| The leader fails; the sibling asks while it's pending | A rejects with its own `Error`, and B rejects with `SiblingRenewalFailedError`; **1** local renewal. |
| A hung leader (`renew` never settles), `timeoutMs` 100 | A rejects `RenewalTimeoutError` and B gets `SiblingRenewalFailedError` (A posted `renewal:failed` at its deadline). The lock is **free** afterwards, and the next `A.renew()` succeeds (`a2`), giving 2 local renewals across the two episodes. |
| `close()` right after `renew()` started | `renew()` still resolves `z` locally. There was no throw from posting on the closed channel. |
| No locks injected in jsdom (`navigator.locks` undefined) | Local-only: `renew()` returns `local-only`, and no channel is opened. |

## Deviations
None.

The cross-tab end-to-end case (two tabs, each with a T05 boundary, AC6) is covered in phase 10 rather than in the
scratch probe.
