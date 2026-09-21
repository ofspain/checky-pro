# crypto · T28 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T28 — Threat-model closure |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

---

## Summary

Phase 9 closed the Phase 8 gaps: `WatcherTest` now covers the literal single-provider case, and `T01SkeletonRegressionTest` verifies both a `ClassName.methodName` citation pattern and the per-row caveats for rows #3/#4/#5. The threat-model table is now machine-guarded against silent regression of its `closed` status and its important caveats.

The remaining gaps are **end-to-end verification still blocked by Docker**, **row #3's unit-level citations remain class-level rather than method-level**, and **row #5's DB-grant claim still has no cited test**.

---

## Recommendations

### 1. End-to-end threat verification is still not executed

- **Gap:** The fresh `mvn verify` run reports 698 tests / 0 failures / 14 Docker-only errors. The integration tests that would prove the threats end-to-end (`EndToEndIntegrationTest`) have never actually run in this environment.
- **Why it matters:** Rows #1, #3, and #5 are only partially verified by unit tests. The task statement asks to confirm system-level properties (no single-provider fact, reorg-after-confirmed, no non-attest KMS path), but the environment prevents that confirmation.
- **Suggested test:** Run `mvn -pl services/crypto -am verify` in a Docker-available CI environment and confirm 0 errors before bumping the spec status in task 29.

### 2. Row #3's unit-level test citations are still class-level

- **Gap:** `SECURITY-THREAT-MODEL.md` row #3 cites `EthereumFinalityPolicyTest`, `TronFinalityPolicyTest`, and `ReorgDetectorTest` without naming specific methods. Every other row cites `ClassName.methodName`.
- **Why it matters:** A class-level citation is weaker; a future refactor could remove or rename the relevant test method while the class still exists, making the citation stale and misleading.
- **Suggested test:** Identify the actual named tests in those classes (e.g., `EthereumFinalityPolicyTest.shouldRequireBeaconFinalizedCheckpointForEthereumFinality`) and update the Status cell to cite them.

### 3. Row #5's DB-grant claim has no test coverage

- **Gap:** Row #5 notes that `chain.observations` is append-only because `crypto_app` has only `INSERT`/`SELECT` grants. No test currently verifies that migration file content.
- **Why it matters:** A future migration could add `UPDATE`/`DELETE` grants to `chain.observations` and silently undermine the insider-attack mitigation without failing any cited test.
- **Suggested test:** Extend `T01SkeletonRegressionTest` to read `src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql` and assert that the grant line for `chain.observations` contains only `INSERT, SELECT` and no `UPDATE`/`DELETE`.

### 4. `T01SkeletonRegressionTest` does not verify that cited tests actually exist

- **Gap:** The regex checks for a `ClassName.methodName` pattern but does not resolve it against the test classpath. A typo in either the class or method name would still match the pattern.
- **Why it matters:** The regression guard protects the *format* of the citation but not its *accuracy*.
- **Suggested test:** Add a small classpath-scanning assertion that loads each backtick-quoted `ClassName` and confirms it declares a method matching `methodName`. Keep it lenient for the class-level citations in row #3 if those are not immediately replaced.

### 5. No test guards the "no non-attest path can reach `kms:Sign`" property beyond static analysis

- **Gap:** Row #4 is closed by `KmsSignerArchitectureTest`, which is purely static ArchUnit analysis. It proves code-structure compliance, not that runtime reflection, bean override, or misconfiguration cannot expose the signer.
- **Why it matters:** The task statement explicitly asks to "confirm no non-attest path can reach `kms:Sign`." Static analysis is the right tool for code, but it cannot confirm runtime/infrastructure posture.
- **Suggested test:** Document this as an ownership split in the threat-model table (already done) and add a CI smoke test, if feasible, that verifies the runtime IAM role has no `kms:Sign` permission outside the crypto-service role.

### 6. The single-provider coverage does not assert that observations are still logged

- **Gap:** `WatcherTest.doesNotEvaluateWithOnlyOneOfThreeProvidersAnswering` verifies no quorum evaluation and no lifecycle event, but it does not assert that the lone observation was still persisted to the observation log.
- **Why it matters:** L3 requires every provider response to be logged verbatim even when quorum cannot be reached. A regression that dropped single-provider observations would evade this test.
- **Suggested test:** Add `verify(observationLog).record(...)` (or inspect the repository) in the single-provider and two-provider tests to prove the observation is preserved despite no event being emitted.

---

## Confirmations

- `WatcherTest` now directly covers the literal single-provider case and two multi-provider "not enough answers" cases, all asserting no lifecycle event is published.
- `T01SkeletonRegressionTest` now guards the `closed` status, requires a `ClassName.methodName` citation, and enforces the three documented caveats.
- `SECURITY-THREAT-MODEL.md` rows #1–#6 are updated, rows #7–#8 are untouched, and the document header no longer calls the file a stub.
- All changes are within the T28 scope (documentation + minimal regression-test strengthening); no production code was modified.
