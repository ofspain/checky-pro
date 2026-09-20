# crypto · T05 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). Only `src/main` files touched — no tests (Phase 10 scope);
`FakeChainAdapter` and its tests are explicitly test-scope per the frozen brief and are Phase 10's job
despite being named in this task's own statement, consistent with this pipeline's main/test split.

## Files created

- `services/crypto/src/main/java/com/themistra/crypto/adapter/Chain.java` — `ETHEREUM`, `TRON`;
  Javadoc documents the `Chain.valueOf(String)` bridge to T03's config (amendment #3).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TxResult.java` — Javadoc
  documents the "never itself JSON-serialized" assumption (amendment #4).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TokenInfo.java` — Javadoc
  documents the equals/hashCode discipline rule (amendment #1) — no override, business code compares
  via `.contractAddress()`.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/FinalityStatus.java` — single
  non-nullable `finalizedBlockNumber` unifying Ethereum/Tron (amendment #8).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/Subscription.java` — single-method
  functional interface.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ObservationSink.java` — Javadoc scopes
  it to tx observations only (amendment #5); not renamed (design.md §6 names this file explicitly).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java` — copied verbatim
  from design.md §4c, including its own inline comments.

## Mapping to acceptance criteria

- **AC1** — `ChainAdapter`'s 5 method signatures are a direct copy of design §4c's own code block
  (verified by side-by-side comparison against the spec text, not just from memory). Not yet
  test-verified (Phase 10's `ChainAdapterShapeTest`).
- **AC2** — `Chain` has exactly two constants, `ETHEREUM` and `TRON`. Verified by direct inspection.
- **AC4 (L7, amendment #1)** — no `equals`/`hashCode` override exists on `TokenInfo`; the discipline
  rule is documentation-only, satisfied by construction (there's no business-logic code anywhere yet
  that could violate it — this task adds no consumer of `TokenInfo`).
- **AC5 (L4, amendment #8)** — `FinalityStatus` has no boolean field and no confirmation-count
  threshold; `finalizedBlockNumber` is a plain non-nullable `long`. Verified by direct inspection.
- **AC3, AC6, AC7** — all three concern `FakeChainAdapter`'s behavior, which is test-scope and not
  built in this phase. Deferred to Phase 10, per this pipeline's own main/test split (matches T04's
  own precedent: production shape now, test-scope fixtures later).

## Verification performed this phase

- `mvn -pl services/crypto -am compile` — clean, 22 source files (up from 15 after T04), no errors,
  no warnings.
- This task has no Docker dependency anywhere (pure Java, no persistence, no Spring context) — unlike
  T03/T04, there is no environment-limitation caveat to carry forward here.

## Deviations from the Phase 5 plan (flagged, not hidden)

None. Every file matches the Phase 5 plan and the frozen brief's Files-to-Create list exactly — no
file outside that scope was touched, and no class shape diverges from what Phase 5 sketched.
`FakeChainAdapter` and its two test classes remain correctly deferred to Phase 10 (test scope), which
the frozen brief itself already placed under `src/test/java`, not `src/main/java` — nothing to
implement here for them.
