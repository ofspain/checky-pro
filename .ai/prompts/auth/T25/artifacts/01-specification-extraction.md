# auth · T25 · Phase 1 — Specification Extraction

**Task:** T25 — API-key exchange endpoint. `POST /api-keys/token` (public); validate the key, update `last_used_at`, issue a JWT via `ApiKeyTokenIssuer`, and audit.
**Consumes:** `artifacts/00-repository-understanding.md`.
**Starting set:** `R31`, `R32`, `R33` · `L8`, `L11`. Widened only where the task statement's own words ("and audit") or an unavoidable platform rule (error format, key parsing, claim set, module boundaries) make a requirement binding on this endpoint. Every widening is justified inline.

---

## 1. Business Rules

**Scoped (from the prompt header):**

- **R31** — A valid, non-expired, non-revoked API key presented in the `Authorization` header to `POST /api-keys/token` yields a 10-minute JWT whose `sub` is the merchant account UUID, whose `scope` contains `merchant.api`, and whose `amr` contains `api_key`.
- **R32** — A successful exchange updates that key's `last_used_at`.
- **R33** — A revoked, expired, malformed, or hash-mismatched key produces a uniform `401 Unauthorized`.

**Widened — binding on this endpoint:**

- **R43** *(the task statement says "and audit")* — Every security-relevant action, API-key operations included, appends an `auth_audit` row and mirrors a reduced event to `auth.security.audit` through the outbox.
- **R46** *(this endpoint returns 4xx)* — Any 4xx response is an RFC 9457 `application/problem+json` body carrying no stack trace, no internal detail, and no hint about key or account existence. R33's uniformity is the enumeration-safety half of the same rule.
- **R48** *(this endpoint mints an access token)* — Every access token contains exactly the claims in `contracts/api/token-claims.md` and no PII beyond `email_verified`. See Open Question **OQ-4**: that contract file does not exist yet.

**Explicitly out of scope for T25** (belongs to T26 / T27 / T33): `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}` (R30/R34/R35 — the service methods already exist from T24 but get no HTTP surface here), the create→exchange→revoke integration test (task 27), and `contracts/api/auth.yaml` (task 33).

---

## 2. Locked Decisions

**Scoped:**

- **L8 — API-key JWT contract.** `POST /api-keys/token` issues a **10-minute RS256** JWT with `sub` = merchant account UUID, `scope` containing `merchant.api`, `amr` containing `api_key`, and the standard `roles` / `client_id` claims **via the existing `TokenClaimsCustomizer` path**. (See **OQ-1** — that path has no `api_key` branch today, and **OQ-2** — no `client_id` is defined for a keyholder.)
- **L11 — Public endpoint discipline.** The unauthenticated set is exhaustive: actuator health/info/prometheus, `POST /accounts`, the SAS protocol endpoints, and **`POST /api-keys/token`**. Any new public path must be added to `PublicEndpoints.java`.

**Widened — unavoidably constrains this task:**

- **L7 — API key format.** `ck_live_<24-char suffix>.<32-char secret>`; only SHA-256 stored. T25 does not generate keys, but the endpoint must accept and parse exactly this shape, and `ApiKeyService.exchange` already splits on the `.` separator.
- **L9 — Token claims contract.** The access-token claim set is exactly `iss, sub, aud, exp, iat, nbf, jti, scope, roles, client_id, amr, acr, email_verified`; no email or name. L8's JWT is an access token, so its claim set is bounded by L9. Note L9 lists `acr`, which L8 does not mention for the api-key case — see **OQ-3**.
- **L12 — Module boundaries.** The new `apikey` HTTP layer must not import another module's entity; cross-module data comes through service methods or the repository's native UUID/id resolvers, as `ApiKeyService` already does.
- **L13 — Secrets discipline.** No key material, signing key, or plaintext credential committed or logged; the token response type must not leak the presented key.
- **L1 — Migration immutability.** V1–V4 immutable. T25 implies no DDL at all (`api_keys` is fully provisioned through V7); if any were needed it would be `V8`.

---

## 3. Files involved

### Existing — read / extend

