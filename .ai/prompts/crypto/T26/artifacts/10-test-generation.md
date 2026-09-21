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

## Phase 11 (Kimi Test Review) additions

Per this pipeline's own Phase 11 convention, no separate resolution artifact is written — accepted
findings are folded directly into this artifact and the test suite. Kimi raised 12 findings. 8 accepted
(2 of them — the two most consequential — combined into one fix), 3 rejected (2 already resolved/
disproven at Phase 9, 1 already covered by an existing unit test), 1 not separately implemented
(redundant with existing, dedicated coverage).

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | Kafka consumer reads stale messages from earlier flows (class-scoped container, fresh `earliest`-reset group per test) | **ACCEPTED — real, serious bug** | Combined with #4's fix: `awaitRecordOnTopic`/`noRecordAppearsOnTopic` now filter by the record's own parsed `txHash` payload field, not just topic — a later flow's consumer can no longer be satisfied (or wrongly failed) by an earlier flow's leftover message on the same topic. |
| 2 | Unstubbed `ObservationSnapshotStore` causes NPE | **REJECTED — already disproven at Phase 9** | Re-raised verbatim; already directly disproven by a standalone Mockito probe (`Optional.empty()` is Mockito's real default, not `null`) at Phase 9's own resolution. Not re-litigated. |
| 3 | `ChainCursor` placeholder existence assumed, not asserted | **REJECTED — already re-verified at Phase 9 (twice)** | `WatchService.register` demonstrably creates the placeholder; re-confirmed directly against source again this phase for a third time. |
| 4 | Kafka assertions check presence only, not content | **ACCEPTED — combined with #1's fix above** | The same `txHash`-filtering fix serves both: matching by a real payload field *is* the content check, and it closes the stale-message risk in the same change. |
| 5 | No negative R27 assertions (missing/under-scoped JWT) | **REJECTED — redundant with existing, dedicated coverage** | `ResourceServerConfigIntegrationTest` (T03) already exhaustively covers this exact scenario for these exact endpoints; the frozen brief's own scope for this task is proving R27's *positive* path end-to-end, not re-proving the security layer's negative paths a dedicated test already owns. |
| 6 | No lifecycle-ordering assertions | **ACCEPTED** | Added `assertStrictlyOrdered(earlier, later)`, comparing each record's own Kafka-assigned produce timestamp (offsets aren't comparable across different topics' independent partitions) — applied to `seen < confirmed < finalized` in Flows 1/4 and `seen < confirmed < reorged` in Flow 3. |
| 7 | No assertion that `KmsSigner.sign` is invoked in Flow 1 | **ACCEPTED** | Added `verify(kmsSigner, times(1)).sign(any())` after the successful attest call. |
| 8 | No assertion that `Attestation` rows are persisted | **ACCEPTED** | Added `findAttestations(chain, txHash)` (`EntityManager`/JPQL, matching the established package-private-repository workaround) and asserted the real, correct outcome in Flows 1 and 4. |
| 9 | No assertion that `HeldFactAlerter` is *not* invoked in healthy flows | **ACCEPTED** | Added `verify(heldFactAlerter, never())...` to Flows 1, 3, and 4. |
| 10 | `TOKEN` quorum decision not asserted in Flow 1 | **ACCEPTED** | Added `isAgreed(..., TOKEN)` assertion alongside the existing `EXISTENCE`/`FINALITY` ones. |
| 11 | `chain.tx.seen` not asserted in Flows 3 and 4 | **ACCEPTED** | Both flows now await and assert `chain.tx.seen` before their later events, and it participates in the new ordering assertions too. |
| 12 | No assertion that S3 write precedes the DB insert (L3 ordering) | **REJECTED — already covered by a dedicated unit test** | Confirmed directly: `ObservationLogTest` already has exactly this assertion (`InOrder order = inOrder(snapshotStore, repository)`, line 79) at the unit level, where it belongs — an E2E test re-proving internal method-call ordering inside a single already-unit-tested class would be disproportionate, and technically awkward here besides (`ObservationRepository` is a real bean in this test, not a mock, so `Mockito.inOrder` can't be used across it and the mocked snapshot store the way the suggestion assumes). |

**Verification run (Phase 11):**
`mvn -pl services/crypto -am test-compile` — clean, confirmed directly after every fix. Actual execution
of the 4 flows remains deferred — Docker unavailability, unchanged since Phase 0.
