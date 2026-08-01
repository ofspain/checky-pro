# auth · T12 — Phase 0: Repository Understanding

Grounding only — no design, no requirements extraction. Read: `spec/auth-service/{package.md,
requirements.md, design.md, tasks.md, agents.md}` plus the actual repository state.

## 1. Architecture summary

`services/auth` is a Spring Boot 3.5.4 / Java 21 module, package-by-feature under
`com.themistra.auth`: `account`, `authn`, `authz`, `apikey`, `token`, `audit`, `events`, `common`.
Postgres (Flyway, DDL-only, `V1`-`V5` present — V1-V4 immutable, V5 is the most recent). Every
cross-service-relevant state change goes through the `events` module's outbox in the same
transaction as the DB write (`OutboxPublisher.publish(...)` → `outbox_event` row →
`OutboxRelay` → Kafka). Security is zero-trust resource-server JWT validation; a CI-enforced
`PublicEndpoints` list is the only sanctioned unauthenticated surface. Module boundaries are
enforced at compile-plus-test time by `ArchitectureTest` (ArchUnit), not just convention.

## 2. Existing code this task touches

**Already exists, unmodified by this task's predecessor (T11):**
- `lockout_state` table (`V1__auth_baseline_schema.sql:114-120`) — `account_id BIGINT PRIMARY KEY
  REFERENCES accounts(id) ON DELETE CASCADE`, `failed_attempts INT NOT NULL DEFAULT 0`,
  `last_failed_at TIMESTAMPTZ`, `locked_until TIMESTAMPTZ`, `lock_count INT NOT NULL DEFAULT 0`.
  No JPA entity or repository for it exists yet — this table has never been touched from Java.
- `idx_lockout_state_locked_until` (`V5__lockout_cleanup_and_shedlock.sql:4-6`) — partial index
  on `locked_until IS NOT NULL`, for an efficient expired-lock scan. No code queries it yet.
- `shedlock` table (`V5`, same file) — for a future ShedLock-coordinated cleanup job (`tasks.md`
  item 30, not this task).
- `Account.lock()` / `Account.unlock()` (`Account.java:88-98`) — guarded transitions:
  `lock()` requires `status == ACTIVE`, transitions to `LOCKED`; `unlock()` requires
  `status == LOCKED`, transitions to `ACTIVE`. Both throw `InvalidAccountStateException` if the
  precondition fails. Neither touches `lockout_state` — that table has always been separate from
  the `Account`/`accounts` aggregate.
- `AccountService.resetPassword` (`AccountService.java:195-214`) already calls
  `account.unlock()` unconditionally when `status == LOCKED` (T07/T09), as part of proof-of-
  ownership recovery — but it does **not** touch `lockout_state` at all today. A password reset
  currently leaves stale `failed_attempts`/`lock_count`/`locked_until` rows behind even though the
  `Account` itself is back to `ACTIVE`. This task's own scope note ("ties `Account.lock()` /
  `unlock()` to `AccountService`") is the first opportunity to close that gap — whether it's in
  this task's scope or deferred is a Phase 1/2 question, not decided here.
- `LockoutStateMachine` (`services/auth/src/main/java/com/themistra/auth/authn/
  LockoutStateMachine.java`, T11, merged) — pure decision logic, zero dependencies beyond
  `java.time`. Constructor takes `(int maxAttempts, Duration decayWindow, Duration
  baseLockDuration)`. `evaluate(LockoutSnapshot, Instant now, LockoutAttemptOutcome)` returns a
  `LockoutDecision` (counters + `blocked` + `AccountStatusChange` of `LOCK`/`UNLOCK`/`NONE`).
  `reset()` returns an unconditional zeroed decision for admin-unlock/password-reset-unlock
  callers. This is the engine T12 wraps with persistence.

**New, this task (per `design.md` §6 package map, `authn/`):**
- `LockoutState.java` — JPA entity for `lockout_state`. No precedent class exists; closest model
  is `VerificationToken.java` (single-table, `Long accountId` FK column, no cross-module import,
  timestamps supplied by caller via injected `Clock`, never `@PrePersist`/`Instant.now()` for
  business timestamps).
