# Feature Spec: Auth Service — Phase 1 Completion

| Field | Value |
|---|---|
| Spec ID | `AUTH-PHASE1` |
| Version | `0.1` |
| Author (senior/owner) | `<name>` |
| Implementer | `TBD` |
| Status | `DRAFT` |
| Target repo / service | `services/auth` |
| Skills to load | `spec-authoring`, `code-review` |
| Standing rules | [`agents.md`](agents.md) in this directory is authoritative for `services/auth` (distilled from `ARCHITECTURE.md`, `docs/service-languages.pdf`, the ADRs, and `auth-decisions.md` D-001…D-014). This spec references it and does not restate or override it except where §4a says so explicitly. |

## 0. TL;DR

Complete the missing Phase 1 capabilities of the Auth service so it can safely issue tokens to real users and merchants: self-service email verification, password reset, NIST-aligned password policy, brute-force lockout, TOTP MFA, merchant API keys, refresh-token session management, per-account rate limits, and the missing event/audit contracts. The account, RBAC, audit, events, and SAS token infrastructure already exist and should be reused, not rewritten.

## 1. Context & why now

Themistra's trust layer depends on a correct identity issuer. `ARCHITECTURE.md` §3.2 and `SECURITY-THREAT-MODEL.md` #8 make MFA and account-takeover defense a day-one requirement for any account that can create invoices or administer the platform.

The Auth service scaffold already implements:

- Account aggregate, admin lifecycle, and outbox-based lifecycle events (`account` module).
- Role/role-template model and admin assignment APIs (`authz` module).
- Append-only audit writer mirrored to Kafka (`audit` module).
- Transactional outbox publisher/relay (`events` module).
- SAS wiring: registered-client seeding, JWKS with current/previous keys, refresh-token family/reuse tracking, and role-aware JWT claims (`token` module).

What is missing prevents production use: users cannot verify their email or reset their password, there is no brute-force defense, MFA is just a schema, API keys are an empty package, and the audit/event contracts for these flows do not exist. This spec closes those gaps.

## 2. Scope

**In scope**

- `account` module extensions: verification tokens, password reset, password policy, change-own-password.
- `authn` module: lockout state machine, failed-attempt tracking, admin unlock, MFA step integration into the SAS interactive flow.
- `mfa` module: TOTP enrollment/confirmation/removal, recovery codes.
- `apikey` module: merchant API key CRUD and key→JWT exchange.
- `admin` module: account unlock endpoint.
- `events` / `audit` integration for all new flows.
- Contract artifacts: `contracts/api/auth.yaml`, `contracts/api/token-claims.md`, and `contracts/events/auth/*.schema.json` for new events.
- Supporting code: configuration records, exception handlers, `PublicEndpoints` updates, ArchUnit/security tests.

**Explicitly out of scope**

- The reference project (`netra-identity-service`) — ideas only, no code.
- Social/federated login, WebAuthn, token exchange (RFC 8693), multi-tenancy claims.
- Dynamic OAuth2 client registration; clients remain statically provisioned.
- A backend-for-frontend or gateway service; the SPA continues to use PKCE against SAS.
- Sending actual email (auth emits events; Notification Service sends email).
- Payment Service, Crypto Service, Notification Service implementation.
- Extracting the outbox to `libs/java/outbox` — local implementation stays until a second service needs it.
- KMS-backed `JWKSource` — Secrets-Manager-injected PEM keys remain (D-011).
- Infrastructure/CDK changes beyond what the service manifest already expects.

## 3. Requirements — acceptance criteria (EARS)

See [`requirements.md`](requirements.md).

## 4. Design — how to build it

See [`design.md`](design.md).

## 5. Data model & schema changes

