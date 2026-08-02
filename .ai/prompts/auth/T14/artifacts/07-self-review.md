# auth · T14 — Phase 7: Self Review

Reviews `AccountService.adminUnlock` and `AdminAccountController.unlock` (Phase 6) against the
frozen brief (`04-frozen-task-brief.md`) and `agents.md`. No rewrite performed — findings only;
fixes are Phase 9's job.

---

## Finding 1 — Calling the endpoint on a `SUSPENDED`/`DELETED`/`PENDING_VERIFICATION` account still emits `"user.unlocked"`/`"account.unlocked"`, even though nothing became `ACTIVE`

- **Issue:** The frozen brief's AC2/AC7 frame the "no-op" case as "the account is already
  `ACTIVE`" — but `unlock(UUID)`'s guard (`if (status == LOCKED) account.unlock();`) is a no-op
  for **any** non-`LOCKED` status, not just `ACTIVE`. `adminUnlock` unconditionally publishes
  `"user.unlocked"` and records `"account.unlocked"` regardless of which non-`LOCKED` status the
  account was actually in. Calling this endpoint on a `SUSPENDED` or `DELETED` account (a caller
  error, or a stale/incorrect admin action) leaves `Account.status` completely unchanged, but
  still emits an event whose name asserts the account is now unlocked/usable — a materially
  misleading signal to anything consuming the audit trail or the `auth.user.lifecycle` topic.
- **Severity:** Medium — not a security-control bypass (`Account.status` itself is correctly
  guarded and never incorrectly transitions), but a genuine observability/correctness gap: the
  audit row and lifecycle event assert something that didn't happen, and neither carries enough
  detail to tell the difference (`recordAudit`'s `details` parameter is `null` for every call site
  in this class, confirmed by inspection — no before/after status is captured anywhere).
- **Evidence:** `AccountService.java:325-330` (`unlock(UUID)`'s guard, unchanged, T13) — the
  condition is `status == LOCKED`, not `status != ACTIVE`. `AccountService.java:343-349`
  (`adminUnlock`) — `publishLifecycleEvent`/`recordAudit` calls have no conditional guard at all,
  unlike the status mutation itself.
- **Recommendation:** Decide explicitly (Phase 9): either (a) only publish/audit when the account
  was actually `LOCKED` at the time of the call (checking `account.getStatus()` before calling
  `unlock(accountUuid)`, mirroring the mutation's own guard so the event and the mutation stay in
  sync), or (b) keep the current unconditional behavior but rename the emitted events to something
  status-agnostic (e.g., `"admin.unlock_requested"`) that doesn't assert a transition occurred, or
  (c) explicitly document this as an accepted characteristic — the endpoint's purpose is "attempt
  to unlock," and the event records the *attempt*, not a guaranteed transition — matching
  `LockoutService.resetLockout`'s own "clear counters unconditionally" philosophy one level up.
  Not silently left as-is without a decision, since it wasn't explicitly considered at Phase 2/3/4
  (those discussions only ever contrasted `LOCKED` vs. `ACTIVE`, never the other two statuses).

---

## Non-findings (verified clean)

- **Module boundary (L12):** confirmed via direct `grep` this phase (not just trusting the
  implementation notes' own claim) — zero `com.themistra.auth.authn` imports anywhere in
  `AccountService.java`. Only `AdminAccountController` gained the new cross-module dependency,
  exactly as the frozen brief requires.
- **Transaction sequencing (Finding 2 from Phase 3/4, re-traced, not a new issue):** confirmed
  `LockoutService.resetLockout`'s internal call to `accountService.unlock(accountUuid)` joins
  `resetLockout`'s own transaction (Spring's default `REQUIRED` propagation across the two beans)
  — so the actual `lockout_state` clear and the `Account.status` transition commit together, in
  one transaction, separate from `adminUnlock`'s own (redundant, still-correct) second `unlock()`
  call plus its audit/lifecycle writes. This matches the frozen brief's Finding 2 description
  exactly — traced precisely here to confirm it isn't subtly worse (e.g., three transactions, or a
  narrower/wider window than documented) than what was already accepted.
- **Redundant `unlock()` call correctness:** traced the two-transaction sequence for a concurrent
  or re-entrant call — since each transaction loads a fresh `Account` via a new persistence
  context, `adminUnlock`'s own `unlock(accountUuid)` call correctly observes whatever
  `resetLockout`'s already-committed transaction did, and is a true no-op (not a double
  transition, not a lost update) in the common case where `resetLockout` already succeeded.
- **Null handling:** `accountUuid`/`actorUuid` are never explicitly null-checked, consistent with
  every sibling method in this class (Spring guarantees non-null path variables and an
  authenticated principal's resolved UUID) — not a new gap introduced by this task.
- **Authorization:** `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")` matches R20's header and
  `suspend`/`reinstate`'s existing precedent exactly.
- **Readability/complexity:** both new methods are minimal, match their respective files'
  established shape closely; no excess complexity introduced.

## Specification references

- Frozen brief: `04-frozen-task-brief.md` — AC2 ("safe no-op"), AC7 ("status-transition
  idempotent... every successful call appends a new audit row"), Finding 2's disposition (traced,
  confirmed accurate, not worsened).
- `agents.md`: module boundaries (L12), audit/outbox conventions.
