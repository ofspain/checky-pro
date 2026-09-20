# auth · T23 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T23 — ApiKey entity/repository |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 TIB. Findings only.

---

## 1. L7's public prefix length (32 characters) does not fit the existing `prefix VARCHAR(16)` column

- **Issue.** L7 defines the API-key lookup handle as `ck_live_` + 24-character alphanumeric suffix = 32 characters. The DDL's `prefix` column is `VARCHAR(16)`. A future task (T24) generating an L7-compliant key will fail at insert time with a `value too long for type character varying(16)` error. This is a genuine blocker for any task that actually creates keys, even though T23 itself only maps the table.
- **Severity.** High — it makes the LOCKED decision L7 physically unrealizable on the current schema without either changing L7 or adding a migration.
- **Evidence.** `spec/auth-service/design.md` §4a L7; `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql` line 86 (`prefix VARCHAR(16) NOT NULL`).
- **Recommended brief amendment.** Explicitly require Phase 4 to resolve this conflict before any downstream task (T24+) is approved, and list the two options: (a) amend L7 so the suffix is ≤8 characters, yielding a 16-character prefix that fits the existing column; or (b) accept a `V7__widen_api_key_prefix.sql` migration to widen `prefix` to `VARCHAR(32)`, noting that this deviates from T23's "map the existing table as-is" scope but is necessary for L7 compliance. Do not let the conflict remain unresolved in the frozen brief.

---

## 2. The `keyHash` CHAR(64) column needs an explicit Hibernate type-code mapping

- **Issue.** The DDL declares `key_hash CHAR(64)`, but the TIB only says `keyHash (String, not null, CHAR(64))`. Without `@JdbcTypeCode(SqlTypes.CHAR)` (and likely `columnDefinition = "char(64)"`), Hibernate 6 with `ddl-auto=validate` will report a type mismatch because Postgres exposes the column as `bpchar`, not `varchar`. This same issue was already solved for `RecoveryCode.codeHash`, `VerificationToken.tokenHash`, `RefreshTokenFamily.currentTokenHash`, and `RefreshTokenArchiveEntry.tokenHash`.
- **Severity.** Medium — the test will fail on schema validation before it can prove anything else.
- **Evidence.** `RecoveryCode.java:37-41` uses `@JdbcTypeCode(SqlTypes.CHAR)` + `columnDefinition = "char(64)"`; `services/auth/src/main/resources/application.properties` line 30 sets `spring.jpa.hibernate.ddl-auto=validate`.
- **Recommended brief amendment.** Add explicit mapping instructions for `keyHash`: `@JdbcTypeCode(SqlTypes.CHAR)` and `columnDefinition = "char(64)"`, mirroring `RecoveryCode`. Also add `unique = true` to match the DDL's `UNIQUE` constraint (and similarly `unique = true` for `keyUuid`).

---

## 3. The `scopes TEXT[]` mapping has no precedent in this codebase and needs explicit validation

- **Issue.** The TIB proposes `@JdbcTypeCode(SqlTypes.ARRAY)` on a `List<String>` field, which is the standard Hibernate 6 + Postgres approach, but no other entity in `services/auth` maps a Postgres array. With `ddl-auto=validate`, the mapping must match `TEXT[] NOT NULL DEFAULT '{}'` exactly. A slight misconfiguration (e.g., using `String[]`, omitting the not-null mapping, or using the wrong SQL type) will fail validation or insert.
- **Severity.** Medium — new mapping type, must be proven against a real DB.
- **Evidence.** `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql` line 89 (`scopes TEXT[] NOT NULL DEFAULT '{}'`); grep for `SqlTypes.ARRAY` in `services/auth/src/main/java` finds no existing usage.
- **Recommended brief amendment.** Specify the exact mapping: `@JdbcTypeCode(SqlTypes.ARRAY)` on a `List<String> scopes` field, with `@Column(nullable = false)` and default initialization to `new ArrayList<>()` in the factory. Require the persistence test to assert the array round-trips (including a non-empty list) and that the DB-reported type is `text[]`.

---

## 4. `Optional<ApiKey> findByPrefix(String prefix)` is questionable for a non-UNIQUE column

- **Issue.** `prefix` has no DB-level `UNIQUE` constraint. Returning `Optional` implies at most one row, which is only true if the generation logic guarantees uniqueness. The brief's comparison to `RecoveryCodeRepository.findByAccountIdAndCodeHash` is not equivalent: that method is scoped by `accountId` *and* `codeHash`, whereas `findByPrefix` is unscoped. If two accounts ever receive the same prefix (collision, manual insert, or a future bug), the method returns one arbitrarily, and T25's exchange logic may authenticate the wrong merchant.
- **Severity.** Medium — the assumption that prefix is globally unique is encoded in the return type but not enforced by the schema.
- **Evidence.** `V1__auth_baseline_schema.sql` lines 86, 95 (`prefix VARCHAR(16) NOT NULL`, index only, no UNIQUE); `RecoveryCodeRepository.java:24` scopes its `Optional` by account + hash.
- **Recommended brief amendment.** Either (a) change the method to `List<ApiKey> findByPrefix(String prefix)` and let T25 decide how to handle duplicates (preferred for safety), or (b) keep `Optional` but explicitly require T24 to guarantee global prefix uniqueness at creation time and add a `UNIQUE` constraint on `prefix` in a future migration. Do not leave an `Optional` return type on a column that the schema allows to duplicate.

---

## 5. The persistence test has no specified way to obtain a valid `accountId`

