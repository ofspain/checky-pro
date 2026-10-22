# frontend · T06 · Phase 7 — Self-Review

Reviews the phase 6 diff (`3e87ce5d`), `frontend/src/api/crossTabRenewal.ts`, against the frozen brief and
`spec/frontend/agents.md`. Findings only; fixes are applied in phase 9. Line numbers refer to `crossTabRenewal.ts`.

## Findings

### 1. A leader whose renewal finishes after its deadline never shares the token
- **Issue:** at the deadline, `lead()` posts `renewal:failed` (`:129`). The underlying `local.renew()` keeps running,
  because there is no cancellation. If it later succeeds, the auth module installs the token in this tab only, and no
  `renewal:succeeded` is posted. Siblings stay on the old token until their next 401, when they renew again.
- **Severity:** Low. It is self-healing: the sibling's next 401 runs one renewal.
- **Evidence:** `:110-117` (`untilAborted` drops a late result), `:126` (posts only on the raced path).
- **Recommendation:** no change. Sharing a late result would contradict the `renewal:failed` already posted for that
  episode. A late token is a fresh, separate fact that each tab picks up on its next renewal. Document it.

### 2. The service worker could receive the token if it ever listens on this channel
- **Issue:** a BroadcastChannel delivers to every same-origin context, including a service worker. The PWA worker
  (tasks 9–12) is in scope later. If it opened `RENEWAL_CHANNEL`, it would receive tokens.
- **Severity:** Low (latent).
- **Evidence:** `:81` (the channel name is exported as `RENEWAL_CHANNEL`).
- **Recommendation:** note it for the service-worker tasks: the worker must never open `RENEWAL_CHANNEL`. A guard test
  there can assert the worker source doesn't reference it. No T06 change.

### 3. A residual extra renewal between the leader attempt and a message in transit
- **Issue:** `mark` is taken at `:156`. If a sibling's outcome is in transit, and this tab's `ifAvailable` attempt
  finds the lock free (`:160`) because the sibling has just released it, this tab leads and renews once more. This is
  the documented residual case (Phase 2 OQ3; frozen finding 1).
- **Severity:** Info.
- **Evidence:** `:160-163`.
- **Recommendation:** none. Preventing it would need shared storage (forbidden). The extra renewal is correct.

### 4. Closing an injected channel
- **Issue:** `close()` closes the channel even when the caller injected it (`:183`). This is intended: the coordinator
  owns the channel it uses. A caller that shares one channel object across concerns would lose it.
- **Severity:** Info.
- **Evidence:** `:183`.
- **Recommendation:** none. Each coordinator gets its own channel instance, and the default constructs one. Task 39
  (sign-out) uses its own channel or instance.

### 5. A back/forward-cached tab
- **Issue:** a page in the bfcache can't run script. Browsers generally don't keep pages that hold a Web Lock (Chrome
  evicts them). A cached page with an open BroadcastChannel misses messages and, when restored, keeps its old token
  until its next 401.
- **Severity:** Info.
- **Evidence:** browser behaviour. There is no code path for it.
- **Recommendation:** none. The restored tab's next 401 renews or follows normally.

### 6. Readability of `renew()`
- **Issue:** the leader and follower paths sit in one function, with the deadline mapping in `catch`. It is compact but
  dense.
- **Severity:** Info.
- **Evidence:** `:155-175`.
- **Recommendation:** none. The doc comment on `shareRenewalAcrossTabs` describes the protocol in prose, and the inline
  comment at `:166` explains the late check.

## Checklist (no finding)
- **Correctness:**
  - the leader posts before returning, so the lock is released only after the post;
  - a follower uses only outcomes newer than its mark;
  - `close()` no longer wakes waiters with a stale outcome (fixed during phase 6, before commit);
  - a deadline abort during the queued wait maps to `RenewalTimeoutError`, while a sibling's failure stays
    `SiblingRenewalFailedError`;
  - a synchronous throw from `local.renew()` becomes a rejection.
- **Boundary conditions:**
  - messages without an object shape, of an unknown type, or with an empty or non-string token are dropped and don't
    count;
  - with no locks or no channel the instance is local-only, and no channel is left open.
- **Concurrency:** a single thread per tab. Cross-tab ordering is handled by the lock plus the grace wait, which is the
  case the phase 2 probe showed failing without it.
- **Timers:** every `setTimeout` is cleared on its success path. The grace timer deletes its waiter.
- **Module boundaries:**
  - no module-level mutable state;
  - T05 untouched (imported for `SessionTokens` and `RENEWAL_TIMEOUT_MS` only);
  - no global `fetch`.
- **Secrets:**
  - the token is sent only in `renewal:succeeded` on the dedicated same-origin channel;
  - `renewal:failed` and `SiblingRenewalFailedError` carry no reason;
  - nothing is stored or logged.
- **Not applicable:** transactions, idempotency, money, enumeration-safety.