- `LockoutStateRepository.java` — package-private `JpaRepository`, per `AccountRepository`'s
  precedent (`interface ... extends JpaRepository<...>`, no `public` modifier — ArchUnit-enforced,
  see §3).
- `LockoutProperties.java` — `@ConfigurationProperties` record for the three `L4` constants. No
  `themistra.auth.lockout.*` keys exist in `application.properties` yet, despite `design.md:42-44`
  proposing `max-attempts`/`window-minutes`/`base-lock-minutes`. Closest model:
  `PasswordPolicyProperties.java` (`@ConfigurationProperties` + `@Validated` record, `@Min`/`@Max`
  bounds, startup-fails-on-missing, no silent defaults).
- `LockoutService.java` — the task's namesake. No precedent of this exact shape (load/update a
  per-account row + call a pure decision engine + apply an `Account` status change) exists yet in
  this codebase.

## 3. Established patterns to follow

- **Persistence:** JPA entities are package-private-repository, public-service-only-access
  (`AccountRepository`, `VerificationTokenRepository` both `interface ... extends
  JpaRepository`, no `public`). Timestamps come from an injected `Clock` parameter/field, never
  `Instant.now()` inline for business logic (a global `Clock` bean exists:
  `SecurityBeansConfig.clock()` → `Clock.systemUTC()`, injected into `AccountService`,
  `VerificationTokenService`, `AuditService`, `RoleService`, `OutboxRelay`, `RefreshTokenTracker`
  — `LockoutService` will follow the same constructor-injection pattern).
- **Module boundary (L12, ArchUnit-enforced, `ArchitectureTest.java:24-30`):** *"no classes
  outside `com.themistra.auth.account` may depend on the `Account` entity class"* — this is a
  compiled, CI-enforced rule, not just a convention. **This directly constrains T12's design**:
  `LockoutService`, per `design.md`'s package map, lives in `authn`, not `account`. It therefore
  **cannot** import or call `Account.lock()`/`Account.unlock()` directly. The rule only forbids
  depending on the `Account` class itself — `AccountService` (a different class, in the same
  package) is fair game to depend on. `LockoutStateMachine` already respects this (confirmed:
  zero `account`-package import). No precedent yet exists in this codebase of one feature module
  injecting another module's service class across this exact boundary (`authz`/`audit` both
  explicitly avoid even that, per their own ArchUnit rules at lines 32-45 — they operate on UUIDs
  only, never call into `AccountService`). `LockoutService` calling `AccountService` (to actually
  flip `Account.status`) would be the *first* such cross-module service-to-service dependency in
  this codebase. Alternatively, the wiring could run the other direction (`AccountService` calls
  `LockoutService`). Which direction, and the exact method shape, is a Phase 1/2 design question
  — not decided here, only flagged as the single most consequential open architectural point for
  this task.
- **Repositories are package-private** (`ArchitectureTest.java:57-65`, ArchUnit-enforced):
  `*Repository` interfaces must not be `public`.
