# crypto · T05 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-03. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in.

## Task

Adapter interface + fakes. Define `ChainAdapter` (design §4c) and `Chain`. Build a scripted
`FakeChainAdapter` for tests that can agree, disagree, lag, and reorg.

## Purpose

Establish the one contract every chain integration (real or fake) implements: `ChainAdapter`. Every
later task in "Adapters, providers, quorum" and beyond depends on this shape existing first —
`EthereumAdapter`/`TronAdapter` (tasks 6/7) implement it for real, `QuorumEvaluator` (task 9) compares
answers across multiple instances of it, and every unit test from here forward scripts
`FakeChainAdapter` instead of hitting a real chain (agents.md: "real RPC providers are never called in
tests or CI").

## Scope

**In:**
- `ChainAdapter` — VERBATIM interface from design §4c (5 methods, copied exactly).
- `Chain` — enum, `ETHEREUM`, `TRON` only (design.md §2 launch scope).
- `TxResult`, `TokenInfo`, `FinalityStatus`, `Subscription`, `ObservationSink` — designed to the
  minimum needed for the observation log's fact types and the `chain.tx.finalized` event schema,
  refined per amendments #1, #4, #5, #8 below.
- `FakeChainAdapter` (test scope) — agree/disagree emergent from scripting multiple instances;
  lag via `TxResult.exists=false` or lower `confirmations`/`blockNumber`; reorg via a re-script
  method that pushes to subscriptions matching the new result's own addresses (amendment #2).

**Out:**
- `EthereumAdapter`/`TronAdapter` (tasks 6/7).
- Retrofitting `Chain` into T03's config `String` fields — **not retrofitted**; bridged instead via
  the enum's own built-in `Chain.valueOf(String)` (amendment #3), no new converter file.
- ArchUnit rules for `adapter/` — task 25's job.
- Renaming `ObservationSink` — design.md §6 names this file explicitly; not authorized to deviate
  (amendment #5).
- Any Jackson/JSON-serialization annotation on `TxResult` — no JSON boundary exists in this task's
  scope; premature (amendment #4).
- `Observation`/`ObservationSnapshotStore` (task 8), `QuorumEvaluator` (task 9), `TokenValidator`
  (task 11), `FinalityPolicy` implementations (task 14), the watcher layer (task 16).

## Business Rules

None — no `R`-numbered requirement is independently testable by this task's own deliverable.

## Locked Decisions

- **L4.** `FinalityStatus` carries raw per-chain state only — no precomputed finality decision.
- **L7.** `TokenInfo`'s identity is `contractAddress` (paired with `ChainAdapter.chain()`); `symbol`
  is display-only. Enforced at the business-logic-discipline level, not via a record `equals`
  override (amendment #1).
- **L14.** `ChainAdapter` must not preclude a sidecar-backed implementation.
- **L15.** All new files under `adapter/` (main) or its test-scope mirror.

### Amendments folded in from Phase 3 (Kimi) design challenge, human-approved 2026-09-03

1. **`TokenInfo` identity is a business-logic-discipline rule, not a record-equality guarantee.**
   AC4 is restated: business code (and any future `Set`/`Map` keyed on token identity) MUST compare/key
   by `tokenInfo.contractAddress()` explicitly — MUST NOT rely on `TokenInfo.equals()`/`hashCode()` for
   contract-address-only comparison, since the record's auto-generated `equals` includes `symbol`/
   `decimals` too. This is documented on the class Javadoc, not fixed via an `equals` override
   (overriding a record's generated equality is itself an anti-pattern this codebase avoids). Real
   token-identity lookups (task 11) are DB-keyed queries, not Java object comparisons, so this is a
   documentation guard, not a functional gap.
2. **`FakeChainAdapter`'s reorg-to-subscription mapping uses `TxResult`'s own `fromAddress`/
   `toAddress` fields — no new parameter or index needed.** `simulateReorg(String txHash, TxResult
   newResult)` re-scripts the tx and pushes `newResult` to every active subscription whose address
   equals `newResult.fromAddress()` or `newResult.toAddress()`. (The original TIB sketch omitted this
   wiring detail; the underlying data was already present in `TxResult`'s design.)
3. **`Chain.valueOf(String)`** (built into every Java enum) is the documented bridge between T03's
   regex-constrained `String` config values and this task's new `Chain` enum — no new converter
   method or file. T03's `@Pattern(regexp = "ETHEREUM|TRON")` already constrains values to exact
   enum-constant spellings by construction, so `Chain.valueOf(...)` never fails on a validly-bound
   config value. Documented on `Chain`'s own Javadoc for future tasks to find.
4. **`TxResult.amount`'s Javadoc states explicitly that this type is an in-process value object,
   never itself JSON-serialized** — the observation log persists the *raw* provider payload verbatim
   (L3), never a normalized `TxResult`, so no JSON wire-format boundary exists for this type in the
   current design. No Jackson annotation added (would be a speculative, premature dependency into an
   otherwise framework-free package). A future task that *does* put `TxResult` on a JSON boundary must
   add the guard at that point, per agents.md's decimal-string-not-JSON-number rule.
5. **`ObservationSink`'s Javadoc clarifies it is specifically for address-watch transaction
   observations pushed by `subscribeAddress`** — not a general channel for `getTokenInfo`/
   `getFinalityStatus` responses. Not renamed (design.md §6 names this file explicitly; spec files are
   not modifiable, and neither is deviating from a name the spec itself fixes).
6. **Unscripted `FakeChainAdapter` queries throw `IllegalStateException`**, naming the unscripted key
   (`txHash`/`contractAddress`) in the message — mirrors `EventTopics.forAggregateType`'s exact
   convention (T04).
7. **`subscribeAddress` does NOT replay historical transactions.** Only observations pushed *after*
   subscription (via a future `scriptTx`-driven push or `simulateReorg`) reach the sink — mirrors how
   a real websocket-style subscription behaves; a caller needing historical state calls `getTx`
   separately. Documented explicitly; a test proves no immediate push occurs for an address already
   scripted before `subscribeAddress` was called.
8. **`FinalityStatus.finalizedCheckpointBlockNumber` is renamed to `finalizedBlockNumber` and is no
   longer nullable/Ethereum-only.** Both Ethereum's beacon-finalized checkpoint and Tron's solidified
   block are structurally the same concept — the highest block number the chain's own consensus
   considers irreversibly final — so a single non-null field serves both, and finality reduces
   uniformly to `txBlockNumber <= finalizedBlockNumber` for either chain (each `FinalityPolicy`, task
   14, still owns *how* that number was obtained per chain). `currentBlockNumber` is retained
   (informative, not required for the finality check itself).
9. **The `ChainAdapter` structural test asserts method name + return type + parameter *types* only**
   — not parameter names (not preserved by reflection without `-parameters`, which this project does
   not compile with) and not annotations/declaration order.

## Dependencies

None beyond the JDK (`java.math.BigDecimal`, `java.util`). No Spring, no persistence, no config, no
contracts, no Jackson (amendment #4).

## Inputs / Outputs / State Changes

None at runtime for this task itself — `ChainAdapter` is a contract; `FakeChainAdapter` takes
programmatic scripting calls from (future) test code. No state changes.

## Files to Create

Main (`services/crypto/src/main/java/com/themistra/crypto/adapter/`):
- `ChainAdapter.java`
  ```java
  public interface ChainAdapter {
      Chain chain();
      TxResult getTx(String txHash);
      TokenInfo getTokenInfo(String contractAddress);
      Subscription subscribeAddress(String address, ObservationSink sink);
      FinalityStatus getFinalityStatus(String txHash);
  }
  ```
- `Chain.java` — `public enum Chain { ETHEREUM, TRON }` (Javadoc documents `valueOf` bridge, amendment #3).
- `model/TxResult.java`
  ```java
  public record TxResult(
          boolean exists, String txHash, String fromAddress, String toAddress,
          String tokenContractAddress, BigDecimal amount, int confirmations, long blockNumber) {}
  ```
  (Javadoc: in-process value type, never itself JSON-serialized — amendment #4.)
- `model/TokenInfo.java`
  ```java
  public record TokenInfo(String contractAddress, String symbol, int decimals) {}
  ```
  (Javadoc: identity is `contractAddress` only; never compare/key via `.equals()`/`.hashCode()` —
  amendment #1.)
- `model/FinalityStatus.java`
  ```java
  public record FinalityStatus(long txBlockNumber, long currentBlockNumber, long finalizedBlockNumber) {}
  ```
  (amendment #8 — renamed, non-nullable, populated by both chains.)
- `model/Subscription.java` — `public interface Subscription { void cancel(); }`
- `ObservationSink.java` — `public interface ObservationSink { void onObservation(TxResult result); }`
  (Javadoc: tx-observations only, not token/finality — amendment #5.)

Test (`services/crypto/src/test/java/com/themistra/crypto/adapter/`):
- `FakeChainAdapter.java` — see Constraints for its scripting-API shape.
- A test class exercising the four AC3 behaviors, exception behavior (AC6), and no-replay semantics
  (amendment #7).

## Files to Modify

None.

## Files NOT to Modify

- `ProviderProperties.java`, `FinalityProperties.java` (T03) — not retrofitted.
- `services/crypto/pom.xml` — no new dependency.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1.** `ChainAdapter` matches design §4c's verbatim interface exactly.
- **AC2.** `Chain` has exactly two values, `ETHEREUM` and `TRON`.
- **AC3.** `FakeChainAdapter` supports agree, disagree, lag, and reorg (reorg per amendment #2's
  address-matching logic).
- **AC4 (L7, amendment #1).** No business-logic code compares/keys `TokenInfo` via `.equals()`/
  `.hashCode()` — only `.contractAddress()` (paired with the owning adapter's `chain()`).
- **AC5 (L4, amendment #8).** `FinalityStatus` has no boolean "is final" field; `finalizedBlockNumber`
  is populated by every chain (no nullable/single-chain special case).
- **AC6 (amendment #6).** Unscripted `FakeChainAdapter` queries throw `IllegalStateException` naming
  the unscripted key.
- **AC7 (amendment #7).** `subscribeAddress` does not replay pre-existing scripted transactions.

## Required Tests

- `FakeChainAdapter`'s four AC3 behaviors, including reorg's address-matching subscription push.
- Unscripted-query exception type and message content (AC6).
- No-replay-on-subscribe (AC7).
- Structural `ChainAdapter` test, scoped per amendment #9 (name + return type + parameter types only).

## Constraints

- **Module boundaries (L15):** everything under `adapter/`; `FakeChainAdapter` under the test-source
  mirror of the same package.
- **Null handling:** `FakeChainAdapter` throws (not `null`) on an unscripted query (AC6); model
  records are plain data carriers, no validation framework at this layer.
- **Money (agents.md):** `TxResult.amount` is `BigDecimal`, base units, never floating point; never
  itself JSON-serialized in this task's scope (amendment #4).
- **Thread-safety:** not a concern — `FakeChainAdapter` is a single-threaded test fixture.
- **`FakeChainAdapter`'s scripting API** (informative sketch, Phase 5 finalizes exact method names):
  fluent `scriptTx(String txHash, TxResult result)`, `scriptTokenInfo(String contractAddress,
  TokenInfo info)`, `scriptFinalityStatus(String txHash, FinalityStatus status)`; `subscribeAddress`
  stores the sink keyed by address (no replay, amendment #7), returns a cancellable `Subscription`;
  `simulateReorg(String txHash, TxResult newResult)` re-scripts and pushes to matching subscriptions
  per amendment #2.

## Open Questions

No blockers.
