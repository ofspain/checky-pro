<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review) for crypto · T07. -->

# crypto · T07 · Phase 11 — Test Review Findings

**Scope:** Review the Phase 10 test suite (`TronAdapterTest.java`, `TronAdapterConfigTest.java`) against the frozen brief's acceptance criteria and `spec/crypto-service/agents.md` to identify coverage gaps, weak assertions, false positives, flakiness, and missing edge cases.

**Directive:** Do not rewrite production code or tests. Return gaps as **Gap · Why it matters · Suggested test.**

---

## Gap 1 — No direct test that `TronAdapter.chain()` returns `Chain.TRON`

**Why it matters:** AC4 explicitly requires `chain()` to return `Chain.TRON`. The config test indirectly asserts the chain name via reflection (`adapter.chain().name()`), but there is no unit test in `TronAdapterTest` that directly exercises the method and asserts the enum value. A regression that changed the return value would be caught only by the wiring test, not by the adapter's own contract tests.

**Suggested test:** Add `chainReturnsTron` to `TronAdapterTest` that asserts `adapter.chain() == Chain.TRON`.

---

## Gap 2 — Poll interval/delay values passed to the scheduler are not asserted

**Why it matters:** `subscribeAddress` uses `scheduleWithFixedDelay` with `pollInterval.toMillis()` for both initial delay and period. A regression that passed the wrong value, the wrong `TimeUnit`, or used `scheduleAtFixedRate` would not be caught by the existing tests, which only verify that a `Runnable` was scheduled.

**Suggested test:** Enhance the scheduler-capture tests (e.g., `subscribeAddressCursorInitializesToCurrentBlockNotGenesis`) to assert the captured delay, period, and `TimeUnit` equal the configured poll interval.

---

## Gap 3 — Cursor advancement after a normal single-block poll is not explicitly tested

**Why it matters:** The tests verify cursor initialization, the catch-up cap, and that block 151 is not scanned when capped, but no test asserts that `lastScannedBlock` advances from its initial value to the poll's `toBlock` after a routine poll with no catch-up. A regression that forgot to update the cursor would cause duplicate scans on every tick.

**Suggested test:** Add `cursorAdvancesToToBlockAfterPoll` that runs one poll tick from block 100 to 101 with one empty block, then verifies the second tick queries block 102 (not 101 again).

---

## Gap 4 — `subscribeAddress` early-return path when no new blocks exist is not tested

**Why it matters:** When `fromBlock > headBlock`, `pollOnceUnguarded` returns immediately without calling `getTransactionInfoByBlockNum`. A regression that removed this guard would issue a block-number query with a reversed/inverted range.

**Suggested test:** Add `pollSkipsWhenNoNewBlocksExist` that runs the poll task twice with the same current block number and verifies `getTransactionInfoByBlockNum` is called exactly once (during the first tick).

---

## Gap 5 — `confirmations` field in `subscribeAddress` observations is not asserted

**Why it matters:** The adapter computes `confirmations` using `computeConfirmations(headBlock, info.getBlockNumber())` for each observation, but none of the subscription tests assert the value. A regression that always returned 0, 1, or a stale head-based value would not be caught.

**Suggested test:** Add `observationCarriesConfirmationsBasedOnPollHeadBlock` that stubs `currentBlock = 110` and a matching transfer in block 105, runs the poll, and asserts the delivered `TxResult.confirmations()` equals 6.

---

## Gap 6 — `getFinalityStatus` transport failure for block-number RPCs is not tested

**Why it matters:** The tests cover not-found and inconsistency-guard paths, but not the case where `getNowBlock(FULL_NODE)` or `getNowBlock(SOLIDITY_NODE)` throws a transport-level `IllegalException`. A regression that swallowed the exception or produced the wrong exception message would not be caught.

**Suggested test:** Add `getFinalityStatusPropagatesCurrentBlockTransportFailure` and `getFinalityStatusPropagatesSolidityBlockTransportFailure` that stub each block call to throw `IllegalException("deadline exceeded")` and assert an `IllegalStateException` with the provider name and call name.

---

## Gap 7 — `getTx` transport failure on the native-TRX fallback call is not tested

**Why it matters:** `getTx` may call `getTransactionById` after `getTransactionInfoById` succeeds. `getTxPropagatesATransportFailureUnchecked` only tests failure of `getTransactionInfoById`. A regression that mishandled a transport failure on the fallback call would not be caught.

**Suggested test:** Add `getTxPropagatesTransactionByIdTransportFailureUnchecked` that stubs a mined `TransactionInfo`, stubs `getTransactionById` to throw a non-not-found `IllegalException`, and asserts an `IllegalStateException` with provider name and `getTransactionById`.

---

## Gap 8 — `getTokenInfo` success path for a non-TRC-20 contract and `decimals` overflow are not tested

**Why it matters:** The adapter wraps `getTokenInfo` in a try/catch that converts any `RuntimeException` into a contextual `IllegalStateException`. The existing test verifies the catch path with a mocked `RuntimeException` from `getContract`, but it does not exercise the realistic failure modes: a contract that returns a non-TRC-20 response, or a `decimals()` value that overflows `int` (`intValueExact()` throws `ArithmeticException`).

