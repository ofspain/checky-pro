<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review) for crypto · T05. -->

# crypto · T05 · Phase 8 — Independent Code Review Findings

**Scope:** Review the Phase 6 implementation (`adapter/Chain.java`, `adapter/ChainAdapter.java`, `adapter/ObservationSink.java`, `adapter/model/TxResult.java`, `adapter/model/TokenInfo.java`, `adapter/model/FinalityStatus.java`, `adapter/model/Subscription.java`) and the Phase 7 self-review with fresh, adversarial eyes.

**Directive:** Do not rewrite. Report findings as **Issue · Evidence · Recommendation · Confidence**.

---

## Finding 1 — `TxResult.exists=false` semantics and `getTx` contract are undocumented

**Issue:** Neither `TxResult` nor `ChainAdapter.getTx` states that a legitimately unobserved/nonexistent transaction must be represented by returning `TxResult(exists=false, ...)`, not by throwing. R1 treats "tx existence" as a quorum-checked fact, so `exists=false` is a normal, expected answer (e.g., a provider lagging behind chain head). If a real adapter implementer throws instead, quorum evaluation will misclassify lag as provider failure and never reach a stable "not yet seen" state.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TxResult.java` — no Javadoc on `exists` or the record describing the `exists=false` case.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java:18` — `getTx` comment only says "provider-scoped; quorum compares across adapters."
- `spec/crypto-service/requirements.md` R1: "tx existence, amount, token contract, confirmations, finality status" are independently quorum-checked facts.
- This was Phase 7 self-review Finding 1; it remains unfixed.

**Recommendation:** Add Javadoc to `ChainAdapter.getTx` and/or `TxResult` explicitly stating: (a) a transaction this provider has not observed returns `TxResult(exists=false, ...)`; (b) throwing is reserved for transport/provider errors, not for absence of the transaction; (c) when `exists=false`, the remaining fields carry no meaningful data and should be zero/null. Add a unit test documenting this behavior.

**Confidence:** High.

---

## Finding 2 — Required T05 tests are entirely missing

**Issue:** The frozen brief's Required Tests section lists tests for `FakeChainAdapter`'s four AC3 behaviors (agree/disagree/lag/reorg), an unscripted-query-throws test, and a structural test for `ChainAdapter`. No test files exist under `services/crypto/src/test/java/com/themistra/crypto/adapter/`.

**Evidence:**
- `services/crypto/src/test/java/com/themistra/crypto/adapter/` directory does not exist (verified via glob/find).
- TIB §Required Tests explicitly lists:
  - A test proving each of `FakeChainAdapter`'s four AC3 behaviors concretely, including reorg subscription-push.
  - A test asserting an unscripted query throws (AC6).
  - A structural/reflection test asserting `ChainAdapter` matches design §4c exactly.
- TIB §Acceptance Criteria (AC1–AC6) cannot be verified without automated tests.

**Recommendation:** Add the missing tests before Phase 9 sign-off. Include `FakeChainAdapterTest` covering agree/disagree/lag/reorg + unscripted throw, and `ChainAdapterStructuralTest` verifying method names/return types/parameter types against design.md §4c.

**Confidence:** High.

---

## Finding 3 — `FinalityStatus.finalizedBlockNumber` as primitive `long` cannot represent unavailable state

**Issue:** `FinalityStatus.finalizedBlockNumber` is a primitive `long`. If a real adapter cannot determine the chain's finalized/solidified block (e.g., beacon API outage, RPC error, or a chain where the concept is not a single number), it has no way to represent "unknown" except a misleading sentinel like `0` or `Long.MAX_VALUE`. A nullable `Long` would let the adapter signal unavailability and let the policy fail closed.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/FinalityStatus.java:16-20`:
  ```java
  public record FinalityStatus(long txBlockNumber, long currentBlockNumber, long finalizedBlockNumber) {}
  ```
- The Javadoc unifies Ethereum's beacon-finalized checkpoint and Tron's solidified block as a single number, but does not address the unavailable case.
- `spec/crypto-service/design.md` §4c finality policy table describes Tron and Ethereum in terms of block-number thresholds; it does not explicitly require a nullable representation, but "not available" is a realistic runtime state.

