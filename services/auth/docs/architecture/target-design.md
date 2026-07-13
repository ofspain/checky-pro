# Phase 4 — Target Authentication Service Design

**Date:** 2026-07-13 · Status: for review · No implementation code in this phase.
**Inputs:** `ARCHITECTURE.md`, `gap-analysis.md`, `auth-decisions.md` (D-001…D-014).

---

## 1. Responsibilities

The auth service is the platform's **identity issuer**. It owns:

1. Account lifecycle — registration, email verification, profile basics, suspension, deletion.
2. Authentication — password login, TOTP MFA, account lockout, password reset.
3. Token issuance — OIDC/OAuth2 via Spring Authorization Server: authorization code + PKCE (SPA), client credentials (services), refresh-token rotation, revocation, JWKS.
4. Authorization data — roles, role templates, their assignment; authorities embedded in tokens at issue time.
5. Merchant API keys — issuance, rotation, revocation, and **exchange for standard JWTs**.
6. Security audit — append-only record of every auth-relevant event, mirrored to Kafka.
7. Domain events — `auth.user.registered`, `auth.user.suspended`, email-request events, via outbox.

**Explicit non-responsibilities:** sending email (Notification service, via events); payment/invoice
authorization decisions (Payment service applies its own rules to token authorities); tenancy
(Phase 5, see D-007); social/federated login (future ADR).

---

## 2. Module Breakdown & Package Structure

Package-by-feature under `com.themistra.auth` (D: this service is the template for the fleet).
Each module = one package owning its entities, repositories, services, and API endpoints; ArchUnit
enforces the dependency direction `api → application → domain` *within* a module and forbids
cross-module entity imports.

| Module (package) | Purpose | Depends on |
|---|---|---|
| `account` | Registration, verification, profile, suspension | `authz` (role assignment), `events`, `audit` |
| `authn` | Login flow customization for SAS: password auth, **MFA step**, lockout, password policy & reset | `account`, `audit`, `events` |
| `mfa` | TOTP enrollment, recovery codes, seed crypto | `audit` |
| `token` | SAS configuration: registered clients, claims customizer, JWKS & key rotation, authorization persistence (hashed refresh tokens, families) | `account`, `authz` |
| `authz` | Roles, role templates, assignments | — |
| `apikey` | Merchant API keys: CRUD, hashing, **key→JWT exchange endpoint** | `token`, `audit` |
| `audit` | Append-only audit writer + Kafka mirror | `events` |
| `events` | Outbox publishing (wraps `libs/java/outbox`), event model mapping from `contracts/` codegen | — |
| `admin` | Operator endpoints (unlock, suspend, key revoke), all `ADMIN`-scoped | thin facade over other modules |
| `common` | Error handling (RFC 9457), validated `@ConfigurationProperties`, shared web config | — |

Module count is deliberately flat — no `core`/`util` dumping grounds; anything shared by two
modules either belongs in `common` (plumbing only) or signals a wrong boundary.

---

## 3. Domain Model

Owned entirely by this service (D-004). Key aggregates:

- **Account** — `id (UUID, external subject)`, `email (citext, unique)`, `emailVerified`, `passwordHash (bcrypt)`, `status (PENDING_VERIFICATION | ACTIVE | LOCKED | SUSPENDED | DELETED)`, `createdAt`, timestamps. The UUID is the `sub` claim; internal bigint PKs never leak into tokens or APIs.
- **MfaEnrollment** — `accountId`, `type (TOTP)`, `secretEncrypted`, `confirmedAt`, `lastUsedAt`. One active TOTP enrollment per account (schema allows more types later — WebAuthn is a planned addition).
- **RecoveryCode** — `accountId`, `codeHash`, `usedAt` (single-use, 10 per enrollment).
- **Role** / **RoleTemplate** / assignment tables — role templates are named bundles (from the reference, reimplemented); template expansion happens at token issuance, so a template edit affects future tokens only (documented behavior).
- **ApiKey** — `id`, `accountId (merchant)`, `prefix (ck_live_xxxx, lookupable)`, `keyHash (SHA-256)`, `scopes`, `lastUsedAt`, `expiresAt?`, `revokedAt?`. Plaintext shown exactly once at creation.
- **LoginAttempt / LockoutState** — failed-attempt counter with decay, lock expiry, lock reason.
- **VerificationToken** — `accountId`, `purpose (EMAIL_VERIFY | PASSWORD_RESET)`, `tokenHash`, `expiresAt`, `usedAt`. Single-use, hashed, 30-min TTL.
- **AuthAuditEvent** — see §15.
- **SAS persistence** — `oauth2_registered_client`, `oauth2_authorization` (customized: token values stored **hashed**; extra columns `family_id`, `device_label` for refresh families).
- **Outbox** — standard table from `libs/java/outbox`.

