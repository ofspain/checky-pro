# auth · T16 — Phase 6: Implementation Notes

Consumes `artifacts/05-implementation-plan.md`. All code changes described below are committed to
the working tree, not yet to git (per standing session rhythm — commit only on explicit request).

## What Changed

- **`services/auth/pom.xml`** — added a `<dependencyManagement>` block importing
  `software.amazon.awssdk:bom:2.50.2` (confirmed as the latest stable release against Maven
  Central's `maven-metadata.xml` at implementation time, per the frozen brief's deferral) and a
  `software.amazon.awssdk:kms` dependency (BOM-managed, no explicit version).
- **`services/auth/src/main/resources/application.properties`** — added
  `themistra.auth.mfa.issuer-name`, `themistra.auth.mfa.seed-kek-arn`, and
  `themistra.auth.mfa.seed-kek-required` (the last one is new relative to the plan — see
  Deviations below).
- **`MfaProperties.java`** (new) — `@ConfigurationProperties(prefix = "themistra.auth.mfa")`
  record: `issuerName` (`@NotBlank`), `seedKekArn` (unvalidated — blank is legal), `seedKekRequired`
  (boolean, new field).
- **`TotpGenerator.java`** (new) — `generateSecret()` (20 random bytes via `SecureRandom`) and
  `buildProvisioningUri(byte[], String accountLabel)` (hand-rolled unpadded uppercase Base32 +
  RFC-3986-encoded `otpauth://` URI). No AWS import, per ADR-0003/D-025's single-class exception.
- **`MfaSeedEncryption.java`** (new) — implements ADR-0003's envelope format exactly. Two
  constructors: a public one-arg (`MfaProperties`) that Spring autowires, and a package-private
  two-arg (`MfaProperties`, `KmsClient`) test seam for injecting a mocked/faked client. `encrypt`/
  `decrypt` branch on whether a real `KmsClient` was resolved (i.e. whether `seedKekArn` is blank),
  not on Spring profile — see Deviations.
- **`MfaEncryptionException.java`** (new) — thrown on an unsupported envelope version byte or a
  failed KMS `Decrypt` call, matching the codebase's one-class-per-exception convention
  (`RoleNotFoundException`, `AccountNotFoundException`, etc.). Unmapped by any `ExceptionHandler`
  for now — no `mfa` API exists yet (tasks 17-19).

No entity, repository, controller, or persistence code touched, matching the frozen brief's Out
scope. `MfaEncryptionException` was not itemized in the frozen brief's Files-to-Create list but is
authorized by AC8 ("a dedicated exception") — same disposition Phase 5 already reasoned through.

## Mapping to Acceptance Criteria

