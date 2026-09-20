# auth · T16 — Phase 13: PR / Commit Preparation

Consumes `artifacts/12-specification-verification.md` — **PASS**. No code changes in this phase;
this artifact only prepares merge material. Nothing is committed by this phase — per this
project's established session convention, an actual `git commit` only runs on explicit request.

Branch: `spec/service-specs-and-ai-framework`, off `main`. `main` is untouched and stays
deployable; this branch is where T01-T16 (and the `.ai`/spec framework itself) have been built up
task-by-task, each already verified PASS before the next one starts.

---

## Commit Title

```
Add TOTP secret generation and KMS-enveloped seed encryption (T16)
```

## Commit Message

```
Add TOTP secret generation and KMS-enveloped seed encryption (T16)

Implements the two standalone, independently-testable components T16 scopes: TotpGenerator
(random 160-bit secret, RFC-3986-encoded otpauth:// provisioning URI per L6) and
MfaSeedEncryption (AES-256-GCM envelope encryption of TOTP seeds with a KMS-enveloped data
key, the one narrow named exception to D-010 permitting AWS SDK use in this service, per
ADR-0003/D-025). Neither persists anything nor exposes an endpoint - those are tasks 17/19.

The non-local startup guard went through one documented correction: an initial config-boolean
approach (matching the one existing precedent for this exact problem, SigningKeysProperties/
JWT) was replaced with true Spring-profile detection once `local` was wired up as this
service's actual default profile for the first time, restoring ADR-0003's literal wording
without breaking every existing test (none of which had ever activated any profile before).

Full review chain applied: Phase 3/8/11 Kimi findings triaged and resolved (human-approved at
the Phase 4 and Phase 9 gates), malformed-envelope handling, GCM-authentication-failure
coverage (not just KMS-rejection), generateDataKey exception wrapping, an ArchUnit rule
enforcing the single-class AWS SDK boundary, and KmsClient cleanup on shutdown all included.

Spec: spec/auth-service/tasks.md task 16. R22 (generation/encryption portion only).
Locked decisions: L6, L13, L14. ADR-0003 (docs/adr/0003-narrow-kms-exception-for-totp-seed-
encryption.md) is the byte-for-byte implementation spec for the envelope format.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

*(The prompt template header for this phase names "Claude Opus 4.8" in its trailer — that's a
stale placeholder from whenever `.ai/generate.py` last ran; the actual model that did this
work is Sonnet 5, so the trailer above reflects that instead of the literal template text.)*

## Files Changed

**Production code:**
- `services/auth/src/main/java/com/themistra/auth/mfa/TotpGenerator.java` (new)
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaSeedEncryption.java` (new)
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaProperties.java` (new)
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEncryptionException.java` (new)
- `services/auth/pom.xml` (modified — AWS SDK BOM 2.50.2 + `kms` dependency)
- `services/auth/src/main/resources/application.properties` (modified — two new
  `themistra.auth.mfa.*` keys, plus `spring.profiles.active=local` as the service's new app-wide
  default, overridden per environment by the pre-existing `SPRING_PROFILES_ACTIVE` in
  `deploy/k8s/base.yaml`)

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` (modified — one new
  rule confining AWS SDK imports to `MfaSeedEncryption`)
- `services/auth/src/test/java/com/themistra/auth/mfa/TotpGeneratorTest.java` (new, 9 tests)
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaSeedEncryptionTest.java` (new, 24 tests)
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaPropertiesTest.java` (new, 3 tests)

**Pipeline audit trail** (`.ai/prompts/auth/T16/artifacts/`): all 13 phase artifacts,
`00-repository-understanding.md` through this file.

**Not part of this PR:** an untracked `prompt` file at the repo root (unrelated spec-audit
content from a different exercise, never touched by any T16 phase) — flagging so it isn't
accidentally swept into a `git add -A`, not recommending any action on it here.

## Summary

T16 lays the cryptographic groundwork R22 depends on: a TOTP secret/URI generator and a
KMS-enveloped AES-GCM seed encryptor, both fully independent of persistence (tasks 17/19 wire
them in later). The implementation matches ADR-0003's envelope format exactly and went through
the full 13-phase review pipeline — Kimi's adversarial Phase 3/8/11 reviews, two human-approval
gates (Phase 4 freeze, Phase 9 resolution), and a clean Phase 12 specification-verification PASS.

## Testing Performed

- `mvn -pl services/auth -am compile` — success.
- `mvn -pl services/auth -am test -Dtest='!*IntegrationTest'` — **349 tests, 0 failures, 5
  errors**. All 5 errors confirmed pre-existing and unrelated to this task (re-verified via
  `git stash` against a pre-T16 tree at Phase 6): `AuthServiceApplicationTests.contextLoads`
  (this sandbox's known Docker/Testcontainers handshake limitation) and 4 unrelated
  Mockito-strict-stubbing/NPE issues in `token`/`authz` tests untouched by T16.
- 36 new unit tests (`TotpGeneratorTest` 9, `MfaSeedEncryptionTest` 24, `MfaPropertiesTest` 3) —
  plain JUnit, no Spring context, `KmsClient` mocked via Mockito, `Environment` stood in for by
  `MockEnvironment`. Cover all 8 frozen-brief acceptance criteria plus every Phase 8/9/11 review
  fix: malformed/truncated-envelope handling, the real GCM-authentication-failure case (not just
  KMS-level rejection), ciphertext/nonce tampering, `generateDataKey` exception wrapping,
  version-`0x00` zero-wrapped-key-length enforcement, `destroy()`/`KmsClient` cleanup
  (including double-`destroy()` safety), zero-active-profile and multi-profile guard behavior,
  and RFC-3986-correct URI encoding.
- **No LocalStack/KMS-Testcontainers integration test** — a deliberate, human-confirmed scope
  decision at Phase 4 (mocked `KmsClient` unit coverage instead), consistent with T15's own
  precedent and this sandbox's still-unresolved Testcontainers/Docker handshake issue.
- The new ArchUnit AWS-SDK-confinement rule was additionally verified directly via ArchUnit's
  `ClassFileImporter` API against the real compiled classes (Phase 9), since this sandbox's
  Maven Surefire run doesn't execute `ArchitectureTest`'s rules at all — a pre-existing,
  environment-wide quirk affecting every rule in that file, not something T16 introduced.

## Specification References

- **Task:** `spec/auth-service/tasks.md` #16 — "TOTP seed handling."
- **Requirement:** R22 (partial — generation and encryption components only).
- **Locked decisions:** L6 (RFC 6238 parameters), L13 (secrets discipline), L14 (TOTP seed
  encryption, widened into scope at Phase 0/1).
- **ADR:** `docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md` (D-025) — the
  byte-for-byte envelope format implemented here.
- **Standing rules:** `spec/auth-service/agents.md` (Security section: AWS SDK confinement,
  secrets discipline; Testing section: plain JUnit unit tests, no Spring context).
