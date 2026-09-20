<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T36 · Phase 0 — Repository Understanding

Task statement: register → verify email → login (password) → admin assigns MERCHANT → next login
requires MFA enrollment → enroll TOTP → login with TOTP → create API key → exchange key for JWT →
call session list → revoke session, driven end-to-end via Testcontainers Postgres+Kafka.

---

## 1. Architecture summary

- Spring Boot 3.5.4 / Java 21, package-by-feature under `com.themistra.auth`: `account`, `authn`,
  `authz`, `audit`, `token`, `mfa`, `apikey`, `events`, `cleanup`, `ratelimit`, plus shared `common`.
- Spring Authorization Server (SAS) 1.5.1 is the OIDC/OAuth2 issuer for interactive login
  (authorization_code + refresh_token); the service is also a resource server for its own management
  APIs (session list/revoke, API-key CRUD, admin endpoints).
- PostgreSQL, one logical schema (`auth`), Flyway `V1`–`V5` (immutable up to the point each was
  shipped), JPA for simple find/save. Internal PKs are `bigint identity`; the account UUID (JWT `sub`)
  is the only externally visible identifier.
- Kafka outbox: state changes are published in the same transaction as the DB write; topic naming
  `<domain>.<entity>.<event>`, schemas versioned under `contracts/events/`. Two event contracts exist
  (`email-requested.v1`, `security-audit.v1`, both formalized in T33); `auth.user.registered` (R4) is
  a further event this task's flow will trigger but does not itself have a dedicated schema file in
  the T33 inventory — not a blocker, just noted.
- Security: zero-trust, exhaustive CI-enforced public-endpoint allowlist (`SecurityChainsConfig`,
  covered by `ArchitectureTest.shouldEnforcePublicEndpointAllowlist`, T32). Errors are RFC 9457
  `application/problem+json`. MFA (TOTP) is mandatory for `MERCHANT`/`ADMIN` roles, enforced inside
  the interactive login flow *before* an authorization code is issued (R24, `agents.md`).
