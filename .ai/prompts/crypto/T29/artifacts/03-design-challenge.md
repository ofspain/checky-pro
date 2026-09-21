# crypto · T29 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T29 — Bump spec status |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

---

## Summary

The Phase 2 brief is internally consistent and correctly scopes T29 to documentation-only changes in `package.md`. The main risk is over-claiming readiness: several §9 checklist items depend on Docker/CI/IAM conditions that have not actually run in this environment, and Q7's "deployment-swappable" claim is contradicted by a hard-coded signing algorithm. If the brief is honest about these as deferred/conditional items, the `0.2`/`READY FOR IMPL` bump is defensible; if it silently marks them `[x]`, the gate is false.

---

## Findings

### 1. §9 checklist item 13 — `mvn -pl services/crypto verify` cannot be honestly checked here

- **Issue:** The brief states the final `mvn verify` run produced **698 tests, 0 failures, 14 already-disclosed Docker-only errors**. Item 13's text explicitly requires "unit + integration with fake providers".
- **Severity:** High.
- **Evidence:** `package.md` §9 item 13 reads `"mvn -pl services/crypto verify` passes (unit + integration with fake providers)`. The integration tests in this module rely on Testcontainers (Postgres + Kafka) and LocalStack/Docker per `agents.md`. The 14 Docker errors mean the integration portion did not pass in the environment used for T28.
- **Recommended brief amendment:** Do not mark item 13 `[x]` unconditionally. Either split it into `[x]` unit + `[ ]` integration (with a "blocked: no Docker daemon in this environment; must run in CI" note), or mark the whole item `[ ]` and state the unit-test subset passed while integration is deferred to CI.

### 2. §9 checklist item 14 — Docker image build is unverified

- **Issue:** The brief does not mention attempting a Docker build, and the environment has no Docker daemon, so the "Docker image builds" claim cannot be checked.
- **Severity:** High.
- **Evidence:** `services/crypto/Dockerfile` exists (T27), but `docker build` has not been executed successfully here; `agents.md` and `package.md` §9 item 14 both require a working image.
- **Recommended brief amendment:** Mark item 14 `[ ]` with the note "Docker daemon unavailable in this environment; must pass in CI before release." Do not mark it `[x]`.

### 3. Q7 — KMS signing key spec is not deployment-swappable today

- **Issue:** The brief resolves Q7 as a "complete, tested, deployment-swappable engineering answer, with the pure vendor/procurement decision deferred." But `KmsSigner.SIGNING_ALGORITHM` is a hard-coded `SigningAlgorithmSpec.ECDSA_SHA_256` constant; changing the provisioned key type would require a code change and redeploy.
- **Severity:** Medium.
- **Evidence:** `services/crypto/src/main/java/com/themistra/crypto/attest/KmsSigner.java` line 74: `private static final SigningAlgorithmSpec SIGNING_ALGORITHM = SigningAlgorithmSpec.ECDSA_SHA_256;`. The `SignRequest` also sends `MessageType.DIGEST` with a SHA-256 digest, coupling it to ECDSA-over-SHA-256.
- **Recommended brief amendment:** Resolve Q7 as "engineering answer: SHA-256 digest + ECDSA P-256 signing via KMS, verified end-to-end against a compatible key. **Open follow-up:** make the KMS `signingAlgorithm` configurable through `KmsProperties` if the platform later provisions a different key spec (e.g., secp256k1, RSA-PSS)."

### 4. Q1 — "commercially independent providers" cannot be enforced in code

- **Issue:** The brief resolves Q1 by pointing to the configurable provider list and quorum threshold. That answers the count/threshold half, but not the commercial-independence half.
- **Severity:** Medium.
- **Evidence:** `ProviderProperties` validates provider count >= `quorumThreshold`, names, URLs, and timeout, but cannot know whether two providers share infrastructure, ownership, or jurisdiction.
- **Recommended brief amendment:** Add the sentence: "Commercial independence of the configured providers is an operational/procurement guardrail, not a runtime assertion; the code enforces only that `providers.size() >= quorumThreshold` and that answers must agree."

### 5. Several §9 checklist items depend on CI/IAM/process gates, not just code

- **Issue:** Items 10 (`kms:Sign` only from this role), 11 (observation log before quorum), 12 (secrets not committed), and 13/14 are partially or wholly environmental/CI/IAM concerns. Marking them `[x]` from a local test run alone overstates evidence.
- **Severity:** Medium.
- **Evidence:** Item 10 references IAM; item 12 references gitleaks/CI; items 13/14 require Docker. The brief correctly plans to cite tests for code-enforceable items, but does not distinguish process-enforceable items.
- **Recommended brief amendment:** Group each `[x]` with the evidence type: `"unit test"`, `"ArchUnit"`, `"integration test (blocked locally, run in CI)"`, `"CI/IAM process"`, `"gitleaks"`, etc. This preserves honesty when the local environment cannot exercise the full gate.

### 6. The "tests pass" working decision may conflict with §9 item 13

- **Issue:** Working decision #1 redefines "tests pass" to mean "§3/§8 scoped acceptance criteria and named tests," excluding the wider integration-test surface. That is reasonable for the task's gate, but §9 item 13 is written more broadly.
- **Severity:** Medium.
- **Evidence:** The brief explicitly says T28's pre-existing integration failures "do not block this task's own gate." However, a PR implementer reading §9 will expect item 13 to mean full `mvn verify`.
- **Recommended brief amendment:** Either edit §9 item 13 to match the working decision (e.g., append "unit + named ArchUnit tests pass; integration tests run in CI"), or keep the broader wording and leave item 13 `[ ]` until CI confirms it.

### 7. Q2/Q3 resolution may leave attest permanently failing in non-prod environments

- **Issue:** `FailClosedScreeningClient` returns `ScreeningOutcome.ERROR` for every call. Combined with `AttestationService`'s fail-closed handling, this means attest will always refuse in local/staging until a real vendor is wired.
- **Severity:** Low.
- **Evidence:** `FailClosedScreeningClient.screen()` returns `ERROR`; `AttestationService` throws `AttestationRefusedException` on `ERROR` or exception.
- **Recommended brief amendment:** Note in Q2's resolution that the fail-closed stub intentionally prevents any attestation in environments without a real screening vendor, and that end-to-end attest tests therefore require a test double that returns `CLEARED` (e.g., a `@Profile("test")` bean) — confirm such a double exists or is planned.

### 8. No regression test guards the `package.md` status/version bump itself

- **Issue:** Once `Version` is bumped to `0.2` and `Status` to `READY FOR IMPL`, a later edit could silently revert or mis-bump them. There is no test that asserts the header values.
- **Severity:** Low.
- **Evidence:** `T01SkeletonRegressionTest` only guards `SECURITY-THREAT-MODEL.md`; nothing guards `package.md`.
- **Recommended brief amendment:** Consider adding a lightweight assertion to `T01SkeletonRegressionTest` (or a sibling spec-regression test) that parses `spec/crypto-service/package.md`, asserts `Status: READY FOR IMPL` and `Version: 0.2`, and asserts that every §9 item is `[x]` — with allowed exceptions for items explicitly tagged as CI-only.

---

## Confirmations

- The brief correctly limits T29 to `package.md` header, §9, and §11 only.
- The brief correctly refuses to change `requirements.md`, `design.md`, `tasks.md`, `agents.md`, and `services/crypto` source.
- The brief correctly identifies Q1/Q2/Q7 as resolvable through engineering answers plus explicit vendor deferrals, matching Q8's precedent style.
