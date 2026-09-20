# auth · T05 — Phase 8: Independent Code Review Findings

Reviewed the implementation in `services/auth/src/main/java/com/themistra/auth/account/`
(`VerificationTokenService.java`, `VerificationToken.java`, `VerificationTokenRepository.java`,
`VerificationTokenProperties.java`) and `services/auth/src/main/resources/application.properties`,
against the frozen brief (`04-frozen-task-brief.md`) and `agents.md`. Findings are fresh but
overlap with the Phase 7 self-review where the self-review correctly identified real defects.

---

## Finding 1 — `issue`'s collision-retry loop does not actually retry in PostgreSQL (HIGH)

**Issue:** The method catches `DataIntegrityViolationException` around `saveAndFlush` and loops up
to 3 times. PostgreSQL aborts the entire transaction as soon as any statement fails a constraint.
Because `issue` is `@Transactional`, attempts 2 and 3 run inside the same aborted transaction and
fail immediately, typically with a Spring-translated exception other than
`DataIntegrityViolationException` (e.g., `InvalidDataAccessApiUsageException` or
`JpaSystemException`). That exception is not caught, so retries do not happen and the intended
`IllegalStateException` is never thrown.

**Evidence:**
- `VerificationTokenService.java` lines 57–83: retry loop inside `@Transactional issue(...)`.
- `VerificationTokenRepository.java` line 39: `token_hash` has a `UNIQUE` constraint.

**Recommendation:** Move each insertion attempt into its own transaction (e.g., a collaborator
method annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)` called via a self-injected
proxy, or a dedicated `VerificationTokenInsertService`). The retry loop must live outside any single
transaction. Because real collisions at 32 random bytes are effectively impossible, an acceptable
alternative is to catch the first exception and immediately throw `IllegalStateException`, dropping
the retry requirement.

**Confidence:** HIGH.

---

## Finding 2 — `VerificationTokenResult` auto-generated `toString()` leaks the raw token (HIGH)

**Issue:** Java records generate a `toString()` that includes every component. The raw token is
therefore printed verbatim whenever a caller logs the result object, violating the frozen brief's
requirement that "no `toString`/serialization path may expose the raw token" and `agents.md`'s
standing rule to never log tokens/secrets.

**Evidence:**
- `VerificationTokenService.java` lines 150–152: plain record with `rawToken` component and no
  custom `toString()`.

**Recommendation:** Override `toString()` to omit `rawToken` (e.g., include only `accountUuid` and
`purpose`). Also consider suppressing Jackson serialization of the raw token if the result object
could ever be returned from a controller — either by using a non-record holder for the raw value or
by adding an explicit serialization exclusion.

**Confidence:** HIGH.

---

## Finding 3 — `consume`'s account-usability check is not atomic with token consumption (MEDIUM)

**Issue:** `consume` resolves the account and checks `DELETED`/`SUSPENDED`, then calls
`markConsumed`. If the account is deleted or suspended by a concurrent transaction between those
two steps, `consume` will mark the token used and return the account UUID of an unusable account.
The single-use token is consumed, but the redemption should arguably have been rejected.

**Evidence:**
- `VerificationTokenService.java` lines 115–126: `resolveUsableAccount(...)` before `markConsumed(...)`.

**Recommendation:** Re-resolve the account status inside the same transaction after
`markConsumed` returns 1, and return `Optional.empty()` if the account is no longer usable. This
wastes a single-use token only in the rare interleaving where an account is deactivated during
redemption, preserving R5's uniform shape while avoiding "successful" redemption of deactivated
accounts. Alternatively, document that the existing behavior is acceptable because account
deactivation during redemption is out of scope for T05.

**Confidence:** MEDIUM.

---

## Finding 4 — No unit tests exist for the verification token service (HIGH)

**Issue:** The task statement explicitly requires unit tests with a fixed `Clock`. The current
repository contains `PasswordPolicyTest`, `PasswordPolicyPropertiesTest`, `AccountServiceTest`, etc.,
but no `VerificationTokenServiceTest`, `VerificationTokenPropertiesTest`, or similar.

**Evidence:**
- Task statement: "Unit-test with a fixed `Clock`."
- Frozen brief Required Tests (lines 170–184): lists tests for TTL boundary, atomic double-consume,
  collision retry, config validation, purpose round-trip, null handling, and raw-token non-leakage.
- No `*VerificationToken*Test.java` files under `services/auth/src/test/java/com/themistra/auth/account/`.

**Recommendation:** Treat this as a blocker before accepting T05. Create unit tests covering the
required cases, especially the atomic double-consume and collision-retry paths that cannot be
verified by inspection alone.

**Confidence:** HIGH.

---

## Finding 5 — `invalidateActive` marks already-expired tokens as used; harmless but unnecessary, and it widens lock contention (LOW)

**Issue:** `issue` calls `tokenRepository.invalidateActive(...)` with `usedAt IS NULL` only — it does
not restrict to unexpired tokens. This also updates rows whose `expires_at` is already in the past.
Functionally this makes no difference because expired tokens are already unredeemable, but it
acquires row locks on more rows than necessary and can block a concurrent `consume` of a prior
valid token during reissue.

**Evidence:**
- `VerificationTokenRepository.java` lines 36–40: `invalidateActive` lacks an `expires_at > :now`
  clause.

**Recommendation:** Add `AND t.expiresAt > :now` to the `invalidateActive` query so only tokens that
are currently redeemable are invalidated. This reduces lock scope without changing observable
behavior.

**Confidence:** LOW.

---

## Finding 6 — `ttlMinutes` has no upper bound, allowing absurd TTLs and potential `Instant` overflow (LOW)

**Issue:** `VerificationTokenProperties.ttlMinutes` has `@Min(1)` but no `@Max`. A configured value
near `Long.MAX_VALUE` would cause `clock.instant().plus(ttlMinutes, MINUTES)` to overflow and
produce a near-past `expiresAt`, creating effectively expired tokens at issue time without failing
startup validation.

**Evidence:**
- `VerificationTokenProperties.java` line 16: `@Min(1) long ttlMinutes`.
- `VerificationTokenService.java` line 68: `now.plus(properties.ttlMinutes(), ChronoUnit.MINUTES)`.

**Recommendation:** Add a sensible `@Max` (e.g., one year in minutes, `525_600`) to the TTL record.
This aligns with `agents.md`'s intent that invalid config values fail startup validation rather
than produce silently broken runtime behavior.

**Confidence:** MEDIUM.

---

## Finding 7 — `DataIntegrityViolationException` catch is overly broad (LOW)

**Issue:** The retry loop catches `DataIntegrityViolationException` for any constraint failure, not
just `token_hash` uniqueness. In practice, the only other constraints are `NOT NULL` and the purpose
check constraint, both of which should never fail given the code path. Still, a future schema change
or unexpected persistence failure could be swallowed and retried rather than surfaced.

**Evidence:** `VerificationTokenService.java` lines 74–79.

**Recommendation:** If the retry mechanism is retained after Fixing Finding 1, inspect the exception
cause to ensure it is a unique-violation on `token_hash` before retrying; otherwise rethrow the
original exception.

**Confidence:** LOW.

---

## Summary

The verification token service is well-structured for its scope, but the two HIGH findings are
blockers: the collision-retry loop is incorrect in PostgreSQL and the result record leaks the raw
token via its generated `toString()`. Missing tests (Finding 4) also prevent acceptance. The MEDIUM
finding about account-status concurrency should be either fixed or explicitly accepted, and the
LOW findings are straightforward hardening.
