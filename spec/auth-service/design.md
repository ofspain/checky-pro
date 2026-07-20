# 4. Design — how to build it

## 4a. LOCKED decisions — implement exactly, do NOT deviate

- L1. **Immutability of existing migrations.** The V1–V4 Flyway migrations in `services/auth/src/main/resources/db/migration/` are immutable. New schema work, if any, is delivered only as a follow-up migration named `V5__...`.
- L2. **Password policy (NIST 800-63B).** Minimum 12 characters, maximum 128 characters, no composition rules, no forced periodic rotation. Breached-password screening uses the Have I Been Pwned k-anonymity range API with a 5-character uppercase SHA-1 hash prefix. If the range API is down, the change is allowed but audited.
- L3. **BCrypt settings.** Stored password hashes use the delegating encoder with `{bcrypt}` at strength 12, already configured in `SecurityBeansConfig`.
- L4. **Brute-force lockout.** 5 failed attempts within a rolling 30-minute window transition an `ACTIVE` account to `LOCKED` for 15 minutes. Each subsequent lock doubles the effective duration via `lock_count` until it is reset. Counter decays 30 minutes after the last failure.
- L5. **Enumeration-safe responses.** Login, registration, password-reset request, password-reset confirmation, and email verification endpoints return uniform responses that do not reveal whether an email exists, whether an account is locked/suspended/deleted, or whether a token is invalid.
- L6. **TOTP algorithm.** RFC 6238, time step 30 seconds, 6 digits, HMAC-SHA1. Recovery codes are random single-use values; only SHA-256 hashes are stored.
- L7. **API key format.** Public prefix is `ck_live_` followed by a random 24-character alphanumeric suffix (lookup handle). The full key is `ck_live_<suffix>.<secret>` with a 32-character secret. Only SHA-256 is stored; plaintext is returned exactly once.
- L8. **API-key JWT contract.** Exchange endpoint `POST /api-keys/token` issues a 10-minute RS256 JWT with `sub` = merchant account UUID, `scope` containing `merchant.api`, `amr` containing `api_key`, and the standard `roles` / `client_id` claims via the existing `TokenClaimsCustomizer` path.
- L9. **Token claims contract.** Access-token claims are exactly: `iss`, `sub`, `aud`, `exp`, `iat`, `nbf`, `jti`, `scope`, `roles`, `client_id`, `amr`, `acr`, `email_verified`. No email address or name is included in access tokens. `id_token` and `/userinfo` carry email/name separately via SAS defaults.
- L10. **MFA enforcement role rule.** MFA is mandatory for accounts that hold `MERCHANT` or `ADMIN`. It is optional for `USER` and `COMPLIANCE`. Enrollment is enforced at the next interactive login after a mandatory role is granted.
- L11. **Public endpoint discipline.** The only unauthenticated API paths are: actuator health/info/prometheus, `POST /accounts`, SAS protocol endpoints (`/oauth2/**`, `/.well-known/**`, `/userinfo`, `/login`), and the API-key exchange `POST /api-keys/token`. Any new public path must be added to `PublicEndpoints.java`.
- L12. **Module boundaries.** No feature module may import an entity class from another feature module. Shared plumbing lives in `common`. This is enforced by `ArchitectureTest`.
- L13. **Secrets discipline.** No secret, credential, or signing key material is committed to the repo. External Secrets Operator injects values; hardcoded defaults exist only for local development and are refused in non-local profiles by validated `@ConfigurationProperties` or startup guards.

## 4b. OPEN decisions — implementer/Claude MAY propose

- O1. **TOTP seed encryption implementation.** `target-design.md` requires AES-GCM encryption of seeds with a KMS-enveloped data key, while `auth-decisions.md` D-010 rejects AWS SDK code in the service. Propose 2–3 options (e.g., local AES-GCM with a key injected by External Secrets; a minimal KMS client call for envelope encryption; a synchronous call to a Crypto Service attestation-style endpoint) with latency, security, and complexity trade-offs. **Do not implement until the author selects an option.**
- O2. **Per-account rate-limiting mechanics.** The preference is in-process per-replica buckets (Bucket4j or a simple concurrent-map implementation) backed by the durable lockout table for credential-guessing defense. Propose thresholds for login, `/oauth2/token`, password-reset confirm, and MFA verify; recommend values; proceed only if low-risk or after author approval.
- O3. **Session/device label source.** When a refresh-token family is created, the `device_label` can come from (a) a client-supplied label in the authorize request, (b) a hash of the `User-Agent`, or (c) a generic default. Propose a default and how it is surfaced in `GET /accounts/me/sessions`.
- O4. **Login page presentation.** The SAS first-party `/login` page can be the default Spring Security form or a minimal custom Thymeleaf template that supports password + TOTP/recovery-code fields. Propose one option; proceed if low-risk.
- O5. **Recovery-code hashing primitive.** Single-purpose SHA-256 is acceptable, but bcrypted recovery codes are also defensible if rotation is rare. Propose and justify; default to SHA-256 unless changed.

