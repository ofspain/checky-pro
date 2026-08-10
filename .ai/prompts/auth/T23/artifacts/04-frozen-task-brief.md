> **STATUS: FROZEN.** Human sign-off given 2026-08-10 ("go ahead") on the resolution set below, presented alongside the Phase 3 findings. Downstream phases may not renegotiate this brief.

# auth · T23 · Phase 4 — Frozen Task Brief

## Disposition of Phase 3 (Kimi) findings

**1. L7's 32-character public prefix does not fit `prefix VARCHAR(16)`.**
**Disposition: DEFERRED — not a blocker for T23, owner is T24.**
T23 maps the `prefix` column as it actually exists (`VARCHAR(16)`) and generates no keys, so this does not block the entity/repository mapping. It is a real, high-severity blocker for whichever task next generates an L7-compliant key. Recorded here with both options preserved for T24's Phase 1/2 to pick up: (a) amend L7 so the suffix is ≤8 characters (fits the existing column), or (b) accept a `V7` migration widening `prefix` to `VARCHAR(32)`. No default is chosen now — this is T24's decision to make with its own Phase 4 gate.

**2. `keyHash` needs explicit Hibernate type-code mapping.**
**Disposition: ACCEPTED.** `keyHash` gets `@JdbcTypeCode(SqlTypes.CHAR)` + `columnDefinition = "char(64)"`, mirroring `RecoveryCode.codeHash`/`VerificationToken.tokenHash`. Both `keyHash` and `keyUuid` get `unique = true`, matching the DDL's `UNIQUE` constraints.

**3. `scopes TEXT[]` mapping needs explicit validation.**
**Disposition: ACCEPTED.** `@JdbcTypeCode(SqlTypes.ARRAY)` on `List<String> scopes`, `@Column(nullable = false)`, defaulted to a fresh `ArrayList<>()` in the factory when not supplied. `ApiKeyPersistenceIntegrationTest` must assert a non-empty list round-trips correctly — this is the only real proof, since no other entity in this schema maps a Postgres array.

**4. `Optional<ApiKey> findByPrefix` overclaims uniqueness the schema doesn't enforce.**
**Disposition: ACCEPTED.** Changed to `List<ApiKey> findByPrefix(String prefix)`. `prefix` has no DB-level `UNIQUE` constraint, unlike `key_hash`; the brief's original comparison to `RecoveryCodeRepository.findByAccountIdAndCodeHash` (scoped by two columns, not one) didn't hold. T25's exchange logic inherits the responsibility of handling more than one match, if that ever occurs.

**5. No specified way for the persistence test to obtain a valid `accountId`.**
**Disposition: ACCEPTED, with a correction to the recommended fix.** Kimi's suggested option — reusing `MfaEnrollmentRepository.findAccountIdByUuid` from the `apikey` test package — does not compile: that method is package-private to `com.themistra.auth.mfa`, and Java package-private visibility is per-package regardless of module or source root (main vs. test). The actual resolution: `ApiKeyPersistenceIntegrationTest` inserts its own minimal `accounts` row directly via `JdbcTemplate` (a plain `INSERT ... RETURNING id`) and captures the generated id. This is fully self-contained in the `apikey` test package, adds no cross-module dependency, and needs no production code change.

**6. Immutable/mutable columns unspecified.**
**Disposition: ACCEPTED.** `id`, `keyUuid`, `accountId`, `prefix`, `keyHash`, and `createdAt` are `updatable = false`, matching `MfaEnrollment`'s convention. `name`, `lastUsedAt`, `expiresAt`, `revokedAt` remain mutable for later tasks.

**7. No guidance on mutators for later tasks.**
**Disposition: ACCEPTED — documentation only.** Brief now states explicitly: T23 provides only the mapping and factory; simple mutators for `lastUsedAt`, `revokedAt`, and `name` are intentionally left for T24–T26 to add when those behaviors are implemented.

**8. Persistence test needs the breach-check-disabled property source.**
**Disposition: SUPERSEDED by the fix to finding #5.** Since the test no longer calls `AccountService.register` (it inserts the `accounts` row directly via `JdbcTemplate`), the HIBP breach-check path is never invoked and the property source is unnecessary. No action needed.

**9. `created_at`'s DB default is unused by application code.**
**Disposition: ACCEPTED — documentation only.** Brief now states: `createdAt` is always supplied by the caller from the injected `Clock`; the DB default exists only as a safety net and is not used by application code.

**10. No `agents.md`/LOCKED-decision conflicts.**
**Disposition: Confirmed, no action needed** (non-issue).

No findings rejected.

---

## Final brief (supersedes Phase 2 TIB on every point above; unchanged elsewhere)

