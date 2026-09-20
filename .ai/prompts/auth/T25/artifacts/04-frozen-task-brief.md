# auth · T25 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Approved by femi at the Phase 4 human gate, 2026-08-15. Consumes `artifacts/02-task-implementation-brief.md` (TIB) and `artifacts/03-design-challenge.md` (Kimi 2.7, 15 findings). Downstream phases may not renegotiate this brief.

---

## Task

T25 — **API-key exchange endpoint.** Add `POST /api-keys/token` (public). Validate the key, update `last_used_at`, issue a JWT via `ApiKeyTokenIssuer`, and audit.
(Verbatim, `spec/auth-service/tasks.md` task 25.)

## Purpose

Give merchant machine clients a way to trade a long-lived `ck_live_…` API key for a short-lived, standards-shaped access token, so that downstream Themistra resource servers validate one artifact — a service-issued RS256 JWT — and never see or verify API-key material themselves.

---

## Phase 4 decisions (gate outcomes)

Four blockers were put to the human gate. All four were decided as recommended.

### D1 — JWT signing path (resolves OQ-1, Kimi #1 + #2)

**Decided:** declare `@Bean JwtEncoder jwtEncoder(JWKSource<SecurityContext>)` returning `new NimbusJwtEncoder(jwkSource)` in `token/JwksConfig`, and have `ApiKeyTokenIssuer` inject it and assemble the full claim set itself.

**Why this is safe:** SAS's `OAuth2ConfigurerUtils.getJwtEncoder` (verified, `OAuth2ConfigurerUtils.java:138-152`) looks for an optional `JwtEncoder` bean *before* falling back to `new NimbusJwtEncoder(jwkSource)`. Declaring that exact bean is therefore behaviourally identical to what SAS constructs internally today — same class, same `JWKSource`, same CURRENT-key-first ordering from `SigningKeyMaterial` — so existing grants are unaffected.

**Recorded L8 deviation:** L8 says the API-key JWT gets its `roles`/`client_id` claims "via the existing `TokenClaimsCustomizer` path". That path cannot be taken: `TokenClaimsCustomizer` is an `OAuth2TokenCustomizer<JwtEncodingContext>` invoked only by SAS's `JwtGenerator` during a grant, and it branches on a `RegisteredClient`, an `Authentication` principal, and `getAuthorizedScopes()` — none of which exist for a key exchange. T25 honours **L8's claim contract exactly** while assembling those claims in `ApiKeyTokenIssuer` rather than in the customizer. `TokenClaimsCustomizer` is **not modified** by T25. This is a documented, gate-approved deviation, not a silent one.

### D2 — `client_id` claim (resolves OQ-2, Kimi #3)

**Decided:** emit a fixed literal `client_id` of **`checky-api-key`**, with **no** corresponding `RegisteredClient` seeded.

**Context established at the gate:** SAS's `JwtGenerator` (verified, `JwtGenerator.java:112-127`) sets `iss`, `sub`, `aud` (= clientId), `iat`, `exp`, `jti`, `nbf`, and `scope` — it never sets a `client_id` claim, and `azp` is id-token-only. **No access token this service issues today carries `client_id` at all**, so L9's claim set is already unimplemented for existing tokens. T25 emits it for its own token; closing that gap for interactive/client-credentials tokens is out of scope and is **not** T25's to fix.

Nothing in the system resolves `client_id` back to a `RegisteredClient`, so an unbacked literal is inert. `RegisteredClientSeeder` and `AuthClientsProperties` are **not modified**.

### D3 — `acr` claim (resolves OQ-3, Kimi #4)

**Decided:** emit `acr` = **`urn:themistra:acr:api_key`**, mirroring the existing `urn:themistra:acr:pwd` / `urn:themistra:acr:otp` naming in `TokenClaimsCustomizer`. L9's claim set stays complete; no L9 amendment is needed.

### D4 — `Authorization` header scheme (resolves OQ-6, Kimi #5)