**Recommendation:** Change `finalizedBlockNumber` from `long` to `Long` and document that `null` means "finalized/solidified block could not be determined." Update any tests accordingly. Alternatively, explicitly state in the contract that adapters must never return a `FinalityStatus` unless the finalized block is known, and must throw on failure — but this removes the ability to carry partial state.

**Confidence:** Medium.

---

## Finding 4 — `ChainAdapter.getTokenInfo` contract for unknown/non-allowlisted tokens is unspecified

**Issue:** R14 requires the system to classify unknown contract addresses as `UNKNOWN_TOKEN`. The `ChainAdapter.getTokenInfo` boundary does not specify what it returns when the provider reports an unknown contract. Should the adapter throw, return a sentinel `TokenInfo`, or return provider-provided metadata that downstream `TokenValidator` (task 11) classifies? This ambiguity will hit T06/T07 implementers directly.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java:20`:
  ```java
  TokenInfo getTokenInfo(String contractAddress); // identity by address only (L7)
  ```
- `spec/crypto-service/requirements.md` R14: "IF a transfer's contract address is not on the signed, versioned canonical-token allowlist for its chain, THEN the system SHALL classify it as `UNKNOWN_TOKEN`."
- `spec/crypto-service/design.md` §4a L7: "Token identity is contract address only. ... anything else is `UNKNOWN_TOKEN`."

**Recommendation:** Document `getTokenInfo`'s contract explicitly. For example: "Returns whatever metadata the provider supplies for the contract address; the adapter does not enforce the allowlist. A provider-reported unknown contract is represented by throwing `IllegalStateException` / returning a sentinel with `symbol="UNKNOWN_TOKEN"` (choose one). The signed allowlist check lives in `token.TokenValidator` (task 11)." Add a test enforcing the chosen behavior.

**Confidence:** Medium.

---

## Finding 5 — `ChainAdapter.getFinalityStatus` contract for a nonexistent transaction is unspecified

**Issue:** If `getFinalityStatus` is called for a `txHash` the provider has not observed, should it throw or return a `FinalityStatus` with sentinel values (e.g., `txBlockNumber=0`)? This is symmetric to the `getTx(exists=false)` issue and equally important for quorum and finality policy logic. A thrown exception vs. a sentinel value will be treated differently by downstream code.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java:24`:
  ```java
  FinalityStatus getFinalityStatus(String txHash); // evaluated against the per-chain FinalityPolicy (L4)
  ```
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/FinalityStatus.java` — no documentation on sentinel/null values.

**Recommendation:** Add Javadoc to `getFinalityStatus` stating the expected behavior for an unobserved transaction. For consistency with `getTx`, recommend returning a `FinalityStatus` with `txBlockNumber <= 0` or a nullable `txBlockNumber` (if changed per Finding 3) rather than throwing. Add a unit test documenting this.

**Confidence:** Medium.

---

## Finding 6 — `ObservationSink` has no error/exception channel

**Issue:** `ObservationSink` only exposes `onObservation(TxResult result)`. If a `ChainAdapter` encounters a permanent or transient error while watching an address (e.g., subscription dropped, RPC failure), it has no typed way to notify the watcher layer. The watcher must infer failure from silence or the adapter must throw from a background thread, both of which are poor observability/ordering signals.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ObservationSink.java:13-15`:
  ```java
  public interface ObservationSink {
      void onObservation(TxResult result);
  }
  ```
- `services/crypto/src/main/java/com/themistra/crypto/adapter/Subscription.java:7-9`: only `cancel()`.
- `spec/crypto-service/requirements.md` R5: providers can become "unhealthy, lagging, or repeatedly disagreeing" — the watcher needs to react to adapter health.

**Recommendation:** Either add `void onError(Throwable error)` to `ObservationSink` (preferred) or document that adapter errors are logged internally and the watcher layer detects health via separate polling/heartbeat mechanisms. If the latter, defer to task 16 with an explicit note.

**Confidence:** Medium.

---

## Finding 7 — `Subscription.cancel()` idempotency and thread-safety semantics are unspecified

