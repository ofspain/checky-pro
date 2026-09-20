<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review) for crypto · T06. -->

# crypto · T06 · Phase 11 — Test Review Findings

**Scope:** Review the Phase 10 test suite (`EthereumAdapterTest.java`, `EthereumAdapterConfigTest.java`) against the frozen brief's acceptance criteria and `spec/crypto-service/agents.md` to identify coverage gaps, weak assertions, false positives, flakiness, and missing edge cases.

**Directive:** Do not rewrite production code or tests. Return gaps as **Gap · Why it matters · Suggested test.**

---

## Gap 1 — `@PreDestroy shutdown()` wiring is not tested

**Why it matters:** `EthereumAdapterConfig` tracks created adapters in a `CopyOnWriteArrayList` and closes them via `@PreDestroy`. The existing `closeShutsDownWeb3jAndScheduler` test only verifies `EthereumAdapter.close()` directly; it does not prove that the Spring context actually invokes `shutdown()` on context close, or that all adapters in a multi-provider setup are closed.

**Suggested test:** Add a test in `EthereumAdapterConfigTest` that runs the context with two ETHEREUM providers, then explicitly closes the context and verifies `web3j.shutdown()` and `scheduler.shutdown()` were called for each adapter. This can be done by wrapping/adapting the adapters with spies or by verifying the context's lifecycle.

---

## Gap 2 — `subscribeAddress` cursor advancement after a poll is not tested

**Why it matters:** `pollOnce` updates `lastScannedBlock` to the latest block after processing logs. A regression that forgot to advance the cursor would cause every subsequent poll to re-fetch the same block range, producing duplicate observations and redundant RPC calls.

**Suggested test:** Add a test that runs the scheduled poll task twice with different `LATEST` block numbers, asserting that the second `eth_getLogs` call uses a `fromBlock` equal to `firstPollToBlock + 1`. Mock `fetchLatestBlockNumber` to return 100, then 105, and capture both `EthFilter` arguments.

---

## Gap 3 — `subscribeAddress` early-return path when no new blocks exist is not tested

**Why it matters:** When `fromBlock > toBlock`, `pollOnce` returns early without calling `eth_getLogs`. A regression that removed this guard would issue an invalid `eth_getLogs` request with a reversed block range.

**Suggested test:** Add a test where the scheduled poll task runs twice with the same `LATEST` block number. Assert that `eth_getLogs` is called exactly once (only during the first poll) and that no exception is thrown.

---

## Gap 4 — `getTx` with a mined transaction but a `null` receipt is not tested

**Why it matters:** Some nodes exhibit an indexing-lag race where `eth_getTransactionByHash` returns a mined transaction but `eth_getTransactionReceipt` still returns `null`. The production code falls back to reporting native value with `tokenContractAddress=null`, but this path is not exercised by any test.

**Suggested test:** Add `getTxFallsBackToNativeValueWhenReceiptIsNullButTransactionIsMined` that stubs a mined transaction, stubs `fetchReceipt` to return `null`, and asserts the result has `exists=true`, `tokenContractAddress=null`, and the native `amount`.

---

## Gap 5 — `getTx` failure of `eth_getTransactionReceipt` is not tested

**Why it matters:** The suite tests `eth_getTransactionByHash` failure, but `getTx` also calls `eth_getTransactionReceipt`. A regression that swallowed an `IOException` from the receipt fetch (or produced the wrong exception message) would not be caught.

**Suggested test:** Add `getTxPropagatesReceiptIoExceptionUnchecked` that stubs a mined transaction and makes `ethGetTransactionReceipt` throw `IOException`, asserting an `IllegalStateException` with `eth_getTransactionReceipt` in the message.

---

## Gap 6 — Poll interval/delay values passed to the scheduler are not asserted

**Why it matters:** `subscribeAddress` uses `scheduleWithFixedDelay` with `pollInterval.toMillis()` for both initial delay and period. A regression that passed zero, the wrong unit, or used `scheduleAtFixedRate` would change operational behavior without failing the existing cancellation test.

**Suggested test:** Enhance `cancellingTheSubscriptionStopsFurtherPolling` (or add a dedicated test) that captures the delay, period, and `TimeUnit` arguments to `scheduleWithFixedDelay` and asserts they match the configured poll interval.

---

## Gap 7 — Multiple observations in a single poll are not tested

**Why it matters:** `pollOnce` iterates over all returned logs and emits one observation per log. The existing test uses a single log. A bug that broke iteration (e.g., returning after the first log) would only be caught with multiple logs.

