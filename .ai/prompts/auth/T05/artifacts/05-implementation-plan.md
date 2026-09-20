# auth · T05 — Phase 5: Implementation Plan

Plans against `artifacts/04-frozen-task-brief.md` only. Every file traces to that brief's Files to
Create / Files to Modify — nothing added or renamed. Reuses `AccountNotFoundException` (existing,
`account` package) for Finding 5's "account not found at issue" case rather than adding a new
exception file — consistent with the brief's file list and with `AccountService`'s own use of the
same exception for the same scenario.

## Files to create

1. `services/auth/src/main/java/com/themistra/auth/account/VerificationToken.java`
2. `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenRepository.java`
3. `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java`
4. `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenProperties.java`
5. `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenServiceTest.java`

`VerificationTokenService.VerificationTokenResult` (Finding 10's result type) is a `public static`
nested record inside file 3 — not a separate file, since the brief doesn't authorize one (same
pattern as T03's nested exceptions).

## Files to modify

1. `services/auth/src/main/resources/application.properties` — append:
   ```properties
   themistra.auth.verification-token.ttl-minutes=30
   ```

## Public methods (signatures)

**`VerificationTokenProperties`** (record, `@ConfigurationProperties(prefix =
"themistra.auth.verification-token")`, `@Validated`):
```java
record VerificationTokenProperties(@Min(1) long ttlMinutes)
```

**`VerificationToken`** (`@Entity @Table(name = "verification_tokens")`):
```java
public static VerificationToken create(Long accountId, Purpose purpose, String tokenHash,
                                        Instant createdAt, Instant expiresAt)

public enum Purpose { EMAIL_VERIFY, PASSWORD_RESET }

// getters only: getId, getAccountId, getPurpose, getTokenHash, getExpiresAt, getUsedAt, getCreatedAt
```
Deliberately **no `setUsedAt`/mutator**: consumption happens only via
`VerificationTokenRepository`'s atomic `@Modifying` update (Finding 2), never by loading the
entity, calling a setter, and saving — that read-modify-write shape is exactly the race Finding 2
flagged. No `@PrePersist`/`@PreUpdate` (Finding 9) — `createdAt`/`expiresAt` are passed into
`create(...)` by the service, sourced from the injected `Clock`.

**`VerificationTokenRepository`** (package-private, `JpaRepository<VerificationToken, Long>`):
```java
Optional<VerificationToken> findByTokenHash(String tokenHash)

@Modifying
@Query("UPDATE VerificationToken t SET t.usedAt = :now "
     + "WHERE t.tokenHash = :tokenHash AND t.usedAt IS NULL AND t.expiresAt > :now")
int markConsumed(@Param("tokenHash") String tokenHash, @Param("now") Instant now)

@Modifying
@Query("UPDATE VerificationToken t SET t.usedAt = :now "
     + "WHERE t.accountId = :accountId AND t.purpose = :purpose AND t.usedAt IS NULL")
int invalidateActive(@Param("accountId") Long accountId,
                      @Param("purpose") VerificationToken.Purpose purpose,
                      @Param("now") Instant now)
```
`markConsumed`'s `WHERE` clause folds *both* the atomicity requirement (Finding 2: `usedAt IS
NULL`) and the TTL boundary (`expiresAt > :now`) into one conditional update — a single atomic
operation is strictly safer than checking expiry separately from marking used, since a separate
check would reopen a race window between "checked expiry" and "marked used." This composes two
already-frozen requirements (Finding 2 + the TTL boundary AC); it doesn't add a new one.

**`VerificationTokenService`** (`@Service`):
```java
public VerificationTokenService(VerificationTokenRepository tokenRepository,
                                 AccountRepository accountRepository,
                                 VerificationTokenProperties properties,
                                 Clock clock)

@Transactional
public VerificationTokenResult issue(UUID accountUuid, VerificationToken.Purpose purpose)
        throws AccountNotFoundException

@Transactional(readOnly = true)
public Optional<UUID> verify(String rawToken)

@Transactional
public Optional<UUID> consume(String rawToken)

public record VerificationTokenResult(
        String rawToken, VerificationToken token, UUID accountUuid, VerificationToken.Purpose purpose)
```
`verify`/`consume` both return `Optional<UUID>` (the account UUID on success, empty for *any*
failure reason) — this is the uniform R5 shape locked at Phase 4: there is no exception hierarchy
to accidentally leak which of "not found / expired / used / account unusable" applied.

## Private methods

**`VerificationTokenService`:**
- `private String generateRawToken()` — 32 bytes from a `SecureRandom` field, encoded via
  `Base64.getUrlEncoder().withoutPadding()` (Finding 1: 43-character URL-safe string).
- `private boolean isAccountUsable(Account account)` — `status != DELETED && status != SUSPENDED`
  (Finding 4, option a: no other status filtering). Shared by `verify` and `consume`.

**`issue` flow** (all inside one `@Transactional` method, no separate private method needed beyond
the two above):
1. `Objects.requireNonNull` on `accountUuid` and `purpose`.
2. `accountRepository.findByAccountUuid(accountUuid).orElseThrow(() -> new
   AccountNotFoundException(accountUuid))` (Finding 5 — reused existing exception, internal call,
   not the uniform R5 path).
3. `tokenRepository.invalidateActive(account.getId(), purpose, now)` (Finding 8, before creating
   the new token, same transaction).
4. Loop up to 3 attempts (Finding 6): generate raw token, hash it, `VerificationToken.create(...)`,
   `tokenRepository.saveAndFlush(...)` inside a `try`; on `DataIntegrityViolationException`, retry.
   `saveAndFlush` (not `save`) is required so the unique-constraint violation surfaces immediately
   inside the loop — the same reason `AccountService.register` uses `saveAndFlush` for its own
   duplicate-email race.
5. Exhausted retries → `throw new IllegalStateException(...)` (Finding 6).

**`verify` flow:**
1. `Objects.requireNonNull` on `rawToken`.
2. Hash it, `tokenRepository.findByTokenHash(...)` — empty → `Optional.empty()`.
3. `now = clock.instant()`; if `usedAt != null || !expiresAt.isAfter(now)` → `Optional.empty()`.
4. Resolve `Account` by the token's `accountId`; missing or `!isAccountUsable(...)` →
   `Optional.empty()`.
5. Otherwise `Optional.of(account.getAccountUuid())`. No mutation.

**`consume` flow:**
1. `Objects.requireNonNull` on `rawToken`.
2. Hash it, `tokenRepository.findByTokenHash(...)` — empty → `Optional.empty()`.
3. Resolve `Account`; missing or `!isAccountUsable(...)` → `Optional.empty()` — checked **before**
   attempting the atomic update, so a token belonging to a deleted/suspended account is never
   marked used by this call.
4. `tokenRepository.markConsumed(tokenHash, clock.instant())` — `0` rows affected (already used,
   expired, or lost a concurrent race) → `Optional.empty()`.
5. Otherwise `Optional.of(account.getAccountUuid())`.

## Entities used

- `VerificationToken` (new, this task).
- `Account` (existing, read-only — `findByAccountUuid`, `findById` via the internal FK, status
  checks only; no mutation, no `activateEmail()` call from this task).

## Repositories used

- `VerificationTokenRepository` (new, this task).
- `AccountRepository` (existing, read-only).

## Services used

- None injected — `VerificationTokenService` depends only on the two repositories above, the new
  `VerificationTokenProperties`, and the existing `Clock` bean.

## Unit tests required

All unit-only (plain JUnit 5 + Mockito or a lightweight in-memory fake for the repository layer,
fixed `Clock`, no Spring context, per `agents.md`). No integration/Testcontainers test is
authorized by the frozen brief.

**`VerificationTokenServiceTest`:**
- `shouldActivateAccountWithValidVerificationToken` — issue then consume a token; resolves to the
  correct account UUID.
- `shouldNotRevealAccountExistenceForInvalidVerificationToken` — token-not-found, expired,
  already-used, and deleted/suspended-account-owning tokens all return `Optional.empty()` from both
  `verify` and `consume` — same shape, asserted per case.
- TTL boundary: a token whose `expiresAt` equals the fixed `Clock`'s instant exactly is treated as
  expired (not valid); one tick before is valid, one tick after is expired.
- Atomicity: two sequential `consume` calls on the same token — first succeeds, second returns
  `Optional.empty()` (proves the `usedAt IS NULL` condition, not true concurrent-thread testing,
  which is out of scope for a unit test without Testcontainers).
- Reissue: issuing a second token for the same `(account, purpose)` invalidates the first — the
  old raw token no longer verifies/consumes.
- Collision retry: repository stubbed to throw `DataIntegrityViolationException` on the first N-1
  attempts, succeed on the Nth — `issue` still returns a result; exhausting all 3 attempts throws
  `IllegalStateException`.
- `VerificationTokenProperties` config validation: `ttlMinutes = 0` (and negative) rejected by
  `@Min(1)` (via `jakarta.validation.Validation`, same pattern as T03's
  `PasswordPolicyPropertiesTest`).
- Both `Purpose` values round-trip through `issue` (purpose stored and returned correctly).
- Null `accountUuid`/`purpose` to `issue`, null `rawToken` to `verify`/`consume` — intentional
  exception (`NullPointerException` with a descriptive message, matching the `BreachCheckClient`
  precedent from T03), not a bare NPE from deeper in the call stack.
- `issue` for a nonexistent `accountUuid` throws `AccountNotFoundException` (not the uniform R5
  path — this is `issue`'s own, separate error semantics per Finding 5).
- No raw-token leakage: the persisted `VerificationToken`'s fields and `toString()` never contain
  the raw token value — only `tokenHash`.

## Execution order

1. `application.properties` — add the TTL config key (front-loaded, everything else binds against
   it).
2. `VerificationTokenProperties.java` — config binding, no dependencies on other new classes.
3. `VerificationToken.java` — entity, no dependencies on the repository or service.
4. `VerificationTokenRepository.java` — depends on the entity.
5. `VerificationTokenService.java` — depends on steps 2–4 plus the existing `AccountRepository` and
   `Clock`.
6. `VerificationTokenServiceTest.java` — depends on all of the above.
7. Compile/test verification, following the T03 precedent: direct `javac` against the module's
   resolved classpath (bypassing the still-unrelated, still-unfixed `token` package compile
   failure noted in T03's Phase 6/12), then execute via the JUnit Platform `Launcher` API.
