<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T36 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T36 — End-to-end integration test |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Reviewed the completed implementation in `services/auth/src/test/java/com/themistra/auth/EndToEndLifecycleIntegrationTest.java` and the Phase 7 self-review with fresh eyes. Findings only.

---

## Finding 1 — CSRF-exemption claim for `/admin/accounts/.../roles/...` is unverified

**Issue.** The class Javadoc and implementation notes state that Bearer/API-key-authenticated calls — including `POST /admin/accounts/{accountUuid}/roles/{roleName}` — are "empirically unaffected" by CSRF, citing `ApiKeyLifecycleIntegrationTest` as precedent. That precedent only exercises `/api-keys/**`; no existing test in this codebase calls the admin role-assignment endpoint over real HTTP. The only live run performed timed out before reaching this line, so the claim has not been validated.

**Evidence.**
- `EndToEndLifecycleIntegrationTest.java` lines 280-287 (class Javadoc) and implementation notes "Deviations forced by reality" §1.
- `ApiKeyLifecycleIntegrationTest` calls only `/api-keys` and `/api-keys/token`.
- `grep -rln "/admin/accounts/" src/test/java` returned no matches for the roles path.

**Recommendation.** Correct the Javadoc and Phase 6 notes to say "expected to be CSRF-exempt (stateless Bearer auth) but not yet exercised by a live run." Once the Kafka environment blocker clears, verify the call passes; if it fails with the same CSRF-routing behavior seen for `/accounts`, apply the same CSRF-context fix.

**Confidence.** High.

---

## Finding 2 — Kafka payload access is not null-safe

**Issue.** `awaitRawVerificationToken` reads `payload.get("purpose").asText()` and `payload.get("token").asText()` without checking whether the `JsonNode` is null. The current contract requires both fields, and current production code always produces them, but a differently-shaped message on the shared topic would throw an unhandled `NullPointerException` inside the Awaitility lambda rather than producing a clear assertion failure.

**Evidence.** `EndToEndLifecycleIntegrationTest.java` lines 373-375.

**Recommendation.** Guard each field: skip the record if `payload.get("purpose")` or `payload.get("token")` is null before calling `.asText()`.

**Confidence.** High.

---

## Finding 3 — AC6 session-list assertion does not verify the required fields

**Issue.** AC6 requires the session list to return active families with required fields (device label, created, last-rotated). The test asserts the list has size 1 and extracts `familyId`, but never asserts `deviceLabel`, `createdAt`, or `rotatedAt`. A regression that dropped or malformed those fields would not be caught.

**Evidence.**
- `EndToEndLifecycleIntegrationTest.java` lines 260-264.
- `SessionResponse` record: `familyId`, `deviceLabel`, `createdAt`, `rotatedAt`.
- Business rules (TIB §35): "active families (device label, created, last-rotated)."

**Recommendation.** Add assertions for `createdAt` and `rotatedAt` being present/non-blank, and for `deviceLabel` being either present or null as expected by the authorize flow.

**Confidence.** High.

---

## Finding 4 — AC5 API-key JWT claim assertions are incomplete against `token-claims.md`

**Issue.** AC5 says the exchanged JWT must match the API-key claim contract. The test asserts `sub`, `scope`, and `amr`, but does not assert `email_verified`, `exp`, `iat`, `nbf`, `jti`, `iss`, `aud`, `roles`, or `client_id` — all listed in L9/token-claims.md. It also does not assert the `token_type` or `expires_in` fields of the OAuth2 response envelope, so the 10-minute TTL claim is not verified.

**Evidence.**
- `EndToEndLifecycleIntegrationTest.java` lines 249-256.
- `spec/auth-service/design.md` L9 / `contracts/api/token-claims.md`: exact access-token claim set.
- `ApiKeyTokenResponse` record: `accessToken`, `tokenType`, `expiresIn`.

**Recommendation.** Assert `token_type` is `Bearer` and `expires_in` equals 600 (or within tolerance). Add presence assertions for the L9 claims that are expected on an API-key-exchanged token (at minimum `email_verified`, `exp`, `roles`, `client_id`).

**Confidence.** High.

---

## Finding 5 — AC4 API-key create response shape is under-asserted

**Issue.** AC4 requires `POST /api-keys` to return a `ck_live_`-prefixed plaintext key exactly once. The test asserts the status and the `plaintextKey` regex, but does not assert the presence or shape of `keyUuid`, `name`, and `createdAt`, nor does it guard against a hash-shaped value leaking in the response (the pattern `ApiKeyLifecycleIntegrationTest` already established).

**Evidence.**
- `EndToEndLifecycleIntegrationTest.java` lines 242-246.
- `ApiKeyService.CreateApiKeyResult` record: `keyUuid`, `plaintextKey`, `name`, `createdAt`.
- `ApiKeyLifecycleIntegrationTest` lines 116-121 asserts the full field set and excludes hash-shaped values.

**Recommendation.** Mirror `ApiKeyLifecycleIntegrationTest`'s assertions: verify the field set is exactly `keyUuid`, `plaintextKey`, `name`, `createdAt`, and that the response body does not contain a 64-hex-character hash.

**Confidence.** High.

---

