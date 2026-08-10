# auth · T24 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T24 — Key service |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 TIB. Findings only.

---

## 1. `themistra.auth.api-key.prefix` is referenced but does not exist

- **Issue.** The TIB lists `themistra.auth.api-key.prefix=ck_live_` as an existing dependency, but `services/auth/src/main/resources/application.properties` contains no API-key configuration keys at all. `design.md` §4c lists `themistra.auth.api-key.prefix` and `themistra.auth.api-key.token-ttl-minutes`, but they were never added to the properties file.
- **Severity.** Medium — the service cannot read the configured prefix if the key is missing. Hardcoding `ck_live_` works but contradicts the stated dependency and the configurable-prefix intent of L7.
- **Evidence.** `services/auth/src/main/resources/application.properties` (no `api-key` keys); `spec/auth-service/design.md` §4c (lists the keys).
- **Recommended brief amendment.** Add the API-key property block to `application.properties` (at least `themistra.auth.api-key.prefix=ck_live_`) and update the Files to Modify section to include `application.properties`. If the prefix is intentionally hardcoded for T24, state that explicitly and remove it from the Dependencies section.

---

## 2. `exchange(...)` needs an internal-id→UUID account lookup, but the brief only plans UUID→id

- **Issue.** The TIB says T24 will add "a new `ApiKeyRepository` method resolving an account UUID to its internal id." But `exchange(presentedKey)` must return the validated key's owning **account UUID** (`ApiKey` stores only `accountId`, a `Long`). The brief does not specify how to resolve `accountId` back to `accountUuid`.
- **Severity.** High — the service's stated output (owning account UUID + scopes) is unreachable without this reverse lookup.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java:42-43` (`accountId` is a plain `Long`); TIB §15 (exchange output); `MfaEnrollmentRepository.findAccountIdByUuid` only resolves UUID→id.
- **Recommended brief amendment.** Specify the reverse resolver: either (a) add `Optional<UUID> findAccountUuidById(Long accountId)` to `ApiKeyRepository` (mirroring `MfaEnrollmentRepository.findAccountIdByUuid` but in reverse), or (b) expose a public `AccountService`/`AccountRepository` method to resolve id→UUID. L12 forbids importing the `Account` entity, but a repository native query or service method returning the UUID is allowed.

---

## 3. Service method return types are undefined

- **Issue.** The TIB says create returns "the plaintext key (once) + key metadata," list returns "a list of key metadata," revoke returns "none (void) or the revoked key's metadata — TBD Phase 5," and exchange returns "the validated key's owning account UUID + granted scopes." It explicitly defers DTO shapes to T26, but the service still needs concrete return types so that T25/T26 can call it.
- **Severity.** Medium — ambiguity here means the implementer must invent shapes that may not match T26's eventual HTTP DTOs.
- **Evidence.** TIB §51-55 (Outputs).
- **Recommended brief amendment.** Define minimal service-level result records now, even if T26 later wraps them in HTTP DTOs. For example:
  - `CreateApiKeyResult(UUID keyUuid, String plaintextKey, String name, Instant createdAt)`
  - `ApiKeyMetadata(UUID keyUuid, String name, List<String> scopes, Instant createdAt, Instant lastUsedAt, Instant expiresAt, Instant revokedAt)`
  - `ExchangeResult(UUID accountUuid, List<String> scopes)`

---

## 4. `exchange` prefix-collision behavior is not specified