### Task
Add a JPA entity mapping the existing `api_keys` table exactly as it stands in `V1__auth_baseline_schema.sql`, plus a package-private repository with a lookup-by-prefix method.

### Scope
**In:** `ApiKey` entity, `ApiKeyRepository`, `List<ApiKey> findByPrefix(String prefix)`, `ApiKeyPersistenceIntegrationTest`.
**Out:** key generation/hashing/creation (T24), exchange/JWT issuance (T25), CRUD controller (T26), any Flyway migration, any UUID→accountId resolver method on the repository itself, any mutators beyond the factory.

### Business Rules
- **R30** (data-shape half only) — entity must represent the row R30 describes; creation/gating/audit behavior is T24's.

### Locked Decisions
- **L7** — public prefix format governs column semantics; the width conflict with `VARCHAR(16)` is real but explicitly deferred to T24 (see disposition #1).
- **L12** — `accountId` is a plain `Long` column, never a JPA relation to `Account`.

### Files to Create
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java`
  - `id: Long` — `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`.
  - `keyUuid: UUID` — not null, `unique = true`, `updatable = false`.
  - `accountId: Long` — not null, `updatable = false`. Plain column, no `@ManyToOne`.
  - `prefix: String` — not null, `updatable = false`, maps `VARCHAR(16)` as-is.
  - `keyHash: String` — not null, `unique = true`, `updatable = false`, `@JdbcTypeCode(SqlTypes.CHAR)`, `columnDefinition = "char(64)"`.
  - `name: String` — not null, mutable, `VARCHAR(100)`.
  - `scopes: List<String>` — not null, mutable, `@JdbcTypeCode(SqlTypes.ARRAY)`, defaults to `new ArrayList<>()`.
  - `lastUsedAt, expiresAt, revokedAt: Instant` — nullable, mutable.
  - `createdAt: Instant` — not null, `updatable = false`; always caller-supplied from the injected `Clock`, never relies on the DB's `DEFAULT now()`.
  - Protected no-arg constructor for JPA. Static `create(Long accountId, String prefix, String keyHash, String name, List<String> scopes, Instant createdAt)` factory: `Objects.requireNonNull` on `accountId`, `prefix`, `keyHash`, `name`, `createdAt`; `scopes` defaults to an empty `ArrayList` if null; generates its own `keyUuid` via `UUID.randomUUID()`.
  - Note for future tasks: mutators for `lastUsedAt`, `revokedAt`, `name` are intentionally not provided by T23 — T24/T25/T26 add them when they implement the behaviors that need them.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyRepository.java` — package-private, `extends JpaRepository<ApiKey, Long>`, one method: `List<ApiKey> findByPrefix(String prefix)`.
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyPersistenceIntegrationTest.java` — Testcontainers (Postgres + Kafka, matching this codebase's standard base test class). Obtains a valid `accountId` via a direct `JdbcTemplate` insert into `accounts` (minimal required columns, `RETURNING id`) — no `AccountService`/`AccountRepository` dependency, no breach-check property needed. Must:
  - `saveAndFlush` an `ApiKey` with every field populated (including non-empty `scopes`) and assert every column round-trips correctly, including the array column's DB-reported type.
  - Assert the three nullable timestamp columns persist correctly both null and set.
  - Exercise `findByPrefix`: found and not-found cases.

### Files to Modify
None.

### Files NOT to Modify
- Any file outside `apikey/`.
- `services/auth/src/main/resources/db/migration/` — no new migration.
- `spec/`.

### Acceptance Criteria
| ID | Criterion |
|---|---|
| AC1 (R30 data-shape) | `ApiKey` maps every `api_keys` column with correct type/nullability/uniqueness; no column added, renamed, or dropped. |
| AC2 (L12) | `accountId` is a plain column; `ArchitectureTest` passes unmodified. |
| AC3 (task statement) | `ApiKeyRepository.findByPrefix` exists and returns `List<ApiKey>`. |
| AC4 (implicit) | `ApiKeyRepository` is package-private; `ArchitectureTest.repositories_are_never_public` passes. |
| AC5 (Phase 3 #3) | The `scopes` array mapping round-trips correctly against a real Postgres `text[]` column, proven by the persistence test. |

### Required Tests
`ApiKeyPersistenceIntegrationTest`, as specified under Files to Create.

### Constraints
Unchanged from Phase 2 TIB: no security logic, no new transactional behavior beyond default JPA save semantics, no concurrency concerns (no `@Modifying` methods in this task), L12 module boundary enforced, `Objects.requireNonNull` on every required factory argument.

### Open Questions
- **L7 vs. `prefix VARCHAR(16)`** — deferred to T24 with both resolution options recorded (see disposition #1). Not a T23 blocker.

(End of frozen brief)
