# auth · T25 · Phase 0 — Repository Understanding

**Task:** T25 — API-key exchange endpoint. `POST /api-keys/token` (public); validate the key, update `last_used_at`, issue a JWT via `ApiKeyTokenIssuer`, audit.
**Scope:** requirements `R31`, `R32`, `R33` · LOCKED `L8`, `L11` · named tests `shouldExchangeValidApiKeyForMerchantJwt`, `shouldRejectRevokedOrUnknownApiKeyWithUniform401`.
**Sources read:** `spec/auth-service/{package,requirements,design,tasks,agents}.md`; `services/auth/src/{main,test}`.
**No code written.** No design decisions taken — those are Phases 1–4.

---

## 1. Architecture summary — `services/auth`

**Module shape.** Package-by-feature under `com.themistra.auth`, one Maven module (`services/auth`), Java 21 / Spring Boot 3.5.4. Feature packages present today: `account`, `admin` (empty but for `package-info`), `apikey`, `audit`, `authn`, `authz`, `common`, `events`, `mfa`, `token`. `common` holds shared plumbing only (`Hashing`, `ProblemTypes`, `PublicEndpoints`, `ApiExceptionHandler`, `SecurityBeansConfig`). Boundaries are ArchUnit-enforced (`src/test/java/com/themistra/auth/ArchitectureTest.java`).

**Persistence.** PostgreSQL, schema owned by this service, Flyway DDL-only migrations `V1`…`V7` (`src/main/resources/db/migration/`). V1–V4 immutable per L1; V5 (lockout index + shedlock), V6 (SAS `oauth2_authorization` columns), V7 (widened `api_keys.prefix` to `VARCHAR(32)`, added by T24) already landed. JPA entities map existing tables; internal PKs are `bigint identity` and never leave the service — the external identifier is `accounts.account_uuid`. Repositories are package-private interfaces (ArchUnit rule `repositories_are_never_public`). All timestamps are `java.time.Instant` sourced from an injected `Clock` bean (`SecurityBeansConfig.clock()` → `Clock.systemUTC()`); no `Instant.now()` inline, no `@PrePersist`.

**Events / outbox.** `events` package: `OutboxEvent` + `OutboxPublisher` (same-transaction write) + `OutboxRelay` (Kafka publish) + `EventTopics` (explicit aggregate-type → topic table, throws on an unmapped type). Current mappings: `account` → `auth.user.lifecycle`, `audit` → `auth.security.audit`, `verification-token` → `auth.email.requested`.

**Audit.** `audit.AuditService.record(RecordAuditEventRequest)` appends an `auth_audit` row and mirrors a reduced payload (`AuditMirrorPayload`) to the outbox under aggregate type `audit`. `RecordAuditEventRequest(eventType, outcome, accountUuid, actorUuid, ip, rawUserAgent, traceId, details)` — `accountUuid`/`actorUuid` may be null (T24 relies on this for un-attributable exchange failures); `rawUserAgent` is hashed by `AuditService`, never persisted in the clear.

**Security.** Two filter chains in `token.SecurityChainsConfig`:
1. `@Order(1)` SAS protocol chain — `/oauth2/**`, `/.well-known/**`, `/userinfo`, `/login`; HTML callers without a session go to `/login`.
2. `@Order(2)` application chain — `PublicEndpoints.PATTERNS` + `PublicEndpoints.METHOD_SCOPED` are `permitAll`, `anyRequest().authenticated()`, `oauth2ResourceServer().jwt(...)` with `JwtRoleAuthoritiesConverter` mapping the `roles` claim to `ROLE_*`, plus a locally-registered `TotpAuthenticationProvider` and form login.

Signing material: `token.SigningKeysProperties` → `SigningKeyMaterial.load(...)` → `JWKSource<SecurityContext>` bean (`JwksConfig`), CURRENT key first, CURRENT+PREVIOUS published at `/oauth2/jwks`. `JwksConfig` also exposes a `JwtDecoder`. Errors are RFC 9457 `ProblemDetail`; `common.ApiExceptionHandler` handles framework-level failures and must never import a feature module, while each feature module maps its own domain exceptions in its own `@RestControllerAdvice` (`account.AccountExceptionHandler` is the template).

