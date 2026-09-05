<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review) for crypto · T06. -->

# crypto · T06 · Phase 8 — Independent Code Review Findings

**Scope:** Review the Phase 6 implementation (`adapter/eth/EthereumAdapter.java`, `adapter/eth/EthereumAdapterConfig.java`, `application.properties`) and the Phase 7 self-review with fresh, adversarial eyes.

**Directive:** Do not rewrite. Report findings as **Issue · Evidence · Recommendation · Confidence**.

---

## Finding 1 — `subscribeAddress` can push a `TxResult` derived from the wrong Transfer log in multi-Transfer transactions

**Issue:** `pollOnce` filters `eth_getLogs` by the recipient topic (`to == watchedAddress`), so the matched log is guaranteed to involve the watched address. However, it then calls `getTx(log.getTransactionHash())`, and `getTx` re-scans the entire transaction receipt for the **first** log matching the Transfer event signature, with no recipient filter. A transaction with multiple Transfer events (e.g., a DEX router, aggregator, or batched transfer) can therefore report a different Transfer's token/amount/counterparty than the one the poll actually detected.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:153-157` — `EthFilter` restricts topic 2 to the watched address.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:156` — `pollOnce` calls `getTx(log.getTransactionHash())`.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:167-171` — `findTransferLog` uses `.findFirst()` with only the event-signature topic filter, ignoring recipient.
- This was Phase 7 self-review Finding 1; it remains unfixed.

**Recommendation:** Have `pollOnce` build the `TxResult` directly from the already-matched `Log` instead of round-tripping through `getTx`. This eliminates the redundant `eth_getTransactionByHash`/`eth_getTransactionReceipt` calls and guarantees the observation matches the log that triggered it. If `getTx` must remain the canonical builder, add an overload that takes the specific `Log` to decode.

**Confidence:** High.

---

## Finding 2 — `ethCall` does not check for JSON-RPC errors before decoding

**Issue:** `ethCall` returns `response.getValue()` directly. When the RPC returns an error such as `execution reverted` (e.g., calling `symbol()`/`decimals()` on a non-ERC20 contract), web3j does not throw; it returns a response whose value is `null`. `callErc20StringFunction` and `callErc20Uint8Function` then pass that `null` into `FunctionReturnDecoder.decode`, resulting in an opaque `NullPointerException` or `IndexOutOfBoundsException` rather than a clear, actionable exception naming the contract and error.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:207-218` — `ethCall` returns `.send().getValue()` without checking `Response.hasError()`.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:190-196` and `:199-205` — both callers decode the raw result with no null guard.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:220-256` — other fetch methods wrap `IOException` in named `IllegalStateException`, but `ethCall` does not handle JSON-RPC-level errors.
- This was Phase 7 self-review Finding 2; it remains unfixed.

**Recommendation:** Check `Response.hasError()` in `ethCall`; if an error is present, throw `IllegalStateException` naming the provider, contract address, function, and the RPC error message. Add a test that mocks a reverted `eth_call` response and asserts the exception message contains the contract address and error text.

**Confidence:** High.

---

## Finding 3 — `Web3j` and per-provider `ScheduledExecutorService` are never shut down

**Issue:** `EthereumAdapterConfig` builds a `Web3j` instance and a dedicated `ScheduledExecutorService` for each Ethereum provider but provides no destroy hook. `Web3j` implements `AutoCloseable` and holds HTTP connections; the scheduled pool holds non-daemon virtual-thread carrier threads. Because the `@Bean` returns a `List<EthereumAdapter>`, Spring has no way to invoke a destroy method on individual elements. `EthereumAdapter` itself exposes no `close()`/`shutdown()` method. On context restart or redeploy, these resources leak.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfig.java:44` — `Web3j.build(...)` without `.shutdown()`.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfig.java:46-47` — `Executors.newScheduledThreadPool(1, ...)` without shutdown.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java` — no `close()` or `shutdown()` method.
- This was Phase 7 self-review Finding 3; it remains unfixed.

