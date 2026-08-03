# auth · T17 · Phase 3 — Design Challenge Findings

Adversarial review of `artifacts/02-task-implementation-brief.md`. Findings only; no redesign or implementation.

---

### 1. The task statement says "enforce one confirmed enrollment"; the brief scopes enforcement out to task 18

- **Severity:** High
- **Evidence:**
  - `spec/auth-service/tasks.md` §17: "**MfaEnrollment entity/repository.** Map the existing `mfa_enrollments` and `recovery_codes` tables. **Enforce one confirmed enrollment per account.**"
  - TIB §Scope: "A service-level 'reject a second enrollment attempt' check is task 18's responsibility; T17 only makes that check *queryable*."
  - TIB §AC5 frames the repository method as making the check queryable, not enforcing it.
- **Recommended brief amendment:**
  - Either keep T17 mapping-only and explicitly state that the brief is *narrowing* the task-statement wording (with author consent), or add an enforcement mechanism to T17 such as a unique partial index on `(account_id, type) WHERE confirmed_at IS NOT NULL` plus a matching repository query.
  - Clarify whether an *unconfirmed* enrollment should also block creation of a second enrollment (the schema's `UNIQUE(account_id, type)` already blocks a second row of any confirmation state, which effectively enforces one enrollment total — not specifically one *confirmed* enrollment).

---

### 2. No mutators are specified for `MfaEnrollment` despite AC2 requiring an in-place confirmation update

- **Severity:** Medium
- **Evidence:**
  - TIB §Scope describes only a static factory for `MfaEnrollment` (`accountId`, `secretEncrypted`, `createdAt`).
  - AC2 requires that an `MfaEnrollment` row "can be updated in place to set `confirmedAt`".
  - The entity fields are described as private; without an explicit `confirm(Instant)` or `setConfirmedAt(...)` method, the update is unrepresentable.
- **Recommended brief amendment:**
  - Add a requirement for a package-private/public mutator such as `confirm(Instant confirmedAt)` and, if applicable, `recordUse(Instant lastUsedAt)`.
  - State whether `confirmedAt` may be set only once (idempotent confirm) or whether re-confirming should be allowed.

---

### 3. `RecoveryCode.usedAt` update pattern is unspecified and risks a task-18 race condition

- **Severity:** Medium
- **Evidence:**
  - `VerificationToken` deliberately has *no* `usedAt` mutator because redemption happens through an atomic conditional update in the repository (its Javadoc explains this exact race concern).
  - TIB does not mention any equivalent pattern for `RecoveryCode.usedAt`. It only says the field is nullable and maps the column.
  - R23/R28 imply single-use recovery-code semantics; a load→set→save cycle would be unsafe under concurrent use.
- **Recommended brief amendment:**
  - Specify that `RecoveryCode` follows the `VerificationToken` pattern: no `usedAt` mutator on the entity; task 18 will mark a code as used via an atomic repository update (e.g., `UPDATE recovery_codes SET used_at = ... WHERE id = ... AND used_at IS NULL`).
  - If a mutator is intended, explicitly state it and that task 18 must use pessimistic locking or the atomic update pattern.

---

### 4. "Mirrors `Account`'s constructor pattern" is the wrong precedent for caller-supplied timestamps

- **Severity:** Low
- **Evidence:**
  - TIB §Scope says `createdAt` is "set at construction from a caller-supplied value, not `Instant.now()` — mirrors `Account`'s constructor pattern".
  - `Account` does **not** take a caller-supplied timestamp; it uses `@PrePersist` to set `createdAt`/`updatedAt` to `Instant.now()`.
  - The actual precedent in this codebase for caller-supplied timestamps is `VerificationToken.create(...)`, whose Javadoc explicitly rejects `@PrePersist`.
- **Recommended brief amendment:**
  - Replace the `Account` reference with `VerificationToken`'s timestamp pattern and cite the injected `Clock` convention.

---

### 5. Recovery codes are tied only to `account_id`, not to a specific enrollment

- **Severity:** Medium
- **Evidence:**
  - `recovery_codes` has no `mfa_enrollment_id` foreign key; the brief correctly maps only `account_id`.
  - R28 requires that disabling MFA "invalidate all recovery codes". With no enrollment link, invalidation must delete *all* codes for the account.
  - The brief does not state this implication, so task 18 may assume codes are scoped to the current enrollment.
- **Recommended brief amendment:**
  - Add a note that recovery-code invalidation on MFA disable is an account-level `DELETE FROM recovery_codes WHERE account_id = ...` because the schema does not link codes to a specific enrollment.

---

### 6. JPA annotation details are underspecified for an "exact" schema mapping

- **Severity:** Low
- **Evidence:**
  - TIB says entities must map the tables "exactly" but does not list the `@Column` attributes needed to match the DDL (e.g., `length = 16` for `type`, `length = 64` for `code_hash`, `nullable = false`/`updatable = false` for immutable columns, `columnDefinition` for `BYTEA`).
  - Hibernate defaults for `byte[]` in PostgreSQL map to `bytea`, and `String` defaults to `varchar`; but without explicit annotations, AC3/AC4 cannot be mechanically verified by inspecting the entity.
- **Recommended brief amendment:**
  - Include a short annotation checklist or explicitly defer exact annotation shape to the implementation notes with the requirement that generated DDL matches the existing V1 schema under `hbm2ddl` validation.

---

### 7. No strategy is given for testing JPA mappings without Testcontainers

- **Severity:** Medium
- **Evidence:**
  - TIB §Open Questions carries over T16's Testcontainers limitation but says only "whatever level Phase 4 confirms".
  - `agents.md` expects integration tests with Testcontainers, but the sandbox cannot run them ([[docker-testcontainers-handshake-issue]]).
  - Plain JUnit cannot actually persist and read an entity, so AC1–AC3 (which are about persistence behavior) cannot be meaningfully verified without at least an in-memory DB or annotation/reflection checks.
- **Recommended brief amendment:**
  - Choose and state the test approach: (a) reflection-based annotation verification plus constructor/factory tests if no DB is available, or (b) `@DataJpaTest` with an embedded database, or (c) Testcontainers if the issue is resolved.
  - If using `@DataJpaTest`, address how the `findAccountIdByUuid` native query against `accounts` will be tested without importing or mocking the `Account` entity.

---

### 8. `type` is a plain `String` despite being a closed set in practice

- **Severity:** Low
- **Evidence:**
  - The schema has `type VARCHAR(16) NOT NULL DEFAULT 'TOTP'` with a comment that it "admits WEBAUTHN later"; there is no `CHECK` constraint on the column.
  - TIB maps `type` as a `String` defaulting to `"TOTP"`.
  - A Java `enum` would prevent invalid values and make `findByAccountIdAndType` callers type-safe.
- **Recommended brief amendment:**
  - Either introduce an `MfaType` enum (with a single `TOTP` value for now) and `@Enumerated(EnumType.STRING)`, or explicitly state that `String` is intentional to avoid a schema change when WEBAUTHN is added later.

---

### 9. No repository method to find *unused* recovery codes

- **Severity:** Low
- **Evidence:**
  - `RecoveryCodeRepository` only exposes `findByAccountId(Long)`.
  - Task 18's recovery-code verification flow will almost certainly need only codes with `usedAt IS NULL`.
  - Adding it now is natural scope for a repository (the column already exists and is nullable).
- **Recommended brief amendment:**
  - Add `findByAccountIdAndUsedAtIsNull(Long accountId)` to `RecoveryCodeRepository`, or leave it for task 18 but note the deliberate omission.

---

### 10. No locking/resolver variant is specified for the confirm/recovery-code redemption flows

- **Severity:** Low
- **Evidence:**
  - `LockoutStateRepository` provides both a plain read (`findByAccountUuid`) and a row-locking read (`findByAccountUuidForUpdate`) because concurrent login attempts create a real race.
  - T17's `MfaEnrollmentRepository` provides only `findByAccountIdAndType`. Task 18's confirm flow and MFA-verify flow may need `FOR UPDATE` semantics when updating `confirmedAt`/`lastUsedAt` or when consuming a recovery code.
- **Recommended brief amendment:**
  - Either add a `findByAccountIdAndTypeForUpdate(Long, String)` native-query method now, or document that task 18 is responsible for adding any locking query it needs.

---

## Summary

The brief correctly keeps T17 as a mapping-only task and respects L12, but it silently narrows the task-statement word "enforce" and leaves several method-level contracts (mutators, `usedAt` update strategy, recovery-code invalidation) implicit. The highest-priority fixes are (1) resolving the "enforce" wording, (2) specifying the `MfaEnrollment` confirmation mutator, and (3) documenting the recovery-code single-use update pattern.
