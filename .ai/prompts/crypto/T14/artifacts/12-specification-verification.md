# crypto · T14 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R6 — Ethereum finality: at or below beacon `finalized` checkpoint, not a fixed confirmation count | Yes | `finality/EthereumFinalityPolicy.java:31-34` (`isFinal`), obtaining the checkpoint itself already handled by `adapter/eth/EthereumAdapter.java:145-166` | Yes — `shouldRequireBeaconFinalizedCheckpointForEthereumFinality`, boundary/negative/genesis/scripted-heads tests, `EthereumFinalityPolicyTest.java` (10 tests) | No | None |
| R7 — Tron finality: solidified block (~19 confirmations), per policy object | Yes | `finality/TronFinalityPolicy.java:40-43` (`isFinal`), solidified block obtained by `adapter/tron/TronAdapter.java:179-208` | Yes — `shouldRequireSolidifiedBlockForTronFinality`, boundary/negative/genesis/scripted-heads tests, `TronFinalityPolicyTest.java` (9 tests) | No | None — `~19` never appears as a code literal anywhere in `TronFinalityPolicy.java` (confirmed by inspection) |
| L4 — finality is a per-chain policy object, not a global constant; adding a chain adds a policy object; no confirmation count hardcoded across chains | Yes | `finality/FinalityPolicy.java` (interface) + two separate `@Component` implementations, each self-identifying via `chain()` (`EthereumFinalityPolicy.java:26-28`, `TronFinalityPolicy.java:35-37`); no shared numeric constant anywhere in either class | Yes — `chainReturnsEthereum`/`chainReturnsTron`; the intentional-duplication rationale is documented in both classes' Javadoc (Phase 3 Finding 3) | No | None |
| Q4 — confirm the Tron confirmation-count basis | Documented (this task's own deliverable was "confirm," not "implement") | `finality/TronFinalityPolicy.java:17-23` — Javadoc states the basis is already fixed by `TronAdapter.computeConfirmations` (T07) as block depth from the current head, identical in kind to `EthereumAdapter.computeConfirmations` (T06) | N/A (documentation deliverable, not a runtime behavior) | No | None |
| AC4 (L4, scope) — neither policy performs any RPC/network call | Yes | Both implementation files import only `Chain`, `FinalityStatus`, `java.util.Objects`, and Spring's `@Component` — no adapter/RPC/persistence dependency | Indirectly confirmed by `FinalityModuleBoundaryTest` and by inspection | No | None |
| AC6 (module boundary, Phase 3 Finding 2) — `finality/` imports nothing from `observation`/`provider`/`quorum`/`token`/`events`, and only two named types from `adapter` | Yes | `finality/FinalityModuleBoundaryTest.java` (source-scan) | Yes — `noMainSourceFileInFinalityImportsBeyondItsAllowedAdapterTypesOrAnyForbiddenPackage` | No | None |

## Assessment

**(1) Is the task fully complete?** Yes. All three files the frozen brief authorized
(`FinalityPolicy.java`, `EthereumFinalityPolicy.java`, `TronFinalityPolicy.java`) are implemented, and
all Required Tests — including the ones added during Phase 9 (review resolution) and Phase 11 (test
review) — are written and passing.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC6 (frozen brief) are each traced
to a concrete implementation and at least one passing test above; AC5 (Q4) is a documentation deliverable
and is satisfied as such.

**(3) Does it violate any LOCKED decision?** No. L4 is satisfied by construction: two distinct policy
objects behind a shared interface, each self-identifying its chain, with no confirmation-count constant
shared or hardcoded across chains anywhere in the implementation.

**(4) Remaining risks?**
- `FinalityStatus` carries no chain discriminator, so a future caller could route a status to the wrong
  policy undetected — accepted as a documented, out-of-scope risk at the Phase 4 gate (Finding 1);
  `appliesTheSameRawComparisonAsTronFinalityPolicyForTheSameStatus` now locks this contract in
  executable form rather than leaving it purely as prose.
- No dispatcher/registry yet wires `FinalityProperties.enabledChains` to the two new `@Component` beans
  — explicitly out of scope (frozen brief), deferred to a future task (most plausibly the watcher/attest
  layer). `FinalityProperties` has no runtime effect on these policies today; this is documented, not a
  defect of this task.
- `chain.tx.finalized`/`chain.tx.confirmed` event emission (R9, R10) remains unbuilt anywhere in this
  codebase, as expected at this point in the task sequence.

## Verdict

**PASS.** Both R6 and R7 are correctly implemented as separate, per-chain `FinalityPolicy` objects
satisfying L4, all named and required tests pass with zero regressions (419 tests, 411 passing, 8
pre-existing Docker-environment errors unrelated to this task), and Q4 is resolved by documenting the
already-shipped, chain-uniform confirmation-count basis rather than inventing new behavior.
