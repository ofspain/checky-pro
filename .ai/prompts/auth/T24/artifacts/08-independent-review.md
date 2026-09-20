# auth · T24 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T24 — Key service |
| **Consumes** | `artifacts/07-self-review.md` + Phase 6 implementation |
| **Produces** | `artifacts/08-independent-review.md` |

Independent adversarial review of the T24 implementation. Findings only.

**Review limitation:** `mvn` is not available in this environment, so compilation, `ArchitectureTest`, and Testcontainers tests could not be re-run. Findings below are based on static analysis of the `apikey/` package, `common/Hashing.java`, `application.properties`, and `V7__widen_api_key_prefix.sql`.

---

## 1. `ApiKey.prefix` still maps `length = 16`, but `V7` widened the column to `VARCHAR(32)`

- **Issue.** `ApiKey.java:45` annotates `prefix` with `@Column(..., length = 16, ...)`, while `V7__widen_api_key_prefix.sql` changes the actual column to `VARCHAR(32)`. With `spring.jpa.hibernate.ddl-auto=validate`, Hibernate compares entity mappings against the real schema at startup. A declared length of 16 against a `VARCHAR(32)` column is a schema-validation mismatch and will likely cause context startup failure.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java:45`; `services/auth/src/main/resources/db/migration/V7__widen_api_key_prefix.sql:7`.
- **Recommendation.** Update `ApiKey.prefix` to `@Column(name = "prefix", nullable = false, length = 32, updatable = false)`. This is not a semantic mapping change — it is updating the mapping to reflect the schema change the migration already made. Add a regression assertion in the T23/T24 persistence/integration tests that a 32-character prefix round-trips.
- **Confidence.** High.

---

## 2. `ApiKeyExceptionHandler` was required by the brief but is missing

- **Issue.** The frozen brief lists `ApiKeyExceptionHandler.java` under Files to Create, but no such class exists in `apikey/` or anywhere else. `ApiKeyService` throws `ApiKeyNotAuthorizedException`, `ApiKeyNotFoundException`, and `ApiKeyExchangeRejectedException`; without a handler, these propagate to the global `ApiExceptionHandler` and become `500 Internal Server Error`. In particular, `ApiKeyExchangeRejectedException` must map to `401 Unauthorized` for R33, and `ApiKeyNotFoundException` should map to `404`.
- **Evidence.** TIB Files to Create; `glob` for `*Handler.java` in `apikey/` returns nothing; `grep` for the exception classes finds only their definitions and throw sites.
- **Recommendation.** Either create `ApiKeyExceptionHandler` now with explicit mappings (preferred), or formally defer it to T25/T26 in the phase artifacts and note that the service-layer exceptions are currently untested at the HTTP boundary. Do not leave the brief listing the file as created when it is absent.
- **Confidence.** High.

---

## 3. `ProblemTypes` has no entries for API-key failures

- **Issue.** `ProblemTypes` is described as part of the public API contract and says "Add new types here; never inline URIs at call sites." The new API-key exceptions need stable problem-type URIs, but none were added. This blocks the exception handler from following the codebase's own convention.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` (no API-key types).
- **Recommendation.** Add at least `API_KEY_REJECTED` (for exchange failures, mapped to 401) and `API_KEY_NOT_FOUND` (for revoke failures, mapped to 404) to `ProblemTypes`. If `ApiKeyExceptionHandler` is deferred, these entries should still be added now so the contract stabilizes before T25/T26.
- **Confidence.** High.

---

## 4. `exchange` audit target misattributes rejections when multiple candidates share a prefix

- **Issue.** In the `!eligible` branch, `auditAccountUuid` is always resolved from `candidates.getFirst().getAccountId()`, even when a specific, different candidate (`matched`) had the correct hash but is revoked or expired. If two keys share a prefix, a revoked-key usage attempt against account B could be audited against account A.
- **Evidence.** `ApiKeyService.java:150-167`.
- **Recommendation.** When `matched != null`, resolve the audit UUID from `matched.getAccountId()`; only fall back to `candidates.getFirst()` when `matched == null` (genuine hash mismatch across all candidates).
- **Confidence.** High.

---

## 5. `revoke` records an audit event even when no state change occurred

