# auth · T05 — Phase 7: Self-Review

Findings only, against the frozen brief (`04-frozen-task-brief.md`) and `agents.md`. No fixes
applied here — Phase 9 handles remediation after independent review (Phase 8).

---

## Finding 1 — The collision-retry loop in `issue` does not actually work against PostgreSQL (HIGH)

**Issue:** `issue`'s loop (lines 69–83) calls `tokenRepository.saveAndFlush(token)` inside a
`try/catch (DataIntegrityViolationException e)`, intending to retry up to 3 times on a
`token_hash` collision. But PostgreSQL aborts the *entire enclosing transaction* the instant any
statement inside it fails a constraint check — every subsequent statement in that same
transaction (including the next loop iteration's `saveAndFlush`) fails immediately with
`ERROR: current transaction is aborted, commands ignored until end of transaction block`. Since
`issue` is a single `@Transactional` method, all 3 attempts share one Postgres transaction. If
attempt 1 ever actually collided:
- Attempt 2's `saveAndFlush` would not cleanly retry — it would fail immediately due to the
  already-aborted transaction, very likely surfacing as a different exception type (Spring
  typically translates this as `InvalidDataAccessApiUsageException` or a `JpaSystemException`, not
  `DataIntegrityViolationException`), which the current `catch` clause would **not** catch.
- That exception would propagate out of `issue` uncaught, rather than being retried or producing
  the intended `IllegalStateException` after exhausting attempts.

**Severity:** HIGH — this is a direct correctness failure of the exact mechanism Phase 4 Finding 6
required ("retry... if all retries fail, throw `IllegalStateException`"). The saving grace is that
a real collision at 32 random bytes (256 bits of entropy) is astronomically unlikely to ever occur
in practice — but the code as written would misbehave badly, not gracefully, if it ever did.

**Evidence:** `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java:69-83`.

**Recommendation:** Each insert attempt needs its own top-level transaction, isolated from a prior
attempt's aborted one — e.g., extract the single-attempt insert into a separate collaborator (or
self-injected proxy) method annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)`
(a plain private method won't work due to Spring AOP's self-invocation limitation), with the
retry loop living *outside* any single transaction, in the (non-transactional) `issue` caller.

---

## Finding 2 — `VerificationTokenResult`'s auto-generated `toString()` leaks the raw token (HIGH)

**Issue:** `VerificationTokenResult` (lines 150–152) is a plain Java `record` with no custom
`toString()`. Records auto-generate a `toString()` that includes *every* component — so
`VerificationTokenResult.toString()` will print `rawToken=<the actual raw token>` verbatim. This
directly contradicts the frozen brief's Finding 10 disposition: *"No `toString`/serialization path
may expose the raw token."* Any future caller (T06 is the first) that logs this result for
debugging — a very natural thing to do with a freshly-created object — would leak the raw
verification/reset token into logs.

**Severity:** HIGH — a concrete, currently-live violation of an explicit, human-approved
requirement, and a real secret-leakage vector (`agents.md`: "Never log tokens, secrets... ").

**Evidence:** `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java:150-152`.

**Recommendation:** Override `toString()` on `VerificationTokenResult` to omit `rawToken` (e.g.,
`"VerificationTokenResult[accountUuid=" + accountUuid + ", purpose=" + purpose + "]"`), or wrap the
raw token in a small non-record holder type that doesn't expose it via `toString`/equals.

---

## Dimensions checked with no findings

- **Boundary conditions:** TTL boundary is consistently "expires_at == now ⇒ expired" in both
  `verify` (`!expiresAt.isAfter(now)`) and `markConsumed`'s SQL (`expiresAt > :now`) — no
  inconsistency between the read-only and mutating paths.
- **Null-safety:** all three public methods (`issue`, `verify`, `consume`) guard their primary
  arguments via `Objects.requireNonNull` before any use.
- **Thread-safety:** `SecureRandom` is documented thread-safe for shared-instance use across
  concurrent callers; `VerificationTokenService` holds no other mutable state.
- **Transaction boundaries (aside from Finding 1):** `verify` is correctly `readOnly = true` and
  performs no writes; `consume`'s account-usability check happens before the atomic
  `markConsumed` call, so a rejected consume never mutates a token belonging to an unusable
  account.
- **Module boundaries:** all new code stays within `account`; no new dependency on `authn`,
  `audit`, or `events`.
- **Idempotency:** `consume` is correctly *not* idempotent on success (single-use is the point);
  repeated calls after the first correctly and uniformly return `Optional.empty()`.
- **Money types:** N/A — no monetary values in this task.
- **Enumeration-safety / secret-handling (aside from Finding 2):** only the token hash is ever
  persisted or would appear in a repository-level log; `AccountNotFoundException`'s message
  (UUID only) is safe per its own existing javadoc and is only ever thrown from `issue`, an
  internal, non-attacker-facing call per the frozen brief's own reasoning.
