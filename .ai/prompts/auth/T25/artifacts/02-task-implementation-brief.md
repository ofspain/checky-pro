# auth · T25 · Phase 2 — Task Implementation Brief

## Task

T25 — **API-key exchange endpoint.** Add `POST /api-keys/token` (public). Validate the key, update `last_used_at`, issue a JWT via `ApiKeyTokenIssuer`, and audit.
(Verbatim, `spec/auth-service/tasks.md` task 25.)

## Purpose

Give merchant machine clients a way to trade a long-lived `ck_live_…` API key for a short-lived, standards-shaped access token, so that downstream Themistra resource servers validate one artifact — a service-issued RS256 JWT — and never see or verify API-key material themselves.

## Scope

**In**

- A public HTTP endpoint `POST /api-keys/token`.
- `ApiKeyTokenIssuer` — the component that mints the API-key access token.
- Registration of the new path in `PublicEndpoints` (L11).
- RFC 9457 mapping of `ApiKeyExchangeRejectedException` to a uniform `401`.
- The `themistra.auth.api-key.token-ttl-minutes` configuration key and its binding.
- The two named tests plus the boundary tests in **Required Tests**.

**Out**

- `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}` (T26) — their service methods already exist and stay untouched.
- Rewriting or re-tuning `ApiKeyService.exchange`; its validation, constant-time comparison, `last_used_at` update, and audit calls are T24 work and are consumed as-is.
- The create→exchange→revoke integration test (T27).
- `contracts/api/auth.yaml`, `contracts/api/token-claims.md`, and the event schemas (T33/T34).
- Any Flyway migration; any change to `api_keys` semantics.
- Closing the account-status gap documented in `ApiKeyService.exchange`'s Javadoc (a suspended merchant's key remains valid until revoked/expired) — out of scope for R31–R33.

## Business Rules

- **R31** — A valid, non-expired, non-revoked key presented in the `Authorization` header returns a 10-minute JWT with `sub` = merchant account UUID, `scope` ⊇ `merchant.api`, `amr` ∋ `api_key`.
- **R32** — A successful exchange updates that key's `last_used_at`.
- **R33** — Revoked, expired, malformed, or hash-mismatched keys all return a uniform `401 Unauthorized`.
- **R43** — Success and failure both append an `auth_audit` row and mirror to `auth.security.audit` via the outbox.
- **R46** — The 401 is `application/problem+json` with no stack trace, no internal detail, and no existence hint.
- **R48** — The issued access token carries no PII beyond `email_verified`.

## Locked Decisions

- **L8** — 10-minute RS256 JWT; `sub` = merchant account UUID; `scope` contains `merchant.api`; `amr` contains `api_key`; standard `roles` / `client_id` claims via the existing `TokenClaimsCustomizer` path. *(See Open Questions — that path has no `api_key` branch.)*
- **L11** — `POST /api-keys/token` is public; the public set is exhaustive and lives in `PublicEndpoints.java`.
- **L7** — Key format `ck_live_<24 alnum>.<32 alnum>`; only SHA-256 at rest. The endpoint parses this shape and never persists or echoes plaintext.
- **L9** — Access-token claims are exactly `iss, sub, aud, exp, iat, nbf, jti, scope, roles, client_id, amr, acr, email_verified`; no email or name.
- **L12** — No cross-module entity imports; shared plumbing only in `common`.
- **L13** — No secret, key, or signing material committed or logged.
- **L1** — V1–V4 immutable; T25 implies no DDL.

## Dependencies

`apikey`: `ApiKeyService.exchange(String)` → `ExchangeResult(accountUuid, scopes)`, `ApiKeyExchangeRejectedException`, `ApiKeyProperties`, `ApiKeyRepository`, `ApiKey`, `ApiKeyHasher`.
`token`: `JWKSource<SecurityContext>` (`JwksConfig`), `SigningKeyMaterial` ordering guarantee (CURRENT first), `TokenClaimsCustomizer`, `SecurityChainsConfig` (`@Order(2)` application chain).
`authz`: `RoleService.resolveEffectiveRoles(UUID)` for `roles`.
`audit`: `AuditService` / `RecordAuditEventRequest` / `AuditOutcome` — already invoked inside `exchange`.
`common`: `PublicEndpoints`, `ProblemTypes`, `Clock` bean from `SecurityBeansConfig`.
Config: `themistra.auth.api-key.prefix` (existing), `spring.security.oauth2.authorizationserver.issuer` = `${AUTH_ISSUER_URI:http://localhost:8080}` (the `iss` value), `themistra.auth.jwt.*` signing keys, and the new `themistra.auth.api-key.token-ttl-minutes`.
Tables: `api_keys`, `accounts` (UUID resolution via native queries), `auth_audit` + outbox via `AuditService`. **No schema change.**

