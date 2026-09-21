# crypto · T28 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T28 — Threat-model closure |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

---

## Findings

### 1. Row #5's S3 Object Lock / WORM immutability is not actually tested

- **Issue:** The brief scopes row #5 to crypto-service's own portion (observation log + S3 WORM snapshot) and cites `ObservationLogTest` and `ObservationSnapshotStoreTest`. Both tests mock `ObservationSnapshotStore`; they verify that a snapshot is attempted and that the S3 key is computed, but they do not verify Object Lock, WORM, legal hold, or any immutability property of the S3 bucket. The "insider alters a historical verification" mitigation depends on bucket-level configuration that no Java unit test can enforce.
- **Severity:** Medium
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationLogTest.java` and `ObservationSnapshotStoreTest.java` use Mockito for the S3 client and never assert Object Lock parameters or bucket policy.
- **Recommended brief amendment:** For row #5, explicitly state that the crypto-service test coverage is limited to "snapshot is written before the Postgres row" and "the log is append-only"; the S3 Object Lock / WORM guarantee is an operational/deployment control, not a crypto-service unit-testable assertion. Cite the exact test methods (e.g., `recordAttemptsTheS3WriteBeforeThePostgresInsert`) and add a note that bucket Object Lock must be enforced by infrastructure/CI, not by these tests.

### 2. Row #4's IAM / runtime key-protection is not tested

- **Issue:** `KmsSignerArchitectureTest` verifies the static-code rule that only `attest`-package code may reference `KmsSigner` or the KMS SDK. It does not verify the IAM rule that only the Crypto Service role may call `kms:Sign`, nor does it prove that key material never leaves KMS at runtime (the KMS SDK behavior). The threat's mitigation has both a code-path half and an infrastructure half; only the code-path half is testable here.
- **Severity:** Medium
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java` is purely ArchUnit/static analysis; no test exercises IAM policies or KMS key policy.
- **Recommended brief amendment:** In row #4's closure note, split the mitigation into (a) code-path: covered by `KmsSignerArchitectureTest`, and (b) IAM/runtime: covered by infrastructure controls (EKS IAM roles, KMS key policy, deployment review), not by this test suite. Do not mark the IAM half as closed by tests.

### 3. "No single-provider fact is ever emitted" lacks a system-level, running test

