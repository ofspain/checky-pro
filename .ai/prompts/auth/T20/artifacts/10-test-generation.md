# auth · T20 · Phase 10 — Test Generation (Manifest)

No production code changed this phase. `mvn -pl services/auth -am test-compile` is clean. All non-Docker tests pass (420 run, 0 failures, 2 pre-existing errors unrelated to T20 and reproduced identically on a clean pre-T20 stash — `AdminAccountRoleControllerTest.effectiveRolesDelegatesDirectly` and `ReuseDetectingAuthorizationServiceTest.saveTracksRotationWhenFamilyAlreadyExists`, both `UnnecessaryStubbingException`). Testcontainers-backed tests (Postgres/Kafka) could not be executed in this environment — no Docker daemon available, the same limitation every prior Testcontainers test in this pipeline has flagged; they are written and test-compile cleanly but are unverified by an actual run.

## Necessary fix to a pre-existing test bug (not production code)

`token/TokenClaimsCustomizerTest.java` had a latent bug independent of T20: `private final TokenClaimsCustomizer customizer = new TokenClaimsCustomizer(roleService)` was a field initializer, which JUnit 5 evaluates during test-instance construction — before `MockitoExtension` injects `@Mock` fields via its `TestInstancePostProcessor` callback. `customizer` therefore permanently held a `null` `RoleService`, NPE'ing on every test that reached the roles/amr/acr code path. Reproduced identically on a clean pre-T20 stash (confirmed pre-existing, flagged but deliberately left alone in Phase 6/7). Fixed here because it directly blocked writing this phase's own mandated tests — every interactive-grant test in this file, including the required `shouldIssueTokenWithOtpAmrAndAcrAfterMfa`, hits the same code path. Fix: moved construction into `@BeforeEach`, field no longer `final`.

## Layered testing strategy for R26/R27 — and why no full OAuth2-flow test was written

`auth-decisions.md` D-023 already established, for this exact category of test, that "a full MockMvc-driven OAuth2 Authorization Code + PKCE flow test against Spring Authorization Server's actual `/oauth2/authorize` → `/login` → `/oauth2/token` sequence... requires exact knowledge of SAS's session/CSRF/redirect behavior... which cannot be verified without running it... Writing speculative flow-orchestration code that looks thorough but is unverified would be worse than not writing it." That reasoning applies exactly as much to T20. Rather than chase the full flow to inspect an actually-issued JWT's `amr`/`acr`, R26/R27 are verified at the layer that's actually responsible for them — `TokenClaimsCustomizer` — directly and deterministically, the same engineering discipline the rest of this codebase already applies. `SasLoginIntegrationTest`'s new tests go exactly as far as the existing, already-Phase-11-hardened pattern already goes: through `/login`, never into `/oauth2/authorize`/`/oauth2/token`.

## Test manifest

### `authn/TotpAuthenticationProviderTest.java` (new — unit, plain JUnit + Mockito, no Spring context)
| Test | Verifies |
|---|---|
| `unknownEmailFailsUniformly` | Uniform failure boundary |
| `disabledAccountFailsUniformlyBeforePasswordIsChecked` | Account-usable gate ordering |
| `lockedAccountFailsUniformlyBeforePasswordIsChecked` | Account-usable gate ordering |
| `wrongPasswordFailsUniformlyBeforeMfaIsChecked` | R29-adjacent boundary; MFA never consulted on password failure |
| `correctPasswordNoEnrollmentAndNoMandatoryRoleSucceedsPasswordOnly` | R27 |
| `merchantWithoutEnrollmentIsBlocked` | R24, L10 |
| `adminWithoutEnrollmentIsBlocked` | R24, L10 (both mandatory roles) |
| `voluntarilyEnrolledUserAccountStillRequiresMfa` | R25; regression test for Phase 8 finding #2 |
| `voluntarilyEnrolledUserAccountCanUseARecoveryCode` | R25 recovery-code branch of the same fix |
| `sixDigitCodeIsDispatchedAsTotp` | R25 shape-based dispatch |
| `wrongTotpCodeFailsUniformly` | R29 |
| `blankMfaCodeFailsUniformlyWithoutCallingMfaService` | Boundary — nothing to verify |
| `unexpectedMfaServiceFailureStillFailsUniformly` | Regression test for Phase 7/8 finding #4 |
| `resultCarriesWebDetailsForwardButNeverTheRawMfaCode` | Regression test for Phase 8 finding #7, as narrowed during resolution |
| `supportsOnlyUsernamePasswordAuthenticationToken` | `AuthenticationProvider` contract |

