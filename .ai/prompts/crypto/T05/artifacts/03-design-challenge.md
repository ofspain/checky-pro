<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) adversarial review for crypto · T05. -->

# crypto · T05 · Phase 3 — Design Challenge Findings

**Scope:** Review `artifacts/02-task-implementation-brief.md` (TIB) against `spec/crypto-service/agents.md`, `spec/crypto-service/design.md` §4a/§4c/§6, `spec/crypto-service/requirements.md` R1–R5/R8–R12/R13–R14, and `spec/crypto-service/package.md` §2.

**Directive:** Do not redesign or implement. Surface hidden assumptions, ambiguities, untestable rules, missing edge cases, conflicts with locked decisions or `agents.md`, unstated dependencies, ordering hazards, and contract mismatches. For each finding: **Issue · Severity · Evidence · Recommended brief amendment.**

---

## Finding 1 — `TokenInfo` auto-generated `equals`/`hashCode` conflicts with AC4's "symbol never used for identity/equality"

**Issue:** The TIB proposes `TokenInfo` as a Java record with fields `contractAddress`, `symbol`, `decimals`. Java records generate `equals`/`hashCode`/`toString` over **all** record components. AC4 states: "`TokenInfo` never uses `symbol` for identity/equality; only `contractAddress` (combined with the owning adapter's `chain()`) does." This is a direct conflict: two `TokenInfo` objects with the same `contractAddress` but different `symbol` values will **not** be equal, so `symbol` is effectively part of equality.

**Severity:** Medium — the implementation would satisfy the letter of the model shape but violate the stated identity rule, risking bugs in any code that puts `TokenInfo` in a `Set`/`Map` or compares with `.equals()`.

**Evidence:**
- TIB §Files to Create / `model/TokenInfo.java`:
  ```java
  public record TokenInfo(String contractAddress, String symbol, int decimals) {}
  ```
- TIB §Acceptance Criteria AC4: "`TokenInfo` never uses `symbol` for identity/equality; only `contractAddress` ... does."
- `spec/crypto-service/design.md` §4a L7: "Token identity is contract address only."
- `spec/crypto-service/requirements.md` R13: "identify it by `<chain, contractAddress>` and SHALL NOT rely on a token symbol."

**Recommended brief amendment:**
- Either override `equals`/`hashCode` in `TokenInfo` to use only `contractAddress` (and document that `symbol`/`decimals` are ignored for equality), or
- Change AC4 to say "business logic never uses `symbol` for identity" and do not assert equality semantics, accepting the record's default behavior.
- Add a test that two `TokenInfo` instances with the same `contractAddress` but different `symbol`/`decimals` are equal (if option 1 is chosen).

---

## Finding 2 — `FakeChainAdapter` reorg API does not specify how the fake maps `txHash` to a subscribed address

**Issue:** The TIB says reorg support works via a "re-script method that also pushes the new state to any live subscription." It states: "if a subscription is active for the relevant address, [the method] invokes the stored sink with the new state." But it does not say how the fake determines which address is "relevant" for a given `txHash`. A txHash does not inherently belong to an address; the fake must be told the association at script time or the reorg method must take an address parameter.

**Severity:** Medium — the scripting API cannot be implemented without making this design decision, and different choices affect test ergonomics and the ability to simulate realistic reorg scenarios.

**Evidence:**
- TIB §Scope / In / `FakeChainAdapter`: "reorg needs explicit support (a temporal change), via a re-script method that also pushes the new state to any live subscription."
- TIB §Constraints / `FakeChainAdapter`'s scripting API: "a `simulateReorg(String txHash, TxResult newResult)`-style method re-scripts the tx and, if a subscription is active for a relevant address, invokes the stored sink with the new state."
- No field or method in the proposed `TxResult` carries the address(es) involved in the transaction, so the fake cannot derive the address from `newResult` alone.

**Recommended brief amendment:**
- Choose and document one of:
  1. `simulateReorg(String txHash, String address, TxResult newResult)` — caller provides the address whose subscription should be notified; or
  2. `scriptTx(String txHash, String address, TxResult result)` — the fake builds an internal `txHash → address` index at script time, and `simulateReorg(txHash, newResult)` looks it up.
- Add the chosen shape to the scripting API sketch and require a test verifying the subscription-push behavior for a reorged tx.

---

## Finding 3 — `Chain` enum is deliberately not retrofitted into T03's config, creating a representation drift

**Issue:** T03's `ProviderProperties` and `FinalityProperties` use regex-constrained `String` values (`"ETHEREUM"|"TRON"`) for chain identifiers. T05 introduces a `Chain` enum with the same two values. The TIB resolves Phase 1's open question by **not** retrofitting the enum into T03 config. This is a scoping decision, but it leaves two parallel representations of the same concept. Every future task that reads config (string) and uses adapters (enum) must perform a conversion, and a new chain added later must be updated in both places.

**Severity:** Low-Medium — not a bug in T05, but a maintainability hazard and hidden assumption that downstream tasks must bridge the two representations.