**Decided:** the scheme is **`ApiKey`** — `Authorization: ApiKey ck_live_<suffix>.<secret>`. **`Bearer` is explicitly rejected.**

**Why it is load-bearing:** `SecurityChainsConfig:71` applies `.oauth2ResourceServer(rs -> rs.jwt(...))` to the `@Order(2)` application chain, so `BearerTokenAuthenticationFilter` runs even on `permitAll` paths. A `Bearer`-schemed API key would be handed to `JwtDecoder`, fail to parse, and be rejected with the filter's own 401 + `WWW-Authenticate` — bypassing the controller and T25's uniform problem body entirely, breaking AC10. A custom scheme is ignored by that filter and reaches the handler.

Scheme matching is **case-insensitive** per RFC 7235; the credential portion is not. A missing header, a wrong scheme, or a blank credential all produce the same uniform 401 as any other rejection.

### D5 — Transaction ordering (resolves Kimi #6; decided by the model as a design call, per the gate's disposition below)

**Decided:** the controller calls `ApiKeyService.exchange(...)` to completion (transaction commits), **then** calls `ApiKeyTokenIssuer.issue(...)`. No new `@Transactional` wrapper; `ApiKeyService` is not modified.

**Why:** Kimi's premise was incomplete — `AuditService.record` is `@Transactional(propagation = REQUIRES_NEW)` (verified, `AuditService.java:57-58`), so audit rows commit independently of the caller's transaction. Rolling back the exchange transaction would therefore **not** roll back the audit event, so the "mint inside the transaction" option buys no audit consistency. It would only avoid a committed `last_used_at` on a signing failure — cosmetic merchant-facing metadata, not a security control — at the cost of holding a DB transaction open across a signing operation and adding a wrapper class.

**Accepted residual risk, explicitly:** if signing fails after a successful validation, `last_used_at` is updated and an `api_key.exchanged` SUCCESS audit row exists without a token having been issued. A signing failure means absent or broken key material — a catastrophic configuration fault, not a routine path. **A signing failure MUST surface as a 500** (via `ApiExceptionHandler.onUnexpected`, opaque body + `trace_id`), **never as the uniform 401** — a 401 would falsely tell a merchant their valid key is bad.

---

## Phase 3 findings — disposition

### Accepted (folded into this brief)

| # | Finding | How it is folded in |
|---|---|---|
| 1 | No `JwtEncoder` bean exists | **D1.** New `JwtEncoder` bean in `token/JwksConfig`. |
| 2 | `TokenClaimsCustomizer` not reusable as-is | **D1.** Issuer assembles the claim set; customizer untouched; L8 deviation recorded. |
| 3 | No `client_id` defined | **D2.** Fixed literal `checky-api-key`, no seeded client. |
| 4 | No `acr` defined | **D3.** `urn:themistra:acr:api_key`. |
| 5 | Header scheme unspecified / filter hazard | **D4.** `ApiKey` scheme; `Bearer` rejected. |
| 6 | Transaction ordering unspecified | **D5.** Ordering locked; residual risk documented; 500-not-401 on signing failure. |
| 9 | Max header length unspecified | Accepted **with a corrected rationale** — see below. Bound the credential at **256 characters** before hashing; anything longer gets the same uniform 401. |
| 10 | `roles` lookup conflicts with the perf constraint | Accepted. The TIB's "no extra account lookups" constraint is **struck**. `RoleService.resolveEffectiveRoles` is 2 queries (`RoleService.java:156-163`) and roles must be resolved fresh at issuance — caching them on the `api_keys` row would go stale after a role change, which is worse than the query cost. |
| 12 | `scope` claim format ambiguous | Accepted as a decision, **premise corrected** — see below. Lock `scope` to a **JSON array**. |
| 13 | Response envelope unspecified | Accepted. Lock `access_token`, `token_type` = `"Bearer"`, `expires_in` (seconds). |
| 14 | `package.md` §8 maps tests to wrong IDs | Accepted. Test Javadoc traces to **R31–R33**; no code-level reference to R28/R29. `spec/` untouched. |
| 15 | Null audit target for unidentifiable keys | Accepted as-is. Verified `AuditService.partitionKey` substitutes a random UUID for a null account (`AuditService.java:110-114`) — deliberate, documented. Test asserts exactly one mirror row per attempt. |