## 4c. VERBATIM artifacts — copy exactly, do not paraphrase

### New configuration keys (add to `application.properties`)

```properties
# --- Verification / reset tokens ---
themistra.auth.verification-token.ttl-minutes=30

# --- Password policy ---
themistra.auth.password.min-length=12
themistra.auth.password.max-length=128
themistra.auth.password.breach-check.enabled=true
themistra.auth.password.breach-check.url-prefix=https://api.pwnedpasswords.com/range/

# --- Lockout ---
themistra.auth.lockout.max-attempts=5
themistra.auth.lockout.window-minutes=30
themistra.auth.lockout.base-lock-minutes=15

# --- MFA ---
themistra.auth.mfa.issuer-name=Themistra
# The data-key or KEK reference for TOTP seed encryption (implementation depends on O1)
themistra.auth.mfa.seed-kek-arn=${MFA_SEED_KEK_ARN:}

# --- API keys ---
themistra.auth.api-key.prefix=ck_live_
themistra.auth.api-key.token-ttl-minutes=10

# --- Cleanup job (ShedLock) ---
themistra.auth.cleanup.cron=0 2 * * *
themistra.auth.cleanup.token-retention-days=7
themistra.auth.cleanup.family-retention-days=90
```

### New aggregate-topic mapping (update `EventTopics.java`)

```java
private static final Map<String, String> TOPIC_BY_AGGREGATE_TYPE = Map.of(
        "account", "auth.user.lifecycle",
        "audit", "auth.security.audit",
        "verification-token", "auth.email.requested",
        "api-key", "auth.email.requested"
);
```

The event payload aggregate type for password-reset and verify-email events SHOULD be `"verification-token"`; the event `type` field differentiates `verify_email` from `password_reset`.

### New Flyway migration `V5__lockout_cleanup_and_shedlock.sql`

```sql
-- Cleanup support for lockout rows and scheduled job coordination.
-- No changes to existing tables; this migration is additive only.

CREATE INDEX IF NOT EXISTS idx_lockout_state_locked_until
    ON lockout_state(locked_until)
    WHERE locked_until IS NOT NULL;

-- ShedLock for multi-replica scheduled cleanup (refresh-token family cleanup
-- referenced in target-design.md §7 must not run concurrently across pods).
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
```

### Relevant existing DDL (do not alter semantics)

The following columns/tables from V1–V4 already exist and must be used exactly as defined:

```sql
-- from V1
CREATE TABLE accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_uuid UUID NOT NULL UNIQUE,
    email CITEXT NOT NULL UNIQUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash VARCHAR(100),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE mfa_enrollments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    type VARCHAR(16) NOT NULL DEFAULT 'TOTP',
    secret_encrypted BYTEA NOT NULL,
    confirmed_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_mfa_account_type UNIQUE (account_id, type)
);

CREATE TABLE recovery_codes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    code_hash CHAR(64) NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE api_keys (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key_uuid UUID NOT NULL UNIQUE,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    prefix VARCHAR(16) NOT NULL,
    key_hash CHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    scopes TEXT[] NOT NULL DEFAULT '{}',
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_keys_prefix ON api_keys(prefix);
CREATE INDEX idx_api_keys_account ON api_keys(account_id);

CREATE TABLE verification_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_token_purpose CHECK (purpose IN ('EMAIL_VERIFY','PASSWORD_RESET'))
);
CREATE INDEX idx_verification_tokens_account ON verification_tokens(account_id);

CREATE TABLE lockout_state (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    failed_attempts INT NOT NULL DEFAULT 0,
    last_failed_at TIMESTAMPTZ,
    locked_until TIMESTAMPTZ,
    lock_count INT NOT NULL DEFAULT 0
);

-- from V2
CREATE TABLE refresh_token_family (
    family_id UUID PRIMARY KEY,
    authorization_id VARCHAR(100) NOT NULL UNIQUE,
    principal_name VARCHAR(200) NOT NULL,
    device_label VARCHAR(100),
    current_token_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    rotated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(64)
);
CREATE INDEX idx_refresh_token_family_principal ON refresh_token_family(principal_name);
CREATE INDEX idx_refresh_token_family_current_hash ON refresh_token_family(current_token_hash)
    WHERE revoked_at IS NULL;

CREATE TABLE refresh_token_archive (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES refresh_token_family(family_id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL,
    superseded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_archive_hash ON refresh_token_archive(token_hash);
```