---

## 4. Security Model

- **Zero trust:** every non-public endpoint requires a valid JWT; the service is also a resource server for its own management APIs. Public set (exhaustive, CI-enforced): SAS protocol endpoints, `/.well-known/*`, registration, verification/reset confirmation, health probes.
- **Password policy (NIST 800-63B, D-006):** min 12 chars, max 128, no composition rules, breached-password screening via k-anonymity range API at set/change time (fail-open with audit event if the API is down), no periodic rotation.
- **Lockout:** 5 failed attempts → 15-min lock, exponential doubling, counter decays after 30 min; lock events audited + emitted; admin unlock endpoint. Response to a locked login is indistinguishable from bad credentials (no enumeration oracle).
- **MFA (D-014):** enforced *inside* the interactive authentication flow — after password success, SAS's authentication chain requires a TOTP step before the authorization code is issued. Tokens carry `amr: ["pwd","otp"]` and `acr`. MFA is mandatory for accounts holding `MERCHANT` or `ADMIN` roles; optional otherwise. TOTP seeds encrypted (AES-GCM) with a data key envelope-encrypted by KMS.
- **No enumeration anywhere:** registration, reset, and login return uniform responses/timing whether or not the account exists.

Threat-model mapping (platform `SECURITY-THREAT-MODEL.md` #8 + auth-local threats) is maintained
in the decision log as tests are written.

---

## 5. OAuth2 Flows & OIDC Support

| Flow | Client | Notes |
|---|---|---|
| Authorization Code + **PKCE** | React PWA (public client) | First-party login page served by SAS; SAS session cookie (httpOnly, SameSite=Lax) enables silent renewal |
| Client Credentials | payment-service, notification-service, crypto-service | Scoped machine tokens, e.g. `internal.accounts:read` |
| Refresh Token (rotation) | SPA | Families + reuse detection (§7) |
| Revocation (RFC 7009) | all | `/oauth2/revoke` |
| **Not supported:** password grant, implicit, token exchange (D-002, D-008), device code | | |

**OIDC:** discovery document, `id_token` for the SPA, `openid profile email` scopes, `/userinfo`.
Custom scopes: `merchant.api` (API-key-derived tokens), `internal.*` (service-to-service).

**Client registration (Phase 1):** static — registered clients are provisioned by Flyway migration
/ configuration, not a runtime API. A dynamic client-registration API is a Phase 5 (partner plane)
concern. Merchant-facing credentials are **API keys** (§9), not OAuth clients.

---

## 6. JWT Strategy

- **Signature:** RS256, 3072-bit keys. Chosen over ES256 for maximal ecosystem compatibility (partner verification of tokens later); revisit if token size matters.
- **Access-token TTL: 10 minutes.** Revocation story = short TTL + refresh rotation; no introspection round-trips for resource servers.
- **Claims (the contract with resource servers, versioned in `contracts/api/token-claims.md`):**
  `iss`, `sub` (account UUID), `aud`, `exp/iat/nbf`, `jti`, `scope`, `roles` (flattened, template-expanded), `client_id`, `amr`, `acr`, `email_verified`. **No PII beyond that** — no email, no names in access tokens (they live in `id_token`/`userinfo`).
- **Key management (D-011):** signing keypair stored in AWS Secrets Manager, injected via External Secrets; JWKS always publishes **current + previous** keys with `kid`. Rotation: generate → publish in JWKS → wait ≥ max token TTL → switch signer → retire old key after overlap window (automated, quarterly + on-demand). KMS-backed signing recorded as a future hardening ADR trigger, not Phase 1 (per-token KMS latency/cost, custom Nimbus integration).

---

## 7. Refresh Token Strategy (D-003)

- Opaque 256-bit random values; **only SHA-256 hashes at rest** (SAS authorization persistence customized).
- **TTL:** 30 days absolute, 14 days sliding inactivity.
- **Rotation with families:** every refresh issues a new token in the same `family_id`. Presenting a *superseded* token = theft signal → revoke entire family, audit `token.reuse_detected`, emit event.
- **Multi-session:** one family per device/login; sessions listable and individually revocable (API now, user-facing UI later). Logout revokes the family; "logout everywhere" revokes all families + SAS sessions.
- Nightly cleanup job (ShedLock-guarded — multi-replica EKS, unlike the reference's single-instance assumption).

## 8. Authorization Strategy

- **Roles** (user-level): `USER`, `MERCHANT`, `ADMIN`, `COMPLIANCE` + template bundles. Embedded in tokens as `roles`; resource servers map them to their own local rules — auth distributes authorities, it does not evaluate sibling services' policies.
- **Scopes** (client-level): what an *application* may do (`merchant.api`, `internal.payments:write`). Roles ∧ scopes must both allow an action.
- Method security (`@PreAuthorize`) on auth's own management/admin APIs; every admin action audited with actor + target.

## 9. User & Client & API-Key Management

- **Registration:** email + password → `PENDING_VERIFICATION` → `auth.email.requested(purpose=verify)` event → Notification sends link → confirmation activates account → `auth.user.registered` published (outbox, same tx as the state change).
- **Password reset:** uniform-response request endpoint → hashed single-use token → reset revokes all refresh-token families + sessions.
- **Suspension (admin/compliance):** immediate — SAS sessions killed, families revoked, `auth.user.suspended` published; access tokens die within ≤10 min (TTL).
- **API keys (merchant):** created by an MFA-authenticated merchant; plaintext shown once. Consumers never hit resource servers with raw keys — they call **`POST /api-keys/token`** (key in header) and receive a standard short-lived JWT (`sub` = merchant account, `scope=merchant.api`, `amr:["api_key"]`). Resource servers therefore validate exactly one credential type: our JWTs. Key lookup by prefix, constant-time hash compare, `lastUsedAt` tracking, revocation immediate.

## 10. API Design

Contract-first: `contracts/api/auth.yaml` (OpenAPI) is authored with the implementation and is the
source for generated models + the TS client. Errors: RFC 9457 `application/problem+json` with a
stable `type` catalogue; validation errors list field violations; **no stack traces, no enumeration
hints**. Standard SAS endpoints excluded from the spec by reference (they follow the RFCs).

Surface (prefix `/api/v1` for non-protocol endpoints):

| Group | Endpoints (summary) |
|---|---|
| Protocol (SAS) | `/oauth2/{authorize,token,revoke,jwks}`, `/.well-known/openid-configuration`, `/userinfo`, `/login` (first-party page) |
| Registration | `POST /accounts` · `POST /accounts/verify-email` · `POST /accounts/resend-verification` |
| Credentials | `POST /accounts/password-reset-request` · `POST /accounts/password-reset` · `POST /accounts/me/password` |
| MFA | `POST /accounts/me/mfa/totp` (begin) · `POST /accounts/me/mfa/totp/confirm` · `DELETE /accounts/me/mfa/totp` (password + code required) · `POST /accounts/me/mfa/recovery-codes` |
| Sessions | `GET /accounts/me/sessions` · `DELETE /accounts/me/sessions/{familyId}` · `DELETE /accounts/me/sessions` |
| API keys | `POST /api-keys` · `GET /api-keys` · `DELETE /api-keys/{id}` · `POST /api-keys/token` (exchange) |
| Roles (admin) | `GET/POST /roles`, `/role-templates`, assignment endpoints — **authenticated + `ADMIN`**, no exceptions (reference's whitelist violation is a named CI check) |
| Admin | `POST /admin/accounts/{id}/{unlock,suspend,reinstate}` · `GET /admin/audit` (paged, filtered) |

## 11. Database Schema (Flyway, DDL-only, `auth` schema)

`accounts`, `mfa_enrollments`, `recovery_codes`, `roles`, `role_templates`,
`role_template_roles`, `account_roles`, `account_role_templates`, `api_keys`,
`verification_tokens`, `lockout_state`, `auth_audit` (append-only; no UPDATE/DELETE grants),
`oauth2_registered_client`, `oauth2_authorization` (+ `family_id`, `device_label`, hashed values),
`outbox`. Conventions: bigint identity PKs internal, UUID external identifiers, `citext` email,
`created_at/updated_at` triggers, indexes on every FK + `api_keys.prefix` + `auth_audit(account_id, occurred_at)`.

## 12. Event Contracts & Kafka Topics

Schemas in `contracts/events/auth/` (JSON Schema, versioned, backward-compatible only). Envelope:
`event_id (UUID)`, `event_type`, `schema_version`, `occurred_at`, `partition key = account UUID`.
Published exclusively through the outbox in the same transaction as the state change.

| Topic | Events | Consumers |
|---|---|---|
| `auth.user.lifecycle` | `registered`, `suspended`, `reinstated`, `deleted` | Payment, Notification, (Intelligence later) |
| `auth.email.requested` | `verify_email`, `password_reset`, `security_alert` | Notification |
| `auth.security.audit` | mirror of audit records (`login_failed`, `account_locked`, `token_reuse_detected`, `mfa_disabled`, `api_key_created`, …) | Notification (user alerts), Phase 2+ Intelligence |

## 13–15. Errors, Observability, Audit

- **Errors:** RFC 9457 catalogue in `common`; 4xx never reveal whether an account/key exists; 5xx are opaque with `trace_id` correlation.
- **Observability:** OTel auto-instrumentation (traces → collector), Micrometer metrics, structured JSON logs (logstash encoder) with `trace_id`, **no tokens/secrets/emails in logs** (assertion-tested). Security metrics: `auth_login_failures_total`, `auth_lockouts_total`, `auth_mfa_failures_total`, `auth_token_issued_total{grant}`, `auth_token_reuse_detected_total`, `auth_jwks_rotation_age_days`. Paged: reuse-detected spike, lockout spike, JWKS age > policy window.
- **Audit (D: dispute-grade):** every auth-relevant action appended with actor, target, outcome, IP, user-agent hash, `trace_id`; 7-year retention (matches platform policy); DB role for the service has INSERT-only on `auth_audit`; mirrored to Kafka for downstream analysis.

## 16. Configuration, Deployment, K8s, Secrets

- **Config:** validated `@ConfigurationProperties` records (startup fails on missing/invalid — the reference's silent-default bug class is structurally impossible); profiles `local`, `dev`, `staging`, `prod`; local dev via Docker Compose (Postgres + Kafka), no shared dev DB.
- **Container:** multi-stage build → distroless JRE 21, non-root, read-only rootfs.
- **K8s (EKS, via the CDK service stack):** Deployment ≥ 2 replicas, HPA (CPU + p95 latency), PDB, liveness/readiness/startup probes (readiness gates on DB + Kafka + JWKS material loaded), NetworkPolicy (ingress: nginx + siblings; egress: RDS, MSK, Secrets Manager endpoints, HIBP), IRSA role scoped to its secrets only.
- **Secrets (D-010):** External Secrets Operator syncs from Secrets Manager: DB credentials, JWT signing keys, TOTP-seed KEK reference. No AWS SDK in application code; no secret in any properties file; gitleaks gate in CI.

## 17. Testing Strategy

1. **Unit** — password policy, lockout state machine, family/reuse logic, claims customizer, key rotation selection: plain JUnit, no Spring context.
2. **Integration (Testcontainers: Postgres + Kafka)** — full flows: register→verify→login(PKCE)→refresh→rotate→reuse-detect→revoke; MFA enroll/enforce; API-key exchange; outbox delivery.
3. **Security regression** — one test per Phase 2 reference defect (gap-analysis §3): endpoint-authentication sweep (no unlisted `permitAll`), hashed-at-rest assertions, enumeration-uniformity checks, config-binding failure tests.
4. **Authorization** — role/scope matrix tests per endpoint group.
5. **Contract** — responses validated against `contracts/api/auth.yaml`; event payloads against `contracts/events/auth/*`.
6. **Architecture** — ArchUnit: module dependency rules, no cross-module entities, no `java.util.Date`, controllers depend on module services only.

CI order: unit → arch/contract → integration (Testcontainers) → image build → gitleaks/dependency scan.

---

## 18. Design Invariants (review checklist)

1. Resource servers and the SPA consume **only standard OIDC artifacts** — nothing Themistra-custom to validate.
2. Every credential at rest is hashed or envelope-encrypted; every credential in transit appears exactly once (creation response).
3. Every state change that other services care about is an outbox event in the same transaction.
4. Every security-relevant action is audited append-only, and the audit is itself exported.
5. Any config misread fails startup, not silently.
6. No endpoint is public unless it appears in the CI-enforced public list.

*Next deliverable (Step 5): implementation roadmap — module build order, spikes (SAS MFA step,
authorization-persistence customization), and per-module acceptance criteria.*
