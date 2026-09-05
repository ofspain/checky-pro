# auth · T12 — Phase 5: Implementation Plan

Plans the frozen brief (`04-frozen-task-brief.md`). No code below — signatures and call-order only.

## Files to create

1. `services/auth/src/main/java/com/themistra/auth/authn/LockoutState.java`
2. `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateRepository.java`
3. `services/auth/src/main/java/com/themistra/auth/authn/LockoutProperties.java`
4. `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java`
5. `services/auth/src/test/java/com/themistra/auth/authn/LockoutServiceTest.java`
6. `services/auth/src/test/java/com/themistra/auth/authn/LockoutPropertiesTest.java`
7. `services/auth/src/test/java/com/themistra/auth/authn/LockoutPersistenceIntegrationTest.java`

All seven trace to the frozen brief's Files to Create (four production) plus the Required Tests
section (three test files — one per production concern: service logic, config validation,
real-Postgres native-query correctness).

## Files to modify

1. `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — add `lock(UUID)`
   / `unlock(UUID)`.
2. `services/auth/src/main/resources/application.properties` — add the three
   `themistra.auth.lockout.*` keys.

Both authorized by the frozen brief's Files to Modify list.

## Public methods (signatures)

**`LockoutState.java`** (entity, package `authn`):
- `@Id @Column(name = "account_id") private Long accountId;` — no `@GeneratedValue`.
- `@Column(name = "failed_attempts", nullable = false) private int failedAttempts;`
- `@Column(name = "last_failed_at") private Instant lastFailedAt;` — nullable.
- `@Column(name = "locked_until") private Instant lockedUntil;` — nullable.
- `@Column(name = "lock_count", nullable = false) private int lockCount;`
- `protected LockoutState() {}` — JPA only.
- `static LockoutState of(Long accountId, LockoutDecision decision)` — factory building a
  `LockoutState` from a resolved internal id and a `LockoutStateMachine.LockoutDecision`; mirrors
  `VerificationToken.create(...)`'s static-factory style, caller-supplied fields only.
- `void applyDecision(LockoutDecision decision)` — updates all four mutable fields on an
  already-persistent instance (used on the update path, distinct from `of(...)`'s insert path, so
  JPA's dirty-checking handles the `UPDATE` without an explicit `save()` call being required, though
  `LockoutService` calls `save()` explicitly anyway for clarity/consistency with every other
  repository call site in this codebase).
- Getters for all five fields (`getAccountId`, `getFailedAttempts`, `getLastFailedAt`,
  `getLockedUntil`, `getLockCount`) — no setters beyond `applyDecision`.

**`LockoutStateRepository.java`** (package-private interface, `extends JpaRepository<LockoutState, Long>`):
- `@Query(value = "SELECT ls.* FROM lockout_state ls JOIN accounts a ON a.id = ls.account_id " +
  "WHERE a.account_uuid = :accountUuid FOR UPDATE", nativeQuery = true)
  Optional<LockoutState> findByAccountUuidForUpdate(@Param("accountUuid") UUID accountUuid);`
- `@Query(value = "SELECT a.id FROM accounts a WHERE a.account_uuid = :accountUuid",
  nativeQuery = true)
  Optional<Long> findAccountIdByUuid(@Param("accountUuid") UUID accountUuid);`

**`LockoutProperties.java`** (`@ConfigurationProperties(prefix = "themistra.auth.lockout")
@Validated record`):
- `record LockoutProperties(@Min(1) int maxAttempts, @Min(1) int windowMinutes, @Min(1) int
  baseLockMinutes)` — mirrors `PasswordPolicyProperties`'s shape exactly (flat record, `@Min`
  bounds, no defaults, fails startup if any key is absent).

**`LockoutService.java`** (`@Service`, package `authn`):
- `public LockoutService(LockoutStateRepository repository, LockoutProperties properties,
  AccountService accountService)` — constructs one `LockoutStateMachine` field internally:
  `new LockoutStateMachine(properties.maxAttempts(), Duration.ofMinutes(properties.windowMinutes()),
  Duration.ofMinutes(properties.baseLockMinutes()))`. No `Clock` field — `now` is caller-supplied
  to every time-sensitive method (T11's determinism convention).
- `@Transactional public LockoutDecision recordFailedAttempt(UUID accountUuid, Instant now)`
- `@Transactional public LockoutDecision recordSuccessfulAttempt(UUID accountUuid, Instant now)`
- `@Transactional public LockoutDecision resetLockout(UUID accountUuid)`

**`AccountService.java`** (existing class, two new methods, no signature changes elsewhere):
- `@Transactional public void lock(UUID accountUuid)` — guarded: `account.lock()` only if
  `status == ACTIVE`.
- `@Transactional public void unlock(UUID accountUuid)` — guarded: `account.unlock()` only if
  `status == LOCKED`.
- Both reuse the existing private `getAccount(UUID)` helper (`AccountService.java:351-354`) — no
  new lookup logic.

## Private methods

**`LockoutService.java`:**
- `private LockoutSnapshot toSnapshot(Optional<LockoutState> existing)` — maps a possibly-empty
  repository result to a `LockoutStateMachine.LockoutSnapshot`: empty → `new
  LockoutSnapshot(0, null, null, 0)`; present → the four fields copied across.
- `private void applyStatusChange(AccountStatusChange statusChange, UUID accountUuid)` — a
  one-line `switch` calling `accountService.lock(accountUuid)` / `.unlock(accountUuid)` / nothing
  for `LOCK`/`UNLOCK`/`NONE` respectively. Shared by all three public methods so the
  decision-to-`AccountService`-call mapping exists in exactly one place.
- `private LockoutState persistNewOrUpdated(Optional<LockoutState> existing, UUID accountUuid,
  LockoutDecision decision)` — if `existing` is present, calls `applyDecision(decision)` and
  `repository.save(existing)`; if absent, resolves the internal id via
  `repository.findAccountIdByUuid(accountUuid)` (throwing `IllegalStateException` if the account
  itself doesn't exist — an unreachable-in-practice defensive branch, since a caller only invokes
  this service for an account it already resolved), builds via `LockoutState.of(id, decision)`,
  and calls `repository.save(...)`.

## Entities used

- `LockoutState` (new, this task) — the only entity `LockoutService`/`LockoutStateRepository`
  touch. `Account` is never imported (L12) — `AccountService`'s two new methods are the only
  cross-module call.

## Repositories used

- `LockoutStateRepository` (new, this task).
- `AccountRepository` — not touched directly by this task; `AccountService`'s existing private
  `getAccount(UUID)` helper already wraps it, reused as-is by the two new `AccountService` methods.

## Services used

- `LockoutStateMachine` (T11) — not a Spring bean; constructed as a plain field inside
  `LockoutService`'s constructor.
- `AccountService` — injected into `LockoutService`; the first cross-module (`authn` → `account`)
  service dependency in this codebase (confirmed at Phase 0 — no prior precedent).

## Unit/integration tests required

**`LockoutServiceTest.java`** — plain JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`),
mocking `LockoutStateRepository` and `AccountService`, matching `PasswordPolicyTest`'s shape
(real collaborators mocked, the class under test is real):
- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` (named) — stub
  `findByAccountUuidForUpdate` to return progressively-updated `LockoutState`s (or `Optional.empty()`
  on the first call) across five sequential `recordFailedAttempt` calls; assert the 5th persists a
  `LOCKED`-shaped row and `verify(accountService).lock(accountUuid)` exactly once, `verify(accountService,
  never()).unlock(any())`.
- `shouldResetLockoutCounterOnSuccessfulLogin` (named) — stub an existing locked row; call
  `recordSuccessfulAttempt`; assert the persisted row is zeroed and `verify(accountService).unlock(accountUuid)`
  exactly once.
- `missingRowOnFailureCreatesNewRowViaResolvedAccountId` — stub `findByAccountUuidForUpdate` empty,
  `findAccountIdByUuid` present; assert `repository.save(...)` is called with a `LockoutState`
  whose `accountId` matches the resolved id and `failedAttempts == 1`.
- `missingRowOnSuccessIsANoOp` — stub both repository methods empty/unused; assert
  `verify(repository, never()).save(any())` and the returned decision is the zeroed `NONE` shape
  (Finding 6/AC8).
- `blockedAttemptWritesNothingAndCallsNothing` — stub a row whose `lockedUntil` is after `now`;
  assert `verify(repository, never()).save(any())` and `verifyNoInteractions(accountService)`
  (Finding 7/AC7).
- `reLockWhileAccountStatusStillLockedDoesNotThrowAndStillLocksAgain` — stub a row matching T11's
  AC7 shape (`failedAttempts=5`, `lockedUntil` at `now`, `lockCount=1`); call
  `recordFailedAttempt` at exactly `lockedUntil`; assert no exception, the row updates to the
  doubled-duration lock, and `verify(accountService).lock(accountUuid)` is still called once (the
  guard's no-op behavior is `AccountService`'s own concern, proven separately in
  `AccountServiceTest`, not re-proven here with a real `Account`) (Finding 2/5/AC2).
- `resetLockoutZeroesAnExistingLockedRowAndUnlocks` / `resetLockoutOnAlreadyCleanAccountIsHarmless`
  (AC9).
- `constructorBuildsMachineFromProperties` — not directly testable via mocking (the machine is a
  private field); covered indirectly by the boundary/doubling assertions above actually producing
  L4-correct durations end-to-end through `LockoutService`, proving the constants reached the
  machine correctly (AC5).
- Null-rejection tests for `accountUuid`/`now` on all three entry points (matching T11's
  `Objects.requireNonNull` convention, now at the `LockoutService` layer too).

**`LockoutPropertiesTest.java`** — mirrors `PasswordPolicyPropertiesTest.java` exactly: JSR-380
`Validator` direct-use, no Spring context. Valid-values case; one violation case per field
(`maxAttempts`/`windowMinutes`/`baseLockMinutes` each `< 1`).

**`LockoutPersistenceIntegrationTest.java`** — `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`,
matching `AccountPersistenceIntegrationTest.java`'s shape:
- A real `LockoutStateRepository.findByAccountUuidForUpdate` against a real inserted `accounts`
  row + `lockout_state` row returns the correct entity.
- `findAccountIdByUuid` resolves the correct internal id for a real account.
- End-to-end: `LockoutService.recordFailedAttempt` called five times against a real, registered
  `Account` (via `AccountService.register` or a direct repository insert) results in a real
  `LOCKED` `Account.status` readable back through `AccountService.getByUuid`, and a real persisted
  `lockout_state` row — proves the whole native-query + transaction-join chain actually works
  against Postgres, which no mocked unit test can.

**`AccountServiceTest.java`** (existing file, not newly created — but gains new test methods per
the frozen brief's Files to Modify list covering `AccountService.java` itself):
- `lockNoOpsWhenAccountIsNotActive` / `lockTransitionsActiveToLocked`
- `unlockNoOpsWhenAccountIsNotLocked` / `unlockTransitionsLockedToActive`
This file is already in the frozen brief's Files to Modify list (via `AccountService.java`); no
new file needed.

## Execution order

1. `LockoutState.java` — entity first (no dependencies beyond `java.time`).
2. `LockoutStateRepository.java` — depends on `LockoutState`.
3. `LockoutProperties.java` — independent, can be written in parallel with 1-2.
4. `application.properties` — add the three keys (needed before any Spring context can start for
   integration testing).
5. `AccountService.java` — add `lock(UUID)`/`unlock(UUID)` (independent of `LockoutService`; T12's
   own `LockoutService` will depend on these existing by the time it's written).
6. `LockoutService.java` — depends on 1-3 and 5.
7. `AccountServiceTest.java` — new tests for `lock`/`unlock` (can start as soon as step 5 lands).
8. `LockoutPropertiesTest.java` — depends on step 3 only.
9. `LockoutServiceTest.java` — depends on step 6.
10. `LockoutPersistenceIntegrationTest.java` — depends on steps 1-6 fully landed; requires Docker
    (Testcontainers) to actually run, unlike every other test in this plan.
11. Compile + run everything except step 10 via the established `javac` + JUnit Platform Launcher
    workaround (module-wide `mvn test` still blocked by the pre-existing, unrelated `token`
    package break). Step 10 requires either a working `mvn -pl services/auth test` invocation
    (blocked by the same break) or a standalone Testcontainers-capable run — flagged as a Phase 6
    verification risk, not resolved in this planning phase.

No new Flyway migration — `lockout_state` and its index are already applied (L1 immutability,
confirmed at Phase 0).
