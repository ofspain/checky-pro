# crypto · T09 · Phase 10 — Test Generation

No production code changed in this phase. Five new test files under
`services/crypto/src/test/java/com/themistra/crypto/quorum/`.

## Test manifest

### `QuorumEvaluatorTest` (14 tests, pure logic, no mocks/Spring)

| Test | Verifies |
|---|---|
| `shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree` | package.md §8 named test — R1 |
| `allThreeMatchIsAgreedWithAgreeingCountThree` | AC1, AC6 (exhaustive matrix: all-match) |
| `firstAndSecondMatchThirdDiffersIsAgreedWithAgreeingCountTwo` | AC1, AC6 (2-1 split, variant 1) |
| `firstAndThirdMatchSecondDiffersIsAgreedWithAgreeingCountTwo` | AC1, AC6 (2-1 split, variant 2) |
| `secondAndThirdMatchFirstDiffersIsAgreedWithAgreeingCountTwo` | AC1, AC6 (2-1 split, variant 3) |
| `allThreeDistinctIsHeldWithAgreeingCountOne` | AC1, AC6 (exhaustive matrix: all-distinct) |
| `agreedOnAFalseValueIsStillAgreedNotHeld` | Amendment #4 — AGREED means consensus, not boolean truth |
| `bigDecimalAnswersWithDifferentScaleButEqualValueAreTreatedAsMatching` | AC9 — scale-invariant `compareTo()` comparison |
| `rejectsAnEmptyList` | AC1 — exactly-3 requirement, size 0 |
| `rejectsAListOfOne` | AC1 — exactly-3 requirement, size 1 |
| `rejectsAListOfTwo` | AC1 — exactly-3 requirement, size 2 |
| `rejectsAListOfFour` | AC1 — exactly-3 requirement, size 4 |
| `rejectsANullList` | AC1 — null-list guard |
| `rejectsANullElementWithinTheList` | AC1, Amendment #2 — null-element guard |

### `QuorumDecisionTest` (5 tests, plain JUnit)

| Test | Verifies |
|---|---|
| `createPopulatesEveryFieldExactlyAsGiven` | AC4 — every field round-trips via `create` |
| `createPersistsAHeldOutcomeJustAsFaithfullyAsAgreed` | AC4 — `HELD` outcome equally faithful |
| `createRejectsANegativeAgreeingCount` | Phase 9 fix (Kimi Phase 8 Issue 3) — range check |
| `createRejectsAProviderCountExceedingShortRange` | Phase 9 fix (Kimi Phase 8 Issue 3) — range check |
| `hasNoPublicMutatorBeyondConstruction` | AC5 — grant-enforced immutability (reflection) |

### `HeldFactAlerterTest` (1 test, Logback `ListAppender`)

| Test | Verifies |
|---|---|
| `alertLogsAtErrorLevelWithChainTxHashFactTypeAndEveryProviderAnswer` | AC2, AC7 — interim log-based alert content and level |

### `QuorumDecisionServiceTest` (11 tests, Mockito for repository/alerter, real `QuorumEvaluator`)

| Test | Verifies |
|---|---|
| `shouldHoldFactAndAlertWhenProvidersDisagree` | package.md §8 named test — R2, AC2 |
| `shouldNeverAutoResolveDisagreementInPayersFavor` | package.md §8 named test — R3, AC3 |
| `alerterIsInvokedOnlyOnHeldNeverOnAgreed` | AC7 |
| `alertFiresOnlyAfterTheDecisionIsSuccessfullyPersisted` | Phase 9 fix (Kimi Phase 8 Issue 1) — alert-after-save ordering |
| `aFailedSaveNeverTriggersAnAlert` | Phase 9 fix (Kimi Phase 8 Issue 1) — corollary |
| `agreeingCountAndProviderCountOnThePersistedDecisionMatchTheEvaluatorsComputation` | AC4 |
| `decidedAtUsesTheInjectedClockNotWallClockTime` | Fixed-`Clock` discipline (agents.md) |
| `rejectsDuplicateProviderAnswersBeforeAnyCollaboratorIsInvoked` | AC8 (Amendment #6) |
| `rejectsReEvaluationOfAnAlreadyDecidedFactBeforeAnyOtherCollaboratorIsInvoked` | Phase 9 fix (Kimi Phase 8 Issue 7) — pre-flight existing-decision check |
| `rejectsANullAnswersList` | Phase 9 fix (Kimi Phase 8 Issue 2) — null guard |
| `rejectsANullChain` | Phase 9 fix (Kimi Phase 8 Issue 2) — null guard |

### `QuorumDecisionRepositoryIntegrationTest` (4 tests, Docker-gated Testcontainers, mirrors `ObservationRepositoryIntegrationTest`)

| Test | Verifies |
|---|---|
| `savedQuorumDecisionRoundTripsEveryFieldIncludingTheFactTypeAndOutcomeConversion` | AC4 — real Postgres round-trip, `FactType`/`QuorumOutcome` conversion |
| `findByChainAndTxHashAndFactTypeReturnsTheMatchingDecision` | Derived-query finder, real DB |
| `repositoryHasNoUpdateOrDeleteMethodReachableAtTheDatabaseLevel` | AC5 — real `crypto_app` grant enforcement |
| `aSecondDecisionForTheSameChainTxHashFactTypeViolatesTheUniqueConstraint` | Amendment #7 — `uq_quorum_tx_fact`, proven against real Postgres |

## Test results

- `mvn -pl services/crypto test -Dtest=QuorumEvaluatorTest,QuorumDecisionTest,HeldFactAlerterTest,QuorumDecisionServiceTest`
  — **31/31 passing**.
- `mvn -pl services/crypto -am test` (full module regression) — **269 tests, 263 passing, 6 errors**, all
  `IllegalState: … Docker environment …` (5 pre-existing from T02/T04/T08's own Testcontainers tests,
  plus this task's own new `QuorumDecisionRepositoryIntegrationTest`) — zero genuine failures, zero
  regressions in any previously-passing test.
- One test-authoring fix made during this phase: `rejectsDuplicateProviderAnswersBeforeAnyCollaboratorIsInvoked`
  originally asserted `verifyNoInteractions(repository, alerter)`, which is incorrect given the actual
  (Phase 9-approved) invocation order — the pre-flight existing-decision check legitimately calls
  `repository.findByChainAndTxHashAndFactType` *before* duplicate-provider rejection runs. Corrected to
  assert `repository.save` is never called and the alerter has no interactions, which is what AC8
  actually requires.
