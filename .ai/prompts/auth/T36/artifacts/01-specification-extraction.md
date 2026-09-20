<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T36 · Phase 1 — Specification Extraction

## Business Rules

- **R1** — `POST /accounts` with a valid email + policy-conformant password creates a `PENDING_VERIFICATION` account, returns `202 Accepted`.
- **R4** — a valid, unused, unexpired token to `POST /accounts/verify-email` transitions the account to `ACTIVE`, emits `auth.user.registered`.
- **R24** — a `MERCHANT`/`ADMIN` account with no confirmed TOTP enrollment is blocked from receiving an authorization code at interactive login until enrollment completes.
- **R30** — an authenticated `MERCHANT` with confirmed MFA calling `POST /api-keys` gets a `ck_live_`-prefixed key, plaintext returned once, SHA-256 hash stored, `api_key.created` audited.
- **R31** — a valid, non-expired, non-revoked API key to `POST /api-keys/token` yields a 10-minute JWT (`sub` = merchant UUID, `scope` ⊇ `merchant.api`, `amr` ⊇ `api_key`).
- **R36** — an authenticated user calling `GET /accounts/me/sessions` gets active refresh-token families (device label, created, last-rotated).
- **R37** *(widened — the task's final step)* — `DELETE /accounts/me/sessions/{familyId}` revokes that family and removes its live SAS authorization.

## Locked Decisions

- **L10** — MFA mandatory for `MERCHANT`/`ADMIN`, enrollment enforced at the *next* interactive login after the mandatory role is granted. Governs the "admin assigns MERCHANT → next login requires MFA enrollment" step directly; silent on the enrollment mechanism's transport.
- **L6** — TOTP is RFC 6238, 30s step, 6 digits, HMAC-SHA1 — governs how the test must generate a valid code for the "login with TOTP" step (a reference generator, not a real authenticator app).
- **L8** — API-key JWT contract (`sub`, `scope` ⊇ `merchant.api`, `amr` ⊇ `api_key`, 10-minute TTL) — governs the assertion on the "exchange key for JWT" step.
- **L9** — exact access-token claim set (no email/name) — governs what the test may assert is present/absent on any JWT it decodes, including the exchange-step token.
- **L11** — public-endpoint list is exhaustive and does not include any MFA/enrollment path — confirms an enrollment endpoint, if reached over HTTP, would need authentication; does not itself require the endpoint to exist.
- **L12** — module boundaries — this task adds only a test file, no cross-module production dependency is introduced.

No LOCKED decision specifies the transport (HTTP vs. direct call) for TOTP enrollment itself — see Open Questions.

## Files involved

**Existing, to read/reuse (no changes expected):**
- `AccountController` (`/accounts`, `/accounts/verify-email`, `/accounts/me/sessions*`)
- `AdminAccountRoleController` (`/admin/accounts/{accountUuid}/roles/{roleName}`)
- `ApiKeyController` (`/api-keys`, `/api-keys/token`)
- `MfaService` (`beginEnroll`, `confirm`) — no controller wraps it (Phase 0 finding)
- `SasLoginIntegrationTest` — `registerAndActivateEmail`, `attemptLogin` (both overloads), `attemptFullAuthorizeFlow`, `exchangeCodeForToken`, `ensureRoleExists`, `seedConfirmedTotpEnrollment`, `referenceGenerateCode` (pattern source, not modified)
- `RoleAssignmentIntegrationTest` — real-HTTP admin role-assignment pattern (pattern source)
- `ApiKeyLifecycleIntegrationTest` — real-HTTP create/exchange/revoke pattern (pattern source)
- `SessionIntegrationTest` — real-HTTP session list/revoke pattern (pattern source)
- `contracts/api/auth.yaml`, `contracts/api/token-claims.md` — assertion source of truth for shapes/claims

**New, expected by this task:**
- One new integration test class (e.g. `EndToEndLifecycleIntegrationTest`), package TBD in Phase 2 — the task's sole deliverable. No production code file is expected to change.

## Dependencies

- **Entities/repos**: `Account`, `MfaEnrollment`, `ApiKey` (or equivalent), refresh-token family entity — read-only from the test's perspective, exercised via the controllers above.
- **Services**: `MfaService` (direct call for enrollment), `RoleService`/role assignment path, `ApiKeyService`, session/family service backing `AccountController`'s session endpoints.
- **Config**: none new — existing `application.properties` MFA/TOTP and API-key keys (already defined per `design.md` §4c) apply unchanged.
- **Contracts**: `contracts/api/auth.yaml` (route/schema shapes for every HTTP-driven step), `contracts/api/token-claims.md` (claim assertions on both the SAS-issued and API-key-issued JWTs).
- **Infra**: Testcontainers Postgres + Kafka (per task statement and `agents.md` testing tier), no shared base test class exists — this test declares its own containers.

## Acceptance Criteria

| AC | Maps to | Statement |
|---|---|---|
| AC1 | R1, R4 | Registration + email verification transitions an account `PENDING_VERIFICATION` → `ACTIVE` via real HTTP. |
| AC2 | R24, L10 | A freshly `MERCHANT`-assigned account is blocked from completing interactive login (no authorization code) until TOTP is enrolled. |
| AC3 | R24, L10, L6 | After enrollment, the same account completes interactive login with a valid TOTP code and receives an authorization code / tokens. |
| AC4 | R30, L7 (referenced, not scoped) | `POST /api-keys` returns a `ck_live_`-prefixed plaintext key exactly once. |
| AC5 | R31, L8, L9 | `POST /api-keys/token` returns a JWT matching the API-key claim contract (`sub`, `scope`, `amr`, TTL). |
| AC6 | R36 | `GET /accounts/me/sessions` returns the caller's active families with the required fields. |
| AC7 | R37 | `DELETE /accounts/me/sessions/{familyId}` revokes the family; a subsequent list no longer shows it (or an equivalent post-condition). |

## Tests required

- **Named test**: package.md §8 names `shouldConformToAuthOpenApiContract` for this task, but that name is T33's already-implemented, unrelated OpenAPI contract test — not an end-to-end lifecycle test. No entry in package.md's actual §8 list describes this task's scenario. Treated as **no valid named test exists**; Phase 2 will need to name the new test class/method itself rather than match a stale reference.
- **One composed end-to-end test**, covering the full stated flow in a single ordered scenario (matching the task statement's own single-flow framing, not one test per step) — the pattern this session has used for genuine single-flow tasks (cf. `ApiKeyLifecycleIntegrationTest`'s single `shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle`).
- **Boundary implied by AC2**: the pre-enrollment login attempt must be asserted as blocked (no auth code), not merely skipped — otherwise AC2 is unverified. Matches the existing `merchantWithoutMfaEnrollmentCannotLogIn` precedent's assertion style (redirect to `/login?error`, no code).

## Open Questions

- **Q-A (blocker-class, not in package.md §11).** No LOCKED decision or requirement specifies an HTTP transport for TOTP enrollment, and none exists in the codebase (Phase 0 finding). Two ways to satisfy the task statement's "enroll TOTP" step:
  (a) direct `mfaService.beginEnroll`/`.confirm` calls, matching `SasLoginIntegrationTest`'s already-accepted precedent, or
  (b) treat this as a blocker requiring a new production HTTP endpoint (out of scope for a "Final verification" task per every T31–T35 precedent, which shipped test-only or near-test-only changes).
  This is a genuine human-gate decision, not decidable from the spec alone — carried forward to Phase 4.
- **Q-B.** package.md §8 has no valid named test for this task (see Tests required, above) — carried forward as a documentation gap, not a blocker; does not affect what gets built.
- Not blockers, not carried forward: package.md §11's own Q1–Q6 are all either resolved or unrelated to this task's scope.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
