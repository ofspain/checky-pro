> **STATUS: RESOLVED.** Human sign-off given 2026-08-08 ("let go team") on the recommendation set presented alongside the Phase 8 findings. Fixes applied below; `mvn -pl services/auth -am compile`, `test-compile`, and `ArchitectureTest`/`MfaServiceTest`/`TotpVerifierTest` (33/33) all pass after every change.

# auth · T20 · Phase 9 — Review Resolution

Eight findings from Phase 8 (`08-independent-review.md`), four of which independently re-derived Phase 7 self-review findings. Each disposed below; exact changes follow.

---

**1. Custom `Authentication` subclass unlikely to survive `JdbcOAuth2AuthorizationService`'s Jackson persistence (R26/R27 silently broken).**
**Disposition: ACCEPTED.**
Given no Docker/Testcontainers access in this environment to verify empirically, chose not to gamble on the original design. Replaced `TotpStepUpAuthenticationToken` (custom `AbstractAuthenticationToken`, private constructor, no Jackson support) with a synthetic `SimpleGrantedAuthority("OTP_VERIFIED")` — a type Spring Security's own Jackson modules already cover — added to a plain `UsernamePasswordAuthenticationToken`'s authorities.
**Change:** Deleted `authn/TotpStepUpAuthenticationToken.java`. `authn/TotpAuthenticationProvider.java` now returns `new UsernamePasswordAuthenticationToken(userDetails, null, authoritiesFor(userDetails, otpUsed))`, with a new public constant `TotpAuthenticationProvider.OTP_VERIFIED_AUTHORITY`. `token/TokenClaimsCustomizer.java` now checks `context.getPrincipal().getAuthorities().contains(TotpAuthenticationProvider.OTP_VERIFIED_AUTHORITY)` instead of an `instanceof` check. Side benefit: `token` no longer depends on a custom `authn` type at all, just a `public static final` constant.

**2. R25 not enforced for voluntarily-enrolled non-MERCHANT/ADMIN accounts.**
**Disposition: ACCEPTED.**
Confirmed by tracing the code: the MFA gate was keyed on `isMfaRequired` (role only), so a `USER`/`COMPLIANCE` account with a confirmed enrollment skipped MFA entirely — contradicts R25, which conditions on enrollment, not role.
**Change:** `TotpAuthenticationProvider.authenticate` restructured: checks `mfaService.hasConfirmedTotpEnrollment(accountUuid)` first (triggers MFA verification regardless of role, R25); only falls through to the role check (`isMfaRequired`, R24 — MERCHANT/ADMIN without enrollment blocked) when no confirmed enrollment exists.

**3. TOTP replay guard over-rejects legitimate next-step codes.**
**Disposition: ACCEPTED.**
Hand-traced the arithmetic and confirmed: `recordUseIfNewer` stored wall-clock `now` as `lastUsedAt`, but compared it against `acceptedStepStart` (a step-aligned value) on the next call. Under ordinary network latency, `now` for one accepted step can already exceed the *next* step's start, causing a legitimate subsequent code to be rejected as a false replay.
**Change:** `mfa/MfaEnrollmentRepository.recordUseIfNewer` now takes only `(Long id, Instant acceptedStepStart)` and stores `acceptedStepStart` itself, not wall-clock time — comparing step-start against step-start makes "strictly newer" correct. `mfa/MfaService.verifyTotpCodeForLogin` updated to match (dropped the now-unused separate `now` local).

**4. Broad `catch (RuntimeException)` swallows infrastructure failures with no operator-visible logging.**
**Disposition: ACCEPTED.**
**Change:** `TotpAuthenticationProvider.verifyMfaCodeOrFail` now multi-catches the three expected MFA-verification exceptions (`InvalidTotpCodeException`, `InvalidRecoveryCodeException`, `MfaNotEnrolledException`) silently (uniform response, nothing unusual), and separately catches any other `RuntimeException` with a `log.warn(...)` before rewrapping — the client-facing response stays uniform either way; only operator visibility changes.

**5. Raw `mfaCode` not trimmed; `TotpAuthenticationDetails` leaks it via default record `toString()`.**
**Disposition: ACCEPTED.**
**Change:** `TotpAuthenticationDetailsSource.buildDetails` now `.strip()`s the raw form value before wrapping it. `TotpAuthenticationDetails` gained an overridden `toString()` that redacts `mfaCode` — relevant because `AbstractAuthenticationToken`'s own `toString()` includes `details`, so any default logging of the `Authentication` object would otherwise have printed the raw code.

**6. MFA failures now consume the same lockout budget as password failures.**
**Disposition: ACCEPTED as documented behavior, no code change.**
Confirmed as a genuine, non-obvious consequence, not a spec violation (L4 doesn't distinguish failure cause) and defensible as-is: it also rate-limits TOTP brute-forcing, which nothing else in this task does. Decision: leave `LoginFailureHandler`'s uniform counting untouched. Recorded here as the explicit sign-off this finding asked for, so it isn't mistaken for an unexamined side effect later.

**7. `TotpStepUpAuthenticationToken` didn't carry `WebAuthenticationDetails` forward.**
**Disposition: ACCEPTED, with a deliberate narrowing beyond the literal recommendation.**
Kimi's recommendation was "copy the details from the incoming token forward." Implementing that literally would have also carried the raw `mfaCode` forward onto the token that `JdbcOAuth2AuthorizationService` persists — embedding a bearer secret (recovery code or TOTP code) into the `oauth2_authorization` table's JSON blob, a worse problem than the one being fixed. Narrowed the fix accordingly.
**Change:** `TotpAuthenticationProvider` now has a `webDetailsOnly(...)` helper that extracts and forwards only the `WebAuthenticationDetails` (remote address/session id) component of the incoming `TotpAuthenticationDetails`, never the `mfaCode` component, onto the authenticated result via `setDetails(...)`.

**8. R24's "must complete enrollment" is implemented as a hard block only, no enrollment path.**
**Disposition: ACCEPTED as accurate, no code change.**
Already an explicit, recorded decision from the frozen brief (Phase 4 #2/#6) — self-service enrollment is task 19's scope, not T20's. Kimi's specific ask (word Phase 10's tests as asserting *denial*, not *completion*) is a test-authoring instruction, not a code change; carried forward here for whoever writes Phase 10's tests to see.

---

## Files changed this phase
- `authn/TotpAuthenticationProvider.java` — restructured gate (R25 fix), redesigned MFA-outcome propagation (Jackson-safety fix), narrowed `setDetails` forwarding, multi-catch with logging.
- `authn/TotpAuthenticationDetailsSource.java` — `.strip()`, redacted `toString()`.
- `authn/TotpStepUpAuthenticationToken.java` — deleted (superseded by the `OTP_VERIFIED_AUTHORITY` approach).
- `token/TokenClaimsCustomizer.java` — reads the granted authority instead of an `instanceof` check on the now-deleted type.
- `mfa/MfaEnrollmentRepository.java` — `recordUseIfNewer` signature and stored value both corrected.
- `mfa/MfaService.java` — call site updated to match.

No public API outside this task's own new classes changed; no class renamed (one class removed, per the accepted #1 redesign, not renamed). No unrelated refactoring.