## Inputs

- HTTP `POST /api-keys/token`, unauthenticated.
- The full plaintext key `ck_live_<suffix>.<secret>` presented in the `Authorization` header (R31). The header scheme is not fixed by the spec — see Open Questions.
- No request body is required by any requirement.

## Outputs

- **200** — a token response carrying the signed JWT, its token type, and its expiry. The JWT: RS256, `sub` = merchant account UUID, `scope` ⊇ `merchant.api`, `amr` ∋ `api_key`, `roles`, `client_id`, `exp − iat` = 10 minutes, claim set bounded by L9, no PII beyond `email_verified`.
- **401** — a single `application/problem+json` body, byte-identical for every rejection cause, with a stable `ProblemTypes` URI.
- Response types must not expose the presented key, any hash, the account email, or an internal id.

## State Changes

- `api_keys.last_used_at` ← exchange instant, on success only (performed inside `ApiKeyService.exchange`).
- One `auth_audit` row + one outbox row per attempt: `api_key.exchanged` (SUCCESS) or `api_key.exchange_failed` (FAILURE) — also inside `exchange`.
- No other persistent state. No new table, column, or index.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyTokenIssuer.java`
- The HTTP entry point in `com.themistra.auth.apikey` — `design.md` §6 offers `ApiKeyController.java` **or** `ApiKeyAuthenticationFilter.java`; selecting between them is a design decision for Phase 5, not a brief-level fact.
- `services/auth/src/main/java/com/themistra/auth/apikey/dto/ApiKeyTokenResponse.java`
- An `apikey`-module `@RestControllerAdvice` mapping `ApiKeyExchangeRejectedException` → 401 (`design.md` §6 does not name it; the one-advice-per-module pattern requires it).
- Tests mirroring the package layout under `src/test/java/com/themistra/auth/apikey/`.

## Files to Modify

- `apikey/ApiKeyProperties.java` — add the validated token-TTL field.
- `common/PublicEndpoints.java` — add `POST /api-keys/token` to `METHOD_SCOPED` (L11).
- `common/ProblemTypes.java` — add the stable problem-type URI for the uniform 401.
- `src/main/resources/application.properties` — add `themistra.auth.api-key.token-ttl-minutes`.
- `token/TokenClaimsCustomizer.java` and/or `token/SecurityChainsConfig.java` — **only if** the resolution of OQ-1/OQ-3 requires it.

## Files NOT to Modify

- Everything under `spec/`.
- `apikey/ApiKeyService.java`, `ApiKeyRepository.java`, `ApiKey.java`, `ApiKeyHasher.java`, `ApiKeyExchangeRejectedException.java` — T24-frozen behaviour.
- Any Flyway migration, `V1`–`V7`.
- `events/EventTopics.java` — T25 emits no new aggregate type.
- Other feature modules (`account`, `authn`, `authz`, `mfa`, `admin`) and their tests.

## Acceptance Criteria

- **AC1** — `POST /api-keys/token` is reachable with no prior authentication. *(R31, L11)*
- **AC2** — The path is in `PublicEndpoints.METHOD_SCOPED`, POST-scoped, and is the only public path added. *(L11)*
- **AC3** — A valid key returns 200 and an RS256 JWT signed by the current signing key. *(R31, L8)*
- **AC4** — `sub` equals the owning merchant's account UUID. *(R31, L8)*
- **AC5** — `scope` contains `merchant.api`. *(R31, L8)*
- **AC6** — `amr` contains `api_key`. *(R31, L8)*
- **AC7** — `exp − iat` is 10 minutes, driven by `themistra.auth.api-key.token-ttl-minutes` and the injected `Clock`. *(L8)*
- **AC8** — `roles` and `client_id` are present; the full claim set stays within L9 and carries no PII beyond `email_verified`. *(L8, L9, R48)*
- **AC9** — `last_used_at` is updated on success and on no failure path. *(R32)*
- **AC10** — Revoked, expired, malformed, unknown-prefix, and wrong-secret keys each return 401 with an identical problem body. *(R33, R46)*
- **AC11** — No response echoes the key, a hash, an email, or an internal id. *(R46, L13)*
- **AC12** — Every attempt is audited with a row and an outbox mirror. *(R43)*
- **AC13** — No new migration; `api_keys` semantics unchanged. *(L1)*
- **AC14** — ArchUnit stays green: the new HTTP layer references neither `PublicEndpoints` nor a foreign module's entity. *(L12)*

## Required Tests

**Named (`package.md` §8, verbatim method names):**

1. `shouldExchangeValidApiKeyForMerchantJwt` — exchange a real merchant key through the endpoint; decode the JWT and assert `sub`, `scope`, `amr`, `exp − iat`, `roles`, `client_id`, RS256, and the absence of PII claims.
2. `shouldRejectRevokedOrUnknownApiKeyWithUniform401` — revoked key, unknown prefix, malformed key, and wrong secret all return 401 with an identical body.

**Boundary / supporting:**

3. `last_used_at` written on success, untouched on every rejection path.
4. Expired key → the same uniform 401, evaluated against a fixed `Clock`, including the `expires_at == now` boundary (consistent with `exchange`'s existing `isAfter(now)`).
5. A non-default `token-ttl-minutes` changes `exp` — the TTL is configuration, not a literal.
6. `PublicEndpoints.METHOD_SCOPED` contains `POST /api-keys/token` (guard test, `PublicEndpointsTest` style).
7. The endpoint is reachable anonymously through the real filter chain.
8. Audit assertions: one SUCCESS row on the happy path, one FAILURE row per rejection, each mirrored to the outbox.
9. `ApiKeyExchangeRejectedException` produces 401 `application/problem+json`, not the 500 it produces today.
10. `ArchitectureTest`, `PublicEndpointsTest`, `ApiKeyServiceIntegrationTest`, and `TokenClaimsCustomizerTest` stay green.

Pure logic (TTL arithmetic, claim assembly) as plain JUnit with a fixed `Clock`; anything touching the schema or the filter chain as `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` against Postgres + Kafka.

## Constraints

- **Security** — The 401 must be uniform across every cause: same status, same type URI, same title, no varying `detail`, no timing-revealing early return added on top of `exchange`'s deliberately non-short-circuiting comparison. Never log or echo the presented key, its hash, or the account email. The endpoint is public and unauthenticated — treat every input as hostile and bound its size before parsing.
- **Transaction** — `ApiKeyService.exchange` is `@Transactional`; the `last_used_at` update and both audit writes must remain in that one transaction. JWT minting must not widen or nest that transaction, and a signing failure must not silently commit a `last_used_at` update that was never matched by an issued token — the ordering is a Phase 5 design point, flagged here as a constraint.
- **Thread-safety** — The issuer is a singleton bean serving concurrent unauthenticated requests: no mutable instance state; any `SecureRandom`/signer/encoder held as a field must be safe for concurrent use.
- **Module boundaries** — New classes live in `com.themistra.auth.apikey`; no foreign entity imports (L12); no reference to `PublicEndpoints` from outside `token`/`common` (ArchUnit); repositories stay package-private.
- **Configuration** — Flat `application.properties`, `${ENV:default}` form, bound to the validated `ApiKeyProperties` record; startup must fail on a missing/invalid TTL in non-local profiles.
- **Time** — All instants from the injected `Clock`; never `Instant.now()`.
- **Null handling** — `exchange` already tolerates a null presented key; the HTTP layer must convert a missing or blank `Authorization` header into the same uniform 401, never a `NullPointerException` or a 500.
- **Performance** — The exchange path is a hot machine-client path: no extra account lookups, no per-request key-material reload beyond what the existing `JWKSource` provides.

## Open Questions

Blockers, all recorded in Phase 1 §7 and unresolved:

- **OQ-1** — L8 requires the JWT's claims "via the existing `TokenClaimsCustomizer` path", but that customizer is a SAS `OAuth2TokenCustomizer<JwtEncodingContext>` with no `api_key` branch (its task-21 update is unimplemented), and **no `JwtEncoder` bean exists in the context**. How `ApiKeyTokenIssuer` reaches that path is not answerable from the spec. If the chosen route deviates from L8 as written, it must stop at the Phase 4 human gate.
- **OQ-2** — No `client_id` value is defined for an API-key JWT; the caller presents a key, not client credentials, and `RegisteredClientSeeder` seeds no keyholder client. L8 and L9 both require the claim.
- **OQ-3** — L9's exact claim set includes `acr`; L8 is silent on `acr` for this token, and no `urn:themistra:acr:*` value is defined for an API-key credential.
- **OQ-6** — The `Authorization` header scheme is unspecified. This is load-bearing: on the `@Order(2)` chain a `Bearer`-schemed value is picked up by the resource-server filter and decoded as a JWT before any handler runs.
- **OQ-5** *(spec defect, non-blocking)* — `package.md` §8 maps this task's two named tests to R28/R29, which are MFA requirements; the API-key exchange requirements are R31–R33, as the prompt header scopes them. Proceeding on R31–R33. `spec/` not modified.
- **OQ-4 / OQ-7** *(non-blocking)* — The named contract files do not exist yet (T33/T34), so contract conformance defers; `package.md` §11 Q3 (scope vocabulary, key caps) remains open but R31 only requires `scope` to *contain* `merchant.api`.
