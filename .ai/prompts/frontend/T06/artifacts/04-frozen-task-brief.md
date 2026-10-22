# frontend · T06 · Phase 4 — Frozen Task Brief (Human Approval gate)

STATUS: FROZEN (2026-10-22)

Consumes `02-task-implementation-brief.md` and `03-design-challenge.md` (Kimi 2.7: 10 findings; 2 Medium, 8 Low).
Each was checked against the brief, the T05 code and the phase 2 probes. Finding 2 is the one interpretation of a
locked decision. It was surfaced to the user in the phase 2 hand-back ("tell me if you'd rather tokens never leave the
tab that fetched them") and no objection was raised. It is recorded explicitly below, as Kimi recommends.

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| 1 | A late outcome after the grace period races the follower's own renewal | **ACCEPTED (clarified; there's no cancellation to add).** The follower checks for an outcome since its mark, then again after the grace wait, **immediately before** it calls `local.renew()`. That is the latest possible point. A message that lands *after* `local.renew()` has started can't stop it, because `LocalSession` has no cancellation and the leader that sent it is gone from the lock. The follower's own result still decides what its `renew()` returns. The late `renewed` message is adopted when it arrives, and the follower's own token, adopted when its renewal finishes, wins if it finishes later. Both are valid tokens from the same SAS session, so no tab is left with a bad token. It is documented as the residual case alongside Phase 2 OQ3. |
| 2 | The token crossing tabs needs an explicit L3 reading | **ACCEPTED (interpretation recorded).** L3 / `agents.md` forbid storage, logs, analytics and URLs. A same-origin BroadcastChannel message is none of these: it is transient, in-memory structured cloning between the SPA's own browsing contexts. L18 names BroadcastChannel as the sharing mechanism, and "wait on the one renewal" can't be met unless the outcome reaches the waiter. `spec/` is read-only for frontend tasks. This reading is in the Locked decisions below, and it is flagged to the user in the phase 13 summary for an ADR if they want one. |
| 3 | `close()` during an in-flight renewal | **ACCEPTED.** `close()` removes the listener and closes the channel. An in-flight `renew()` still settles for its local caller. Because posting on a closed channel throws `InvalidStateError`, every post is skipped once closed, so the local result isn't turned into an error. If the tab itself goes away, the browser releases the lock and siblings take the "leader vanished" path. |
| 4 | The double timeout | **REJECTED as proposed; the layering is documented.** T05's boundary timeout bounds what the boundary's *waiters* see, but it can't release a Web Lock held inside the coordinator. If the coordinator relied on it, a hung `local.renew()` would hold the lock forever and block every tab's later renewals. T06's deadline therefore bounds both the follower's lock wait (`signal`) and the leader's hold (racing `local.renew()`), so the lock is always released. Both default to `RENEWAL_TIMEOUT_MS`. Whichever fires first fails that call only, and both outcomes are failures, so they agree. |
| 5 | Several coordinators in one tab | **REJECTED (the guard); documented.** A module-level registry would be the module-level mutable state the Constraints forbid. Two coordinators in one tab behave like two tabs (one lock, one channel), so the cross-tab guarantee still holds for them. Production creates one per tab in the auth wiring. |
| 6 | No `BroadcastChannel` | **ACCEPTED.** Without `BroadcastChannel`, or without `navigator.locks`, and with none injected, `renew()` delegates to `local.renew()`. The T05 per-tab guarantee still holds. Cross-tab sharing needs both APIs. |
| 7 | Grace length in tests | **Already in the brief.** `graceMs` and `timeoutMs` are options; the default is `SIBLING_GRACE_MS = 1_000`. Tests use small values and fake timers. |
| 8 | Message-type collisions with task 39 | **ACCEPTED.** The types are namespaced as `renewal:succeeded` and `renewal:failed`, on the dedicated channel `RENEWAL_CHANNEL = 'themistra.session.renewal'`. Task 39 uses its own `signout:*` type or channel. |
| 9 | N > 2 tabs | **ACCEPTED.** Add a three-tab case: one leader, two followers, 1 local renewal, and both followers resolve with the leader's token. |
| 10 | `current()` updated before the follower settles | **ACCEPTED.** In the AC1/AC2 case, assert B's `current()` equals A's token as soon as the message lands, before B's `renew()` settles. |

Open Questions: none. The Phase 2 defaults stand, amended above:
- the token crosses tabs over BroadcastChannel;
- a 1 s grace;
- the residual redundant renewal is documented;
- without the APIs, local renewal.

---

## Frozen brief

### Task
T06 — **Shared renewal across tabs.** Sibling tabs wait on the one renewal instead of starting their own. Verifies
`siblingTabsShareOneRenewal`. Cites R21, L18.

### Purpose
Extend the T05 single flight across tabs. While any tab renews, a sibling that needs a renewal waits for that outcome
and uses it, and every tab ends up holding the fresh token.

### Scope
**In**
- `crossTabRenewal.ts`: `LocalSession`, `TabLocks`, the constants, and `shareRenewalAcrossTabs(local, options?)`,
  which returns `SessionTokens & { close(): void }`.
- `siblingTabsShareOneRenewal`.

**Out**
- The real `LocalSession` (tasks 14–23).
- Sign-out propagation (task 39).
- Re-auth UI (task 33).
- Any T05 change.

### Business rules
- **R21, the sibling-tab half.**

### Locked decisions
- **L18:** a Web Locks leader with a BroadcastChannel outcome.
- **L3, as interpreted (finding 2):**
  - a renewed token may cross the SPA's own same-origin tabs as a BroadcastChannel message;
  - nothing is written to storage, logs, analytics or a URL;
  - `renewal:failed` carries no reason.
- **L16 / §5:** no storage for coordination.
- **L12:** T05 untouched.
- **L6:** no global `fetch`.

