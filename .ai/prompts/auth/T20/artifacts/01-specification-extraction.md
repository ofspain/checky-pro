# auth · T20 · Phase 1 — Specification Extraction

## Business Rules

- **R24.** A `MERCHANT`/`ADMIN` account with no confirmed TOTP enrollment must be required to complete TOTP enrollment before an authorization code is issued.
- **R25.** An account with a confirmed TOTP enrollment must be required to present a valid TOTP code or an unused recovery code, during the SAS interactive flow, before an authorization code is issued.
- **R26.** A login completed with password + TOTP must produce an access token with `amr: ["pwd","otp"]` and `acr: urn:themistra:acr:otp`.
- **R27.** A login completed with password only, where MFA is not required, must produce an access token with `amr: ["pwd"]` and `acr: urn:themistra:acr:pwd`.
- **R29 (widened in).** A failed TOTP or recovery-code verification must record an `mfa.failed` audit event and deny authentication — this is the failure-path rule for the same login-time verification R25 establishes, and `MfaService`'s existing `verifyRecoveryCode`/`confirm` methods already implement this pattern for the branches they own; the new login-time TOTP check this task adds must follow it too.

Not pulled in: R22/R23/R28 (enrollment begin/confirm/disable — tasks 18/19, already implemented or explicitly a different task) and R30+ (API keys, sessions, rate limiting — later tasks). R43–R48 (general audit/contract mandates) are standing rules from `agents.md`/§3's audit section, not restated here as task-specific business rules.

## Locked Decisions

- **L10.** MFA is mandatory for `MERCHANT`/`ADMIN`, optional for `USER`/`COMPLIANCE`; enrollment is enforced at the next interactive login after a mandatory role is granted. This is the role-gating rule R24 implements.
- **L6** (referenced, not owned by this task). RFC 6238 TOTP: 30s step, 6 digits, HMAC-SHA1, ±1 step tolerance — already implemented in `TotpVerifier`; this task consumes it, does not redefine it.
- **L9** (referenced). The exact access-token claim set, including `amr`/`acr` — this task's R26/R27 obligations must land inside this fixed set, not add new claims.
- **L5** (referenced, scope ambiguous for this task — see Open Questions). Enumeration-safe uniform responses; explicitly enumerates login/registration/reset/verification endpoints but does not explicitly name the MFA step.
- **L12** (referenced). No feature module imports another module's entity classes — binds how `authn`'s new MFA-step code may reach `mfa`'s and `authz`'s state (through `MfaService`/`RoleService` only, never `MfaEnrollment`/`RecoveryCode`/`Role` directly).
- **L11** (referenced, possible tension — see Open Questions). The public-endpoint list is closed and CI-enforced; if the MFA step needs any new HTTP-reachable path, its authentication posture (public vs. authenticated vs. something in between — a user who passed password but not yet MFA) isn't obviously either bucket in the existing list.

## Files involved

**Existing — read and extend:**
- `token/SecurityChainsConfig.java` — the `authorizationServerChain` bean is the integration point (its own doc comment already earmarks it: "the MFA step (D-014) plugs into this chain's authentication in a later stage").
- `token/TokenClaimsCustomizer.java` — currently hardcodes `amr: ["pwd"]`/`acr: pwd` for every interactive token; must become conditional on the MFA outcome (R26/R27). Its own comment already flags this: `// MFA stage appends "otp" when MFA passed`.
- `mfa/MfaService.java` — `verifyRecoveryCode(UUID, String)` is already built for this task's use (its Javadoc says so verbatim). No equivalent exists yet for (a) checking confirmed-enrollment status and (b) verifying a submitted TOTP code without `confirm`/`disable`'s side effects — both are needed and most likely land here as new methods, to keep `MfaEnrollmentRepository` (package-private) and `MfaSeedEncryption` (secret decryption) behind this module's own service, consistent with how every other cross-module read in this codebase is shaped.
- `mfa/TotpVerifier.java` — stateless `verify(secret, code, now)`; reusable as-is, but only once a decrypted secret is obtained (which today only happens inside `MfaService`).
- `authz/RoleService.java` — `resolveEffectiveRoles(UUID)` is the only role source; needed to test for `MERCHANT`/`ADMIN` (L10/R24).
- `authn/AccountUserDetailsService.java`, `authn/LoginSuccessHandler.java`, `authn/LoginFailureHandler.java` — not modified by R24–R27 directly, but establish the only precedent in this codebase for hooking the SAS password-login chain without disturbing its default redirect/session behavior; the new MFA step must follow the same posture (extend Spring Security defaults, never replace them).
- `common/PublicEndpoints.java` — consulted, not necessarily changed; only relevant if the design ends up needing a new HTTP-reachable path for the MFA challenge (open question below).