**Suggested test:** Add `getTokenInfoThrowsWhenDecimalsOverflowsInt` that stubs `constantCall` for `decimals()` to return a uint256 value > `Integer.MAX_VALUE` and asserts the resulting `IllegalStateException` carries the contract address. Optionally add a test for a non-TRC-20 ABI response.

---

## Gap 9 — Multiple Transfer logs in a single `TransactionInfo` are not tested

**Why it matters:** `findTransferLog` returns the first matching log. For a transaction containing more than one Transfer event, the chosen log determines the reported token/amount. The existing tests use either zero or one Transfer log.

**Suggested test:** Add `getTxUsesFirstTransferLogWhenMultipleArePresent` that builds a `TransactionInfo` with two Transfer logs and asserts the result matches the first one. Document that this is the deliberate, Ethereum-mirroring behavior for `getTx(txHash)`.

---

## Gap 10 — Invalid Base58Check address in `subscribeAddress` is not tested

**Why it matters:** `topicForAddress` calls `ApiWrapper.parseAddress(...)`, which validates Base58Check and will throw on invalid input. The poll boundary catches this and logs it, but no test proves that an invalid address does not crash the scheduler.

**Suggested test:** Add `pollBoundarySurvivesInvalidBase58Address` that calls `subscribeAddress("not-a-valid-base58-address", ...)` and runs the captured task, asserting no exception propagates and the failure is logged.

---

## Gap 11 — `@PreDestroy` lifecycle wiring is not verified through the Spring context

**Why it matters:** `TronAdapterConfigTest` calls `config.shutdown()` directly and explains why it avoids `ApplicationContextRunner` (eager `ManagedChannel` construction). This leaves a small gap: nothing proves the `@PreDestroy` annotation is actually honored when the Spring context closes. A regression that removed `@PreDestroy` would not be caught.

**Suggested test:** If feasible, add a context test that uses `MockedConstruction` of `ApiWrapperBuilder` *inside* an `ApplicationContextRunner` and asserts schedulers are shut down on context close. If Mockito's `mockConstruction` cannot span the Spring context reliably, document this as an accepted manual-verification item.

---

## Gap 12 — `close()` idempotency is not tested

**Why it matters:** `close()` shuts down the scheduler and closes the `ApiWrapper`. Calling it twice is realistic (manual close plus `@PreDestroy`). If either underlying close is not idempotent, the second call could throw.

**Suggested test:** Add `closeIsIdempotent` that calls `adapter.close()` twice and asserts no exception is thrown and that `scheduler.shutdown()` and `apiWrapper.close()` were each invoked exactly once.

---

## Gap 13 — `getFinalityStatus` when the transaction's block exceeds the current head is not tested

**Why it matters:** `getFinalityStatus` guards against `finalizedBlockNumber > currentBlockNumber` but not against `txBlockNumber > currentBlockNumber`. A provider returning a `TransactionInfo` for a block it does not yet report as current would produce a logically impossible `FinalityStatus`.

**Suggested test:** Add `getFinalityStatusThrowsWhenTxBlockExceedsCurrentBlock` that stubs `txBlock=150`, `currentBlock=140`, and any solidified block ≤ 140, asserting an `IllegalStateException`. If the intended behavior is to return raw values anyway, document the contract explicitly and test that instead.

---

## Gap 14 — No test exercises `info.getId()` being null/empty during a poll

**Why it matters:** `pollOnceUnguarded` builds the observation's `txHash` with `ByteArray.toHexString(info.getId().toByteArray())`. If a provider returns a `TransactionInfo` with no id, this line throws an NPE inside the poll loop. The exception boundary would swallow it, but the observation would be lost silently.

**Suggested test:** Add `pollBoundarySurvivesTransactionInfoWithMissingId` that stubs `getTransactionInfoByBlockNum` to return an info with no id set and asserts the poll task completes without throwing.

---

## Summary table

| # | Gap | Risk | Suggested test approach |
|---|-----|------|-------------------------|
| 1 | `chain()` not directly tested | AC4 regression | Direct enum assertion |
| 2 | Scheduler delay/period unasserted | Wrong scheduling behavior | Capture scheduler arguments |
| 3 | Cursor advancement not explicit | Duplicate scans | Verify next poll queries new block |
| 4 | No-new-blocks early return untested | Invalid inverted query | Two polls with same head block |
| 5 | Observation `confirmations` unasserted | Wrong confirmation semantics | Assert computed confirmations |
| 6 | `getFinalityStatus` block-RPC failures untested | Wrong failure mode | Stub transport failure per block tag |
| 7 | `getTransactionById` transport failure untested | Wrong fallback failure | Stub non-not-found `IllegalException` |
| 8 | `getTokenInfo` realistic failures untested | Unclear overflow/non-TRC20 behavior | Decimals overflow + bad ABI |
| 9 | Multiple Transfer logs untested | Ambiguous `getTx` semantics | Two logs, assert first is used |
| 10 | Invalid Base58 address untested | Silent poll boundary assumption | Subscribe with invalid address |
| 11 | `@PreDestroy` wiring not context-tested | Annotation could be removed | ContextRunner + mockConstruction |
| 12 | `close()` idempotency untested | Double-close failure | Call close twice |
| 13 | `txBlock > currentBlock` untested | Impossible `FinalityStatus` | Stub inconsistent provider |
| 14 | Missing `info.getId()` untested | Silent lost observation | Info without id |

(End of test review.)
