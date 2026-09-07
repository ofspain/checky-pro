# crypto · T14 · Phase 10 — Test Generation

No production code changed in this phase. Three new test files, all in a new
`services/crypto/src/test/java/com/themistra/crypto/finality/` package (18 tests total).

## Test manifest

### `EthereumFinalityPolicyTest` (9 tests)

| Test | Verifies |
|---|---|
| `shouldRequireBeaconFinalizedCheckpointForEthereumFinality` | package.md §8 named test — R6, AC1 |
| `isFinalAtTheBoundaryWhenTxBlockEqualsTheFinalizedCheckpoint` | AC1 boundary |
| `isNotFinalWhenTxBlockIsOneAboveTheFinalizedCheckpoint` | AC1 boundary |
| `isFinalAtTheGenesisBoundaryWhenBothBlockNumbersAreZero` | AC1 (Phase 9, Kimi Issue 3) |
| `ignoresCurrentBlockNumberFarAheadOfBothOtherFields` | AC1, "scripted chain heads" (task statement) |
| `ignoresCurrentBlockNumberFarBehindBothOtherFields` | AC1 (Phase 9, Kimi Issue 4) |
| `chainReturnsEthereum` | AC3 |
| `isFinalThrowsOnNullStatus` | Constraints — null handling |
| `appliesTheSameRawComparisonAsTronFinalityPolicyForTheSameStatus` | Phase 9, Kimi Issue 2 — locks in the documented "no chain self-check" contract (Phase 3 Finding 1) |

### `TronFinalityPolicyTest` (8 tests)

| Test | Verifies |
|---|---|
| `shouldRequireSolidifiedBlockForTronFinality` | package.md §8 named test — R7, AC2 |
| `isFinalAtTheBoundaryWhenTxBlockEqualsTheSolidifiedBlock` | AC2 boundary |
| `isNotFinalWhenTxBlockIsOneAboveTheSolidifiedBlock` | AC2 boundary |
| `isFinalAtTheGenesisBoundaryWhenBothBlockNumbersAreZero` | AC2 (Phase 9, Kimi Issue 3) |
| `ignoresCurrentBlockNumberFarAheadOfBothOtherFields` | AC2, "scripted chain heads" (task statement) |
| `ignoresCurrentBlockNumberFarBehindBothOtherFields` | AC2 (Phase 9, Kimi Issue 4) |
| `chainReturnsTron` | AC3 |
| `isFinalThrowsOnNullStatus` | Constraints — null handling |

(The cross-policy "no self-check" test lives once, in `EthereumFinalityPolicyTest`, since it needs both
classes — see manifest above.)

### `FinalityModuleBoundaryTest` (1 test)

| Test | Verifies |
|---|---|
| `noMainSourceFileInFinalityImportsBeyondItsAllowedAdapterTypesOrAnyForbiddenPackage` | AC6, module boundary (Phase 3 Finding 2) — source-scan: no import of `observation`/`provider`/`quorum`/`token`/`events`; any `adapter` import is exactly `Chain` or `FinalityStatus` |

## Test results

- `mvn -pl services/crypto test -Dtest=EthereumFinalityPolicyTest,TronFinalityPolicyTest,FinalityModuleBoundaryTest`
  — **18/18 passing**.
- `mvn -pl services/crypto -am test` (full module regression) — **417 tests, 409 passing, 8 errors**, all
  `IllegalState: … Docker environment …` (the same pre-existing set carried unchanged from T13's own
  baseline — this task introduces no persistence layer), zero genuine failures, zero regressions in any
  previously-passing test.
