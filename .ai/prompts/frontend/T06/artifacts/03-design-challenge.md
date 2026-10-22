# frontend · T06 — Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T06 — Shared renewal across tabs**. Findings are against the brief itself; no code was changed.

---

## Findings

### 1. Late outcome message after grace can race with a follower's own renewal
**Issue:** A follower that does not receive an outcome within `graceMs` becomes the leader and starts `local.renew()`. If the original leader's message was merely delayed (not lost), it arrives after the follower entered leader mode. The brief says every valid message calls `local.adopt(token)` and counts as an outcome, but it does not say the follower should cancel its own in-flight `local.renew()`. This can leave the follower with two competing tokens and two outcomes for the same renewal episode.  
**Severity:** Medium — can produce inconsistent token state and an extra renewal in a single episode.  
**Evidence ·** Brief §Behaviour step 3: "otherwise the previous holder vanished without an outcome, so become the leader inside this lock." Brief §Behaviour step 5: "`renewed` calls `local.adopt(token)`. Every valid message counts as an outcome and wakes the waiters."  
**Recommended brief amendment:** When a follower acquires the lock and becomes leader, it should first re-check for any outcome message received since it marked its count and since it started waiting. If a late `renewed` or `renewal-failed` message is found, it should adopt/use it and release the lock without starting `local.renew()`. Document that the leader's own `local.renew()` is only started when no outcome has been observed for this episode.

### 2. The token crosses tabs, which needs an explicit L3 carve-out
**Issue:** The brief states that BroadcastChannel same-origin messaging is "in-memory messaging between the app's own tabs, not storage, a log or a URL." However, `agents.md` L3 says "Access tokens live in memory only" and "Tokens are never written to `localStorage`, logs, or analytics, and are never placed in a URL." It does not explicitly list BroadcastChannel as an allowed medium.  
**Severity:** Medium — a strict reading of L3 could forbid token transmission over BroadcastChannel.  
**Evidence ·** Brief §Locked Decisions L3: justification for BroadcastChannel. `agents.md` L3: "in memory only" and the list of forbidden destinations.  
**Recommended brief amendment:** Either add a note to the frozen brief that L3 is interpreted to permit same-origin BroadcastChannel between the SPA's own tabs, or request an ADR/clarification in `agents.md` before freezing T06.

### 3. No behaviour specified for `close()` while a renewal is in flight
**Issue:** `close()` detaches from the channel. If a tab calls `close()` while it is the leader and `local.renew()` is running, the outcome will not be posted to siblings. Followers will wait out the grace period and then renew themselves, which is correct but wasteful. More importantly, the local tab's own boundary may still be waiting on the renewal.  
**Severity:** Low — unlikely in normal use, but undefined.  
**Evidence ·** Brief §Outputs: "`close()`, which detaches from the channel." No mention of in-flight renewal.  
**Recommended brief amendment:** Specify that `close()` only detaches the BroadcastChannel listener; any in-flight `renew()` promise continues and its outcome is still returned to the local caller. If the tab is closed by the browser (not `close()`), the lock is released and siblings follow the "leader vanished" path.

### 4. Double timeout: both the cross-tab layer and the T05 boundary bound the same call
**Issue:** The T05 `sessionBoundary.ts` already wraps `tokens.renew()` in a 30 s timeout (`RENEWAL_TIMEOUT_MS`). T06's `shareRenewalAcrossTabs` also sets a `timeoutMs` default to the same value. A follower may hit the T06 deadline and reject while the leader is still renewing; the leader may then succeed, but the follower has already given up. Conversely, the leader's T05 timeout may fire before T06's, producing inconsistent outcomes.  
**Severity:** Low — the timeouts are the same duration, so races are rare; but the layering is redundant.  
**Evidence ·** Brief §Behaviour step 1: deadline of `timeoutMs` from now (default `RENEWAL_TIMEOUT_MS`). T05 `sessionBoundary.ts` `RENEWAL_TIMEOUT_MS = 30_000`.  
**Recommended brief amendment:** Make T06's `renew()` not add its own timeout when the underlying `LocalSession.renew()` is already bounded (which it will be by T05's boundary). T06 should rely on `local.renew()` rejecting on timeout. T06's own deadline should only bound lock acquisition / follower waiting. Document the layering.

