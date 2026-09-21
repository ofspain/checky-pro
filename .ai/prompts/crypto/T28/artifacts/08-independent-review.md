# crypto · T28 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T28 — Threat-model closure |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` + `SECURITY-THREAT-MODEL.md` + `T01SkeletonRegressionTest.java` + `WatcherTest.java` |
| **Produces** | `artifacts/08-independent-review.md` |

---

## Findings

### 1. `T01SkeletonRegressionTest` only checks for the substring `closed`, not for a test citation

- **Issue:** The regression guard asserts that rows #1–#6 contain `closed` and do not end with `| — |`, but it does not verify that a named, backtick-quoted test method is actually cited. A row could read `closed — no test cited` and still pass.
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java:41-46`.
- **Recommendation:** Strengthen the assertion to require at least one backtick-quoted token after `closed`, e.g. `assertThat(row).contains("`").contains(".")` or a regex that matches a `ClassName.methodName` reference.
- **Confidence:** Medium

### 2. `T01SkeletonRegressionTest` does not enforce the documented caveats for rows #3, #4, and #5

- **Issue:** The self-review explicitly distinguishes an "execution gap" (row #3) from "ownership splits" (rows #4/#5). The regression guard will still pass if those caveats are silently removed from the Status cells, allowing a future edit to claim rows are fully closed when they are not.
- **Evidence:** `T01SkeletonRegressionTest.java:41-46` checks only `closed`; `SECURITY-THREAT-MODEL.md` rows #3–#5 contain parenthetical caveats.
- **Recommendation:** Add assertions that row #3 contains one of `"not yet executed"`, `"integration-level"`, or `"Docker"`; and that rows #4/#5 contain one of `"infrastructure"`, `"code-path only"`, or `"crypto-service portion"`.
- **Confidence:** Medium

### 3. `WatcherTest` covers only two of the "fewer than three providers" shapes

- **Issue:** The two added `verifyNoInteractions(txLifecyclePublisher)` assertions cover the "only two answer" and "one provider lags" cases. They do not cover the "exactly one provider answers" or "zero providers answer" cases, even though the threat statement is "no single-provider fact is ever emitted."
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java:162-190`.
- **Recommendation:** Add a test that delivers a single observation and asserts `verifyNoInteractions(txLifecyclePublisher)`, or rename the cited tests to avoid implying broader coverage than they provide.
- **Confidence:** Low

### 4. Row #3 cites test classes rather than specific named tests

- **Issue:** Rows #1, #2, and #6 cite exact `ClassName.methodName` references. Row #3 cites `EthereumFinalityPolicyTest`, `TronFinalityPolicyTest`, and `ReorgDetectorTest` without method names, making it harder to verify the claim and inconsistent with the rest of the table.
- **Evidence:** `SECURITY-THREAT-MODEL.md` row #3.
- **Recommendation:** Replace the class-level citations with specific named tests, e.g. `EthereumFinalityPolicyTest.shouldRequireBeaconFinalizedCheckpointForEthereumFinality`, `TronFinalityPolicyTest.shouldRequireSolidifiedBlockForTronFinality`, and the relevant `ReorgDetectorTest` method.
- **Confidence:** Low

### 5. Row #5's DB-grant claim has no cited test

- **Issue:** Row #5 correctly notes that append-only is enforced by DB grants in `V2__crypto_app_role_and_grants.sql`, but no test is cited for that grant. A future schema edit could add `UPDATE`/`DELETE` grants on `chain.observations` without failing any currently cited test.
- **Evidence:** `SECURITY-THREAT-MODEL.md` row #5; `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql:34`.
- **Recommendation:** Either cite `T01SkeletonRegressionTest` (after extending it to read the SQL file and assert `chain.observations` is granted only `INSERT, SELECT`) or add a small dedicated regression test that scans the migration file for disallowed grants.
- **Confidence:** Low

### 6. `T01SkeletonRegressionTest` method name still says `Tracks` while asserting `closed`

- **Issue:** The method `threatModelTracksThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched` was written when the expected status was `tracked`; it now asserts `closed`. The verb is imprecise and may confuse future readers.
- **Evidence:** `T01SkeletonRegressionTest.java:38`.
- **Recommendation:** Rename the method to `threatModelClosesThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched`. This is a single test-method rename with no production impact.
- **Confidence:** Low

### 7. No automated check that the cited tests actually exist

- **Issue:** The threat-model table cites test methods, but nothing parses or validates those references. A typo in a method name (e.g., `shouldTreatFactAsTrueWhenTwoOfThreeProvidersAgree` vs. the actual method name) would go unnoticed.
- **Evidence:** `SECURITY-THREAT-MODEL.md` rows #1–#6.
- **Recommendation:** Add a lightweight CI step or unit test that extracts the backtick-quoted references from the markdown table and checks that each `ClassName.methodName` exists on the test classpath. This is optional but would close a real regression risk.
- **Confidence:** Low

---

## Confirmations

- `SECURITY-THREAT-MODEL.md` rows #1–#6 now show `closed` with explicit test citations; rows #7–#8 remain untouched.
- The trailing paragraph correctly distinguishes the execution gap (row #3) from the infrastructure/ownership splits (rows #4/#5).
- `WatcherTest` now directly verifies that no lifecycle event is published when fewer than three providers answer, strengthening row #1's closure.
- `T01SkeletonRegressionTest` has been updated to assert `closed` for rows #1–#6, matching the new expected state.
- The document header no longer calls the file a stub.