See [`design.md`](design.md#5-data-model--schema-changes).

## 6. Package & file map

See [`design.md`](design.md#6-package--file-map).

## 7. Tasks — ordered execution plan

See [`tasks.md`](tasks.md).

## 8. Test plan — named tests

The existing tests (`ArchitectureTest`, account/authz/audit/token integration tests) must keep passing. The following new or strengthened tests are required.

- `shouldReturnSameAcknowledgementForDuplicateAndNewRegistration` → R1 / R2
- `shouldEmitVerifyEmailEventOnRegistration` → R3
- `shouldActivateAccountWithValidVerificationToken` → R4
- `shouldNotRevealAccountExistenceForInvalidVerificationToken` → R5
- `shouldResendVerificationOnlyForPending accounts` → R6
- `shouldEmitPasswordResetEventOnlyWhenEmailExists` → R8
- `shouldResetPasswordAndRevokeAllFamiliesWithValidToken` → R9
- `shouldRejectPasswordShorterThan12OrLongerThan128` → R11
- `shouldRejectBreachedPasswordUsingHibpRange` → R12
- `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` → R13
- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` → R15
- `shouldResetLockoutCounterOnSuccessfulLogin` → R16
- `shouldUnlockAccountViaAdminEndpoint` → R17
- `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` → R18
- `shouldReturnTotpProvisioningUriOnEnrollmentBegin` → R19
- `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` → R20
- `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` → R21
- `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` → R22
- `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` → R23
- `shouldIssueTokenWithPwdAmrWhenMfaNotRequired` → R24
- `shouldRequirePasswordAndTotpToDisableMfa` → R25
- `shouldCreateApiKeyAndShowPlaintextExactlyOnce` → R27
- `shouldExchangeValidApiKeyForMerchantJwt` → R28
- `shouldRejectRevokedOrUnknownApiKeyWithUniform401` → R29
- `shouldListAndRevokeOwnApiKeys` → R30 / R31
- `shouldListActiveSessions` → R32
- `shouldRevokeSingleSessionFamily` → R33
- `shouldRevokeAllSessionFamilies` → R34
- `shouldReturn429WhenPerAccountRateLimitExceeded` → R35
- `shouldCleanupExpiredTokensAndFamilies` → R36
- `shouldAppendRowAndMirrorAuditEventForLoginFailure` → R37
- `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` → R38
- `shouldConformToAuthOpenApiContract` → R40 / R41
- `shouldEnforcePublicEndpointAllowlist` → L9
- `shouldPreventCrossModuleEntityImports` → L10

## 9. Verification checklist — implementer self-checks before raising PR

- [ ] All §3 acceptance criteria have a passing named test from §8.
- [ ] Every §4a LOCKED decision implemented as written (no silent deviation).
- [ ] Every §4c VERBATIM artifact copied exactly.
- [ ] No plaintext credentials, signing keys, or client secrets committed.
- [ ] `mvn -pl services/auth verify` passes (unit + integration + Testcontainers).
- [ ] New endpoints are either in `PublicEndpoints` or authenticated; the ArchUnit public-endpoint sweep passes.
- [ ] New outbox aggregate types are mapped in `EventTopics`.
- [ ] `contracts/api/auth.yaml` covers every new non-SAS endpoint and error response.
- [ ] The service boots with `JWT_REQUIRE_CONFIGURED=true` and `MFA_SEED_KEK_ARN` populated in a non-local profile.
- [ ] Password-policy, lockout, and MFA boundary tests exercise the failure catalogue from `reference-analysis.md` §4.

## 10. Migration, rollout & rollback

**Schema**

- The existing V1–V4 migrations already support the features in this spec. The only new DDL is V5 (cleanup index + ShedLock table) in [`design.md`](design.md#4c-verbatim-artifacts).
- Rollback: Flyway `repair`/`undo` is not enabled. If V5 must be backed out, deploy the previous code and run `DELETE FROM auth.shedlock; DROP INDEX IF EXISTS auth.idx_lockout_state_locked_until;` manually; there is no application dependency on the index at runtime.

**Code rollout**

- This is a purely additive code change. Deploy order: merge → build image → rolling update on EKS. Readiness gates on DB + Kafka + JWKS material ensure no pod serves traffic before it can validate tokens.
- Verify zero-downtime: old pods continue to validate tokens signed by the same JWKS; new pods add the missing endpoints. No breaking API contract changes to existing `/accounts`, `/admin/accounts`, `/admin/roles`, `/admin/audit` responses.

**Emergency rollback**

- Revert to the previous image. The V5 schema additions are backward compatible. Any in-flight verification tokens will still exist in the table but will not be read until a rollout containing this code resumes.

## 11. Open questions for the author

- Q1. ~~**TOTP seed encryption KMS approach.**~~ **Resolved (2026-07-22):** option (b) — a narrow KMS envelope-encryption call, confined to `MfaSeedEncryption`, as a named exception to D-010. See `design.md` L14, `auth-decisions.md` D-025, `docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md`.
- Q2. **Per-account rate-limit thresholds.** What are the permitted requests-per-minute limits for login attempts, `/oauth2/token`, password-reset confirmation, and MFA verify? Please confirm or replace the placeholders in `design.md` §4b-O2.
- Q3. **API key limits and scopes.** Should there be a maximum number of active API keys per merchant? Is the only scope at launch `merchant.api`, or are additional scopes needed?
- Q4. **Email link base URL.** Verification and password-reset links need a base URL + path. Should this come from `SPA_REDIRECT_URI` / `AUTH_ISSUER_URI`, or does the Notification Service need a new `AUTH_EMAIL_LINK_BASE_URL` secret?
- Q5. **Lockout event publication.** Is lock/unlock published only as an `auth.security.audit` mirror, or also as a lifecycle event on `auth.user.lifecycle`? The schema currently only has status enum values.
- Q6. **Agents / standing rules file.** ~~No repo `agents.md` exists.~~ **Resolved (2026-07-20):** a per-service `spec/auth-service/agents.md` now holds the durable rules, distilled from `ARCHITECTURE.md` / `service-languages.pdf` / `auth-decisions.md`; this spec references it instead of restating stack assumptions. Open follow-up: whether to also seed a single repo-root `agents.md` for the platform-common section shared by all four service files (dedupe), or keep them self-contained per service.