## Finding 6 — AC1 does not directly assert the account status transition

**Issue.** AC1 says registration + verification transitions the account from `PENDING_VERIFICATION` to `ACTIVE`. The test asserts the `auth.user.lifecycle(ACTIVE)` Kafka event, which is a proxy, but never reads the account's persisted status. A bug that emitted the event while leaving the account in `PENDING_VERIFICATION` would not be caught.

**Evidence.**
- `EndToEndLifecycleIntegrationTest.java` lines 193-210.
- AC1 (TIB §90): "registration + verification transitions `PENDING_VERIFICATION` → `ACTIVE` via HTTP."

**Recommendation.** Add a direct assertion after `verifyEmailViaHttp`, e.g., `assertThat(accountService.getByUuid(merchantUuid).status()).isEqualTo(AccountStatus.ACTIVE);`.

**Confidence.** Medium.

---

## Finding 7 — AC7 does not verify removal of the live SAS authorization

**Issue.** R37 / AC7 requires that session revoke removes both the family row and the live SAS authorization. The test verifies the family row's `revokedAt`/`revokedReason` and an empty follow-up list, but does not assert that the underlying `OAuth2Authorization` row is gone. `SessionIntegrationTest.shouldRevokeSingleSessionFamily` checks both.

**Evidence.**
- `EndToEndLifecycleIntegrationTest.java` lines 266-273.
- `SessionIntegrationTest` lines 152-154: `assertThat(authorizationService.findById(authorizationId)).isNull();`.
- R37 (TIB §36): "revokes family + live SAS authorization."

**Recommendation.** Either capture the interactive-login authorization ID during the authorize/token exchange and assert its removal, or add a comment documenting that the empty list is treated as sufficient proof. The stronger approach (assert removal) is preferred.

**Confidence.** Medium.

---

## Finding 8 — Pre-enrollment blocked-login assertion omits HTTP status

**Issue.** The test asserts that the post-login `Location` contains `/login?error`, but does not assert that the response status is `FOUND`. A non-302 response with a `Location` header containing that substring would satisfy the assertion without proving the SAS form-login redirect behavior.

**Evidence.** `EndToEndLifecycleIntegrationTest.java` lines 222-225.

**Recommendation.** Add `assertThat(preEnrollment.loginResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);` before the location assertion.

**Confidence.** Medium.

---

## Finding 9 — TOTP code may age across the full authorize flow

**Issue.** The test generates the TOTP code with `Instant.now()` immediately before `attemptFullAuthorizeFlow`, but the authorize flow involves multiple HTTP round-trips (`/oauth2/authorize` → `/login` → `/oauth2/authorize` → `/oauth2/token`). If the code is generated near the end of a 30-second window, server-side validation may occur in the next window and reject the code, producing flaky failures.

**Evidence.**
- `EndToEndLifecycleIntegrationTest.java` lines 234-235.
- `SasLoginIntegrationTest.attemptLoginWithFreshTotpCode` (lines 470-472) generates the code immediately before the single `/login` POST, which is a shorter path.

**Recommendation.** Adopt the `attemptLoginWithFreshTotpCode` pattern: generate the TOTP code inside the helper that posts the login form, so the code is as fresh as possible at submission time. The `referenceGenerateCode(secret, Instant.now())` call should move into `postLoginForm` or a wrapper.

**Confidence.** Medium.

---

## Finding 10 — `auth.user.lifecycle` event matching is brittle to JSON formatting

**Issue.** `awaitUserRegisteredLifecycleEvent` matches the registered event by `record.value().contains("\"status\":\"ACTIVE\"")`. This depends on Jackson's exact serialization formatting (no spaces, specific field order). A future change to serialization settings or a schema evolution that adds fields would break this assertion even if the semantic event is correct.

**Evidence.** `EndToEndLifecycleIntegrationTest.java` lines 391-392.

**Recommendation.** Parse the record value with `objectMapper.readTree` and assert `payload.get("status").asText().equals("ACTIVE")`, the same way `awaitRawVerificationToken` already handles the email-requested payload.

**Confidence.** Low.

---

## Finding 11 — Shared Kafka broker with `earliest` offset may be slow in a large suite

**Issue.** The consumer uses `auto.offset.reset=earliest` and a random group ID, so every test method replays the entire topic from the beginning. As the integration suite grows, the 15-second Awaitility timeout may become tight if the consumer has to scan many prior messages before reaching the relevant ones.

**Evidence.**
- `EndToEndLifecycleIntegrationTest.java` lines 172-179.
- Class Javadoc lines 97-108 acknowledges the shared-container hazard.

**Recommendation.** Consider seeking to the end of each topic in `@BeforeEach` before producing new messages, or use a topic-partition assignment with `seekToEnd`. This preserves test isolation without replaying unrelated history.

**Confidence.** Low.

---

## Summary

The test correctly composes the full lifecycle and handles the major CSRF and Kafka-correlation hazards. The highest-impact gaps are incomplete assertions against the acceptance criteria (AC4, AC5, AC6, AC7) and the two self-review issues (unverified CSRF claim and null-unsafe Kafka access). Fixing these would make the test materially stronger without changing its overall shape.

(End of Phase 8 independent review.)
