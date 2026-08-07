# auth · T20 · Phase 8 — Independent Code Review

Consumed the Phase 6 implementation (`TotpAuthenticationProvider.java`, `TotpStepUpAuthenticationToken.java`, `TotpAuthenticationDetailsSource.java`, `SecurityChainsConfig.java`, `TokenClaimsCustomizer.java`, `MfaService.java`, `MfaEnrollmentRepository.java`, `TotpVerifier.java`) and `artifacts/07-self-review.md`.

Findings below are independent of the Phase 7 self-review; overlaps are re-derived from the actual code.

---

1. **Issue · `TotpStepUpAuthenticationToken` is unlikely to survive `JdbcOAuth2AuthorizationService` serialization, breaking R26/R27 silently or loudly.**
   **Evidence:** `AuthorizationServiceConfig` wires `JdbcOAuth2AuthorizationService`, which persists the OAuth2 authorization (including the principal `Authentication`) as Jackson-serialized JSON. `TotpStepUpAuthenticationToken` is an application-defined `AbstractAuthenticationToken` subclass with a **private constructor and no Jackson mixin**, so Spring Security's built-in Jackson modules cannot deserialize it. At token issuance `TokenClaimsCustomizer` reads `context.getPrincipal() instanceof TotpStepUpAuthenticationToken`; if deserialization fails or returns a different type, `otpUsed` is always false and tokens always emit `amr: ["pwd"]` / `acr: pwd`, even after MFA login.
   **Recommendation:** Either register a Jackson mixin/`@JsonCreator` for the token with the `JdbcOAuth2AuthorizationService`'s `ObjectMapper`, or — simpler and safer — stop using a custom `Authentication` subclass and instead add a synthetic `GrantedAuthority` (e.g., `SimpleGrantedAuthority("OTP_VERIFIED")`) to a standard `UsernamePasswordAuthenticationToken`, then have `TokenClaimsCustomizer` check the authorities.
   **Confidence:** High.

2. **Issue · R25 is not enforced for voluntarily-enrolled non-MERCHANT/ADMIN accounts.**
   **Evidence:** `TotpAuthenticationProvider.authenticate` only calls `requireConfirmedEnrollmentOrFail` and `verifyMfaCodeOrFail` when `isMfaRequired(accountUuid)` returns true, which only checks for `MERCHANT` or `ADMIN` (`TotpAuthenticationProvider.java:98-101`). A `USER` or `COMPLIANCE` account with a confirmed TOTP enrollment skips the MFA step entirely and logs in on password alone. This directly contradicts the extracted R25 ("An account with a confirmed TOTP enrollment must be required to present a valid TOTP code or an unused recovery code") and the boundary test noted in Phase 1 for a voluntarily-enrolled USER/COMPLIANCE account.
   **Recommendation:** Restructure the MFA gate so that a confirmed enrollment always triggers code verification, regardless of role. Use the role check only for R24's "mandatory but not enrolled" block. For example: if `hasConfirmedTotpEnrollment(...)` → verify code; else if `isMfaRequired(...)` → throw (or redirect to enrollment); else → password only.
   **Confidence:** High.

3. **Issue · TOTP replay guard over-rejects legitimate codes because it compares usage instant to step start.**
   **Evidence:** `MfaService.verifyTotpCodeForLogin` (`MfaService.java:224-241`) calls `mfaEnrollmentRepository.recordUseIfNewer(enrollment.getId(), now, acceptedStepStart)`, and `MfaEnrollmentRepository.recordUseIfNewer` (`MfaEnrollmentRepository.java:65-69`) sets `lastUsedAt = :now` with condition `lastUsedAt IS NULL OR lastUsedAt < :acceptedStepStart`. Because `now` is later than `acceptedStepStart`, after any accepted code the guard rejects every code whose step started before that usage instant — including a current-step code submitted immediately after a past-step code was accepted. The guard should compare step-start to step-start, not usage-instant to step-start.
   **Recommendation:** Change `recordUseIfNewer` to set `lastUsedAt = :acceptedStepStart` (or introduce a dedicated `last_accepted_step_start` column) and keep the same condition. Then a current-step code is accepted after a past-step code because its `acceptedStepStart` is greater than the stored previous step start.
   **Confidence:** High.