- API keys: `POST /api-keys` creates a `ck_live_`-prefixed key, SHA-256-hashed at rest, plaintext
  returned once (R30); `POST /api-keys/token` exchanges a valid key for a 10-minute JWT with
  `scope` containing `merchant.api` and `amr` containing `api_key` (R31) — a third, non-SAS token
  issuance path (per T34's finding), distinct from both SAS grant types.
- Sessions: refresh-token families; `GET /accounts/me/sessions` lists active families (R36);
  `DELETE /accounts/me/sessions/{familyId}` revokes one family (R37, per `SessionIntegrationTest`'s
  own `@Test` comments — not in this task's scoped requirement IDs but is the endpoint the task's
  final step exercises).

## 2. Existing code this task touches

All controllers/services below already exist; this task's own deliverable is a *new* integration
test composing them. No production code is expected to change.

| Flow step | Existing endpoint / mechanism | Class | Requirement |
|---|---|---|---|
| register | `POST /accounts` | `AccountController` | R1 |
| verify email | `POST /accounts/verify-email` | `AccountController` | R4 |
| login (password) | SAS interactive login (form login → authorization_code) | SAS filter chain | — |
| admin assigns MERCHANT | `POST /admin/accounts/{accountUuid}/roles/{roleName}` | `AdminAccountRoleController` | — (authz) |
| MFA-enrollment gate | enforced inside the interactive login flow, pre-auth-code | SAS success/failure handling + `mfa` package | R24 |
| enroll TOTP | **no HTTP endpoint — see Known gaps below** | `MfaService.beginEnroll` / `.confirm` | R24 (indirectly) |
| login with TOTP | second-factor step of the same interactive flow | SAS filter chain + `mfa` package | R24 |
| create API key | `POST /api-keys` | `ApiKeyController` | R30 |
| exchange key for JWT | `POST /api-keys/token` | `ApiKeyController` | R31 |
| session list | `GET /accounts/me/sessions` | `AccountController` | R36 |
| revoke session | `DELETE /accounts/me/sessions/{familyId}` | `AccountController` | R37 |

Directly reusable precedent test files (all already reviewed/accepted in prior tasks):
- `SasLoginIntegrationTest` — registration/verification/login helpers (`registerAndActivateEmail`,
  `registerAndActivate`, `attemptLogin` (two overloads), `attemptFullAuthorizeFlow`,
  `exchangeCodeForToken`, `ensureRoleExists`), and critically `seedConfirmedTotpEnrollment` (see §5).
- `RoleAssignmentIntegrationTest` — real HTTP admin-driven role assignment via
  `AdminAccountRoleController`.
- `ApiKeyLifecycleIntegrationTest` — `shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle`,
  explicitly commented "the full lifecycle, one key, real HTTP throughout" — the target pattern for
  this task's API-key steps.
- `SessionIntegrationTest` — real HTTP session list/revoke against `AccountController`.

## 3. Established patterns to follow

- **Real HTTP throughout** is the stated norm for lifecycle-style integration tests in this codebase
  (`ApiKeyLifecycleIntegrationTest`'s own comment). T36 should follow this for every step that has an
  HTTP surface.
- **No shared Testcontainers base class exists** — each `*IntegrationTest` declares its own
  `@Testcontainers`/`@Container` Postgres+Kafka setup independently (confirmed: no
  `AbstractIntegrationTest` or similar found under `src/test/java/com/themistra/auth`). T36's new
  test file will need its own container declarations, matching sibling files' style rather than
  introducing a new shared base (out of scope for a verification task).
- **Fixed `Clock`** convention applies per `agents.md`'s testing section.
- Where a flow step has no HTTP surface (TOTP enrollment — see §5), the established, already-reviewed
  precedent is a direct Spring-managed service call, not a fabricated endpoint or a skipped step.

## 4. Testing conventions

Per `agents.md`: unit (plain JUnit, fixed `Clock`, no Spring context) → ArchUnit + contract →
integration (Testcontainers: Postgres + Kafka) → image build → security scans. T36 sits squarely in
the integration tier. `TestRestTemplate` (or equivalent) against a real `@SpringBootTest` context is
the pattern used by every sibling lifecycle test (`ApiKeyLifecycleIntegrationTest`,
`SessionIntegrationTest`, `RoleAssignmentIntegrationTest`, `SasLoginIntegrationTest`).

## 5. Known gaps / unknowns

- **No MFA-enrollment REST endpoint exists anywhere in this codebase.**
  `services/auth/src/main/java/com/themistra/auth/mfa/` contains `MfaService`, entities,
  repositories, and exceptions only — no `Controller` class. `contracts/api/auth.yaml` (T33's
  exhaustive 30-endpoint contract, itself verified complete by a reflection-based conformance test)
  has zero `mfa`/`totp`/`enroll` paths. The task statement's "enroll TOTP" step therefore cannot be
  executed as a real HTTP call today.
  Established precedent for this exact gap already exists and has already been reviewed/accepted in
  this codebase: `SasLoginIntegrationTest.seedConfirmedTotpEnrollment` calls
  `mfaService.beginEnroll(accountUuid)` then `mfaService.confirm(accountUuid, code)` directly as
  Spring-managed service calls (with a `referenceGenerateCode` helper standing in for a real
  authenticator app). I do not know whether Phase 1/4 should adopt this same direct-service-call
  pattern for T36's enrollment step or treat the missing endpoint as a blocker — that is a design
  decision, not a Phase 0 finding, and is flagged here rather than decided.
- **`package.md` §8's named-test table is confirmed systemically stale**, not a handful of isolated
  entries — every entry checked across T31–T36 is offset from `requirements.md`'s current numbering.
  T36's own Phase 0 header cites `shouldConformToAuthOpenApiContract` as the named test for this
  task; that name unambiguously belongs to T33's already-implemented, unrelated OpenAPI contract
  test. No entry in package.md's actual §8 list describes an end-to-end multi-step lifecycle
  scenario. I do not know the correct named test for T36 — package.md does not appear to have one,
  and none should be fabricated.
- I do not know the exact shape of the interactive login flow's TOTP second-factor submission (form
  field names, redirect targets) beyond what `SasLoginIntegrationTest`'s `attemptLogin` overloads
  imply — to be confirmed by reading that flow in full during Phase 1, not assumed here.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
