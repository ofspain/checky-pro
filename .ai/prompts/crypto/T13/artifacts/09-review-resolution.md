# crypto · T13 · Phase 9 — Review Resolution

**Human Approval gate.** Approved 2026-09-05. Findings from Phase 7 (self-review) and Phase 8 (Kimi
independent review) are consolidated below — Kimi Issues 2 and 3 independently confirmed self-review
Findings 2 and 1 respectively. No public API changed (a Javadoc-only addition), no class renamed.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Kimi Issue 1 — `AddressPoisoningDetectorTest` is missing from the working tree | **REJECTED** | No change. Same misreading of this pipeline's own phase separation already corrected in T11 and T12's Phase 9 gates: Phase 6's own directive states "Do NOT write tests here (that is Phase 10)." Tests are correctly deferred, not missing. |
| 2 | Self-review Finding 2 / Kimi Issue 2 — `detectPoisoning` lacks method-level Javadoc | **ACCEPTED** | Added a full method-level Javadoc to `detectPoisoning` in `AddressPoisoningDetector.java`, condensing the class-level contracts (null-safety, case-sensitivity/no-normalization, no-validation, arbitrary-match) plus a new note on the generics-erasure limitation (folds in Issue 8 below). |
| 3 | Self-review Finding 1 / Kimi Issue 3 — uniform prefix threshold makes Tron detection stricter than the original per-chain intent | **ACCEPTED (already documented, no further action)** | Confirmed as a deliberate, already-disclosed tradeoff from the Phase 4 gate (Amendment #1) and the class's own Javadoc; no code change. |
| 4 | Kimi Issue 4 — empty-string candidate not explicitly tested | **ACCEPTED (noted for Phase 10)** | Additional required test: `doesNotFlagOrThrowForAnEmptyCandidateAddress`. |
| 5 | Kimi Issue 5 — arbitrary-match-among-multiples not tested | **ACCEPTED (noted for Phase 10)** | Additional required test: `returnsOneOfMultipleMatchingPreviouslySeenAddresses` (assert the result is one of the two matching entries, not a specific one). |
| 6 | Kimi Issue 6 — combined prefix-and-suffix match not tested | **ACCEPTED (noted for Phase 10)** | Additional required test: `flagsWhenBothPrefixAndSuffixMatch`. |
| 7 | Kimi Issue 7 — a history consisting entirely of too-short addresses not tested | **ACCEPTED (noted for Phase 10)** | Additional required test: `returnsEmptyWhenAllPreviouslySeenAddressesAreTooShortToMatch`. |
| 8 | Kimi Issue 8 — generics erasure allows a raw-type caller to pass non-`String` elements, risking `ClassCastException` | **REJECTED (code hardening); documentation folded into #2** | No runtime type-checking added — Low confidence, a standard Java generics-erasure limitation, and no other generic method anywhere in this codebase (T09-T12) defends against it; adding it here uniquely would be unprecedented, inconsistent hardening for a caller-error scenario. A one-line clarifying note was folded into the Javadoc fix from #2 instead. |

## Summary

1 accepted with a code change (2, folding in 8's documentation), 1 accepted as already-documented with
no further action (3), 4 accepted as noted additional required tests for Phase 10 (4, 5, 6, 7), 2
rejected (1: phase-separation misunderstanding; 8: unprecedented, low-confidence hardening request).

`mvn -pl services/crypto compile` succeeds cleanly after the change, with zero new warnings.

Files changed in this phase: `AddressPoisoningDetector.java` (Javadoc only). No public method signature
changed; no class renamed.
