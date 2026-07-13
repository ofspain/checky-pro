# Phase 3 — Gap Analysis: Reference (`authrex`) vs Target Architecture

**Date:** 2026-07-13 · Inputs: `reference-analysis.md` (Phase 2), `ARCHITECTURE.md` (§3.2, §4–6, §8),
repo engineering rules (root `README.md`), `SECURITY-THREAT-MODEL.md`.
**Output:** keep / redesign / remove / add decision per capability. No code in this phase.

Decision legend:
- **KEEP (idea)** — concept survives; implementation is rewritten to our standards
- **REDESIGN** — capability required, reference approach replaced
- **REMOVE** — not carried into our service
- **ADD** — required by target architecture, absent in reference

---

## 1. Capability-by-Capability Matrix

| # | Capability | Reference state | Target requirement | Decision | Rationale |
|---|---|---|---|---|---|
| 1 | Token issuance | Hand-rolled `jjwt`, custom `/api/auth/login`, custom grant enum | Standards-compliant OAuth2/OIDC issuer consumed by 3 resource-server services + SPA | **REDESIGN** | Spring Authorization Server (SAS): `/oauth2/token`, `/oauth2/jwks`, `/.well-known/openid-configuration`, `/oauth2/revoke` for free, audited by the Spring Security team. Custom token shapes would force every sibling service to special-case validation |
| 2 | SPA login flow | Password grant (deprecated in OAuth 2.1) | React PWA must authenticate securely without embedding secrets | **REDESIGN** | Authorization Code + **PKCE** (OAuth 2.1 baseline for public clients). Password grant is explicitly removed from OAuth 2.1; inheriting it would bake in a deprecated flow |
| 3 | Service-to-service auth | Custom client-credentials with hand-built claims; secret hash copied into token-builder `Identity` | Sibling services and future partners need scoped machine tokens | **KEEP (idea)** | Service principals are required; realized as SAS `client_credentials` registered clients with scopes. Reference's instinct was right, mechanics discarded |
| 4 | Token exchange / delegation | Present but unreachable (claims never issued); trusts inbound `original_roles` | No Phase 1 requirement; Phase 3+ dispute flows *might* need delegation | **REMOVE** | Dead, broken code. If delegation materializes, SAS supports RFC 8693 natively. Recorded as a future ADR trigger, not code |
| 5 | Refresh tokens | Plaintext UUID in Postgres; revoke-all on login (single session); rotation without reuse detection | Short-lived access + rotating refresh (ARCHITECTURE §3.2) | **REDESIGN** | Store only a SHA-256 **hash**; token families with **reuse detection** (a replayed rotated token revokes the whole family — the OAuth 2.1 recommendation); multi-session allowed, per-device sessions listable/revocable. Cleanup job idea kept |
| 6 | RBAC | Roles + role templates (bundles), direct & template assignment, sproc-backed | Phase 1 needs: `USER`, `MERCHANT`, `ADMIN`, `COMPLIANCE` + future dispute roles | **KEEP (idea)** | Role templates are good administration UX and map to our future org/team accounts. Reimplemented as JPA entities in our own domain; template expansion evaluated at token issue |
| 7 | Multi-tenancy (`domain_code`/`domain_type` claims + `X-` header conformity) | Present throughout token + filter logic | Single user population in Phase 1; institutions arrive Phase 5 | **REMOVE** | Unfunded complexity that taxes every token and filter today for a Phase 5 maybe. Claim namespace is designed to be extensible so tenancy can be added without breaking token consumers |
| 8 | Password storage & policy | BCrypt (good); history via sproc; **inverted** rotation check; broken expiry read | Secure by default | **REDESIGN** | BCrypt stays (strength ≥ 12). Password history dropped in favor of NIST 800-63B guidance: breached-password screening (k-anonymity HIBP check), length ≥ 12, no forced periodic rotation — which also deletes the two reference bugs by design |
| 9 | Account lockout | `locked` column, never set | Brute-force defense required (threat model #8) | **ADD** | Failed-attempt tracking with exponential backoff + temporary lock, surfaced as `auth.account.locked` event; admin unlock endpoint |
| 10 | MFA | Absent | **Day-one requirement** for invoice-capable accounts (ARCHITECTURE §3.2) | **ADD** | TOTP (RFC 6238) with encrypted seed storage, recovery codes (hashed, single-use), MFA enforced at token issuance via SAS customization; WebAuthn/passkeys as a fast-follow, schema designed for it |
| 11 | Email verification & password reset | Absent | Required for a financial-adjacent product | **ADD** | Single-use, hashed, TTL'd tokens; delivery via `auth.email.requested` event consumed by Notification Service — auth service sends no email itself (boundary rule) |
| 12 | Merchant API keys | Absent (client registration is adjacent but service-oriented) | Required: merchant integrations (ARCHITECTURE §3.2) | **ADD** | Prefixed keys (`ck_live_…`), SHA-256 hashed at rest, last-used tracking, scoped, revocable. Distinct from OAuth clients: API keys are *merchant-facing* credentials |
| 13 | Login/security audit | `last_login` update only | Evidence platform: our own auth events must be dispute-grade (§6.5 ethos) | **ADD** | Append-only `auth_audit` table (login success/failure, MFA events, lockouts, password/key changes, token revocations) + mirrored Kafka `auth.audit` topic for the Phase 2+ intelligence consumers |
| 14 | Rate limiting | Absent | Threat model #8; edge does coarse limits only | **ADD** | ingress-nginx handles IP-level; service adds per-account limits on `/oauth2/token`, login, reset, MFA verify (Bucket4j, backed by Postgres/Redis only if needed — start in-process per replica) |
| 15 | Domain events | None | `user.registered`, `user.suspended` + audit events via **outbox** (§4) | **ADD** | Uses `libs/java/outbox`; event schemas live in `contracts/events/`, generated models. Consumers: Notification, Payment, future Intelligence |
| 16 | Signing-key custody | Static PEM pair on classpath, raw-PEM endpoint, no rotation | KMS-grade custody posture (§6.4 ethos applies to auth, not just receipts) | **REDESIGN** | JWKS with `kid` + rotation. Keys generated/stored outside the artifact: Secrets Manager-injected at minimum; KMS-backed `JWKSource` as the ambitious option (decision deferred to Phase 4 design, both documented) |
| 17 | Secrets management | Plaintext properties; **committed credentials**; disabled hand-rolled SM client printing secrets | Secrets Manager + IRSA, nothing in the repo (§8) | **REDESIGN** | External Secrets Operator injects env/volume secrets; Spring config imports them; zero AWS SDK code in the service. The committed-credential incident becomes a CI rule: gitleaks/trufflehog gate in the pipeline |
| 18 | Persistence style | `JdbcClient` + Postgres stored procedures; JPA unused on classpath | JPA + Flyway (repo standard, `services/payment` parity) | **REDESIGN** | Sprocs split business logic between Java and SQL — untestable without a DB, invisible to review tooling. Flyway keeps DDL only. `IdentityDao` sproc catalogue reimplemented as repositories + queries |
| 19 | Domain model ownership | Entities imported from shared `com.netra:commons-netra` | Services own their domain; cross-service sharing only via `contracts/` (repo rule) | **REMOVE** (the pattern) | The distributed-monolith trap our monorepo rules exist to prevent. Auth defines its own `Identity`/`Credential`/etc. entities |
| 20 | Caching | Redis cluster config, env-prefixed keys, near-zero actual use | No proven auth-service cache need in Phase 1 | **REMOVE** (for now) | JWKS is cached by *consumers*; SAS caches internally. Env-prefixed key pattern noted for later. Redis returns only with a measured need (e.g., distributed rate limits) |
| 21 | Read/write datasource split | Configured, unused | Single RDS endpoint per service | **REMOVE** | Premature; RDS handles our scale. Reintroduce only with read-replica evidence |
| 22 | API surface | Custom envelope DTOs (`ApiResponse`, `AuthApiResponse`), Swagger annotations inline | OpenAPI spec lives in `contracts/api/`, models generated; RFC 9457 problem-details errors | **REDESIGN** | Contract-first is a repo rule; hand-written envelope DTOs replaced by spec-generated models + standard error shape |
| 23 | Observability | Actuator health only | OTel traces, Micrometer metrics, structured JSON logs; security metrics paged (Phase 1 gap-list item) | **ADD** | Failed logins, lockouts, MFA failures, token issuance/revocation rates, JWKS fetch errors as first-class metrics |
| 24 | Deployment | None (no Dockerfile/K8s/CI) | EKS via CDK service stack; path-filtered CI (repo standard) | **ADD** | Dockerfile (distroless, non-root), K8s Deployment + HPA + probes, IRSA role, CI job in existing workflow |
| 25 | Testing | 500 lines of `@SpringBootTest` against a live local DB, hard-coded users, tokens printed | Testcontainers + unit + security/authorization tests (dev-prompt mandate) | **REDESIGN** | Unit tests for token/policy logic; Testcontainers (Postgres+Kafka) integration tests; negative security tests derived from every Phase 2 defect (§3 below); no live-DB coupling |
| 26 | Scheduled jobs | Refresh-token cleanup cron | Still needed | **KEEP (idea)** | Reimplemented with ShedLock (multi-replica safety on EKS — reference assumed single instance) |

---

## 2. Violations Register (reference patterns our standards prohibit)

| Violation | Standard violated | Consequence if inherited |
|---|---|---|
| Committed DB password + RDS credential in comment | §8 secrets policy; basic hygiene | Credential leak; failed audit. Also mandates a **secret-scanning CI gate** so this class of error cannot merge |
| Signing keys inside the artifact | §6.4 custody posture | Anyone with image-pull access can mint valid tokens for the whole platform |
| Unauthenticated `/api/roles/**` whitelist ("testing only") | Zero-trust rule (§3.1) | Privilege-escalation-as-a-service; becomes a CI policy check (no `permitAll` outside an allowlisted set) |
| Shared domain-model artifact | Monorepo dependency rule | Lock-step deployments, schema coupling across services |
| Plaintext refresh tokens | Secure-by-default principle | DB read access = session hijack for every user |
| Trusting inbound token claims for authority (`original_roles`) | Zero-trust | Revoked privileges persist for token lifetime across exchanges |
| `allow-circular-references=true` | Engineering standards (design smell) | Hides dependency cycles that later block modularization |
| Logic-bearing stored procedures | Persistence standard | Untestable, review-invisible business rules |
| Password grant for first-party SPA | OAuth 2.1 baseline | Deprecated flow baked into clients; phishing-equivalent credential handling in JS |

---

## 3. Defect → Test-Case Mapping (Phase 2 findings become our regression suite)

| Phase 2 defect | Our test / control |
|---|---|
| Inverted rotation check | Unit tests on password policy boundaries (allowed/blocked cases, off-by-one days) |
| `Long.getLong` config misread | Config-binding test: `@ConfigurationProperties` records + startup validation (`@Validated`), so misreads fail boot, not silently default |
| Plaintext refresh tokens | Integration test asserts stored value ≠ presented token and matches SHA-256(presented) |
| No reuse detection | Security test: replaying a rotated refresh token revokes the family and 401s |
| Unauthenticated admin routes | ArchUnit/security test: every endpoint outside the published-public set requires authentication; CI fails on new `permitAll` |
| Broken token exchange | Not applicable (flow removed); ADR records the removal |
| Committed secrets | gitleaks gate in CI (repo-wide, not just auth) |
| Roles-from-token trust | Authorization tests assert authorities are DB/issuer-derived at issue time; resource-server tests in sibling services consume only standard claims |

---

## 4. Coupling & Operational-Complexity Deltas

**Coupling removed:** `commons-netra` shared models; sproc-embedded logic; Redis dependency;
read/write DB split; custom token shape that would have coupled every consumer to authrex quirks.

**Coupling added (deliberate, priced):** Kafka + outbox (platform-wide pattern, shared library);
`contracts/` codegen (build-time, no runtime coupling); Secrets Manager/IRSA (platform-wide).

**Net operational complexity:** the service itself gets *simpler* (fewer moving parts: Postgres +
Kafka only), while the platform absorbs complexity where it's shared (SAS is one dependency that
replaces ~8 hand-rolled mechanisms; ESO/IRSA/OTel are fleet-wide, not auth-specific).

---

## 5. Gap Summary

- **Carried forward as ideas (4):** grant-dispatch clarity, role templates, refresh rotation + cleanup, service principals.
- **Redesigned (8):** issuance (SAS), SPA flow (PKCE), refresh storage (hash + families), password policy (NIST), key custody (JWKS/KMS), secrets (ESO), persistence (JPA), API surface (contract-first), testing (Testcontainers).
- **Removed (5):** token exchange, multi-tenancy claims, Redis, read/write split, shared domain artifact.
- **Added — required but absent in reference (9):** MFA/TOTP, account lockout, email verification + password reset, merchant API keys, audit trail, rate limiting, Kafka events via outbox, observability, deployment/CI.

The reference ultimately contributes **~4 concepts and a defect catalogue**; the remaining ~80%
of the target service is either redesigned or net-new. This matches the dev-prompt's success
criterion: influenced, not dictated.

---

*Next: Phase 4 — target service design (module breakdown, domain model, SAS configuration
strategy, token/claims spec, schema, event contracts, K8s/deployment, testing strategy), with
open decisions (KMS-backed JWKS vs Secrets-Manager-injected keys; SAS session model for the SPA)
resolved with trade-off analysis.*