### Dependencies
None new: the built-in `BroadcastChannel` and `navigator.locks`. Tests inject a fake lock manager (FIFO, `ifAvailable`,
`signal`). The Node 22 `BroadcastChannel` is real.

### Files to create
- **`frontend/src/api/crossTabRenewal.ts`:**
  - `export interface LocalSession extends SessionTokens { adopt(token: string): void }`;
  - `export interface TabLocks`, the `LockManager.request(name, options, callback)` subset, with options
    `{ ifAvailable?, signal? }`;
  - `export const RENEWAL_CHANNEL = 'themistra.session.renewal'`, `RENEWAL_LOCK = 'themistra.session.renewal'`,
    `SIBLING_GRACE_MS = 1_000`;
  - `export class SiblingRenewalFailedError extends Error`;
  - `export function shareRenewalAcrossTabs(local: LocalSession, options?: { channel?: BroadcastChannel; locks?:
    TabLocks; timeoutMs?: number; graceMs?: number }): SessionTokens & { close(): void }`.
- **`frontend/src/api/siblingTabsShareOneRenewal.test.ts`.**

### Files to modify
None.

### Files not to modify
- `frontend/src/api/sessionBoundary.ts`, `client.ts` and the T01–T05 tests.
- `frontend/package.json`.
- `libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`.
- `prompt`.

### Behaviour (normative)

**Construction.**
- The channel defaults to `new BroadcastChannel(RENEWAL_CHANNEL)`, and the locks to `navigator.locks`.
- If either is unavailable and none is injected, the instance is **local-only**:
  - `renew()` = `local.renew()`;
  - `current()` = `local.current()`;
  - `close()` is a no-op.

**`current()`** delegates to `local.current()`.

**`renew()`:**
1. Take `mark` = the outcome count, and set `deadline` = now + `timeoutMs` (default `RENEWAL_TIMEOUT_MS`).
2. **Leader attempt:** `locks.request(RENEWAL_LOCK, { ifAvailable: true }, cb)`. With a lock, **lead**:
   - race `local.renew()` against the deadline;
   - on success, post `{ type: 'renewal:succeeded', token }` and return the token;
   - on failure or timeout, post `{ type: 'renewal:failed' }` and rethrow.
3. **Follower:** if there is no lock, request it exclusively with a `signal` aborted at the deadline. When granted:
   - **use** an outcome that arrived after `mark`, or one that arrives within `graceMs`:
     `succeeded` returns its token, and `failed` throws `SiblingRenewalFailedError`;
   - **otherwise lead**: re-check right before calling `local.renew()`, per finding 1.
4. **An aborted follower wait** rejects with the timeout error.

**Messages.**
- **Valid** messages are `{ type: 'renewal:succeeded', token: <non-empty string> }` or `{ type: 'renewal:failed' }`;
  anything else is ignored.
- **`succeeded`** calls `local.adopt(token)` in every receiving tab, whether or not it is waiting.
- **Every valid message** increments the outcome count, stores it as the last outcome, and wakes the grace waiters.

**Posting** is skipped after `close()`.

**`close()`** removes the listener and closes the channel. An in-flight `renew()` still settles locally.

### Acceptance criteria
- **AC1:** while A leads, B's `renew()` never calls B's `local.renew` and resolves with A's token.
- **AC2:** B's `current()` equals A's token as soon as the message lands, before B's `renew()` settles. A T05 boundary
  on B re-sends with it.
- **AC3:** if A fails, B rejects with `SiblingRenewalFailedError`, with no local renewal, and B's boundary hook runs
  once.
- **AC4:** A and B in the same tick give exactly 1 local renewal. With three tabs (1 leader, 2 followers), also 1, and
  both followers get the leader's token.
- **AC5:**
  - if the leader vanishes with no outcome, the follower renews itself after `graceMs`;
  - a follower's wait past the deadline rejects;
  - a leader's hung `local.renew()` is released at the deadline, `renewal:failed` is posted, and the lock is freed for
    the next renewal.
- **AC6:**
  - T05's suite is still green;
  - two tabs, each with a T05 boundary and `createAuthApi`, both bursting 401s, give 1 local renewal in total, and
    every call is re-sent and gets 200.
- **AC7:**
  - no storage or cookie writes, and no console output with a token;
  - `renewal:failed` carries only `type`;
  - malformed or foreign messages are ignored (no adopt, no outcome);
  - after `close()`, an in-flight leader still resolves locally and nothing is posted.
- **AC8:** with no `BroadcastChannel` or no Web Locks, the instance delegates to local.

### Required tests: `siblingTabsShareOneRenewal`
Vitest. Each "tab" is a coordinator with its own fake `LocalSession`. All of them share one unique-per-test channel
name and one fake lock manager. Fake timers are used for grace and deadline.

Cases:
1. AC1/AC2;
2. AC3;
3. AC4, two tabs;
4. AC4, three tabs;
5. AC5, leader vanished;
6. AC5, follower deadline;
7. AC5, hung leader;
8. AC6, end to end;
9. AC7, malformed messages;
10. AC7, no leaks and a reasonless failure message;
11. AC7, `close()` mid-renewal;
12. AC8, fallbacks;
13. a second episode leads to exactly one more renewal.

Mutation breaks:
- the naive design (no `ifAvailable` or follower path);
- the grace wait removed;
- `adopt` not called;
- failure not broadcast;
- a reason added to `renewal:failed`;
- validation removed;
- the leader not bounded by the deadline;
- no post guard after `close()`.

### Constraints
- `strict` TypeScript.
- No new dependency.
- No storage, logging or URL for tokens.
- No module-level mutable state.
- T05 untouched.
- No network in tests. Channels are closed after each test, and each test uses a unique channel name.
