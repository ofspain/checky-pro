# crypto · T09 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS** (`artifacts/12-specification-verification.md`). Proceeding to prepare T09
for merge. Branches off `main`; `main` remains deployable throughout — no commit in this task touches
anything outside `services/crypto/` (plus this task's own `.ai/prompts/crypto/T09/` artifacts).

## Commit title

```
crypto: add 2-of-3 quorum evaluator with HELD alerting (T09)
```

## Commit message

```
crypto: add 2-of-3 quorum evaluator with HELD alerting (T09)

Implement QuorumEvaluator, the pure arbitration rule the platform exists
to provide: given exactly 3 provider answers for one fact, it determines
the largest compareTo()==0-matching group and reports AGREED when that
group is at least 2, HELD otherwise. Exactly-3 (not a generalized N-of-M
rule) matches L1's own "2-of-3 quorum" wording and eliminates tied-group
ambiguity structurally; compareTo() instead of equals() makes BigDecimal
AMOUNT comparisons scale-invariant.

QuorumDecisionService composes the evaluator with HeldFactAlerter (an
interim, structured error-level log line - no external paging
integration exists yet) and QuorumDecisionRepository into the single
"evaluate and persist" operation R1-R3/L1-L2 describe. A HELD outcome is
alerted only after the decision is durably persisted, never before, so
an alert always corresponds to a real row. A pre-flight check against
the existing uq_quorum_tx_fact constraint fails fast with a named
IllegalStateException rather than letting re-evaluation of the same
fact surface as a raw database error.

QuorumDecision maps quorum_decisions (T02, frozen) exactly as shipped -
fully immutable post-construction, matching crypto_app's INSERT/SELECT-
only grant. factType reuses observation.FactType (T08); its converter
is duplicated locally rather than widening FactType's own visibility,
preserving T08's already-approved file boundary.

Kimi design/independent/test review findings (Phases 3, 8, 11) were
triaged and folded in - most notably the alert-after-persist reordering,
explicit null guards, a bounds check on the int-to-short count mapping,
and the pre-flight duplicate-decision check. Two review findings that
would have reopened already-decided amendments (relaxing the exactly-3
rule; adding an agreed-value column) were deliberately rejected with
reasoning recorded in the review-resolution artifacts.

Testing gated on Docker (QuorumDecisionRepositoryIntegrationTest) has
not executed in this environment - a pre-existing limitation already
affecting T02/T04/T08's own integration tests, disclosed throughout this
task's artifacts, not a defect in this change.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Main:**
- `services/crypto/src/main/java/com/themistra/crypto/quorum/ProviderAnswer.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumOutcome.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumEvaluator.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecision.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecisionRepository.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/quorum/HeldFactAlerter.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecisionService.java` — new

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/quorum/QuorumEvaluatorTest.java` — new
- `services/crypto/src/test/java/com/themistra/crypto/quorum/QuorumDecisionTest.java` — new
- `services/crypto/src/test/java/com/themistra/crypto/quorum/HeldFactAlerterTest.java` — new, extended at Phase 11
- `services/crypto/src/test/java/com/themistra/crypto/quorum/QuorumDecisionServiceTest.java` — new, extended at Phase 11 (+7)
- `services/crypto/src/test/java/com/themistra/crypto/quorum/QuorumDecisionRepositoryIntegrationTest.java` — new, extended at Phase 11 (+1)
- `services/crypto/src/test/java/com/themistra/crypto/quorum/ProviderAnswerTest.java` — new (Phase 11)
- `services/crypto/src/test/java/com/themistra/crypto/quorum/QuorumOutcomeTest.java` — new (Phase 11)

**Pipeline artifacts:**
- `.ai/prompts/crypto/T09/artifacts/00-repository-understanding.md` through `13-pr-preparation.md` — all 14 phase artifacts

## Summary

T09 adds the platform's arbitration core: the 2-of-3 quorum rule that decides whether a provider-
reported fact becomes `AGREED` or is `HELD` for manual resolution (R1-R3, L1-L2). `QuorumEvaluator` is
genuinely pure logic — no I/O, no injected dependencies, exactly 3 scale-invariantly-compared answers
in, an outcome and counts out. `QuorumDecisionService` is the first consumer of `observation.FactType`
outside its own package, and the first task in this service to layer a second, independently-reasoned
review pass (Phase 9) on top of an already-frozen brief in response to adversarial findings that
surfaced only once real code existed (the alert-before-persist ordering risk neither Phase 2 nor Phase 4
anticipated).

## Testing performed

- `mvn -pl services/crypto test-compile` — BUILD SUCCESS, no new warnings.
- `mvn -pl services/crypto test -Dtest=QuorumEvaluatorTest,QuorumDecisionTest,HeldFactAlerterTest,QuorumDecisionServiceTest,ProviderAnswerTest,QuorumOutcomeTest` — 40/40 passing.
- `mvn -pl services/crypto -am test` (full module suite) — 278 tests, 272 passing, 6 errors, all
  `IllegalState: … Docker environment …` (5 pre-existing from T02/T04/T08's own Testcontainers
  integration tests, 1 new from this task's own `QuorumDecisionRepositoryIntegrationTest`) — zero
  genuine failures.
- Docker unavailable throughout this session — `QuorumDecisionRepositoryIntegrationTest` compiles
  cleanly and mirrors the already-established, previously-proven-once-Docker-is-available
  `ObservationRepositoryIntegrationTest` pattern, but has not itself executed against a real Postgres in
  this environment.

## Specification references

- **Task:** T09 — Quorum evaluator (`spec/crypto-service/tasks.md` #9).
- **Requirements:** R1, R2, R3 (`spec/crypto-service/requirements.md:10-12`).
- **Locked decisions:** L1 (`spec/crypto-service/design.md:5`) — 2-of-3 quorum, no single-provider
  truth, not a tunable; L2 (`design.md:6`) — disagreement → `HELD`, ops-alerted, never auto-resolved.
- **Named tests:** `shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree`,
  `shouldHoldFactAndAlertWhenProvidersDisagree`, `shouldNeverAutoResolveDisagreementInPayersFavor`
  (`package.md` §8).
- **Contracts:** none of `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`,
  `contracts/events/chain/tx-finalized.v1.schema.json` are touched by this task — `QuorumDecision` is
  purely internal persistence with an interim log-based alert, no API/event surface (task 15+ is the
  first to reach an event/contract boundary from quorum outcomes).
