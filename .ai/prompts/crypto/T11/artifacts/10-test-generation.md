# crypto · T11 · Phase 10 — Test Generation

No production code changed in this phase. Six new test files: five under
`services/crypto/src/test/java/com/themistra/crypto/token/`, one under
`services/crypto/src/test/java/com/themistra/crypto/common/config/`.

## Test manifest

### `TokenAllowlistPropertiesTest` (7 tests, `ApplicationContextRunner`, mirrors `ProviderPropertiesTest`/`FinalityPropertiesTest`)

| Test | Verifies |
|---|---|
| `bindsAValidEntry` | Valid binding |
| `failsWhenEntriesMissing` | `@NotEmpty` |
| `failsWhenChainIsNotEthereumOrTron` | `@Pattern` |
| `failsWhenDecimalsIsNegative` | `@Min(0)` |
| `failsWhenDecimalsExceedsThirty` | Phase 9 fix (Kimi Phase 8 Issue 10) — `@Max(30)` |
| `failsWhenVersionIsNotPositive` | `@Positive` |
| `failsOnADuplicateChainContractAddressVersionTuple` | Phase 9 fix (Kimi Phase 8 Issue 6) — compact-constructor duplicate check |

### `TokenAllowlistTest` (6 tests, plain JUnit)

| Test | Verifies |
|---|---|
| `createPopulatesEveryFieldExactlyAsGiven` | AC1 — round-trip |
| `createRejectsNullArguments` | Null-guard discipline |
| `createRejectsANegativeDecimals` | Range check lower bound |
| `createRejectsADecimalsValueExceedingThirty` | Phase 9 fix (Kimi Phase 8 Issue 10) |
| `createAcceptsTheBoundaryDecimalsValueOfThirty` | Boundary (inclusive) |
| `hasNoPublicMutatorBeyondConstruction` | Append-only entity shape (reflection) |

### `TokenValidatorTest` (8 tests, Mockito, mocked `TokenAllowlistRepository`)

| Test | Verifies |
|---|---|
| `shouldIdentifyTokenByContractAddressNotSymbol` | package.md §8 named test — AC1, a deliberately misleading `symbol` doesn't change the outcome |
| `validateHasNoOverloadAcceptingASymbolParameter` | AC1 (reflection) |
| `shouldSurfaceUnknownTokenForNonAllowlistedContract` | package.md §8 named test — AC2 |
| `returnsEmptyWhenTheAllowlistHasNoRowsAtAllForThatChain` | AC2 |
| `rejectsNullChainOrContractAddress` | Null-guard discipline |
| `throwsForAnUnrecognizedChain` | AC7 |
| `logsAWarnLineOnUnknownToken` | AC2 (Logback `ListAppender`) |
| `doesNotLogWhenTheTokenIsFound` | AC2 (no false-positive logging) |

Note: the per-chain current-version query logic itself (Phase 9's `findCurrentVersionEntry` fix) lives
inside a `@Query` JPQL string, not Java branching logic — it is proven against a real Postgres by
`TokenAllowlistRepositoryIntegrationTest`, not by mocking here (a mock would only prove `TokenValidator`
delegates to the repository, not that the query itself is correct).

### `TokenAllowlistSeederTest` (6 tests, Mockito, mocked `TokenAllowlistRepository`, fixed `Clock`)

| Test | Verifies |
|---|---|
| `seedsEveryConfiguredEntryThatDoesNotAlreadyExist` | AC5 |
| `skipsAnEntryThatAlreadyExists` | Idempotent skip-if-exists |
| `catchesAConcurrentDuplicateInsertWhenTheRowNowActuallyExists` | AC8 |
| `rethrowsWhenTheDataIntegrityViolationIsNotABenignConcurrentDuplicate` | Phase 9 fix (Kimi Phase 8 Issue 5) |
| `continuesProcessingRemainingEntriesAfterOneEntryHitsTheBenignConcurrentRace` | AC8, multi-entry resilience |
| `runRejectsANullEntriesList` | Phase 9 fix (Kimi Phase 8 Issue 9) |

### `TokenModuleBoundaryTest` (1 test, mirrors `ProviderModuleBoundaryTest` exactly)

| Test | Verifies |
|---|---|
| `noMainSourceFileInTokenImportsAdapterObservationProviderOrQuorum` | AC6 |

### `TokenAllowlistRepositoryIntegrationTest` (4 tests, Docker-gated, full Spring context so the real seeder runs against the real `application.properties` entries)

| Test | Verifies |
|---|---|
| `seederPopulatesAllFourRealConfiguredEntriesOnStartup` | AC5, against the actual production config |
| `reRunningTheSeederIsIdempotent` | Idempotency, against a real DB |
| `findCurrentVersionEntryScopesToPerChainMaxVersionIndependently` | Phase 9 fix (Kimi Phase 8 Issues 1/3) — the headline correctness fix, proven end-to-end |
| `deleteFailsAtTheDatabaseLevel` | AC4 — real `crypto_app` grant enforcement |

## Test results

- `mvn -pl services/crypto test -Dtest=TokenAllowlistPropertiesTest,TokenAllowlistTest,TokenValidatorTest,TokenAllowlistSeederTest,TokenModuleBoundaryTest`
  — **28/28 passing**.
- `mvn -pl services/crypto -am test` (full module regression) — **340 tests, 332 passing, 8 errors**, all
  `IllegalState: … Docker environment …` (7 pre-existing from T02/T04/T08/T09/T10's own Testcontainers
  tests, plus this task's own new `TokenAllowlistRepositoryIntegrationTest`) — zero genuine failures,
  zero regressions in any previously-passing test (including `ChainBaselineMigrationIntegrationTest`,
  whose Phase 6 edits could not be exercised in this environment either, for the same Docker reason).