---

## 2. Existing code this task touches

### Already exists (reuse, do not rewrite)

| Artifact | Where | Relevance to T25 |
|---|---|---|
| `ApiKeyService.exchange(String presentedKey)` | `apikey/ApiKeyService.java:154` | **The whole service-layer half of T25 is already built by T24.** Splits `prefix.secret`, looks up candidates by prefix, constant-time-compares every candidate without short-circuiting, checks `revokedAt`/`expiresAt`, calls `updateLastUsedAt` (R32), audits `api_key.exchanged` / `api_key.exchange_failed`, and returns `ExchangeResult(accountUuid, scopes)`. Rejections all throw the single `ApiKeyExchangeRejectedException` (R33). |
| `ApiKeyExchangeRejectedException` | `apikey/` | Uniform, cause-free rejection — the exception T25's HTTP layer must map to a uniform 401. |
| `ApiKey` entity, `ApiKeyRepository` | `apikey/` | `findByPrefix`, `updateLastUsedAt`, `findAccountUuidById` already present; repository is package-private. |
| `ApiKeyHasher` | `apikey/` | SHA-256 + `Hashing.constantTimeEquals`. |
| `ApiKeyProperties` | `apikey/` | Validated `@ConfigurationProperties` record with a single field: `prefix`. |
| `TokenClaimsCustomizer` | `token/` | SAS `OAuth2TokenCustomizer<JwtEncodingContext>`; today branches on `client_credentials` (→ `amr: [client_secret]`) vs. interactive (→ `roles`, `amr: [pwd]`/`[pwd,otp]`, `acr`, `email_verified`). **No `api_key` branch exists.** |
| `JWKSource<SecurityContext>` bean | `token/JwksConfig.java:25` | The signing material any new issuer would sign with. |
| `RoleService.resolveEffectiveRoles(UUID)` | `authz/` | Role resolution for the `roles` claim; already used by `ApiKeyService.create`. |
| `AuditService` | `audit/` | Exchange auditing is already wired inside `ApiKeyService.exchange`. |
| `PublicEndpoints` | `common/` | `METHOD_SCOPED` currently lists 5 `/accounts` POSTs; `/api-keys/token` is **not** there yet. |
| `ProblemTypes` | `common/` | 8 stable URIs; **none covers API-key rejection.** |
| `api_keys` table | `V1` + `V7` | Unchanged by T25 — no new DDL implied by the task statement. |

### New in T25 (does not exist anywhere in the tree today)

- `apikey/ApiKeyTokenIssuer.java` — named by the task statement and `design.md` §6; **no file, class, or reference to this name exists in `src/`.**
- An HTTP entry point for `POST /api-keys/token` — no controller exists in `apikey/` at all (`design.md` §6 lists both `ApiKeyController.java` and `ApiKeyAuthenticationFilter.java` as "or" alternatives; T26 owns the CRUD controller).
- `apikey/dto/ApiKeyTokenResponse.java` (and a request DTO if the key is not taken from the `Authorization` header) — no `apikey/dto/` package exists.
- An `apikey` module `@RestControllerAdvice` mapping `ApiKeyExchangeRejectedException` → uniform 401 — none exists; the exception is currently unmapped.
- `themistra.auth.api-key.token-ttl-minutes` — present in `design.md` §4c's verbatim config block but **absent from `application.properties` and from `ApiKeyProperties`**.
- `PublicEndpoints.METHOD_SCOPED` entry for `POST /api-keys/token` (required by L11).
- The two named tests; `src/test/.../apikey/` currently holds `ApiKeyHasherTest`, `ApiKeyPersistenceIntegrationTest`, `ApiKeyServiceIntegrationTest` only.

---

## 3. Established patterns to follow