| AC | Where | Verified how |
|---|---|---|
| AC1 | `TotpGenerator.generateSecret` | Manual smoke check: 20-byte output, `SecureRandom` |
| AC2 | `TotpGenerator.buildProvisioningUri` | Manual smoke check: `otpauth://totp/Themistra:user%40example.com?secret=<32-char [A-Z2-7]>&issuer=Themistra&algorithm=SHA1&digits=6&period=30` |
| AC3 | `MfaSeedEncryption.encrypt`/`decrypt` (KMS mode) | Manual smoke check with a hand-rolled fake `KmsClient` (dynamic proxy, not Mockito — Phase 6 doesn't write the real test suite): version-`0x01` envelope, correct field layout, round-trips |
| AC4 | `MfaSeedEncryption` local mode | Manual smoke check: blank ARN + `seedKekRequired=false` → version-`0x00` envelope, no `KmsClient` built at all |
| AC5 | Constructor guard | Manual smoke check: `seedKekRequired=true` + blank ARN → `IllegalStateException` at construction, before any encrypt/decrypt |
| AC6 | Ciphertext / wrong-key behavior | Manual smoke check: envelope bytes never contain the raw secret as a substring; a fake "wrong key" `Decrypt` throws instead of returning a silently-wrong plaintext |
| AC7 | `Cipher` per call | Manual smoke check: 200 concurrent encrypt+decrypt round-trips across 32 threads on one shared `MfaSeedEncryption` instance, all correct |
| AC8 | Unsupported version byte | Manual smoke check: version `0x05` → `MfaEncryptionException` |

(These are throwaway reflection-based smoke checks run against the compiled classes, not the real
JUnit suite — Phase 10 writes `TotpGeneratorTest`/`MfaSeedEncryptionTest` per the plan. This phase
only had to confirm the code is real and correct, not deliver the test artifact.)

Build verification: `mvn -pl services/auth -am compile` succeeds. Full `mvn -pl services/auth -am
test` run shows 5 pre-existing failures, confirmed identical on a `git stash`-ed pre-T16 tree
(`AuthServiceApplicationTests.contextLoads` — the known
[[docker-testcontainers-handshake-issue]]; `AdminAccountRoleControllerTest`,
`ReuseDetectingAuthorizationServiceTest`, `TokenClaimsCustomizerTest` ×2 — pre-existing Mockito
strict-stubbing/NPE issues unrelated to `mfa`). T16's changes introduce zero new failures.

## Deviations Forced by Reality

**The guard mechanism is a config boolean (`seedKekRequired`), not `Environment.acceptsProfiles`,
despite the frozen brief specifying the latter.** During implementation I found that **no test or
local-dev setup in this repository ever activates the `local` Spring profile** — there is no
`docker-compose.yml`, no `@ActiveProfiles` annotation on any existing test, and no
`application-test.properties`; the only place `SPRING_PROFILES_ACTIVE` is set at all is
`deploy/k8s/base.yaml` (`value: ${ENVIRONMENT}`, templated at deploy time for dev/staging/prod).
Implementing the guard as literally `Environment.acceptsProfiles(Profiles.of("local"))` would have
made `MfaSeedEncryption`'s constructor throw `IllegalStateException` on every `@SpringBootTest` in
the module the moment it's component-scanned (default profile ≠ `local`), a real regression I
could reproduce, not a theoretical one.

I also found this service already ships and tests the exact same class of guard —
`SigningKeysProperties.requireConfigured` / `SigningKeyMaterial.load` (L13, for JWT signing keys)
— via an explicit config boolean that defaults `false` everywhere and is only flipped to `true` in
`deploy/k8s/base.yaml` (`JWT_REQUIRE_CONFIGURED: "true"`), never by checking the active profile.
I matched that established, shipped, tested pattern: `themistra.auth.mfa.seed-kek-required`
(new property, not in the Phase 5 plan), defaulting `false`. This preserves ADR-0003's actual
intent (never produce a version-`0x00` envelope outside local dev) without silently breaking the
test suite or diverging from the codebase's one existing precedent for this exact problem.

**Consequence requiring follow-up, out of this task's authorized scope:** `deploy/k8s/base.yaml`
was **not modified** (only `application.properties`/`pom.xml` are in the frozen brief's
Files-to-Modify list). It currently sets `JWT_REQUIRE_CONFIGURED: "true"` but has no
`MFA_SEED_KEK_REQUIRED` entry — meaning as of this commit, the new guard defaults to `false` even
in deployed dev/staging/prod, so a misconfigured deployment could silently fall back to the local
key in a real environment. **Someone must add `MFA_SEED_KEK_REQUIRED: "true"` to
`deploy/k8s/base.yaml`'s env list (mirroring the existing `JWT_REQUIRE_CONFIGURED` line) before
this reaches any non-local environment.** Flagging rather than silently fixing, since that file is
outside T16's Files-to-Modify list and CDK/infra changes are explicitly out of scope per the
frozen brief.

**`KmsClient` construction:** resolved per Phase 5's flagged open question — built inline inside
`MfaSeedEncryption`'s static `resolveKmsClient` helper (skipped entirely, returning `null`, when
`seedKekArn` is blank), not as a separate Spring `@Bean`. No new config-class file was needed.

## Open Questions

None blocking. The `deploy/k8s/base.yaml` follow-up above is a required action before deployment,
not an open design question — recorded here so it isn't lost, not deferred as ambiguous.
