# auth · T24 · Phase 5 — Implementation Plan

Every file below traces to the frozen brief's Files to Create/Modify (`artifacts/04-frozen-task-brief.md`), including its Phase 5-drafting addendum restoring the forward `accountId` resolver and `ApiKeyProperties` (both already committed in Phase 2's TIB, mistakenly dropped when Phase 4 was drafted, corrected before this plan). No file beyond that combined list is planned.

## Files to create

1. `services/auth/src/main/resources/db/migration/V7__widen_api_key_prefix.sql`
2. `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyProperties.java`
3. `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyHasher.java`
4. `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyNotAuthorizedException.java`
5. `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyExchangeRejectedException.java`
6. `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyNotFoundException.java`
7. `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java`
8. `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyHasherTest.java`
9. `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyServiceIntegrationTest.java`

## Files to modify

1. `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyRepository.java` — add `findAccountIdByUuid`, `findAccountUuidById`, `updateLastUsedAt`, `revokeIfActive`.
2. `services/auth/src/main/java/com/themistra/auth/common/Hashing.java` — add `constantTimeEquals(String, String)`.
3. `services/auth/src/main/resources/application.properties` — add `themistra.auth.api-key.prefix=ck_live_`.

## Public methods (signatures)

**`ApiKeyProperties`**
```java
@ConfigurationProperties(prefix = "themistra.auth.api-key")
@Validated
public record ApiKeyProperties(@NotBlank String prefix) {}
```

**`Hashing`** (addition)
```java
public static boolean constantTimeEquals(String hexA, String hexB)
```

**`ApiKeyHasher`**
```java
public String hash(String fullKey)                 // Hashing.sha256(fullKey)
public boolean matches(String presentedFullKey, String storedHash)  // hash(...) + Hashing.constantTimeEquals
```

