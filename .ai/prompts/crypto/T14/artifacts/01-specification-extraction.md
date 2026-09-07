# crypto · T14 · Phase 1 — Specification Extraction

## Business Rules

- **R6.** WHEN evaluating Ethereum finality, THEN the system SHALL require the transaction's block to
  be at or below the beacon-chain `finalized` checkpoint, NOT a fixed confirmation count.
- **R7.** WHEN evaluating Tron finality, THEN the system SHALL require the transaction's block to be
  solidified (~19 confirmations) per the Tron finality policy object.

## Locked Decisions

- **L4.** Finality is a per-chain policy object, not a global constant — Ethereum = beacon `finalized`
  checkpoint; Tron = solidified block (~19 conf). Adding a chain adds a policy object; no confirmation
  count is hardcoded across chains. Attestation only ever happens at finality.

## Files involved

**Existing, to read/extend (no modification unless explicitly named):**
- `adapter/model/FinalityStatus.java` — already-shipped input type: `(txBlockNumber,
  currentBlockNumber, finalizedBlockNumber)`.
- `adapter/ChainAdapter.java` — `getFinalityStatus(String txHash)` already declared and already fully
  implemented by both real adapters (Phase 0 finding); not modified by this task.
- `adapter/eth/EthereumAdapter.java`, `adapter/tron/TronAdapter.java` — pattern/data-source reference
  only; not modified.
- `adapter/Chain.java` — the `ETHEREUM`/`TRON` discriminator enum, reused for policy dispatch if any.
- `common/config/FinalityProperties.java` — `enabled-chains` list; not modified (its own Javadoc
  already defers policy objects to this task).
- `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapter.java` —
  `scriptFinalityStatus` fixture, available for tests if the policy is tested through a `ChainAdapter`
  rather than a bare `FinalityStatus` (Phase 2 decision).

**New, per `design.md` §4c file layout (VERBATIM, `services/crypto/src/main/java/com/themistra/crypto/`):**
```
finality/
├── FinalityPolicy.java          (interface)
├── EthereumFinalityPolicy.java  (beacon finalized checkpoint — L4)
└── TronFinalityPolicy.java      (solidified block — L4)
```
This is the first task to create the `finality/` package.

**Explicitly NOT in this task's own scope:**
- `BASE`/`ARB`/`SOLANA` policy objects — `design.md`'s own VERBATIM table marks them "(later)"; only
  ETHEREUM and TRON ship at launch (`design.md` §4c, `package.md` §2).
- Any dispatcher/registry wiring a `Chain` to its `FinalityPolicy` for a real caller — no such caller
  exists yet (the plausible future caller is the attest module, `design.md`'s own
  `AttestationService.java` file listing, not yet built — task 20+).
- Any change to `chain.tx.finalized` event emission (R10) — that event does not exist yet in this
  codebase (confirmed absent under `contracts/events/chain/` and `contracts/api/`, Phase 0/1 finding);
  emitting it is a future task's job once the watcher/attest pipeline exists.
- The `chain.tx.confirmed` event itself (R9) — not built yet; Q4 only requires *confirming the basis*
  used for the confirmation count, not building the event.

## Dependencies

- `Chain` enum — `ETHEREUM`, `TRON`.
- `FinalityStatus` record — the input this task's policies operate over.
- No new external library — beacon/solidity-block-number retrieval already lives inside
  `EthereumAdapter`/`TronAdapter` (T06/T07); this task's own scope is pure comparison logic over an
  already-obtained `FinalityStatus`, needing nothing from web3j or trident directly.
- No persistence, no `Clock`, no outbox, no Kafka — mirrors the `token/` package's pure-predicate
  precedent (T09, T11-T13); nothing in `design.md`'s `finality/` file listing suggests otherwise.
- `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`,
  `contracts/events/chain/tx-finalized.v1.schema.json` — none of these files exist anywhere in this
  repository yet (confirmed via `find`); this task touches no contract.