### `token/TokenClaimsCustomizerTest.java` (extended)
| Test | Verifies |
|---|---|
| `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` | **Named test**, R26 |
| `otpUsedIsPreservedOnARefreshTokenGrant` | R26/R27 on refresh; Phase 3/4 finding #10's resolution |
| `otpUsedAuthorityMatchesAnEquivalentButDistinctInstance` | Sanity check that the marker is compared by value, matching how a Jackson round-trip would hand it back |
| `interactiveTokenGetsRolesAmrAcrAndEmailVerified_noEmailOrName` (pre-existing, now passing) | **Doubles as `shouldIssueTokenWithPwdAmrWhenMfaNotRequired` (R27)** — present in `package.md` §8 but absent from this task's header; already asserted the exact R27 shape, just needed the Mockito fix to run at all |
| `clientCredentialsTokenGetsClientAmrOnlyAndNeverConsultsRoleService` (pre-existing, unaffected) | `CLIENT_CREDENTIALS` grant untouched by the marker-authority logic |

### `mfa/MfaServiceTest.java` (extended)
| Test | Verifies |
|---|---|
| `hasConfirmedTotpEnrollmentReturnsTrueWhenConfirmedEnrollmentExists` / `...ReturnsFalseWhenNoneOrUnconfirmed` | R24 gate's data source |
| `verifyTotpCodeForLoginThrowsWhenNoConfirmedEnrollment` | Defensive precondition |
| `verifyTotpCodeForLoginSucceedsWhenStepMatchesAndIsRecordedAsNewlyUsed` | R25 happy path |
| `verifyTotpCodeForLoginThrowsAndAuditsWhenNoStepMatches` | R29 |
| `verifyTotpCodeForLoginRejectsAndAuditsWhenStepAlreadyUsed` | R29; regression test for Phase 8 finding #3 at the value-computation level — proves `MfaService` passes the step-*start* instant to the repository, not a wall-clock value (exactly where the original bug lived) |

