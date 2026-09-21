# crypto · T26 · Phase 10 — Test Generation

**Process note.** Per this task's own Phase 6 implementation notes (T26 is test-only — its entire
deliverable is one end-to-end test class), tests were written alongside the implementation itself, then
substantially strengthened at Phase 9 following 7 confirmed-real findings from independent review. No
production code exists for this task. This artifact is the traceability manifest.

## Test files (this task's own contribution)

| File | Tests | Purpose |
|---|---|---|
| `watch/EndToEndIntegrationTest.java` | 4 | Proves the full pipeline — registration, quorum, event emission, and attestation — works together through real Postgres and Kafka, not just in per-module isolation. |

**Total: 4 new test methods**, each covering one complete flow. No production code exists for this
task; nothing else in the codebase was modified.

## Traceability matrix

| Test | Flow / AC | What it proves |
|---|---|---|
| `endToEndFlowRegistersObservesAndAttestsWithASignature` | Flow 1 (AC1), AC5 | Real `MockMvc`/JWT-authenticated registration (R18, R27) → real 2-of-3 quorum agreement on `EXISTENCE`/`AMOUNT` (R1, L1) with real `Observation` rows persisted before the decision (L3, AC5) → real Kafka delivery of `chain.tx.seen` (R8) and `chain.tx.confirmed` (R9) → explicit `pollFinality()` driving `FINALITY` to `AGREED` (R6/L4) → real Kafka delivery of `chain.tx.finalized` (R10) → `POST /internal/v1/attest` returns a genuine `200 SIGNED` response (R20, R23). |
| `disagreementOnANonBooleanFactHoldsAndEmitsNothingFurther` | Flow 2 (AC2) | `EXISTENCE` agrees (`chain.tx.seen` still fires — a Boolean fact can never be `HELD` with 3 providers, pigeonhole principle); both `AMOUNT` and `CONFIRMATIONS` genuinely disagree (R2/R3, L2) → `HeldFactAlerter` invoked for each → no `chain.tx.confirmed`/`chain.tx.finalized` ever delivered. |
| `reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor` | Flow 3 (AC3) | After `CONFIRMED`, a scripted reorg (`exists=false` from all 3 providers) is detected via `Watcher.checkForReorg`'s real pull-based re-check → `chain.tx.reorged` delivered (R11) → `ChainCursor` invalidated (L6) → no `chain.tx.finalized` ever follows. |
| `sanctionedCounterpartyIsBlockedWithNoSignature` | Flow 4 (AC4), AC6 | Quorum + finality genuinely satisfied → mocked `ScreeningClient` (standing in for the real vendor, Q2 still open) returns `BLOCKED` and persists a real `ScreeningResult` row (L12, AC6) → `POST /internal/v1/attest` returns `200 BLOCKED` (R21) → zero `KmsSigner.sign(...)` interactions, verified directly. |

## Verification run

`mvn -pl services/crypto -am test-compile` — **clean**, confirmed directly after every Phase 6/9 fix,
including the 7 real bugs Phase 8/9 caught and corrected.

**Actual execution of all 4 flows remains deferred.** Docker has been unavailable throughout this
task's entire development (Phase 0 through this phase) in this environment. This class has been
written, self-reviewed, independently reviewed, and had every reviewed finding either fixed (with the
fix verified by recompiling) or rejected only after direct verification (including one finding
disproven by actually running a standalone Mockito probe) — the same rigor this whole task's pipeline
has applied throughout — but its real green/red result on a Docker-available environment is still
genuinely unknown, and this is restated here rather than left implicit.

## Gaps

None identified beyond what Phase 7/8/9 already surfaced and resolved, with one exception carried
forward openly: Self-Review Finding #4 (the repeated 3-provider setup block across all 4 test methods)
was acknowledged but not implemented at Phase 9, given the volume of correctness fixes that phase
already required — a legitimate simplification opportunity for a future pass, not a defect.
