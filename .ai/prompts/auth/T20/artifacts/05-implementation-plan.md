# auth · T20 · Phase 5 — Implementation Plan

Every file below traces to the frozen brief's Files to Create/Modify. No file added beyond what it authorized — the one "small additional file" it pre-authorized for capturing `mfaCode` is named here as `authn/TotpAuthenticationDetails.java` (a details holder + its `AuthenticationDetailsSource`, one file).

## Files to Create

### `authn/TotpStepUpAuthenticationToken.java`
The authenticated result of the new provider. Extends `AbstractAuthenticationToken`.
- `static TotpStepUpAuthenticationToken authenticated(Object principal, Collection<? extends GrantedAuthority> authorities, boolean otpUsed)` — factory; sets `setAuthenticated(true)`.
- `Object getPrincipal()` — returns the `UserDetails` loaded for the account (not just the UUID string), so `Authentication.getName()` resolves the same way it already does for today's password-only login (via `UserDetails.getUsername()` = account UUID), keeping `TokenClaimsCustomizer.resolveRoles` unchanged.
- `Object getCredentials()` — returns `null` (never carries the password or TOTP code past authentication).
- `boolean otpUsed()` — true only when the login satisfied R25 via TOTP or recovery code.

### `authn/TotpAuthenticationDetails.java`
- `record TotpAuthenticationDetails(WebAuthenticationDetails webDetails, String mfaCode)` — replaces the filter's default details object; `webDetails` preserves the remote-address/session-id info `WebAuthenticationDetails` normally carries, `mfaCode` is the raw optional form field.
- `class TotpAuthenticationDetailsSource implements AuthenticationDetailsSource<HttpServletRequest, TotpAuthenticationDetails>` — `buildDetails(HttpServletRequest request)` reads `request.getParameter("mfaCode")` (may be null/blank) and wraps it with `new WebAuthenticationDetails(request)`.

### `authn/TotpAuthenticationProvider.java`
Implements `AuthenticationProvider`. Replaces `DaoAuthenticationProvider` for the login form's token type — this is the SAS MFA step (D-014).

**Public methods:**
- `Authentication authenticate(Authentication authentication)` — see behavior below.
- `boolean supports(Class<?> authentication)` — `UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication)`.

**Behavior (single pass, uniform failure throughout):**
1. Load `UserDetails` via `AccountUserDetailsService.loadUserByUsername(email)`; unknown email → `BadCredentialsException`.
2. Check `userDetails.isEnabled()` / `isAccountNonLocked()` (already correctly computed by `AccountUserDetailsService`, including the elapsed-lockout precedent from T13/R18) → fail uniformly if either is false.
3. Verify password via the existing `PasswordEncoder` bean against `userDetails.getPassword()` → fail uniformly on mismatch.
4. Resolve `UUID accountUuid` from `userDetails.getUsername()`.
5. `RoleService.resolveEffectiveRoles(accountUuid)` — MFA required iff the set contains `"MERCHANT"` or `"ADMIN"` (L10; literal role-name strings, matching how role names are handled everywhere else in this codebase — no enum exists for them).
6. If not required → return `TotpStepUpAuthenticationToken.authenticated(userDetails, userDetails.getAuthorities(), false)` (R27).
7. If required → `MfaService.hasConfirmedTotpEnrollment(accountUuid)`; not confirmed → fail uniformly (R24, no distinguishing signal — governing decision).
8. Confirmed → the submitted `mfaCode` (from `TotpAuthenticationDetails`, via `token.getDetails()`) is dispatched by shape: a bare 6-digit string is treated as a TOTP code (`MfaService.verifyTotpCodeForLogin`), anything else as a recovery code (`MfaService.verifyRecoveryCode`) — both already single-purpose enough not to collide by construction (Base64url recovery codes are far longer than 6 digits). Either method's exception (or a null/blank code) is caught and rewrapped as the same uniform `BadCredentialsException` (R29's audit already happens inside the called method, not here — this provider never audits `mfa.failed` itself, it only translates the exception).
9. Success → return `TotpStepUpAuthenticationToken.authenticated(userDetails, userDetails.getAuthorities(), true)` (R26).

**Private methods:** `loadUserDetailsOrFail`, `verifyAccountUsableOrFail`, `verifyPasswordOrFail`, `isMfaRequired`, `dispatchMfaVerification` (the shape-based TOTP-vs-recovery-code routing), each a thin wrapper ending in the same `BadCredentialsException` on any failure — no branch returns a differently-shaped result.

