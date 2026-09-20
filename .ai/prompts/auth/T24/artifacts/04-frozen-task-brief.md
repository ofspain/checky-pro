> **STATUS: FROZEN.** Human sign-off given 2026-08-10 ("move on") on the resolution set below, presented alongside the Phase 3 findings. The L7-vs-schema decision (V7 migration) was separately authorized earlier in this phase. Downstream phases may not renegotiate this brief.

# auth · T24 · Phase 4 — Frozen Task Brief

## Disposition of Phase 3 (Kimi) findings

**1. `themistra.auth.api-key.prefix` referenced but not in `application.properties`.**
**Disposition: ACCEPTED.** Add `themistra.auth.api-key.prefix=ck_live_` to `application.properties`. Added to Files to Modify.

**2. `exchange` needs a reverse `accountId`→`accountUuid` lookup, not specified.**
**Disposition: ACCEPTED.** `ApiKeyRepository` gains `Optional<UUID> findAccountUuidById(Long accountId)` — a native query mirroring `MfaEnrollmentRepository.findAccountIdByUuid`'s reverse direction, no `Account` entity import (L12).

**3. Service method return types undefined.**
**Disposition: ACCEPTED.** Three records, defined now:
```java
record CreateApiKeyResult(UUID keyUuid, String plaintextKey, String name, Instant createdAt)
record ApiKeyMetadata(UUID keyUuid, String name, List<String> scopes, Instant createdAt,
                       Instant lastUsedAt, Instant expiresAt, Instant revokedAt)
record ExchangeResult(UUID accountUuid, List<String> scopes)
```
`revoke` returns `void` — no metadata needed by any known caller.

**4. `exchange` prefix-collision behavior unspecified.**
**Disposition: ACCEPTED.** `exchange` evaluates **every** row `findByPrefix` returns with a constant-time comparison — never short-circuits on the first match — and only reports success/failure after all candidates are checked. Closes a (remote, but real) timing side-channel.

