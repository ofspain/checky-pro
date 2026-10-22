# frontend · T06 · Phase 2 — Task Implementation Brief

## Task
T06 — **Shared renewal across tabs.** Sibling tabs wait on the one renewal instead of starting their own. Verifies
`siblingTabsShareOneRenewal`. Cites R21, L18.

## Purpose
Extend T05's per-tab single flight to the whole browser. While any tab is renewing the session, a sibling tab that needs
a renewal waits for that tab's outcome and uses it. Every tab then holds the fresh token, and each still reports a
failure once.

## Scope
**In**
- `shareRenewalAcrossTabs(local, …)`: a `SessionTokens` that wraps the tab's local session and coordinates renewal
  across tabs. Web Locks gives mutual exclusion; BroadcastChannel carries the outcome.
- `LocalSession` (`SessionTokens` plus `adopt(token)`).
- `siblingTabsShareOneRenewal`.

**Out**
- The real silent re-auth and token holder (tasks 14–23; they implement `LocalSession`).
- Sign-out propagation (task 39; it will reuse the channel name space, with its own message type).
- Re-auth UI (task 33).
- Any change to T05's boundary behaviour.

## Business Rules
- **R21 (sibling half):** a sibling tab waits on the one renewal and doesn't start its own. On failure it gets the
  failure, so its boundary reports once, with no loop.

## Locked Decisions
- **L18:** "Tabs share renewal … through BroadcastChannel or a Web Locks leader." This design is a Web Locks leader
  with a BroadcastChannel for the outcome.
- **L3:**
  - The token is in memory only. It travels to sibling tabs only as a same-origin BroadcastChannel message, which is
    in-memory messaging between the app's own tabs, not storage, a log or a URL.
  - "Wait on the one renewal" can't be met without the outcome reaching the waiter (Phase 1).
- **L16 / §5:** no storage of any kind for coordination.
- **L12:** T05's `createSessionBoundary` and its test are unchanged. T06 plugs in through the `SessionTokens` seam.
- **L6:** no global `fetch`.

## Dependencies
- None new: the built-in `BroadcastChannel` and `navigator.locks` (`LockManager`).
- The test environment has Node 22's `BroadcastChannel`, and no `navigator.locks` (probed). Tests inject a fake lock
  manager with Web Locks semantics: FIFO exclusive grant, `ifAvailable`, and `signal` abort while queued.

**Probed 2026-10-22** (scratch Vitest files, deleted afterwards; two coordinator instances on one channel name and one
fake lock manager stand in for two tabs):
- **Naive design** (take the lock, then check whether an outcome message arrived): **it fails.** In every scenario the
  lock reached the sibling before the leader's message did, so the sibling renewed itself.
  - Success case: 2 local renewals, and tab B ended up with its own token.
  - Simultaneous starts: 2 renewals.
  - Failure case: B renewed after A failed.

  Cross-tab lock grant and message delivery are not ordered, in browsers either.
- **Leader/follower with a grace wait** (the design below):
  - success: **1** renewal, and both tabs get `fresh`;
  - simultaneous starts: **1**;
  - failure: **1**, and the sibling rejects with the leader's failure;
  - leader gone without a message: the sibling waits out the grace period, then renews itself (1 renewal).

## Inputs
- `local: LocalSession`, with `current()`, `renew()` (silent re-auth) and `adopt(token)`.
- Optional injection: `{ channel, locks, timeoutMs, graceMs }`.

## Outputs
- A `SessionTokens` for `createSessionBoundary`. Its `renew()` resolves with the token, whether this tab renewed it or a
  sibling did, or rejects.
- `close()`, which detaches from the channel.
- Channel messages:
  - `{ type: 'renewed', token }`;
  - `{ type: 'renewal-failed' }`, which carries no reason, so nothing sensitive crosses tabs.

## State Changes
In memory, per instance:
- an outcome counter;
- the last outcome;
- the listeners waiting for the next outcome.

A received `renewed` message calls `local.adopt(token)` in every sibling, which keeps tabs coherent even when they
weren't waiting. Nothing is persisted.

## Behaviour (normative)
`renew()`:
1. **Mark** the current outcome count, and set a deadline of `timeoutMs` from now (default `RENEWAL_TIMEOUT_MS`).
2. **Leader attempt:** `locks.request(RENEWAL_LOCK, { ifAvailable: true }, cb)`. If granted, this tab is the
   **leader**:
   - run `local.renew()` against the deadline;
   - on success, post `renewed` and return the token;
   - on failure or timeout, post `renewal-failed` and rethrow;
   - the lock is released on return, and the browser also releases it if the tab dies.
