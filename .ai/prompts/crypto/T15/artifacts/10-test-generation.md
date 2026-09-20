# crypto · T15 · Phase 10 — Test Generation

No production code changed in this phase. Six new test files, all in
`services/crypto/src/test/java/com/themistra/crypto/watch/` (46 tests total).

## Test manifest

### `WatchTest` (3 tests, unit)

| Test | Verifies |
|---|---|
| `registerAssignsEveryFieldAndDefaultsToRegisteredStatus` | AC1 |
| `registerRejectsANullWatchId` | Null-safety (representative) |
| `registerRejectsANullChain` | Null-safety (representative) |

### `ChainCursorTest` (3 tests, unit)

| Test | Verifies |
|---|---|
| `placeholderAssignsTheSentinelLastBlockAndNullLastFinalizedBlock` | AC2 |
| `placeholderRejectsANullChain` | Null-safety (representative) |
| `placeholderRejectsANullWatchId` | Null-safety (representative) |

### `WatchServiceTest` (25 tests, unit — mocked repositories, real `AddressValidator`, `Clock.fixed`)

| Test | Verifies |
|---|---|
| `shouldRegisterWatchAndReturnWatchId` | package.md §8 named test — R18, AC1 |
| `registerPersistsAChainCursorWithTheMatchingWatchIdAndChain` | AC2 |
| `registerCallsChainCursorRepositorySaveExactlyOnce` | AC2 (Phase 8 Finding 4) |
| `twoSequentialRegisterCallsProduceTwoDistinctWatchIds` | Phase 3 Finding 3 (documented non-idempotency risk, locked in) |
| `registerRejectsAMalformedExpectedAmount` (×8, parameterized: `"1.5"`, `"1e18"`, `"-0"`, `"0"`, `"-5"`, `"abc"`, `"01"`, `""`) | AC5 (Phase 3 Finding 2) |
| `registerRejectsAnExpectedAmountOverSeventyEightDigits` | AC5 (Phase 8/9 Finding 2) |
| `registerAcceptsAnExpectedAmountOfExactlySeventyEightDigits` | AC5 boundary |
| `registerRejectsAnExpiresAtOfExactlyNow` | AC5 boundary |
| `registerRejectsAPastExpiresAt` | AC5 |
| `registerAcceptsAnExpiresAtOneNanosecondInTheFuture` | AC5 boundary |
| `registerRejectsAStructurallyInvalidEvmAddress` | AC6 (Phase 3 Finding 1, L8) |
| `registerRejectsAStructurallyInvalidTokenContractAddress` | AC6 — confirms both address fields validated independently |
| `registerRejectsAnUncheckedLowercaseEvmAddress` | AC6 — strict EIP-55 policy (L8) applies here too |
| `registerAcceptsAValidTronAddress` | AC6 |
| `registerRejectsAStructurallyInvalidTronAddress` | AC6 |
| `shouldUnregisterWatchOnDelete` | package.md §8 named test — R19, AC3 |
| `unregisterThrowsWatchNotFoundExceptionForAnUnknownWatchId` | AC4 |
| `unregisterDoesNotThrowWhenTheConditionalUpdateAffectsZeroRows` | AC4 (idempotent no-op, service-layer behavior) |

### `WatchControllerTest` (9 tests, `@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)`, mocked `WatchService`, real bean validation)

| Test | Verifies |
|---|---|
| `shouldRegisterWatchAndReturnWatchId` | package.md §8 named test — R18, AC1 (VERBATIM response shape) |
| `postWithAMissingRequiredFieldReturnsProblemJsonValidationFailure` | AC5, Phase 8/9 Finding 1 |
| `postWithAnUnrecognizedChainReturnsBadRequest` | AC5 |
| `postWithAnOversizeAddressReturnsBadRequest` | AC5 (Phase 3 Finding 7) |
| `postWithMalformedJsonReturnsProblemJsonMalformedBody` | Phase 8/9 Finding 1 |
| `postMappedToAnInvalidWatchRequestExceptionReturnsProblemJsonBadRequest` | AC6, `WatchExceptionHandler` |
| `shouldUnregisterWatchOnDelete` | package.md §8 named test — R19, AC3 |
| `deleteWithAnInvalidPathUuidReturnsProblemJsonBadRequest` | AC4, Phase 8/9 Finding 1/8 |
| `deleteForAnUnknownWatchIdReturnsProblemJsonNotFound` | AC4, `WatchExceptionHandler` |

### `WatchRepositoryIntegrationTest` (5 tests, Testcontainers Postgres, `crypto_app` datasource)

| Test | Verifies |
|---|---|
| `fullRegisterThenUnregisterFlowPersistsCorrectlyAndIsIdempotent` | AC1/AC2/AC3/AC4 end-to-end against real Postgres |
| `unregisterOnAnUnknownWatchIdThrowsWatchNotFoundException` | AC4 |
| `unregisterOnAManuallySeededExpiredWatchIsANoOp` | AC4 (Phase 3 Finding 6 required test) |
| `cryptoAppCanInsertSelectAndUpdateButNotDeleteOnWatches` | AC7 |
| `cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnChainCursors` | AC7 |

### `WatchModuleBoundaryTest` (1 test, source-scan)

| Test | Verifies |
|---|---|
| `noMainSourceFileInWatchImportsBeyondItsAllowedTokenTypeOrAnyForbiddenPackage` | AC8, L15 — allow-lists exactly `token.AddressValidator` |

## Test results

- `mvn -pl services/crypto test -Dtest="Watch*"` plus `-Dtest=ChainCursorTest` — **46/46 passing**.
- `mvn -pl services/crypto -am test` (full module regression) — **499 tests, 493 passing, 6 failures**,
  the same pre-existing, disclosed set in `ObservationRepositoryIntegrationTest`,
  `ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`, and
  `TokenAllowlistRepositoryIntegrationTest` (T08-T11's own work, unrelated to `watch/` or
  `common/ApiExceptionHandler.java`) — zero regressions, zero new failures.
