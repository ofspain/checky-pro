<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review) for crypto · T05. -->

# crypto · T05 · Phase 11 — Test Review Findings

**Scope:** Review the Phase 10 test suite (`FakeChainAdapterTest.java`, `ChainAdapterShapeTest.java`, `FakeChainAdapter.java`) against the frozen brief's acceptance criteria and `spec/crypto-service/design.md` to identify coverage gaps, weak assertions, false positives, flakiness, and missing edge cases.

**Directive:** Do not rewrite production code or tests. Return gaps as **Gap · Why it matters · Suggested test.**

---

## Gap 1 — `exists=false` is not tested as the normal, non-throwing return path

**Why it matters:** `R1` treats "tx existence" as a quorum-checked fact, so `getTx` returning `TxResult(exists=false, ...)` must be a normal, expected result for a provider that hasn't observed the transaction. The existing `lagIsARelativelyBehindOrUnobservedResult` test creates an `exists=false` result and asserts `.exists()` is false, but it does not explicitly assert that `getTx` returned it without throwing, nor does it document the convention for the other fields when `exists=false`. This leaves the ambiguity flagged in Phase 7/8 self-review and independent review unresolved at the test level.

**Suggested test:** Add `getTxReturnsExistsFalseForUnobservedTransaction` that scripts `tx(false, 0, 0L)`, calls `getTx`, and asserts the returned `TxResult.exists()` is false and no exception is thrown. Optionally add a test or Javadoc assertion that when `exists=false`, `confirmations`/`blockNumber`/`amount`/`addresses` carry no meaningful data.

---

## Gap 2 — AC2 (`Chain` enum has exactly `ETHEREUM` and `TRON`) has no automated test

**Why it matters:** The Phase 10 manifest dismisses AC2 as "trivially true by inspection," but a regression that added a third constant or renamed one would not be caught by any existing test. Other tasks in this repository (e.g., `EventTopicsTest`, `PublicEndpointsTest`) test similarly "obvious" constants.

**Suggested test:** Add `ChainTest` (or extend `ChainAdapterShapeTest`) asserting `Chain.values()` contains exactly `Chain.ETHEREUM` and `Chain.TRON` and that `Chain.valueOf("ETHEREUM")` and `Chain.valueOf("TRON")` succeed.

---

## Gap 3 — AC4 (`TokenInfo` identity is `contractAddress` only) is not enforced by code or test

**Why it matters:** `TokenInfo` is a record, so its generated `equals`/`hashCode` include `symbol` and `decimals`. The production Javadoc warns callers not to rely on equality, but there is no test proving the identity rule. A future author could introduce a `Map<TokenInfo, ...>` or a `Set<TokenInfo>` and silently violate L7/R13.

**Suggested test:** Add a test asserting that two `TokenInfo` instances with the same `contractAddress` but different `symbol`/`decimals` are treated as the same token by business logic. If the decision is to keep record equality as-is, add a test that documents the warning by asserting they are *not* equal and that callers must compare by `contractAddress()`.

---

## Gap 4 — AC5 (`FinalityStatus` has no precomputed "is final" field) has no automated test

**Why it matters:** AC5 states `FinalityStatus` must carry only raw block/checkpoint data, with no boolean "is final" field and no hardcoded confirmation threshold. The current tests do not inspect the record's shape. A future change could add `boolean finalized` or `int requiredConfirmations` without failing any test.

**Suggested test:** Add a reflection-based test (similar to `ChainAdapterShapeTest`) asserting `FinalityStatus.class.getRecordComponents()` contains only `txBlockNumber`, `currentBlockNumber`, and `finalizedBlockNumber`, all of type `long`, and no field whose name or type suggests a precomputed finality decision.

---

## Gap 5 — Reorg subscription matching is only tested for `fromAddress`, not `toAddress`

**Why it matters:** `FakeChainAdapter.pushToMatchingSubscriptions` matches both `fromAddress` and `toAddress`, but the positive reorg test only subscribes to `FROM`. A bug that accidentally matched only `fromAddress` (or only `toAddress`) would not be caught.

**Suggested test:** Add `reorgPushesToASubscriptionOnTheToAddress` that subscribes to `TO`, simulates a reorg, and asserts the sink receives the new result.

---

## Gap 6 — Multiple subscriptions on the same address are not tested

**Why it matters:** `pushToMatchingSubscriptions` iterates over all active subscriptions. A regression that stopped after the first match (e.g., an accidental `break` or `return`) would only be caught if multiple subscribers exist.

**Suggested test:** Add `reorgPushesToAllMatchingSubscriptions` that registers two separate sinks for the same `FROM` address, simulates a reorg, and asserts both sinks receive the new result.

---

## Gap 7 — Reorg that removes a transaction (`exists=false`) is not tested

