# crypto · T10 · Phase 10 — Test Generation

No production code changed in this phase. Five new test files: four under
`services/crypto/src/test/java/com/themistra/crypto/provider/`, one under
`services/crypto/src/test/java/com/themistra/crypto/common/config/`.

## Test manifest

### `ProviderHealthPropertiesTest` (4 tests, `ApplicationContextRunner`, mirrors `FinalityPropertiesTest`)

| Test | Verifies |
|---|---|
| `bindsAPositiveThreshold` | Valid binding |
| `failsWhenThresholdMissing` | `@Positive` requires a value |
| `failsWhenThresholdIsZero` | `@Positive` boundary |
| `failsWhenThresholdIsNegative` | `@Positive` boundary |

### `ProviderHealthTest` (6 tests, plain JUnit)

| Test | Verifies |
|---|---|
| `createIsHealthyByDefaultWithLastOkAndLastDisagreementUnset` | AC1 — matches DB `DEFAULT TRUE` |
| `createRejectsNullArguments` | Phase 9 fix (Kimi Phase 8 Issue 4) |
| `markHealthySetsHealthyAndLastOkAtButDoesNotClearLastDisagreementAt` | Amendment (Kimi Phase 3 Issue 10) — historical marker semantics |
| `markUnhealthySetsHealthyFalseButDoesNotTouchLastOkAt` | Mutator isolation |
| `recordDisagreementSetsLastDisagreementAtButDoesNotTouchHealthy` | Mutator isolation |
| `hasOnlyTheThreeNamedMutatorsBeyondGetters` | No raw setters (reflection) |

### `ProviderDegradedPublisherTest` (5 tests, Mockito, mocked `OutboxPublisher`)

| Test | Verifies |
|---|---|
| `publishUsesTheFixedAggregateTypeAndEventType` | AC6/AC7 — `"provider"` / `"chain.provider.degraded"` |
| `publishBuildsTheDocumentedPayloadShape` | AC7 — `chain`, `provider`, `reason`, `occurredAt` |
| `twoPublishesWithIdenticalArgumentsProduceDifferentIdempotencyKeys` | Amendment (Kimi Phase 3 Issue 7) — UUID component |
| `publishRejectsAChainContainingAColon` | Phase 9 fix (Kimi Phase 8 Issue 5) |
| `publishRejectsAProviderContainingAColon` | Phase 9 fix (Kimi Phase 8 Issue 5) |

### `ProviderHealthTrackerTest` (10 tests, Mockito with a map-backed fake repository behavior, fixed `Clock`)

| Test | Verifies |
|---|---|
| `shouldEmitProviderDegradedWhenAProviderIsUnhealthy` | package.md §8 named test — R5, AC2 |
| `recordUnhealthyOnAnAlreadyUnhealthyProviderDoesNotReemitOrResave` | AC2 |
| `recordHealthyNeverEmitsAnEvent` | No recovery event exists anywhere in this spec |
| `recordDisagreementBelowThresholdDoesNotTransitionAndReachingItDoes` | AC3 |
| `recordHealthyResetsTheDisagreementCounter` | AC3 |
| `recordDisagreementWhileAlreadyUnhealthyUpdatesTimestampButNeverTouchesTheCounterOrPublishes` | AC3, Amendment (Kimi Phase 3 Issue 6) |
| `recordHealthyRejectsNullArguments` | Null-guard discipline |
| `recordUnhealthyRejectsNullArguments` | Null-guard discipline |
| `recordDisagreementRejectsNullArguments` | Null-guard discipline |
| `distinctProvidersOnTheSameChainAreTrackedIndependently` | AC1 — per-`(chain, provider)` isolation |

### `ProviderHealthRepositoryIntegrationTest` (4 tests, Docker-gated Testcontainers, mirrors `QuorumDecisionRepositoryIntegrationTest`)

| Test | Verifies |
|---|---|
| `savedProviderHealthRoundTripsEveryField` | AC1 — real Postgres round-trip |
| `findByChainAndProviderReturnsTheMatchingRowAndEmptyWhenNoneExists` | Derived-query finder, real DB |
| `anUpdateToAnAlreadyPersistedRowSucceeds` | AC4 — the first integration test in this service proving `UPDATE` succeeds, not fails (inverse of every prior append-only entity's own test) |
| `deleteStillFailsAtTheDatabaseLevel` | AC4 — `V4` grants no `DELETE` |

## Test results

- `mvn -pl services/crypto test -Dtest=ProviderHealthPropertiesTest,ProviderHealthTest,ProviderDegradedPublisherTest,ProviderHealthTrackerTest`
  — **25/25 passing**.
- `mvn -pl services/crypto -am test` (full module regression) — **304 tests, 297 passing, 7 errors**, all
  `IllegalState: … Docker environment …` (6 pre-existing from T02/T04/T08/T09's own Testcontainers
  tests, plus this task's own new `ProviderHealthRepositoryIntegrationTest`) — zero genuine failures,
  zero regressions in any previously-passing test.
- One test-authoring fix made during this phase: `ProviderHealthTrackerTest`'s `@BeforeEach`-configured
  repository stubs needed `lenient()` — the three null-guard tests throw before ever reaching the
  repository, so Mockito's strict-stubbing mode flagged those specific test methods'
  never-invoked-in-that-method stubs as `UnnecessaryStubbingException` until marked lenient (a shared
  `@BeforeEach` setup covering both "normal" and "fails before any repository call" test shapes is the
  cause, not a defect in the production code).
