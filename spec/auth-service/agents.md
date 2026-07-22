# agents.md — Auth Service

Standing, durable rules for `services/auth`. This file is authoritative for this service. A feature
spec never restates these; it references this file and records only what is specific to the feature.
If a spec must override a rule here, it says so explicitly in its §4a (LOCKED). The numbered decision
log `services/auth/docs/architecture/auth-decisions.md` (D-001…D-014) is the long-form rationale behind
these rules; `target-design.md` is the design of record.

## Platform rules (identical across all Themistra services)

**Language & build**
- Java 21, Spring Boot 3.5.4 (inherited from the root `/pom.xml` parent). No other JVM language.
- Maven multi-module (ADR-0002). Build: `mvn -pl services/auth verify`. The module is registered in the
  root `<modules>`. Convention sharing happens through the parent POM.

**Configuration**
- Flat `application.properties` only — never YAML.
- Config is bound to validated `@ConfigurationProperties` records; startup FAILS on missing/invalid
  values in non-local profiles (the reference project's silent-default bug class is structurally
  impossible). Profiles: `local`, `dev`, `staging`, `prod`. Local dev runs against Docker Compose
  (Postgres + Kafka); no shared dev database.

**Persistence & schema**
- PostgreSQL only, one logical schema per service (this service owns `auth`). No cross-schema queries.
- Flyway, DDL-only migrations. **V1–V4 are immutable**; new work is a new `V<n>__...` file.
- JPA for simple find/save; a stored proc / native query only for complex or reporting reads.
- Internal PKs are `bigint identity`; the external identifier is the account UUID (the JWT `sub`) — internal
  PKs never leak into tokens or APIs. `citext` for email. No `java.util.Date`; use `java.time` with a `Clock`.

**Package layout & boundaries**
- Package-by-feature under `com.themistra.auth` (this service is the template for the fleet). Each module
  owns its entities, repositories, services, and API. ArchUnit enforces `api → application → domain`
  within a module and forbids cross-module entity imports. Shared plumbing lives only in `common`.

**Events & messaging**
- Kafka (AWS MSK). Every state change other services care about is published through the **outbox** in the
  same transaction as the DB write. Topic naming `<domain>.<entity>.<event>`; schemas in
  `contracts/events/`, versioned, backward-compatible only; models generated from `contracts/`. Consumers
  are idempotent and dedupe on the event key. Services depend only on `libs/` and `contracts/`.

**Security**
- Zero trust: every non-public endpoint validates a JWT (this service is also a resource server for its
  own management APIs). The public-endpoint set is exhaustive and CI-enforced (`PublicEndpoints`).
- Errors are RFC 9457 `application/problem+json` — no stack traces, no internal detail, no enumeration hints.
- Secrets (JWT signing keys, DB creds, TOTP-seed KEK reference): injected by External Secrets Operator;
  none committed; gitleaks gate in CI; no AWS SDK code in the service, **except** a single scoped
  KMS `GenerateDataKey`/`Decrypt` call inside `mfa.MfaSeedEncryption` for TOTP-seed envelope encryption
  (D-010 exception, see `docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md`, L14, D-025).

**Observability**
- OpenTelemetry traces, Micrometer→Prometheus metrics, structured JSON logs with `trace_id`. Never log
  tokens, secrets, or emails. Security metrics (login failures, lockouts, MFA failures, token reuse, JWKS
  age) are exported; reuse-detected and lockout spikes are paged.

**Testing**
- Unit (plain JUnit, fixed `Clock`, no Spring context) → ArchUnit + contract → integration
  (Testcontainers: Postgres + Kafka) → image build → gitleaks/dependency scan. One security-regression
  test per known reference defect. Contract tests validate against `contracts/`.

**Deployment**
- Multi-stage Docker → distroless JRE 21, non-root, read-only rootfs. EKS ≥ 2 replicas; multi-replica
  scheduled jobs are ShedLock-guarded. Infra is AWS CDK (TypeScript).

**Process**
- Trunk-based; `main` always deployable. Material design changes require an ADR in `docs/adr/`.
- Non-custodial: web2 security posture is sufficient here (team decision); this service holds no funds
  and no user private keys.

## Service-specific standing rules (durable, cross-feature)

- **This service is the platform's identity issuer** — OIDC/OAuth2 via Spring Authorization Server. It
  distributes authorities; it never evaluates sibling services' policies. Resource servers and the SPA
  consume **only standard OIDC artifacts** — nothing Themistra-custom to validate.
- **JWTs:** RS256, 3072-bit keys; access-token TTL 10 minutes. Access-token claims are exactly the set in
  `contracts/api/token-claims.md` — **no PII beyond `email_verified`** (no email/name in access tokens;
  those live in `id_token`/`userinfo`). JWKS always publishes current + previous keys with `kid`.
- **Refresh tokens:** opaque, only SHA-256 hashes at rest; rotation with families; presenting a superseded
  token revokes the whole family (theft signal) and is audited.
- **Credentials at rest** are hashed or envelope-encrypted; any credential in transit appears exactly once
  (the creation response). Passwords: NIST 800-63B (min 12 / max 128, no composition rules, no forced
  rotation, breached-password screening fail-open with audit).
- **MFA (TOTP)** is mandatory for accounts holding `MERCHANT` or `ADMIN`; enforced inside the interactive
  auth flow before the authorization code is issued.
- **Enumeration-safe everywhere:** registration, verification, password-reset request/confirm, and login
  return uniform responses that never reveal whether an account/email/token exists or its state.
- **`auth_audit` is append-only** (the DB role has INSERT only) and mirrored to Kafka; every
  security-relevant action is recorded with actor, target, outcome, and correlation id.

## Reusable procedures — reference, don't inline

Load the relevant Skill rather than restating: `idempotency`, `stored-proc-dao`, `code-review`. Feature
specs are authored with the `spec-authoring` skill.
