# auth · T20 — Task Implementation Brief (TIB)

## Task
Customize the SAS interactive authentication chain so that, after password success, `MERCHANT`/`ADMIN` accounts without confirmed TOTP enrollment are required to enroll, and accounts with confirmed enrollment are required to present a valid TOTP code or unused recovery code, before an authorization code is issued. Accounts for which MFA is not required proceed on password alone.

## Purpose
Close the last gap that keeps MFA advisory instead of enforced (D-014): today `mfa` only implements enrollment/confirm/disable — nothing in the login path checks or requires it, so a MERCHANT/ADMIN account can obtain a token on password alone.

## Scope

**In:**
- Gate authorization-code issuance in the SAS interactive chain on MFA status (R24, R25, L10).
- Verify a submitted TOTP code or recovery code as part of that gate (R25), reusing `TotpVerifier` and `MfaService.verifyRecoveryCode`.
- Make `TokenClaimsCustomizer`'s `amr`/`acr` conditional on whether the MFA step ran (R26, R27) — in scope because R26/R27 are explicitly in this task's scoped requirement IDs, even though `tasks.md` sequences a separate "Token claim updates" task (21) after this one. This brief takes the header's scoped IDs as authoritative over that sequencing note.
- Record `mfa.failed` on a failed TOTP/recovery-code verification at login (R29 — pulled in as the failure-path counterpart of R25).

