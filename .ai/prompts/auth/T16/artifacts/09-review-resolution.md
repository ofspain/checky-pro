# auth · T16 — Phase 9: Review Resolution

**Human Approval gate.** Consumes `artifacts/07-self-review.md` and `artifacts/08-independent-review.md`.
Decisions below were made by femi via two rounds of explicit sign-off; this log records each
comment's disposition and the exact change made. No refactoring, optimization, public-API changes,
or renames beyond what each accepted comment required.

---

## 1. Kimi #1 (Critical) — `base.yaml` never wires the non-local guard

**Decision:** Superseded by the resolution of #2 below, not applied literally.
**Reason:** femi chose, for #2, to restore true Spring-profile-based detection rather than keep
the `seed-kek-required` boolean this finding's literal fix (`MFA_SEED_KEK_REQUIRED: "true"` in
`base.yaml`) was written against. With profile detection restored, `deploy/k8s/base.yaml`'s
pre-existing `SPRING_PROFILES_ACTIVE: ${ENVIRONMENT}` (unrelated to T16, already present) is what
makes the guard fire in dev/staging/prod — no new env var exists to add, and `seed-kek-required`
no longer exists as a property. `base.yaml` was not touched. The underlying Critical risk (a
deployed pod silently using the local key) is closed by #2's fix instead.

## 2. Kimi #2 (High) — guard mechanism deviates from ADR-0003's literal "active Spring profile" wording

**Decision:** ACCEPTED — restore profile-based detection (the larger of the two options offered).
**Reason:** femi chose to fix the root cause (no profile is ever active in this repo) rather than
formally amend ADR-0003 to match the workaround.
**Change made:**
- `application.properties`: added `spring.profiles.active=local` as the app-wide default (new —
  previously no default was set anywhere). `SPRING_PROFILES_ACTIVE` from `deploy/k8s/base.yaml`
  still overrides this per environment, so dev/staging/prod are unaffected; this only changes the
  previously-unset default for local dev and for any test that doesn't set profiles explicitly
  (all of them, today).
- `MfaProperties.java`: removed the `seedKekRequired` field entirely.
- `MfaSeedEncryption.java`: constructor now takes `Environment` (in addition to `MfaProperties`);
  the guard and the local-vs-KMS mode switch both use `environment.acceptsProfiles(Profiles.of("local"))`,
  matching ADR-0003's literal wording. Test seam constructor is now 3-arg
  (`MfaProperties, Environment, KmsClient`).
- Re-verified via a scoped `AnnotationConfigApplicationContext` (mfa package only, no
  DataSource/Kafka/Testcontainers) with `MockEnvironment`: local+blank-ARN uses the local key with
  no KMS call; non-local+blank-ARN throws `IllegalStateException` at construction; non-local+ARN
  uses the (faked) KMS path correctly.

## 3. Kimi #3 / self-review #2 (Medium) — malformed/truncated envelopes leak raw JDK exceptions

**Decision:** ACCEPTED.
**Change made:** `MfaSeedEncryption.java` — the version-byte read in `decrypt` now catches
`NullPointerException`/`ArrayIndexOutOfBoundsException` and rethrows `MfaEncryptionException`;
`parseEnvelope` wraps its `ByteBuffer` reads in a try/catch for `BufferUnderflowException` and
`NegativeArraySizeException`, rethrowing the same. Verified: `null`, an empty array, and a
truncated-but-known-version envelope all now throw `MfaEncryptionException` with a descriptive
message instead of an unchecked JDK exception type.

## 4. Kimi #4 (Medium) — `GenerateDataKey` failures not wrapped like `Decrypt` failures

**Decision:** ACCEPTED.
**Change made:** `encryptKms`'s `kmsClient.generateDataKey(...)` call is now wrapped in the same
try/catch pattern `decryptKms` already used, rethrowing `MfaEncryptionException("Failed to
generate MFA seed data key via KMS", e)`. Verified with a fake `KmsClient` that throws on
`generateDataKey`.

## 5. Kimi #5 / self-review #1 (Medium) — no ArchUnit rule enforces the single-class AWS SDK exception

**Decision:** ACCEPTED.
**Change made:** added `only_MfaSeedEncryption_may_use_the_aws_sdk` to
`ArchitectureTest.java`, mirroring the file's existing style
(`noClasses().that().doNotHaveFullyQualifiedName("com.themistra.auth.mfa.MfaSeedEncryption").should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk..")`).
Note: this sandbox's Maven Surefire run reports "Tests run: 0" for the whole `ArchitectureTest`
class (a pre-existing characteristic — the same "0" appeared before this task touched anything,
for all the file's existing rules too, apparently an ArchUnit/JUnit-Platform-engine wiring quirk
local to this environment, not something T16 introduced or can fix in scope). Verified the rule
itself directly instead, using ArchUnit's `ClassFileImporter` API against the real compiled
`com.themistra.auth` classes: it holds cleanly today (only `MfaSeedEncryption` imports the AWS
SDK).

## 6. Kimi #6 / self-review #4 (Low-Medium) — `KmsClient` never closed

**Decision:** ACCEPTED.
**Change made:** `MfaSeedEncryption` now implements `DisposableBean`; `destroy()` closes
`kmsClient` if non-null. Verified via a Spring context test that `ctx.close()` runs `destroy()`
without throwing.

