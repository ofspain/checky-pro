# auth · T24 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T24 — Key service |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Review of `ApiKeyHasherTest` and `ApiKeyServiceIntegrationTest` against the frozen brief's acceptance criteria. Gaps only.

---

## Confirmed coverage (no gaps)

- **AC1–AC4, AC10 (`shouldCreateApiKeyAndShowPlaintextExactlyOnce`)** — full create flow, plaintext shape, hash-only persistence, 32-character prefix, and `api_key.created` audit all asserted.
- **AC1 role/MFA/status gates** — `createRejectsNonMerchantAccount`, `createRejectsUnconfirmedMfa`, `createRejectsNonActiveAccount` cover the three preconditions.
- **AC1 name validation** — `createRejectsBlankOrOverlongName` covers blank and >100 characters.
- **AC7 constant-time hash compare** — `ApiKeyHasherTest` covers correct-key match, incorrect-key mismatch, and position-independent mismatch rejection.
- **AC9/R33 uniform rejection** — `shouldRejectRevokedOrUnknownApiKeyWithUniform401` covers malformed, unknown prefix, wrong secret, revoked, and expired keys.
- **AC8/R32 `last_used_at` update** — `exchangeUpdatesLastUsedAt` asserts the timestamp moves from null to non-null.
- **Prefix-collision handling** — `exchangeChecksEveryPrefixCollisionCandidate` and `exchangeAuditsTheMatchedAccountEvenWhenItIsNotTheFirstCandidate` prove the loop and audit-target fixes.
- **AC5 list isolation/no-secret-material** — `listReturnsOnlyTheCallersOwnKeysWithNoSecretMaterial` proves cross-account isolation and that metadata carries no hash/secret fields.
- **AC6 revoke ownership boundary** — `revokeOfNonOwnedKeyFails` uses the same exception for non-owned and non-existent keys.
- **Phase 9 idempotency fix** — `revokeIsIdempotent` asserts exactly one `api_key.revoked` audit event after two revoke calls.

---

## Gaps

### 1. Successful `exchange` does not assert the `api_key.exchanged` audit event

- **Gap.** `exchangeUpdatesLastUsedAt` checks the state change but never verifies that `AuditService.record("api_key.exchanged", ...)` actually fired. R32 alone doesn't require an audit, but the service records one and the create/revoke tests both assert audit events — leaving exchange success unaudited is an inconsistent coverage hole.
- **Why it matters.** A regression that dropped the exchange-success audit call would not be caught by any test, even though the audit trail is part of the security contract.
- **Suggested test.** Add to `exchangeUpdatesLastUsedAt` (or as a separate test): after a successful exchange, assert `latestAuditEventType(accountUuid)` is `"api_key.exchanged"`.

---

### 2. `create` does not assert the stored `scopes`

- **Gap.** R30 requires the created key's scope to contain `merchant.api`. `shouldCreateApiKeyAndShowPlaintextExactlyOnce` never reads `stored.getScopes()` or asserts anything about scopes in the result/metadata.
- **Why it matters.** A regression that stored an empty or wrong scope list would pass the current suite, breaking the contract T25's JWT minting relies on.
- **Suggested test.** In `shouldCreateApiKeyAndShowPlaintextExactlyOnce`, add `assertThat(stored.getScopes()).containsExactly("merchant.api")`. Alternatively, assert the same on `apiKeyService.list(accountUuid).getFirst().scopes()`.

---

### 3. `exchange` result scopes are not asserted

- **Gap.** `ExchangeResult` carries `scopes`, but no test asserts they are returned. `exchangeChecksEveryPrefixCollisionCandidate` only checks `accountUuid`.
- **Why it matters.** T25 will mint a JWT whose `scope` claim comes from this result; a silent regression that returned an empty scope list would not be caught here.
- **Suggested test.** In `exchangeUpdatesLastUsedAt` or `exchangeChecksEveryPrefixCollisionCandidate`, add `assertThat(result.scopes()).containsExactly("merchant.api")`.