**Evidence:**
- TIB §Scope / Out: "Retrofitting `Chain` into T03's `ProviderProperties.ChainProviders.chain`/`FinalityProperties.enabledChains` ... **decision ... do not retrofit.**"
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java`: `@Pattern(regexp = "ETHEREUM|TRON") String chain`.
- `services/crypto/src/main/java/com/themistra/crypto/common/config/FinalityProperties.java`: `List<@NotBlank @Pattern(regexp = "ETHEREUM|TRON") String> enabledChains`.
- `spec/crypto-service/design.md` §6 file map: `Chain.java` enum under `adapter/`.

**Recommended brief amendment:**
- Either (a) add a helper/converter (e.g., `Chain.fromConfig(String)`) to Files-to-Create and document that downstream tasks must use it, or
- (b) revisit the "do not retrofit" decision and update T03 config to use `Chain` directly if the human approver accepts the scope change.
- If (a), add an AC/test that every string value allowed by the T03 regex maps to exactly one `Chain` enum constant and vice versa.

---

## Finding 4 — `TxResult.amount` is `BigDecimal` but no JSON-serialization guard prevents numeric encoding

**Issue:** `agents.md` states money/base-unit values "On the wire ... are decimal strings, never JSON numbers." `TxResult.amount` is `BigDecimal`. Jackson's default serialization of `BigDecimal` emits a JSON number. Although `TxResult` itself is not the final event payload (task 17 defines payload classes), it may still be serialized in tests, logs, or the observation log's raw response if a provider returns a structured representation. The TIB does not require any `@JsonSerialize(using = ToStringSerializer.class)` or similar guard on the `amount` field.

**Severity:** Low-Medium — depends on whether `TxResult` ever crosses a JSON boundary. If it does, this violates a standing rule; if it does not, it is only a latent risk.

**Evidence:**
- `agents.md`: "Money / base-unit values are `NUMERIC` / `BigDecimal` — never floating point. On the wire they are decimal strings, never JSON numbers."
- TIB §Files to Create / `model/TxResult.java`: `BigDecimal amount`.
- TIB §Constraints: "Money (agents.md): `TxResult.amount` is `BigDecimal`, base units, never floating point." — mentions storage type but not wire format.

**Recommended brief amendment:**
- Add `@JsonSerialize(using = ToStringSerializer.class)` to the `amount` field (or a custom serializer) and a test asserting that serializing a `TxResult` produces `"amount":"123.456"` (string), not `"amount":123.456` (number).
- If `TxResult` is intentionally never serialized to JSON, document that assumption explicitly in the class Javadoc so future authors do not accidentally violate it.

---

## Finding 5 — `ObservationSink` is tx-only but its name implies a general observation channel

**Issue:** The TIB defines `ObservationSink` as `void onObservation(TxResult result)`. The `ChainAdapter` interface also has `getTokenInfo` and `getFinalityStatus`, whose responses are observations in the broad sense, but they are not pushed through `ObservationSink`. The name `ObservationSink` suggests it handles all observation types, which could confuse implementers of later tasks.

**Severity:** Low — mostly a naming/clarity issue; the design.md file map uses the same name, so changing it may require a spec update.

**Evidence:**
- TIB §Files to Create / `ObservationSink.java`: `public interface ObservationSink { void onObservation(TxResult result); }`
- `spec/crypto-service/design.md` §4c ChainAdapter interface: `Subscription subscribeAddress(String address, ObservationSink sink); // watcher layer`
- `spec/crypto-service/requirements.md` R4: observation log fact types include `existence | amount | token | confirmations | finality` — `token` and `finality` come from `getTokenInfo`/`getFinalityStatus`, not from the sink.

**Recommended brief amendment:**
- Document in the Javadoc that `ObservationSink` is specifically for address-watch transaction observations pushed by `subscribeAddress`, not for token/finality lookups.
- Optionally rename to `TxObservationSink` in the TIB and file a matching spec update if the human approver wants the clarity. If renaming is rejected, the Javadoc clarification is still worthwhile.

---

## Finding 6 — Exception type for unscripted `FakeChainAdapter` queries is not specified

**Issue:** AC6 requires that an unscripted query "throws, matching this codebase's established fail-loudly convention." The TIB does not specify which exception type (e.g., `IllegalStateException`, `NoSuchElementException`, `UnsupportedOperationException`). Different choices affect how callers/tests handle the failure and whether the message convention is consistent with `EventTopics.forAggregateType`.

**Severity:** Low — any runtime exception satisfies the test, but consistency matters for readability and maintainability.

**Evidence:**
- TIB §Acceptance Criteria AC6: "No unscripted call to `FakeChainAdapter.getTx`/`getTokenInfo`/`getFinalityStatus` returns `null` or a default/zero-value silently — it throws, matching this codebase's established fail-loudly convention (`EventTopics.forAggregateType`, T04)."
- `EventTopics.forAggregateType` throws `IllegalStateException`.
- TIB §Constraints: "`FakeChainAdapter` throws (not `null`) on an unscripted query (AC6)."

