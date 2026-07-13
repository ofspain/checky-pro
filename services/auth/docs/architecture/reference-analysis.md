# Phase 2 — Reference Project Analysis: `netra-identity-service` (authrex)

**Reviewed:** 2026-07-13 · local path `/Users/macbookpro/IdeaProjects/netra-identity-service`
**Scope:** architecture review only, no code generated. Input to Phase 3 (gap analysis).
**Size:** ~4,600 lines of Java across ~60 classes; Maven; Spring Boot 3.5.4; Java 21.

---

## 1. Overall Architecture

**Layout:** single Maven module, layer-by-type packages (`configs`, `controllers`, `daos`, `dtos`, `exceptions`, `model`, `schedulers`, `services`).

**Layering:** Controller → Service → DAO. DAOs use Spring `JdbcClient` with raw SQL and
Postgres **stored procedures/functions** (upserts, searches, password history) created via Flyway.
JPA is on the classpath but essentially unused — the persistence style is JDBC + sproc.

**External coupling:** depends on a shared artifact `com.netra:commons-netra` which owns the core
domain models (`Identity`, `RefreshToken`, `PasswordHistory`, enums, row mappers, util classes).
The auth service does not own its own domain model.

**Token architecture:** self-issued JWTs via the `jjwt` library — **not** Spring Authorization
Server, and **not OAuth2/OIDC compliant**. Custom grant dispatch (`PASSWORD`, `CLIENT_CREDENTIALS`,
`TOKEN_EXCHANGE`) through one `/api/auth/login` endpoint with a custom request/response shape.
RS256 signing with a PEM keypair loaded **from the classpath**. Public key published at
`/api/auth/public-key` as raw PEM (no JWKS, no key IDs, no rotation support).

**Notable design ideas present:** domain/tenant concept (`domain_code`, `domain_type` claims with
an `X-` header conformity check), service principals distinguished from users via claims, role
templates (role bundles) for RBAC assignment, read/write datasource split scaffolding, Redis
cache manager with environment-prefixed keys, scheduled refresh-token cleanup.

---

## 2. Security Feature Inventory

| Capability | Present? | Notes |
|---|---|---|
| Password grant (login) | ✅ | Spring `AuthenticationManager` + BCrypt |
| Client credentials | ✅ | Custom implementation, UUID clientId + BCrypt-hashed secret |
| Token exchange (RFC 8693-ish) | ⚠️ broken | Requires `exchangeable`/`authorized_client` claims that **no code path ever issues** — dead/untestable flow |
| Refresh tokens | ⚠️ weak | Random UUID stored **in plaintext** in Postgres; rotation on refresh; all-token revocation per user on new login (single-session); no reuse detection |
| Token revocation | ⚠️ partial | Refresh only; access tokens irrevocable (acceptable if short-lived, but expiry is 3600s) |
| RBAC + role templates | ✅ | Roles, templates (bundles), direct + template assignment; `@PreAuthorize` on some endpoints |
| Password policy | ⚠️ buggy | History check (last N via sproc) works; rotation check **logic is inverted** (see §4.1) |
| Password expiry | ⚠️ buggy | `Long.getLong(...)` misuse — reads a JVM system property, not the config value (§4.2) |
| Account lockout | ❌ | `locked` column exists; no code ever sets it; no failed-attempt counting |
| MFA / TOTP | ❌ | Absent |
| Email verification | ❌ | Absent (no email capability at all) |
| Password reset | ❌ | Only authenticated change-password; no forgot-password flow |
| Login audit | ❌ | `updateLastLogin` only; no audit trail of auth events |
| Rate limiting / brute-force defense | ❌ | Absent |
| OIDC / OAuth2 endpoints | ❌ | No `/.well-known`, no JWKS, no authorization code, no PKCE |
| Key rotation | ❌ | Single static PEM pair on classpath; clients told to poll a raw-PEM endpoint |
| Secrets management | ❌ disabled | `AwsSecretsManagerService` is commented out (`//@Service`), hand-rolled, prints fetched credentials to stdout |
| Event publishing (Kafka etc.) | ❌ | No messaging of any kind |

---

## 3. Infrastructure

- **DB:** Postgres, Flyway (versioned migrations incl. sprocs). HikariCP tuned manually.
- **Cache:** Redis (Lettuce), cluster-mode config for prod, env-prefixed cache keys. Actual
  `@Cacheable` usage is minimal — infrastructure without a consumer.
- **Messaging:** none.
- **Secrets:** plaintext in `application.properties` — **a real local DB password and, inside a
  comment in `AwsSecretsManagerService`, a pasted production-looking RDS password + hostname are
  committed to the repo.**
- **Deployment:** no Dockerfile, no Kubernetes manifests, no CI config in the repo.
- **Observability:** actuator health only; no metrics, no tracing, no structured logging;
  logging includes sensitive-adjacent info at `info` level.

---

## 4. Defects Found (beyond missing features)

1. **Inverted password-rotation check** (`PasswordPolicyService.validatePasswordRotationInterval`):
   throws if the password *was* changed within the last 90 days — i.e., it blocks changing your
   password more than once per 90 days, instead of forcing a change after 90 days.
2. **`Long.getLong(passwordExpiryDays, 90)`** (`IdentityService.isPasswordExpired`): reads a JVM
   *system property* named after the config value; always returns the default. The configured
   expiry is silently ignored.