### 5. Multiple coordinators in one tab are not prevented
**Issue:** `shareRenewalAcrossTabs` can be called more than once in the same tab, creating multiple coordinators on the same channel and lock name. They would compete for the lock and broadcast duplicate outcomes, violating "exactly one renewal per tab set."  
**Severity:** Low — production wiring should create one coordinator per tab, but the API allows mistakes.  
**Evidence ·** Brief §Files to Create: `shareRenewalAcrossTabs(local, options?)` with no singleton guard. Brief §Constraints: "one coordinator per tab, created by production wiring."  
**Recommended brief amendment:** Either (a) make `shareRenewalAcrossTabs` return a singleton per channel name using a module-level `WeakRef`/`Map`, or (b) document loudly that calling it twice in one tab is undefined behaviour and add a dev-time warning if two instances detect each other on the same channel.

### 6. No fallback if `BroadcastChannel` is unavailable
**Issue:** The brief assumes BroadcastChannel exists. Some testing environments, older browsers, or sandboxed contexts may not expose it. The fallback only covers missing `navigator.locks`.  
**Severity:** Low — modern browsers and Node 22 support it, but it's an unstated assumption.  
**Evidence ·** Brief §Dependencies: "The built-in `BroadcastChannel` and `navigator.locks`." Brief §Behaviour step 6: "No Web Locks ... delegate straight to `local.renew()`." No BroadcastChannel fallback.  
**Recommended brief amendment:** If `BroadcastChannel` is unavailable, degrade to local renewal (same as no-lock fallback). Document that cross-tab sharing requires both APIs.

### 7. Grace-period length is hard-coded with no rationale for tests
**Issue:** `SIBLING_GRACE_MS = 1_000` is chosen based on the probe. Tests will need fake timers to cover the grace path and the "leader vanished" path. Fake timers plus Web Locks semantics plus BroadcastChannel could make tests flaky if not carefully sequenced.  
**Severity:** Low — test design concern.  
**Evidence ·** Brief §Open Questions: "`SIBLING_GRACE_MS = 1_000`." Brief §Required Tests: timing uses fake timers where grace or deadline matters.  
**Recommended brief amendment:** Keep the constant, but expose `graceMs` in the options so tests can use a much smaller value (e.g., 50 ms) to keep the suite fast and deterministic. The default remains 1,000 ms.

### 8. `renewal-failed` message type could collide with future sign-out messages
**Issue:** Task 39 will reuse the channel namespace for sign-out propagation with its own message type. The brief does not define a namespace/prefix for message types. If a future developer adds a type like `failed` or `renew`, it could be confused with T06 types.  
**Severity:** Low — unlikely collision if names are descriptive, but worth preventing.  
**Evidence ·** Brief §Outputs: message types `renewed` and `renewal-failed`. Brief §Scope Out: "Sign-out propagation (task 39; it will reuse the channel name space, with its own message type)."  
**Recommended brief amendment:** Prefix T06 message types with a namespace, e.g., `checky:renewed` and `checky:renewal-failed`, or define a top-level `scope` field. Document the convention for task 39.

### 9. No test for a third tab joining an in-flight renewal
**Issue:** The required tests use two coordinators. Real browsers can have N tabs. The design should scale, but there is no explicit test for three or more tabs.  
**Severity:** Low — the design naturally scales, but a regression in broadcast handling might only show with N > 2.  
**Evidence ·** Brief §Required Tests: cases use two tabs.  
**Recommended brief amendment:** Add a case with three coordinators: A is leader, B and C are followers. Assert both B and C resolve with A's token and neither calls `local.renew()`.

### 10. No assertion that a received token is immediately `current()` in every sibling
**Issue:** AC2 says "after A succeeds, B's `current()` is A's token, through `adopt`." The test should assert not only that B's `renew()` resolves with the token, but also that `local.current()` returns it before `renew()` resolves (because `adopt` is called on message receipt, before the follower gets the lock).  
**Severity:** Low — subtle ordering test.  
**Evidence ·** Brief §AC2.  
**Recommended brief amendment:** In the AC1/AC2 test case, assert `localB.current()` equals the token immediately after the message is received, before `renewB` settles.

---

## Open Questions

- None blocking. Finding 1 (late-message race) is the most significant and should be resolved in the frozen brief.