4. **Issue · Broad `catch (RuntimeException)` in the provider swallows infrastructure failures without operator-visible logging.**
   **Evidence:** `TotpAuthenticationProvider.verifyMfaCodeOrFail` (`TotpAuthenticationProvider.java:119-123`) catches every `RuntimeException` from the MFA service and rewraps it as `BadCredentialsException`. A KMS outage (`MfaEncryptionException`), database failure, or any other unexpected error becomes indistinguishable from a wrong code to the caller *and* produces no log line at the catch site. `LoginFailureHandler` also avoids logging exceptions by design. The only observability would be an `mfa.failed` audit spike.
   **Recommendation:** Add a WARN-level log inside the catch block for non-MFA exceptions (or log all of them, since no secret material is included) while preserving the uniform `BadCredentialsException` response.
   **Confidence:** Medium.

5. **Issue · Raw `mfaCode` is not trimmed and `TotpAuthenticationDetails` can leak it via `toString()`.**
   **Evidence:** `TotpAuthenticationDetailsSource.buildDetails` (`TotpAuthenticationDetailsSource.java:21`) reads `request.getParameter("mfaCode")` with no `.strip()`. `TotpAuthenticationProvider.verifyMfaCodeOrFail` then checks `isBlank()` and dispatches by shape. A recovery code copied with trailing whitespace fails the hash lookup; a TOTP code with whitespace fails the `\d{6}` shape and is mis-dispatched to the recovery-code path. The `TotpAuthenticationDetails` record has no overridden `toString()`, so any logging, exception inspection, or Jackson serialization of `details` exposes the raw code.
   **Recommendation:** `.strip()` the `mfaCode` once in `buildDetails` (or at the top of `verifyMfaCodeOrFail`), and override `TotpAuthenticationDetails.toString()` to redact the code.
   **Confidence:** Medium.

6. **Issue · MFA failures now consume the same lockout budget as password failures.**
   **Evidence:** `LoginFailureHandler` increments the per-account failed-attempt counter for any `ACTIVE`-account authentication failure without branching on cause. Before T20 the budget only absorbed password failures; T20 adds "correct password, wrong TOTP/recovery code" as a new failure mode that spends from the same 5-attempt/30-minute budget. This is not a spec violation (L4 doesn't distinguish causes) but it is a product/security side effect nobody explicitly signed off on.
   **Recommendation:** Confirm whether this is intended. If not, consider branching `LoginFailureHandler` so MFA-step failures increment a separate counter or are excluded from the password lockout budget.
   **Confidence:** Medium.

7. **Issue · `TotpStepUpAuthenticationToken` does not carry `WebAuthenticationDetails` forward.**
   **Evidence:** The provider returns a new `TotpStepUpAuthenticationToken` built only from `userDetails`, `authorities`, and `otpUsed` (`TotpAuthenticationProvider.java:69`). The request's `WebAuthenticationDetails` (remote address, session id) captured by `TotpAuthenticationDetailsSource` is dropped. Any downstream audit or security feature that reads `Authentication.getDetails()` for IP/session context will get `null` after the MFA step.
   **Recommendation:** Copy the details from the incoming `UsernamePasswordAuthenticationToken` into the returned `TotpStepUpAuthenticationToken` (e.g., via `setDetails(...)`) so existing context is preserved.
   **Confidence:** Low.

8. **Issue · R24's "must complete enrollment" requirement is implemented only as a hard block, with no enrollment path.**
   **Evidence:** For a `MERCHANT`/`ADMIN` account without confirmed enrollment, `TotpAuthenticationProvider.requireConfirmedEnrollmentOrFail` throws `BadCredentialsException` (`TotpAuthenticationProvider.java:103-107`). There is no redirect, enrollment initiation, or distinguishing signal. The Phase 2 brief explicitly accepted this as a rollout gap, but it means R24's "complete enrollment" wording is not literally satisfied — the account is simply denied an authorization code.
   **Recommendation:** If this is the intended behavior, document it clearly in the frozen brief as "enforcement = hard block; enrollment UI is a separate task" so R24 tests assert denial rather than completion. If completion in-flow is required, add the redirect/enrollment step to scope.
   **Confidence:** Medium (depends on intended product behavior).