- **Issue.** `ApiKeyRepository.findByPrefix` returns `List<ApiKey>` because `prefix` has no `UNIQUE` constraint. The TIB says exchange "looks up by prefix, constant-time-compares the presented key's hash against the stored hash." It does not say what to do when multiple rows share the prefix.
- **Severity.** Medium — a collision (however unlikely) or a future data issue would make the exchange behavior undefined.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyRepository.java:20` (`List<ApiKey> findByPrefix`).
- **Recommended brief amendment.** State explicitly that `exchange` iterates over all rows returned by `findByPrefix` and succeeds if **any** row's stored hash matches the presented key's hash and that row is active/non-expired. All rows must be checked with constant-time comparison to avoid leaking which row matched via timing. If none match, the rejection is uniform.

---

## 5. Constant-time comparison details are not specified

- **Issue.** The TIB mandates constant-time comparison but does not say how to apply it. `Hashing.sha256` returns a hex `String`; `String.equals` is short-circuiting and not constant-time. `MessageDigest.isEqual(byte[], byte[])` requires byte arrays, so the hex strings must be converted back to bytes (or the hash computed as bytes in the first place).
- **Severity.** Medium-High — getting this wrong creates a timing side channel.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/common/Hashing.java:17-25` returns hex; TIB Constraints §88 (constant-time comparison mandatory).
- **Recommended brief amendment.** Specify the exact flow: compute `Hashing.sha256(presentedKey)` as a hex string, convert both stored and computed hex strings to `byte[]` using the same encoding, then `MessageDigest.isEqual(storedBytes, computedBytes)`. Or, preferably, add a helper `Hashing.constantTimeEquals(String a, String b)` that does the hex→byte conversion and `MessageDigest.isEqual` internally.

---

## 6. Account status precondition for `create(...)` is not specified

- **Issue.** R30 requires an authenticated user with the `MERCHANT` role and confirmed MFA. It does not explicitly require the account to be `ACTIVE`, but a locked, suspended, or deleted merchant account should not be able to create API keys. The TIB only mentions verifying role and MFA.
- **Severity.** Medium — omitting the status check is a security gap.
- **Evidence.** `MfaService.beginEnroll/confirm/disable` call `requireActiveAccount`; TIB §12 only lists `MERCHANT` + confirmed MFA.
- **Recommended brief amendment.** Add `AccountStatus.ACTIVE` as a third precondition for `create`, with a clear failure mode (reuse `InvalidAccountStateException` or a new uniform exception). This matches `MfaService`'s established pattern and closes the obvious privilege-escalation hole.

---

## 7. `ApiKeyExceptionHandler` and `ProblemTypes` entries are premature and underspecified