- **Issue.** `ApiKey` requires a non-null `accountId` FK to `accounts(id)`. The test must create or resolve an account, but `AccountService` returns `AccountResponse`, which does not expose the internal `Long` id (deliberately — internal PKs never leak). The TIB says T23 should not add a UUID→accountId resolver to `ApiKeyRepository`. This leaves the test without a clean, specified way to get a valid `accountId`.
- **Severity.** Medium — will block test implementation or force an unplanned cross-module test dependency.
- **Evidence.** `AccountService.java` returns `AccountResponse.from(saved)`; `AccountResponse.java` does not contain the internal id. `MfaPersistenceIntegrationTest.java:420-424` solves the same problem by calling `mfaEnrollmentRepository.findAccountIdByUuid(...)`, which is a cross-module repository call from the test layer.
- **Recommended brief amendment.** Explicitly state how the test obtains `accountId`. Options: (a) reuse `MfaEnrollmentRepository.findAccountIdByUuid` from the test (acknowledging it as a test-only cross-module dependency), (b) use a native query via `EntityManager`, or (c) add a package-private test helper in `account` that exposes the id. Pick one and document it.

---

## 6. The entity's mutable/immutable columns are not specified

- **Issue.** The TIB lists every column but does not say which fields should be `updatable = false`. Following `MfaEnrollment`'s pattern, identity columns (`id`, `keyUuid`, `accountId`, `prefix`, `keyHash`, `createdAt`) should be immutable, while `name`, `lastUsedAt`, `expiresAt`, and `revokedAt` may be updated by later tasks. Without explicit guidance, an implementer might mark everything updatable or everything immutable, causing either accidental mutability of identity data or blocking legitimate future updates.
- **Severity.** Low-Medium.
- **Evidence.** `MfaEnrollment.java:30-47` marks `accountId`, `type`, `secretEncrypted`, and `createdAt` as `updatable = false`; `confirmedAt` and `lastUsedAt` are mutable.
- **Recommended brief amendment.** Specify `updatable = false` for `id`, `keyUuid`, `accountId`, `prefix`, `keyHash`, and `createdAt`; leave `name`, `lastUsedAt`, `expiresAt`, and `revokedAt` mutable. This matches the codebase convention and avoids future T24-T26 surprises.

---

## 7. No guidance on whether `ApiKey` should expose mutators for `lastUsedAt`/`revokedAt`

- **Issue.** T25 needs to update `lastUsedAt`; T26 needs to update `revokedAt` (and possibly `name`). The TIB says T23 only maps the table and does not implement behavior. However, without even simple guarded mutators, T25/T26 will need to add them. This is a scope boundary, but the brief should acknowledge it so later tasks don't assume mutators already exist.
- **Severity.** Low.
- **Evidence.** TIB §16 explicitly excludes "Key generation, hashing, creation flow (T24)" and "Exchange endpoint / JWT issuance (T25)."
- **Recommended brief amendment.** Add a note: "T23 provides only the mapping and factory; simple mutators for `lastUsedAt`, `revokedAt`, and `name` are intentionally left for T24-T26 to add when those behaviors are implemented."

---

## 8. The persistence test should disable breach-check and handle transactions like `MfaPersistenceIntegrationTest`

- **Issue.** The TIB says the test should match `MfaPersistenceIntegrationTest`'s convention but does not repeat the two hard-won details that make that class work: (1) `@TestPropertySource(properties = "themistra.auth.password.breach-check.enabled=false")` is needed because `AccountService.register` calls the HIBP API, which is unreachable in Testcontainers and whose fail-open audit path triggers an FK violation before the account is committed; (2) custom `@Modifying` and derived-delete repository calls need an explicit transaction wrapper.
- **Severity.** Low-Medium — omission will cause test failures unrelated to the entity mapping.
- **Evidence.** `MfaPersistenceIntegrationTest.java:56-59` and `:79-85` document both points in detail.
- **Recommended brief amendment.** Require `ApiKeyPersistenceIntegrationTest` to include the breach-check-disabled property source. Since T23's repository has no `@Modifying` methods, the transaction wrapper may not be needed, but the brief should state that plain `save`/`find` operations inherit `SimpleJpaRepository`'s transaction behavior and do not need a wrapper.

---

## 9. `created_at`'s DB default is hidden from the Java contract

- **Issue.** The DDL gives `created_at` a `DEFAULT now()`, but the TIB requires `createdAt` as a mandatory factory argument (`Objects.requireNonNull`). This is the right choice (timestamps from the injected `Clock`, no `@PrePersist`), but it means the DB default is never used. This is consistent with the rest of the codebase, but worth noting so no one later tries to rely on the default.
- **Severity.** Low.
- **Evidence.** `MfaEnrollment.java:46-47` and `RecoveryCode.java:46-47` both require `createdAt` as a factory argument despite DB defaults.
- **Recommended brief amendment.** Add a one-line note: "`createdAt` is always supplied by the caller from the injected `Clock`; the DB default exists only as a safety net and is not used by application code."

---

## 10. No conflict found with `agents.md` or other LOCKED decisions

- L12 is correctly reflected: `accountId` must be a plain `Long` column, not a JPA relation to `Account`.
- `repositories_are_never_public` (`ArchitectureTest`) is respected: the TIB requires `ApiKeyRepository` to be package-private.
- No production security logic is introduced; the entity stores only hashes and metadata, consistent with L7/L8 and `agents.md`.
- `spec/` files are not modified.

---

## Open Questions

- **L7 vs. DDL prefix width** (finding #1) — requires human decision in Phase 4; do not freeze the brief without resolving it.
- **`scopes` array mapping** (finding #3) — approach is sound but must be validated against a real Postgres instance; the persistence test is the validation mechanism.
- **Lookup-by-prefix return type** (finding #4) — the `Optional` vs. `List` choice affects T25's exchange logic and should be made explicit before freezing.

(End of artifact)