**Constructor dependencies:** `AccountUserDetailsService`, `PasswordEncoder`, `RoleService`, `MfaService`.

## Files to Modify

### `mfa/TotpVerifier.java` — additive only
- New: `OptionalLong verifyAndReturnStep(byte[] secret, String submittedCode, Instant now)` — same matching logic as the existing `verify(...)`, but returns the matched 30s time-step (`OptionalLong.empty()` if none of the three steps match) instead of a boolean. `verify(...)` itself is unchanged, and can be implemented in terms of the new method (`verifyAndReturnStep(...).isPresent()`) or left untouched — implementer's call, behavior must be identical either way.

### `mfa/MfaEnrollmentRepository.java` — one new method (package-private, unchanged visibility)
- `int recordUseIfNewer(Long id, Instant now, long acceptedStep)` (exact JPQL/native shape left to the implementer) — atomic conditional update mirroring `confirmIfUnconfirmed`/`RecoveryCodeRepository.markUsed`'s established pattern: sets `last_used_at = :now` only if the row's current `last_used_at` corresponds to a step `< acceptedStep` (or is null), returns rows affected. This is the concurrency-safe primitive `verifyTotpCodeForLogin` uses for replay rejection — never a plain read-then-write.

### `mfa/MfaService.java` — two new public methods
- `boolean hasConfirmedTotpEnrollment(UUID accountUuid)` — `@Transactional(readOnly = true)`; delegates to `resolveAccountId` + `mfaEnrollmentRepository.findByAccountIdAndTypeAndConfirmedAtIsNotNull(...).isPresent()`. No audit, no exception on absence — pure predicate.
- `void verifyTotpCodeForLogin(UUID accountUuid, String submittedCode)` — `@Transactional`. Loads the confirmed enrollment (`MfaNotEnrolledException` if absent — defensive only, since callers are expected to have already called `hasConfirmedTotpEnrollment`); decrypts the secret via `mfaSeedEncryption.decrypt(...)`; calls `totpVerifier.verifyAndReturnStep(secret, submittedCode, clock.instant())`; on no match or on `recordUseIfNewer(...)` returning `0` rows (replay), records `mfa.failed` (R29) and throws `InvalidTotpCodeException`; on success, returns normally (the `recordUseIfNewer` call already persisted the new `lastUsedAt`, no separate write needed). No account-status precondition (matches `verifyRecoveryCode`'s existing precedent — the caller, `TotpAuthenticationProvider`, already gated status before either new method is reached).

### `token/TokenClaimsCustomizer.java`
- `customizeAccessToken(JwtEncodingContext context)` — the two hardcoded lines (`"amr", List.of("pwd")` and `"acr", "urn:themistra:acr:pwd"`) become conditional on a new private helper `otpUsed(Authentication principal)` (`principal instanceof TotpStepUpAuthenticationToken t && t.otpUsed()`): true → `amr: ["pwd","otp"]` / `acr: urn:themistra:acr:otp` (R26); false (including today's plain password-only principal, which never becomes a `TotpStepUpAuthenticationToken` when MFA wasn't required per step 6 above — actually it does, with `otpUsed=false`, so this is really just reading the flag off the one token type) → `amr: ["pwd"]` / `acr: urn:themistra:acr:pwd` (R27). No grant-type branching needed: SAS stores and replays the same `Authentication` on refresh, so this logic runs identically and correctly for refresh-token grants (R26/R27 preserved on refresh) with zero extra code.

### `token/SecurityChainsConfig.java`
- In the `applicationChain` bean (where `.formLogin(...)` already lives — `authorizationServerChain` only matches `/oauth2/**` etc. and is unaffected):
  - Inject `TotpAuthenticationProvider` and `TotpAuthenticationDetailsSource`.
  - Build an explicit `AuthenticationManager` — a `ProviderManager` whose sole provider is `TotpAuthenticationProvider` — and set it via `.formLogin(form -> form.authenticationManager(thatManager).authenticationDetailsSource(totpAuthenticationDetailsSource).failureHandler(loginFailureHandler).successHandler(loginSuccessHandler))`.
  - This explicit, scoped `AuthenticationManager` is what satisfies the frozen brief's "no duplicate authentication providers" constraint — it guarantees Spring Boot's autoconfigured `DaoAuthenticationProvider` (which would otherwise be built from `AccountUserDetailsService` + the existing `PasswordEncoder` bean) never gets a chance to handle this form's login attempts, without needing to suppress or exclude that autoconfiguration globally.

## Entities used
`MfaEnrollment` (read + `lastUsedAt` update, via the new repository method — no new column). No other entity touched; `Account`/`RecoveryCode` unchanged.

## Repositories used
`MfaEnrollmentRepository` (one new method, still package-private). `RecoveryCodeRepository` unchanged (reached only through the existing `MfaService.verifyRecoveryCode`).

## Services used
`AccountUserDetailsService`, `RoleService.resolveEffectiveRoles`, `MfaService` (extended), `AuditService` (unchanged, called from inside `MfaService.verifyTotpCodeForLogin` exactly as `verifyRecoveryCode` already calls it for `mfa.failed`).

## Unit tests required
- `TotpAuthenticationProviderTest` (new, plain JUnit, mocked collaborators) — covers every branch in the Behavior list above: unknown email, disabled/locked account, wrong password, MERCHANT/ADMIN unconfirmed, MERCHANT/ADMIN confirmed + right TOTP, + right recovery code, + wrong code, + blank code, USER/COMPLIANCE unconfirmed (password-only success), USER/COMPLIANCE confirmed (MFA still required) — each failure branch asserted to throw the same `BadCredentialsException` type.
- `MfaServiceTest` (extend existing, fixed `Clock` pattern already established) — `hasConfirmedTotpEnrollment` true/false; `verifyTotpCodeForLogin` success, wrong code, and same-step replay-rejected cases; confirms `mfa.failed` audit on both failure kinds.
- `TotpVerifierTest` (extend existing) — `verifyAndReturnStep` returns the correct step on match, `OptionalLong.empty()` on no match, across the existing ±1-step tolerance test cases.
- `TokenClaimsCustomizerTest` (extend existing) — `amr`/`acr` for a `TotpStepUpAuthenticationToken` with `otpUsed=true` vs. `false`, plus the pre-existing `CLIENT_CREDENTIALS` case unaffected.

## Integration tests required (Testcontainers, per `TestcontainersConfiguration`)
Fixtures create accounts/roles/enrollments directly via repositories (no self-service enrollment endpoint exists — task 19 — so tests seed a confirmed `MfaEnrollment` row directly, consistent with the frozen brief's recorded rollout gap).
- `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` (R24) — MERCHANT, no enrollment, correct password → no authorization code.
- `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` (R25) — MERCHANT with confirmed enrollment: correct password alone fails; password + correct TOTP succeeds; password + correct unused recovery code succeeds.
- `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` (R26) — full authorize→token exchange after password+TOTP; asserts `amr`/`acr` on the issued JWT.
- `shouldIssueTokenWithPwdAmrWhenMfaNotRequired` (R27) — USER/COMPLIANCE, password only; asserts `amr: ["pwd"]`/`acr: pwd`.
- Boundary: wrong TOTP/recovery code → no authorization code, `auth_audit` row + Kafka-mirrored `mfa.failed` event present (R29).
- Boundary: same valid TOTP code submitted twice → second attempt rejected (replay, Phase 3 #7).
- Boundary: recovery code accepted once → rejected on a second submission, through the login path (not just `MfaServiceTest`'s unit-level coverage).
- Boundary: `amr`/`acr` preserved across a refresh-token grant following a password+TOTP login (Phase 3 #10).

## Execution order
1. `MfaEnrollmentRepository.recordUseIfNewer` (repository layer first — everything else depends on it).
2. `TotpVerifier.verifyAndReturnStep` (pure utility, no dependencies).
3. `MfaService.hasConfirmedTotpEnrollment` / `verifyTotpCodeForLogin` (depends on 1 and 2) — plus `MfaServiceTest`/`TotpVerifierTest` extensions alongside, per this codebase's established pattern of landing unit tests with the code they cover.
4. `authn/TotpStepUpAuthenticationToken.java` (no dependencies beyond Spring Security base classes).
5. `authn/TotpAuthenticationDetails.java` (no dependencies beyond the servlet API).
6. `authn/TotpAuthenticationProvider.java` (depends on 3, 4, 5, plus existing `AccountUserDetailsService`/`RoleService`) — with `TotpAuthenticationProviderTest` alongside.
7. `token/TokenClaimsCustomizer.java` (depends on 4) — with its test extension alongside.
8. `token/SecurityChainsConfig.java` (depends on 5, 6 — wires everything together; last, since it's the integration point, not a unit with its own tests).
9. Integration tests (Testcontainers) — full login→MFA→token flow, boundary cases, refresh-grant preservation; last, since they exercise the fully wired chain from steps 1–8.