- **Issue.** The TIB says create an `ApiKeyExceptionHandler`, but T24 has no controller. The handler will be untested until T25/T26. The TIB does not specify which `ProblemTypes` URIs to add (e.g., for create-time unauthorized, exchange rejection, not-found/revoked key) or what HTTP status each maps to.
- **Severity.** Low-Medium — creates untested production code and a latent contract mismatch with T25/T26.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` has no API-key types; TIB Files to Create §63-67 lists the handler.
- **Recommended brief amendment.** Either defer `ApiKeyExceptionHandler` to T25/T26 (where it will actually be used and tested) or, if created now, specify the exact exception→status→ProblemType mappings and require at least one service-layer test that exercises each mapped exception through a dummy controller or directly asserts the exception type. Add required `ProblemTypes` entries (e.g., `API_KEY_REJECTED`, `NOT_MERCHANT_OR_MFA_NOT_CONFIRMED`, `API_KEY_NOT_FOUND`).

---

## 8. V7 migration SQL is imprecise

- **Issue.** The TIB states the migration will be `ALTER TABLE api_keys ALTER COLUMN prefix TYPE VARCHAR(32)`. It does not specify the Flyway file content, schema qualification, or whether the existing `idx_api_keys_prefix` index needs handling.
- **Severity.** Low — the SQL is simple, but precision prevents environment-specific failures.
- **Evidence.** TIB §58; `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql` lines 86, 95.
- **Recommended brief amendment.** Provide the exact migration file content:
  ```sql
  ALTER TABLE auth.api_keys ALTER COLUMN prefix TYPE VARCHAR(32);
  ```
  Note that the existing index `idx_api_keys_prefix` remains valid (Postgres does not rebuild it for a widening VARCHAR change) and no data is lost. Match the file-naming convention: `V7__widen_api_key_prefix.sql`.

---

## 9. Mutator vs. conditional-update shape for `lastUsedAt`/`revokedAt` is unresolved

- **Issue.** The TIB says T24 will add "mutators/conditional-update support" for `lastUsedAt` and `revokedAt` but defers the exact shape to Phase 5. The choice has concurrency implications: `lastUsedAt` updates on exchange are inherently racy, and `revoke` must be idempotent.
- **Severity.** Medium — the brief should resolve this before implementation, not after.
- **Evidence.** TIB §18; TIB Constraints §90 (references `RecoveryCodeRepository.markUsed` precedent).
- **Recommended brief amendment.** Specify the repository methods now:
  - `int updateLastUsedAt(Long id, Instant lastUsedAt)` — conditional `@Modifying @Query` that sets `last_used_at = :lastUsedAt WHERE id = :id` (no further condition needed; exchange already verified the row). Return value checked only to detect an unexpectedly missing row.
  - `int revokeIfActive(Long id, Instant revokedAt)` — conditional `@Modifying @Query` that sets `revoked_at = :revokedAt WHERE id = :id AND revoked_at IS NULL`, making revoke idempotent by treating an already-revoked row as a 0-row update that the service accepts.
  Add plain getters on `ApiKey` for these fields; defer setters until a future task needs them.

---

## 10. Audit on exchange rejection when account is unknown is unspecified

- **Issue.** The TIB states that every exchange rejection should be audited, matching `MfaService`'s precedent. For malformed keys or unknown prefixes, there is no owning `accountUuid` to use as the audit target. The brief does not say whether to skip the audit, use a null target, or derive some other identifier.
- **Severity.** Low-Medium — `AuditService.record` expects an `accountUuid` and mirrors it to Kafka; passing null may or may not be supported.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/audit/AuditService.java:57-78`; TIB §60.
- **Recommended brief amendment.** State that exchange rejections with no identifiable account are still audited with `accountUuid = null` (the audit row and mirror event tolerate this), or alternatively, skip the audit only for malformed/unknown-prefix cases and audit all hash-mismatch/revoked/expired cases where the account is known. Do not leave the implementer to guess.

---

## 11. `create` does not specify validation of the key name

- **Issue.** R30 says the caller provides a name. The TIB does not say whether blank or overly long names are rejected. The DDL limits `name` to `VARCHAR(100)`.
- **Severity.** Low.
- **Evidence.** TIB §12; `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql` line 88.
- **Recommended brief amendment.** Specify that `create` rejects blank names and names longer than 100 characters (throwing a validation-domain exception). This can be delegated to T26's request DTO if preferred, but the service should still guard against null/blank since it is the first line of defense.

---

## 12. Key-generation alphabet and entropy source are not specified

- **Issue.** L7 says a "random 24-character alphanumeric suffix" and a "32-character secret." The TIB does not specify the alphabet (A-Z, a-z, 0-9? URL-safe base64?) or that `SecureRandom` must be used. A naive `Random` implementation would be insecure.
- **Severity.** Medium — security-critical detail.
- **Evidence.** TIB §12; `spec/auth-service/design.md` §4a L7.
- **Recommended brief amendment.** Specify: suffix and secret are generated from `SecureRandom` using alphabet `[A-Za-z0-9]`, producing exactly 24 and 32 characters respectively. The full key is `prefix + suffix + "." + secret`. No `java.util.Random`, no reduced alphabet.

---

## 13. No conflict found with `agents.md` or other LOCKED decisions

- L7 is implemented exactly (widening the column rather than shrinking the suffix).
- L12 is respected: no `Account` entity import; `accountId` remains a plain `Long`.
- Only SHA-256 hashes are stored; no plaintext key material persists.
- `spec/` files are not modified.

---

## Open Questions

- **Reverse account-UUID resolver** (finding #2) — must be resolved before implementation.
- **Service return-type records** (finding #3) — should be defined before freezing.
- **Mutator/conditional-update shape** (finding #9) — should be resolved before Phase 5.

(End of artifact)