## 7. Kimi #7 / self-review #3 (Low) — zeroing the KMS plaintext data key is only partially effective

**Decision:** REJECTED — no code change.
**Reason:** the zeroing is harmless best-effort hygiene (it does scrub the caller's own array,
even though `SecretKeySpec`'s internal clone survives independently); there is no comment in the
source overclaiming it as a complete guarantee, so nothing is actually "misleading" in the shipped
code. Calling `.destroy()` has inconsistent JDK support across versions and isn't worth the added
complexity for a Low-severity, defense-in-depth-only concern the frozen brief never required.

## 8. Kimi #8 (Low-Medium) — `Decrypt` request unnecessarily pins `keyId`

**Decision:** REJECTED — no code change.
**Reason:** ADR-0003 specifies "the single TOTP-seed CMK ARN" (a direct CMK identity, not a
rotatable alias) plus KMS's own automatic annual key-material rotation under that same CMK ID —
under that model, passing `.keyId(...)` to `Decrypt` is a valid extra integrity check (KMS
verifies the ciphertext was encrypted under the expected CMK) with no operational downside; the
alias-repointing scenario Kimi's finding hypothesizes doesn't apply to a direct CMK ARN. Removing
it would reduce a legitimate defense-in-depth check for no documented benefit.

## 9. Kimi #9 (Low) — local-dev key constant may trigger gitleaks false positives

**Decision:** REJECTED / deferred — no code change.
**Reason:** confirmed via search that no `.gitleaks.toml` (or equivalent) and no CI workflow
referencing gitleaks exist anywhere in this repository today — `agents.md`'s "gitleaks gate in
CI" is aspirational infrastructure that hasn't been built yet. There is no config file to add an
allowlist entry to; creating gitleaks CI infrastructure from scratch is far outside T16's scope.

## 10. Kimi #10 (Low) — `TotpGenerator` doesn't validate `secret`/`accountLabel` inputs

**Decision:** REJECTED — no code change.
**Reason:** the frozen brief (Phase 4, §Constraints) already explicitly settled this at the
human-approval gate: "seed-kek-arn blank-vs-non-blank is the one null/blank-sensitive path... no
other null-argument case is in scope." Revisiting that would be relitigating an already-decided
scope boundary, not applying a new review comment.

## 11. Kimi #11 (Low) — `URLEncoder` is form-encoding, not strict RFC 3986 percent-encoding

**Decision:** ACCEPTED.
**Change made:** `TotpGenerator.buildProvisioningUri` now uses
`org.springframework.web.util.UriUtils.encodePathSegment`/`encodeQueryParam` (already available —
`spring-boot-starter-web` is an existing dependency, no new one added) instead of `URLEncoder`.
The now-unused `urlEncode` private helper was removed. Verified: unreserved characters like `~`
are no longer over-encoded, and RFC-3986-legal path characters (e.g. `@`, `+` in an email-shaped
account label) are correctly left unescaped rather than form-encoded.

## 12. Kimi #12 (Low) — version `0x00` envelopes don't enforce a zero-length wrapped key

**Decision:** ACCEPTED.
**Change made:** `decryptLocal` now asserts `parsed.wrappedKey().length == 0` per ADR-0003's table
(`N = 0` for version `0x00`), throwing `MfaEncryptionException` otherwise. Verified with a
hand-corrupted local-mode envelope carrying a non-zero wrapped-key-length field.

## 13. Self-review #5 (Low, readability) — class Javadoc pointed to an ephemeral pipeline artifact

**Decision:** ACCEPTED — resolved as a side effect of #2's rewrite.
**Change made:** `MfaSeedEncryption`'s class Javadoc no longer contains the "deviates from the
frozen brief... see implementation notes" paragraph, since the profile-based mechanism restored
in #2 is no longer a deviation from anything — the comment now just describes the (compliant)
design directly.

---

## Verification Summary

- `mvn -pl services/auth -am compile`: success.
- `mvn -pl services/auth -am test -Dtest='!*IntegrationTest'`: 313 tests, 5 errors — identical to
  the pre-existing baseline established in Phase 6/7 (`AuthServiceApplicationTests.contextLoads`'s
  known [[docker-testcontainers-handshake-issue]]; `AdminAccountRoleControllerTest`,
  `ReuseDetectingAuthorizationServiceTest`, `TokenClaimsCustomizerTest` ×2 pre-existing
  strict-stubbing/NPE issues unrelated to `mfa`). Zero new failures from any change in this phase.
- Manual verification (reflection + `MockEnvironment` + hand-built fake `KmsClient`, since
  Testcontainers/Docker still blocks any real `@SpringBootTest` in this sandbox) re-confirmed all
  original ACs plus every fix above: local/KMS mode switching, the profile-based guard (both
  branches), malformed-envelope rejection, unsupported-version rejection, the new
  version-0x00-wrapped-key-length check, `generateDataKey` failure wrapping, `destroy()` closing
  the KMS client cleanly, and the corrected RFC-3986 URI encoding.
- The direct ArchUnit `ClassFileImporter` check confirmed the new AWS-SDK-confinement rule holds
  against the real compiled classes.

## Open Questions

None. All comments have an explicit accepted/rejected disposition above; nothing is deferred as
ambiguous.
