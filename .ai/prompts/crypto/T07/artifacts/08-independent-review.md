<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review) for crypto · T07. -->

# crypto · T07 · Phase 8 — Independent Review Findings

**Scope:** Independent adversarial review of the implemented `TronAdapter`/`TronAdapterConfig` and `application.properties` changes, consuming the Phase 6 diff and Phase 7 self-review.

**Directive:** Do not rewrite code. Return findings as **Issue · Evidence · Recommendation · Confidence.**

---

## Issue 1 — Local-profile Tron provider URL is an invalid gRPC target and will likely fail at startup

**Evidence:**
- `application.properties:70` sets the Tron provider URL to `http://localhost:9903/fake-tron-a`.
- `TronAdapterConfig:52-53` passes that value directly to `ApiWrapperBuilder`'s constructor and `withGrpcEndpointSolidity(...)`.
- `ApiWrapper.buildChannel` uses `io.grpc.ManagedChannelBuilder.forTarget(String)`, which expects a bare `host:port` or a URI scheme registered with gRPC's name resolver (e.g., `dns:`). The `http` scheme is not registered by default; gRPC target strings are not HTTP URLs.
- Unlike T06's `Web3j.build(new HttpService(url))`, which is lazy, `ManagedChannelBuilder.forTarget(...)` parses the target string synchronously during `ApiWrapperBuilder.build()`, so the failure occurs at Spring context startup, not on first use.

**Recommendation:**
Change the local Tron fixture URL to a valid gRPC target form (e.g., `localhost:9903`). If the `fake-tron-a` path segment is load-bearing for some local test fixture, replace it with a `dns:`/`localhost:9903` form that gRPC can resolve. Update the frozen brief's expected `application.properties` edit to include this larger fix, not just the poll-interval property.

**Confidence:** High

---

## Issue 2 — `pollOnce` has no exception boundary; a single RPC failure silently and permanently kills the subscription

**Evidence:**
- `TronAdapter:190-216` is the `Runnable` submitted to `scheduler.scheduleWithFixedDelay(...)` (`:155-157`) with no surrounding `try/catch`.
- `ScheduledExecutorService.scheduleWithFixedDelay` documentation states that an uncaught exception from the task causes all future executions to be suppressed silently.
- A transient failure in any inner call — `fetchCurrentBlockNumber`, `fetchTransactionInfoByBlockNum`, or even `topicForAddress` on a malformed address — therefore ends polling for that subscription forever, with no observable signal unless the returned `Future` is inspected.
- T06's `EthereumAdapter.pollOnce` has the same structural exposure, but that does not reduce the severity here.

**Recommendation:**
Wrap the body of `pollOnce` in a `try/catch` that logs the unexpected failure at error level and swallows it, preserving the `ScheduledExecutorService` contract. Decide explicitly (and document in the brief) whether T06 should receive the same fix; leaving one adapter resilient and the other not is an inconsistency that should be intentional.

**Confidence:** High

---

## Issue 3 — `findTransferLog` uses a weaker topic-count guard than `isMatchingTransferLog`, risking an unguarded `IndexOutOfBoundsException`

**Evidence:**
- `TronAdapter:257-262` (`findTransferLog`, used by `getTx`) checks only `getTopicsCount() > 0` before matching `topic[0]`.
- `TronAdapter:218-222` (`isMatchingTransferLog`, used by `subscribeAddress`) correctly requires `getTopicsCount() >= 3` before reading `topic[0]`, `topic[1]`, and `topic[2]`.
- A log whose `topic[0]` collides with the Transfer event signature but has fewer than three topics would pass `findTransferLog`, then throw an unguarded `IndexOutOfBoundsException` inside `buildTxResultFromLog` (`:226-227`) when it reads `getTopics(1)` and `getTopics(2)`.

**Recommendation:**
Align `findTransferLog`'s guard to `getTopicsCount() >= 3`, identical to `isMatchingTransferLog`. The two methods should enforce the same matching rule.

**Confidence:** High

---

## Issue 4 — `fetchContract`'s null guard is likely dead code; unknown contract addresses surface as raw `IllegalException`

**Evidence:**
- `TronAdapter:351-358` checks `if (contract == null)` and throws a named `IllegalStateException`.
- Direct bytecode inspection of `ApiWrapper.getTransactionById` and `getTransactionInfoById` confirmed those methods throw `IllegalException` with a `"... not found: "` prefix for missing entities; `fetchTransaction`/`fetchTransactionInfo` exploit that pattern.
- The same inspection discipline was not applied to `ApiWrapper.getContract`. By analogy with the other single-entity getters, it almost certainly throws `IllegalException` rather than returning `null`, making the `null` branch unreachable.
- If it does throw, the raw `IllegalException` propagates unchecked instead of the adapter's usual `IllegalStateException` pattern, breaking the failure-contract consistency documented in `ChainAdapter`.

**Recommendation:**
Trace `ApiWrapper.getContract` at the bytecode level or test against a known-nonexistent contract address, then align `fetchContract` handling with the confirmed behavior. If it throws `IllegalException`, catch it and rethrow as `IllegalStateException` with provider and address context.

**Confidence:** Medium

---

## Issue 5 — `buildNativeTransferResult` indexes the contract list with no empty-list guard

**Evidence:**
- `TronAdapter:236` calls `tx.getRawData().getContract(0)` without first checking `getContractCount()`.
- The protobuf `repeated` field does not structurally guarantee at least one element. An empty list would produce a bare `IndexOutOfBoundsException` rather than the named, contextual `IllegalStateException` used everywhere else in this class.