- **Issue.** `ApiKeyService.revoke` discards the return value of `revokeIfActive` and unconditionally records `api_key.revoked`. Revoking an already-revoked key produces a duplicate audit event for a non-existent state change, contradicting the established idempotency convention in `RoleService.removeRole`.
- **Evidence.** `ApiKeyService.java:117-118`; `RoleService.java:119-127`.
- **Recommendation.** Only call `recordAudit(...)` when `revokeIfActive` returns `1`; when it returns `0`, the key was already revoked and no audit event should be emitted.
- **Confidence.** High.

---

## 6. `exchange` malformed/unknown-prefix paths skip the constant-time comparison

- **Issue.** The malformed-key and unknown-prefix checks return before calling `apiKeyHasher.matches(...)`. This creates a measurable timing difference between obviously malformed/unknown prefix and well-formed key, wrong secret. The practical value to an attacker is low, but it is a timing signal the implementation otherwise explicitly tries to close.
- **Evidence.** `ApiKeyService.java:137-148` vs. `:150-156`.
- **Recommendation.** Optional but recommended: perform a dummy `apiKeyHasher.matches(presentedKey, DUMMY_HASH)` before rejecting on malformed/unknown-prefix paths to normalize timing. The dummy hash can be a fixed 64-character hex constant.
- **Confidence.** Medium.

---

## 7. `ApiKey` entity Javadoc is now stale about mutators

- **Issue.** The Javadoc states that whichever task next needs to update those fields adds the mutator then, and specifically names T24 as the task that would add a `lastUsedAt` mutator. T24 instead uses conditional repository queries (`updateLastUsedAt`, `revokeIfActive`) without adding entity mutators. The Javadoc is therefore misleading about the actual design.
- **Evidence.** `ApiKey.java:24-29`.
- **Recommendation.** Update the Javadoc to say that T24 updates `lastUsedAt`/`revokedAt` via conditional repository queries, not entity mutators, and that mutators for `name` remain deferred to a future rename feature if ever needed.
- **Confidence.** Low.

---

## 8. No test coverage for the service layer

- **Issue/Non-issue.** No `ApiKeyServiceTest` or integration test exists yet. Per this codebase's phase model, service-layer tests for T24 belong in Phase 10, so this is expected rather than a defect. However, several of the findings above (#1, #4, #5) are exactly the kind of subtle bugs a service-layer test would catch.
- **Evidence.** `services/auth/src/test/java/com/themistra/auth/apikey/` contains only `ApiKeyPersistenceIntegrationTest.java`.
- **Recommendation.** Ensure Phase 10 tests explicitly cover: (a) schema validation starts successfully after V7, (b) exchange with multiple candidates sharing a prefix audits the matched row, (c) revoking an already-revoked key emits no duplicate audit event, and (d) `ApiKeyExceptionHandler` maps exceptions to the correct HTTP statuses once it exists.
- **Confidence.** Medium.

---

## 9. `exchange` does not verify the owning account is still ACTIVE

- **Issue.** `exchange` checks `revokedAt` and `expiresAt` but does not verify the account itself is still `ACTIVE`. A suspended or deleted merchant's stored key would still exchange successfully until revoked or expired. R33 lists only revoked/expired/malformed/mismatched as rejection reasons, so this is arguably out of scope, but it is a latent authorization gap.
- **Evidence.** `ApiKeyService.java:159-161`.
- **Recommendation.** Consider adding an account-status check in `exchange` (resolve `accountUuid` and verify `AccountStatus.ACTIVE`) and failing with the same uniform `ApiKeyExchangeRejectedException`. If this is intentionally out of scope for T24, document the decision explicitly so T25/T26 do not assume it is enforced.
- **Confidence.** Medium.

---

## 10. No conflict found with `agents.md` or other LOCKED decisions

- L7 is implemented exactly: `ck_live_` prefix + 24-char suffix + `.` + 32-char secret, SHA-256 hash stored.
- L12 is respected: no `Account` entity import; `accountId` remains a plain `Long`.
- Only hashes are persisted; plaintext keys exist only transiently in `CreateApiKeyResult`.
- `spec/` files are not modified.

---

## Open Questions

None beyond the deferred `ApiKeyExceptionHandler`/ProblemTypes entries (findings #2 and #3), which should be resolved before T25's HTTP endpoint relies on them.

(End of artifact)
