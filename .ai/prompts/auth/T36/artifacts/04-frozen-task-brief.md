<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T36 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

## Decision packet

All 11 Phase 3 (Kimi) findings were verified against actual source before disposition (per this
session's standing practice). All 11 checked out accurate — a first this session; none rejected.

| # | Finding | Disposition |
|---|---|---|
| 1 | TOTP enrollment has no HTTP surface | **Human-gate decision (this phase)**: direct `MfaService.beginEnroll`/`.confirm()` calls, matching `SasLoginIntegrationTest`'s precedent. Confirmed by re-reading `mfa/` package listing (no Controller) and `auth.yaml` (no enrollment path). |
| 2 | JWT-acquisition path for post-login HTTP calls unspecified | Accepted. Verified `attemptFullAuthorizeFlow`/`exchangeCodeForToken` exist in `SasLoginIntegrationTest` (lines 613, 664) as the only real-HTTP path to a token. Adopted verbatim. |
| 3 | Admin bootstrap for the MERCHANT role grant unspecified | Accepted. Verified `AdminAccountRoleController.assignRole` requires `@PreAuthorize("hasRole('ADMIN')")` and `ApiKeyTokenIssuer.issue()` resolves `roles` dynamically via `RoleService.resolveEffectiveRoles` at mint-time (not cached) — so an account granted `ADMIN` via direct `RoleService.assignRole` and then minted a token via `ApiKeyTokenIssuer.issue()` legitimately satisfies the `@PreAuthorize` check. Adopted. |
| 4 | AC2 assertion layer ambiguous | Accepted. Verified two distinct precedent tests exist at two layers (`merchantWithoutMfaEnrollmentCannotLogIn` at `/login`; `merchantWithoutEnrollmentCannotFinishAuthorizeFlow` at `/oauth2/authorize`). Adopted the stronger `/oauth2/authorize`-layer assertion (no `code` returned). |
| 5 | R4's event half is scoped but untested | Accepted. Verified `AccountPersistenceIntegrationTest` already uses a real `KafkaConsumer` against Testcontainers Kafka to assert an outbox-relayed event — a directly reusable, already-precedented mechanism, not a novel one. Since the task statement itself provisions a Kafka container, an event assertion is now in scope rather than dropping R4's event half silently. |
| 6 | Contract-assertion mechanism undefined | Accepted. Specified as field-existence/value-pattern checks against response JsonNode/JWT claims, matching `ApiKeyLifecycleIntegrationTest`'s existing style — no OpenAPI schema validator introduced. |
| 7 | Named-test mismatch vs. header | Accepted (already independently flagged at Phase 1/2). Confirmed no valid named test exists in `package.md` §8 for this task; a new name will be chosen in Phase 5, not `shouldConformToAuthOpenApiContract` (T33's). |
| 8 | Header says "no LOCKED decisions" but brief depends on 6 | Accepted. Confirmed by re-reading the Phase 2 prompt header text directly. Made explicit below rather than silently inherited. |
| 9 | Which JWT authenticates API-key/session calls | Accepted. Verified `AccountController`'s session endpoints require only `Authentication` (any valid JWT `sub`, no scope/role check) — so the exchanged API-key JWT, whose `sub` is the same merchant account that did the interactive login, legitimately manages that account's own sessions. Coherent, single-account flow throughout. |
| 10 | TOTP determinism strategy vague | Accepted. Verified `attemptLoginWithFreshTotpCode` generates the code via `referenceGenerateCode(secret, Instant.now())` at submission time against the server's real `Clock.systemUTC()`, relying on the verification window rather than controlling the server clock. Adopted verbatim. |
| 11 | AC7 "or equivalent" vague | Accepted. Specified as a follow-up empty session list plus a direct repository reload confirming `revokedAt`/`revokedReason`, matching `SessionIntegrationTest.shouldRevokeSingleSessionFamily`'s existing pattern. |

## Frozen brief (Phase 2 TIB, as amended)

### Task

Add one Testcontainers (Postgres + Kafka) integration test executing, in order and over real HTTP
wherever an endpoint exists: register → verify email → login (password) → admin assigns MERCHANT →
next login requires MFA enrollment (blocked) → enroll TOTP (direct `MfaService` call) → login with
TOTP → create API key → exchange key for JWT → call session list → revoke session.

### Purpose

Prove the full identity-issuance lifecycle actually composes end-to-end against real infrastructure.

### Scope

**In**: one new integration test class; real HTTP for every step with an HTTP surface; a direct
`MfaService.beginEnroll`/`.confirm()` call for the one step with no HTTP surface (Finding 1); a
Kafka-consumer assertion that `auth.user.registered` is relayed after email verification (Finding 5).

**Out**: any production code change (including no new MFA endpoint); bulk session revoke (R38);
token-reuse/theft detection (R39); cleanup-job behavior (R40); any other task in `tasks.md`.

### Business Rules

R1, R4 (incl. its event half — Finding 5), R24, R30, R31, R37 (widened, per Phase 1).

### Scoped LOCKED Decisions (Finding 8 — made explicit)

L6 (TOTP: RFC 6238, 30s/6-digit/HMAC-SHA1), L8 (API-key JWT contract), L9 (access-token claim set,
no email/name), L10 (MFA mandatory for MERCHANT/ADMIN, enforced at next interactive login), L11
(exhaustive public-endpoint list — no enrollment path on it, consistent with Finding 1's resolution),
L12 (module boundaries — test-only, no new cross-module dependency introduced).

### Dependencies

`AccountController`, `AdminAccountRoleController`, `ApiKeyController`, `MfaService`
(`beginEnroll`/`confirm`, direct call), `RoleService` (`assignRole`, direct call for admin bootstrap
per Finding 3), `ApiKeyTokenIssuer` (`issue`, for the admin-bootstrap JWT per Finding 3), the SAS
interactive login/authorize/token endpoints. Test-side precedent: `SasLoginIntegrationTest`
(`attemptFullAuthorizeFlow`, `exchangeCodeForToken`, `attemptLoginWithFreshTotpCode`,
`referenceGenerateCode`, `seedConfirmedTotpEnrollment`-style direct calls,
`merchantWithoutEnrollmentCannotFinishAuthorizeFlow`), `AccountPersistenceIntegrationTest`
(`KafkaConsumer` outbox-assertion pattern), `SessionIntegrationTest`
(`shouldRevokeSingleSessionFamily`'s list+repository-reload verification style).

### Inputs

One test-generated merchant email/password pair; one separately-bootstrapped admin identity
(registered, activated, granted `ADMIN` via direct `RoleService.assignRole`, authenticated via an
`ApiKeyTokenIssuer`-minted JWT — Finding 3); a reference TOTP code generator seeded from the
enrollment secret returned by `MfaService.beginEnroll` (Finding 1/10).

### Outputs

A passing integration test asserting each step's real HTTP response (status, body shape) and decoded
JWT claims, plus one Kafka-consumer assertion for `auth.user.registered` (Finding 5).

### State Changes

None to production code or schema. Test-only: creates/mutates rows across `account`, MFA enrollment,
role assignment, API-key, and refresh-token-family tables in its own Testcontainers Postgres
instance; publishes and consumes from Kafka via the outbox/relay.

### Files to Create

- One new integration test class under `services/auth/src/test/java/com/themistra/auth/` — exact
  package/name decided in Phase 5 (candidate per Finding 7: `shouldCompleteFullIdentityLifecycle`,
  final name not presumed here).

### Files to Modify

None.

### Files NOT to Modify

All production source; all `spec/` files; all sibling `*IntegrationTest.java` files (read as pattern
reference only); `contracts/**` (read-only).

### Acceptance Criteria

- **AC1** (R1, R4) — registration + verification transitions `PENDING_VERIFICATION` → `ACTIVE` via
  HTTP, and the outbox relays `auth.user.registered` to Kafka (verified via a real `KafkaConsumer`,
  Finding 5).
- **AC2** (R24, L10) — freshly `MERCHANT`-assigned account, at the `/oauth2/authorize` layer, gets no
  authorization `code` pre-enrollment (Finding 4).
- **AC3** (R24, L10, L6) — same account, post-enrollment, completes the full authorize flow with a
  valid TOTP code generated via `referenceGenerateCode(secret, Instant.now())` at submission time
  (Finding 10).
- **AC4** (R30) — `POST /api-keys` returns a `ck_live_`-prefixed plaintext key exactly once.
- **AC5** (R31, L8, L9) — `POST /api-keys/token` returns a JWT matching the API-key claim contract.
- **AC6** (R36) — `GET /accounts/me/sessions`, authenticated with the exchanged JWT (Finding 9),
  returns the family created by the earlier interactive login.
- **AC7** (R37) — `DELETE /accounts/me/sessions/{familyId}` revokes the family, verified by a
  follow-up empty list **and** a repository reload confirming `revokedAt`/`revokedReason` are set
  (Finding 11).

### Required Tests

One composed end-to-end integration test method covering AC1–AC7 in flow order, plus the AC2
negative assertion as part of the same flow (not a separate test method).

### Constraints

- **Transport**: real HTTP for every step with an HTTP surface; direct `MfaService` calls only for
  enrollment (Finding 1); direct `RoleService`/`ApiKeyTokenIssuer` calls only for the admin-bootstrap
  identity (Finding 3) — the admin's own role grant is bootstrap plumbing, not a flow step under test.
- **Security**: no plaintext secret logged or asserted via string containment beyond what the flow
  requires.
- **Determinism**: TOTP codes generated at submission time against the real server clock, relying on
  the verification tolerance window (Finding 10) — no server-clock control introduced.
- **Contract assertions**: field-existence/value-pattern checks against JsonNode/JWT claims, matching
  existing precedent style — no OpenAPI schema validator introduced (Finding 6).
- **Module boundaries**: test-only change; L12 not implicated.

### Open Questions

No blockers. Finding 1 (the sole blocker) is resolved above via human-gate decision.

---

**Phase 4 complete — task brief frozen and approved.** Proceed to Phase 5 (Implementation Plan).
