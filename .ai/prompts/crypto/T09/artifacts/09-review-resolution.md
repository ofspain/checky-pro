# crypto · T09 · Phase 9 — Review Resolution

**Human Approval gate.** Approved 2026-09-03. Findings from Phase 7 (self-review) and Phase 8 (Kimi
independent review) are consolidated below — the two phases substantially overlapped (Kimi Issues 1-4
independently confirmed self-review Findings 1-4). No public API changed, no class renamed, no
refactoring beyond the accepted fixes.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Self-review Finding 1 / Kimi Issue 1 — `HeldFactAlerter` fires before `repository.save`, so a persistence failure leaves an alert with no corresponding row | **ACCEPTED** | `QuorumDecisionService.evaluate` reordered: `repository.save(decision)` now happens first; `alerter.alert(...)` is called only after `save` returns successfully, using the outcome computed earlier (`QuorumDecisionService.java:58-69`). |
| 2 | Self-review Finding 2 / Kimi Issue 2 — no explicit null guard on `answers`; `chain`/`txHash`/`factType` unvalidated | **ACCEPTED** | Added `Objects.requireNonNull(chain, "chain")`, `Objects.requireNonNull(txHash, "txHash")`, `Objects.requireNonNull(factType, "factType")`, `Objects.requireNonNull(answers, "answers")` at the top of `evaluate` (`QuorumDecisionService.java:51-54`). |
| 3 | Self-review Finding 3 / Kimi Issue 3 — `int`→`short` narrowing cast in `QuorumDecision.create` has no range check | **ACCEPTED** | Added a private `toShort(int, String)` helper that throws `IllegalArgumentException` for a negative value or one exceeding `Short.MAX_VALUE`; `create` now calls it for both `agreeingCount` and `providerCount` (`QuorumDecision.java:80-101`). |
| 4 | Self-review Finding 4 / Kimi Issue 4 — `FactType`-to-string conversion duplicated across `observation`/`quorum`, no compiler-enforced link | **ACCEPTED (documentation only)** | No code change — this remains the correct Phase 6 tradeoff (preserving the frozen brief's "do not modify `FactType.java`" boundary over DRY). Already documented in `QuorumDecision`'s class Javadoc and Phase 6/7 artifacts; no further action taken. |
| 5 | Kimi Issue 5 — exactly-3-answers rule "too rigid" for degraded-provider scenarios | **REJECTED (reopens an already-decided Phase 3/4 amendment); documentation reinforced** | No code change. This is Kimi's own Phase 3 Issues 3/10, already deliberated and deliberately fixed at the Phase 4 human-approval gate specifically to eliminate tie ambiguity — reopening it now on the same grounds, with no new information, would undo a reasoned tradeoff. Degraded-provider handling is explicitly out of this task's scope (frozen brief "Out: `ProviderHealth`/`chain.provider.degraded` (task 10)"). Added a Javadoc note to `QuorumEvaluator` stating this explicitly, so task 10's author sees the boundary named rather than discovering it by trial (`QuorumEvaluator.java:26-33`). |
| 6 | Kimi Issue 6 — `HeldFactAlerter` logs full provider values, hypothetical future PII risk | **PARTIAL ACCEPT (documentation only); log-level-downgrade recommendation REJECTED** | No behavioral change — logging at `error` level is R2/L2's own "ops-alerted" requirement; downgrading to `debug`/`trace` would defeat that purpose, since production log retention/alerting is typically wired off error-level output. Added a Javadoc note to `HeldFactAlerter` stating the `error`-level choice is deliberate and that callers must never pass a `T` whose content could be secret/PII (`HeldFactAlerter.java:11-17`). |
| 7 | Kimi Issue 7 — no pre-flight check for an existing `(chain, txHash, factType)` decision before `save` | **ACCEPTED** | Added a `rejectExistingDecision` pre-flight check at the top of `evaluate`, calling the previously-unused `QuorumDecisionRepository.findByChainAndTxHashAndFactType` and throwing a clear `IllegalStateException` before any collaborator (evaluator, alerter, repository.save) is touched, if a decision already exists for the triple (`QuorumDecisionService.java:55, 72-78`). This also further narrows (though a race under fully concurrent calls could still theoretically occur - out of this task's own scope, no concurrency-control mechanism was requested) the residual risk from item 1. |
| 8 | Kimi Issue 8 — agreed value not returned/stored by `QuorumDecisionService` | **REJECTED (reopens an already-decided Phase 3/4 amendment)** | No code change. Identical to Kimi's own Phase 3 Issue 5, already decided at the Phase 4 gate (Amendment #5: documented as a known gap, no schema column added, no consumer defined in this task's own scope). No new information is presented here beyond what was already considered and resolved. |

## Summary

4 accepted with code changes (1, 2, 3, 7), 2 accepted as documentation-only with no code change (4, 6's
documentation half), 2 rejected as re-opening already-decided Phase 3/4 amendments with no new
information (5, 8; 6's log-level-downgrade half also rejected on R2/L2 grounds).

`mvn -pl services/crypto compile` succeeds cleanly after all changes, with zero new warnings.

Files changed in this phase: `QuorumDecisionService.java`, `QuorumDecision.java`,
`HeldFactAlerter.java`, `QuorumEvaluator.java` (Javadoc only for the last two). No file outside
`services/crypto/src/main/java/com/themistra/crypto/quorum/` was touched. No public method signature
changed; no class renamed.
