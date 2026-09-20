# crypto · T06 · Phase 7 — Self Review

Reviewed the Phase 6 diff (`adapter/eth/EthereumAdapter.java`, `adapter/eth/EthereumAdapterConfig.java`,
`application.properties`) against the frozen brief and `agents.md`. No rewrites performed — findings
only, fixes are Phase 9.

---

## Finding 1 — `getTx`'s Transfer-log lookup can disagree with what `subscribeAddress` actually detected, for a transaction carrying multiple Transfer events

**Issue:** `pollOnce` (`EthereumAdapter.java:139-161`) queries `eth_getLogs` filtered specifically by
`to == <watched address>` (topic 2), so it only ever surfaces logs relevant to that address. But when
it then calls `getTx(log.getTransactionHash())` to build the full `TxResult`, `getTx`'s own
`findTransferLog` (`:167-171`) re-scans the transaction's **entire** receipt for the **first** log
matching just the Transfer event signature (topic 0) — with no recipient filter at all. For a
transaction that emits more than one `Transfer` event (e.g. a DEX router or aggregator swap that
happens to also move funds through the watched address, not just a simple direct wallet-to-wallet
payment), `getTx` could report the *wrong* Transfer's token/amount/counterparty — a different one than
the specific log `pollOnce` actually matched.

**Severity:** Medium — the platform's primary use case (a direct wallet-to-wallet stablecoin payment)
has exactly one Transfer log per transaction, where this can't happen; the risk is narrower than "any
payment," but still real for any transaction shaped differently, and would silently misattribute a
payment rather than fail loudly.

**Evidence:**
- `EthereumAdapter.java:153-157` (`pollOnce`) — the `EthFilter` is topic-2-restricted to `address`.
- `EthereumAdapter.java:167-171` (`findTransferLog`) — `.findFirst()` with no address/recipient
  awareness at all, called generically by `getTx` regardless of caller context.
- `EthereumAdapter.java:156` — `pollOnce` calls the same, unfiltered `getTx` to build the result it
  hands to `sink.onObservation(...)`.

**Recommendation:** Either (a) have `pollOnce` build the `TxResult` directly from the already-matched
`log` it retrieved (which is *guaranteed* correct — it's the exact log the recipient-filtered query
found) instead of re-deriving it via a second, broader `getTx` call, or (b) give `getTx` (or a
sibling method) a way to disambiguate among multiple Transfer logs in one receipt. Option (a) is
likely simpler and avoids a redundant `eth_getTransactionByHash`/`eth_getTransactionReceipt` round
trip per observation.

---

## Finding 2 — `ethCall`'s response is never checked for a JSON-RPC error before decoding, risking an unhelpful `NullPointerException` for a non-ERC20 or reverting contract

**Issue:** `ethCall` (`EthereumAdapter.java:207-218`) returns `response.getValue()` directly. web3j's
`EthCall`/`Response` does not throw for a JSON-RPC-level error (e.g. `execution reverted`) — the error
is captured in the response object itself, and the value accessor returns `null` in that case. Both
callers (`callErc20StringFunction`, `callErc20Uint8Function`) immediately pass that return value into
`FunctionReturnDecoder.decode(result, ...)` with no null check. A `getTokenInfo` call against a
contract that doesn't implement `symbol()`/`decimals()` (not a valid ERC-20, or a reverting call) would
most likely fail with an opaque `NullPointerException` somewhere inside web3j's own decoder, not a
clear, actionable exception naming the contract/provider.

**Severity:** Medium — doesn't corrupt data (fails loudly, just unhelpfully), but makes a real,
foreseeable failure mode (a non-ERC20 contract address reaching `getTokenInfo`) harder to diagnose
than this codebase's own established "fail loudly with a clear message" convention elsewhere in the
same class (every other `fetch*`/`ethCall` failure path wraps its `IOException` in a message naming
the provider and the call).

**Evidence:**
- `EthereumAdapter.java:207-218` (`ethCall`) — no check on the response before returning `.getValue()`.
- `EthereumAdapter.java:190-196`, `199-205` — both callers decode the raw result with no null guard.
- Contrast with `fetchTransaction`/`fetchReceipt`/`fetchBlockNumber`/`fetchLogs`, all of which wrap
  their specific `IOException` in a named `IllegalStateException`.

**Recommendation:** Check the response for an error (web3j's `Response` base class exposes
`hasError()`/`getError()`) before returning from `ethCall`, and throw a clear `IllegalStateException`
naming the contract address and the underlying RPC error message when one is present.

---

## Finding 3 — `EthereumAdapterConfig` creates a `Web3j` client and a dedicated thread pool per provider with no shutdown path

**Issue:** `buildAdapter` (`EthereumAdapterConfig.java:36-50`) constructs a `Web3j` instance (which
implements `AutoCloseable` and has its own `shutdown()`) and a dedicated
`Executors.newScheduledThreadPool(1, ...)` per configured Ethereum provider entry. Neither is ever
closed/shut down anywhere — the `@Bean` method returns a plain `List<EthereumAdapter>`, which gives
Spring no hook to call a destroy method on each element when the application context closes (Spring's
automatic-destroy-method inference only applies to a bean that is itself `Closeable`/`DisposableBean`,
not to elements inside a returned collection). `EthereumAdapter` itself exposes no shutdown/close
method for anything to call even if there were a hook.

**Severity:** Medium-High — a real resource leak (open HTTP connections, live non-daemon-by-default
scheduled-thread-pool threads) on every application context restart; not immediately catastrophic in
a single long-running pod, but exactly the kind of thing that accumulates across redeploys/rolling
restarts and complicates graceful shutdown.

**Evidence:**
- `EthereumAdapterConfig.java:44` — `Web3j.build(...)`, never `.shutdown()`.
- `EthereumAdapterConfig.java:46-47` — `Executors.newScheduledThreadPool(...)`, never `.shutdown()`/`.shutdownNow()`.
- `EthereumAdapter.java` — no `close()`/`shutdown()` method exists on the class at all, so even a
  future caller with a reference to an individual adapter has no way to release its resources.

**Recommendation:** Give `EthereumAdapter` a `shutdown()` (or implement `AutoCloseable`) that closes
its `Web3j` and shuts down its `scheduler`; have `EthereumAdapterConfig` either return a bean type
Spring can invoke a destroy method on (e.g. a small wrapper implementing `DisposableBean`) or register
an explicit `@Bean(destroyMethod = ...)`-compatible shape instead of a bare `List`.

---

## Not flagged (checked and found correct)

- `pollOnce`'s cursor arithmetic (`fromBlock = lastScannedBlock + 1`, then `lastScannedBlock =
  toBlock` at the end) has no gap or double-count between consecutive polls.
- `Subscription.cancel()`'s `future.cancel(false)` correctly matches T05's own already-documented
  contract ("does not guarantee... already in flight is suppressed... only that no further ones are
  delivered") — an in-flight poll is allowed to finish, only future scheduling is prevented.
- Money handling: `new BigDecimal(BigInteger)` (both the native-value fallback and the decoded
  Transfer-log amount) is exact, base-unit, never floating point, matching agents.md.
- `getTx`'s handling of a mined transaction whose receipt is unexpectedly still `null` (a narrow
  indexing-lag race some nodes can exhibit) degrades gracefully to the native-value fallback path
  rather than throwing or NPEing.
- Reorg-awareness is correctly *not* built into `pollOnce`'s cursor logic — that's `ReorgDetector`'s
  own, separate task (task 18), consistent with this task's own scope boundaries.
- `mvn -pl services/crypto -am compile`/`test-compile` clean.
