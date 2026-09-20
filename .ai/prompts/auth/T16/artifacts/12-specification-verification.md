# auth · T16 — Phase 12: Specification Verification

Consumes all prior artifacts (Phases 0-11). Compares the final implementation and tests against
`requirements.md`, `design.md`, `tasks.md`, and ADR-0003 for this task only.

## Traceability Matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R22** (partial — generation + encryption only; persistence + endpoint are tasks 17/19) | Yes, for the scoped portion | `TotpGenerator.java:31-35` (generate secret), `MfaSeedEncryption.java:101-103` (encrypt) | Yes — `TotpGeneratorTest`, `MfaSeedEncryptionTest` | No (within scope) | No — scope split with 17/19 was frozen at Phase 4 |
| **L6** (RFC 6238: 30s, 6 digits, HMAC-SHA1) | Yes | `TotpGenerator.java:51-54` (`algorithm=SHA1&digits=6&period=30`) | Yes — `buildProvisioningUriCarriesL6Parameters` | No | No |
| **L13** (secrets discipline: local-only defaults refused outside local dev) | Yes | `MfaSeedEncryption.java:75-83` (`validateGuard`), `application.properties:80-81` | Yes — `constructorRefusesToBootInNonLocalProfileWithBlankArn`, `constructorRefusesToBootWithBlankArnWhenNoProfileIsActive` | No | No |
| **L14** (AES-256-GCM, KMS-enveloped data key, confined to `MfaSeedEncryption`) | Yes | `MfaSeedEncryption.java:36` (class), `:142-162` (`encryptKms`), `:164-183` (`decryptKms`) | Yes — `kmsModeProducesAdr0003EnvelopeLayoutAndRoundTrips` + request-content captor assertions | No | No |
| **ADR-0003 byte layout** (version / 2-byte length / wrapped key / 12-byte nonce / ciphertext+16-byte tag) | Yes | `MfaSeedEncryption.java:211-219` (`assembleEnvelope`), `:221-236` (`parseEnvelope`) | Yes — layout asserted byte-by-byte in `kmsModeProducesAdr0003EnvelopeLayoutAndRoundTrips` | No | No |
| **ADR-0003 local-dev fallback** (version `0x00`, local profile + blank ARN only) | Yes | `MfaSeedEncryption.java:52-57` (`LOCAL_DEV_KEY`), `:85-94` (`resolveKmsClient`) | Yes — `localModeRoundTripsAndNeverCallsKms`, `constructorAllowsLocalModeWhenLocalProfileIsPresentAmongOthers` | No | Yes, disclosed — guard uses `Environment.acceptsProfiles("local")` restored at Phase 9 after a Phase 6 boolean-flag detour; see Deviations below |
| **ADR-0003 testing obligations** (round-trip, no raw seed substring, wrong-key fails distinctly) | Yes | throughout `MfaSeedEncryptionTest` | Yes — 6+ dedicated tests including the Phase 11 GCM-authentication-failure case | No | No |
| **AC1** (`SecureRandom`, 20-byte secret) | Yes | `TotpGenerator.java:19,31-35` | Yes | No | No |
| **AC2** (valid `otpauth://` URI, Base32 uppercase/unpadded, RFC 3986) | Yes | `TotpGenerator.java:45-54` | Yes — 7 tests | No | No |
| **AC3** (KMS-mode round-trip, exact envelope, mocked `KmsClient`) | Yes | `MfaSeedEncryption.java:142-183` | Yes | No | No |
| **AC4** (local mode, no KMS call) | Yes | `MfaSeedEncryption.java:88-93` | Yes | No | No |
| **AC5** (non-local + blank ARN fails startup) | Yes | `MfaSeedEncryption.java:75-83` | Yes | No | No |
| **AC6** (no raw-seed substring; wrong-key fails distinctly, not silently) | Yes | envelope construction throughout; GCM tag via `Cipher`/`GCMParameterSpec` | Yes — includes the Phase 11 real-GCM-failure test, not just KMS-rejection | No | No |
| **AC7** (thread-safe, new `Cipher` per call) | Yes | `MfaSeedEncryption.java:191-209` (`gcmEncrypt`/`gcmDecrypt`, new `Cipher.getInstance` per call) | Yes — 100 concurrent round-trips across 16 threads | No | No |
| **AC8** (unsupported version → dedicated exception) | Yes | `MfaSeedEncryption.java:106-118` | Yes | No | No |
| **`agents.md` — AWS SDK confined to one class (D-025)** | Yes | `ArchitectureTest.java:78` (new rule, Phase 9) | Verified directly via ArchUnit `ClassFileImporter` against compiled bytecode (Phase 9); the rule itself reports "0 tests" under this sandbox's Surefire run — a pre-existing, environment-wide ArchUnit reporting quirk unrelated to T16 (affects all of `ArchitectureTest`'s existing rules identically, confirmed before T16 touched anything) | No | No — see Open Risk below |
| **`agents.md` — secrets never logged** | Yes | no raw secret/URI ever passed to a logger anywhere in `mfa/`; the one `log.warn` (`MfaSeedEncryption.java:92-93`) names only the missing config key | No dedicated test (not independently testable without a log-capture harness; judged low-risk and out of proportion to add for this task) | No | No |
| **`package.md` §8 named test** `shouldReturnTotpProvisioningUriOnEnrollmentBegin` | Not applicable to T16 | — | No | Correctly deferred | No — Phase 1/2 established this requires the persistence layer + `POST /accounts/me/mfa/totp` endpoint (tasks 17/19); T16's own statement never claims to satisfy it |