- **Outbox/audit:** every state change other services or the audit trail care about goes through
  `OutboxPublisher.publish(aggregateType, aggregateId, eventType, schemaVersion, payload)`
  (`AccountService.java:328-334`) and/or `AuditService.record(RecordAuditEventRequest)`
  (`AuditService.java:44-58`, itself outbox-backed for a Kafka mirror). **Not yet clear whether
  T12 is in scope for either** — `tasks.md` item 13 ("Login failure/success tracking... Record
  `login.failed` audit events") reads as the audit-emission task, distinct from item 12. Frozen at
  Phase 1/2, not here.
- **Configuration:** flat `application.properties`, `@ConfigurationProperties` records,
  `@Validated`, fail-fast on missing/invalid (`PasswordPolicyProperties.java` is the direct
  model for the not-yet-created `LockoutProperties`).
- **Error handling:** RFC 9457 `application/problem+json`, no stack traces/internal detail. Not
  obviously relevant to `LockoutService` itself (no new endpoint in this task per `tasks.md`
  item 12's wording — T14 adds the admin unlock endpoint), but any new exception type this task
  introduces would still need to fit the existing `AccountExceptionHandler`-style mapping if it's
  ever thrown across a controller boundary.

## 4. Testing conventions

- **Unit tests:** plain JUnit 5 + AssertJ (+ Mockito where there are collaborators to mock), no
  Spring context. `LockoutStateMachineTest.java` (T11) is the most directly relevant precedent —
  no Mockito needed there (zero collaborators); `LockoutService` will have real collaborators
  (`LockoutStateRepository`, `LockoutStateMachine`, `Clock`, possibly `AccountService`) so
  `PasswordPolicyTest.java`'s `@ExtendWith(MockitoExtension.class)` + `@Mock` shape is the closer
  model.
- **Integration tests:** `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, real
  Postgres + Kafka via Testcontainers (`AccountPersistenceIntegrationTest.java`,
  `RefreshTokenFamilyIntegrationTest.java`, `AuditTrailIntegrationTest.java`,
  `RoleAssignmentIntegrationTest.java` are the precedents). This is the layer that would prove
  `LockoutStateRepository` actually persists/loads correctly against real Postgres — the module
  has no `@DataJpaTest`-only precedent; every persistence-proving test in this codebase goes
  straight to the full Testcontainers `@SpringBootTest` shape.
- **ArchUnit:** `ArchitectureTest.java`, `@AnalyzeClasses(packages = "com.themistra.auth")`,
  runs as part of the normal test suite (not a separate CI stage) — any new cross-module
  dependency this task introduces will be checked by the existing rules, and a new rule may be
  warranted if T12 establishes the first authn→account service dependency (see §3).
- **Fixed `Clock` convention:** confirmed again — `agents.md:56`, "Unit (plain JUnit, fixed
  `Clock`, no Spring context)". `LockoutService`'s unit tests will construct it with a fixed
  `Clock.fixed(...)` or pass explicit `Instant`s, matching `LockoutStateMachineTest`'s
  no-`Instant.now()` rule.

## 5. Known gaps / unknowns

- **I do not know** whether `LockoutService` calling into `AccountService` (or vice versa) is the
  intended wiring direction — no precedent exists in this codebase for a feature module depending
  on another module's service class, and the task statement's phrasing ("ties `Account.lock()` /
  `unlock()` to `AccountService`") is consistent with either direction. This is the single largest
  open question for Phase 1/2.
- **I do not know** whether this task is expected to also fix `AccountService.resetPassword`'s
  pre-existing gap (unlocks the `Account` but never clears `lockout_state`) — `tasks.md` item 12's
  one-line description doesn't say, and T11's frozen brief flagged this exact gap (Finding 8) as
  future work without assigning it to a specific task number.
- **I do not know** whether `LockoutService` is expected to emit any audit event or outbox event
  in this task, or whether that's entirely `tasks.md` item 13's scope. The `security-audit.v1
  .schema.json` contract this task's header lists under "Contracts" does not exist in the repo yet
  (confirmed: only `contracts/events/auth/user-lifecycle.v1.schema.json` exists) — same
  already-logged gap as T11 Phase 3 Finding 11.
- **I do not know** the exact config key names/defaults for `LockoutProperties` — `design.md:42-44`
  proposes `themistra.auth.lockout.max-attempts=5` /`.window-minutes=30` / `.base-lock-minutes=15`
  as a **design proposal**, not yet present in the actual `application.properties` file (confirmed
  by direct grep — zero `lockout` keys exist there today).
- **Confirmed, not a gap:** the `services/auth/src/main/java/com/themistra/auth/token` package
  still fails to compile (`OAuth2TokenType`/`JwtAuthenticationConverter` symbols not found on the
  currently resolved Spring Security version) — pre-existing, unrelated, tracked since T03,
  reconfirmed this phase via `mvn -pl services/auth compile`. `LockoutStateMachine`'s own
  isolated `javac` verification path (T11) is unaffected by this and remains available; whether
  `LockoutService`'s dependencies (real Spring beans, `AccountService`, possibly Testcontainers)
  can be verified the same way, or need the full `mvn` build (currently broken), is worth
  confirming in Phase 5/6 — this class has real collaborators, unlike T11's zero-dependency class.
- **Confirmed, not a gap:** V5's migration (schema, ShedLock table, index) is already applied to
  the migration history; no new Flyway migration is expected for this task per `tasks.md`
  (schema work was T01's "Foundation" task, already done).
