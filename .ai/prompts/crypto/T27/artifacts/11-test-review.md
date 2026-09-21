# crypto · T26 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T27 — Run full suite |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

---

## Summary

T27 explicitly authors no new JUnit tests; its verification is the full-suite Maven run and the Docker image build. The only test code touched is the existing `KmsSignerArchitectureTest`, updated to allowlist `EndToEndIntegrationTest`'s legitimate cross-module `KmsSigner` mock reference.

The gaps are therefore about **process-level verification completeness** (Docker-dependent tests and the image build were not actually run) and **the strength of the `KmsSignerArchitectureTest` changes** (negative-proof assertions are still weak, and there is no proof that the allowlist itself works).

---

## Recommendations

### 1. `mvn verify` did not actually pass in this environment

- **Gap:** The reported run produced 696 tests with 0 failures but 14 already-disclosed Docker-only errors. The task statement says `mvn -pl services/crypto verify` must pass; an environment that cannot run Testcontainers means AC1 is not fully satisfied here.
- **Why it matters:** The 14 errors are integration tests (`*IT` classes under failsafe) that exercise real Postgres/Kafka. Their absence leaves the end-to-end paths unverified.
- **Suggested test:** Run `mvn -pl services/crypto -am verify` in a Docker-available CI environment and capture the output. Only when it returns 0 failures with 0 Docker-environment errors can AC1 be considered satisfied.

### 2. Docker image build was not actually executed

- **Gap:** The `docker build` command was attempted but blocked at the daemon-connection level. The Dockerfile was reviewed by construction only.
- **Why it matters:** AC2 requires the image to build from the repo root. A syntax-correct Dockerfile can still fail to build because of dependency resolution, plugin, or compilation issues.
- **Suggested test:** Run `docker build -f services/crypto/Dockerfile -t crypto-service .` from the repo root in a CI environment with Docker available, and confirm the image is produced.

### 3. Negative-proof assertions in `KmsSignerArchitectureTest` do not verify the failure message

- **Gap:** `bothRulesActuallyFailAgainstAGenuineViolation` checks only `isInstanceOf(AssertionError.class)`. It does not assert that the message names `RogueAttestReferencer`, `KmsSigner`, or the KMS SDK package.
- **Why it matters:** An unrelated assertion failure (e.g., a class-loading problem) could also throw `AssertionError` and make the test pass for the wrong reason.
- **Suggested test:** Add `.hasMessageContaining("RogueAttestReferencer")` and `.hasMessageContaining("KmsSigner")` / `.hasMessageContaining("software.amazon.awssdk.services.kms")` to the two `assertThatThrownBy` blocks.

### 4. No proof that the allowlist actually permits `EndToEndIntegrationTest`

- **Gap:** The regression guard `allowlistedKmsSignerCrossModuleReferenceStillExistsInCode` proves the dependency still exists, and the canary proves the rules pass on the whole codebase. Neither proves that *if* the allowlist entry were removed, the rule would fail on `EndToEndIntegrationTest`.
- **Why it matters:** A bug in the allowlist logic (e.g., wrong class name comparison) could silently allow or forbid the wrong class, and the existing tests would not expose it.
- **Suggested test:** Add a negative-proof that builds a narrow `JavaClasses` set containing only `EndToEndIntegrationTest` and `KmsSigner`, removes the allowlist entry temporarily (or asserts the rule fails when the entry is removed), and verifies the rule throws.

### 5. No automated check that the Dockerfile satisfies packaging rules

- **Gap:** The Dockerfile is expected to be multi-stage, distroless, non-root, and secret-free, but there is no automated test or CI check enforcing these properties.
- **Why it matters:** A future edit could accidentally introduce a secret, remove `USER nonroot`, or switch to a non-distroless base image without failing any test.
- **Suggested test:** Add a lightweight test or CI script that parses the Dockerfile and asserts: exactly two `FROM` lines; the runtime image is `gcr.io/distroless/java21-debian12:nonroot`; `USER nonroot` is present; no `ARG`/`ENV` values look like secrets (e.g., no base64 blobs, no `password=`, no `AWS_SECRET`).

### 6. Traceability matrix omits the `KmsSignerArchitectureTest` updates

- **Gap:** Phase 10 states no new tests were authored, but T27 did modify `KmsSignerArchitectureTest` (allowlist + message assertions). The traceability matrix does not list these changes.
- **Why it matters:** Reviewers cannot see which existing test now covers the cross-module KmsSigner mock exception introduced by T26.
- **Suggested test:** Add a row to the traceability matrix: `KmsSignerArchitectureTest.allowlistedKmsSignerCrossModuleReferenceStillExistsInCode` and `noClassOutsideAttestMayReferenceKmsSigner` → T27 conflict resolution → verifies only the explicitly allowlisted cross-module test double may reference `KmsSigner`.

### 7. No test verifies the produced jar name matches the runtime `COPY` path

- **Gap:** The Dockerfile copies `/workspace/services/crypto/target/crypto-service.jar`. If the POM `finalName` ever changes, the image build fails at the `COPY` step.
- **Why it matters:** The `finalName` is currently `crypto-service`, but there is no test linking it to the Dockerfile.
- **Suggested test:** Add a CI smoke step that runs `mvn -pl services/crypto package -DskipTests` and asserts `services/crypto/target/crypto-service.jar` exists before invoking `docker build`.

### 8. The full-suite result is not reproducibly captured as an artifact

- **Gap:** The Phase 10 report cites 696 tests / 0 failures / 14 Docker errors, but there is no attached surefire/failsafe report or build log.
- **Why it matters:** Without an artifact, a later reviewer cannot independently verify the claim or inspect which 14 tests failed.
- **Suggested test:** Configure CI to archive `services/crypto/target/surefire-reports/`, `failsafe-reports/`, and the Maven build log as artifacts.

---

## Confirmations

- T27 correctly does not author new JUnit tests; its scope is the process-level verification.
- The existing test suite (696 tests) covers the requirements built in prior tasks.
- The `KmsSignerArchitectureTest` allowlist is the correct, minimal change to resolve the T26 cross-module mock reference without weakening the KMS-signer package ban.
- Docker availability is the only disclosed blocker preventing a fully green AC1/AC2 result.
