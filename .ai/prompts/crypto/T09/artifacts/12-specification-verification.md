# crypto · T09 · Phase 12 — Specification Verification

**Task (verbatim, `tasks.md` #9):** Quorum evaluator. Implement `QuorumEvaluator` (pure 2-of-3 logic):
`AGREED` needs ≥2 matching; disagreement → `HELD` + `HeldFactAlerter` + persisted `QuorumDecision`;
never auto-resolve (L1, L2, R1–R3). Unit-test the agreement matrix exhaustively.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R1 — fact true only when ≥2-of-3 agree | Yes | `QuorumEvaluator.evaluate`/`largestMatchingGroupSize` (`QuorumEvaluator.java:39-65`) — exactly-3, `compareTo()==0`-based grouping, `AGREED` iff `agreeingCount≥2` | `QuorumEvaluatorTest.shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree` (named test) + 5 exhaustive-matrix tests | No | No |
| R2 — disagreement → HELD, ops-alerted, no downstream event | Yes | `QuorumDecisionService.evaluate` (`QuorumDecisionService.java:58-79`) — persists `HELD`, calls `HeldFactAlerter.alert` after a successful save; no Kafka/outbox call anywhere in `quorum/` | `QuorumDecisionServiceTest.shouldHoldFactAndAlertWhenProvidersDisagree` (named test), `.alerterIsInvokedOnlyOnHeldNeverOnAgreed` | No | No |
| R3 — HELD never auto-resolved | Yes | `QuorumDecision` has no mutator (`QuorumDecision.java`, entire file — only getters + `create`); `QuorumDecisionRepository` is never called with anything but `save` on a freshly-created instance | `QuorumDecisionServiceTest.shouldNeverAutoResolveDisagreementInPayersFavor` (named test); `QuorumDecisionTest.hasNoPublicMutatorBeyondConstruction` (reflection) | No | No |
| L1 — 2-of-3 quorum, not a tunable | Yes | `QuorumEvaluator.validate` rejects any list size ≠3 (`QuorumEvaluator.java:46-56`) — the threshold is hardcoded, no `@ConfigurationProperties` surface exists for it | `QuorumEvaluatorTest.rejectsAListOfOne/Two/Four/AnEmptyList` | No | No |
| L2 — disagreement → HELD, ops-alerted, never auto-resolved | Yes | Same evidence as R2/R3 rows | Same tests as R2/R3 rows | No | No |
| AC1 (exactly-3, non-null, `compareTo()==0`) | Yes | `QuorumEvaluator.java:46-56` (`validate`), `:59-73` (`largestMatchingGroupSize`) | `QuorumEvaluatorTest` — 14 tests covering matrix, size boundaries, null list/element | No | No |
| AC2 (HELD → alert-after-save, persist, no event) | Yes | `QuorumDecisionService.java:58-79` — reordered at Phase 9 so `save` precedes `alert` | `QuorumDecisionServiceTest.alertFiresOnlyAfterTheDecisionIsSuccessfullyPersisted`, `.aFailedSaveNeverTriggersAnAlert`, `.decisionIsAlreadyPersistedWhenTheAlerterThrows` | No | No |
| AC3 (never auto-resolve) | Yes | Same as R3 | Same as R3 | No | No |
| AC4 (counts persisted exactly) | Yes | `QuorumDecision.create` (`QuorumDecision.java:80-93`) | `QuorumDecisionServiceTest.agreeingCountAndProviderCountOnThePersistedDecisionMatchTheEvaluatorsComputation`; `QuorumDecisionRepositoryIntegrationTest.savedQuorumDecisionRoundTripsEveryFieldIncludingTheFactTypeAndOutcomeConversion` (real Postgres) | No | No |
| AC5 (no UPDATE/DELETE) | Yes | No such call exists anywhere in `quorum/`; DB grant (`V2__crypto_app_role_and_grants.sql:34`) enforces it structurally | `QuorumDecisionTest.hasNoPublicMutatorBeyondConstruction`; `QuorumDecisionRepositoryIntegrationTest.repositoryHasNoUpdateOrDeleteMethodReachableAtTheDatabaseLevel` (real DB-enforced) | No | No |
| AC6 (exhaustive agreement matrix) | Yes | `QuorumEvaluator.largestMatchingGroupSize`'s 3-branch logic (`QuorumEvaluator.java:59-73`) covers all 5 meaningful 3-element equivalence patterns | `QuorumEvaluatorTest` — all-match, all 3 distinct 2-1-split variants, all-distinct | No | No |
| AC7 (alerter iff HELD) | Yes | `QuorumDecisionService.java:75-77` — single `if (result.outcome() == QuorumOutcome.HELD)` branch | `QuorumDecisionServiceTest.alerterIsInvokedOnlyOnHeldNeverOnAgreed` | No | No |
| AC8 (duplicate-provider rejected) | Yes | `QuorumDecisionService.rejectDuplicateProviders` (`QuorumDecisionService.java:90-98`) | `QuorumDecisionServiceTest.rejectsDuplicateProviderAnswersBeforeAnyCollaboratorIsInvoked`, `.existingDecisionIsCheckedBeforeDuplicateProviderRejection` | No | No |
| AC9 (scale-invariant amount comparison) | Yes | `compareTo()==0` in `largestMatchingGroupSize` (`QuorumEvaluator.java:60-62`), not `equals()` | `QuorumEvaluatorTest.bigDecimalAnswersWithDifferentScaleButEqualValueAreTreatedAsMatching`; `QuorumDecisionServiceTest.bigDecimalAmountAnswersWithDifferentScaleAreTreatedAsAgreedThroughTheFullService` (end-to-end) | No | No |

## Amendments (Phase 3, 10 findings; Phase 8, 8 findings) — verification

**Phase 3 (design challenge), all 10 verified implemented as decided:** exactly-3 requirement
(`QuorumEvaluator.java:47-50`), `compareTo()==0` grouping (`:60-62`), null-element rejection
(`:51-55`), `ProviderAnswer` duplicate-provider guard (`QuorumDecisionService.java:90-98`), `AGREED`
consensus-not-truth documented (`QuorumEvaluator.java:26-33` Javadoc), no agreed-value column added
(confirmed absent from `QuorumDecision`'s field list), no `ObservationLog` internalization (confirmed
absent from `QuorumDecisionService`'s dependencies — only `QuorumEvaluator`/`QuorumDecisionRepository`/
`HeldFactAlerter`/`Clock`), interim log-based `HeldFactAlerter` (`HeldFactAlerter.java`, `@Component`,
one `logger.error` call), `quorum_decisions` single-decision-per-fact documented
(`QuorumDecisionService.java` class Javadoc), `factType` reuse of `observation.FactType` (`QuorumDecision.java:61`).

**Phase 8 (independent review), all 8 verified with their approved dispositions:** the 4 shared with
self-review (alert-after-save reorder, null guards, `short` range check, duplicated converter
documented) plus the pre-flight existing-decision check (`QuorumDecisionService.java:82-87`,
`rejectExistingDecision`), the exactly-3 rule reinforced rather than reopened (`QuorumEvaluator.java:26-33`
Javadoc addition), the `HeldFactAlerter` error-level-by-design Javadoc note (`HeldFactAlerter.java:11-17`),
and the agreed-value gap re-confirmed as already-decided (no code change, matches Phase 3 Amendment #5).

**One deviation, disclosed (Phase 6):** `QuorumDecision.FactTypeDbConverter` (`QuorumDecision.java:137-153`)
duplicates `observation.FactType.DbConverter`'s mapping locally, because the original is package-private
and the frozen brief forbids modifying `FactType.java`. Not a violation of any `R`/`L` requirement — a
disclosed, reasoned tradeoff (DRY vs. respecting an approved file boundary), re-flagged at Phase 7 and
Phase 8 independently, resolution unchanged both times (no action needed within this task's scope).

## Files-to-create / Files-to-modify conformance

All seven files listed under "Files to Create" in the frozen brief exist at their exact specified paths
(`ProviderAnswer.java`, `QuorumOutcome.java`, `QuorumEvaluator.java`, `QuorumDecision.java`,
`QuorumDecisionRepository.java`, `HeldFactAlerter.java`, and the coordinator — named
`QuorumDecisionService`, Phase 5). "Files to Modify: None expected" held — no `pom.xml` change, no
`application.properties` change (no new dependency, no new config surface, both correctly anticipated).
No file under "Files NOT to Modify" was touched: `V1__chain_baseline.sql`,
`V2__crypto_app_role_and_grants.sql`, `V3__crypto_app_outbox_grant.sql` (T02), `observation/FactType.java`
(T08, consumed via its enum type only — its converter was duplicated, not the file itself modified),
`observation/ObservationLog.java` (T08, referenced in documentation only, never called),
`common/ClockConfig.java` (T04), and nothing under `spec/`.

## Required Tests conformance

All required tests from the frozen brief exist, plus the Phase 11 (Kimi)-driven additions layered on
top (all human-approved 2026-09-03): `ProviderAnswerTest` (2), `QuorumOutcomeTest` (1), and 9 test
additions/enhancements across `QuorumDecisionServiceTest`/`HeldFactAlerterTest`/
`QuorumDecisionRepositoryIntegrationTest`. Current suite state (last full run, this session): 278
module tests total, 272 passing, 6 errors — all Docker-environment-unavailable (`IllegalState: …
Docker environment …`), a pre-existing, disclosed environment limitation (5 pre-existing from
T02/T04/T08, 1 new from this task's own `QuorumDecisionRepositoryIntegrationTest`), not a code defect.
Zero genuine failures.

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every class named in the frozen brief exists, is wired
together as specified, and every acceptance criterion has direct evidence and a passing test (subject
only to the environment's lack of Docker, which blocks *execution* of this task's own one integration
test, not its existence or correctness — it compiles cleanly and mirrors the T08-established,
already-proven pattern).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC9, see matrix above, each with
file:line evidence and at least one passing (or Docker-gated-but-compiling) test.

**(3) Does it violate any LOCKED decision?** No. L1 (exactly 2-of-3, not a tunable) and L2 (HELD,
ops-alerted, never auto-resolved) are both implemented exactly as decided, and both were actively
defended against reopening at the Phase 9 gate (Kimi Phase 8 Issue 5's attempt to relax the exactly-3
rule was rejected with reasoning). No cross-module import violation: `quorum/` imports only
`observation.FactType` (a small, stable, spec-fixed enum) and `common`'s `Clock` bean, nothing from
`adapter/`, `events/`, or any sibling feature module.

**(4) Remaining risks?**
- A race between `QuorumDecisionService`'s pre-flight existing-decision check and the subsequent
  `save` is not eliminated by application code (Phase 11 Gap 6) — two concurrent evaluations of the
  same fact could both pass the pre-flight check before either saves, with the second failing at the
  database's `uq_quorum_tx_fact` constraint as a raw `DataIntegrityViolationException` rather than the
  friendlier `IllegalStateException`. This is a documented, accepted risk (re-evaluation is out of this
  task's own scope per Amendment #7; task 18's reorg handling will need to address the underlying
  single-decision-per-fact constraint regardless).
- `QuorumDecision.create`'s null-permissiveness for `chain`/`txHash`/`factType`/`outcome`/`decidedAt`
  (Phase 11 Gap 3) and lack of `agreeingCount≤providerCount` semantic validation (Gap 4) remain
  un-guarded at construction time — both would surface only as a JPA/DB-level failure at persist time
  today, since the only real caller (`QuorumDecisionService`) never supplies invalid values. Flagged,
  not fixed, since fixing either requires a production code change outside Phase 11's test-only scope.
- No ArchUnit enforcement exists yet (in this task or anywhere in the codebase) to catch a future
  Java-level mutating-method addition to `QuorumDecision`/`QuorumDecisionRepository` before it would
  reach the (still DB-grant-enforced) no-UPDATE/DELETE boundary. Flagged for a future dedicated
  cross-cutting task, not this one.
- `QuorumDecisionRepositoryIntegrationTest` has never actually executed in this environment (Docker
  unavailable throughout this session) — it compiles and is structurally sound (mirroring the
  already-proven `ObservationRepositoryIntegrationTest` pattern), but its assertions are unverified
  against a real Postgres until Docker is available.

## Verdict

**PASS** — every requirement, LOCKED decision, and acceptance criterion for T09 is implemented with
file:line evidence and test coverage; both Phase 3 and Phase 8 Kimi reviews were fully triaged with
reasoned accept/reject dispositions (including two deliberate refusals to reopen already-decided
amendments); remaining risks are pre-existing environment limitations or explicitly accepted-by-design/
deferred-to-a-later-task risks, not defects.