---

### 4. Malformed-key boundaries are incomplete

- **Gap.** `shouldRejectRevokedOrUnknownApiKeyWithUniform401` uses `"not-a-valid-key-shape"` for the malformed case, but does not test the dot-with-empty-prefix boundary (e.g., `".secret"`) or dot-with-empty-secret boundary (e.g., `"ck_live_prefix."`).
- **Why it matters.** The service's malformed-key parser (`separator <= 0 || separator == presentedKey.length() - 1`) is specifically designed to reject these two shapes. Without testing them, a refactor could silently change the malformed-key definition.
- **Suggested test.** Add two more `assertThatThrownBy(...).isInstanceOf(ApiKeyExchangeRejectedException.class)` cases in the uniform-rejection test: `".abcdefghijklmnopqrstuvwxyzabcdef"` and `"ck_live_validprefixshape00000000."`.

---

### 5. `revoke` with an unknown key UUID is not explicitly tested

- **Gap.** `revokeOfNonOwnedKeyFails` covers the "exists but not owned" case. The "doesn't exist at all" case is logically covered because the same exception is thrown, but there is no dedicated assertion for it.
- **Why it matters.** The no-enumeration contract depends on both paths producing the exact same exception type and no distinguishing side effects. A dedicated test makes the contract explicit and protects against a future change that accidentally distinguishes the two.
- **Suggested test.** `revokeOfUnknownKeyFails`: call `apiKeyService.revoke(accountUuid, UUID.randomUUID())` and assert `ApiKeyNotFoundException`.

---

### 6. `list` does not assert revoked keys appear (or don't appear)

- **Gap.** `listReturnsOnlyTheCallersOwnKeysWithNoSecretMaterial` only creates active keys. R34 says "their keys with metadata" but does not explicitly include or exclude revoked keys. The service currently returns revoked keys too, but this behavior is untested.
- **Why it matters.** A future change that filtered out revoked keys (or accidentally included another account's revoked keys) would change the API contract without failing this suite.
- **Suggested test.** `listIncludesRevokedKeys` (or `listExcludesRevokedKeys`, whichever matches the intended contract): create a key, revoke it, then call `list` and assert whether the revoked key is present with a non-null `revokedAt`.

---

### 7. `ApiKeyHasherTest` does not exercise the constant-time property against a real timing oracle

- **Gap.** The hasher tests verify correctness, not constant-time behavior. The test comment honestly notes this is not a timing measurement. This is acceptable for a unit test, but the service-level uniform-rejection test also does not use timing assertions.
- **Why it matters.** The task statement explicitly mandates constant-time comparison. A test cannot truly prove constant-time without specialized tooling, but the current tests don't even attempt a coarse statistical check.
- **Suggested test.** Optional and low priority: add a micro-benchmark-style test (with many iterations and a generous tolerance) that compares rejection times for first-character vs. last-character hash mismatches and asserts they are statistically indistinguishable. Document it as a smoke test, not a cryptographic proof.

---

### 8. MERCHANT role obtained via role template is not tested

- **Gap.** `seedMerchantWithConfirmedMfa` assigns the `MERCHANT` role directly. `RoleService.resolveEffectiveRoles` also expands role templates, so a merchant who holds `MERCHANT` only via a template should also pass the gate.
- **Why it matters.** A subtle bug that only checked direct assignments (e.g., by bypassing `resolveEffectiveRoles`) would pass the current suite but break template-based merchants.
- **Suggested test.** `createAcceptsMerchantViaRoleTemplate`: create a role template containing `MERCHANT`, assign it to an account, and assert `apiKeyService.create` succeeds.

---

## Open Questions

None. The missing `ApiKeyExceptionHandler` and `ProblemTypes` entries identified in Phase 8 are production-code gaps, not test gaps, and will be exercised by T25/T26 tests rather than T24 service-layer tests.

(End of artifact)