## Files Delivered vs. Frozen Brief

| File | Authorized by | Status |
|---|---|---|
| `mfa/TotpGenerator.java` | Frozen brief, Files to Create | Created |
| `mfa/MfaSeedEncryption.java` | Frozen brief, Files to Create | Created |
| `mfa/MfaProperties.java` | Frozen brief, Files to Create | Created |
| `mfa/MfaEncryptionException.java` | Frozen brief AC8 (dedicated exception), confirmed Phase 5 | Created |
| `application.properties` | Frozen brief, Files to Modify | Modified (2 MFA keys + Phase 9's `spring.profiles.active=local` default) |
| `pom.xml` | Frozen brief, Files to Modify | Modified (AWS SDK BOM 2.50.2 + `kms`) |
| `ArchitectureTest.java` | Not in original Files list; added at Phase 9 to close a review finding (test-only, no production code) | Modified |
| `mfa/TotpGeneratorTest.java`, `mfa/MfaSeedEncryptionTest.java`, `mfa/MfaPropertiesTest.java` | Phase 5 plan (first two) + Phase 11 review finding #10 (third) | Created |

No file outside `mfa/`, `application.properties`, `pom.xml`, and the one test-file exception above was
touched. `mfa_enrollments`/`recovery_codes` schema untouched. No entity, repository, service, or
controller added — matches the frozen brief's Out-of-scope list exactly.

## Deviations (all previously disclosed, not new)

1. **Guard mechanism churn (Phase 6 → Phase 9).** Phase 6 initially implemented the L13/ADR-0003
   non-local guard as a config boolean (`seedKekRequired`) instead of literal
   `Environment.acceptsProfiles("local")`, because no test or local-dev setup in this repository
   activated the `local` profile anywhere. Phase 9 (human-approved) fixed the root cause instead:
   restored true profile-based detection and added `spring.profiles.active=local` as
   `application.properties`'s new app-wide default (overridden by `SPRING_PROFILES_ACTIVE` in
   `deploy/k8s/base.yaml` for every deployed environment, pre-existing and unrelated to T16). Final
   state matches ADR-0003's literal wording; no outstanding deviation.
2. **`MfaEncryptionException` not in the frozen brief's original Files-to-Create list.** Authorized
   by AC8's "dedicated exception" requirement, confirmed explicitly at Phase 5.
3. **`ArchitectureTest.java` modified.** Not in the frozen brief's file list at all; added at Phase
   9 to close a Kimi/self-review finding (D-025's AWS-SDK confinement had no CI-enforced rule).
   Test-only, additive, no behavior change to any file the brief did authorize.

None of these were applied silently — each is documented in its originating phase's artifact
(`06-implementation-notes.md`, `09-review-resolution.md`) and cross-referenced here.

## Open Risk (non-blocking)

- **ArchUnit rule execution in this sandbox.** `mvn test` reports "Tests run: 0" for the whole
  `ArchitectureTest` class under this environment's Maven Surefire setup — true for every rule in
  that file, before and after T16, not something this task caused or can fix in scope (looks like
  a JUnit-Platform-engine wiring quirk local to this sandbox). The new AWS-SDK-confinement rule was
  independently verified correct via ArchUnit's `ClassFileImporter` API against the real compiled
  classes (Phase 9), so the logic is sound — but CI in the real environment should be checked to
  confirm `ArchitectureTest`'s rules actually execute there. Out of scope to investigate further
  under T16.
- **`spec/auth-service/package.md` §8 traceability numbering.** A pre-existing, unrelated
  inconsistency noted at Phase 4: `shouldReturnTotpProvisioningUriOnEnrollmentBegin` is mapped to
  R19 there, but `requirements.md`'s R19 is the lockout-decay requirement — R22 is the real match.
  Not fixed (spec files off-limits, out of scope), flagged for separate follow-up.
- **`deploy/k8s/base.yaml`.** No change needed for T16's final (profile-based) guard design — it
  already sets `SPRING_PROFILES_ACTIVE` per environment, which is sufficient. (The earlier
  Phase 6 boolean-flag design would have needed a `base.yaml` edit; that need disappeared when
  Phase 9 reverted to profile-based detection.)

## Principal-Engineer Sign-off

1. **Is the task fully complete?** Yes, for T16's own scope as frozen at Phase 4:
   `TotpGenerator` and `MfaSeedEncryption` are both implemented, tested, and match ADR-0003
   exactly. R22 itself is only partially satisfied by design — persistence and the endpoint belong
   to tasks 17 and 19, which this task explicitly does not touch.
2. **Does it satisfy every acceptance criterion?** Yes — AC1 through AC8 all have both a code
   path and a passing test, verified above with file:line evidence.
3. **Does it violate any LOCKED decision?** No. L6, L13, and L14 are all satisfied as specified;
   the one implementation-mechanism deviation (profile detection, Phase 6→9) was corrected before
   this phase, not left outstanding.
4. **Remaining risks?** Two non-blocking items noted above (ArchUnit execution visibility in this
   sandbox; the pre-existing `package.md` numbering bug) — neither affects T16's own correctness or
   blocks merge. `mvn -pl services/auth -am test -Dtest='!*IntegrationTest'`: 349 tests, 5 errors,
   all 5 confirmed pre-existing and unrelated to `mfa` (the sandbox's Docker/Testcontainers
   limitation plus unrelated Mockito/NPE issues in `token`/`authz` tests) — zero failures
   attributable to T16.

**VERDICT: PASS** — T16 is complete, spec-compliant, and fully tested within its frozen scope;
no LOCKED decision is violated and no regression was introduced.
