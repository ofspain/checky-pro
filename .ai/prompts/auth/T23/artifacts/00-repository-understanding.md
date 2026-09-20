# auth · T23 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/auth` is a Spring Boot 3.5.4 / Java 21 module, package-by-feature under `com.themistra.auth`, acting as the platform's identity issuer via Spring Authorization Server (OIDC/OAuth2). Persistence is PostgreSQL (one schema, `auth`) via JPA + Flyway (DDL-only, `V1`–`V4` immutable, `V5`/`V6` already added by prior tasks). Every state change other services care about goes through an outbox table in the same transaction as the write, published to Kafka (AWS MSK); the `events` module stays domain-agnostic on purpose (ArchUnit-enforced). Security is zero-trust resource-server JWT validation everywhere except an explicit, CI-enforced public-endpoint set (`common.PublicEndpoints`). Module boundaries — no feature module may import another module's entity, repositories are always package-private — are compiled into `ArchitectureTest` (ArchUnit), not just documented.

## 2. Existing code this task touches

- **`api_keys` table** — already exists, created in `V1__auth_baseline_schema.sql` (`services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql:82-96`):
  ```sql
  CREATE TABLE api_keys (
      id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
      key_uuid            UUID        NOT NULL UNIQUE,
      account_id          BIGINT      NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      prefix              VARCHAR(16) NOT NULL,                   -- ck_live_xxxx: lookup handle, non-secret
      key_hash            CHAR(64)    NOT NULL UNIQUE,            -- SHA-256 of full key; plaintext shown once
      name                VARCHAR(100) NOT NULL,
      scopes              TEXT[]      NOT NULL DEFAULT '{}',
      last_used_at        TIMESTAMPTZ,
      expires_at          TIMESTAMPTZ,
      revoked_at          TIMESTAMPTZ,
      created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  CREATE INDEX idx_api_keys_prefix ON api_keys(prefix);
  CREATE INDEX idx_api_keys_account ON api_keys(account_id);
  ```
  No new migration is required for T23 — the table, both indexes (including the prefix index this task's lookup method needs), and the FK to `accounts` all already exist.
- **`com.themistra.auth.apikey` package** — exists but is empty except `package-info.java`, which already documents the module's intended shape: *"Merchant API keys — hashed at rest, exchanged for standard JWTs at /api-keys/token; resource servers only ever validate JWTs... modules expose services, never entities."* T23 populates this package's entity and repository only.
- **`Account`** (`account` module) — T23 must NOT import this entity directly (ArchUnit rule below); the FK is a plain `account_id BIGINT` column, resolved from the external `account_uuid` the same way `MfaEnrollmentRepository.findAccountIdByUuid` does it (native query), if a UUID-keyed lookup is needed by whatever calls this repository later. Whether T23 itself needs that method, or only the later service task (T24) does, is noted under Known Gaps.

## 3. Established patterns to follow

- **Entity shape** (`mfa.MfaEnrollment` is the closest analog — same "credential-like row FK'd to an account, immutable creation fields, later-set nullable state fields" shape as `ApiKey`):
  - `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` on a `Long id` — internal PK, never exposed.
  - `accountId` is a plain `Long` column (`account_id`), never a JPA relation/`@ManyToOne` to `Account` — enforced by `ArchitectureTest.only_the_account_module_may_touch_the_Account_entity`.
  - A protected no-arg constructor for JPA only, plus a static factory (`ApiKey.create(...)`) that null-checks every argument via `Objects.requireNonNull` and defensively copies any mutable array-typed field.
  - Timestamps are `java.time.Instant`, always supplied by the caller (sourced from an injected `Clock` upstream) — never `Instant.now()` inline, never `@PrePersist`.
  - Mutating methods live on the entity only for non-concurrent, single-actor transitions; anything that must be race-safe under concurrent access is instead a conditional `@Modifying` repository method (see below) — `revoked_at` being set on `DELETE /api-keys/{keyUuid}` (T26, not this task) will likely follow the same "conditional update, check rows-affected" shape `RecoveryCodeRepository.markUsed` and `MfaEnrollmentRepository.confirmIfUnconfirmed` use, but that decision belongs to whichever task implements revoke, not T23.
- **Repository shape** (`mfa.MfaEnrollmentRepository` / `mfa.RecoveryCodeRepository`):
  - Interface is package-private (`interface ApiKeyRepository extends JpaRepository<ApiKey, Long>`, no `public`) — CI-enforced by `ArchitectureTest.repositories_are_never_public`.
  - Derived-query methods (`findByPrefix`, etc.) are plain Spring Data method names where a derived query suffices; a `@Query` is reserved for cases a derived name can't express (native UUID→id resolution, conditional `@Modifying` updates).
  - Javadoc on each method states which requirement/task it exists for and why its return shape (`Optional` vs `List`) or locking behavior is what it is — this codebase treats repository Javadoc as load-bearing documentation of a design decision, not boilerplate.
- **Package layout**: entity, repository, service, controller, `dto/` all flat under the feature package (`design.md` §6's package map: `apikey/ApiKey.java`, `ApiKeyRepository.java`, `ApiKeyService.java`, etc.) — T23 only adds the first two.
- **Flyway**: not needed here since the table pre-exists; if it were, new work is always a new `V<n>` file, V1–V4 immutable.

## 4. Testing conventions

- Unit tests: plain JUnit, no Spring context, fixed `Clock` (`Clock.fixed(...)`) wherever time matters — never wall-clock `Instant.now()` in a test.
- Persistence/integration tests: Testcontainers (Postgres + Kafka), following the `*PersistenceIntegrationTest` naming convention seen in `mfa.MfaPersistenceIntegrationTest` / `account.AccountPersistenceIntegrationTest` — `@Autowired` the repository directly, `saveAndFlush` real rows, assert against the real DB (this is exactly the pattern that caught the citext case-insensitivity bug in T22).
- ArchUnit (`ArchitectureTest`) runs against production code only (`ImportOption.DoNotIncludeTests.class`) and will fail the build if T23's entity/repository violate module boundaries (`Account` import) or repository visibility (`public interface`).
- `package.md` §8 has no named test for T23 — the named tests for this module (`shouldCreateApiKeyAndShowPlaintextExactlyOnce`, `shouldExchangeValidApiKeyForMerchantJwt`, `shouldRejectRevokedOrUnknownApiKeyWithUniform401`, `shouldListAndRevokeOwnApiKeys`) all belong to later tasks (T24–T27) that add behavior this task doesn't touch. T23 itself is entity/repository mapping only; per this pipeline's own established pattern (T18/T23-class "no code behavior yet" tasks), a plain repository-level persistence test (mapping correctness + the new lookup-by-prefix method) is the right-sized test for this task, to be proposed formally in Phase 5.

## 5. Known gaps / unknowns

- **Lookup-by-prefix return shape and exact method name are not specified anywhere in the spec package.** `requirements.md`'s R30 only covers key creation; the prefix-lookup requirement comes from the task statement itself ("Add lookup-by-prefix method") with no accompanying acceptance criterion. I do not know whether the exchange flow (T25) needs `Optional<ApiKey> findByPrefix(String prefix)` (single row, since `prefix` has no explicit uniqueness constraint in the DDL) or something else — the DDL only makes `key_hash` and `key_uuid` `UNIQUE`, not `prefix`. This will need a design decision in Phase 1/2, not an assumption made here.
- **Whether T23 needs a UUID→internal-id resolution method** (the `findAccountIdByUuid` pattern `MfaEnrollmentRepository` uses) is unclear — T23's own scope is entity/repository only; whatever later calls this repository to create a key (T24) will need to resolve `accountId` from the authenticated principal's UUID somehow, but whether that resolution lives in this repository or is reused from an existing shared source, I do not know and will not assume.
- **`scopes TEXT[]`** — the DDL already has a Postgres array column with no corresponding precedent elsewhere in this schema (no other table in `V1`–`V6` uses an array column). I do not know what Hibernate array-mapping approach (native array type support vs. a converter) this codebase expects; there is no existing pattern to copy. This is a design question, not a Phase 0 answer.
- Q3 in `package.md` ("Should there be a maximum number of active API keys per merchant? Is the only scope at launch `merchant.api`?") is still open in the spec itself and out of scope for T23 regardless of its answer — entity/repository mapping doesn't enforce a cap.
