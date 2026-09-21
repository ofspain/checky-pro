# crypto · T26 · Phase 6 — Implementation Notes

## What changed

**Created:**
- `services/crypto/src/test/java/com/themistra/crypto/watch/EndToEndIntegrationTest.java` — one
  `@SpringBootTest` class, real `PostgreSQLContainer` + `KafkaContainer`, `@MockBean` for
  `WatcherRegistry`/`ObservationSnapshotStore`/`KmsSigner`/`ScreeningClient`/`HeldFactAlerter`, and 4
  `@Test` methods (one per flow), exactly per the frozen brief and Phase 5's implementation plan.

No production code changed anywhere in this task.

## Deviations from the plan, forced by reality

Three real issues surfaced only by actually attempting to compile and carefully self-reviewing the
file — not by mechanically transcribing the plan — each fixed within the one authorized file, no
production code touched and no new file added:

1. **`KmsSigner.sign(...)`'s return type, `SignatureResult`, is package-private in `attest`** —
   invisible from this test's own `watch` package (required for direct `Watcher` construction, an
   already-accepted architectural constraint). Discovered only by actually compiling, not anticipated
   at Phase 5. Resolved via reflection (`Class.forName(...).getDeclaredConstructor(...)`) to construct
   a real instance and `Mockito.doReturn(Object)` (which accepts a raw `Object`, sidestepping the need
   to ever name the invisible type) to stub `KmsSigner.sign(...)` — no production visibility change, no
   new cross-package test-fixture file.
2. **`ConsumerConfig.doReturn` vs `when(...).thenReturn(...)` import correction** — a mechanical
   follow-on of fix #1 (removed the now-unused `Mockito.when` import).
3. **A genuine correctness bug in the Kafka-record-lookup helper itself, caught during self-review
   before this artifact was written, not left for Phase 7 to find.** The original
   `awaitRecordOnTopic` polled and returned on the first matching record, discarding every other
   record in that same batch. Since `chain.tx.seen` and `chain.tx.confirmed` can legitimately arrive
   in the same delivery round (both facts can agree simultaneously in this test's own scripted
   scenario), a naive "return on first match" implementation would silently lose the second event —
   once a Kafka `poll()` batch is fetched, the consumer's position has moved past every record in it,
   whether or not the caller inspected each one, so the discarded record could never be redelivered
   within the same test. Fixed by buffering every polled record in an instance-level list and
   searching that buffer (not just newly-polled records) on every lookup.

## Mapping to acceptance criteria

- **AC1 (Flow 1).** `endToEndFlowRegistersObservesAndAttestsWithASignature` — register via real
  `MockMvc`/JWT → real quorum agreement on `EXISTENCE`/`AMOUNT` → real Kafka delivery of
  `chain.tx.seen`/`confirmed` → explicit `pollFinality()` step (Frozen Brief Finding #8) → real Kafka
  delivery of `chain.tx.finalized` → attest returns `200 SIGNED` from the (reflection-constructed)
  mocked signature.
- **AC2 (Flow 2, redefined per Finding #3).** `disagreementOnANonBooleanFactHoldsAndEmitsNothingFurther`
  — `EXISTENCE` agrees (`chain.tx.seen` still fires), 3 distinct `AMOUNT` values produce no majority,
  `HeldFactAlerter` invoked, no further event ever appears.
- **AC3 (Flow 3).** `reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor` — confirmed, then
  `scriptTx(..., exists=false)` on all 3 + `pollFinality()` (drives `checkForReorg`) → `chain.tx.reorged`
  delivered, cursor `txHash` nulled, no `chain.tx.finalized` ever follows.
- **AC4 (Flow 4).** `sanctionedCounterpartyIsBlockedWithNoSignature` — finality reached, mocked
  `ScreeningClient` returns `BLOCKED` and persists a real `ScreeningResult` row (mirroring the real
  contract) → attest returns `200 BLOCKED`, zero `KmsSigner.sign(...)` interactions.
- **AC5 (L3, new).** Flow 1 asserts real `Observation` rows exist per provider/fact-type via
  `EntityManager`/JPQL (package-private-repository workaround, Phase 5).
- **AC6 (new).** Flow 4 asserts a `ScreeningResult` row with `outcome=BLOCKED` exists for the
  sanctioned address, via the same JPQL technique.

## Verification

`mvn -pl services/crypto -am test-compile` — **clean**, confirmed directly (not assumed) after fixing
the 2 real compile errors above.

**Actual test execution is deferred, stated plainly rather than claimed.** Docker is unavailable in
this development environment (disclosed at Phase 0, carried through every phase since, unresolved as
of this writing). This class cannot be run here. It has been written and self-reviewed to the same
rigor as if it could run — every method signature, field name, and entity mapping it uses was verified
directly against the real source across Phases 0-6, and the class compiles cleanly against all of it —
but its actual green/red result on the 4 flows is genuinely unknown until it runs in a Docker-available
environment. This will be restated at every subsequent phase rather than silently assumed away.