**Issue:** `Subscription` exposes only `void cancel()`. The contract does not say whether multiple calls to `cancel()` are safe, whether `cancel()` blocks until in-flight observations complete, or whether it is safe to call from a different thread than the one delivering observations. Real adapters (especially websocket-based ones) will need to answer these questions to avoid races or double-cancellation errors.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/Subscription.java:7-9`:
  ```java
  public interface Subscription {
      void cancel();
  }
  ```
- No Javadoc addresses idempotency, blocking, or thread-safety.

**Recommendation:** Document the contract: e.g., "`cancel()` is idempotent; subsequent calls are no-ops. It may be called from any thread. It does not guarantee delivery of observations already in flight at the time of cancellation." Add tests for idempotent cancellation in `FakeChainAdapterTest`.

**Confidence:** Low-Medium.

---

## Finding 8 — `TokenInfo` Javadoc warns against using record equality, which is a design smell

**Issue:** `TokenInfo` is a record, but its Javadoc explicitly tells callers not to rely on `equals()`/`hashCode()` because the generated equality includes `symbol` and `decimals`. This is a workaround for a mismatch between the record type (value equality over all fields) and the domain rule (identity by `contractAddress` only). Any code that ignores the warning and uses `TokenInfo` as a `Map` key or in a `Set` will silently violate L7/R13.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TokenInfo.java:8-15` — lengthy warning about not using equals/hashCode.
- `spec/crypto-service/design.md` §4a L7: "Token identity is contract address only."
- `spec/crypto-service/requirements.md` R13: "identify it by `<chain, contractAddress>` and SHALL NOT rely on a token symbol."

**Recommendation:** Enforce the identity rule in code, not just documentation. Either (a) override `equals`/`hashCode` to use only `contractAddress`, or (b) change `TokenInfo` from a record to a regular class with explicit `contractAddress`-only equality. Add a test that two `TokenInfo` instances with the same `contractAddress` but different `symbol`/`decimals` are equal.

**Confidence:** Medium.

---

## Finding 9 — `FinalityStatus` shape will not generalize to BASE/ARB or Solana

**Issue:** The implementation unifies Ethereum and Tron finality into a single `finalizedBlockNumber: long`. The design.md finality table, however, says BASE/ARB finality requires "L2 confirmed AND batch settled on L1" and Solana requires `"finalized" commitment level`. A single `long` cannot represent these multi-fact finality concepts. While these chains are explicitly out of launch scope, the interface shape may need to change later, creating a breaking change for `FinalityPolicy` consumers.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/FinalityStatus.java:16-20`:
  ```java
  public record FinalityStatus(long txBlockNumber, long currentBlockNumber, long finalizedBlockNumber) {}
  ```
- `spec/crypto-service/design.md` §4c finality policy table includes BASE/ARB and SOLANA with different finality concepts.
- `spec/crypto-service/package.md` §2: "Chains beyond Tron + Ethereum at launch" are out of scope but the adapter interface "must not preclude them."

**Recommendation:** Document that `FinalityStatus` is intentionally launch-scope (Ethereum/Tron) and that future chains may require a new field or a sealed interface hierarchy. Alternatively, design `FinalityStatus` as an interface or sealed class now with `EthereumFinalityStatus`/`TronFinalityStatus` implementations, though this is a larger change. At minimum, add a code comment warning future maintainers not to overload `finalizedBlockNumber` with unrelated concepts.

**Confidence:** Low-Medium.

---

## Summary table

| # | Finding | Severity | Confidence |
|---|---------|----------|------------|
| 1 | `TxResult.exists=false` semantics undocumented | Medium | High |
| 2 | Required T05 tests missing | High | High |
| 3 | `FinalityStatus.finalizedBlockNumber` can't be unknown | Medium | Medium |
| 4 | `getTokenInfo` unknown-token contract unspecified | Medium | Medium |
| 5 | `getFinalityStatus` nonexistent-tx contract unspecified | Low-Medium | Medium |
| 6 | `ObservationSink` has no error channel | Medium | Medium |
| 7 | `Subscription.cancel()` semantics unspecified | Low-Medium | Low-Medium |
| 8 | `TokenInfo` equality warning is a design smell | Medium | Medium |
| 9 | `FinalityStatus` won't generalize to future chains | Low-Medium | Low-Medium |

(End of independent code review.)