**Why it matters:** A reorg can invalidate a previously observed transaction, meaning the new state is "this tx no longer exists." `simulateReorg` accepts any `TxResult`, including `exists=false`, but no test exercises this case. The watcher/reorg logic (task 16) will need to handle this signal.

**Suggested test:** Add `reorgCanPushAnExistsFalseResult` that scripts an `exists=true` tx, subscribes to `FROM`, simulates a reorg with `tx(false, 0, 0L)`, and asserts the sink receives the `exists=false` result.

---

## Gap 8 — `scriptTx` is not proven to avoid pushing to active subscriptions

**Why it matters:** The production Javadoc states that `scriptTx` "only set what the next query returns — they never push to an active subscription," while `simulateReorg` is the only method that pushes. The existing `subscribeDoesNotReplayAnAlreadyScriptedTransaction` test proves no replay at subscription time, but does not prove that calling `scriptTx` after a subscription is active does not push.

**Suggested test:** Add `scriptTxAfterSubscriptionDoesNotPush` that subscribes to `FROM`, then calls `scriptTx` for the same txHash, and asserts the sink receives nothing.

---

## Gap 9 — `Subscription.cancel()` idempotency is not tested

**Why it matters:** `Subscription.cancel()` is implemented as `activeSubscriptions.remove(subscription)`. Calling it twice is currently safe (no-op), but this behavior is undocumented and untested. A future change could throw on double-cancel or have side effects.

**Suggested test:** Add `cancelIsIdempotent` that subscribes, cancels, cancels again, simulates a reorg, and asserts the sink receives nothing.

---

## Gap 10 — `FakeChainAdapter.chain()` return value is not tested

**Why it matters:** `FakeChainAdapter` is constructed with a `Chain`, and `chain()` is part of the `ChainAdapter` contract. The tests use `Chain.ETHEREUM` in every case. A regression where `chain()` always returned `Chain.ETHEREUM` regardless of constructor argument would not be caught.

**Suggested test:** Add `chainReturnsConstructorValue` parameterized over `Chain.ETHEREUM` and `Chain.TRON`, or include an assertion in one existing test that `adapter.chain()` equals the value passed to the constructor.

---

## Gap 11 — No test bridges `Chain` enum to the T03 config-string values

**Why it matters:** `Chain.java` documents that `valueOf(String)` is the bridge from T03's regex-constrained config strings. If a future chain is added to the enum but the T03 regex is not updated (or vice versa), the conversion will fail at runtime in higher environments. A test can lock the two representations together.

**Suggested test:** Add `chainEnumMatchesConfigRegex` that asserts every `Chain` value's name matches the regex `ETHEREUM|TRON` used in `ProviderProperties`/`FinalityProperties`, and that `Chain.valueOf(name)` succeeds for both.

---

## Gap 12 — `ChainAdapterShapeTest` does not verify method modifiers or absence of extra supertypes

**Why it matters:** The shape test verifies method name/return/parameter types, but does not assert that the methods are `public abstract`, that `ChainAdapter` is an `interface`, or that it has no superinterfaces that introduce extra methods. A refactor turning it into an abstract class or adding a superinterface would not fail the current test.

**Suggested test:** Extend `ChainAdapterShapeTest` to assert `ChainAdapter.class.isInterface()` is true and that each declared method has `Modifier.isPublic()` and `Modifier.isAbstract()`. Also assert `ChainAdapter.getInterfaces()` is empty (or explicitly list allowed supertypes).

---

## Summary table

| # | Gap | Risk | Suggested test approach |
|---|-----|------|-------------------------|
| 1 | `exists=false` normal path not explicitly tested | Ambiguity persists for T06/T07 | Assert `getTx` returns `exists=false` without throwing |
| 2 | `Chain` enum values untested | Regression in enum constants | Assert exactly ETHEREUM/TRON |
| 3 | `TokenInfo` identity rule untested | Silent equality misuse | Test same-contractAddress/different-symbol equality behavior |
| 4 | `FinalityStatus` shape untested | Precomputed finality field could be added | Reflection test on record components |
| 5 | Reorg matching only tests `fromAddress` | Bug in `toAddress` matching unseen | Subscribe to `TO`, simulate reorg |
| 6 | Multiple subscriptions untested | Only first match might be pushed | Two sinks on same address |
| 7 | Reorg with `exists=false` untested | Removed-tx signal untested | Reorg to `tx(false, ...)` |
| 8 | `scriptTx` no-push behavior untested | Could accidentally push on script | `scriptTx` after subscription |
| 9 | `cancel()` idempotency untested | Double-cancel behavior fragile | Cancel twice, assert no observations |
| 10 | `chain()` return value untested | Constructor arg ignored regression | Assert `adapter.chain()` equals input |
| 11 | Enum/config-string bridge untested | Drift between enum and T03 regex | Test `valueOf` against regex |
| 12 | Shape test ignores modifiers/superinterfaces | Interface contract could regress | Assert interface + public abstract methods |

(End of test review.)