3. **Unauthenticated role administration**: `TEST_INVOKE_WHITE_LIST` permits `/api/roles/**` and
   `/api/role-templates/**` without auth, marked "remove, testing only" — the classic temporary
   hole that ships.
4. **`spring.security.filter` + `spring.autoconfigure.exclude=SecurityAutoConfiguration`** in
   properties contradicts the `@EnableWebSecurity` config — config drift, unclear what's active.
5. **`spring.main.allow-circular-references=true`** — masks a design smell instead of fixing it.
6. **Client-credentials flow copies the BCrypt secret hash into the token-building `Identity`**
   and role-template registration checks `existsByClientCode(request.getClientName())` (wrong
   field). Duplicate-name check performed twice, code check never.
7. **Token exchange trusts `original_roles` claim** from the inbound token rather than reloading
   from the DB (privilege persistence if roles were revoked) — moot only because the flow is
   currently unreachable (§2).
8. **~120 lines of pasted AI-chat transcript** left as comments in `AuthController`; repeated
   `//todo` blocks; commented-out alternates throughout — signals unreviewed code.
9. **Tests** (~500 lines): `@SpringBootTest` controller tests that hit a real local DB with
   hard-coded users ("johndoe"/"password123"), print tokens to stdout; no Testcontainers, no
   unit tests of token logic, no negative security tests, no authorization tests.

---

## 5. Engineering Assessment (per prompt classification)

| Feature / pattern | Verdict | Why |
|---|---|---|
| Password login via Spring `AuthenticationManager` + BCrypt | ✅ Essential | Correct use of the framework primitive |
| Refresh token rotation + scheduled cleanup | ✅ Useful | Right idea; storage (plaintext) and single-session policy need redesign |
| Client credentials for service-to-service | ✅ Useful | Needed in a microservice fleet; implementation needs Spring Authorization Server, not hand-rolled claims |
| RBAC with role templates | ✅ Useful | Role bundles are a genuinely good administration UX; worth keeping conceptually |
| Domain/tenant claims (`domain_code`/`domain_type`) | ⚠ Optional | Multi-tenant seed idea; Themistra Phase 1 has one user population — defer, don't inherit complexity |
| Read/write datasource split | ❌ Over-engineered | Premature for an auth service; adds config surface with no consumer |
| Redis cache infrastructure | ❌ Over-engineered (here) | Configured cluster support but almost nothing cached; carry the *need* (token/JWKS caching) not the code |
| Stored-procedure persistence layer | ❌ Unnecessary | Business logic split between Java and SQL sprocs; hurts testability/reviewability; JPA + Flyway DDL is the project standard |
| Shared `commons-netra` domain models | ❌ Violates our architecture | Services must own their domain; sharing entity classes across services is the distributed-monolith trap our `contracts/` rule exists to prevent |
| Hand-rolled JWT issuance (jjwt) | ❌ Under-engineered | No OIDC discovery, no JWKS, no kid/rotation, custom token shapes every client must special-case. Spring Authorization Server gives all of it, audited |
| Custom token exchange | ❌ Under-engineered + broken | Unreachable flow, trusts inbound claims; if delegation is ever needed, SAS supports RFC 8693 properly |
| Raw-PEM public-key endpoint | ❌ Under-engineered | JWKS with `kid` is the standard our resource servers (and third parties) should consume |
| Classpath PEM signing keys | ❌ Unacceptable | Keys ship inside the artifact; contradicts our KMS-grade key custody posture |
| Secrets in properties / committed credentials | ❌ Unacceptable | Must be rotated & purged; our stack uses Secrets Manager + External Secrets |
| MFA, lockout, audit, email verification, rate limiting | ❌ Missing | All required by our architecture (MFA is a day-one commitment) |
| Kafka events | ❌ Missing | Our architecture requires `user.registered` etc. via outbox |

---

## 6. What We Take Forward (ideas, not code)

1. **Grant-type dispatch clarity** — one clean entry point per flow; we get this natively from
   Spring Authorization Server's `/oauth2/token`.
2. **Role templates** — keep as an RBAC administration concept in our own domain model.
3. **Refresh-token hygiene jobs** — scheduled cleanup is right; we add hashing + family-based
   reuse detection.
4. **Service principals as first-class** — correct instinct; realized properly as SAS
   client-credentials clients with scoped tokens.
5. **Environment-prefixed cache keys** — small, sensible pattern if/when we cache.
6. **The failure catalogue itself** — §4 becomes negative test cases and threat-model entries
   for our implementation (e.g., "temporary whitelist" is exactly what CI policy checks must catch).

## 7. What We Explicitly Reject

Hand-rolled JWT/OAuth mechanics; shared domain-model artifact; sproc-centric persistence;
plaintext refresh tokens; classpath signing keys; secrets in properties; unauthenticated
admin whitelists; the broken token-exchange flow; Redis-first caching without a proven need;
read/write datasource split; `allow-circular-references`; AI-transcript comments and TODO-driven
security decisions.

---

*Next: Phase 3 gap analysis maps this inventory against `ARCHITECTURE.md` §3.2 and the
engineering standards, feature by feature, with keep/redesign/remove/add decisions.*
