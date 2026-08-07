> **STATUS: FROZEN.** Approved 2026-08-07. Downstream phases (5 onward) treat this brief as non-renegotiable — any change to scope, files, or acceptance criteria from here on requires reopening this gate, not a silent deviation in a later phase.

# auth · T20 — Frozen Task Brief

## Governing design decision (resolves Phase 3 #1, #4, #8)

**O4 resolved: single-request login, no new endpoint.** The `/login` form is extended with one optional `mfaCode` field, submitted together with `username`/`password` in the same `POST`. There is no second page, no partially-authenticated session state, and no new HTTP path — `TotpAuthenticationProvider` performs password verification *and* the conditional MFA check inside one `authenticate()` call, replacing the default `DaoAuthenticationProvider` for this flow. This is the cheapest option that satisfies D-014 ("no authorization code until MFA passes") without inventing session/partial-auth machinery the spec never asked for, and it is why several of the ten findings collapse into "no change needed" below.

Consequence adopted deliberately, stated explicitly rather than left implicit: **every failure mode inside `TotpAuthenticationProvider` — wrong password, wrong TOTP code, wrong recovery code, and missing MFA enrollment on a MERCHANT/ADMIN account — throws the same `BadCredentialsException` and produces the identical `/login?error` response.** This was a real design fork (see #2/#6 below): a distinct "please enroll MFA" message is *only* reachable once the password is already proven correct, which would work as a password-correctness oracle for an attacker probing a known MERCHANT/ADMIN email. Full uniformity avoids that oracle at the cost of a UX gap — a MERCHANT/ADMIN account without enrollment gets no in-band signal telling them what to do. That gap is accepted and recorded below (#2), not hidden.

## Phase 3 findings — disposition

1. **O4 blocker — ACCEPTED, resolved above.**
2. **R24 vs. out-of-scope task 19 — ACCEPTED, resolved with a recorded gap.** T20 implements the *block* only (deny the authorization code for MERCHANT/ADMIN without confirmed enrollment), uniformly, per the design decision above. It does not implement a self-service enrollment path — that stays task 19's job. Recorded consequence: until task 19 ships, an affected account has no self-service way to unblock itself through this login flow; that must be handled out of band (support/admin channel) until task 19 lands. Not a T20 defect — a rollout-ordering fact worth the team's attention.
3. **R26/R27 vs. task 21 — ACCEPTED, locked.** T20 fully owns `TokenClaimsCustomizer`'s `amr`/`acr` output for the interactive (`pwd`-only and `pwd`+`otp`) cases. `tasks.md` task 21's remaining scope, if any, is the `api-key` grant branch only (which doesn't exist yet — task 25 builds `ApiKeyTokenIssuer`), unaffected by this task.
4. **Claims-propagation mechanism unspecified — ACCEPTED, resolved.** `TotpAuthenticationProvider.authenticate()` returns an authenticated `TotpStepUpAuthenticationToken` carrying an `otpUsed` fact. That `Authentication` is the one SAS stores against the resulting `OAuth2Authorization` and later hands back as `context.getPrincipal()` at token-issuance time (including on refresh — see #10). `TokenClaimsCustomizer.customizeAccessToken` branches on `context.getPrincipal()` instanceof `TotpStepUpAuthenticationToken` with `otpUsed() == true` → `amr: ["pwd","otp"]`/`acr: otp`; otherwise (plain password-only principal) → `amr: ["pwd"]`/`acr: pwd`, i.e. today's unconditional behavior becomes the fallback branch.
5. **`MfaService` method contracts unspecified — ACCEPTED, locked:**
   - `boolean hasConfirmedTotpEnrollment(UUID accountUuid)` — pure read, no side effects, no audit.
   - `void verifyTotpCodeForLogin(UUID accountUuid, String submittedCode)` — throws `MfaNotEnrolledException` if no confirmed enrollment; throws `InvalidTotpCodeException` on a wrong code (records `mfa.failed`, R29); returns normally on success (records replay-resistance state, #7). No account-status precondition (see #9).
6. **Enumeration safety for MFA failures — ACCEPTED, resolved by full uniformity** (governing decision above) rather than a "same handler, different message" compromise — deliberately more conservative than Phase 3's suggestion, to close the password-oracle risk that a partially-distinct message would open.
7. **TOTP replay resistance — ACCEPTED, will be implemented, not just documented-as-accepted-risk.** `verifyTotpCodeForLogin` uses the already-built `MfaEnrollment.lastUsedAt`/`recordUse(Instant)`: a code is accepted only if its matched time-step is strictly newer than the step implied by `lastUsedAt`; on acceptance, `recordUse(clock.instant())` is called. This requires `TotpVerifier` to expose *which* step matched (its current `verify(...): boolean` doesn't) — `TotpVerifier.java` therefore moves from "no change" to a small **additive** change (new overload; existing signature/behavior unchanged for `confirm`/`disable`'s callers). Exact signature is Phase 5's call.
8. **Challenge-endpoint security posture — MOOT under the governing decision.** No new endpoint exists, so `PublicEndpoints.java` is untouched and there is no semi-authenticated session state to secure.
9. **Account-state preconditions on new methods — ACCEPTED, resolved.** The new `MfaService` methods trust the password step's gating entirely (no independent `ACTIVE` check) — consistent with `verifyRecoveryCode`'s existing precedent, and correct here because password verification happens first, in the same `authenticate()` call, before either new method is ever reached.
10. **Refresh-grant `amr`/`acr` — ACCEPTED, resolved.** Refresh tokens preserve the original interactive authentication's `amr`/`acr`; no re-evaluation at refresh time. This falls out of #4's mechanism automatically (SAS hands `TokenClaimsCustomizer` the same originally-stored principal on a refresh-token grant) — no extra code required, just stated explicitly so tests can assert it.

No findings rejected.

---

## Task
Customize the SAS interactive authentication chain so that, after password success, `MERCHANT`/`ADMIN` accounts without confirmed TOTP enrollment are blocked from receiving an authorization code, and accounts with confirmed enrollment must present a valid TOTP code or unused recovery code before one is issued. Accounts for which MFA is not required proceed on password alone. All of this happens inside a single `/login` `POST`, not a multi-step session flow.

## Purpose
Close the last gap that keeps MFA advisory instead of enforced (D-014): today `mfa` implements enrollment/confirm/disable but nothing in the login path checks or requires it.

## Scope

**In:**
- Single-request password+MFA authentication via `TotpAuthenticationProvider` (R24, R25, L10).
- TOTP/recovery-code verification at login, with replay resistance (R25, R29, Phase 3 #7).
- Conditional `amr`/`acr` in `TokenClaimsCustomizer` for both initial and refresh grants (R26, R27).
- `mfa.failed` audit on any failed verification at login (R29).

**Out:**
- Self-service MFA enrollment/confirm/disable (task 19 — untouched; recorded rollout gap, #2).
- Any in-band signal distinguishing "needs enrollment" from "wrong credentials" (deliberately rejected, #6).
- Rate limiting on login/MFA attempts (O2, task 31).
- Authoring `contracts/api/auth.yaml`, `contracts/api/token-claims.md`, or the two event schemas (tasks 33/34) — none gate this task.
- API keys, sessions (later tasks); `api-key`-grant branch of `TokenClaimsCustomizer` (task 21/25 remainder).

## Business Rules
- **R24.** MERCHANT/ADMIN, no confirmed TOTP enrollment → blocked from receiving an authorization code (uniform failure, no distinguishing signal).
- **R25.** Confirmed TOTP enrollment → valid TOTP code or unused, not-already-consumed recovery code required before an authorization code is issued.
- **R26.** Password + TOTP login → `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp` — on both the original token and any subsequent refresh.
- **R27.** Password-only login, MFA not required → `amr: ["pwd"]`, `acr: urn:themistra:acr:pwd` — on both the original token and any subsequent refresh.
- **R29.** Failed TOTP/recovery-code verification → `mfa.failed` audited, authentication denied via the same uniform failure as R24/wrong-password.

## Locked Decisions
- **L10.** MFA mandatory for MERCHANT/ADMIN, optional for USER/COMPLIANCE; enforced at next interactive login after a mandatory role is granted.
- **L6** (consumed). TOTP: RFC 6238, 30s step, 6 digits, HMAC-SHA1, ±1 step — `TotpVerifier` implements this; the replay-resistance overload must not change these parameters.
- **L9** (consumed). Fixed claim set; `amr`/`acr` changes land inside it, no new claims added.
- **L12.** No feature module imports another module's entity classes; `authn` reaches `mfa`/`authz` state only via `MfaService`/`RoleService`.
- **L5** (extended per `agents.md`'s "enumeration-safe everywhere," applied here at maximum strength — full uniformity, no partial exception for the enrollment-required case).

## Dependencies
- `mfa.MfaService` — `verifyRecoveryCode` (existing, reused as-is) plus two new methods (#5).
- `mfa.TotpVerifier` — existing `verify(...)` unchanged; one new additive overload exposing the matched step (#7).
- `authz.RoleService.resolveEffectiveRoles(UUID)` — MERCHANT/ADMIN gate.
- `authn.AccountUserDetailsService` — reused as-is for credential/status loading inside the new provider.
- `audit.AuditService.record(...)` — `mfa.failed`, existing event type.
- `token.SecurityChainsConfig`, `token.TokenClaimsCustomizer` — integration points.

## Inputs
- `username`, `password`, optional `mfaCode` from the single `/login` POST.
- Account's effective roles (`RoleService`) and confirmed-enrollment status (`MfaService`).

## Outputs
- Authorization code issuance proceeds only when R24/R25's conditions are satisfied.
- Access token with correct `amr`/`acr` (R26/R27), preserved across refresh (#10).
- `mfa.failed` audit event on a failed verification attempt.

## State Changes
- `MfaEnrollment.lastUsedAt` updated via `recordUse` on a successful login-time TOTP verification (new — replay resistance, #7).
- Recovery code marked used on successful redemption (existing `MfaService.verifyRecoveryCode` behavior, unchanged).
- No new persistent state beyond T16–T18; no new migration.

## Files to Create
- `authn/TotpAuthenticationProvider.java` — replaces `DaoAuthenticationProvider` for the login flow; verifies password (via `AccountUserDetailsService` + the existing `PasswordEncoder` bean) and, conditionally, MFA; throws uniform `BadCredentialsException` on any failure mode.
- `authn/TotpStepUpAuthenticationToken.java` — the authenticated result, carrying the `otpUsed` fact `TokenClaimsCustomizer` reads (#4).
- One small additional file, exact name/shape left to Phase 5, for capturing the optional `mfaCode` request parameter into the authentication attempt (e.g. a custom `AuthenticationDetailsSource`) — pre-authorized so Phase 5 isn't blocked by "no unauthorized files."

## Files to Modify
- `token/SecurityChainsConfig.java` — register `TotpAuthenticationProvider`; ensure no duplicate/competing `DaoAuthenticationProvider` remains registered for `UsernamePasswordAuthenticationToken`; wire the `mfaCode`-capturing mechanism into `.formLogin(...)`.
- `token/TokenClaimsCustomizer.java` — conditional `amr`/`acr` per #4; applies identically to initial and refresh grants (#10), no grant-type branching needed beyond what already exists for `CLIENT_CREDENTIALS`.
- `mfa/MfaService.java` — add `hasConfirmedTotpEnrollment` and `verifyTotpCodeForLogin` (#5), including the replay check (#7).
- `mfa/TotpVerifier.java` — additive-only: new overload exposing the matched time-step; existing `verify(byte[], String, Instant): boolean` signature and behavior unchanged.

## Files NOT to Modify
- `authn/LoginSuccessHandler.java`, `authn/LoginFailureHandler.java` — both already provider-agnostic (any successful `Authentication` → success handler; any `AuthenticationException` → the same uniform failure handler); the new provider composes with both without changes.
- `authn/AccountUserDetailsService.java` — reused as-is.
- `mfa/MfaEnrollmentRepository.java`, `mfa/RecoveryCodeRepository.java`, `mfa/MfaSeedEncryption.java`, `mfa/MfaEnrollment.java`, `mfa/RecoveryCode.java` — consumed via `MfaService` only.
- `common/PublicEndpoints.java` — no new endpoint (#8, moot).
- Any Flyway migration; `spec/` (never modified by any phase).

## Acceptance Criteria
| ID | Criterion |
|---|---|
| R24 | MERCHANT/ADMIN, unconfirmed MFA → no authorization code, uniform failure |
| R25 | Confirmed MFA → valid TOTP or unused recovery code required before authorization code |
| R26 | Password+TOTP login → `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp`, preserved on refresh |
| R27 | Password-only, MFA not required → `amr: ["pwd"]`, `acr: urn:themistra:acr:pwd`, preserved on refresh |
| R29 | Failed TOTP/recovery code → `mfa.failed` audited, authentication denied, uniform failure |

## Required Tests
- `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` (R24)
- `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` (R25 — TOTP branch and recovery-code branch)
- `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` (R26)
- `shouldIssueTokenWithPwdAmrWhenMfaNotRequired` (R27 — in `package.md` §8, scoped to R27; included despite absence from the task header's named-test list)
- Boundary: wrong TOTP/recovery code → uniform failure, `mfa.failed` audited, no code issued (R29).
- Boundary: same valid TOTP code replayed within the 90s tolerance window on a second attempt → rejected (#7).
- Boundary: USER/COMPLIANCE, no enrollment → password-only login succeeds (negative case, L10).
- Boundary: USER/COMPLIANCE, voluntarily enrolled → still must pass MFA (R25 conditions on enrollment, not role).
- Boundary: recovery code accepted once → rejected on reuse, through the login path (not just the unit).
- Boundary: `amr`/`acr` on a refresh-token grant matches the original interactive login's outcome (#10).
- ArchUnit: no new `authn` class imports an `mfa`/`authz` entity directly (L12) — confirm existing rules already cover this; add one only if they don't.

## Constraints
- **Security:** full response uniformity across every `TotpAuthenticationProvider` failure mode (governing decision) — no per-cause message, status, or timing difference reaching the client. Constant-time TOTP comparison stays inside `TotpVerifier`.
- **Transactional:** `verifyTotpCodeForLogin` and the replay-state update follow the codebase's existing atomic-conditional-update pattern (cf. `confirmIfUnconfirmed`, `markUsed`), not read-then-write.
- **Module boundaries (L12):** `authn` reaches `mfa`/`authz` only via their services.
- **Null handling / error signaling:** typed exceptions (`MfaNotEnrolledException`, `InvalidTotpCodeException`, existing `InvalidRecoveryCodeException`), never an unchecked boolean or null.
- **No duplicate authentication providers:** `SecurityChainsConfig` must ensure `TotpAuthenticationProvider` is the sole provider handling the login form's token type — Spring Boot's autoconfigured `DaoAuthenticationProvider` (from `AccountUserDetailsService` + the existing `PasswordEncoder` bean) must not also remain registered, or password verification would run twice with divergent MFA enforcement.

## Open Questions
No blockers. All ten Phase 3 findings resolved above; the one accepted trade-off (#2/#6 — no in-band "please enroll" signal, and no self-service enrollment path until task 19 ships) is recorded as a known rollout gap, not an open question requiring further decision before implementation.