**Recommended brief amendment:**
- Specify the exception type, e.g., `IllegalStateException` with a message naming the unscripted key (`txHash`, `contractAddress`, etc.), mirroring `EventTopics`.
- Add a test asserting both the exception type and the message content.

---

## Finding 7 — `subscribeAddress` semantics for already-known transactions are unspecified

**Issue:** The TIB says `subscribeAddress` stores the sink keyed by address and returns a cancellable `Subscription`, but it does not specify whether the adapter should immediately push any already-known transactions for that address or only new ones observed after subscription. This matters for the watcher layer (task 16) and for tests that need deterministic behavior.

**Severity:** Low-Medium — a hidden assumption that affects watcher correctness and test stability.

**Evidence:**
- TIB §Files to Create / `ChainAdapter.java`: `Subscription subscribeAddress(String address, ObservationSink sink); // watcher layer`
- TIB §Files to Create / `Subscription.java`: `void cancel();`
- TIB §Files to Create / `ObservationSink.java`: `void onObservation(TxResult result);`
- No AC or constraint defines the behavior at subscription time.

**Recommended brief amendment:**
- State the expected behavior explicitly, e.g.: "`subscribeAddress` does **not** replay historical transactions; it only pushes transactions observed after subscription. Callers that need historical state must call `getTx` separately." (Or choose the opposite if the task author prefers replay.)
- Add a test that subscribes an address already scripted with a tx and asserts whether/no observation is pushed immediately.

---

## Finding 8 — `FinalityStatus` does not capture the Tron "solidified block" concept explicitly

**Issue:** Tron finality policy is "block is solidified (~19 confirmations toward the solidified block)". The proposed `FinalityStatus` exposes `txBlockNumber` and `currentBlockNumber`, from which a policy can compute `currentBlockNumber - txBlockNumber >= 19`. However, it does not expose the solidified block number itself, which is a first-class concept in Tron and would make the policy clearer and less dependent on a hardcoded constant.

**Severity:** Low — the current shape is sufficient to implement the policy, but it buries the chain-specific concept in a difference calculation.

**Evidence:**
- `spec/crypto-service/design.md` §4c finality policy table: "TRON : block is solidified (~19 confirmations toward the solidified block)."
- TIB §Files to Create / `model/FinalityStatus.java`: `public record FinalityStatus(long txBlockNumber, long currentBlockNumber, Long finalizedCheckpointBlockNumber /* nullable - Ethereum beacon-finalized only */) {}`

**Recommended brief amendment:**
- Either rename `finalizedCheckpointBlockNumber` to a more generic `finalizedBlockNumber` and document that for Tron it represents the solidified block, or add a separate `solidifiedBlockNumber` field that is populated for Tron and null for Ethereum.
- If no change is made, document in the Javadoc how Tron finality is derived from `currentBlockNumber - txBlockNumber` and where the ~19-confirmation threshold lives (task 14).

---

## Finding 9 — Structural test for `ChainAdapter` may be too brittle if method-level documentation or parameter names differ

**Issue:** The TIB requires a reflection/structural test asserting `ChainAdapter`'s method set matches design §4c "exactly (name/parameter/return-type triples)." This is a strong verbatim guard, but method parameter **names** are not part of the compiled bytecode by default (unless compiled with `-parameters`), and return types for generic parameters could be ambiguous. A test based on raw reflection might fail for harmless differences (e.g., parameter name changes, additional `@Nullable` annotations) or miss equivalence (e.g., `java.util.List` vs a custom list).

**Severity:** Low — the intent is good, but the implementation details need care to avoid a flaky or over-constrained test.

**Evidence:**
- TIB §Required Tests: "A lightweight reflection or structural test asserting `ChainAdapter`'s method set matches design §4c exactly (name/parameter/return-type triples)."
- Java reflection does not preserve parameter names unless the class is compiled with `-parameters`.

**Recommended brief amendment:**
- Refine the structural test to assert method names, return types, and parameter **types** only (not parameter names), and ignore annotations/order of declared methods unless order is meaningful.
- Verify that the project compiles with `-parameters` before requiring parameter-name assertions; otherwise, drop parameter names from the test.

---

## Summary of requested brief amendments

| # | Amendment | Priority |
|---|-----------|----------|
| 1 | Resolve `TokenInfo` equals/hashCode vs. symbol-ignored identity rule. | Medium |
| 2 | Specify how `FakeChainAdapter` maps `txHash` to address for reorg subscription push. | Medium |
| 3 | Document or bridge the `String`-config / `Chain`-enum representation drift. | Low-Medium |
| 4 | Add JSON-serialization guard for `TxResult.amount` or document that it is never JSON-serialized. | Low-Medium |
| 5 | Clarify `ObservationSink` is tx-only in Javadoc (or rename). | Low |
| 6 | Specify exception type for unscripted `FakeChainAdapter` queries. | Low |
| 7 | Define `subscribeAddress` replay-vs-new-only semantics. | Low-Medium |
| 8 | Clarify Tron solidified-block representation in `FinalityStatus`. | Low |
| 9 | Refine structural test to avoid parameter-name brittleness. | Low |

(End of design challenge review.)