**Suggested test:** Add `pollEmitsOneObservationPerMatchedLog` that stubs `eth_getLogs` to return two distinct Transfer logs for the watched address, runs the poll task, and asserts the sink receives two observations with the correct respective token/amount pairs.

---

## Gap 8 — `close()` idempotency is not tested

**Why it matters:** `EthereumAdapter.close()` calls `web3j.shutdown()` and `scheduler.shutdown()`. While these are generally idempotent, calling `close()` twice is a realistic scenario (e.g., manual close plus `@PreDestroy`). If either underlying call throws on double-close, the second close would fail.

**Suggested test:** Add `closeIsIdempotent` that calls `adapter.close()` twice and asserts no exception is thrown and that `web3j.shutdown()`/`scheduler.shutdown()` were each invoked exactly once.

---

## Gap 9 — API-key URL substitution does not test special-character encoding

**Why it matters:** `resolveUrl` does a literal `String.replace("{apiKey}", apiKey)`. If an API key contains characters that must be URL-encoded (e.g., `+`, `/`, `&`, `=`), the resulting URL may be invalid or semantically incorrect. This is a latent deployment bug.

**Suggested test:** Add `substitutesUrlEncodedCredentialWhenKeyContainsSpecialCharacters` that uses an API key like `a+b/c&d=e` and asserts the substituted URL is properly encoded (e.g., `a%2Bb%2Fc%26d%3De`). If the current implementation does not encode, this test documents the limitation and drives a fix.

---

## Gap 10 — `getFinalityStatus` inconsistent block-tag responses are not tested

**Why it matters:** `getFinalityStatus` fetches `LATEST` and `FINALIZED` blocks independently. If a provider returns a `FINALIZED` block number greater than `LATEST` (a transient inconsistency or provider bug), the `FinalityStatus` is nonsensical. The production code does not guard against this.

**Suggested test:** Add `getFinalityStatusThrowsWhenFinalizedBlockExceedsLatestBlock` (or document acceptance) that stubs `FINALIZED=150` and `LATEST=140` and asserts either a specific exception or the raw values, depending on the intended contract.

---

## Gap 11 — No test verifies observations are delivered from a different thread

**Why it matters:** The production code documents that `sink.onObservation(...)` may be invoked from a non-caller thread. Tests currently run the poll task synchronously by invoking the captured `Runnable` directly from the test thread. This does not exercise the actual async boundary.

**Suggested test:** Add a test that runs the poll task on a real (virtual) thread pool and asserts the observation is delivered, or at minimum assert that the `Runnable` submitted to the scheduler is not executed synchronously inside `subscribeAddress`.

---

## Gap 12 — `EthereumAdapterConfig` does not test the no-ETHEREUM-chain case

**Why it matters:** If `ProviderProperties` contains only TRON providers, `ethereumAdapters` should return an empty list without error. A regression in the filter predicate could throw or return unexpected adapters.

**Suggested test:** Add `buildsNoAdaptersWhenNoEthereumChainIsConfigured` that configures only a TRON provider and asserts the returned list is empty and the context boots successfully.

---

## Summary table

| # | Gap | Risk | Suggested test approach |
|---|-----|------|-------------------------|
| 1 | `@PreDestroy` shutdown wiring untested | Resource leak on context close | Close context, verify all adapters closed |
| 2 | Cursor advancement after poll untested | Duplicate observations / redundant RPC | Two polls with advancing block numbers |
| 3 | No-new-blocks early return untested | Invalid reversed-range RPC | Two polls with same block number |
| 4 | Mined tx + null receipt path untested | Native fallback regresses | Stub mined tx, null receipt |
| 5 | Receipt `IOException` path untested | Wrong exception on receipt failure | Stub receipt IOException |
| 6 | Poll interval/delay values untested | Wrong scheduling behavior | Capture scheduler arguments |
| 7 | Multiple observations per poll untested | Only first log emitted | Two logs in one poll |
| 8 | `close()` idempotency untested | Double-close failure | Call close twice |
| 9 | API-key URL encoding untested | Invalid URL with special chars | Special-character API key |
| 10 | Inconsistent finalized/latest untested | Nonsensical `FinalityStatus` | Finalized > latest |
| 11 | Async observation delivery untested | Threading assumptions unchecked | Real thread pool or async assertion |
| 12 | No-ETHEREUM-chain case untested | Unexpected error/return | Only TRON configured |

(End of test review.)