| File | Why |
|---|---|
| `apikey/ApiKeyService.java` | `exchange(String)` (T24) already performs validation, the non-short-circuiting constant-time compare, the `last_used_at` update (R32), and both audit events (R43); returns `ExchangeResult(accountUuid, scopes)`. T25 consumes it — it is not to be rewritten. |
| `apikey/ApiKeyExchangeRejectedException.java` | The single uniform rejection (R33) that the HTTP layer must map to 401. |
| `apikey/ApiKeyProperties.java` | Extend with the token TTL key (§4). |
| `apikey/ApiKeyRepository.java`, `apikey/ApiKey.java`, `apikey/ApiKeyHasher.java` | Already complete; read-only for this task. |
| `common/PublicEndpoints.java` | Add the `POST /api-keys/token` method-scoped entry (L11). |
| `common/ProblemTypes.java` | Add the stable problem-type URI for the uniform 401 (no existing constant fits). |
| `token/TokenClaimsCustomizer.java` | L8 names it as the claims path (see OQ-1). |
| `token/JwksConfig.java`, `token/SigningKeyMaterial.java` | Source of the RS256 signing material L8 requires. |
| `token/SecurityChainsConfig.java` | The `@Order(2)` application chain that governs whether the new public path is reachable, and how a non-JWT `Authorization` header is treated. |
| `src/main/resources/application.properties` | Add `themistra.auth.api-key.token-ttl-minutes`. |
| `account/AccountController.java`, `account/AccountExceptionHandler.java` | Pattern references only — controller shape and the uniform-rejection advice precedent. |
| `src/test/java/com/themistra/auth/ArchitectureTest.java` | `only_token_module_references_public_endpoints` means the new controller must not reference `PublicEndpoints`. |

### New — expected by the spec

| File | Source |
|---|---|
| `apikey/ApiKeyTokenIssuer.java` | Task statement (verbatim) and `design.md` §6. |
| `apikey/ApiKeyController.java` **or** `apikey/ApiKeyAuthenticationFilter.java` | `design.md` §6 lists both, joined by "or" — the choice is a Phase 2/3 design decision, not a spec fact. |
| `apikey/dto/ApiKeyTokenResponse.java` | `design.md` §6. |
| `apikey/ApiKeyExceptionHandler.java` (name not fixed by spec) | Implied by R33 + R46 + the one-advice-per-module pattern; `ApiKeyExchangeRejectedException` is unmapped today and would surface as a 500. |
| Tests mirroring the package layout | `design.md` §6 ("Tests mirror the package layout"). |

---

## 4. Dependencies

**Classes / services:** `ApiKeyService` (`exchange`, `ExchangeResult`), `ApiKeyProperties`, `ApiKeyExchangeRejectedException`, `ApiKeyHasher`, `ApiKeyRepository`, `ApiKey`; `AuditService` + `RecordAuditEventRequest` + `AuditOutcome` (already invoked inside `exchange`); `RoleService.resolveEffectiveRoles(UUID)` for the `roles` claim; `TokenClaimsCustomizer`; `JWKSource<SecurityContext>`; `Clock` bean (`SecurityBeansConfig`); `PublicEndpoints`; `ProblemTypes`.

