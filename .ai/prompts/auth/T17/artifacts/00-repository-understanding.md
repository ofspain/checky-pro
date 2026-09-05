# auth · T17 — Phase 0: Repository Understanding

No code written. Read-only pass over the repository and `spec/auth-service/`'s five files.

## 1. Architecture Summary

`auth-service` is Themistra's OIDC/OAuth2 issuer (Spring Boot 3.5.4 / Java 21, Spring Authorization
Server). Package-by-feature under `com.themistra.auth.<module>`, each module owning its own
entities, repositories, services, and API; `ArchitectureTest` (ArchUnit) mechanically enforces
several module-boundary invariants (no cross-module entity imports, `authz`/`audit`/`events` never
depend on `account`, repository interfaces are never `public`, admin controllers require
`@PreAuthorize`, and — as of T16 — only `mfa.MfaSeedEncryption` may import the AWS SDK).

Persistence is PostgreSQL, one schema per service, Flyway DDL-only migrations (`V1`-`V5`,
immutable once shipped; new work is a new `V<n>__...` file — T17 needs none, both target tables
already exist in `V1__auth_baseline_schema.sql`). JPA is used for simple find/save; native queries
for anything joining across the account-UUID boundary without importing the `Account` entity
class (see §3). Internal PKs are `bigint identity`; the account UUID (JWT `sub`) is the only
identifier ever exposed externally — internal ids never leak into tokens or APIs.

Events flow through an outbox in the same transaction as the write; not relevant to this task —
T17 is pure entity/repository mapping, no state changes worth publishing on their own (task 18's
`MfaService` will be the one deciding what, if anything, gets audited/published when an enrollment
is created/confirmed).

