# auth · T20 · Phase 6 — Implementation Notes

## Summary
Implemented the SAS MFA step exactly as scoped in the frozen brief (Phase 4) / plan (Phase 5): a single `TotpAuthenticationProvider` replaces `DaoAuthenticationProvider` for the login form, verifying password and, when the account holds `MERCHANT`/`ADMIN`, a TOTP or recovery code, in one request — no second page, no partial-auth session state. Every failure mode is uniformly a `BadCredentialsException`. `TokenClaimsCustomizer` now emits `amr`/`acr` conditionally on the MFA outcome. TOTP replay resistance is implemented, not deferred further.

`mvn -pl services/auth -am compile` and `test-compile` both succeed. `ArchitectureTest`, `MfaServiceTest`, `TotpVerifierTest` all pass (33/33). Testcontainers integration tests weren't run — Docker isn't available in this environment — and no new tests were written here per the phase's own instruction (Phase 10).

## Files created

**`authn/TotpStepUpAuthenticationToken.java`** — plan §Files-to-Create item 1, unchanged from plan. `AbstractAuthenticationToken` carrying the account's `UserDetails` as principal (so `getName()` resolves identically to today's password-only flow) and an `otpUsed` fact.

**`authn/TotpAuthenticationDetailsSource.java`** — plan's pre-authorized "one small additional file," **named differently than the plan's placeholder** (`TotpAuthenticationDetails.java`). Java allows one public top-level type per file; the `AuthenticationDetailsSource` implementation and its `TotpAuthenticationDetails` record both need to be public and importable from `token` (for `SecurityChainsConfig`) and `authn` (for the provider), so the record became a public static nested type of the source class, and the file is named after that outer, actually-referenced type. Functionally identical to what the plan described — flagging only the name, not a behavior change.

**`authn/TotpAuthenticationProvider.java`** — plan §Files-to-Create item 3 (originally item under "authn/TotpAuthenticationProvider.java" in the frozen brief). Implements the full behavior sequence from the plan: load `UserDetails` → account-usable check → password check → role check → (if MFA required) confirmed-enrollment check → code verification, dispatched by shape (6 digits → `MfaService.verifyTotpCodeForLogin`, else → `MfaService.verifyRecoveryCode`) → uniform `BadCredentialsException` on any failure, same message throughout (maps directly to R24/R25/R29 and the governing uniformity decision from Phase 4).

## Files modified

