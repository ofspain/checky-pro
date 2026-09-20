# auth · T12 — Phase 8: Independent Code Review

Reviewed `LockoutService.java`, `LockoutStateRepository.java`, `LockoutState.java`,
`LockoutProperties.java`, and `AccountService.java`'s new `lock` / `unlock` methods against the
frozen brief (`04-frozen-task-brief.md`) and the Phase 7 self-review.

---

## Finding 1 — Native `FOR UPDATE` query locks both `lockout_state` and `accounts` rows

- **Issue.** PostgreSQL locks rows from every table named in a `FOR UPDATE` clause that lacks an `OF <table>` list. `LockoutStateRepository.findByAccountUuidForUpdate` joins `lockout_state` to `accounts` and applies `FOR UPDATE` without `OF ls`, so the matching `accounts` row is also row-locked for the duration of every login-attempt transaction. This is wider than the frozen brief's Finding 4 disposition intended, serializes otherwise-unrelated account operations against login attempts, and increases deadlock surface area.
- **Evidence.** `LockoutStateRepository.java:23-25`:
  ```sql
  SELECT ls.* FROM lockout_state ls JOIN accounts a ON a.id = ls.account_id
  WHERE a.account_uuid = :accountUuid FOR UPDATE
  ```
  The Phase 7 self-review independently found the same evidence.
- **Recommendation.** Scope the lock to the intended table: append `OF ls` to the query (`... FOR UPDATE OF ls`). Re-run the concurrency-respecting test required by the frozen brief to confirm serializability of concurrent `lockout_state` updates is preserved.
- **Confidence.** High.

---

## Finding 2 — `recordFailedAttempt` throws for a missing account, contradicting the frozen brief

- **Issue.** The frozen brief Outputs section says "Neither method throws for a missing account." `recordFailedAttempt` resolves the internal account id in `persistNewOrUpdated` and throws `IllegalStateException` when the UUID has no matching account. This is a direct deviation from the stated contract.
- **Evidence.** `LockoutService.java:137-138`:
  ```java
  Long accountId = repository.findAccountIdByUuid(accountUuid)
          .orElseThrow(() -> new IllegalStateException("No account found for UUID " + accountUuid));
  ```
  Frozen brief `04-frozen-task-brief.md` Outputs states that neither entry point throws for a missing account.
- **Recommendation.** Resolve the contradiction in Phase 9: either (a) remove the existence check and treat a missing account as a no-op (consistent with `recordSuccessfulAttempt` and the brief), accepting that an FK violation is only the last-resort signal if a caller violates its trust boundary; or (b) amend the frozen brief to state that `recordFailedAttempt` may throw while the other methods do not. Do not leave the mismatch silent.
- **Confidence.** High.

---

## Finding 3 — Asymmetric existence validation could become an enumeration oracle

- **Issue.** Only the first-failure insert path validates that the UUID maps to a real account. `recordSuccessfulAttempt` and the blocked-attempt short-circuit return silently for any UUID, real or not. `resetLockout` delegates to `AccountService.unlock`, which loads the account and can throw `AccountNotFoundException`. If these methods are ever exposed beyond the trusted T13/T14 callers, the differing behavior leaks whether an account exists.
- **Evidence.** `LockoutService.java:83` (success no-op on empty), `LockoutService.java:62-64` (blocked no-op, also on empty when no row), `LockoutService.java:137-138` (failure throws), `LockoutService.java:108` (reset delegates to `AccountService.unlock` which loads and can throw).
- **Recommendation.** Make existence handling consistent across all three entry points once the brief choice in Finding 2 is made: either all three no-op for unknown UUIDs, or all three surface the same internal exception type regardless of account state. Document the chosen trust boundary in the brief.
- **Confidence.** Medium.

---

## Finding 4 — `recordSuccessfulAttempt` no-op on a missing row leaves a LOCKED account locked under data inconsistency

- **Issue.** The frozen brief AC8 deliberately makes success on a missing `lockout_state` row a no-op. In normal flow a LOCKED account always has a row, but if the row is ever missing due to data corruption or a pre-existing account, a successful login after lock expiry will clear nothing and the account remains LOCKED with stale counters.
- **Evidence.** `LockoutService.java:83` short-circuits when `existing.isEmpty()` before any persistence or `AccountService` call. Frozen brief AC8: "Success on a missing row → no insert, zeroed decision returned."
- **Recommendation.** Document this as an accepted data-integrity invariant in the brief: "a LOCKED account must have a `lockout_state` row; its absence is an operator-facing data-corruption scenario, not a state this service repairs." If repair is desired, add a defensive `AccountService.unlock` call when the row is absent (requires an additional status lookup, which would need a new `AccountService` query).
- **Confidence.** Low (matches the brief exactly; only a latent operational edge case).