### Accepted, but with Kimi's stated premise corrected

- **#12 — `scope` format.** Kimi asserts "Spring Authorization Server emits `scope` as a space-delimited string by default." **This is factually wrong.** `JwtGenerator.java:125` passes `context.getAuthorizedScopes()` — a `Set<String>` — straight to `JwtClaimsSet`, which Nimbus serializes as a **JSON array**. The decision to lock the format stands, but it is locked to a **JSON array**, matching every other access token this service issues. Locking it to a space-delimited string, as Kimi recommended, would have made API-key tokens the odd one out.
- **#9 — header length.** Kimi's "multi-megabyte header → memory pressure / DoS" framing is wrong: `application.properties` sets no `server.max-http-request-header-size`, so Spring Boot's 8KB Tomcat default applies and oversized headers are rejected with Tomcat's own 400 before any application code runs. The bound is adopted anyway for a **different, valid reason**: an explicit 256-character cap keeps oversized-but-under-8KB input flowing through *our* uniform 401 instead of varying by input size, and avoids hashing attacker-controlled bulk.

### Rejected

| # | Finding | Reason |
|---|---|---|
| 7 | Success-path `UPDATE` is a timing side-channel | **Rejected as stated.** Success vs. failure is already fully disclosed by the 200/401 status code, so the `UPDATE`'s timing reveals nothing an attacker doesn't already have. R33's uniformity requirement is about not distinguishing *among failure causes*, which the status code does not do. *Noted for the record (not actioned):* the same reasoning applied **within** the failure paths does find a real asymmetry Kimi missed — prefix-matched rejections call `resolveAccountUuidQuietly` (an extra `SELECT`) while malformed / unknown-prefix ones do not (`ApiKeyService.java:190-192`). Against a 24-character random prefix this is not practically exploitable, and it is T24-frozen code. Logged for a future task; **T25 does not change it.** |
| 8 | No rate limiting on the public endpoint | **Rejected as scope creep.** R41 enumerates login, `/oauth2/token`, password-reset confirm, and MFA verify — it does not list `/api-keys/token`. `tasks.md` task 31 owns rate limiting wholesale. Adding a bespoke limiter here would duplicate and pre-empt that task. Flagged for T31's scope. |
| 11 | `EventTopics` maps `api-key` → `auth.email.requested` | **Rejected for T25.** A genuine oddity in `design.md` §4c, already flagged independently in Phase 1 §4. But `spec/` is immutable for this task, and T25 emits **no** new aggregate type — its audit events use the existing `audit` aggregate. `EventTopics.java` is not modified. Flagged for the spec author. |

### Open Questions — final status

- **OQ-1** → resolved by **D1**.
- **OQ-2** → resolved by **D2**.
- **OQ-3** → resolved by **D3**.
- **OQ-6** → resolved by **D4**.
- **OQ-4** (`contracts/api/auth.yaml`, `token-claims.md` do not exist) → **deferred, owner: task 33 / task 34.** Contract conformance for this endpoint cannot be tested in T25.
- **OQ-5** (`package.md` §8 test→requirement mismatch) → **deferred, owner: spec author.** T25 proceeds on R31–R33; `spec/` untouched.
- **OQ-7** (`package.md` §11 Q3 — scope vocabulary, key caps) → **deferred, owner: spec author.** Not blocking: R31 requires only that `scope` *contain* `merchant.api`, and the issuer echoes the key row's own `scopes` column, which T24 seeds to `["merchant.api"]`.

---

