# auth · T26 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T26 — API-key CRUD controller |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Review of the Phase 10 test suite against the frozen brief (`artifacts/04-frozen-task-brief.md`) and acceptance criteria AC1–AC12. Findings only — no test code changed in this phase.

---

## Gaps

### 1. Integration rejection tests do not assert the problem type, title, or content type

- **Gap:** `createRejectsCallerLackingMerchantRoleOrConfirmedMfa` checks status 403 and that the body string does not contain "detail," but it does not assert the response is `application/problem+json`, has `type = API_KEY_NOT_AUTHORIZED`, or has the expected title. `deleteOfUnownedKeyAndNonexistentKeyAreByteIdentical` uses `rejectionBody`, which only checks status and absence of `detail`, not the problem type/title/content-type. `createRejectsBlankOrOverLengthName` checks status 400 and that the rejected value is not echoed, but not that the `type` is `validation-error`.
- **Why it matters:** AC8 (403 mapping), AC7/AC9 (404 mapping), and R46 (RFC 9457 shape) require specific, stable problem bodies. Without asserting `type`/`title`/content-type, the tests would pass even if the handler returned plain JSON or the wrong problem-type URI.
- **Suggested test:** Extend `rejectionBody` (or add per-test assertions) to assert `Content-Type` contains `application/problem+json`, and to assert exact `type` and `title` values for 400/403/404 cases. For 400, assert `type = ProblemTypes.VALIDATION_ERROR` and `title = "Validation failed"`.

---

### 2. No test asserts the exact field set of the `POST /api-keys` response

- **Gap:** `shouldCreateApiKeyAndShowPlaintextExactlyOnce` asserts the presence of `plaintextKey`, `name`, and `keyUuid`, and the absence of `keyHash`/64-hex values, but it does not assert that the JSON object contains exactly four fields (`keyUuid`, `plaintextKey`, `name`, `createdAt`).
- **Why it matters:** AC6 requires that no response body contains unexpected secret material or internal fields. An extra field (e.g., a future `accountUuid` or `keyHash` addition) could leak internal identifiers without failing the current test.
- **Suggested test:** Add `assertThat(body.fieldNames().toIterable()).containsExactlyInAnyOrder("keyUuid", "plaintextKey", "name", "createdAt")` to the named create test.

---

### 3. No test asserts `GET /api-keys` response items contain the expected fields and no unexpected ones

- **Gap:** `shouldListAndRevokeOwnApiKeys` asserts `revokedAt` changes from null to non-null, and `listResponseContainsNoHashShapedField` asserts no 64-hex/hash-shaped value. No integration test asserts the presence of `keyUuid`, `name`, `scopes`, `createdAt`, `lastUsedAt`, `expiresAt`, and `revokedAt`, or the absence of `plaintextKey`/`keyHash`.
- **Why it matters:** AC5/AC6 require list responses to contain only metadata and never secret material. A serialization change that dropped a field or accidentally included `plaintextKey` would not be caught.
- **Suggested test:** Add a dedicated integration test (or extend `shouldListAndRevokeOwnApiKeys`) that asserts the first list item's field names are exactly `{keyUuid, name, scopes, createdAt, lastUsedAt, expiresAt, revokedAt}` and that `plaintextKey` and `keyHash` are absent.

---

### 4. No positive boundary test for a 100-character name

- **Gap:** `createRejectsBlankOrOverLengthName` covers blank and 101-character names (rejected), but no integration or unit test verifies that exactly 100 characters is accepted.
- **Why it matters:** The DTO's `@Size(max = 100)` and `ApiKeyService.requireValidName`'s `length() > 100` must agree at the boundary. An off-by-one in either direction (e.g., `@Size(max = 99)`) would be caught only by testing the inclusive boundary.
- **Suggested test:** Add an integration test (or extend the existing boundary test) that creates a key with `"x".repeat(100)` and asserts 201 CREATED.

---

### 5. The known malformed-UUID `DELETE` behavior is not captured by any test