**Recommendation:** Make `EthereumAdapter` implement `AutoCloseable` (or add a `shutdown()` method) that closes `Web3j` and shuts down the `scheduler`. Wrap the returned list in a small bean implementing `DisposableBean` that calls `close()` on each adapter, or return adapters as individual `@Bean`s with `destroyMethod = "close"`. Add a test verifying the scheduler is shut down and `Web3j.shutdown()` is called on context close.

**Confidence:** High.

---

## Finding 4 — Required T06 tests are missing

**Issue:** The frozen brief's Required Tests section lists tests for `getFinalityStatus` tag usage, `getTx` not-found/found/exception cases, `getTokenInfo` decoding, wiring URL/credential, log-filter construction, and subscription cancellation. No test files exist under `services/crypto/src/test/java/com/themistra/crypto/adapter/eth/`.

**Evidence:**
- `services/crypto/src/test/java/com/themistra/crypto/adapter/eth/` directory does not exist.
- TIB §Required Tests lists six test scenarios.
- TIB §Acceptance Criteria (AC1–AC7) cannot be verified without automated tests.

**Recommendation:** Add the missing tests before Phase 9 sign-off. Mock `Web3j` and `ScheduledExecutorService` as required by AC5. Include tests for the three findings above, the API-key placeholder resolution, and the edge cases identified below.

**Confidence:** High.

---

## Finding 5 — Missing `apiKey` env var leaves the literal `{apiKey}` placeholder in the request URL

**Issue:** `resolveUrl` is "non-throwing by design": if the URL contains `{apiKey}` but the environment variable does not resolve, it returns the URL unchanged. In a production deployment with a misconfigured secret, the first actual RPC request will be sent to a URL containing the literal string `{apiKey}`, failing with a confusing network/DNS error instead of a clear "missing credential" failure at startup.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfig.java:60-67`:
  ```java
  String apiKey = environment.getProperty(entry.apiKeySecretName());
  return apiKey == null ? url : url.replace("{apiKey}", apiKey);
  ```
- The Javadoc explicitly calls this "non-throwing by design."

**Recommendation:** Fail fast at wiring time when a URL contains `{apiKey}` but no credential resolves. Throw a clear `IllegalStateException` naming the provider and the missing environment variable. The only legitimate case for no credential is when the URL has no placeholder at all (local fake providers). Add a wiring test for both cases.

**Confidence:** High.

---

## Finding 6 — API key embedded in URL can leak through exceptions and logs

**Issue:** The implementation attaches the resolved API key to the request by substituting it into the URL. Web3j exceptions, HTTP client logs, or any diagnostic that captures the request URL will include the key in plaintext. The TIB's Constraints section says "no credential value is ever logged," but this design violates that constraint in practice unless every downstream log/exceptions path is guaranteed to redact URLs.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfig.java:65-66` — `url.replace("{apiKey}", apiKey)`.
- TIB §Constraints: "Secrets: no credential value is ever logged; `apiKeySecretName` (the reference) may appear in logs, the resolved value never does."
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:224-226` — exception messages include provider name but could still wrap a Web3j/OkHttp exception whose message includes the full URL.

**Recommendation:** Attach the API key via an HTTP header or query parameter using an OkHttp interceptor rather than URL substitution, so the key is not part of the base URL string. If URL substitution is required by the provider, ensure Web3j/OkHttp exceptions are caught and re-thrown with the URL redacted. At minimum, document this risk and the mitigation.

**Confidence:** Medium.

---

## Finding 7 — `getTx`'s `findTransferLog` picks the first Transfer log even for direct `getTx` calls

**Issue:** Beyond the `subscribeAddress` mismatch in Finding 1, `getTx(txHash)` itself has no caller context. If a user/service directly queries a multi-Transfer transaction by hash, `getTx` will report the first Transfer log it finds, which may not be the payment of interest. This is a data-integrity risk for any downstream feature that calls `getTx` directly (e.g., attestation-time re-verification).

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:167-171` — `findTransferLog` uses `.findFirst()` with only the event signature filter.