## Scope (final)

**In**

- `POST /api-keys/token`, public, `Authorization: ApiKey <key>`.
- `ApiKeyTokenIssuer` — assembles and signs the API-key access token.
- A `JwtEncoder` bean in `token/JwksConfig` (D1).
- `PublicEndpoints` registration (L11).
- RFC 9457 mapping of `ApiKeyExchangeRejectedException` → uniform 401.
- `themistra.auth.api-key.token-ttl-minutes` config key + binding.
- The two named tests plus the boundary tests below.

**Out**

- `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}` (T26).
- Any modification to `ApiKeyService`, `ApiKeyRepository`, `ApiKey`, `ApiKeyHasher`, or `ApiKeyExchangeRejectedException` (T24-frozen).
- Any modification to `TokenClaimsCustomizer`, `RegisteredClientSeeder`, `AuthClientsProperties`, or `EventTopics`.
- The create→exchange→revoke integration test (T27); `contracts/` files (T33/T34); rate limiting (T31).
- Any Flyway migration.
- The account-status gap in `exchange` (a suspended merchant's key stays valid until revoked/expired) and the failure-path timing asymmetry noted under rejected finding #7.

## Business Rules

- **R31** — A valid, non-expired, non-revoked key in the `Authorization` header returns a 10-minute JWT: `sub` = merchant account UUID, `scope` ⊇ `merchant.api`, `amr` ∋ `api_key`.
- **R32** — A successful exchange updates that key's `last_used_at`.
- **R33** — Revoked, expired, malformed, or hash-mismatched keys all return a uniform `401`.
- **R43** — Success and failure both append an `auth_audit` row and mirror to `auth.security.audit` via the outbox.
- **R46** — The 401 is `application/problem+json`, no stack trace, no internal detail, no existence hint.
- **R48** — No PII beyond `email_verified` in the issued token.

## Locked Decisions

- **L8** — 10-minute RS256 JWT; `sub`, `scope` ⊇ `merchant.api`, `amr` ∋ `api_key`, plus `roles` / `client_id`. **Claim contract honoured; assembly path deviates per D1 (gate-approved).**
- **L11** — `POST /api-keys/token` is public and registered in `PublicEndpoints.java`.
- **L7** — Key format `ck_live_<24 alnum>.<32 alnum>`; SHA-256 only at rest; never echoed.
- **L9** — Claim set exactly `iss, sub, aud, exp, iat, nbf, jti, scope, roles, client_id, amr, acr, email_verified`; no email or name.
- **L12** — No cross-module entity imports.
- **L13** — No secret, key, or signing material committed or logged.
- **L1** — No DDL in T25.

## Inputs

- `POST /api-keys/token`, unauthenticated, no request body required.
- `Authorization: ApiKey ck_live_<suffix>.<secret>` — scheme matched case-insensitively, credential taken verbatim.
- Credential bounded at **256 characters** before any hashing; over-length input yields the uniform 401.
- Missing header, wrong scheme, blank credential → the same uniform 401.

## Outputs

**200** — `ApiKeyTokenResponse`:

| Field | Value |
|---|---|
| `access_token` | the signed compact JWT |
| `token_type` | `"Bearer"` |
| `expires_in` | TTL in **seconds** (600 at the default 10 minutes) |

The JWT: RS256, signed by the CURRENT key; `iss` = `spring.security.oauth2.authorizationserver.issuer`; `sub` = merchant account UUID; `scope` = **JSON array** containing `merchant.api` (echoed from the key row's `scopes`); `roles` = `RoleService.resolveEffectiveRoles(accountUuid)`; `amr` = `["api_key"]`; `acr` = `urn:themistra:acr:api_key`; `client_id` = `checky-api-key`; `iat` / `nbf` = now, `exp` = now + TTL; `jti` = a fresh random UUID. Claim set bounded by L9; no PII.

**401** — one `application/problem+json` body, byte-identical for every rejection cause, with a new stable `ProblemTypes` URI.
**500** — signing failure only, via the existing opaque handler (D5). Never a 401.

No response echoes the presented key, a hash, an email, or an internal id.

## State Changes

- `api_keys.last_used_at` ← exchange instant, success only (inside `ApiKeyService.exchange`).
- One `auth_audit` row + one outbox row per attempt: `api_key.exchanged` (SUCCESS) / `api_key.exchange_failed` (FAILURE), committed independently (`REQUIRES_NEW`).
- Nothing else. No new table, column, or index.

## Files to Create

- `apikey/ApiKeyTokenIssuer.java`
- `apikey/ApiKeyController.java` — **the controller variant is chosen**, not the `ApiKeyAuthenticationFilter` alternative `design.md` §6 offers: the custom `ApiKey` scheme (D4) means no filter is needed to keep the request away from `BearerTokenAuthenticationFilter`, and a controller keeps the rejection path inside the module's `@RestControllerAdvice`.
- `apikey/dto/ApiKeyTokenResponse.java`
- `apikey/ApiKeyExceptionHandler.java`
- Tests under `src/test/java/com/themistra/auth/apikey/`.

## Files to Modify

- `apikey/ApiKeyProperties.java` — add the validated `tokenTtlMinutes` field.
- `common/PublicEndpoints.java` — add `POST /api-keys/token` to `METHOD_SCOPED`.
- `common/ProblemTypes.java` — add the API-key rejection URI.
- `token/JwksConfig.java` — add the `JwtEncoder` bean (D1).
- `src/main/resources/application.properties` — add `themistra.auth.api-key.token-ttl-minutes`.

## Files NOT to Modify

Everything under `spec/`; `apikey/ApiKeyService.java`, `ApiKeyRepository.java`, `ApiKey.java`, `ApiKeyHasher.java`, `ApiKeyExchangeRejectedException.java`; `token/TokenClaimsCustomizer.java`, `RegisteredClientSeeder.java`, `AuthClientsProperties.java`, `SecurityChainsConfig.java`; `events/EventTopics.java`; all Flyway migrations `V1`–`V7`; other feature modules and their tests.

## Acceptance Criteria

- **AC1** — `POST /api-keys/token` is reachable with no prior authentication, using `Authorization: ApiKey <key>`. *(R31, L11, D4)*
- **AC2** — The path is in `PublicEndpoints.METHOD_SCOPED`, POST-scoped, and is the only public path added. *(L11)*
- **AC3** — A valid key returns 200 and an RS256 JWT signed by the current key. *(R31, L8)*
- **AC4** — `sub` equals the owning merchant's account UUID. *(R31, L8)*
- **AC5** — `scope` is a JSON array containing `merchant.api`. *(R31, L8, Kimi #12)*
- **AC6** — `amr` contains `api_key`; `acr` equals `urn:themistra:acr:api_key`. *(R31, L8, D3)*
- **AC7** — `exp − iat` is 10 minutes, driven by `themistra.auth.api-key.token-ttl-minutes` and the injected `Clock`; `expires_in` matches, in seconds. *(L8)*
- **AC8** — `roles` is present and freshly resolved; `client_id` = `checky-api-key`; the claim set stays within L9 with no PII beyond `email_verified`. *(L8, L9, R48, D2)*
- **AC9** — `last_used_at` is updated on success and on no failure path. *(R32)*
- **AC10** — Revoked, expired, malformed, unknown-prefix, wrong-secret, missing-header, wrong-scheme, and over-length inputs each return 401 with an identical problem body. *(R33, R46, D4)*
- **AC11** — No response echoes the key, a hash, an email, or an internal id. *(R46, L13)*
- **AC12** — Every attempt is audited with a row and exactly one outbox mirror. *(R43, Kimi #15)*
- **AC13** — No new migration; `api_keys` semantics unchanged. *(L1)*
- **AC14** — ArchUnit stays green: the new HTTP layer references neither `PublicEndpoints` nor a foreign module's entity. *(L12)*
- **AC15** — A signing failure yields 500, never 401. *(D5)*

## Required Tests

**Named (verbatim method names):**

1. `shouldExchangeValidApiKeyForMerchantJwt` — decode the JWT and assert `sub`, `scope` (array, contains `merchant.api`), `amr`, `acr`, `roles`, `client_id`, `exp − iat`, RS256, and the absence of PII claims. Javadoc traces to **R31**.
2. `shouldRejectRevokedOrUnknownApiKeyWithUniform401` — revoked, unknown prefix, malformed, wrong secret → 401, identical bodies. Javadoc traces to **R33**.

**Boundary / supporting:**

3. `last_used_at` written on success, untouched on every rejection path *(R32)*.
4. Expired key → uniform 401 against a fixed `Clock`, including the `expires_at == now` boundary.
5. A non-default `token-ttl-minutes` changes `exp` and `expires_in`.
6. `PublicEndpoints.METHOD_SCOPED` contains `POST /api-keys/token` (guard test).
7. Reachable anonymously through the real filter chain — and specifically that an `ApiKey`-schemed header is **not** intercepted by `BearerTokenAuthenticationFilter` (the D4 regression test).
8. Missing header, wrong scheme (including `Bearer`), blank credential, and a >256-character credential → the same uniform 401.
9. Audit: one SUCCESS row on the happy path, one FAILURE row per rejection, exactly one outbox mirror each.
10. `ApiKeyExchangeRejectedException` → 401 `application/problem+json`, not the 500 it produces today.
11. Response envelope field names are exactly `access_token` / `token_type` / `expires_in`.
12. `ArchitectureTest`, `PublicEndpointsTest`, `ApiKeyServiceIntegrationTest`, `TokenClaimsCustomizerTest`, and `SasLoginIntegrationTest` stay green — the last confirms the new `JwtEncoder` bean did not disturb SAS's own grants (D1).

Pure logic (TTL arithmetic, claim assembly, header parsing) as plain JUnit with a fixed `Clock`; anything touching the schema or the filter chain as `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.

## Constraints

- **Security** — The 401 is uniform across every cause: same status, type URI, and title, no varying detail. Never log or echo the presented key, its hash, or the account email. Bound the credential at 256 characters before hashing. `Bearer` is never accepted.
- **Transaction** — Per D5: `exchange` commits, then signing happens. No new `@Transactional` wrapper, no modification to `ApiKeyService`. Signing failure → 500.
- **Thread-safety** — `ApiKeyTokenIssuer` is a singleton serving concurrent unauthenticated requests: no mutable instance state; `NimbusJwtEncoder` is thread-safe and held as a field.
- **Module boundaries** — New classes in `com.themistra.auth.apikey` (the `JwtEncoder` bean excepted, which belongs in `token/`); no foreign entity imports; no reference to `PublicEndpoints` outside `token`/`common`.
- **Configuration** — Flat `application.properties`, `${ENV:default}` form, bound to the validated `ApiKeyProperties` record; startup fails on a missing/invalid TTL in non-local profiles.
- **Time** — All instants from the injected `Clock`; never `Instant.now()`.
- **Null handling** — A missing or blank `Authorization` header becomes the uniform 401, never an NPE or a 500.
- **Performance** — One `RoleService.resolveEffectiveRoles` call per exchange is accepted (the TIB's "no extra account lookups" constraint is struck, per Kimi #10). No per-request key-material reload beyond the existing `JWKSource`.

## Open Questions

**No blockers.** OQ-1/2/3/6 resolved by D1–D4. OQ-4, OQ-5, and OQ-7 deferred with named owners (tasks 33/34 and the spec author). Rejected findings #7, #8, and #11 are logged above with reasons and forward owners.

---

**Phase 4 complete — brief FROZEN.** Proceed to Phase 5 (Implementation Plan).
