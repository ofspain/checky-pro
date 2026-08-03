# auth · T17 — Phase 7: Self Review

Consumes `artifacts/06-implementation-notes.md`. Findings only — no fixes applied here (Phase 9).

Reviewed files: `MfaEnrollment.java`, `MfaEnrollmentRepository.java`, `RecoveryCode.java`,
`RecoveryCodeRepository.java`.

## Findings

**1. Issue:** `MfaEnrollment.confirm(null)` and `recordUse(null)` are silently accepted instead of
rejected, and produce misleading results rather than failing loudly.
**Severity:** Medium
**Evidence:** `MfaEnrollment.java:70-75` (`confirm`) — the guard only checks `this.confirmedAt !=
null` (i.e., "not already confirmed"); it never checks that the *incoming* `confirmedAt` argument
is non-null. Calling `confirm(null)` passes the guard (since the field starts null) and then sets
`this.confirmedAt = null` — a no-op that leaves the enrollment looking exactly as unconfirmed as
before, with no exception and no signal that anything went wrong. A caller bug that passes a null
`Clock`-derived instant would silently fail to confirm the enrollment. `recordUse(null)`
(`:78-80`) is worse: it unconditionally overwrites `lastUsedAt`, so a stray null call would *erase*
a previously-recorded last-used timestamp.
**Recommendation:** Add `Objects.requireNonNull(confirmedAt, ...)` / `Objects.requireNonNull(lastUsedAt, ...)`
guards at the top of both methods, so a null argument fails fast with a clear
`NullPointerException` instead of silently corrupting or no-oping state. The frozen brief's
"confirmedAt/lastUsedAt/usedAt are all legitimately nullable" constraint is about the *fields* as
persisted column state, not about tolerating a null argument to the mutator that writes them —
this is a related but distinct case worth its own guard.

**2. Issue:** `MfaEnrollment.getSecretEncrypted()` returns the internal `byte[]` by reference, not
a defensive copy.
**Severity:** Medium
**Evidence:** `MfaEnrollment.java:98-100`. Any caller holding the returned array can mutate it
in place, silently corrupting the entity's in-memory state for the encrypted seed without going
through any JPA-visible write path — Hibernate's dirty-checking for byte[]-typed fields does not
reliably detect in-place mutation of the same array instance, so such a change could be silently
lost, or in the worst case leave `secretEncrypted` and its persisted value inconsistent for the
lifetime of the managed entity. T16's `MfaSeedEncryption` treats secret byte arrays with real care
(zeroing keys after use, never logging them) — this getter is the one place in the `mfa` module
that hands out a live, mutable reference to secret material with no such protection.
**Recommendation:** Return `secretEncrypted.clone()` from the getter (or otherwise document why a
defensive copy is unnecessary, e.g. if every caller is verified read-only — not established here).

## Categories Checked, No Finding

- **Correctness:** entity mappings match the DDL exactly; `confirm`/`markUsed`'s guarded/atomic
  behavior is correct for the non-null-argument case (see Finding #1 for the null-argument gap).
- **Boundary conditions:** `findByAccountIdAndType`/`findByAccountId` with a null `accountId`
  degrade gracefully to "no match" (SQL `= NULL` semantics), not an exception — acceptable, no
  finding.
- **Thread-safety:** entities are not shared across threads; repositories are Spring Data proxies,
  inherently thread-safe.
- **Transaction boundaries:** `markUsed`'s atomicity comes from the single `UPDATE` statement
  itself, matching `VerificationTokenRepository.markConsumed`'s established, already-shipped
  pattern exactly (including the lack of `clearAutomatically`/`flushAutomatically` on
  `@Modifying`) — not a new gap this task introduces, and revisiting that codebase-wide convention
  is out of scope here.
- **Module boundaries (L12):** confirmed no import of `com.themistra.auth.account.Account`
  anywhere in the four new files.
- **Idempotency:** `markUsed` is correctly idempotent (returns 0 on repeat calls against an
  already-used code); `confirm`'s non-idempotent throw-on-repeat behavior was a deliberate Phase 4
  decision, not revisited here.
- **Money types:** N/A — no monetary fields in this task.
- **Enumeration-safety / secret-handling:** `secretEncrypted`/`codeHash` are never logged anywhere
  in these four files; no `toString()` override exists on either entity, so the JVM default
  (class name + identity hash, no field values) can't leak either field even inadvertently — see
  Finding #2 for the one real gap (the getter's missing defensive copy).
- **`CHAR(64)` vs `String`/`length=64` mapping:** `RecoveryCode.codeHash` uses the exact same
  `@Column(length = 64)` pattern `VerificationToken.tokenHash` already uses successfully against
  its own `CHAR(64)` column (`V1__auth_baseline_schema.sql:104`) — not a new risk, directly
  precedented.
- **Readability / complexity:** both entities and repositories are thin, single-purpose mappings;
  no unnecessary abstraction or complexity.
