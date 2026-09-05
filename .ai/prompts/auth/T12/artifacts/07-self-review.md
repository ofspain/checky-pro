# auth · T12 — Phase 7: Self Review

Reviews `LockoutState.java`, `LockoutStateRepository.java`, `LockoutProperties.java`,
`LockoutService.java`, and `AccountService.java`'s new `lock`/`unlock` methods (Phase 6) against
the frozen brief (`04-frozen-task-brief.md`) and `agents.md`. No rewrite performed — findings
only; fixes are Phase 9's job.

---

## Finding 1 — `FOR UPDATE` without a table qualifier locks both `lockout_state` and `accounts`, not just the intended row

- **Issue:** `findByAccountUuidForUpdate`'s native query joins `lockout_state` to `accounts` and
  applies `FOR UPDATE` with no `OF <alias>` clause. PostgreSQL's documented behavior for `SELECT
  ... FOR UPDATE` without a table list is that it locks rows from **every** table contributing to
  the result set, not just the table the method name/Javadoc implies. This query therefore
  row-locks the matching `accounts` row for the full duration of every `LockoutService`
  transaction, in addition to the `lockout_state` row.
- **Severity:** High
- **Evidence:** `LockoutStateRepository.java:23-25`:
  ```java
  @Query(value = "SELECT ls.* FROM lockout_state ls JOIN accounts a ON a.id = ls.account_id "
          + "WHERE a.account_uuid = :accountUuid FOR UPDATE", nativeQuery = true)
  Optional<LockoutState> findByAccountUuidForUpdate(@Param("accountUuid") UUID accountUuid);
  ```
  The method's own Javadoc (`LockoutStateRepository.java:19-21`) says "loads the row for the given
  account, locking it" (singular) — the implementation locks two rows across two tables, wider
  than documented and wider than the frozen brief's Finding 4 disposition intended ("pessimistic
  row lock" on `lockout_state`, to fix the counter's lost-update race — nothing in that finding
  called for locking `accounts` too).
  Consequence: every failed/successful login evaluation now serializes against **any other**
  in-flight `AccountService` operation on that same account (password change, suspend, reinstate,
  `resetPassword`'s own `unlock()` call, etc.), not just against other concurrent lockout
  evaluations. On a security-critical, potentially high-volume path (every login attempt), this is
  a real contention/latency risk between unrelated features that share nothing but the same
  account row — surprising and hard to diagnose in production, since nothing in this task's code
  or docs says login attempts and, say, a password change should ever block on each other.
- **Recommendation:** Scope the lock to the intended row only: `... FOR UPDATE OF ls` (the
  `lockout_state` alias). Re-verify against the frozen brief's Required Tests item ("a
  pessimistic-lock-respecting test, not just single-threaded logic") that the narrower lock still
  serializes concurrent `lockout_state` writes correctly.

---

## Finding 2 — Account-existence validation is asymmetric between the failure and success/blocked paths

- **Issue:** `recordFailedAttempt` on a UUID with no `lockout_state` row eventually calls
  `findAccountIdByUuid`, which throws `IllegalStateException` if the UUID doesn't correspond to
  any real account (`persistNewOrUpdated`, `LockoutService.java:137-138`). `recordSuccessfulAttempt`
  and the blocked-attempt short-circuit in both methods never make an equivalent call — a bogus or
  nonexistent `accountUuid` passed to `recordSuccessfulAttempt` (or to `recordFailedAttempt` while
  a same-shaped stale/blocked row coincidentally exists) silently returns a harmless zeroed/no-op
  decision instead of surfacing any signal that the account doesn't exist.
- **Severity:** Low
- **Evidence:** `LockoutService.java:80-89` (`recordSuccessfulAttempt`, `existing.isEmpty()`
  short-circuits at line 83 with no existence check) vs. `LockoutService.java:130-140`
  (`persistNewOrUpdated`, the only code path that validates existence, and only on the
  never-failed-before insert branch).
- **Recommendation:** Given the frozen brief's own documented precondition — "callers (T13) are
  responsible for only invoking this service for accounts it already knows are eligible" — this
  may be intentional/acceptable (the caller is trusted to pass a real UUID), matching the same
  trust boundary T11 already established for its own precondition (Finding 4's disposition). Flag
  for explicit confirmation rather than assuming: either accept as consistent with that trust
  boundary, or add an equivalent existence check to the two currently-unvalidated paths for
  defense in depth.

---

## Non-findings (verified clean)

- **Module boundary (L12):** confirmed via direct `grep` (Phase 6) — no
  `com.themistra.auth.account.Account` import anywhere in the four new `authn` files; only
  `AccountService` is imported, in `LockoutService.java:3`. `LockoutStateRepository`'s native
  queries reference table/column names as SQL string literals, not the `Account` Java class — no
  bytecode dependency for `ArchitectureTest` to flag.
- **Re-lock guard (Finding 2 from Phase 3/4):** `AccountService.lock`/`unlock`
  (`AccountService.java:316-330`) correctly guard on current status before calling
  `account.lock()`/`unlock()` — verified both branches match the frozen brief exactly, no
  off-by-one or inverted condition.
- **Transaction boundaries:** all three `LockoutService` public methods are `@Transactional`; the
  `AccountService` call happens inside that same transaction (Spring's default `REQUIRED`
  propagation across the two beans — not a self-invocation, since `accountService` is a
  constructor-injected, separately-proxied bean). A thrown exception from either side rolls back
  both the `lockout_state` write and any `Account` mutation together.
- **Null handling:** `Objects.requireNonNull` on every parameter across all three
  `LockoutService` entry points, matching T11's own convention; `resetLockout` correctly omits a
  `now` check since it takes no time parameter.
- **Thread-safety:** `LockoutService` itself is stateless (three `final` fields, no mutable
  instance state) — safe as a shared Spring singleton; concurrency protection is delegated
  entirely to the database row lock (Finding 1's narrower-scope fix still preserves this).
- **Boundary/AC delegation:** `LockoutService` never re-implements any R16-R19/L4 boundary
  arithmetic itself — every decision comes from `LockoutStateMachine.evaluate`/`reset`, already
  proven correct by T11's own 22-test suite. Self-review found no case where `LockoutService`
  second-guesses or duplicates that logic.
- **Missing-row asymmetry (Finding 6/AC8, Phase 4):** verified correct — `recordFailedAttempt`
  creates a row on a missing snapshot; `recordSuccessfulAttempt` explicitly skips persistence via
  the `existing.isEmpty()` short-circuit at line 83. No accidental insert-on-success found.
- **`resetLockout`'s unconditional `AccountService` call (AC9):** verified the call sits outside
  the `existing.ifPresent(...)` lambda (`LockoutService.java:104-108`), so it always runs
  regardless of whether a row existed — matches the design intent exactly (`AccountService.unlock`
  is the safety net, not `resetLockout`'s own conditional logic).
- **Money/enumeration-safety:** not applicable to this class (no monetary types; no
  externally-observable HTTP response for this task to leak enumeration signals through — this
  service has no controller in this task's scope).

## Specification references

- Frozen brief: `04-frozen-task-brief.md` — Finding 4 disposition (Finding 1's over-broad lock
  scope directly concerns this), Finding 5/AC2 (Finding 2's existence-check question touches the
  same trust-boundary language), Required Tests (concurrency-proving test requirement).
- `agents.md`: module boundaries (L12), transaction/persistence conventions.
