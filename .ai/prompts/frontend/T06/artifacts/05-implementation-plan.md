# frontend · T06 · Phase 5 — Implementation Plan

Consumes `04-frozen-task-brief.md` (FROZEN 2026-10-22). No code is written in this phase.

## Files to create
| File | Traces to |
|---|---|
| `frontend/src/api/crossTabRenewal.ts` | Frozen brief, Files to create |
| `frontend/src/api/siblingTabsShareOneRenewal.test.ts` | Frozen brief, Required tests (written in phase 10, per precedent) |

## Files to modify
None.

## Public API
```ts
import { RENEWAL_TIMEOUT_MS, type SessionTokens } from './sessionBoundary';
export interface LocalSession extends SessionTokens { adopt(token: string): void }
export interface TabLockOptions { ifAvailable?: boolean; signal?: AbortSignal }
export interface TabLocks {
  request<T>(name: string, options: TabLockOptions, callback: (lock: unknown) => Promise<T>): Promise<T>;
}
export const RENEWAL_CHANNEL = 'themistra.session.renewal';
export const RENEWAL_LOCK = 'themistra.session.renewal';
export const SIBLING_GRACE_MS = 1_000;
export class SiblingRenewalFailedError extends Error {}
export interface SharedRenewal extends SessionTokens { close(): void }
export function shareRenewalAcrossTabs(local: LocalSession, options?: {
  channel?: BroadcastChannel; locks?: TabLocks; timeoutMs?: number; graceMs?: number;
}): SharedRenewal;
```
`navigator.locks` is assigned to `TabLocks` if TS 7's `lib.dom` `LockManager` is structurally compatible. If it isn't,
a thin adapter `{ request: (n, o, cb) => navigator.locks.request(n, o, cb) }` is used. There is no cast.

## Private helpers (closure)
- **State:** `outcomeCount`, `lastOutcome`, `waiters: Set<() => void>` and `closed`.
- `parseOutcome(data: unknown): Outcome | null`: accepts exactly the two shapes.
- `onMessage(event)`: parses the message, then:
  - drops anything invalid;
  - on `succeeded`, calls `local.adopt`;
  - increments the count, stores the outcome and wakes the waiters.
- `post(outcome)`: does nothing once `closed`.
- `startDeadline(ms)`: an `AbortController` plus a `setTimeout` that aborts with `RenewalTimeoutError`; returns
  `{ signal, clear }`.
- `untilAborted(promise, signal)`: races the promise against the signal's abort. It attaches a no-op catch so a late
  rejection isn't unhandled.
- `lead(signal)`: `untilAborted(Promise.resolve().then(() => local.renew()), signal)`. On success it posts
  `succeeded`; on failure it posts `failed` and rethrows.
- `outcomeSince(mark, graceMs)`: resolves with the last outcome if one arrived after `mark`. Otherwise it waits for the
  next outcome or `graceMs`, giving `Outcome | null`.
- `use(outcome)`: returns the token, or throws `SiblingRenewalFailedError`.
- `renew()`:
  1. `mark` and the deadline;
  2. the leader attempt with `ifAvailable`;
  3. otherwise the follower request with `signal`: `outcomeSince`, then `use` or `lead`;
  4. a rejection caused by the abort is mapped to `RenewalTimeoutError`;
  5. `finally` clears the deadline.
- **Local-only mode** when no channel or locks: `{ current, renew: () => local.renew(), close() {} }`. A channel is
  created only when locks exist, so nothing is opened needlessly.

## Tests (phase 10)
Cases 1–13 as frozen. Fixtures:
- `fakeLocks()` with Web Locks semantics: one holder; a FIFO queue; `ifAvailable` → `cb(null)` when held or queued;
  `signal` abort while queued → remove and reject with `signal.reason`;
- `fakeLocal(name)` with a deferred-driven `renew`;
- a unique channel name per test;
- the tabs are closed in `afterEach`.

## Execution order
1. `crossTabRenewal.ts`.
2. `typecheck` and `lint`.
3. A scratch probe of the frozen scenarios, deleted afterwards.
4. The phase 6 notes.