### External-secret key reference (already in `deploy/k8s/security.yaml`)

```yaml
- secretKey: MFA_SEED_KEK_ARN
  remoteRef: { key: checky/${ENVIRONMENT}/auth-service, property: mfa_seed_kek_arn }
```

## 5. Data model & schema changes

No breaking schema changes are required beyond V5 above. The implementation should treat the following existing aggregates as authoritative:

- `Account` owns `status`, `emailVerified`, `passwordHash`.
- `MfaEnrollment` / `RecoveryCode` own the TOTP lifecycle; one confirmed `MfaEnrollment` per account at a time.
- `ApiKey` owns merchant credentials; `keyHash` is the SHA-256 of the full `ck_live_...` string.
- `VerificationToken` owns single-use, hashed, TTL'd tokens for email verification and password reset.
- `LockoutState` owns the per-account brute-force counter and lock expiry.
- `RefreshTokenFamily` / `RefreshTokenArchive` own reuse detection and session revocation.
- `OutboxEvent` owns all cross-service messaging.

**One new table:** `shedlock` (V5) for the cleanup job. **One new index:** `idx_lockout_state_locked_until` for efficient expired-lock scans.

Money is not in scope for this service. No floating-point types are introduced.

## 6. Package & file map

New and materially changed files under `services/auth/src/main/java/com/themistra/auth/`:

```
account/
├── VerificationToken.java                    (entity)
├── VerificationTokenRepository.java
├── VerificationTokenService.java             (issue, verify, reset flows)
├── PasswordPolicy.java                       (length + breach check rules)
├── PasswordPolicyProperties.java             (validated config)
├── PasswordEncoderFacade.java                (optional: breach-check + encode wrapper)
├── dto/
│   ├── VerifyEmailRequest.java
│   ├── PasswordResetRequest.java
│   ├── PasswordResetConfirmRequest.java
│   ├── ChangePasswordRequest.java
│   └── ResendVerificationRequest.java
├── event/
│   └── EmailRequestedEventPayload.java
├── AccountController.java                    (add verify/resend/reset/change endpoints)
├── AccountService.java                       (wire verification, password change, reset)
└── AccountExceptionHandler.java              (add verification/reset state errors)

authn/
├── LockoutProperties.java
├── LockoutStateMachine.java                  (pure logic, unit-testable)
├── LockoutService.java
├── BreachCheckClient.java                    (RestClient calling HIBP)
├── TotpAuthenticationProvider.java           (SAS MFA step, details depend on O1/O4)
├── TotpStepUpAuthenticationToken.java
├── LoginAttemptAuditService.java             (login success/failure auditing)
└── package-info.java                         (already exists; populate)

mfa/
├── MfaEnrollment.java                        (entity)
├── MfaEnrollmentRepository.java
├── MfaService.java                           (enroll/confirm/remove + recovery codes)
├── RecoveryCodeService.java
├── TotpGenerator.java                        (secret + URI generation)
├── TotpVerifier.java                         (code verification)
├── TotpSeedEncryption.java                   (depends on O1)
├── dto/
│   ├── BeginTotpEnrollmentResponse.java
│   ├── ConfirmTotpEnrollmentRequest.java
│   └── DisableMfaRequest.java
└── MfaController.java                        (POST/DELETE /accounts/me/mfa/totp)

apikey/
├── ApiKey.java                               (entity — already defined in schema)
├── ApiKeyRepository.java
├── ApiKeyService.java                        (CRUD + exchange)
├── ApiKeyTokenIssuer.java                    (JWT for key exchange)
├── ApiKeyAuthenticationFilter.java           (or controller endpoint)
├── ApiKeyHasher.java                         (constant-time compare)
├── dto/
│   ├── CreateApiKeyRequest.java
│   ├── ApiKeyResponse.java
│   └── ApiKeyTokenResponse.java
└── ApiKeyController.java

admin/
└── AdminUnlockController.java                (or extend AdminAccountController)

token/
├── TokenClaimsCustomizer.java                (extend amr/acr logic for MFA/API-key)
└── SecurityChainsConfig.java                 (update MFA authentication provider wiring)

events/
└── EventTopics.java                          (add verification-token mapping)

common/
├── PublicEndpoints.java                      (add /api-keys/token if public)
└── ApiExceptionHandler.java                  (new error mappings only)
```

Tests mirror the package layout under `src/test/java/com/themistra/auth/`.

Contract files (new):

```
contracts/api/auth.yaml
contracts/api/token-claims.md
contracts/events/auth/email-requested.v1.schema.json
contracts/events/auth/security-audit.v1.schema.json
```