**New — expected by `design.md` §6 (package map), scoped to what R24–R27 actually require:**
- `authn/TotpAuthenticationProvider.java` — named explicitly in `design.md` as "SAS MFA step, details depend on O1/O4." O1 is resolved (L14/D-025); O4 (login-page presentation) is still open (see below).
- `authn/TotpStepUpAuthenticationToken.java` — named explicitly in `design.md`; the vehicle for carrying the MFA-passed fact (and which factor: TOTP vs. recovery code) forward to wherever `TokenClaimsCustomizer` reads it.
- Note: `design.md` §6 also lists `authn/LoginAttemptAuditService.java`, but that responsibility is already satisfied today by direct `AuditService.record(...)` calls inline in `LoginFailureHandler`/`MfaService` — that file appears to predate T13/T18's actual implementation and is not obviously still needed; flagged, not assumed either way.

**Not involved (contra the task header — see Open Questions):** the four contracts listed in this task's header (`auth.yaml`, `token-claims.md`, two event schemas) do not exist yet, and on inspection of R47/R48's wording ("WHERE ... is authored") and `tasks.md`'s framing of `auth.yaml` as covering "non-SAS endpoints," none of them appear to actually gate this task's implementation — see Open Questions for the reasoning, not asserted as settled.

## Dependencies

- **Classes/services:** `mfa.MfaService` (extended), `mfa.TotpVerifier` (reused), `authz.RoleService.resolveEffectiveRoles` (reused), `audit.AuditService.record` (reused, `mfa.failed` event type already precedented).
- **Entities (indirect, via owning services only — L12):** `MfaEnrollment` (confirmed-status, `account_id`), `RecoveryCode` (single-use hash match) — both stay behind `MfaService`.
- **Repositories:** none directly — `MfaEnrollmentRepository`/`RecoveryCodeRepository` are package-private to `mfa`; any new query need is a new `MfaService` method, not direct repository access from `authn`.
- **Config:** no new keys identified — `themistra.auth.mfa.issuer-name` and `themistra.auth.mfa.seed-kek-arn` (existing, from T16) are the only MFA config already defined in `design.md` §4c; nothing in that VERBATIM block adds step-up-specific configuration.
- **Contracts:** none block this task in practice (see Files-involved note and Open Questions) — `acr` values `urn:themistra:acr:pwd` / `urn:themistra:acr:otp` and the `amr` array shape are fixed directly by R26/R27/L9, not by an unauthored contract file.

## Acceptance Criteria

| ID | Criterion | Named test |
|---|---|---|
| R24 | MERCHANT/ADMIN account, no confirmed TOTP enrollment → interactive login is blocked from receiving an authorization code until enrollment completes | `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` |
| R25 | Account with confirmed TOTP enrollment → a valid TOTP code or an unused recovery code is required before an authorization code is issued | `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` |
| R26 | Password + TOTP login → access token has `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp` | `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` |
| R27 | Password-only login, MFA not required → access token has `amr: ["pwd"]`, `acr: urn:themistra:acr:pwd` | `shouldIssueTokenWithPwdAmrWhenMfaNotRequired` (see Tests required — not in this task's header list, but present in `package.md` §8 and is unambiguously R27's test) |
| R29 | Failed TOTP/recovery-code verification during login → `mfa.failed` audited, authentication denied | not separately named in §8; implied boundary coverage of R25's test |

## Tests required

