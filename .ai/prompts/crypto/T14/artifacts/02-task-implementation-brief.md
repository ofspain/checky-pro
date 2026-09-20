# crypto · T14 · Phase 2 — Task Implementation Brief

## Task

Implement `EthereumFinalityPolicy` and `TronFinalityPolicy`, both behind a shared `FinalityPolicy`
interface, in a new `finality/` package. Confirm (document) the Tron confirmation-count basis (Q4).

## Purpose

Supply the per-chain boolean "is this transaction final" decision (L4, R6/R7) as a pure, stateless
policy object, decoupled from how each adapter obtains the finalized/solidified block number (already
built in T06/T07). No chain currently has this decision layer.

## Scope

**In:**
- `FinalityPolicy` interface: a `Chain` discriminator plus a boolean finality check over an
  already-obtained `FinalityStatus`.
- `EthereumFinalityPolicy` — R6: final iff `txBlockNumber <= finalizedBlockNumber` (the beacon
  `finalized` checkpoint, already computed by `EthereumAdapter.getFinalityStatus`).
- `TronFinalityPolicy` — R7: final iff `txBlockNumber <= finalizedBlockNumber` (the solidified block,
  already computed by `TronAdapter.getFinalityStatus`).
- Documenting, in `TronFinalityPolicy`'s own Javadoc, that Q4's confirmation-count basis is already
  fixed by T06/T07's shipped code: block depth from the current head
  (`currentBlockNumber - txBlockNumber + 1`), identical in kind for both chains — not confirmations
  counted toward the solidified block specifically.

**Out:**
- Any RPC/network call, or any change to `EthereumAdapter`/`TronAdapter`/`FinalityStatus`/`ChainAdapter`
  — all already correctly implemented (Phase 0).
- `BASE`/`ARB`/`SOLANA` policy objects — explicitly "(later)" per `design.md`'s own VERBATIM table.
- Any dispatcher/registry mapping `Chain` → `FinalityPolicy` — no caller exists yet.
- `chain.tx.finalized` / `chain.tx.confirmed` event emission (R9, R10) — not yet built anywhere in this
  codebase; out of scope.
- Any change to `contracts/` — none of the named contract files exist in this repository yet.

## Business Rules

- **R6.** Ethereum finality requires the tx's block at or below the beacon `finalized` checkpoint, not
  a fixed confirmation count.
- **R7.** Tron finality requires the tx's block to be solidified (~19 confirmations), per the Tron
  finality policy object.

## Locked Decisions

- **L4.** Finality is a per-chain policy object, not a global constant; adding a chain adds a policy
  object; no confirmation count is hardcoded across chains.

## Dependencies

- `com.themistra.crypto.adapter.Chain` (`ETHEREUM`, `TRON`).
- `com.themistra.crypto.adapter.model.FinalityStatus` (`txBlockNumber`, `currentBlockNumber`,
  `finalizedBlockNumber`).
- No other class, config key, repository, or external library.

## Inputs

- `FinalityStatus` — the sole runtime input to `isFinal`. Always the direct result of a prior,
  successful `ChainAdapter.getFinalityStatus(txHash)` call — never a value a caller fabricates
  independently.

## Outputs

- `FinalityPolicy` — public interface:
  ```java
  public interface FinalityPolicy {
      Chain chain();
      boolean isFinal(FinalityStatus status);
  }
  ```
- `EthereumFinalityPolicy implements FinalityPolicy` — `chain()` returns `Chain.ETHEREUM`;
  `isFinal` returns `status.txBlockNumber() <= status.finalizedBlockNumber()`.
- `TronFinalityPolicy implements FinalityPolicy` — `chain()` returns `Chain.TRON`; identical
  `isFinal` comparison (the solidified block *number* — already fetched by `TronAdapter` — is what
  `finalizedBlockNumber` carries; this policy never re-derives "~19" itself).
- Both classes are `@Component` (mirrors the `token/` package's precedent: no configuration
  parameters, no per-provider-instance construction the way `adapter/`'s manually-wired
  `EthereumAdapter`/`TronAdapter` need).

## State Changes

None. No persistence, no outbox, no event emission, no external call.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/finality/FinalityPolicy.java`
- `services/crypto/src/main/java/com/themistra/crypto/finality/EthereumFinalityPolicy.java`
- `services/crypto/src/main/java/com/themistra/crypto/finality/TronFinalityPolicy.java`

## Files to Modify

None.

## Files NOT to Modify

- `adapter/ChainAdapter.java`, `adapter/eth/EthereumAdapter.java`, `adapter/tron/TronAdapter.java`,
  `adapter/model/FinalityStatus.java`, `adapter/Chain.java` — all already correct for this task's needs.
- `common/config/FinalityProperties.java` — already scoped correctly (enabled-chains only).
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R6).** `EthereumFinalityPolicy.isFinal` returns `true` when `txBlockNumber <=
  finalizedBlockNumber`, `false` when `txBlockNumber > finalizedBlockNumber`. No confirmation-count
  arithmetic performed.
- **AC2 (R7).** `TronFinalityPolicy.isFinal` returns `true`/`false` under the identical comparison;
  `~19` never appears as a literal threshold in code.
- **AC3 (L4).** Both classes implement the shared `FinalityPolicy` interface; each self-identifies its
  `Chain` via `chain()`.
- **AC4 (L4, scope).** Neither policy performs any RPC/network call — pure function of its
  `FinalityStatus` argument.
- **AC5 (Q4, "Confirm").** `TronFinalityPolicy`'s Javadoc documents the already-shipped confirmation-
  count basis (block depth from current head, identical to Ethereum's) as this task's resolution of Q4.

## Required Tests

- `shouldRequireBeaconFinalizedCheckpointForEthereumFinality` (`package.md` §8, named) — AC1.
- `shouldRequireSolidifiedBlockForTronFinality` (`package.md` §8, named) — AC2.
- Boundary: `txBlockNumber == finalizedBlockNumber` → final, for each policy.
- Boundary: `txBlockNumber == finalizedBlockNumber + 1` → not final, for each policy.
- Scripted-heads: a `FinalityStatus` with `currentBlockNumber` far ahead of both `txBlockNumber` and
  `finalizedBlockNumber`, confirming `currentBlockNumber` plays no role in the decision, for each
  policy.
- `chain()` returns the correct `Chain` constant, for each policy.
- Null `FinalityStatus` argument fails fast (`NullPointerException`, not a silent `false`) — per
  Constraints below.

## Constraints

- **Performance:** O(1) comparison; not a concern.
- **Security:** none — no external input beyond the trusted, already-validated `FinalityStatus`
  produced internally by this service's own adapters.
- **Thread-safety:** both implementations are stateless (no fields beyond none); safe for concurrent
  use as Spring singletons, same as every other pure predicate `@Component` in this codebase.
- **Transaction:** none — no persistence.
- **Module boundaries:** no dependency on `observation`, `quorum`, `provider`, `token`, or `events`
  packages; no path to `kms:Sign` (agents.md — that path is `attest`-module-only, not yet built).
- **Null handling:** `isFinal(null)` throws `NullPointerException` via `Objects.requireNonNull` — a
  `FinalityStatus` is always the direct result of a prior successful `getFinalityStatus` call, never a
  caller-optional value the way `AddressPoisoningDetector`'s candidate/history parameters legitimately
  can be absent; a null argument here represents a caller bug, not a valid domain state, so failing fast
  is the correct behavior (mirrors `ChainAdapter.getFinalityStatus`'s own "caller error, not a sentinel
  case" contract for a non-existent transaction).

## Open Questions

No blockers.