**Entities / tables:** `api_keys` (`key_hash`, `prefix`, `scopes`, `last_used_at`, `expires_at`, `revoked_at`) — no schema change; `accounts` (UUID resolution via the repository's native queries); `auth_audit` + `outbox_event` via `AuditService`.

**Config keys:**
- Existing: `themistra.auth.api-key.prefix` (`application.properties:95`); `spring.security.oauth2.authorizationserver.issuer` / `...resourceserver.jwt.issuer-uri` (both `${AUTH_ISSUER_URI:http://localhost:8080}`) — the `iss` value L9 requires; the `themistra.auth.jwt.*` signing-key properties behind `SigningKeysProperties`.
- **New:** `themistra.auth.api-key.token-ttl-minutes=10` — specified verbatim in `design.md` §4c, absent from both `application.properties` and `ApiKeyProperties`. Must be a validated field on that record (flat properties, fail-fast binding per `agents.md`).

**Events / topics:** none new. `EventTopics` already maps `audit` → `auth.security.audit`; the `api-key` → `auth.email.requested` line in `design.md` §4c's verbatim block belongs to a different flow and is **not** required by T25 (this endpoint sends no email).

**Contracts:** `contracts/api/token-claims.md` and `contracts/api/auth.yaml` are named by the prompt but **do not exist in the repo** (`contracts/api/` is empty). `contracts/events/auth/` holds only `user-lifecycle.v1.schema.json`; the `email-requested` and `security-audit` schemas listed in the prompt header are likewise absent. They are tasks 33/34. See **OQ-4**.

---

## 5. Acceptance Criteria

| # | Criterion | Requirement / Locked |
|---|---|---|
| AC1 | `POST /api-keys/token` exists and is reachable without prior authentication. | R31, L11 |
| AC2 | The path is registered in `PublicEndpoints` (method-scoped to POST), and is the only new public path added. | L11 |
| AC3 | A valid, unexpired, unrevoked key returns 200 with a token response; the token is an RS256 JWT signed by the service's current signing key. | R31, L8 |
| AC4 | The issued JWT's `sub` equals the owning merchant's account UUID. | R31, L8 |
| AC5 | The issued JWT's `scope` contains `merchant.api`. | R31, L8 |
| AC6 | The issued JWT's `amr` contains `api_key`. | R31, L8 |
| AC7 | The JWT's lifetime is 10 minutes (`exp − iat`), driven by `themistra.auth.api-key.token-ttl-minutes`, computed from the injected `Clock`. | L8, `design.md` §4c |
| AC8 | The JWT carries `roles` and `client_id`, and its overall claim set stays within L9's list — no email, no name, no PII beyond `email_verified`. | L8, L9, R48 |
| AC9 | On success, the key's `last_used_at` is set to the exchange instant. | R32 |
| AC10 | Revoked, expired, malformed, unknown-prefix, and hash-mismatched keys each return **401** with a byte-identical `application/problem+json` body — no field, type URI, title, or detail varies by cause. | R33, R46 |
| AC11 | No response on any path echoes the presented key, a hash, the account's email, or an internal id. | R46, L13 |
| AC12 | Success and every failure are audited (`api_key.exchanged` / `api_key.exchange_failed`) with an `auth_audit` row and an `auth.security.audit` outbox mirror. | R43 |
| AC13 | No new Flyway migration; `api_keys` semantics unchanged. | L1 |
| AC14 | ArchUnit stays green: the new controller does not reference `PublicEndpoints`, imports no foreign module entity, and the new repository-free HTTP layer respects `api → application → domain`. | L12 |

---

## 6. Tests required

**Named (`package.md` §8) — required verbatim:**

1. `shouldExchangeValidApiKeyForMerchantJwt` — end-to-end through the endpoint: create a merchant key (via `ApiKeyService.create`), exchange it, assert 200 and decode the JWT for `sub`, `scope` ⊇ `merchant.api`, `amr` ∋ `api_key`, `exp − iat` = 10 min, `roles`, `client_id`, RS256 header, and no PII claims. Covers AC3–AC8.
2. `shouldRejectRevokedOrUnknownApiKeyWithUniform401` — assert 401 and an *identical* problem body across at least: revoked key, unknown prefix, malformed key (no separator), and correct prefix with a wrong secret. Covers AC10.

**Boundary / supporting tests implied by the acceptance criteria:**

3. `last_used_at` is written on success and **not** written on any rejection path (AC9, R32).
4. Expired key (`expires_at` in the past, evaluated against a fixed `Clock`) → the same uniform 401 (AC10). Time boundary: `expires_at` exactly equal to now must be treated consistently with `ApiKeyService.exchange`'s existing `isAfter(now)` semantics.
5. TTL is driven by configuration, not a literal — a non-default `token-ttl-minutes` changes `exp` (AC7).
6. `PublicEndpoints.METHOD_SCOPED` contains `POST /api-keys/token` — a `PublicEndpointsTest`-style guard so removal fails CI (AC2).
7. The endpoint is reachable **anonymously** through the real filter chain (AC1) — this is the test that would catch the resource-server/`Authorization`-header interaction raised in Phase 0 §5.4.
8. Audit assertions: one `api_key.exchanged` SUCCESS row on the happy path and one `api_key.exchange_failed` FAILURE row per rejection, each with an outbox mirror (AC12).
9. `ApiKeyExchangeRejectedException` maps to 401 `application/problem+json`, not 500 (AC10, R46) — the current unmapped state is a 500.
10. Existing suites stay green: `ArchitectureTest`, `PublicEndpointsTest`, `ApiKeyServiceIntegrationTest`, `TokenClaimsCustomizerTest`.

Convention per `agents.md`: pure logic (TTL arithmetic, claim assembly) as plain JUnit with a fixed `Clock`; anything touching the schema or the filter chain as `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.

---

## 7. Open Questions

Genuine blockers or spec conflicts only. None is resolved here; none licenses a silent deviation.

- **OQ-1 (blocking design, not extraction) — L8's "existing `TokenClaimsCustomizer` path" does not exist for this grant.** `TokenClaimsCustomizer` is an `OAuth2TokenCustomizer<JwtEncodingContext>` driven by SAS's token generators during a grant, and branches only on `client_credentials` vs. interactive; it has no `api_key` branch, and `tasks.md` task 21 — which would have added one — is not implemented. Compounding this, no `JwtEncoder` bean exists in the context (Phase 0 §5.2). Whether L8 means "drive a synthetic SAS authorization", "extend the customizer and invoke it directly", or "mint the JWT independently and mirror the claim set" is not answerable from the spec. **Phase 2/3 must resolve it; if the resolution requires deviating from L8 as written, it stops at the Phase 4 human gate.**

- **OQ-2 — What `client_id` does an API-key JWT carry?** L8 and L9 both require the claim, but an API-key exchange has no OAuth2 client: the caller presents a key, not client credentials. `RegisteredClientSeeder` seeds the SPA and service clients; none corresponds to a keyholder. The spec names no value.

- **OQ-3 — Is `acr` required on the API-key JWT?** L9 lists `acr` in the exact claim set; L8 enumerates `sub`, `scope`, `amr`, `roles`, `client_id` for this token and is silent on `acr`. `requirements.md` R26/R27 define `acr` values only for interactive logins. No `urn:themistra:acr:*` value is defined for an API-key credential.

- **OQ-4 — The contracts named in the prompt header do not exist.** `contracts/api/auth.yaml` and `contracts/api/token-claims.md` are unwritten (task 33/34), so R47/R48 cannot be verified against a real artifact during T25. Conformance testing for this endpoint necessarily defers to task 33.

- **OQ-5 — `package.md` §8's requirement mapping is stale for this task.** §8 maps `shouldExchangeValidApiKeyForMerchantJwt` → R28 and `shouldRejectRevokedOrUnknownApiKeyWithUniform401` → R29, but `requirements.md` R28/R29 are MFA-disable and MFA-failure requirements; the API-key exchange requirements are R31/R32/R33, exactly as the prompt header scopes them. The §8 block is offset by three across the API-key and session rows. I proceed on `requirements.md` R31–R33 (matching the prompt) and flag §8 for the author. `spec/` is not modified.

- **OQ-6 — The `Authorization` header scheme for the presented key is unspecified.** R31 says the key arrives "in the `Authorization` header"; no scheme token (`Bearer`, `ApiKey`, …) is given anywhere in the spec package, and `ApiKeyService.exchange` accepts a bare `prefix.secret` string. This is not merely cosmetic: on the `@Order(2)` chain, a `Bearer`-schemed value is picked up by the resource-server filter and decoded as a JWT. Phase 0 §5.4 records the mechanism; the spec does not settle the scheme.

- **OQ-7 (author question, already open) — `package.md` §11 Q3** asks whether `merchant.api` is the only launch scope and whether a per-merchant key cap exists. T24 hardcoded `DEFAULT_SCOPES = ["merchant.api"]`. R31 requires only that the issued `scope` *contain* `merchant.api`, so T25 is not blocked, but whether the issuer echoes the row's `scopes` column or emits a fixed value depends on Q3's eventual answer.

---

**Phase 1 complete.** One artifact written: `artifacts/01-specification-extraction.md`. No design, no implementation — Phase 2 next.
