<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T36 · Phase 5 — Implementation Plan

No production code is planned — every item below traces to the frozen brief's "Files to Create"
(one test class) and "Files NOT to Modify" (everything else).

## Files to create

- `services/auth/src/test/java/com/themistra/auth/EndToEndLifecycleIntegrationTest.java`
  (top-level package, alongside `ArchitectureTest` — the scenario spans `account`/`authn`/`authz`/
  `mfa`/`apikey`/`token`, no single feature-module package owns it; matches how this codebase already
  places cross-cutting tests).

## Files to modify

None.

## New class: `EndToEndLifecycleIntegrationTest`

`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Import(TestcontainersConfiguration.class)` —
same shape as `SessionIntegrationTest`/`ApiKeyLifecycleIntegrationTest`. Autowired collaborators:
`TestRestTemplate` (or `RestTemplate` + captured `@LocalServerPort`, matching whichever sibling
convention `TestcontainersConfiguration` implies), `AccountService`, `RoleService`, `MfaService`,
`RoleService`, `ApiKeyTokenIssuer`, `RefreshTokenFamilyRepository`, `KafkaContainer`, `ObjectMapper`.

### Public methods

- `void shouldCompleteFullMerchantIdentityLifecycle() throws Exception` — the sole `@Test`, composing
  every acceptance criterion (AC1–AC7) as one ordered flow, matching the frozen brief's "one composed
  end-to-end test method" requirement.

### Private methods (test-local helpers; none touch production code)

| Method | Purpose | Modeled on |
|---|---|---|
| `subscribeToLifecycleAndEmailTopics()` (`@BeforeEach`) | subscribes a test `KafkaConsumer<String,String>` to `auth.user.lifecycle` and `auth.email.requested` | `AccountPersistenceIntegrationTest.subscribeToLifecycleTopic` |
| `closeConsumer()` (`@AfterEach`) | closes the consumer | same |
| `registerViaHttp(String email)` | `POST /accounts` with `RegisterAccountRequest`, asserts `202` | new — HTTP, not `SasLoginIntegrationTest.registerAndActivate`'s direct-call shortcut (frozen brief AC1 requires HTTP) |
| `awaitRawVerificationToken(String email)` | polls the subscribed consumer (Awaitility) for an `auth.email.requested` record whose `accountUuid`/`purpose="verify_email"` matches, returns the raw `token` field | `EmailRequestedEventPayload` shape (T33); consumer pattern per above |
| `verifyEmailViaHttp(String token)` | `POST /accounts/verify-email` with `VerifyEmailRequest`, asserts success | new, real HTTP |
| `awaitUserRegisteredEvent(UUID accountUuid)` | polls the same consumer for an `auth.user.lifecycle` record for this account with the registered event type, satisfies AC1's event half (Finding 5) | `AccountPersistenceIntegrationTest` pattern |
| `bootstrapAdminBearerToken()` | direct `accountService.register`/`activateEmail` (bootstrap plumbing, not a tested step — frozen brief Constraints), `roleService.assignRole(adminUuid, "ADMIN", null)`, `apiKeyTokenIssuer.issue(adminUuid, List.of())`, returns the bearer string | `SasLoginIntegrationTest.registerAndActivate` + Finding 3's resolution |
| `assignMerchantRoleViaHttp(String adminBearer, UUID merchantUuid)` | `POST /admin/accounts/{merchantUuid}/roles/MERCHANT` with `Authorization: Bearer <admin>`, asserts `204` | `AdminAccountRoleController.assignRole` |
| `attemptFullAuthorizeFlow(...)` | full `/oauth2/authorize` → `/login` → `/oauth2/authorize` round trip, optional TOTP code | ported/adapted from `SasLoginIntegrationTest.attemptFullAuthorizeFlow` (private to this new class — no shared base exists, per Phase 0/2) |
| `exchangeCodeForToken(String code, String verifier)` | `/oauth2/token`, returns the SAS-issued access token | ported/adapted from `SasLoginIntegrationTest.exchangeCodeForToken` |
| `enrollTotp(UUID merchantUuid)` | direct `mfaService.beginEnroll(merchantUuid)` then `.confirm(merchantUuid, referenceGenerateCode(secret, Instant.now()))` — frozen brief Finding 1 resolution | `SasLoginIntegrationTest.seedConfirmedTotpEnrollment` |
| `referenceGenerateCode(byte[] secret, Instant now)` | RFC 6238 reference TOTP generator | ported verbatim from `SasLoginIntegrationTest` |
| `createApiKeyViaHttp(String bearer, String name)` | `POST /api-keys`, asserts `201`, returns `ApiKeyService.CreateApiKeyResult` (asserts `ck_live_` prefix) | `ApiKeyController.create` |
| `exchangeApiKeyViaHttp(String plaintextKey)` | `POST /api-keys/token` with `Authorization: ApiKey <key>`, returns `ApiKeyTokenResponse`, decodes claims via `JWTParser.parse(...).getJWTClaimsSet()` | `ApiKeyController.exchange`; claim decoding per `ApiKeyTokenIssuerTest`'s established style |
| `listSessionsViaHttp(String bearer)` | `GET /accounts/me/sessions`, returns parsed `JsonNode`/`List<SessionResponse>`-shaped body | `SessionIntegrationTest.shouldListActiveSessions` |
| `revokeSessionViaHttp(String bearer, UUID familyId)` | `DELETE /accounts/me/sessions/{familyId}`, asserts `204` | `SessionIntegrationTest.shouldRevokeSingleSessionFamily` |
| `reloadFamily(UUID familyId)` | `refreshTokenFamilyRepository.findById(...)`, asserts `revokedAt`/`revokedReason` (AC7, Finding 11) | same test, `reloadFamily` |
| `ensureRoleExists(String roleName)` | idempotent role-row seeding, needed before `assignRole` | `SasLoginIntegrationTest.ensureRoleExists` |
| `PkcePair` (private record) | PKCE verifier/challenge pair for the authorize flow | `SasLoginIntegrationTest.PkcePair` |
| `FullFlowResult` (private record) | authorize-flow result (optional code, response) | `SasLoginIntegrationTest.FullFlowResult` |