**5. Constant-time comparison mechanics unspecified (hex string vs. bytes).**
**Disposition: ACCEPTED.** `common.Hashing` gains `constantTimeEquals(String a, String b)`: converts both hex strings to `byte[]` (same `HexFormat` used by `sha256`), then `MessageDigest.isEqual(byte[], byte[])`. `apikey.ApiKeyHasher` (per `design.md`'s own package map) is a thin wrapper: hashes the presented full key via `Hashing.sha256`, delegates the comparison to `Hashing.constantTimeEquals`.

**6. `create`'s account-status precondition unspecified.**
**Disposition: ACCEPTED.** `create` also requires `AccountStatus.ACTIVE`, matching `MfaService.requireActiveAccount`'s established precedent. Real security gap, not just a nicety — closes a privilege-escalation path for a locked/suspended/deleted merchant account.

**7. `ApiKeyExceptionHandler` premature and underspecified.**
**Disposition: ACCEPTED, scope reduced.** The `@RestControllerAdvice` handler itself is **deferred to T25/T26** — those tasks own the controllers that actually consume it, and building it now means guessing at `ProblemTypes`/status mappings nothing yet exercises (the same "invented shape that may not match" risk finding #3 flagged elsewhere). T24 still defines the exception classes its own methods throw (needed regardless of who maps them to HTTP later); no handler class, no new `ProblemTypes` entries, this task.

**8. `V7` migration SQL imprecise.**
**Disposition: ACCEPTED, with a correction.** Kimi's suggested SQL schema-qualified the table (`auth.api_keys`); V1–V6 are all unqualified, relying on Flyway's configured search path. Matching that convention:
```sql
ALTER TABLE api_keys ALTER COLUMN prefix TYPE VARCHAR(32);
```
File: `V7__widen_api_key_prefix.sql`. The existing `idx_api_keys_prefix` index needs no handling — Postgres doesn't rebuild an index for a widening `VARCHAR` change.

**9. Mutator/conditional-update shape for `lastUsedAt`/`revokedAt` unresolved.**
**Disposition: ACCEPTED.** Two new `ApiKeyRepository` methods, exactly as Kimi specified:
```java
@Modifying @Query("UPDATE ApiKey k SET k.lastUsedAt = :lastUsedAt WHERE k.id = :id")
int updateLastUsedAt(@Param("id") Long id, @Param("lastUsedAt") Instant lastUsedAt);

@Modifying @Query("UPDATE ApiKey k SET k.revokedAt = :revokedAt WHERE k.id = :id AND k.revokedAt IS NULL")
int revokeIfActive(@Param("id") Long id, @Param("revokedAt") Instant revokedAt);
```
No entity mutators — `ApiKey`'s existing getters (T23) are sufficient; only repository-level conditional updates, matching `MfaEnrollmentRepository`'s established convention.

**10. Audit target for exchange rejections with no identifiable account unspecified.**
**Disposition: ACCEPTED.** Confirmed via `AuditService.record`'s own `partitionKey` method, which already explicitly handles a null `accountUuid` ("account-less (system) events have no natural key"). Resolution: hash-mismatch/revoked/expired rejections (a row *was* matched) audit with that row's resolved `accountUuid`; malformed/unknown-prefix rejections (no row matched) audit with `accountUuid = null`.

**11. `create`'s name validation unspecified.**
**Disposition: ACCEPTED.** `create` rejects a blank name or one longer than 100 characters (matching the DDL's `VARCHAR(100)`), throwing `IllegalArgumentException` — a plain guard clause, since no request DTO/Bean Validation layer exists yet (that's T26's).

**12. Key-generation alphabet/entropy source unspecified.**
**Disposition: ACCEPTED.** `SecureRandom`-backed, alphabet `[A-Za-z0-9]` (62 characters), each character via `SecureRandom.nextInt(62)` (unbiased since JDK 17's improved `nextInt(bound)`). Exactly 24 characters for the suffix, 32 for the secret. No `java.util.Random`, no reduced alphabet.

**13. No `agents.md`/LOCKED-decision conflicts.**
**Disposition: Confirmed, no action needed** (non-issue).

No findings rejected.

---

## Final brief (supersedes Phase 2 TIB on every point above; unchanged elsewhere)

### Task
Implement `ApiKeyService`: create (`MERCHANT` role + `ACTIVE` status + confirmed MFA), list, revoke, exchange (constant-time secret verification), generating keys as `ck_live_<24-char suffix>.<32-char secret>`.

### Scope
**In:** `ApiKeyService`, `ApiKeyHasher`, `Hashing.constantTimeEquals`, `V7` migration, `ApiKeyRepository` additions (`findAccountUuidById`, `updateLastUsedAt`, `revokeIfActive`), exception classes (no handler), `application.properties` addition.
**Out:** `ApiKeyExceptionHandler` (T25/T26), any HTTP endpoint/controller/DTO (T25/T26), JWT minting (T25), a max-keys cap or additional scopes (`package.md` §11 Q3 remains unresolved by the spec; T24 enforces none), any `EventTopics` change (no requirement in scope calls for one).

### Business Rules
- **R30** — create: `MERCHANT` role, `ACTIVE` status, confirmed MFA; `ck_live_`-prefixed key; SHA-256 hash stored; plaintext returned once; `api_key.created` audited.
- **R32** — successful exchange updates `last_used_at`.
- **R33** — revoked/expired/malformed/mismatched keys all fail exchange identically.

### Locked Decisions
- **L7** — implemented exactly via the `V7` migration (`prefix` widened to `VARCHAR(32)`), not by shrinking the suffix.
- **L12** — no `Account` entity import anywhere in this task.

### Files to Create
- `services/auth/src/main/resources/db/migration/V7__widen_api_key_prefix.sql` — `ALTER TABLE api_keys ALTER COLUMN prefix TYPE VARCHAR(32);`
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java` — `create`, `list`, `revoke`, `exchange`, plus the three result records (§3 above).
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyHasher.java` — wraps `Hashing.sha256` + `Hashing.constantTimeEquals` for the exchange comparison.
- Exception classes (final naming at implementer's discretion, consistent with codebase convention — e.g. `ApiKeyNotAuthorizedException` for the create-time role/status/MFA gate, `ApiKeyExchangeRejectedException` for R33's uniform rejection, `ApiKeyNotFoundException`/ownership-mismatch for `revoke`). No handler class.
- Tests — unit for `ApiKeyHasher`/`Hashing.constantTimeEquals` (pure logic, no Spring context) and a Testcontainers integration test for `ApiKeyService` (exact split is Phase 5's call).

### Files to Modify
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyRepository.java` — add `findAccountUuidById`, `updateLastUsedAt`, `revokeIfActive`.
- `services/auth/src/main/java/com/themistra/auth/common/Hashing.java` — add `constantTimeEquals(String, String)`.
- `services/auth/src/main/resources/application.properties` — add `themistra.auth.api-key.prefix=ck_live_`.

### Files NOT to Modify
- `ApiKey.java`'s existing mapped columns from T23 — no changes to existing annotations.
- Any file outside `apikey/`, `common/Hashing.java`, the one new migration, and `application.properties`.
- `spec/`.

### Acceptance Criteria
AC1–AC9 as enumerated in Phase 1 (`01-specification-extraction.md`), amended by this phase's dispositions:
- AC1 (MERCHANT+MFA gate) — now also requires `AccountStatus.ACTIVE` (disposition #6).
- AC2 (key format) — resolved via `V7` migration; generation per disposition #12.
- AC7 (constant-time compare) — resolved via `Hashing.constantTimeEquals` + `ApiKeyHasher` (disposition #5), applied to every `findByPrefix` candidate without short-circuiting (disposition #4).
- AC9 (uniform exchange rejection) — audit target resolved per disposition #10.
- New: AC10 — `create` rejects blank/>100-char names (disposition #11).

### Required Tests
Unchanged from Phase 1/2: the two named `package.md` §8 tests plus the boundary tests already listed (create without MERCHANT; create with unconfirmed MFA; **create with a non-ACTIVE account** — new, per disposition #6; exchange wrong-secret; exchange unknown-prefix; revoke of non-owned key; list exposes no secret material; **exchange against multiple prefix-colliding rows, only one matching** — new, per disposition #4).

### Constraints
Unchanged from Phase 2 TIB except where superseded above: constant-time comparison mandatory (now fully specified); `create`/`revoke` `@Transactional`; `exchange`'s `last_used_at` update via the new conditional `@Modifying` methods, not load-mutate-save; `revoke` idempotent via `revokeIfActive`'s `0`-row-is-success semantics; L12 respected; malformed input never throws an unchecked `NullPointerException`/`ArrayIndexOutOfBoundsException` to the caller.

### Open Questions
None remaining. All items raised in Phase 1, Phase 2, and Phase 3 are resolved above.

---

## Addendum (Phase 5 drafting correction, same-session)

Phase 2's TIB already committed to adding a **forward** `accountUuid → accountId` resolver on `ApiKeyRepository` (Files to Modify: "add a UUID→internal-`accountId` resolver, mirroring `MfaEnrollmentRepository.findAccountIdByUuid`") — needed by `create`/`list`/`revoke`, all of which take an `accountUuid` and must resolve it to the internal id `ApiKey.accountId` is keyed on. When drafting this Phase 4 document, only the **reverse** resolver (`findAccountUuidById`, disposition #2, needed by `exchange`'s output) was carried into the final Files to Create/Modify list — the forward one was dropped in transcription, not by any human decision. This addendum restores it; it was never renegotiated, only mistakenly omitted.

**`ApiKeyRepository` therefore gains four new methods, not three:**
```java
@Query(value = "SELECT a.id FROM accounts a WHERE a.account_uuid = :accountUuid", nativeQuery = true)
Optional<Long> findAccountIdByUuid(@Param("accountUuid") UUID accountUuid);
```
(exact mirror of `MfaEnrollmentRepository.findAccountIdByUuid`), in addition to `findAccountUuidById`, `updateLastUsedAt`, and `revokeIfActive` already specified above.

Also restoring from the TIB, dropped for the same reason: a small `apikey.ApiKeyProperties` (`@ConfigurationProperties(prefix = "themistra.auth.api-key")`, `@Validated`, `@NotBlank String prefix`) is required — `agents.md`'s platform rule mandates validated `@ConfigurationProperties` records for all config, never `@Value`, so `ApiKeyService` consuming `themistra.auth.api-key.prefix` (disposition #1) needs this small record. Added to Files to Create.

(End of frozen brief)
