# crypto · T09 · Phase 7 — Self Review

Self-review of the Phase 6 diff (`ProviderAnswer.java`, `QuorumOutcome.java`, `QuorumEvaluator.java`,
`QuorumDecision.java`, `QuorumDecisionRepository.java`, `HeldFactAlerter.java`,
`QuorumDecisionService.java`) against the frozen brief and `agents.md`. No code changed in this phase —
findings only, per the phase directive.

---

## Finding 1 — `HeldFactAlerter` fires before `QuorumDecisionRepository.save`, so a subsequent persistence failure leaves an alert with no corresponding row

**Severity:** Medium

**Evidence:** `QuorumDecisionService.java:55-61` calls `alerter.alert(...)` first, then
`repository.save(decision)`. If `save` throws after the alert already fired — e.g. a violated
`uq_quorum_tx_fact` constraint on an accidental re-evaluation of the same `(chain, txHash, factType)`
(the exact scenario the frozen brief's Amendment #7 flags as a known cross-task risk), or any other
transient DB failure — ops receives an alert for a `HELD` decision that was never actually persisted.
The alert and the durable record can drift out of sync in exactly the failure mode this task is meant
to make auditable.

**Recommendation:** Either move the alert to fire only after a successful `save`, or explicitly document
that the alert is intentionally best-effort/fire-and-forget and may not always correspond 1:1 with a
persisted row (in which case a human reading an alert with no matching row should not assume the alert
itself was spurious).

---

## Finding 2 — The coordinator's own entry point has no explicit, named null-guard, unlike `QuorumEvaluator`'s established discipline

**Severity:** Low-Medium

**Evidence:** `QuorumDecisionService.rejectDuplicateProviders` (`QuorumDecisionService.java:64-72`) and
`extractValues` (`:74-76`) both iterate/dereference `answers` and its elements with no
`Objects.requireNonNull` guard of their own. A `null` `answers` list, or a `null` element within it
(as opposed to a `null` `provider`/`value` *inside* a `ProviderAnswer`, which the record's own compact
constructor already rejects), surfaces as an unnamed `NullPointerException` thrown from inside a loop
rather than the deliberately-worded, named exception this codebase otherwise establishes
(`QuorumEvaluator.validate`, `QuorumDecisionService.java:39-50`; T08's `ObservationSnapshotStore`).

**Recommendation:** Add `Objects.requireNonNull(answers, "answers")` at the top of
`QuorumDecisionService.evaluate`, mirroring `QuorumEvaluator`'s own named-argument discipline.

---

## Finding 3 — `QuorumDecision.create`'s `int`-to-`short` narrowing cast has no range check

**Severity:** Low

**Evidence:** `QuorumDecision.java:88-89` casts `agreeingCount`/`providerCount` from the factory
method's `int` parameters to the entity's `short` fields with a plain, unchecked narrowing cast. Every
value this task's own code ever passes is provably in `[1,3]` (from `QuorumEvaluator.Result`), so this
is not an active bug today — but `create` is a `public static` factory with no documented range
contract of its own; a hypothetical future caller passing an out-of-`short`-range `int` would silently
get a wrong, truncated value instead of a clear failure.

**Recommendation:** Low priority given the only current caller (`QuorumDecisionService`) is trusted and
provably in-range; worth a bounds check or a Javadoc contract if `create` is ever called from elsewhere.

---

## Finding 4 — `FactType`-to-string conversion logic now exists in two places with no compiler-enforced link

**Severity:** Low (already disclosed as a deliberate Phase 6 tradeoff; re-flagged here for completeness
per this phase's own remit)

**Evidence:** `QuorumDecision.FactTypeDbConverter` (`QuorumDecision.java:130-142`) duplicates
`observation.FactType.DbConverter`'s exact lowercase mapping, because the original is package-private
to `observation` and the frozen brief explicitly forbids modifying `FactType.java`. The two copies are
byte-for-byte identical today but have no shared source — if `FactType`'s value set or naming
convention is ever changed by a future task touching only `observation/`, nothing but code review would
catch the `quorum/` copy silently falling out of sync.

**Recommendation:** No action needed within this task (the Phase 6 tradeoff — preserving the approved
"do not modify `FactType.java`" boundary over DRY — remains correct for this task's own scope). Flag
for whichever future task adds a third `fact_type`-mapping consumer: promote the converter to a shared,
public location at that point rather than adding a third copy.

---

## Summary table

| # | Issue | Severity |
|---|-------|----------|
| 1 | Alert fires before persistence succeeds — can desync from the persisted record | Medium |
| 2 | Coordinator's own null-guard is implicit (unnamed NPE), unlike `QuorumEvaluator`'s discipline | Low-Medium |
| 3 | `create`'s `int`→`short` narrowing cast has no range check | Low |
| 4 | Duplicated `FactType`-to-string conversion logic, no compiler-enforced link | Low |

(End of self-review.)
