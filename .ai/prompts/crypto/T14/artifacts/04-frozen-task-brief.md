STATUS: FROZEN

# crypto · T14 · Phase 4 — Frozen Task Brief

**Human Approval gate.** Approved 2026-09-07. Findings from Phase 3 (Kimi design challenge) are folded
in below.

## Design-challenge resolution log

| # | Finding | Disposition | Change made |
|---|---|---|---|
| 1 | `FinalityStatus` carries no chain discriminator; a caller could pass a Tron status to the Ethereum policy undetected | **ACCEPTED (documentation only)** | `FinalityPolicy` and both implementations' Javadoc will state explicitly that the caller is responsible for routing the correct `FinalityStatus` to the matching policy; `chain()` is an identifier for a future dispatcher, not a self-check. Adding a `chain` field to `FinalityStatus` itself is REJECTED as out of scope — that type is frozen (shared by both real adapters, `FakeChainAdapter`, and its own shape test); modifying it is a materially larger change than this task warrants. |
| 2 | No module-boundary test required for the new `finality/` package | **ACCEPTED** | Added `FinalityModuleBoundaryTest` (mirrors T10's `ProviderModuleBoundaryTest`/T11's `TokenModuleBoundaryTest`) to Required Tests below. |
| 3 | The two policy implementations will be identical except for the returned `Chain` constant — maintenance risk that someone "DRYs" them together later | **ACCEPTED** | Both classes' Javadoc will state the duplication is intentional and required by L4 — each chain gets its own policy object even when today's comparison happens to coincide. |
| 4 | No test asserts each class `isInstanceOf(FinalityPolicy.class)` | **REJECTED** | Checked this codebase's one precedent for a reflective interface-conformance test, `ChainAdapterShapeTest` — it exists specifically because `ChainAdapter` is a VERBATIM design.md interface being checked for exact shape. `FinalityPolicy` carries no such VERBATIM text, and "implements the interface" is already a compile-time guarantee (every required test uses the concrete type or calls interface methods directly). No other interface/impl pair in this codebase has this test; adding it here is unprecedented, zero-coverage-value. |
| 5 | Policies perform no defense-in-depth re-validation of `finalizedBlockNumber > currentBlockNumber` | **REJECTED (documentation only)** | Both `EthereumAdapter.getFinalityStatus` and `TronAdapter.getFinalityStatus` already throw `IllegalStateException` for exactly this invalid state before a `FinalityStatus` is ever constructed (confirmed in Phase 0). Re-guarding an invariant already enforced once, upstream, by the sole legitimate producer of this type is the same unprecedented-hardening pattern already rejected at T13 Phase 9 Issue 8. The trust boundary is documented in Javadoc (folded into the null-handling constraint already in the TIB) instead. |
| 6 | `FinalityProperties.enabledChains` is not linked to the new policies — no runtime effect until wired | **ACCEPTED** | TIB's "Out of Scope" section (below) now says explicitly that wiring `FinalityProperties.enabledChains` to the available `FinalityPolicy` beans is a future task's job (most plausibly the watcher/attest layer), so the config's present lack of runtime effect is a documented, not-yet-resolved dependency rather than a silent gap. |
| 7 | No test at `Long.MAX_VALUE`/`MIN_VALUE` block numbers | **REJECTED** | Kimi's own evidence states block numbers "will never approach these values." `<=` on primitive `long` is a JLS-guaranteed correct signed comparison, not an external library with undocumented edge behavior (unlike T12's `Base58Check`/T13's `regionMatches`, which genuinely warranted empirical verification). No other numeric comparison anywhere in this codebase carries such a test. Unprecedented, low-value hardening. |

## Frozen brief

### Task

Implement `EthereumFinalityPolicy` and `TronFinalityPolicy`, both behind a shared `FinalityPolicy`
interface, in a new `finality/` package. Document (confirm) the Tron confirmation-count basis (Q4).

### Purpose

Supply the per-chain boolean "is this transaction final" decision (L4, R6/R7) as a pure, stateless
policy object, decoupled from how each adapter obtains the finalized/solidified block number (already
built in T06/T07).

### Scope

**In:**
- `FinalityPolicy` interface: `Chain chain()` + `boolean isFinal(FinalityStatus status)`.
- `EthereumFinalityPolicy` — R6: final iff `txBlockNumber <= finalizedBlockNumber`.
- `TronFinalityPolicy` — R7: final iff `txBlockNumber <= finalizedBlockNumber`.
- Javadoc on `FinalityPolicy` (interface-level) documenting: (a) caller-routing responsibility (Finding
  1), (b) the trust boundary — `FinalityStatus` is assumed adapter-produced and pre-validated, never
  re-validated here (Finding 5), (c) that the near-identical implementations are intentional per L4,
  not an invitation to merge them (Finding 3).
- `TronFinalityPolicy` Javadoc additionally documents Q4's resolution: the `chain.tx.confirmed`
  confirmation-count basis is already fixed by T06/T07's shipped code as block depth from the current
  head (`currentBlockNumber - txBlockNumber + 1`), identical in kind to Ethereum's — not confirmations
  toward the solidified block specifically.
- `FinalityModuleBoundaryTest` (Finding 2).

**Out:**
- Any RPC/network call, or any change to `EthereumAdapter`/`TronAdapter`/`FinalityStatus`/`ChainAdapter`
  — all already correctly implemented.