**`ApiKeyRepository`** (package-private interface, additions)
```java
Optional<Long> findAccountIdByUuid(UUID accountUuid);
Optional<UUID> findAccountUuidById(Long accountId);
int updateLastUsedAt(Long id, Instant lastUsedAt);
int revokeIfActive(Long id, Instant revokedAt);
List<ApiKey> findByAccountId(Long accountId);   // for list(); derived query, no new Javadoc-worthy logic
```
(`findByAccountId` wasn't separately called out in the frozen brief's method list but is a direct, unavoidable requirement of `list(...)`'s own stated behavior — a plain derived-query method, the same category `ApiKeyRepository` already has via `findByPrefix`, not a new design decision.)

**`ApiKeyService`** (package-private constructor dependencies; public API)
```java
public ApiKeyService(ApiKeyRepository apiKeyRepository, AccountService accountService,
                      RoleService roleService, MfaService mfaService, AuditService auditService,
                      ApiKeyHasher apiKeyHasher, ApiKeyProperties apiKeyProperties, Clock clock)

@Transactional
public CreateApiKeyResult create(UUID accountUuid, String name)

@Transactional(readOnly = true)
public List<ApiKeyMetadata> list(UUID accountUuid)

@Transactional
public void revoke(UUID accountUuid, UUID keyUuid)

@Transactional
public ExchangeResult exchange(String presentedKey)

public record CreateApiKeyResult(UUID keyUuid, String plaintextKey, String name, Instant createdAt) {}
public record ApiKeyMetadata(UUID keyUuid, String name, List<String> scopes, Instant createdAt,
                              Instant lastUsedAt, Instant expiresAt, Instant revokedAt) {}
public record ExchangeResult(UUID accountUuid, List<String> scopes) {}
```

**Exception classes** (each a plain `RuntimeException` subclass, matching `MfaAlreadyEnrolledException`'s shape — no-arg or minimal constructor, no message leaking internal detail per R33's uniformity requirement):
```java
public class ApiKeyNotAuthorizedException extends RuntimeException {}      // create: role/status/MFA gate
public class ApiKeyExchangeRejectedException extends RuntimeException {}   // R33: single uniform cause
public class ApiKeyNotFoundException extends RuntimeException {}           // revoke: missing or not owned
```

## Private methods

**`ApiKeyService`**
```java
private void requireMerchantWithConfirmedMfa(UUID accountUuid)
    // AccountService.getByUuid -> AccountStatus.ACTIVE check (else InvalidAccountStateException,
    // reused from account module, same as MfaService.requireActiveAccount);
    // RoleService.resolveEffectiveRoles(accountUuid).contains("MERCHANT") (else ApiKeyNotAuthorizedException);
    // MfaService.hasConfirmedTotpEnrollment(accountUuid) (else ApiKeyNotAuthorizedException)

private Long resolveAccountId(UUID accountUuid)
    // apiKeyRepository.findAccountIdByUuid(...).orElseThrow(() -> new AccountNotFoundException(accountUuid))
    // mirrors MfaService.resolveAccountId exactly

private String generateSuffix()   // SecureRandom, [A-Za-z0-9], 24 chars
private String generateSecret()   // SecureRandom, [A-Za-z0-9], 32 chars
private String randomAlphanumeric(int length)  // shared by both, SecureRandom.nextInt(62) per char

private ApiKeyMetadata toMetadata(ApiKey apiKey)  // maps entity -> DTO, never touches keyHash

private void recordAudit(String eventType, AuditOutcome outcome, UUID accountUuid, UUID actorUuid)
    // wraps auditService.record(new RecordAuditEventRequest(...)); actorUuid == accountUuid for
    // create/revoke (self-service actions); accountUuid may be null for exchange rejections with
    // no resolved account (disposition #10) — actorUuid null in that case too, no actor to record
```

**`ApiKeyHasher`** — no private methods; two public methods only, both trivial compositions of `Hashing`.

## Entities used
- `ApiKey` (T23) — read via `ApiKeyRepository`, no entity mutation from `ApiKeyService` itself (all field-level changes go through the new conditional `@Modifying` repository methods, per the frozen brief's disposition #9).

## Repositories used
- `ApiKeyRepository` (T23 + this task's four new methods + `findByAccountId`).

## Services used
- `AccountService.getByUuid(UUID)` — status check.
- `RoleService.resolveEffectiveRoles(UUID)` — role check.
- `MfaService.hasConfirmedTotpEnrollment(UUID)` — MFA check.
- `AuditService.record(RecordAuditEventRequest)` — `api_key.created`, `api_key.revoked`, exchange-rejection audit.

## Unit/integration tests required

**`ApiKeyHasherTest`** (plain JUnit, no Spring context — pure logic, mirrors `TotpVerifier`'s precedent for crypto-primitive unit tests):
1. `matchesReturnsTrueForTheCorrectKey` — hash a known key, assert `matches(sameKey, hash)` is true.
2. `matchesReturnsFalseForAnIncorrectKey` — assert `matches(differentKey, hash)` is false.
3. `matchesIsConstantTimeRegardlessOfWhereTheMismatchOccurs` — not literally timing-measured (flaky in CI), but asserts `Hashing.constantTimeEquals` is actually invoked by testing two mismatching hashes of equal length that differ at the first vs. last character both return `false` — a correctness proxy, not a timing benchmark. Documented as such in the test's own comment so it isn't mistaken for real timing verification.

**`ApiKeyServiceIntegrationTest`** (Testcontainers, Postgres + Kafka, matching `MfaPersistenceIntegrationTest`'s established fixture conventions):
1. `shouldCreateApiKeyAndShowPlaintextExactlyOnce` (named test, R30) — MERCHANT + ACTIVE + confirmed MFA account; `create` returns a plaintext key matching `ck_live_<32 chars>.<32 chars>` shape (24+8? — no: prefix stored is `ck_live_` + 24-char suffix = 32 chars total, secret is 32 chars, full plaintext is `<32-char prefix>.<32-char secret>`); reload from DB shows only the hash, never the plaintext.
2. `createRejectsNonMerchantAccount` — role check.
3. `createRejectsUnconfirmedMfa` — MFA check.
4. `createRejectsNonActiveAccount` — status check (frozen brief disposition #6).
5. `createRejectsBlankOrOverlongName` — disposition #11.
6. `shouldRejectRevokedOrUnknownApiKeyWithUniform401` (named test, R33) — covers all four causes (revoked, expired, malformed, hash-mismatch) via one parameterized test or four explicit cases, asserting each throws the same `ApiKeyExchangeRejectedException`.
7. `exchangeUpdatesLastUsedAt` — R32.
8. `exchangeChecksEveryPrefixCollisionCandidate` — two keys sharing a prefix (constructed directly via the repository, bypassing generation, since natural collision is practically impossible), only one with a matching secret; exchange succeeds and matches the right one — disposition #4.
9. `listReturnsOnlyTheCallersOwnKeysWithNoSecretMaterial` — R34-adjacent (AC5).
10. `revokeIsIdempotent` — revoking an already-revoked key doesn't error (disposition #9's `revokeIfActive` semantics).
11. `revokeOfNonOwnedKeyFails` — ownership boundary, `ApiKeyNotFoundException` (enumeration-safe: same exception whether the key doesn't exist or belongs to someone else).

## Execution order

1. `V7__widen_api_key_prefix.sql` — schema first; nothing else can be verified against a real DB without it.
2. `Hashing.constantTimeEquals` (modify `common/Hashing.java`) — no dependencies, needed by `ApiKeyHasher`.
3. `ApiKeyRepository.java` (modify) — the four new methods; depends only on `ApiKey` (T23, already exists).
4. `ApiKeyProperties.java` — no dependencies.
5. `ApiKeyHasher.java` — depends on `Hashing`.
6. Exception classes (`ApiKeyNotAuthorizedException`, `ApiKeyExchangeRejectedException`, `ApiKeyNotFoundException`) — no dependencies, needed by `ApiKeyService`.
7. `ApiKeyService.java` — depends on everything above plus `AccountService`, `RoleService`, `MfaService`, `AuditService` (all pre-existing).
8. `application.properties` (modify) — needed for `ApiKeyService`/`ApiKeyProperties` to actually resolve a value at runtime; can be added any time before running tests, ordered here for clarity.
9. `ApiKeyHasherTest.java` — as soon as `ApiKeyHasher` exists.
10. `ApiKeyServiceIntegrationTest.java` — last; depends on everything.