Security: zero-trust resource-server posture, RFC 9457 problem responses, enumeration-safety
conventions — not exercised by this task either (no controller, no endpoint; that's task 19).

## 2. Existing Code This Task Touches

- **`mfa_enrollments` and `recovery_codes` tables** already exist, unchanged since
  `V1__auth_baseline_schema.sql:24-42`:
  - `mfa_enrollments(id BIGINT IDENTITY PK, account_id BIGINT FK→accounts.id ON DELETE CASCADE,
    type VARCHAR(16) DEFAULT 'TOTP', secret_encrypted BYTEA NOT NULL, confirmed_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ, created_at TIMESTAMPTZ DEFAULT now(), UNIQUE(account_id, type))`.
  - `recovery_codes(id BIGINT IDENTITY PK, account_id BIGINT FK→accounts.id ON DELETE CASCADE,
    code_hash CHAR(64) NOT NULL, used_at TIMESTAMPTZ, created_at TIMESTAMPTZ DEFAULT now())`,
    plus `idx_recovery_codes_account`.
  - Neither table has its own external UUID — no `ApiKey`-style `key_uuid` column. Rows are
    always addressed via `account_id`/`account_uuid`, never their own surrogate id, in any spec
    text I found.
- **`com.themistra.auth.mfa` package** currently holds only `package-info.java` (states the
  ArchUnit boundary rule: "modules expose services, never entities") plus T16's four classes
  (`TotpGenerator`, `MfaSeedEncryption`, `MfaProperties`, `MfaEncryptionException`) — all
  unaffected by this task; T17 adds to the same package, doesn't touch them.
- **`accounts` table / `Account` entity** — read-only context. T17 needs `account_id` (the
  internal `bigint`) to populate the FK column, resolved from the account UUID the same way
  `LockoutStateRepository` does it (native query, no Java-level import of `Account` — see below).
  L12 forbids importing another module's entity class.

## 3. Established Patterns to Follow

- **FK-to-account entity shape**, exact precedent in `authn/LockoutState.java` +
  `authn/LockoutStateRepository.java` (T11): the entity's account-linking column is a plain
  `Long`/`@Column`, never a JPA `@ManyToOne` to `Account` (that would be a cross-module entity
  import, forbidden by L12 and `ArchitectureTest`). The repository is `interface
  <Name>Repository extends JpaRepository<Entity, Long>`, **package-private** (no `public`
  modifier — mirrors `LockoutStateRepository`, `AccountRepository`'s own convention, and is
  itself an ArchUnit rule: `repositories_are_never_public`). UUID→internal-id resolution and any
  query needing to reach the `accounts` table happens via `@Query(nativeQuery = true)` joining
  `accounts` by `account_uuid`, never via a JPQL path through an imported `Account` entity.
- **Entity conventions**: `@Entity` + `@Table(name = "...")` mapping the real table name exactly;
  `protected NoArgsConstructor() { }` for JPA; a package-private or narrowly-scoped static factory
  method for constructing new instances (`LockoutState.of(...)`); getters, no setters where the
  domain doesn't need mutation from outside; `java.time.Instant` for all timestamp columns
  (`agents.md`: "No `java.util.Date`; use `java.time` with a `Clock`" — the `Clock` itself is a
  service-layer concern for *setting* these values, not the entity mapping in this task).
- **Class-doc convention**: every entity/repository in this codebase documents which exact
  migration line(s) it maps (e.g. `` `lockout_state` exactly (`V1__auth_baseline_schema.sql:114-120`) ``)
  and states its module-boundary reasoning inline, not just in `agents.md`.
- **`design.md` §6's file tree** lists `mfa/MfaEnrollment.java` (entity) and
  `MfaEnrollmentRepository.java` explicitly, but names no `RecoveryCode.java`/
  `RecoveryCodeRepository.java` (only a later `RecoveryCodeService.java`). The task 17 statement
  itself is unambiguous — "Map the existing `mfa_enrollments` **and** `recovery_codes` tables" —
  so both tables need an entity+repository pair regardless of the file tree's omission. T16 already
  established that this same file tree lags behind more authoritative sources (it still says
  `TotpSeedEncryption` where every LOCKED-decision source says `MfaSeedEncryption`); treating the
  verbatim task statement as authoritative over the illustrative tree is consistent with that
  precedent, not a new judgment call.
- **"One confirmed enrollment per account"** (task 17's own second sentence) is already partially
  enforced at the schema level: `UNIQUE(account_id, type)` on `mfa_enrollments` means there can
  only ever be one row of a given `type` per account, confirmed or not — not specifically "one
  *confirmed*" as distinct from "one row total." Whether this task needs an additional
  repository-level query (e.g. `findConfirmedByAccountId`) to make the business rule
  *checkable* by task 18, or whether the existing unique constraint is judged sufficient on its
  own, is a Phase 1/2 question, not resolved here.

## 4. Testing Conventions

- Unit tests: plain JUnit, no Spring context, mock the repository (`LockoutServiceTest` is the
  precedent for the *service* layer above a repository like this one — not directly applicable
  to T17 itself, since T17 has no service, only entity/repository).
- Integration tests: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, real
  Postgres, asserting native queries and UUID-to-internal-id resolution work against the actual
  schema (`LockoutPersistenceIntegrationTest` is the exact precedent for what a
  `MfaEnrollmentRepository`/`RecoveryCodeRepository` integration test would look like).
- ArchUnit: any new repository interface must stay package-private (existing rule catches this
  automatically); no new AWS-SDK or cross-module-entity rule is implicated by this task.

## 5. Known Gaps / Unknowns

- **Testcontainers/Docker is still unavailable in this sandbox**
  ([[docker-testcontainers-handshake-issue]], first diagnosed at T15, still blocking as of T16).
  Any integration test T17 would want (proving the repository's native queries work against real
  Postgres) cannot execute here — same constraint T16 hit for its KMS mocking decision. How this
  gets handled (unit-level proof only vs. some other verification) is a Phase 2/4 scope call, not
  decided in this phase.
- **`package.md` §8 numbering.** Same pre-existing, already-flagged (at T16 Phase 4) bug recurs
  here: `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` is mapped to R20 in
  `package.md`, but `requirements.md`'s actual R20 is the admin-unlock requirement — R23 is the
  semantically correct match (and what this task's own header already uses). Not fixed (spec
  files off-limits, out of scope for this task), noted for the same follow-up already flagged
  once.
- **Recovery code hashing primitive.** `design.md` §4b O5 ("Recovery-code hashing primitive")
  defaults to SHA-256 unless changed — `code_hash CHAR(64)` in the schema is sized for a SHA-256
  hex digest, consistent with that default. Not this task's concern to implement (no hashing
  logic in an entity/repository), but worth noting the schema already assumes SHA-256, not a
  bcrypt-shaped column.
- I do not know whether task 17 is expected to add any repository method beyond basic
  `JpaRepository` CRUD plus the UUID-resolution pattern — the task statement's "enforce one
  confirmed enrollment per account" phrase could mean anything from "the existing unique
  constraint already does this, done" to "add a dedicated finder method." Left for Phase 1/2.
