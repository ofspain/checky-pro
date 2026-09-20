<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T36 · Phase 2 — Task Implementation Brief

## Task

Add one Testcontainers (Postgres + Kafka) integration test executing, in order and over real HTTP
wherever an endpoint exists: register → verify email → login (password) → admin assigns MERCHANT →
next login requires MFA enrollment → enroll TOTP → login with TOTP → create API key → exchange key
for JWT → call session list → revoke session.

## Purpose

Prove the full identity-issuance lifecycle actually composes end-to-end against real infrastructure,
not just per-feature in isolation — the one scenario no existing test file exercises as a single
continuous flow.

## Scope

**In**: one new integration test class covering the flow above, real HTTP for every step that has
an HTTP surface, real assertions against `contracts/api/auth.yaml` / `token-claims.md` shapes at the
create-API-key, exchange, session-list, and session-revoke steps.

**Out**: any production code change; any new MFA-enrollment HTTP endpoint; bulk session revoke
(`R38`); token-reuse/theft-detection scenarios (`R39`); cleanup-job behavior (`R40`); any other task
in `tasks.md`.

## Business Rules

- R1 — `POST /accounts` (valid email+password) → `PENDING_VERIFICATION`, `202`.
- R4 — valid verification token → `ACTIVE`, emits `auth.user.registered`.
- R24 — `MERCHANT`/`ADMIN` with no confirmed TOTP enrollment is blocked from an authorization code.
- R30 — `POST /api-keys` (MERCHANT, confirmed MFA) → `ck_live_` key, plaintext once, hash stored.
- R31 — `POST /api-keys/token` (valid key) → 10-min JWT, `sub`=merchant UUID, `scope`⊇`merchant.api`, `amr`⊇`api_key`.
- R36 — `GET /accounts/me/sessions` → active families (device label, created, last-rotated).
- R37 — `DELETE /accounts/me/sessions/{familyId}` → revokes family + live SAS authorization.

## Locked Decisions

- L6 — TOTP: RFC 6238, 30s step, 6 digits, HMAC-SHA1 (governs the reference code generator used).
- L8 — API-key JWT contract (`sub`, `scope`, `amr`, 10-min TTL).
- L9 — exact access-token claim set (no email/name) — bounds what the test may assert present/absent.
- L10 — MFA mandatory for `MERCHANT`/`ADMIN`, enforced at next interactive login after role grant.
- L11 — public-endpoint list is exhaustive; no MFA/enrollment path is on it.
- L12 — module boundaries; this task adds a test file only, no new cross-module dependency.

## Dependencies

`AccountController`, `AdminAccountRoleController`, `ApiKeyController`, `MfaService`
(`beginEnroll`/`confirm`), the SAS interactive login filter chain, `RoleService`/role assignment,
session/family service backing `AccountController`'s session endpoints. Test-side patterns:
`SasLoginIntegrationTest` (registration/login/enrollment helpers), `RoleAssignmentIntegrationTest`
(HTTP admin role grant), `ApiKeyLifecycleIntegrationTest` (HTTP create/exchange), `SessionIntegrationTest`
(HTTP list/revoke).

## Inputs

One test-generated email/password pair; an ADMIN-role test identity to perform the role grant; a
reference TOTP code generator seeded from the enrollment secret (per L6).

## Outputs

A passing integration test asserting each step's real HTTP response (status, body shape per
`auth.yaml`) and, where applicable, decoded JWT claims per `token-claims.md`.

## State Changes

None to production code or schema. Test-only: creates and mutates rows across `account`, MFA
enrollment, role assignment, API-key, and refresh-token-family tables inside its own Testcontainers
Postgres instance; publishes to Kafka topics via the outbox (verified only insofar as R4's event is
in scope — no consumer-side assertion required).

## Files to Create

- One new integration test class under `services/auth/src/test/java/com/themistra/auth/` (package
  and exact name decided in Phase 5 — Implementation Plan; no candidate name presumed here since
  package.md has no valid named test for this task, per Phase 1).

## Files to Modify

None expected.

## Files NOT to Modify

Every production source file; every file under `spec/`; every sibling `*IntegrationTest.java` (read
as pattern reference only, not edited); `contracts/**` (read-only, assertion source of truth).

## Acceptance Criteria

- AC1 (R1, R4) — registration + verification transitions `PENDING_VERIFICATION` → `ACTIVE` via HTTP.
- AC2 (R24, L10) — freshly `MERCHANT`-assigned account is blocked (no auth code) at next login, pre-enrollment.
- AC3 (R24, L10, L6) — same account, post-enrollment, completes login with a valid TOTP code.
- AC4 (R30) — `POST /api-keys` returns a `ck_live_`-prefixed plaintext key exactly once.
- AC5 (R31, L8, L9) — `POST /api-keys/token` returns a JWT matching the API-key claim contract.
- AC6 (R36) — `GET /accounts/me/sessions` returns the caller's active families with required fields.
- AC7 (R37) — `DELETE /accounts/me/sessions/{familyId}` revokes the family (verified by a follow-up list or equivalent).

## Required Tests

One composed end-to-end integration test method covering AC1–AC7 in flow order (single-scenario
style, matching `ApiKeyLifecycleIntegrationTest`'s precedent), plus the AC2 negative assertion
(blocked login, not merely a skipped step) as part of the same flow, not a separate test.

## Constraints

- **Transport**: real HTTP for every step with an HTTP surface (register, verify, login, admin role
  grant, TOTP login, create key, exchange, session list, session revoke). TOTP *enrollment* has no
  HTTP surface in this codebase — see Open Questions.
- **Security**: no plaintext secret (password, API key, TOTP seed) logged or asserted via string
  containment beyond what the flow itself requires to proceed.
- **Transaction/module boundaries**: test-only change; L12 not implicated.
- **Determinism**: TOTP code generation must use a fixed/controllable time source consistent with
  L6's 30s step, not a live wall-clock race.
- **Null handling**: n/a — no new production code.

## Open Questions

**Blocker.** No LOCKED decision or requirement specifies an HTTP transport for TOTP enrollment, and
none exists in the codebase — `services/auth/.../mfa/` has no `Controller` class, and
`contracts/api/auth.yaml` documents no enrollment path. Two ways to satisfy the task statement's
"enroll TOTP" step:
(a) direct `mfaService.beginEnroll`/`.confirm()` calls, matching `SasLoginIntegrationTest`'s
already-accepted precedent (`seedConfirmedTotpEnrollment`), keeping this task test-only; or
(b) treat the missing endpoint as a blocker requiring new production code (a scope expansion with no
precedent among T31–T35, all of which shipped test-only or near-test-only changes for this spec
section).
Requires a human-gate decision at Phase 4 before implementation planning can fix the test's shape.

---

**Phase 2 complete — implementation brief written.** Proceed to Phase 3 (Design Challenge) on
approval.