No shared base class is introduced (Phase 0 confirmed none exists); the above are private to this
one new file, following every sibling `*IntegrationTest`'s established self-contained convention.

## Entities used (read-only, via the controllers/services above)

`Account`, `VerificationToken` (indirectly, via the event payload only — never queried directly),
`MfaEnrollment`, `ApiKey`, `RefreshTokenFamily`, role-assignment rows.

## Repositories used

`RefreshTokenFamilyRepository` (`findById`, for the AC7 reload only — every other read goes through
real HTTP).

## Services used (direct calls, bootstrap/enrollment plumbing only per the frozen brief)

`AccountService` (`register`, `activateEmail` — admin bootstrap only), `RoleService`
(`assignRole` — admin bootstrap and, for the merchant, only if `ensureRoleExists` needs a role-row
seed, not the MERCHANT grant itself which is HTTP), `MfaService` (`beginEnroll`, `confirm` — the
one flow step with no HTTP surface), `ApiKeyTokenIssuer` (`issue` — admin bootstrap only).

## Tests required

- One integration test method (`shouldCompleteFullMerchantIdentityLifecycle`), composing AC1–AC7 in
  flow order, including the AC2 negative assertion inline (no authorization `code` pre-enrollment).
- No unit tests: this task adds no new production logic to unit-test (frozen brief Scope: Out).

## Execution order

1. Register scaffolding: class skeleton, `@SpringBootTest`/`@Import(TestcontainersConfiguration.class)`,
   Kafka-consumer `@BeforeEach`/`@AfterEach`, `baseUrl()` helper — no assertions yet.
2. Port the authorize-flow/PKCE/TOTP-reference-code helpers from `SasLoginIntegrationTest` (private,
   self-contained per above) — needed by nearly every later step.
3. `registerViaHttp` + `awaitRawVerificationToken` + `verifyEmailViaHttp` + `awaitUserRegisteredEvent`
   → AC1.
4. `bootstrapAdminBearerToken` + `assignMerchantRoleViaHttp` — plumbing + the "admin assigns MERCHANT"
   step.
5. `attemptFullAuthorizeFlow` (no TOTP code) → AC2 (assert no `code`).
6. `enrollTotp` → the one direct-call step (Finding 1).
7. `attemptFullAuthorizeFlow` (with TOTP code) + `exchangeCodeForToken` → AC3, produces the SAS
   session/JWT used to authenticate the next step.
8. `createApiKeyViaHttp` → AC4.
9. `exchangeApiKeyViaHttp` → AC5, produces the JWT used to authenticate the remaining steps.
10. `listSessionsViaHttp` → AC6 (locate the family created in step 7).
11. `revokeSessionViaHttp` + follow-up `listSessionsViaHttp` (empty) + `reloadFamily` → AC7.
12. Full local run: `mvn -pl services/auth test -Dtest=EndToEndLifecycleIntegrationTest`.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
