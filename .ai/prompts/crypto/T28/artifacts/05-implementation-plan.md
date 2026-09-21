# crypto · T28 · Phase 5 — Implementation Plan

## Files to modify

- `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java` — two-line additive
  strengthening, Finding #3.
- `SECURITY-THREAT-MODEL.md` — `Status` column (rows #1–#6) + header wording.

## Files to create

None.

## Exact edits

### 1. `WatcherTest.java` — `doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering` (currently line 170)

```java
        verifyNoInteractions(quorumDecisionService);
```
becomes:
```java
        verifyNoInteractions(quorumDecisionService);
        verifyNoInteractions(txLifecyclePublisher);
```

### 2. `WatcherTest.java` — `laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers` (currently
   lines 188-189)

```java
        verifyNoInteractions(quorumDecisionService);
        verify(providerHealthTracker).recordUnhealthy("ETHEREUM", "provider-c", DegradationReason.LAGGING);
```
becomes:
```java
        verifyNoInteractions(quorumDecisionService);
        verifyNoInteractions(txLifecyclePublisher);
        verify(providerHealthTracker).recordUnhealthy("ETHEREUM", "provider-c", DegradationReason.LAGGING);
```

No import changes needed — `verifyNoInteractions` is already statically imported (line 53);
`txLifecyclePublisher` is already an existing field (line 94).

### 3. `SECURITY-THREAT-MODEL.md` — header (line 3)

```
> Status: **stub — must be completed before the first line of crypto-service code.**
```
becomes:
```
> Status: **updated for crypto-service T28 — rows #1–#6 verified and closed (2026-09-21).**
```

### 4. `SECURITY-THREAT-MODEL.md` — table `Status` column, rows #1–#6

Each `tracked` → `closed`, with the verifying test(s) appended in the same cell. Exact per-row text
(derived directly from Phase 4's dispositions, not invented fresh at implementation time):

- Row 1: `closed — QuorumEvaluatorTest.shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree;
  WatcherTest.doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering /
  .laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers`
- Row 2: `closed — TokenValidatorTest.shouldIdentifyTokenByContractAddressNotSymbol /
  .shouldSurfaceUnknownTokenForNonAllowlistedContract`
- Row 3: `closed (unit-level) — EthereumFinalityPolicyTest, TronFinalityPolicyTest, ReorgDetectorTest;
  integration-level proof (EndToEndIntegrationTest.reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor)
  not yet executed — Docker unavailable in this environment`
- Row 4: `closed (code-path only) — KmsSignerArchitectureTest (R22/L11); IAM/runtime half
  (EKS IAM roles, KMS key policy) is an infrastructure control, not tested here`
- Row 5: `closed (crypto-service portion) — ObservationLogTest.recordAttemptsTheS3WriteBeforeThePostgresInsert;
  append-only enforced by DB grants (V2__crypto_app_role_and_grants.sql: crypto_app has only
  INSERT+SELECT on chain.observations); S3 Object Lock/WORM is an infrastructure control, not
  Java-testable; hash-chain ledger + on-chain anchor are Payment-Service-owned (ARCHITECTURE.md §6.5),
  out of this service's scope`
- Row 6: `closed — AddressPoisoningDetectorTest.shouldFlagAddressPoisoningOnPrefixSuffixSimilarity`

Rows #7–#8 and every other column: unchanged.

## Public/private methods

N/A — no new methods; `WatcherTest`'s two edits are additive assertion lines inside existing test
methods.

## Entities/Repositories/Services used

N/A — verification-only task.

## Tests required

None new (Phase 2/4, unchanged) — the two-line `WatcherTest` strengthening is the only test-code touch.

## Execution order

1. Apply the two `WatcherTest.java` edits.
2. Run `mvn -pl services/crypto -am test -Dtest=WatcherTest` to confirm both strengthened tests still
   pass.
3. Run the full `mvn -pl services/crypto -am verify` and record the real result — expect the same shape
   as T27 (697 or 698 tests, 0 failures, 14 Docker-only errors), confirming AC1a and honestly
   documenting AC1b's deferred portion.
4. Apply the `SECURITY-THREAT-MODEL.md` edits (header + `Status` column, rows #1–#6 only).
5. Re-read the file to confirm rows #7–#8 and every other column are untouched.
6. Write Phase 6's implementation notes with the full row-by-row citation and the fresh verification
   result.