- **Gap:** Phase 8 identified that `DELETE /api-keys/not-a-uuid` produces a 500 due to the missing `MethodArgumentTypeMismatchException` handler. No test documents or verifies this behavior.
- **Why it matters:** The frozen brief's Constraints section claims malformed UUIDs 400, which is inaccurate. A test that records the actual behavior prevents a future maintainer from assuming it works as documented and makes the service-wide gap visible.
- **Suggested test:** Add a low-priority integration test sending `DELETE /api-keys/not-a-uuid` with a valid bearer token and assert status 500 (or 400 if a cross-cutting fix is applied first). Javadoc should explicitly note this is documenting a pre-existing limitation, not desired behavior.

---

### 6. `POST /api-keys` response does not assert `createdAt` is present or well-formed

- **Gap:** `shouldCreateApiKeyAndShowPlaintextExactlyOnce` does not assert that `createdAt` exists in the response or that it is a parseable timestamp.
- **Why it matters:** `createdAt` is part of the locked response contract (D2). Its absence would still pass the current test, breaking the contract silently.
- **Suggested test:** Add `assertThat(body.has("createdAt")).isTrue()` and optionally parse it as an ISO-8601/epoch value (depending on the project's Jackson `Instant` serialization config).

---

### 7. `ApiKeyExceptionHandlerTest` does not assert content-type serialization

- **Gap:** The unit tests for `onNotFound` and `onNotAuthorized` verify the `ProblemDetail` object's status/type/title, but not that Spring serializes it as `application/problem+json`.
- **Why it matters:** A handler that returns `ProblemDetail` is normally serialized correctly by Spring, but the test does not prove the full HTTP response shape. This is already covered indirectly by integration tests once they run, but the unit test layer does not stand alone on content-type.
- **Suggested test:** Low priority — acceptable to leave as-is because `ApiKeyCrudIntegrationTest` will exercise the real response content-type once Docker is available. If unit-level coverage is desired, use `MockMvc` or serialize the `ProblemDetail` manually and assert the `Content-Type` header is set.

---

### 8. `ApiKeyCrudIntegrationTest` has not been executed

- **Gap:** The Phase 10 notes state Docker is unavailable, so the full-stack integration tests — including both named tests and all boundary tests — were compiled but not run.
- **Why it matters:** These tests cover AC1–AC12 at the HTTP/DB layer. Compilation alone does not prove the tests pass or that the filter chain, exception handlers, and native queries interact correctly.
- **Suggested test:** No new test needed — run `ApiKeyCrudIntegrationTest` with a working Docker daemon before the Phase 12 final verification gate.

---

## Non-Gaps (Confirmed Coverage)

- **Caller identity from `Authentication`:** covered by `createDerivesCallerFromAuthenticationNotRequestBody` and `listDerivesCallerFromAuthentication`.
- **No `Location` header:** covered at unit level by `createResponseHasNoLocationHeader`.
- **Plaintext key shape and one-time appearance:** covered by `shouldCreateApiKeyAndShowPlaintextExactlyOnce`.
- **Idempotent revoke:** covered by `deleteOfAlreadyRevokedKeyReturns204`.
- **Uniform 404 for unowned vs. nonexistent key:** covered by `deleteOfUnownedKeyAndNonexistentKeyAreByteIdentical`.
- **No rejected-value leakage on validation failures:** covered by `createRejectsBlankOrOverLengthName`.
- **No hash-shaped field in list:** covered by `listResponseContainsNoHashShapedField`.
- **Cross-module exception routing (`InvalidAccountStateException` → 409):** covered by `createRejectsNonActiveAccountWith409`.
- **Trailing whitespace trim:** addressed by T25's `trailingWhitespaceAfterCredentialIsTrimmed` (already in `ApiKeyControllerTest`).
- **Snake_case serialization of `ApiKeyTokenResponse`:** addressed by T25's `responseSerializesWithSnakeCaseFieldNames`.

---

**Phase 11 complete — test review written.** Proceed to Phase 12 (Final Verification) on approval.
