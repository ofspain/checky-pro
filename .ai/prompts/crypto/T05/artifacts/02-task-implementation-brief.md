# crypto · T05 · Phase 2 — Task Implementation Brief (TIB)

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
- Four supporting model types (`TxResult`, `TokenInfo`, `FinalityStatus`, `Subscription`) and
  `ObservationSink` — none has a verbatim shape in the spec; designed below to the minimum needed for
  the fact types R1/L3's observation log already fixes (`existence | amount | token | confirmations`
  via `getTx`, `finality` via `getFinalityStatus` — matching `V1__chain_baseline.sql`'s
  `observations.fact_type` check constraint) and for the `chain.tx.finalized` event schema's own
  fields (design §4c), without guessing at task 8/9/11/14/16's own internal implementation choices.
- `FakeChainAdapter` (test scope) — scriptable per-instance, so **agree/disagree are emergent from
  scripting multiple instances with matching/mismatching data**, not a special mode; **lag** is
  representable via `TxResult.exists=false` or a lower `confirmations`/`blockNumber` than other
  scripted instances; **reorg** needs explicit support (a temporal change), via a re-script method
  that also pushes the new state to any live subscription.

**Out:**
- `EthereumAdapter`/`TronAdapter` (tasks 6/7) — real chain clients.
- Retrofitting `Chain` into T03's `ProviderProperties.ChainProviders.chain`/
  `FinalityProperties.enabledChains` (currently plain, regex-constrained `String`) — **decision
  (resolves Phase 1 Open Question 1): do not retrofit.** The task statement only says "define," not
  "migrate existing config"; T03's files are already shipped and tested, and aren't authorized by
  this task's own statement.
- ArchUnit rules for `adapter/` — task 25 ("ArchUnit/module boundaries") is a dedicated later task.
- `Observation`/`ObservationSnapshotStore` (task 8), `QuorumEvaluator` (task 9), `TokenValidator`
  (task 11), `FinalityPolicy` implementations (task 14), the watcher layer (task 16) — all consume
  these shapes later, none built here.

## Business Rules

None — no `R`-numbered requirement is independently testable by this task's own deliverable (Phase 1
finding; mirrors T01's own precedent).

## Locked Decisions

- **L4.** `FinalityStatus` carries raw per-chain state only (`txBlockNumber`, `currentBlockNumber`, a
  nullable `finalizedCheckpointBlockNumber` for chains with that concept) — no precomputed
  `isFinal`/confirmation-count-vs-threshold decision baked into the adapter layer; that's the
  `FinalityPolicy`'s job (task 14).
- **L7.** `TokenInfo`'s identity is `contractAddress` (paired with `ChainAdapter.chain()`); `symbol` is
  present for display only, documented as never used for identity/equality.
- **L14.** `ChainAdapter` as an interface must not preclude a sidecar-backed implementation — nothing
  in its shape assumes a direct RPC client.
- **L15.** All new files under `adapter/` (main) or the test-scope mirror of it; no other module
  exists yet to conflict with.

## Dependencies

None beyond the JDK (`java.math.BigDecimal` for money — agents.md: never floating point; `java.util`).
No Spring, no persistence, no config, no contracts (none exist yet for these types).

## Inputs

None at runtime for this task itself — `ChainAdapter` is a contract; `FakeChainAdapter` takes
programmatic scripting calls from test code (later tasks).

## Outputs

None at runtime for this task itself — the interface, enum, model types, and fake are inert until a
later task implements/consumes them.

## State Changes

None.

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
- `Chain.java` — `public enum Chain { ETHEREUM, TRON }`
- `model/TxResult.java`
  ```java
  public record TxResult(
          boolean exists, String txHash, String fromAddress, String toAddress,
          String tokenContractAddress, BigDecimal amount, int confirmations, long blockNumber) {}
  ```
- `model/TokenInfo.java`
  ```java
  public record TokenInfo(String contractAddress, String symbol, int decimals) {}
  ```