### `mfa/TotpVerifierTest.java` (extended)
| Test | Verifies |
|---|---|
| `verifyAndReturnStepReturnsTheMatchedStepForKnownVectors` | New overload against RFC 6238 known-answer vectors |
| `verifyAndReturnStepReturnsEmptyForNoMatch` | Boundary |
| `verifyAndReturnStepReportsTheMatchedStepNotTheCurrentOne` | Documents/proves the exact semantic (matched step ≠ "now"'s step) that made the original finding #3 bug possible to reason about |
| `stepStartReturnsTheEpochAlignedInstantForAStep` | New helper method |

### `mfa/MfaPersistenceIntegrationTest.java` (extended — Testcontainers, unexecuted here)
| Test | Verifies |
|---|---|
| `recordUseIfNewerAcceptsALaterStepButRejectsAReplayOfTheSameStep` | Regression test for Phase 8 finding #3 against the **real SQL** — deterministic (arbitrary `Instant`s, no wall-clock waiting needed now that the method takes only a step-start value) |

### `mfa/MfaServicePersistenceIntegrationTest.java` (extended — Testcontainers, unexecuted here, inherits the class's own pre-existing "not expected to run green today" caveat re: a documented, out-of-scope Hibernate defect)
| Test | Verifies |
|---|---|
| `verifyTotpCodeForLoginAcceptsOnceThenRejectsAnImmediateReplay` | R25/R29 against a real DB |
| `hasConfirmedTotpEnrollmentReflectsRealPersistedState` | R24's data source against a real DB, both states |

### `authn/SasLoginIntegrationTest.java` (extended — Testcontainers, unexecuted here; existing, Phase-11-hardened file, discovered late — see class Javadoc note)
| Test | Verifies |
|---|---|
| `merchantWithoutMfaEnrollmentCannotLogIn` | **Named test**, R24 |
| `merchantWithConfirmedEnrollmentRequiresCorrectTotpOrRecoveryCode` | **Named test**, R25 (password-only fails, wrong code fails, correct TOTP succeeds) |
| `merchantCanLoginWithAnUnusedRecoveryCode` | R25 recovery-code branch |
| `voluntarilyEnrolledUserAccountStillRequiresMfaAtLogin` | Regression test for Phase 8 finding #2, full stack |
| `sameValidTotpCodeCannotBeUsedTwice` | Regression test for Phase 8 finding #3, full stack |

## Acceptance-criteria coverage
| ID | Covered by |
|---|---|
| R24 | `TotpAuthenticationProviderTest` (unit), `MfaServiceTest`/`MfaServicePersistenceIntegrationTest` (data source), `SasLoginIntegrationTest.merchantWithoutMfaEnrollmentCannotLogIn` (named test, full stack) |
| R25 | `TotpAuthenticationProviderTest`, `MfaServiceTest`, `SasLoginIntegrationTest.merchantWithConfirmedEnrollmentRequiresCorrectTotpOrRecoveryCode` (named test) + `merchantCanLoginWithAnUnusedRecoveryCode` + the finding-#2 regression test |
| R26 | `TokenClaimsCustomizerTest.shouldIssueTokenWithOtpAmrAndAcrAfterMfa` (named test) + refresh-preservation test |
| R27 | `TokenClaimsCustomizerTest.interactiveTokenGetsRolesAmrAcrAndEmailVerified_noEmailOrName` (present in `package.md` §8 as `shouldIssueTokenWithPwdAmrWhenMfaNotRequired`, not in this task's header — included per the running Phase 1/5/9 decision to treat it as required) |
| R29 | `TotpAuthenticationProviderTest.wrongTotpCodeFailsUniformly`, `MfaServiceTest`'s failure-path tests, `MfaServicePersistenceIntegrationTest`, `SasLoginIntegrationTest`'s wrong-code assertion |

## Open Questions
None. The one thing worth the human's attention isn't a question but a status: none of the Testcontainers-backed tests written this phase (`MfaPersistenceIntegrationTest`, `MfaServicePersistenceIntegrationTest`, `SasLoginIntegrationTest`) have been executed against real Postgres/Kafka in this environment. They should be run in a Docker-available environment before this task is considered verified — particularly `SasLoginIntegrationTest`'s new cases, since they're the only tests exercising the real Spring Security filter chain, CSRF handling, and `HttpSecurity.authenticationProvider(...)` wiring end-to-end, which is exactly the category of risk Phase 7 finding #1 raised about the SAS/Jackson persistence layer this task depends on.

---

## Addendum — Phase 11 (Kimi test review) response

All 8 gaps Kimi found were accepted and implemented. `mvn -pl services/auth -am test-compile` clean; all non-Docker tests pass (411 run — `AdminAccountRoleControllerTest`/`ReuseDetectingAuthorizationServiceTest` excluded from this count, their 2 pre-existing failures already verified unrelated to T20 in Phase 10's main run).

| Finding | Disposition |
|---|---|
| #1 — no assertion that a failed MFA login persists `mfa.failed` | Added to `merchantWithConfirmedEnrollmentRequiresCorrectTotpOrRecoveryCode` |
| #2 — no explicit full-stack test for password-only login with no role/enrollment | New test `userWithoutEnrollmentLogsInWithPasswordOnly`; noted this behavior was already implied by existing tests using no-role accounts, so this closes a naming/intent gap more than a behavioral one |
| #3 — recovery-code single-use not tested end-to-end | `merchantCanLoginWithAnUnusedRecoveryCode` renamed to `...ButNotWithItASecondTime` and extended with a reuse attempt |
| #4 — `.strip()` fix unexercised | **Retargeted, not applied as literally suggested**: Kimi's suggested location (`TotpAuthenticationProviderTest`) doesn't actually exercise the fix — trimming happens in `TotpAuthenticationDetailsSource`, a different class the provider never re-processes. New file `TotpAuthenticationDetailsSourceTest.java` tests the actual responsible unit directly (also covers the `toString()` redaction, Phase 8 finding #5's other half, which had no direct test either) |
| #5 — unexpected-failure log line unasserted | `unexpectedMfaServiceFailureStillFailsUniformly` → `...AndLogsAWarning`, using `CapturedOutput`/`OutputCaptureExtension` (already available via `spring-boot-starter-test`; Phase 10 had skipped this reasoning it required new infrastructure — it didn't) |
| #6 — `roles` claim not asserted to exclude the marker authority | Added assertion to `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` |
| #7 — inverse replay direction (earlier step after a later one) untested | Extended `recordUseIfNewerAcceptsALaterStepButRejectsAReplayOfTheSameStep` with a fourth call |
| #8 — multiple live `Instant.now()` calls risk step-boundary flakiness | Added `attemptLoginWithFreshTotpCode(email, password, secret)` helper that generates-and-submits atomically, replacing the three call sites that previously separated code generation from submission; the deliberate same-code-reused-twice replay test is untouched, since reuse is its entire point |
