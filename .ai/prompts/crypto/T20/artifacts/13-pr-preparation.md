# crypto · T20 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Add crypto-service KMS signer as the sole kms:Sign caller (T20)
```

## Commit message

```
Add crypto-service KMS signer as the sole kms:Sign caller (T20)

Introduce the attest module's first file: KmsSigner, the only class
in this service permitted to call AWS KMS's Sign operation on the
attestation key (R22, L11, ADR-0004). Concentrates the platform's
single highest-consequence capability - producing a Themistra
attestation signature - behind one auditable, structurally-enforced
choke point, backed by an ArchUnit rule (the first real use of this
service's already-declared, previously-unused archunit-junit5
dependency) plus a plain-JUnit canary, since @ArchTest fields alone
are confirmed not to execute under this repo's Surefire setup.

sign(byte[] digestSha256) validates a 32-byte SHA-256 digest, calls
KMS with MessageType.DIGEST and a single named, Q7-pending
SigningAlgorithmSpec constant, and maps the response using the key id
KMS itself returns (not the configured input, which can legitimately
differ - empirically confirmed against a real LocalStack-emulated
key). KmsSigner builds its own KmsClient internally, mirroring
services/auth's MfaSeedEncryption - the actual, already-reviewed
precedent in this monorepo for one class owning both its AWS client's
construction and being the sole caller of the sensitive operation,
discovered necessary after a separate config class was shown to
trip the very ArchUnit rule it would exist to enforce.

Self-review caught and fixed a critical defect before it ever reached
independent review: neither of KmsSigner's two constructors was
annotated @Autowired, so Spring could not resolve which to use and
would have failed to start in any real deployment - confirmed via a
real ApplicationContext probe, fixed with one annotation, and
permanently regression-tested.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created:**
- `services/crypto/src/main/java/com/themistra/crypto/attest/KmsSigner.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/SignatureResult.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerSpringWiringTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerLocalStackIntegrationTest.java`
- `services/crypto/src/test/java/archtestfixtures/RogueAttestReferencer.java` — a deliberately
  rule-violating fixture, placed in a standalone top-level package outside `com.themistra.crypto`
  entirely so the real production ArchUnit canary's package-wide scan can never sweep it up; referenced
  only by `KmsSignerArchitectureTest`'s own negative-proof test.

7 files changed (all created), +704 lines (683 tracked-diff insertions + 21 in the new untracked
fixture file). No migration, no schema change — `KmsSigner` persists nothing.

## Summary

Gives the platform its sole, structurally-enforced path to `kms:Sign` on the attestation key. `KmsSigner`
is a small, self-contained class with no consumer yet (task 21 builds `AttestationService`, the first
caller); it validates its input, calls KMS with a documented, Q7-pending algorithm choice, and maps the
response using KMS's own returned key identifier rather than blindly echoing the configured input — a
design decision empirically validated against a real LocalStack-emulated asymmetric key, where the two
values turned out to genuinely differ (bare id vs. full ARN).

The `KmsSignerArchitectureTest` rule (the task's own named test,
`shouldOnlyAllowAttestPathToInvokeKmsSign`) went through two rounds of tightening after independent and
test review: first adding a package constraint so a hypothetically-named class outside `attest` couldn't
dodge the rule by naming convention alone, then adding a genuine negative-proof (via an isolated fixture
class in a standalone package, never touching the real scan) that the rule can actually fail, not merely
pass on already-clean code — closing the gap where a silently-broken canary would have given a false
sense of security for the platform's single most security-critical guarantee.

The most significant event this task produced was self-discovered, not review-discovered: a critical
Spring-wiring defect (missing `@Autowired` on the constructor Spring needed to disambiguate) that would
have prevented the entire `attest` module from starting in any real deployment. It was caught, fixed, and
permanently regression-tested within the same phase it was found, before ever reaching independent
review — mirroring this pipeline's own established precedent for a defect severe enough that deferring
its fix would make further review moot.

## Testing performed

- `mvn -pl services/crypto test-compile` — clean.
- `mvn -pl services/crypto test -Dtest=KmsSignerTest,KmsSignerArchitectureTest,KmsSignerLocalStackIntegrationTest,KmsSignerSpringWiringTest` — 18/18 pass.
- `mvn -pl services/crypto -am test` (full module regression) — 658 tests, 6 failures, all pre-existing
  and unrelated to this task (disclosed since T18/T19: `ObservationRepositoryIntegrationTest` ×2,
  `ProviderHealthRepositoryIntegrationTest` ×1, `QuorumDecisionRepositoryIntegrationTest` ×1,
  `TokenAllowlistRepositoryIntegrationTest` ×2). One transient Testcontainers/Docker connection error
  was observed on a single run in an unrelated pre-existing test file and did not reproduce on immediate
  re-run. Zero regressions.
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`: `artifacts/12-specification-verification.md` — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 20 ("KMS signer — single path").
- **Requirements:** R22 (`kms:Sign` invoked only from the attest path).
- **LOCKED decisions:** L11 (KMS-only signing, single path; ArchUnit + IAM enforced; Nitro-Enclave
  portable).
- **Related:** `docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md` (the ADR this task
  operationalizes).