**Persistence.** JPA entity + package-private `JpaRepository`; state transitions that can race go through conditional `@Modifying` queries (`revokeIfActive`, `RecoveryCodeRepository#markUsed`) rather than entity mutators. Cross-module id resolution uses native `SELECT` queries against `accounts` (`findAccountIdByUuid` / `findAccountUuidById`) so no module imports another module's entity (L12). No new migration unless a column is genuinely required — V1–V4 immutable, next free number is `V8`.

**Configuration.** Flat `application.properties` only. Every knob is `${ENV_VAR:local-default}` and bound to a `@Validated @ConfigurationProperties` record picked up by `@ConfigurationPropertiesScan` on `AuthServiceApplication`. Extending `ApiKeyProperties` with a TTL field follows `MfaProperties`/`LockoutProperties`.

**Controllers.** `@RestController` + `@RequestMapping("/<resource>")`, constructor injection of exactly one module service, `@Valid @RequestBody` DTO records, `ResponseEntity` for non-200 statuses. The principal is read from `Authentication.getName()` (the `sub` claim is the account UUID) — never a path variable identifying the caller. Public endpoints are registered *only* in `PublicEndpoints`; ArchUnit rule `only_token_module_references_public_endpoints` forbids any class outside `token`/`common` from referencing that class, so a new controller must not import it.

**Error handling.** RFC 9457 `ProblemDetail`, one `@RestControllerAdvice` per module, a new stable URI added to `ProblemTypes` (never inlined). Enumeration-sensitive failures get exactly one mapping with fixed status, fixed title, no `detail` that can vary by cause — `AccountExceptionHandler.onVerificationTokenRejected` is the precedent for what R33's uniform 401 should look like.

**Outbox / audit.** Never publish to Kafka directly; write through `OutboxPublisher` in the same transaction, and add any new aggregate type to `EventTopics`. Security-relevant actions go through `AuditService.record`. For T25 both are already done inside `ApiKeyService.exchange`.

**Security posture.** Anything new and unauthenticated must be in `PublicEndpoints` and justified; secrets never committed; never log tokens, keys, or emails (`CreateApiKeyResult.toString()` is overridden to `[REDACTED]` — the same discipline will apply to any token-carrying response type).

---

## 4. Testing conventions

- **Unit:** plain JUnit 5 + AssertJ, no Spring context, mocks or hand-built collaborators, fixed `Clock` (`Clock.fixed(...)`) wherever time matters — e.g. `TokenClaimsCustomizerTest`, `MfaServiceTest`, `LockoutStateMachine` tests.
- **Integration:** `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` → real Postgres 16 + real Kafka (`apache/kafka:3.8.0`), never a shared DB. `ApiKeyServiceIntegrationTest` is the closest sibling: it builds fixtures through the real `AccountService`/`RoleService`/`MfaService`, uses `TransactionTemplate` for explicit transaction boundaries, and asserts against the real schema.
- **HTTP-layer:** existing controller tests live beside their module; the SAS/end-to-end flows (`SasLoginIntegrationTest` pattern) are the precedent for exercising a real filter chain rather than a standalone MockMvc slice.
- **Architecture:** `ArchitectureTest` (ArchUnit) enforces layering, repository visibility, the `PublicEndpoints` single-consumer rule, the AWS-SDK carve-out, and `@PreAuthorize` on `Admin*` controllers.
- **Contract:** `UserLifecycleEventPayloadContractTest` is the named pattern; `shouldConformToAuthOpenApiContract` is deferred to T33 (`contracts/api/auth.yaml` does not exist yet).
- Named tests from `package.md` §8 are expected as literal method names.

---

## 5. Known gaps / unknowns

Facts, and where I genuinely do not know — no speculation, no design proposed here.

1. **Requirement-ID drift between prompt and `package.md` §8.** The prompt and `README.md` scope T25 to `R31`/`R32`/`R33`, which in `requirements.md` are exactly the exchange JWT contract, the `last_used_at` update, and the uniform 401 — a correct match. But `package.md` §8 maps `shouldExchangeValidApiKeyForMerchantJwt` → **R28** and `shouldRejectRevokedOrUnknownApiKeyWithUniform401` → **R29**, which in `requirements.md` are MFA-disable and MFA-failure-audit requirements. `package.md` §8's numbering is offset from `requirements.md` across the whole API-key/session block. I treat `requirements.md` R31–R33 as authoritative for this task; the §8 mapping appears stale. **Logged as an Open Question — not resolved here, and `spec/` is not to be modified.**