3. **Follower:** if the lock is held, request it exclusively with `signal`, which aborts at the deadline. When granted:
   - if an outcome arrived after the mark, or arrives within `graceMs` (default `SIBLING_GRACE_MS = 1_000`), use it:
     `renewed` returns its token, and `renewal-failed` rejects with `SiblingRenewalFailedError`;
   - otherwise the previous holder vanished without an outcome, so become the leader inside this lock (step 2's body).
4. **Deadline:** if the follower's queued request is aborted, reject with the timeout error. The T05 boundary also
   bounds the call at 30 s.
5. **Received messages:** an invalid shape (wrong type, non-string or empty token) is ignored. `renewed` calls
   `local.adopt`. Every valid message counts as an outcome and wakes the waiters.
6. **No Web Locks** (`navigator.locks` undefined and none injected): delegate straight to `local.renew()`. The T05
   per-tab guarantee still holds, and the posted outcome still lets siblings adopt.

## Files to Create
- **`frontend/src/api/crossTabRenewal.ts`:**
  - `export interface LocalSession extends SessionTokens { adopt(token: string): void }`;
  - `export interface TabLocks { request<T>(name, options, callback): Promise<T> }` (the `LockManager` subset);
  - `RENEWAL_CHANNEL`, `RENEWAL_LOCK` and `SIBLING_GRACE_MS`;
  - `export function shareRenewalAcrossTabs(local, options?): SessionTokens & { close(): void }`.
- **`frontend/src/api/siblingTabsShareOneRenewal.test.ts`.**

## Files to Modify
None. `sessionBoundary.ts` is imported only for `SessionTokens` and `RENEWAL_TIMEOUT_MS`.

## Files NOT to Modify
- `frontend/src/api/sessionBoundary.ts`, `client.ts` and the T01–T05 tests.
- `libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`, `frontend/package.json`.
- `prompt`.

## Acceptance Criteria
- **AC1:** A is renewing and B calls `renew()`. B's `local.renew` is never called, and B resolves with A's token.
- **AC2:** after A succeeds, B's `current()` is A's token, through `adopt`. A boundary on B re-sends its 401'd
  requests with that token.
- **AC3:** when A fails, B rejects with `SiblingRenewalFailedError` and makes no local renewal. B's boundary hook runs
  once.
- **AC4:** A and B call `renew()` in the same tick, and exactly one local renewal runs.
- **AC5:** the leader vanishes with no outcome. B renews itself after `graceMs`, so it never hangs. A follower's wait
  past the deadline rejects.
- **AC6:** `parallel401sTriggerExactlyOneRenewal` is still green, and across two tabs, each with a T05 boundary, a
  401 burst in both gives 1 local renewal in total.
- **AC7:**
  - no `localStorage`, `sessionStorage` or `document.cookie` writes, and no console output containing a token;
  - `renewal-failed` carries no reason;
  - foreign or malformed messages are ignored, and never adopted.

## Required Tests
`siblingTabsShareOneRenewal` (Vitest). Two coordinators on one unique-per-test channel name and one fake lock manager
stand in for two tabs. Timing uses fake timers where grace or deadline matters.

Cases:
1. AC1/AC2;
2. AC3;
3. AC4;
4. AC5, both the grace takeover and the deadline;
5. an end-to-end case: two tabs, each with `createSessionBoundary` and `createAuthApi`, both bursting 401s, give 1
   renewal and all re-sent 200;
6. AC7 malformed messages;
7. AC7 no storage or console leak;
8. a second episode after the first settles leads to exactly one more renewal;
9. without locks, it delegates to local.

Mutation breaks:
- the naive design (no `ifAvailable`; check after the lock only);
- the grace wait removed;
- `adopt` not called;
- failure not broadcast;
- a reason included in `renewal-failed`;
- the message validation removed;
- the deadline removed.

## Constraints
- `strict` TypeScript.
- No new dependency.
- No storage, logging or URL for tokens.
- No module-level mutable state: one coordinator per tab, created by production wiring with the auth tasks.
- The T05 code is untouched.
- No network in tests. Channels are closed after each test.

## Open Questions
No blockers. Defaults for Phase 3/4:
1. **The token crosses tabs over BroadcastChannel.** This follows from L18 and is consistent with L3 (Phase 1). The
   alternative, each tab renewing serially behind the lock, would contradict "instead of starting their own".
2. **`SIBLING_GRACE_MS = 1_000`.** Its cost applies only when a leader dies mid-renewal, and it covers the
   lock-before-message ordering gap the probe exposed.
3. **A residual window:** a tab whose need arises just after the leader released, but before the message lands, may
   renew once more. That is a redundant but correct renewal of a separate, later need. It is documented, not prevented,
   because preventing it would need shared storage, which is forbidden.
4. **Without Web Locks:** fall back to local renewal.
