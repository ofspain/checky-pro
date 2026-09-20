# auth · T23 — Task Implementation Brief (TIB)

## Task
Add a JPA entity mapping the existing `api_keys` table exactly as it stands in `V1__auth_baseline_schema.sql`, plus a package-private repository with a lookup-by-prefix method.

## Purpose
`api_keys` has existed in the schema since V1, but the `apikey` package is empty (only `package-info.java`). Every later API-key task (T24 create, T25 exchange, T26 CRUD, T27 integration tests) needs a persistable `ApiKey` aggregate and a repository to build on; this task creates that foundation without implementing any of the behavior those later tasks own.

## Scope

**In:**
- `ApiKey` entity mapping every column of `api_keys` (V1) with correct nullability/types.
- `ApiKeyRepository`, package-private, extending `JpaRepository<ApiKey, Long>`.
- A lookup-by-prefix method on the repository (the task statement's only named requirement beyond the mapping itself).

**Out:**
- Key generation, hashing, creation flow (T24).
- Exchange endpoint / JWT issuance (T25).
- CRUD controller, list/revoke (T26).
- Any Flyway migration — the table already exists; T23 maps it as-is.
- Any UUID→internal-`accountId` resolver method — nothing in this task's scope calls the repository yet; adding one now would be speculative (no caller, no test to justify the exact shape). T24 adds it when it actually needs it.

## Business Rules
- **R30** (data-shape half only). The entity must be able to represent the row R30 describes: `prefix = ck_live_`, a SHA-256 `key_hash`, a `name`, and the metadata columns already in the DDL. T23 does not implement R30's creation flow, gating, or audit behavior.

## Locked Decisions
- **L7. API key format.** `ck_live_` + a random 24-character alphanumeric suffix as the public "lookup handle" (32 characters total), `.` + a 32-character secret for the full key; only a SHA-256 hash is stored. **See Open Questions — this conflicts with the existing `prefix VARCHAR(16)` column and is a real blocker for whichever task next generates a compliant key, though not for T23's mapping work itself.**
- **L12. Module boundaries.** `ApiKey.accountId` must be a plain `Long` column, never a JPA relation to `Account` — CI-enforced by `ArchitectureTest.only_the_account_module_may_touch_the_Account_entity`.

## Dependencies
- `api_keys` table (`V1__auth_baseline_schema.sql:82-96`), including `idx_api_keys_prefix` and `idx_api_keys_account`.
- `accounts` table via the `account_id` FK — table only, not the `Account` Java entity (L12).
- Spring Data JPA / Hibernate 6.6.22 — same stack as every other repository.

## Inputs
None — this task adds no service-layer behavior that accepts external input.

## Outputs
None — no controller, no response shape. The entity and repository are internal building blocks consumed by later tasks.

## State Changes
None. No migration. No behavior that writes rows — `save`/`saveAndFlush` exist only because `JpaRepository` provides them; nothing in T23 calls them outside its own persistence test.

## Files to Create
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java` — entity, one field per DDL column: `id` (`Long`, identity PK), `keyUuid` (`UUID`, not null), `accountId` (`Long`, not null), `prefix` (`String`, not null, matches `VARCHAR(16)`), `keyHash` (`String`, not null, `CHAR(64)`), `name` (`String`, not null, `VARCHAR(100)`), `scopes` (mapped Postgres `TEXT[]` — see Open Questions for the mapping approach), `lastUsedAt`/`expiresAt`/`revokedAt` (nullable `Instant`), `createdAt` (not-null `Instant`). Protected no-arg constructor for JPA; static `create(...)` factory null-checking required arguments and defaulting `scopes` to an empty collection, following `MfaEnrollment.create`'s shape.
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyPersistenceIntegrationTest.java` — Testcontainers persistence test (see Required Tests).

## Files to Modify
None.

## Files NOT to Modify
- Any file outside `apikey/` — no other module's code is touched by this task.
- `services/auth/src/main/resources/db/migration/` — no new migration; the table is mapped as-is per the task statement ("Map the existing `api_keys` table").
- `spec/` — never modified.

## Acceptance Criteria
| ID | Criterion |
|---|---|
| AC1 (R30 data-shape) | `ApiKey` maps every `api_keys` column with correct type/nullability; no column added, renamed, or dropped. |
| AC2 (L12) | `ApiKey.accountId` is a plain column, not a JPA relation to `Account`; `ArchitectureTest` passes unmodified. |
| AC3 (task statement) | `ApiKeyRepository` has a method to look up an `ApiKey` by `prefix`. |
| AC4 (implicit) | `ApiKeyRepository` is package-private; `ArchitectureTest.repositories_are_never_public` passes. |

## Required Tests
- `ApiKeyPersistenceIntegrationTest` (new, Testcontainers, matching `MfaPersistenceIntegrationTest`'s convention):
  - `saveAndFlush`s an `ApiKey` with all fields populated (including a non-empty `scopes` list) and asserts every column round-trips correctly on reload — this is the test that actually proves whichever `scopes` mapping approach is chosen (Open Questions) works against the real column, the same way T22's citext regression test proved a mapping choice against the real DB rather than an assumption.
  - Asserts the four nullable timestamp columns (`lastUsedAt`, `expiresAt`, `revokedAt`, plus the always-set `createdAt`) persist correctly both null and set.
  - Exercises the lookup-by-prefix method: found case and not-found case.

## Constraints
- **Security:** none — no credential material is generated, hashed, or exposed by this task; the entity only stores whatever a later task passes it.
- **Transactional:** none beyond default JPA save semantics.
- **Thread-safety:** not applicable — no concurrent-access logic in this task (contrast with `MfaEnrollmentRepository`'s conditional `@Modifying` methods, which exist because T18/T20 had real races to close; T23 has none).
- **Module boundaries (L12):** `apikey` must not import `com.themistra.auth.account.Account`.
- **Null handling:** `create(...)`'s required fields (`accountId`, `prefix`, `keyHash`, `name`, `createdAt`) must `Objects.requireNonNull`; `scopes` defaults to an empty, non-null collection if not supplied — no field silently accepts null where the DDL says `NOT NULL`.

## Open Questions

**New finding, not yet raised in Phase 0/1 — flagged now per the guardrail against deviating silently:**
- **L7 vs. the DDL: the public prefix does not fit the column.** L7 defines the public prefix/lookup handle as `ck_live_` + a 24-character alphanumeric suffix = **32 characters**. The DDL's `prefix` column is `VARCHAR(16)` — at most 8 characters of random suffix fit alongside the 8-character `ck_live_` literal. A future task generating an L7-compliant key would fail to insert it (Postgres raises an error on a `VARCHAR(16)` overflow; it does not silently truncate). This does not block T23 — the entity maps the column as it actually is (`VARCHAR(16)`), and T23 generates no keys — but it is a real blocker for whichever task (T24) next generates a compliant key, and needs a human decision: either L7's suffix length is wrong (should be ≤8 characters to fit the existing column), or the column needs widening via a new migration (`V7`), which would itself be a deviation from "map the existing table as-is," the same category of pre-authorized scope-widening T22's `V6` migration set a precedent for. Not resolved here — flagged for Phase 4.
- **`scopes TEXT[]` mapping approach (carried from Phase 0/1, now proposed for Phase 3 to challenge):** propose `@JdbcTypeCode(SqlTypes.ARRAY)` on a `List<String>` field, Hibernate 6's native Postgres array support — no custom converter needed, no precedent elsewhere in this schema to contradict it. This is a genuinely new mapping type for this codebase (T22's `CitextJdbcType` precedent was for a scalar column, not an array), so the persistence test above is what actually validates it, not this brief's assertion that it will work.
- **Lookup-by-prefix return shape (carried from Phase 0/1, now proposed):** `Optional<ApiKey> findByPrefix(String prefix)`. `prefix` has no DB-level `UNIQUE` constraint (unlike `key_hash`), but this codebase already has precedent for treating a non-DB-unique, randomly-generated column as functionally unique at the repository-return-type level (`RecoveryCodeRepository.findByAccountIdAndCodeHash` returns `Optional` despite `recovery_codes.code_hash` also lacking a `UNIQUE` constraint). Proposed for consistency; Phase 3 should challenge if this reasoning doesn't hold for API keys specifically (e.g., if T24's create flow needs to detect a collision pre-insert, a `List`-returning existence check might be more defensive than an `Optional` that would simply return "one of possibly several" rows).