**Out:**
- Self-service MFA enrollment/confirm/disable endpoints (task 19 — not built, not needed here; `MfaService`'s existing `beginEnroll`/`confirm`/`disable` are untouched).
- Rate limiting on MFA verification attempts (O2, task 31).
- Authoring `contracts/api/auth.yaml`, `contracts/api/token-claims.md`, or the two event schemas (tasks 33/34) — none gate this task: `auth.yaml` covers non-SAS endpoints only, `token-claims.md`/R48 applies conditionally once authored, and L9 already fixes the claim set independently.
- API keys, sessions, rate limiting generally (later tasks).

## Business Rules
- **R24.** MERCHANT/ADMIN, no confirmed TOTP enrollment → must complete enrollment before an authorization code is issued.
- **R25.** Confirmed TOTP enrollment → valid TOTP code or unused recovery code required before an authorization code is issued.
- **R26.** Password + TOTP login → access token `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp`.
- **R27.** Password-only login, MFA not required → access token `amr: ["pwd"]`, `acr: urn:themistra:acr:pwd`.
- **R29.** Failed TOTP/recovery-code verification → `mfa.failed` audited, authentication denied.

## Locked Decisions
- **L10.** MFA mandatory for MERCHANT/ADMIN, optional for USER/COMPLIANCE; enforced at next interactive login after a mandatory role is granted.
- **L6** (consumed, not redefined). TOTP: RFC 6238, 30s step, 6 digits, HMAC-SHA1, ±1 step — `TotpVerifier` already implements this.
- **L9** (consumed, not redefined). Fixed access-token claim set; R26/R27 changes must land inside it, not add claims.
- **L12.** No feature module imports another module's entity classes. New `authn` code reaches `mfa`/`authz` state only through `MfaService`/`RoleService`.
- **L5** (consumed). Enumeration-safe uniform responses. Does not explicitly name the MFA step, but `agents.md`'s "enumeration-safe everywhere" standing rule is treated as extending to it: a wrong TOTP/recovery code must not be distinguishable, by shape or timing, from any other login failure mode beyond what `TotpVerifier`'s existing constant-time compare already provides.

## Dependencies
- `mfa.MfaService` — `verifyRecoveryCode` (existing, reusable as-is); needs new methods for (a) confirmed-enrollment status check and (b) TOTP-code verification at login without `confirm`/`disable`'s side effects.
- `mfa.TotpVerifier.verify(byte[], String, Instant)` — reusable, stateless.
- `authz.RoleService.resolveEffectiveRoles(UUID)` — role source for the MERCHANT/ADMIN gate.
- `audit.AuditService.record(...)` — `mfa.failed` event type, already precedented in `MfaService`.
- `token.SecurityChainsConfig` — `authorizationServerChain` bean, the integration point.
- `token.TokenClaimsCustomizer` — currently unconditional `amr: ["pwd"]`/`acr: pwd`; must read the MFA outcome.

## Inputs
- Authenticated principal from password step (account UUID, per `AccountUserDetailsService`).
- Submitted TOTP code or recovery code (transport/collection mechanism is an open question — see below).
- Account's effective roles (`RoleService`) and MFA enrollment status (`MfaService`).

## Outputs
- Authorization code issuance either proceeds (MFA not required, or MFA passed) or is withheld (MFA required and not yet satisfied).
- Access token with correct `amr`/`acr` per R26/R27.
- `mfa.failed` audit event on a failed verification attempt.

## State Changes
- Recovery code marked used on successful redemption (existing `MfaService.verifyRecoveryCode` behavior — no new state machine).
- No new persistent state beyond what T16–T18 already created. No new migration expected.

## Files to Create
- `authn/TotpAuthenticationProvider.java` — named in `design.md` §6 as the SAS MFA step.
- `authn/TotpStepUpAuthenticationToken.java` — named in `design.md` §6; carries the MFA outcome (factor used) forward.

## Files to Modify
- `token/SecurityChainsConfig.java` — wire the MFA step into `authorizationServerChain`.
- `token/TokenClaimsCustomizer.java` — make `amr`/`acr` conditional on MFA outcome (R26/R27).
- `mfa/MfaService.java` — add the enrollment-status-check and login-time TOTP-verify methods (§Dependencies).
- `authn/LoginSuccessHandler.java` — likely interception point for redirecting into the MFA step when required; exact mechanism is Phase 5's call, not fixed here.

## Files NOT to Modify
- `mfa/MfaEnrollmentRepository.java`, `mfa/RecoveryCodeRepository.java`, `mfa/MfaSeedEncryption.java`, `mfa/TotpVerifier.java`, `mfa/MfaEnrollment.java`, `mfa/RecoveryCode.java` — consumed via `MfaService` only, not changed.
- `authn/AccountUserDetailsService.java`, `authn/LoginFailureHandler.java` — password-step behavior is out of scope.
- Any Flyway migration — no schema change expected (mfa tables already exist from V1).
- `spec/` — never modified by any phase.

## Acceptance Criteria
| ID | Criterion |
|---|---|
| R24 | MERCHANT/ADMIN, unconfirmed MFA → no authorization code until enrollment completes |
| R25 | Confirmed MFA → valid TOTP or unused recovery code required before authorization code |
| R26 | Password+TOTP login → `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp` |
| R27 | Password-only, MFA not required → `amr: ["pwd"]`, `acr: urn:themistra:acr:pwd` |
| R29 | Failed TOTP/recovery code → `mfa.failed` audited, authentication denied |

## Required Tests
- `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` (R24)
- `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` (R25 — both TOTP and recovery-code branches)
- `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` (R26)
- `shouldIssueTokenWithPwdAmrWhenMfaNotRequired` (R27 — present in `package.md` §8, scoped to R27 which is in this task's header, but absent from the header's own named-test list; included here as required rather than dropped)
- Boundary: wrong TOTP/recovery code denies authentication and audits `mfa.failed`, without issuing a code (R29).
- Boundary: USER/COMPLIANCE account with no enrollment logs in on password alone (negative case for R24/L10).
- Boundary: USER/COMPLIANCE account that has voluntarily enrolled still must pass MFA (R25 conditions on enrollment status, not role).
- Boundary: a recovery code accepted once is rejected on reuse (integration-level confirmation of `MfaService.verifyRecoveryCode`'s existing single-use semantics, wired through the login path).

## Constraints
- **Security:** constant-time TOTP comparison already provided by `TotpVerifier`; no new comparison logic should bypass it. Enumeration-safety (L5, extended per `agents.md`) applies to MFA-step failure responses.
- **Transactional:** any new `MfaService` method that reads/mutates enrollment or recovery-code state follows the existing pattern of atomic conditional queries (e.g. `markUsed`-style), not read-then-write.
- **Module boundaries (L12):** `authn` code reaches `mfa`/`authz` state only via `MfaService`/`RoleService`, never their entities or repositories.
- **Null handling / error signaling:** failures throw typed exceptions (mirroring `MfaNotEnrolledException`, `InvalidTotpCodeException`, `InvalidRecoveryCodeException`), never an unchecked boolean or null, consistent with `MfaService`'s existing style.
- **Compatibility:** must not alter `LoginFailureHandler`'s or `AccountUserDetailsService`'s existing uniform-response behavior for the password step.

## Open Questions
- **Blocker: login-challenge presentation and transport (`design.md` O4) is unresolved.** Whether the TOTP/recovery-code prompt is the default Spring Security form extended with a second step, or a custom Thymeleaf template/endpoint, is an explicitly OPEN spec decision, not a LOCKED one. This determines whether any new controller/template file or `PublicEndpoints`/session-state change is needed beyond the two files already named in `design.md` §6, and whether `LoginSuccessHandler` is modified or replaced for the MFA-pending case. Needs a decision before Phase 5 (Implementation Plan) can finalize the file list and method signatures.
- **Needs confirmation, not blocking:** whether R26/R27's *full* claim-emission responsibility belongs entirely to T20 or is meant to be split with task 21 ("Token claim updates"). This brief resolves it as in-scope for T20 (see Scope), consistent with the task header, but flags the `tasks.md` sequencing tension for visibility.