- `model/FinalityStatus.java`
  ```java
  public record FinalityStatus(long txBlockNumber, long currentBlockNumber,
          Long finalizedCheckpointBlockNumber /* nullable - Ethereum beacon-finalized only */) {}
  ```
- `model/Subscription.java` — `public interface Subscription { void cancel(); }`
- `ObservationSink.java` — `public interface ObservationSink { void onObservation(TxResult result); }`

Test (`services/crypto/src/test/java/com/themistra/crypto/adapter/`):
- `FakeChainAdapter.java` — see Constraints for its scripting-API shape.
- A test class exercising the four AC3 behaviors (exact name TBD Phase 5).

## Files to Modify

None.

## Files NOT to Modify

- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java`,
  `FinalityProperties.java` — T03 deliverables; not retrofitted (see Scope/Out).
- `services/crypto/pom.xml` — no new dependency needed.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1.** `ChainAdapter` matches design §4c's verbatim interface exactly.
- **AC2.** `Chain` has exactly two values, `ETHEREUM` and `TRON`.
- **AC3.** `FakeChainAdapter` supports all four scripted behaviors: agree (two instances scripted with
  equal `TxResult`s for the same `txHash`), disagree (two instances scripted with differing results
  for the same `txHash`), lag (a scripted result reflecting fewer confirmations/lower block number, or
  `exists=false`, relative to another instance), and reorg (re-scripting a `txHash` after an initial
  script invalidates the prior answer, and — if a subscription is active for the relevant address —
  pushes the new state to the registered `ObservationSink`).
- **AC4 (L7).** `TokenInfo` never uses `symbol` for identity/equality; only `contractAddress`
  (combined with the owning adapter's `chain()`) does.
- **AC5 (L4).** `FinalityStatus` has no boolean "is final" field and no hardcoded confirmation
  threshold — only raw block/checkpoint data.
- **AC6.** No unscripted call to `FakeChainAdapter.getTx`/`getTokenInfo`/`getFinalityStatus` returns
  `null` or a default/zero-value silently — it throws, matching this codebase's established
  fail-loudly convention (`EventTopics.forAggregateType`, T04).

## Required Tests

- A test proving each of `FakeChainAdapter`'s four AC3 behaviors concretely (agree/disagree/lag/
  reorg), including the reorg case's subscription-push behavior.
- A test asserting an unscripted query throws (AC6).
- A lightweight reflection or structural test asserting `ChainAdapter`'s method set matches design
  §4c exactly (name/parameter/return-type triples) — the verbatim-artifact discipline
  `ChainBaselineMigrationIntegrationTest` established for SQL, adapted for an interface (AC1).

## Constraints

- **Module boundaries (L15):** everything under `adapter/`; `FakeChainAdapter` under the test-source
  mirror of the same package.
- **Null handling:** `FakeChainAdapter` throws (not `null`) on an unscripted query (AC6); model
  records' constructors are not required to null-check in this task (no validation framework in play
  at this layer — these are plain data carriers, unlike T03's `@ConfigurationProperties` records).
- **Money (agents.md):** `TxResult.amount` is `BigDecimal`, base units, never floating point.
- **Thread-safety:** not a concern in this task — `FakeChainAdapter` is a single-threaded test
  fixture, not production code; no concurrent access is expected from test code.
- **`FakeChainAdapter`'s scripting API** (informative sketch, not exhaustive — Phase 5 finalizes
  exact method names): fluent `scriptTx(String txHash, TxResult result)`,
  `scriptTokenInfo(String contractAddress, TokenInfo info)`,
  `scriptFinalityStatus(String txHash, FinalityStatus status)`; `subscribeAddress` stores the sink
  keyed by address and returns a `Subscription` whose `cancel()` removes it; a `simulateReorg(String
  txHash, TxResult newResult)`-style method re-scripts the tx and, if a subscription is active for a
  relevant address, invokes the stored sink with the new state.

## Open Questions

No blockers. (Retrofitting `Chain` into T03's config was the one real scoping question Phase 1
raised — resolved above as "do not retrofit," not deferred.)
