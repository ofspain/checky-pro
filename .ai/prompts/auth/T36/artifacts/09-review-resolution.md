<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T36 · Phase 9 — Review Resolution

**Human decision:** approve the recommended disposition — accept 9, reject 2 (Kimi Findings 9 and
11), each verified against source before disposition.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| Self-review 1 / Kimi 1 | Overstated "confirmed CSRF-exempt" claim for `/admin/accounts/.../roles/{roleName}` | **Accepted** | Rewrote `fetchCsrfContext`'s Javadoc: only `/accounts` register (this test's own live run) and `/api-keys`/`/api-keys/token` (`ApiKeyLifecycleIntegrationTest`'s precedent) are evidenced; the admin call is now stated as *expected*, not confirmed, with no precedent test anywhere in the codebase. |
| Self-review 2 / Kimi 2 | `payload.get("purpose")`/`.get("token")` NPE risk on an unexpected message shape | **Accepted** | `awaitRawVerificationToken`: null-checks both `JsonNode`s before calling `.asText()`. |
| Kimi 3 | AC6 session-list assertion doesn't check required fields | **Accepted** | Added assertions for `createdAt`/`rotatedAt` non-blank and `deviceLabel` field presence (documented as always-null today, per `SessionResponse`'s own Javadoc). |
| Kimi 4 | AC5 claim assertions incomplete vs. L9/token-claims.md | **Partially accepted** | Added `token_type`/`expires_in` (600s, matching the configured 10-minute TTL) assertions on the response envelope — genuinely new coverage. **Rejected** the request to also assert the remaining L9 claims (`iss`, `exp`, `iat`, `nbf`, `jti`, `roles`, `client_id`, `email_verified`) — `ApiKeyTokenIssuerTest` already asserts every one of them exhaustively (verified: lines 86-101); duplicating that here adds no new coverage. |
| Kimi 5 | AC4 create-response shape under-asserted | **Partially accepted** | Added the hash-leak guard (`doesNotContainPattern("[a-f0-9]{64}")`), matching `ApiKeyLifecycleIntegrationTest`'s established security-relevant check. **Rejected** duplicating the full `keyUuid`/`name`/`createdAt` field-set assertion — AC4 as written in the frozen brief ("returns a `ck_live_`-prefixed plaintext key exactly once") is fully covered by the existing regex plus the new hash-leak guard; the full field-set shape is already `ApiKeyLifecycleIntegrationTest`'s job. |
| Kimi 6 | AC1 doesn't directly assert the account status transition | **Accepted** | Added `assertThat(accountService.getByUuid(merchantUuid).status()).isEqualTo(AccountStatus.ACTIVE)` immediately after the Kafka event assertion. |
| Kimi 7 | AC7 doesn't verify the live SAS authorization is removed | **Accepted** | Autowired `OAuth2AuthorizationService`; added `assertThat(authorizationService.findById(reloaded.getAuthorizationId())).isNull()`, matching `SessionIntegrationTest.shouldRevokeSingleSessionFamily`'s equivalent assertion. |
| Kimi 8 | Pre-enrollment blocked-login assertion omits HTTP status | **Accepted** | Added `assertThat(preEnrollment.loginResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND)` before the location assertion. |
| Kimi 9 | TOTP code may age across the multi-hop authorize flow | **Rejected** | Verified false: `SasLoginIntegrationTest.confirmedMfaRequiresCodeToFinishAuthorizeFlow` and `.issuedTokenHasOtpAmrAndAcrAfterMfa` (both already-passing, already-accepted) use the identical `referenceGenerateCode(secret, Instant.now())`-before-`attemptFullAuthorizeFlow` pattern this test uses. Kimi's own Phase 3 finding for this same task already established the server's 90-second verification tolerance, which comfortably covers a code generated at the start of a multi-hop round trip completing in milliseconds on localhost. No change. |
| Kimi 10 | `auth.user.lifecycle` matching is brittle string-containment | **Accepted** | `awaitUserRegisteredLifecycleEvent` now parses the record value via `objectMapper.readTree` and checks `payload.get("status")` (null-safe), matching the technique already used in `awaitRawVerificationToken`. |
| Kimi 11 | `earliest`-offset Kafka consumer may be slow in a much-larger future suite | **Rejected** | Speculative future-scale concern, not a current defect — matches `AccountPersistenceIntegrationTest`'s own already-accepted `earliest`+random-group-id pattern exactly. No evidence the current 15s timeout is tight at today's suite size. Per this session's standing practice of not designing for hypothetical future requirements, no change. |

## Verification after applying fixes

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest=EndToEndLifecycleIntegrationTest` — re-run against real Docker
  Testcontainers. Registration + CSRF fix still passes (progressed past the same point as before);
  still blocked at the same, already-logged, pre-existing Kafka producer→broker connectivity
  environment issue (unchanged, not reintroduced or worsened by these fixes). No regression from the
  9 accepted changes.

---

**Phase 9 complete — review resolved, fixes applied, human-approved.** Proceed to Phase 10 (Test
Generation) on approval.