- **Issue:** AC3 acknowledges that only per-unit coverage may exist, but the task statement asks to confirm this property as a standalone check. `QuorumEvaluatorTest`, `QuorumDecisionServiceTest`, and `WatcherTest` prove that a fact is not evaluated (and therefore no lifecycle event is emitted) with fewer than three provider answers. They do not prove that the real `TxLifecyclePublisher` / outbox path cannot emit an event from a single provider.
- **Severity:** Medium
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java` has `doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering` and `laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers`; no test asserts `txLifecyclePublisher.seen/confirmed/finalized` is never invoked in those scenarios.
- **Recommended brief amendment:** Either add a test (e.g., in `WatcherTest`) that verifies `txLifecyclePublisher` has zero interactions when fewer than three providers answer, or disclose in AC3/row #1 that the "no single-provider fact" guarantee is currently proven only at the quorum-evaluation layer, not at the event-publication layer. If `EndToEndIntegrationTest` is eventually runnable, cite its disagreement flow as the end-to-end proof.

### 4. Full `mvn verify` cannot actually pass in this environment

- **Issue:** AC1 requires a fresh `mvn -pl services/crypto -am verify` run confirming every cited test passes. The environment has no Docker daemon, so the failsafe integration tests (`EndToEndIntegrationTest` and any other `*IT` classes) fail with Docker-environment errors. A run that ends with 14 errors is not a passing `verify`.
- **Severity:** High
- **Evidence:** T27's own Phase 10/11 artifacts report 696 tests / 0 failures / 14 already-disclosed Docker-only errors; `./mvnw -pl services/crypto -am compile test-compile` also fails in this environment.
- **Recommended brief amendment:** Split AC1 into (a) unit + ArchUnit tests pass (`mvn test` or `mvn verify` with integration tests skipped), confirmed in this environment; and (b) full `mvn verify` with integration tests must be re-run in a Docker-available CI environment before the spec can be bumped to `READY FOR IMPL`. Do not mark row closure as fully verified by a run that has unresolved errors.

### 5. Row #3's "reorg after confirmed" is not proven end-to-end

- **Issue:** Row #3 cites `EthereumFinalityPolicyTest`, `TronFinalityPolicyTest`, and `ReorgDetectorTest`. These are unit tests for the individual components. They do not prove the integrated property that a transaction can reach `chain.tx.confirmed`, then a reorg is detected, and no `chain.tx.finalized` follows. `EndToEndIntegrationTest.flow3` is the intended end-to-end proof, but it is in the Docker-blocked integration-test set.
- **Severity:** Low/Medium
- **Evidence:** Unit tests exist; integration test `reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor` in `EndToEndIntegrationTest` requires Docker.
- **Recommended brief amendment:** For row #3, cite the unit tests as partial coverage and explicitly note that the reorg-after-confirmed integration path is covered by `EndToEndIntegrationTest.reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor`, which has not yet executed in this environment.

### 6. The brief cites `AddressValidatorTest` but it is not mapped to any threat row

- **Issue:** The TIB lists `AddressValidatorTest` among the dependency tests, but none of rows #1–#6 is about EIP-55 / Base58 address validation. Address validation is R15/R16, which support input correctness but are not directly one of the six threats. Including it without mapping could imply it closes a threat it does not.
- **Severity:** Low
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/token/AddressValidatorTest.java` (assumed to exist) is not tied to a row in the threat-model table.
- **Recommended brief amendment:** Remove `AddressValidatorTest` from the row-citation list, or add a note that it supports data-quality controls but does not directly mitigate threats #1–#6.

### 7. No test verifies the observation log is append-only at the repository/DB layer

- **Issue:** Row #5's crypto-service portion relies on the observation log being append-only (no UPDATE/DELETE). The cited tests verify S3-before-Postgres ordering and snapshot behavior, but they do not assert that `ObservationRepository` lacks update/delete methods or that the DB role has only INSERT+SELECT.
- **Severity:** Low
- **Evidence:** `ObservationLogTest` and `ObservationSnapshotStoreTest` do not inspect `ObservationRepository` method signatures or DB grants.
- **Recommended brief amendment:** Either add a regression test (e.g., an ArchUnit rule or reflection test) that `ObservationRepository` has no `delete*` or `update*` methods, or disclose that the append-only guarantee is enforced by Flyway DDL and DB grants rather than by a Java test.

### 8. The threat-model table itself calls the document a stub and says it must be completed before the first line of crypto code

- **Issue:** `SECURITY-THREAT-MODEL.md` line 3 states it is a stub and must be completed before the first line of crypto-service code. Crypto-service code already exists. Updating the Status column to `closed` without addressing the "stub" wording may leave the document in an inconsistent state.
- **Severity:** Low
- **Evidence:** `SECURITY-THREAT-MODEL.md:3`.
- **Recommended brief amendment:** As part of this task, update the document header from "stub" to a stable status (e.g., "Status: updated for crypto-service T28") so the pre-condition wording does not contradict the completed code.

---

## Open Questions

1. **Should row #5 be marked `closed` at all when its S3 Object Lock/WORM guarantee is not unit-testable?** The brief scopes it to crypto's own portion, but the row's mitigation text still includes Payment-Service-owned and infrastructure-owned controls.
2. **Should AC1 accept `mvn test` (unit + ArchUnit) as sufficient verification in this Docker-blocked environment, with a hard requirement to re-run `mvn verify` in CI before task 29?**
3. **Is there an existing `AddressValidatorTest`, and if so, should it be cited under a different row or omitted?**