**Recommendation:**
Add an explicit guard: if `getContractCount() == 0`, throw `IllegalStateException("Provider ... returned a Transaction with no contracts for " + txHash)`.

**Confidence:** High

---

## Issue 6 — `fetchTransactionInfoByBlockNum` dereferences the response without a null guard

**Evidence:**
- `TronAdapter:343` calls `apiWrapper.getTransactionInfoByBlockNum(blockNum).getTransactionInfoList()` directly.
- If the bulk query returns `null` for any reason (malformed provider response, empty block handled differently by trident, internal error), this line NPEs.
- The self-review assumes empty blocks yield an empty `TransactionInfoList`, but that behavior was not verified at the bytecode level.

**Recommendation:**
Assign the response to a local variable and null-guard it: if null, throw a named `IllegalStateException` with block number context; if non-null, call `getTransactionInfoList()`. Add a Phase 10 test that stubs an empty-block response.

**Confidence:** Medium

---

## Issue 7 — `topicForAddress` lets invalid-address failures propagate into the polling loop, compounding the silent-subscription-death issue

**Evidence:**
- `TronAdapter:280-284` calls `ApiWrapper.parseAddress(base58Address)`, which validates and decodes Base58Check and will throw on an invalid address.
- `topicForAddress` is called from inside `pollOnce`, which has no exception boundary (Issue 2).
- Because address validation is deferred to task 12, a malformed address passed to `subscribeAddress` will crash the first poll tick and silently terminate all future ticks, rather than surfacing as a validation error at registration time or a recoverable poll failure.

**Recommendation:**
Either wrap `topicForAddress` internally and rethrow as a named `IllegalStateException` that the poll boundary can log, or — better — add an explicit poll-level catch that logs and swallows any unexpected failure (Issue 2). Also document that `subscribeAddress` assumes validated Base58Check input until task 12 is in place.

**Confidence:** High

---

## Issue 8 — `ApiWrapperBuilder.withTimeout` unit is unverified; the configured timeout may be wrong by three orders of magnitude

**Evidence:**
- `TronAdapterConfig:54` passes `Duration.ofSeconds(entry.timeoutSeconds()).toMillis()` to `withTimeout(...)`.
- The brief and code do not cite the trident method's documented unit. Many gRPC/Java builders accept milliseconds, but some accept seconds; a mismatch would turn a 5-second timeout into 5,000 seconds or 0.005 seconds.
- This was not verified by bytecode inspection or a test asserting timeout behavior.

**Recommendation:**
Verify `ApiWrapperBuilder.withTimeout` signature and unit via `javap` or trident source. Adjust the conversion and add a test that proves the timeout is applied (e.g., by mocking the builder and capturing the value).

**Confidence:** Medium

---

## Issue 9 — `getTokenInfo` surfaces raw trident failures for non-TRC-20 or unresponsive contracts

**Evidence:**
- `TronAdapter:140-148` constructs `Trc20Contract` and calls `symbol()`/`decimals()` with no try/catch.
- If `contractAddress` is not a TRC-20 contract, if the contract is unresponsive, or if `decimals()` returns a value larger than fits in `int`, the resulting exception (trident-specific or `ArithmeticException` from `intValueExact()`) propagates unchecked without provider context.
- The frozen brief amendment says `getTokenInfo` returns whatever trident reports, but the adapter's failure contract (see `ChainAdapter` Javadoc) expects transport/provider failures to be surfaced as unchecked exceptions with provider context.

**Recommendation:**
Wrap `getTokenInfo` body in a try/catch that converts trident/overflow failures into `IllegalStateException("Provider " + providerName + " failed to read TRC-20 metadata for " + contractAddress, e)`. Add a Phase 10 test for a mocked `decimals()` overflow.

**Confidence:** Medium

---

## Issue 10 — `close()` is not safe to partial failure; `scheduler.shutdown()` is skipped if `apiWrapper.close()` throws

**Evidence:**
- `TronAdapter:185-188` calls `apiWrapper.close()` first, then `scheduler.shutdown()`.
- If `apiWrapper.close()` throws, the scheduler is never shut down, leaving virtual-thread polling tasks alive and potentially leaking resources during context shutdown.

**Recommendation:**
Use a `try/finally` so `scheduler.shutdown()` always runs, or shutdown the scheduler first (so in-flight polls stop) and then close the API wrapper. Mirror whichever pattern is chosen in `EthereumAdapter.close()` for consistency.

**Confidence:** Low-Medium

---

## Summary table

| # | Issue | Severity | Confidence |
|---|-------|----------|------------|
| 1 | Local Tron URL is invalid gRPC target | High | High |
| 2 | `pollOnce` has no exception boundary | High | High |
| 3 | `findTransferLog` weaker guard than `isMatchingTransferLog` | Medium | High |
| 4 | `fetchContract` null guard likely dead code | Medium-High | Medium |
| 5 | `getContract(0)` lacks empty-list guard | Medium | High |
| 6 | `fetchTransactionInfoByBlockNum` response not null-guarded | Medium | Medium |
| 7 | Invalid address in `subscribeAddress` silently kills polling | Medium | High |
| 8 | `withTimeout` unit unverified | Medium | Medium |
| 9 | `getTokenInfo` raw trident failures not normalized | Low-Medium | Medium |
| 10 | `close()` partial-failure leak | Low-Medium | Low-Medium |

(End of independent review.)
