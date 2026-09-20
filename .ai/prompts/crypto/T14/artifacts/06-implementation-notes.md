# crypto · T14 · Phase 6 — Implementation Notes

Implemented exactly the three files the frozen brief and Phase 5 plan authorized. No test files
written (Phase 6's own directive defers all test-writing to Phase 10).

## Files created

1. `services/crypto/src/main/java/com/themistra/crypto/finality/FinalityPolicy.java` — the interface:
   `Chain chain()` + `boolean isFinal(FinalityStatus status)`. Class-level Javadoc carries all three
   documentation-only Phase 3 amendments that apply to both implementations: caller-routing
   responsibility (Finding 1), the trust boundary / no re-validation (Finding 5), and the note that
   `isFinal(null)` fails fast rather than returning a silent `false`.
2. `services/crypto/src/main/java/com/themistra/crypto/finality/EthereumFinalityPolicy.java` —
   `@Component`, `chain()` returns `Chain.ETHEREUM`, `isFinal` is `Objects.requireNonNull` then
   `status.txBlockNumber() <= status.finalizedBlockNumber()`. Javadoc references R6 and notes the
   intentional duplication with `TronFinalityPolicy` (Finding 3).
3. `services/crypto/src/main/java/com/themistra/crypto/finality/TronFinalityPolicy.java` —
   `@Component`, `chain()` returns `Chain.TRON`, identical `isFinal` body. Javadoc references R7, notes
   the same intentional-duplication point (Finding 3), and carries the Q4 resolution: the
   `chain.tx.confirmed` confirmation-count basis is already fixed by `TronAdapter.computeConfirmations`
   (T07) as block depth from the current head, identical in kind to Ethereum's — distinct from this
   class's own finality check (which compares against the *solidified* block, not the *current* one).

## Mapping to the plan and acceptance criteria

- **AC1 (R6):** `EthereumFinalityPolicy.isFinal` — exact comparison, no confirmation-count arithmetic.
- **AC2 (R7):** `TronFinalityPolicy.isFinal` — exact comparison; `~19` appears only in Javadoc prose,
  never as a code literal.
- **AC3 (L4):** both classes `implements FinalityPolicy`; each has its own `chain()`.
- **AC4 (L4, scope):** neither class performs any RPC/network call or touches persistence — confirmed
  by inspection (each file imports only `Chain`, `FinalityStatus`, `Objects`, and Spring's `@Component`).
- **AC5 (Q4):** documented in `TronFinalityPolicy`'s Javadoc, per the frozen brief.
- **AC6 (module boundary):** both classes import only `com.themistra.crypto.adapter.Chain` and
  `com.themistra.crypto.adapter.model.FinalityStatus` — no import of `observation`, `provider`,
  `quorum`, `token`, or `events`, and no other `adapter` subpackage. (`FinalityModuleBoundaryTest`
  itself is a Phase 10 deliverable, per the plan.)
- Public method signatures match the Phase 5 plan exactly; no private helper methods were needed (each
  `isFinal` body is a single guard plus a one-line comparison, as planned).

## Deviations from the plan

None. `mvn -pl services/crypto compile` succeeds cleanly with zero new warnings; no file outside the
plan's three was touched.
