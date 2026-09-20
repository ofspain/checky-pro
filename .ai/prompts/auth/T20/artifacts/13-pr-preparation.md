# auth · T20 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to prepare for merge.

## Commit title
`auth: enforce TOTP/recovery-code MFA in the SAS interactive login (T20)`

## Commit message

```
Enforce TOTP/recovery-code MFA in the SAS interactive login (T20)

Customize the SAS interactive authentication chain so that, after
password success, MERCHANT/ADMIN accounts without confirmed TOTP
enrollment are blocked from receiving an authorization code, and any
account with a confirmed enrollment (regardless of role) must present
a valid TOTP code or unused recovery code before one is issued.
Accounts for which MFA isn't required proceed on password alone.

TotpAuthenticationProvider replaces DaoAuthenticationProvider for the
login form, verifying password and the conditional MFA step together
in one request (no second page, no partial-auth session state). Every
failure mode - wrong password, wrong/missing MFA code, or a mandatory
role without enrollment - produces the identical uniform response, by
design: a distinct "please enroll" signal would work as a
password-correctness oracle once reachable only after a correct
password, so R24's enrollment requirement is enforced as a hard block
with no in-band signal, not a guided completion flow.

The MFA outcome rides forward as a synthetic "OTP_VERIFIED" granted
authority on a plain UsernamePasswordAuthenticationToken rather than a
custom Authentication subclass, so it survives
JdbcOAuth2AuthorizationService's Jackson-based persistence between
/login and /oauth2/token - TokenClaimsCustomizer reads it to emit
amr/acr correctly for both the initial token and any subsequent
refresh. TOTP replay resistance is implemented (not just
accepted as a known gap): a code's time-step is compared against the
enrollment's last-accepted step via an atomic conditional update,
closing a gap T18 explicitly deferred to this task.

Went through this repo's full spec-driven pipeline, including two
Kimi adversarial review rounds. Findings worth calling out beyond the
diff itself:
- The original design carried the MFA outcome on a hand-rolled
  Authentication subclass; independent review caught that it likely
  wouldn't survive SAS's Jackson-based authorization persistence, so
  the design was changed before merge rather than shipped unverified.
- Independent review also caught that the MFA gate was checking role
  before enrollment, silently skipping the MFA requirement for
  voluntarily-enrolled USER/COMPLIANCE accounts; fixed so enrollment
  status alone triggers the check, independent of role.
- A TOTP replay-guard fix initially stored wall-clock time instead of
  the matched step's start instant, which could reject a legitimate
  next-step code as a false replay under ordinary network latency;
  caught and fixed, with a deterministic regression test against the
  real repository query (no wall-clock waiting required).

Known gap at merge time: the Testcontainers-backed integration tests
this task added or extended (SasLoginIntegrationTest,
MfaPersistenceIntegrationTest, MfaServicePersistenceIntegrationTest)
have not been executed in this environment - no Docker daemon was
available throughout this task. They are written and compile cleanly;
run them in CI/a Docker-available environment before treating this as
fully verified, particularly for the SAS/Jackson persistence mechanism
amr/acr depends on.

Spec: spec/auth-service/tasks.md task 20. Requirements R24-R27, R29.
Locked decisions L5 (extended), L6, L9, L10, L12.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production (`services/auth/src/main/java/com/themistra/auth/`):**
- `authn/TotpAuthenticationProvider.java` — new
- `authn/TotpAuthenticationDetailsSource.java` — new
- `mfa/MfaService.java` — modified (`hasConfirmedTotpEnrollment`, `verifyTotpCodeForLogin`)
- `mfa/MfaEnrollmentRepository.java` — modified (`recordUseIfNewer`)
- `mfa/TotpVerifier.java` — modified, additive only (`verifyAndReturnStep`, `stepStart`)
- `token/SecurityChainsConfig.java` — modified (wires the new provider)
- `token/TokenClaimsCustomizer.java` — modified (conditional `amr`/`acr`)

**Tests:**
- `authn/TotpAuthenticationProviderTest.java` — new
- `authn/TotpAuthenticationDetailsSourceTest.java` — new
- `authn/SasLoginIntegrationTest.java` — extended (existing file, discovered mid-pipeline; see its class Javadoc)
- `mfa/MfaPersistenceIntegrationTest.java` — extended
- `mfa/MfaServicePersistenceIntegrationTest.java` — extended
- `mfa/MfaServiceTest.java` — extended
- `mfa/TotpVerifierTest.java` — extended
- `token/TokenClaimsCustomizerTest.java` — extended (also fixed a pre-existing, unrelated Mockito field-initializer bug that blocked this task's own required tests from running)

**Process record:** `.ai/prompts/auth/T20/artifacts/00-…-12-*.md` (this task's full spec-driven pipeline trail).

Net: 6 new files, 9 modified files, ~1,050 lines added across production + test code.

## Summary

Implements task 20 end-to-end: the SAS login form now runs password and MFA verification together in a single request via a new `AuthenticationProvider`, blocking MERCHANT/ADMIN accounts without confirmed TOTP enrollment, requiring a valid TOTP or recovery code from any account that has enrolled (regardless of role), and correctly reflecting the outcome in the issued token's `amr`/`acr` claims — including across refresh. No new HTTP endpoint, no schema change, no contract file needed for this task's scope.

## Testing performed

- `mvn -pl services/auth -am compile` and `test-compile`: clean.
- Full non-Docker test suite: **424 tests run, 422 passing.** The 2 errors (`AdminAccountRoleControllerTest.effectiveRolesDelegatesDirectly`, `ReuseDetectingAuthorizationServiceTest.saveTracksRotationWhenFamilyAlreadyExists`, both `UnnecessaryStubbingException`) are pre-existing and unrelated to this task — reproduced identically on a clean pre-T20 stash before concluding that.
- `ArchitectureTest`: clean, no new module-boundary violations.
- **Not executed** (no Docker daemon available in this environment, an inherited limitation of every Testcontainers test in this pipeline): `SasLoginIntegrationTest` (including all of this task's new cases), `MfaPersistenceIntegrationTest`, `MfaServicePersistenceIntegrationTest`. All three test-compile cleanly. `MfaServicePersistenceIntegrationTest` additionally inherits a pre-existing, documented "not expected to run green today" caveat from an unrelated T18-era Hibernate defect.
- Two full Kimi adversarial review rounds (Phase 3 design-challenge, Phase 8 independent code review, Phase 11 test review) — all accepted findings resolved with fixes and regression tests; findings verified against source rather than taken at face value at every stage.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 20 — "SAS MFA step integration."
- **Requirements:** R24, R25, R26, R27 (scoped), plus R29 (pulled in as R25's failure-path counterpart).
- **LOCKED decisions:** L10 (scoped); L5, L6, L9, L12 individually verified as consumed/extended, not violated (Phase 12 traceability matrix).