From `package.md` §8, matched by content (not by its drifted ID column — see Phase 0 §5):
1. `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` — R24.
2. `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` — R25 (both the TOTP branch and the recovery-code branch belong under this one name; may need two test methods/cases).
3. `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` — R26.
4. `shouldIssueTokenWithPwdAmrWhenMfaNotRequired` — R27. **Not listed in this task's header's "Named tests" set**, but it is scoped to R27 which *is* in the header, and it appears in `package.md` §8 immediately after the three header tests. Treating it as required; flagging the header/package.md mismatch rather than silently dropping the test.

Boundary tests implied, not separately named:
- Wrong TOTP code / wrong recovery code at login → denies authentication, records `mfa.failed` (R29), and does not issue an authorization code.
- A `USER`/`COMPLIANCE` account with *no* enrollment logs in with password only, unaffected by R24 (negative case for the role gate, L10).
- A `USER`/`COMPLIANCE` account that *has* voluntarily enrolled still must pass MFA (R25 conditions on "has a confirmed enrollment," not on role) — worth a boundary case since R24 and R25 are independently triggered.
- Recovery-code single-use: a recovery code accepted once must be rejected on a second attempt (already covered at the service level by `MfaService.verifyRecoveryCode`'s `markUsed` semantics; an integration-level boundary test at the login flow would confirm the wiring, not just the unit).
- ArchUnit: new `authn` classes must not import `mfa`/`authz` entities (L12) — covered by the existing `repositories_are_never_public` / entity-boundary rules already in `ArchitectureTest`; confirm no new rule is needed rather than assuming one is.

## Open Questions

1. **Login-page/challenge presentation (`design.md` O4) is still open.** T20 cannot be fully designed without knowing whether the TOTP/recovery-code prompt is the default Spring Security form extended with a second field/step, or a custom Thymeleaf template. O4's own guidance says "propose one option; proceed if low-risk" — not a hard blocker per the spec's own rules, but it directly shapes whether `TotpAuthenticationProvider` is reached via a second `/login` POST, a distinct path, or an `AuthenticationManager`-level multi-provider chain. Proposing this belongs to Phase 2 (design), not resolved here.
2. **R26/R27 vs. `tasks.md` task 21 ("Token claim updates").** `tasks.md` assigns full `amr`/`acr` correctness to a separate later task, but R26/R27 are scoped to *this* task's header. Carried over from Phase 0 unresolved — this task cannot claim R26/R27 complete without touching `TokenClaimsCustomizer`, so either T20's scope includes that customizer change (contradicting the task-21 split) or T20 only has to make the MFA *fact* available (e.g. via `TotpStepUpAuthenticationToken`) for task 21 to consume later, leaving R26/R27 only partially satisfied by T20 alone. Needs an explicit call before Phase 2 design commits to one shape.
3. **L5's enumeration-safety list does not name the MFA step.** L5 enumerates login/registration/password-reset/email-verification; whether the same uniform-response discipline is required for a wrong-TOTP-code response is not explicitly locked, though R29's audit requirement and the general zero-trust/enumeration posture in `agents.md` strongly suggest it should be. Not a blocker — treating "yes, uniform" as the safe default per `agents.md`'s "enumeration-safe everywhere" standing rule — but noting it isn't literally spelled out in L5's list.
4. **The four contracts named in the task header do not gate this task, on the current reading** — `auth.yaml` is scoped to non-SAS endpoints (`tasks.md` #33) and the SAS interactive chain isn't one; `token-claims.md`/R48 uses "WHERE ... is authored" conditional language, meaning its constraint activates only once that file exists, and L9 (already LOCKED) fixes the claim set independently in the meantime; the two event schemas (R44/R45) aren't implicated by any of R24–R27. This resolves Phase 0's flagged gap in the negative (not blocking) rather than leaving it open, but is stated here as a reading of the spec text, not a directive — worth a sanity check before Phase 4 freezes the brief.
5. **New `MfaService` methods are not named anywhere in the spec.** Their need is inferred (Files involved, above), not spec-mandated by name/signature. Design/naming is Phase 2's job; flagged here only so Phase 2 doesn't mistake this for a pre-decided contract.