**`mfa/TotpVerifier.java`** — additive only, as authorized. `verify(...)` now delegates to a new `verifyAndReturnStep(...)` (returns `OptionalLong`, same constant-time-total-evaluation property — every candidate step is still checked before returning, so timing doesn't leak which step matched). Added `stepStart(long step)` so callers can persist/compare a step as an `Instant` without knowing the step length — this is what let `MfaEnrollmentRepository`'s new query stay expressed purely in `Instant` terms rather than duplicating the 30-second constant elsewhere.

**`mfa/MfaEnrollmentRepository.java`** — one new method, `recordUseIfNewer(Long id, Instant now, Instant acceptedStepStart)`: atomic conditional `UPDATE`, mirrors `confirmIfUnconfirmed`'s shape exactly (0/1 rows affected, `WHERE lastUsedAt IS NULL OR lastUsedAt < :acceptedStepStart`). This is the replay guard — a resubmission of an already-accepted step's code updates 0 rows and is rejected.

**`mfa/MfaService.java`** — two new methods, per the frozen brief's locked contracts:
- `hasConfirmedTotpEnrollment(UUID)` — pure read, no audit, matches plan.
- `verifyTotpCodeForLogin(UUID, String)` — decrypts the enrollment's secret, calls `TotpVerifier.verifyAndReturnStep`, then `recordUseIfNewer`; treats "no step matched" and "step matched but already used" identically — both record `mfa.failed` and throw `InvalidTotpCodeException`. No account-status precondition, matching `verifyRecoveryCode`'s existing precedent, as locked in Phase 4 (#9).

**`token/TokenClaimsCustomizer.java`** — the two previously-hardcoded `amr`/`acr` lines are now conditional on `context.getPrincipal() instanceof TotpStepUpAuthenticationToken totp && totp.otpUsed()`. No grant-type branching was added or needed: as the frozen brief predicted (#10), this reads correctly on a refresh-token grant with zero extra code, because SAS replays the same stored `Authentication`.

**`token/SecurityChainsConfig.java`** — one deviation from the plan/frozen brief's literal wording, forced by reality:

> **Deviation, flagged:** the frozen brief's Constraints section and the plan both specified wiring `TotpAuthenticationProvider` via `.formLogin(form -> form.authenticationManager(...))`. That method **does not exist** on `FormLoginConfigurer`/`AbstractAuthenticationFilterConfigurer` in the Spring Security version this project pins (6.5.2) — confirmed by decompiling the actual class (`javap` on the jar in `~/.m2`), not just a compile error taken at face value. The available, idiomatic equivalent is `HttpSecurity.authenticationProvider(AuthenticationProvider)`, called directly on `http` (not through the `formLogin` configurer): registering a provider on `HttpSecurity` directly makes Spring Security build *this chain's* `AuthenticationManager` from only the locally-registered provider(s), instead of falling back to the global one `AuthenticationConfiguration` would otherwise assemble from every `UserDetailsService`/`AuthenticationProvider` bean in the context (which would include an autoconfigured `DaoAuthenticationProvider` for `AccountUserDetailsService` + the existing `PasswordEncoder` bean). This achieves exactly the frozen brief's "no duplicate authentication providers" constraint, via a different, actually-available API. `.authenticationDetailsSource(totpAuthenticationDetailsSource)` on `.formLogin(...)` was unaffected — that method does exist and was used as planned. The resource-server JWT filter (`.oauth2ResourceServer(...)`) uses its own dedicated manager and is unaffected by this local provider registration, confirmed by `ArchitectureTest`/`MfaServiceTest`/`TotpVerifierTest` still passing (33/33) and a clean `test-compile`.

## Mapping to acceptance criteria
- **R24** — `TotpAuthenticationProvider.requireConfirmedEnrollmentOrFail`, gated behind `isMfaRequired` (MERCHANT/ADMIN via `RoleService`).
- **R25** — `verifyMfaCodeOrFail`, dispatching to `MfaService.verifyTotpCodeForLogin` or `verifyRecoveryCode`.
- **R26/R27** — `TokenClaimsCustomizer`'s conditional `amr`/`acr`, sourced from `TotpStepUpAuthenticationToken.otpUsed()`.
- **R29** — `mfa.failed` audit recorded inside `MfaService.verifyTotpCodeForLogin` (mirroring the existing `verifyRecoveryCode` behavior) on any code-verification failure, including replay.

## Other observations (not fixed — out of scope)
While running the existing suite to sanity-check my changes, `TokenClaimsCustomizerTest` showed 2 failing tests (`interactiveTokenGetsRolesAmrAcrAndEmailVerified_noEmailOrName`, `emailVerifiedFalseWhenEmailScopeNotAuthorized`) with an NPE on `roleService`. I verified this is **pre-existing and unrelated to T20** — reproduced identically on a clean stash of the pre-T20 tree. Root cause: the test's `customizer` field is initialized inline (`private final TokenClaimsCustomizer customizer = new TokenClaimsCustomizer(roleService)`), which runs during test-instance construction, before `MockitoExtension` injects the `@Mock RoleService roleService` field — so `customizer` permanently holds a `null` `RoleService`. Not touched here per "no unrelated refactoring" — flagging for whoever picks up test maintenance, since Phase 10 will likely extend this same file and hit the same NPE on any new case that exercises the `roles`/`amr`/`acr` path.

## Open Questions
None new. The frozen brief's accepted trade-off (no in-band "please enroll" signal; self-service enrollment remains task 19's job) stands as implemented — `TotpAuthenticationProvider`'s `requireConfirmedEnrollmentOrFail` produces the same `BadCredentialsException` as every other failure path, with no distinguishing detail.