## Acceptance Criteria

- **AC1 (R6).** Given a `FinalityStatus` for an Ethereum transaction, `EthereumFinalityPolicy` reports
  final when `txBlockNumber <= finalizedBlockNumber` (the beacon `finalized` checkpoint, already
  computed by `EthereumAdapter`), and not final when `txBlockNumber > finalizedBlockNumber`. No
  confirmation-count arithmetic of any kind is performed by this policy.
- **AC2 (R7).** Given a `FinalityStatus` for a Tron transaction, `TronFinalityPolicy` reports final when
  `txBlockNumber <= finalizedBlockNumber` (the solidified block, already computed by `TronAdapter`), and
  not final otherwise. `~19` is never hardcoded as a literal threshold in this policy — R7's own "(~19
  confirmations)" is descriptive of what a solidified block typically represents, not a value this
  policy re-derives; the actual solidified block number is supplied pre-computed via `FinalityStatus`.
- **AC3 (L4).** Both policies implement a common `FinalityPolicy` interface; adding a future chain adds
  a new policy object, never a new branch/global constant inside an existing one.
- **AC4 (L4, scope).** Neither policy performs any RPC/network call itself — both operate purely on an
  already-obtained `FinalityStatus` value. (Confirms Phase 0's reading: the "obtain the number" half of
  R6/R7 already shipped in T06/T07; this task is the boolean-decision half only.)
- **AC5 (Q4, "Confirm").** The confirmation-count basis `chain.tx.confirmed` (R9) would use for Tron is
  documented as identical in kind to Ethereum's: block depth from the current head
  (`currentBlockNumber - txBlockNumber + 1`), exactly as already implemented in
  `TronAdapter.computeConfirmations`/`EthereumAdapter.computeConfirmations` (T06/T07) — NOT confirmations
  counted toward the solidified block specifically. This is a documentation/confirmation deliverable of
  this task (per the task statement's own "Confirm... (Q4)" wording), not new production code, since
  R9's actual event emission is out of scope (not yet built).

## Tests required

- `shouldRequireBeaconFinalizedCheckpointForEthereumFinality` (`package.md` §8, named) — AC1.
- `shouldRequireSolidifiedBlockForTronFinality` (`package.md` §8, named) — AC2.
- A boundary test at `txBlockNumber == finalizedBlockNumber` for each chain (final, per AC1/AC2's `<=`).
- A boundary test at `txBlockNumber == finalizedBlockNumber + 1` for each chain (not final).
- A "scripted chain heads" test per the task statement's own wording — a `FinalityStatus` where
  `currentBlockNumber` is well ahead of both `txBlockNumber` and `finalizedBlockNumber`, confirming
  `currentBlockNumber` plays no role in the finality decision itself (only `txBlockNumber` vs.
  `finalizedBlockNumber` matters — `currentBlockNumber` is "retained as informative context" per
  `FinalityStatus`'s own Javadoc).

## Open Questions

No blockers cited in `package.md` §11 apply as a hard stop to this task's own scope. Q4 is explicitly
named by the task statement itself as something to **confirm**, not a blocker — Phase 0's own finding
(existing `computeConfirmations` implementations in both real adapters) already supplies the answer;
Phase 2 should propose documenting it as this task's own resolution rather than treating it as an open
gap.

One genuine implementer-facing gap remains, matching this pipeline's established pattern of
implementer-proposed resolution + Kimi challenge + human sign-off:

- **Exact `FinalityPolicy` method signature is not specified anywhere in the spec.** `design.md`'s file
  listing names only the three files, not their method shapes. Phase 0's own analysis favors
  `boolean isFinal(FinalityStatus status)` (a `Chain chain()` discriminator likely alongside it, mirroring
  `ChainAdapter`) as the natural, decoupled, easily-"scripted"-testable shape, but this is a Phase 2
  design decision, not yet locked.