- Adding a `chain` field to `FinalityStatus` (Finding 1's alternative remedy — rejected as out of scope).
- `BASE`/`ARB`/`SOLANA` policy objects — explicitly "(later)" per design.md's VERBATIM table.
- Any dispatcher/registry mapping `Chain` → `FinalityPolicy`, and wiring `FinalityProperties.enabledChains`
  to it — no caller exists yet; deferred to a future task (most plausibly the watcher/attest layer,
  Finding 6). `FinalityProperties.enabledChains` has no runtime effect until that future wiring exists —
  documented, not a defect of this task.
- `chain.tx.finalized` / `chain.tx.confirmed` event emission (R9, R10) — not yet built anywhere in this
  codebase.
- Any change to `contracts/` — none of the named contract files exist in this repository yet.
- A reflective `isInstanceOf(FinalityPolicy.class)` test (Finding 4 — rejected).
- Defense-in-depth re-validation of `FinalityStatus` invariants inside the policy (Finding 5 — rejected).
- A `Long.MAX_VALUE`/`MIN_VALUE` boundary test (Finding 7 — rejected).

### Business Rules

- **R6.** Ethereum finality requires the tx's block at or below the beacon `finalized` checkpoint, not
  a fixed confirmation count.
- **R7.** Tron finality requires the tx's block to be solidified (~19 confirmations), per the Tron
  finality policy object.

### Locked Decisions

- **L4.** Finality is a per-chain policy object, not a global constant; adding a chain adds a policy
  object; no confirmation count is hardcoded across chains.

### Dependencies

- `com.themistra.crypto.adapter.Chain` (`ETHEREUM`, `TRON`).
- `com.themistra.crypto.adapter.model.FinalityStatus` (`txBlockNumber`, `currentBlockNumber`,
  `finalizedBlockNumber`).
- No other class, config key, repository, or external library.

### Inputs

- `FinalityStatus` — the sole runtime input to `isFinal`. Always the direct result of a prior,
  successful `ChainAdapter.getFinalityStatus(txHash)` call.

### Outputs

- `FinalityPolicy` interface:
  ```java
  public interface FinalityPolicy {
      Chain chain();
      boolean isFinal(FinalityStatus status);
  }
  ```
- `EthereumFinalityPolicy implements FinalityPolicy` (`@Component`) — `chain()` → `Chain.ETHEREUM`;
  `isFinal` → `status.txBlockNumber() <= status.finalizedBlockNumber()`.
- `TronFinalityPolicy implements FinalityPolicy` (`@Component`) — `chain()` → `Chain.TRON`; identical
  comparison.

### State Changes

None. No persistence, no outbox, no event emission, no external call.

### Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/finality/FinalityPolicy.java`
- `services/crypto/src/main/java/com/themistra/crypto/finality/EthereumFinalityPolicy.java`
- `services/crypto/src/main/java/com/themistra/crypto/finality/TronFinalityPolicy.java`

### Files to Modify

None.

### Files NOT to Modify

- `adapter/ChainAdapter.java`, `adapter/eth/EthereumAdapter.java`, `adapter/tron/TronAdapter.java`,
  `adapter/model/FinalityStatus.java`, `adapter/Chain.java`.
- `common/config/FinalityProperties.java`.
- Any file under `spec/`.

### Acceptance Criteria

- **AC1 (R6).** `EthereumFinalityPolicy.isFinal` returns `true` iff `txBlockNumber <=
  finalizedBlockNumber`. No confirmation-count arithmetic performed.
- **AC2 (R7).** `TronFinalityPolicy.isFinal` returns `true`/`false` under the identical comparison;
  `~19` never appears as a literal threshold in code.
- **AC3 (L4).** Both classes implement the shared `FinalityPolicy` interface; each self-identifies its
  `Chain` via `chain()`.
- **AC4 (L4, scope).** Neither policy performs any RPC/network call.
- **AC5 (Q4, "Confirm").** `TronFinalityPolicy`'s Javadoc documents the already-shipped confirmation-
  count basis as this task's resolution of Q4.
- **AC6 (module boundary, Finding 2).** `finality/` imports nothing from `observation`, `provider`,
  `quorum`, `token`, or `events`, and imports only `adapter.Chain`/`adapter.model.FinalityStatus` from
  `adapter`.

### Required Tests

- `shouldRequireBeaconFinalizedCheckpointForEthereumFinality` (`package.md` §8, named) — AC1.
- `shouldRequireSolidifiedBlockForTronFinality` (`package.md` §8, named) — AC2.
- Boundary: `txBlockNumber == finalizedBlockNumber` → final, for each policy.
- Boundary: `txBlockNumber == finalizedBlockNumber + 1` → not final, for each policy.
- Scripted-heads: a `FinalityStatus` with `currentBlockNumber` far ahead of both `txBlockNumber` and
  `finalizedBlockNumber`, confirming `currentBlockNumber` plays no role in the decision, for each
  policy.
- `chain()` returns the correct `Chain` constant, for each policy.
- Null `FinalityStatus` argument fails fast (`NullPointerException`), for each policy.
- `FinalityModuleBoundaryTest` (Finding 2).

### Constraints

- **Performance:** O(1) comparison.
- **Security:** none — input is a trusted, already-validated value produced internally by this
  service's own adapters.
- **Thread-safety:** both implementations are stateless; safe as Spring singletons.
- **Transaction:** none.
- **Module boundaries:** no dependency on `observation`, `quorum`, `provider`, `token`, or `events`; no
  path to `kms:Sign`.
- **Null handling:** `isFinal(null)` throws `NullPointerException` via `Objects.requireNonNull` — a
  `FinalityStatus` is always the direct result of a prior successful `getFinalityStatus` call, never a
  caller-optional value; a null argument represents a caller bug, not a valid domain state.

### Open Questions

No blockers. All Phase 3 findings resolved above (4 accepted, 3 rejected with reasons).