**Recommendation:** Document the limitation explicitly in the Javadoc: `getTx` reports the first ERC-20 Transfer log in the receipt, or native value if none; callers must not rely on it for transactions with multiple Transfer logs unless they disambiguate separately. For the watcher path, apply Finding 1's recommendation so at least `subscribeAddress` uses the correct log.

**Confidence:** Medium.

---

## Finding 8 — `getTx` confirmation count can overflow `int`

**Issue:** The confirmation count is computed as `currentBlock.subtract(txBlock).add(BigInteger.ONE).intValue()`. If the difference exceeds `Integer.MAX_VALUE` (unlikely for Ethereum in the near future, but possible for a long-lived chain or a bug in block-number handling), the value silently wraps. More immediately, if `currentBlock < txBlock` due to a reorg or inconsistent RPC responses, the result is negative.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:86`:
  ```java
  int confirmations = currentBlock.subtract(txBlock).add(BigInteger.ONE).intValue();
  ```
- No validation that the result is non-negative or within `int` range.

**Recommendation:** Add a guard: if the computed value is negative, throw `IllegalStateException` (inconsistent provider data); if it exceeds `Integer.MAX_VALUE`, cap it or use `Math.toIntExact` to fail loudly. Add a test for the negative case.

**Confidence:** Low-Medium.

---

## Finding 9 — `OkHttpClient` is missing write and call timeouts

**Issue:** `EthereumAdapterConfig` configures `connectTimeout` and `readTimeout` from `ProviderEntry.timeoutSeconds()` but omits `writeTimeout` and `callTimeout`. A hanging POST body write or an overall call that exceeds the sum of connect+read timeouts will not be bounded by the configured timeout.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfig.java:40-43`:
  ```java
  OkHttpClient httpClient = new OkHttpClient.Builder()
          .connectTimeout(Duration.ofSeconds(entry.timeoutSeconds()))
          .readTimeout(Duration.ofSeconds(entry.timeoutSeconds()))
          .build();
  ```

**Recommendation:** Set `writeTimeout` and `callTimeout` to the same value, or document why only connect/read timeouts matter for this adapter's request patterns. Add a wiring test asserting the configured timeouts are present on the built client.

**Confidence:** Low-Medium.

---

## Finding 10 — `fetchBlockNumber` and `fetchLogs` do not handle null payloads in successful responses

**Issue:** `fetchBlockNumber` calls `response.getBlock().getNumber()` without null-checking `getBlock()`. `fetchLogs` calls `response.getLogs()` without null-checking. In pathological cases (malformed provider response, certain error formats that web3j does not map to `IOException`), these can throw `NullPointerException` rather than the intended `IllegalStateException` with provider context.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:242-248` — `fetchBlockNumber`.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java:251-257` — `fetchLogs`.

**Recommendation:** Add null checks and throw named `IllegalStateException` messages when the response payload is unexpectedly null. This aligns with the existing error-handling style in the rest of the class.

**Confidence:** Low.

---

## Summary table

| # | Finding | Severity | Confidence |
|---|---------|----------|------------|
| 1 | Multi-Transfer tx mismatch in `subscribeAddress` | Medium | High |
| 2 | `ethCall` JSON-RPC errors not checked | Medium | High |
| 3 | Web3j/scheduler resource leak | Medium-High | High |
| 4 | Required T06 tests missing | High | High |
| 5 | `{apiKey}` placeholder in URL if credential missing | Medium | High |
| 6 | API key in URL leaks through exceptions/logs | Medium | Medium |
| 7 | `getTx` picks first Transfer log for direct calls | Medium | Medium |
| 8 | Confirmation count overflow/negative value | Low-Medium | Low-Medium |
| 9 | OkHttp missing write/call timeouts | Low-Medium | Low-Medium |
| 10 | Null response payloads not handled | Low | Low |

(End of independent code review.)