2. **No `JwtEncoder` bean exists in the application context.** Grepping `src/` finds the identifier only in a comment in `JwksConfig`. Spring Authorization Server builds its `NimbusJwtEncoder` internally from the `JWKSource` for its own grant flows; nothing in this service issues a JWT outside a SAS grant today. How `ApiKeyTokenIssuer` obtains a signer is therefore an unresolved design question, not an existing pattern I can point to.

3. **L8 says the API-key JWT gets its `roles`/`client_id` claims "via the existing `TokenClaimsCustomizer` path", but that path has no `api_key` branch.** `TokenClaimsCustomizer.customizeAccessToken` handles `client_credentials` and interactive grants only, and it is driven by SAS's `JwtEncodingContext`, which is constructed by SAS's token generators during a grant. Task 21 in `tasks.md` assigns the `api_key` `amr` work to the MFA block, and it has not been done. Whether "the existing customizer path" means *invoke SAS's generator with a synthetic authorization*, *extend the customizer and call it directly with a hand-built context*, or *something else* — **I do not know**; it is the central design question for Phases 1–3.

4. **How the key is presented on the wire is under-specified.** R31 says "in the `Authorization` header"; `ApiKeyService.exchange` takes a bare `prefix.secret` string. The header scheme is not stated anywhere I read. This interacts with a concrete mechanism in the current code: on the `@Order(2)` chain the resource-server `BearerTokenAuthenticationFilter` runs even for `permitAll` paths, so an `Authorization: Bearer ck_live_….<secret>` value would be handed to `JwtDecoder` and rejected as a malformed JWT before any controller is reached. I have not verified that behaviour by running it — flagging it as a risk to be settled in design, not asserting the outcome.

5. **`ApiKeyExchangeRejectedException` is currently unmapped.** No `@RestControllerAdvice` in `apikey/` exists, so today it would fall through to `ApiExceptionHandler.onUnexpected` → 500 + stack-trace logging. T25 must map it; R33's status is 401, and no existing `ProblemTypes` URI fits.

6. **`themistra.auth.api-key.token-ttl-minutes` is specified in `design.md` §4c but not implemented.** Only `themistra.auth.api-key.prefix` exists in `application.properties:95` and in `ApiKeyProperties`.

7. **`ApiKeyService.exchange` deliberately does not check the owning account's status** (documented in its Javadoc as a Phase 8 finding carried forward from T24: a suspended/deleted merchant's key stays valid until revoked or expired). Whether T25's endpoint inherits that gap or closes it — **I do not know**; R31/R32/R33 do not mention account status.

8. **`exchange` returns `ExchangeResult(accountUuid, scopes)` with the key's own `scopes` column**, seeded to `["merchant.api"]` by `create`. R31 requires the issued JWT's `scope` to *contain* `merchant.api`. Whether the issuer uses the row's scopes or a fixed value is not decided anywhere I read.

9. **No contracts exist yet for this endpoint.** `contracts/api/` is empty (no `auth.yaml`, no `token-claims.md`); `contracts/events/auth/` holds only `user-lifecycle.v1.schema.json` — the `email-requested` and `security-audit` schemas listed in the prompt's Contracts line are not in the repo. They are T33/T34 deliverables, so T25 cannot verify against them.

10. **Whether the endpoint must be excluded from CSRF.** The application chain configures `csrf.ignoringRequestMatchers("/api/**")`, and `/api-keys/token` does not match `/api/**`. A stateless POST from a non-browser client carries no CSRF token. I have not traced whether Spring Security's CSRF filter applies to this path under the current configuration — flagged, not concluded.

---

**Phase 0 complete.** One artifact written: `artifacts/00-repository-understanding.md`. No requirements extracted, no design proposed — Phases 1–2.
