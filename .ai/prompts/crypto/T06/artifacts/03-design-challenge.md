<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) adversarial review for crypto · T06. -->

# crypto · T06 · Phase 3 — Design Challenge Findings

**Scope:** Review `artifacts/02-task-implementation-brief.md` (TIB) against `spec/crypto-service/agents.md`, `spec/crypto-service/design.md` §4a/§4c, `spec/crypto-service/requirements.md` R1/R4/R6/R13/R14, and `spec/crypto-service/package.md` §2/§11.

**Directive:** Do not redesign or implement. Surface hidden assumptions, ambiguities, untestable rules, missing edge cases, conflicts with locked decisions or `agents.md`, unstated dependencies, ordering hazards, and contract mismatches. For each finding: **Issue · Severity · Evidence · Recommended brief amendment.**

---

## Finding 1 — API key injection into the HTTP request is unspecified

**Issue:** The TIB resolves credential resolution (`apiKeySecretName` → `Environment.getProperty`) but does not specify how the resolved key is attached to the Web3j HTTP request. Different providers attach API keys differently: Alchemy uses a path segment or query parameter; Infura embeds it in the URL; some providers require a header. Because the task is intentionally provider-agnostic (Q1 unresolved), the wiring class cannot know the correct mechanism, yet AC4 requires "no hardcoded endpoint or key."

**Severity:** High — without this, the adapter cannot actually talk to a real provider even after credentials are provisioned. It is the most likely first failure mode in a real deployment.

**Evidence:**
- TIB §Scope / In: "Resolving `apiKeySecretName`'s resolution mechanism **for this task**: Spring's `Environment` abstraction (`Environment.getProperty(apiKeySecretName)`) ..."
- TIB §Dependencies: "`org.web3j:core:6.0.0` ... `HttpService` ..."
- TIB §Files to Create / `EthereumAdapter.java`: constructor takes `Web3j web3j`, implying the key must already be baked into the `Web3j` instance by the wiring class.
- `spec/crypto-service/package.md` §11 Q1: "Provider set & quorum N per chain ... is N fixed at 3 with 2-of-3, or configurable per chain? Placeholder in `design.md` §4b-O1. Blocker for real deployment (not for fake-provider tests)."

**Recommended brief amendment:**
- Either (a) document that the wiring class will attach the API key in a provider-specific way and that the exact attachment is out of scope until Q1 is resolved, or
- (b) require `ProviderEntry` to gain a field (e.g., `authType`, `authHeaderName`, `authQueryParam`) that tells the wiring class how to inject the key, or
- (c) explicitly scope T06 to building the adapter only, and defer production wiring to a follow-up task that knows the provider vendor.
- Add an AC/test proving the constructed `HttpService`/`Web3j` contains the resolved credential in the expected location (URL/header) for at least one representative mechanism.

---

## Finding 2 — `getTx` behavior for a pending/unmined transaction is unspecified

**Issue:** `eth_getTransactionByHash` can return a transaction that has not yet been mined (no `blockNumber`, no receipt). `eth_getTransactionReceipt` returns `null` in that case. The TIB says `getTx` uses both RPCs, but does not say what `TxResult` is returned when the tx is in mempool. `TxResult.blockNumber` is a primitive `long`, so it cannot be null; returning `exists=true` with `blockNumber=0` would be misleading, while returning `exists=false` loses the information that the tx is pending.

**Severity:** Medium-High — a common, non-error condition produces an ambiguous contract that T06 implementers and T09 quorum consumers will interpret differently.

**Evidence:**
- TIB §Files to Create / `EthereumAdapter.java`: `getTx` "eth_getTransactionByHash + eth_getTransactionReceipt; exists=false (not throw) if not found."
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TxResult.java`: `long blockNumber` (primitive).
- `spec/crypto-service/requirements.md` R1: "tx existence" is a quorum-checked fact, but "exists" typically means "observed on chain," not "in mempool."

**Recommended brief amendment:**
- Define that a transaction with no receipt (or no `blockNumber`) returns `TxResult(exists=false, ...)`, same as a truly not-found transaction, because the adapter only reports mined transactions. Document that mempool monitoring is the responsibility of `subscribeAddress`/`Watcher`, not `getTx`.
- Add a required test for this case.

---

## Finding 3 — `getFinalityStatus` behavior for a nonexistent transaction is unspecified

**Issue:** `FinalityStatus` has three primitive `long` fields and no `exists` flag. If `eth_getTransactionByHash` returns `null` for the requested `txHash`, `getFinalityStatus` cannot return a meaningful `FinalityStatus` (any numeric value would be a misleading sentinel). The TIB does not say whether to throw or return a sentinel like `(-1, -1, -1)`.

**Severity:** Medium — `FinalityPolicy` consumers need a deterministic failure mode; ambiguity leads to off-by-one or sentinel-handling bugs.

**Evidence:**
- TIB §Files to Create / `EthereumAdapter.java`: `getFinalityStatus` "txBlockNumber from the tx's own block; currentBlockNumber via LATEST; finalizedBlockNumber via FINALIZED."
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/FinalityStatus.java`: `public record FinalityStatus(long txBlockNumber, long currentBlockNumber, long finalizedBlockNumber)`.
- No mention of not-found handling in the TIB.

**Recommended brief amendment:**
- State the contract explicitly: if the transaction is not found, `getFinalityStatus` throws `IllegalStateException` (or a domain exception) rather than returning a sentinel. Alternatively, change `FinalityStatus.txBlockNumber` to `Long` and return `null` for an unobserved tx — but that is a broader type change.
- Add a required test for the not-found case.

---

## Finding 4 — `subscribeAddress` polling initialization and overlap semantics are unspecified

**Issue:** The polling loop needs a "last block scanned" cursor. The TIB says this is held for the lifetime of the `Subscription`, but it does not specify the initial value (genesis block 0? `LATEST` at subscription time? something else?). Starting from 0 would fetch every Transfer log ever, causing a massive first poll. It also does not say whether the scheduler uses fixed-delay (non-overlapping) or fixed-rate (potentially overlapping) execution.

**Severity:** Medium — incorrect initialization causes operational failure (backlog or missed logs); overlap semantics affect thread-safety and duplicate detection.

**Evidence:**
- TIB §State Changes: "its polling loop's only in-memory state is 'the last block number scanned,' held for the lifetime of the `Subscription`."
- TIB §Dependencies: "a scheduler for the polling loop — `ScheduledExecutorService`, constructor-injected."
- TIB §Dependencies: "New, small scalar config: `themistra.crypto.adapter.ethereum.poll-interval-ms`."
- No mention of initial cursor or scheduling mode.

**Recommended brief amendment:**
- Specify that the initial cursor is the `LATEST` block at the time `subscribeAddress` is called (so the adapter only observes new transfers from that point forward).
- Specify that the scheduler uses fixed-delay (`scheduleWithFixedDelay`) so a slow poll delays the next one and overlapping executions are avoided.
- Add tests verifying both the initial cursor and the non-overlap behavior with a mocked scheduler.

---

## Finding 5 — Virtual-thread requirement for the watcher layer is not addressed

**Issue:** `agents.md` states "Virtual threads are used for the watcher layer." `EthereumAdapter.subscribeAddress`'s polling loop is a watcher-layer concern. The TIB proposes a constructor-injected `ScheduledExecutorService` but does not say whether it must use virtual threads, platform threads, or a Spring-managed scheduler. A plain `ScheduledExecutorService` defaults to platform threads, which would contradict the standing rule unless the watcher layer is defined as only the higher-level coordination in task 16.

**Severity:** Medium — potential conflict with `agents.md` if the adapter's polling threads are considered part of the watcher layer.

**Evidence:**
- `spec/crypto-service/agents.md` §Language & build: "Virtual threads are used for the watcher layer."
- TIB §Dependencies: "a scheduler for the polling loop — `ScheduledExecutorService`, constructor-injected ... not a Spring `@Scheduled` method."
- TIB §Constraints: "Thread-safety: the polling `ScheduledExecutorService` runs on its own thread(s)."

**Recommended brief amendment:**
- Explicitly state whether `subscribeAddress`'s polling threads are considered part of the watcher layer and therefore must be virtual threads, or whether virtual threads are deferred to task 16's higher-level watcher.
- If virtual threads are required here, specify how to obtain a virtual-thread-backed `ScheduledExecutorService` (e.g., a custom `ScheduledThreadPerTaskExecutor` wrapper or a Spring-managed virtual-thread scheduler).

---

## Gap 6 — `getTx` confirmations formula may be off-by-one and is undocumented

**Issue:** The TIB says `confirmations = currentBlock - txBlock`. In Ethereum convention, a transaction included in block N has 1 confirmation at block N, so the formula is usually `currentBlock - txBlock + 1`. The TIB's formula would report 0 confirmations for a transaction in the latest block. This is not necessarily wrong, but it must be an intentional, documented convention because `chain.tx.confirmed` (R9) carries this count and the Payment Service will display it.

**Severity:** Low-Medium — a convention mismatch between this service and the Payment Service (or user expectations) would cause confusion.

**Evidence:**
- TIB §Files to Create / `EthereumAdapter.java`: `getTx` "confirmations = currentBlock - txBlock."
- `spec/crypto-service/requirements.md` R9: "emit `chain.tx.confirmed` carrying the confirmation count."
- `spec/crypto-service/design.md` §4c `chain.tx.finalized` schema includes `"confirmations": { "type": "integer" }`.

**Recommended brief amendment:**
- Document the intended formula explicitly. If `currentBlock - txBlock` is intentional, add a comment explaining that 0 confirmations means "in the latest block" and that the Payment Service must interpret it accordingly.
- Add a test with `txBlock == currentBlock` asserting the expected confirmation count.

---

## Finding 7 — `getTx` `fromAddress` semantics for contract-initiated transfers are ambiguous

**Issue:** For an ERC-20 transfer, the transaction's `from` field is the externally owned account or contract that submitted the tx, while the `Transfer` event's `from` topic is the actual token sender. The TIB says `getTx` decodes the Transfer log for `tokenContractAddress`, `amount`, and `toAddress`, but it does not say which `fromAddress` to populate: the transaction originator or the log's token sender. For smart-contract wallets / DeFi routers, these can differ, affecting payment attribution and address-poisoning detection (R17).

**Severity:** Low-Medium — affects downstream quorum comparison and address-poisoning flagging.

**Evidence:**
- TIB §Files to Create / `EthereumAdapter.java`: `getTx` "decodes an ERC-20 Transfer log from the receipt if present for tokenContractAddress/amount/toAddress, else tokenContractAddress=null and amount reflects the native tx value."
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TxResult.java`: fields include `fromAddress` and `toAddress`.
- `spec/crypto-service/requirements.md` R17: address-poisoning detection compares payer/counterparty addresses.

**Recommended brief amendment:**
- Specify that `fromAddress` is the transaction's `from` field (the submitter/origin), not the Transfer log's `from` topic, and that `toAddress` is the recipient from the Transfer log for ERC-20 transfers (or the tx's `to` for native transfers). Document that smart-contract-wallet semantics are handled at a higher layer if needed.
- Add a test with a contract-initiated ERC-20 transfer asserting both `fromAddress` and `toAddress` values.

---

## Finding 8 — Required tests miss several important edge cases

**Issue:** The Required Tests list covers the happy paths and a few error paths, but omits: (a) pending/unmined `getTx`, (b) `getFinalityStatus` for a not-found tx, (c) native-ETH-transfer `getTx`, (d) `getTokenInfo` for a non-ERC20/reverting contract, (e) `subscribeAddress` initial cursor behavior, and (f) scheduler fixed-delay vs fixed-rate semantics.

**Severity:** Medium — missing edge-case tests let ambiguity survive into later tasks.

**Evidence:**
- TIB §Required Tests lists 6 test scenarios, none of the above.
- Findings 2–4 and 6–7 above identify the omitted cases.

**Recommended brief amendment:**
- Expand Required Tests to include:
  - `getTx` returns `exists=false` when the tx has no receipt (pending/unmined).
  - `getFinalityStatus` throws for a not-found tx (or returns whatever sentinel the brief chooses after Finding 3).
  - `getTx` for a native transfer with no Transfer log returns `tokenContractAddress=null` and the correct native value.
  - `getTokenInfo` propagates an exception (or returns a defined sentinel) when the contract does not implement ERC-20.
  - `subscribeAddress` initializes its cursor to `LATEST` and uses fixed-delay scheduling.

---

## Finding 9 — `ProviderEntry.timeoutSeconds` is not wired into the HTTP client

**Issue:** The TIB lists `ProviderProperties.ProviderEntry` (including `timeoutSeconds`) as a dependency but never says how `timeoutSeconds` is applied. If it is ignored, RPC calls use default timeouts, which may be too long or too short for the platform's SLOs and provider-degraded detection (R5).

**Severity:** Low-Medium — operational behavior depends on a property that has no observable effect.

**Evidence:**
- TIB §Dependencies: "`ProviderProperties.ProviderEntry` (T03) — `url`, `apiKeySecretName`, `timeoutSeconds`."
- TIB §Files to Create / `EthereumAdapter.java`: constructor takes `Web3j`, not `timeoutSeconds` directly; the wiring class builds `Web3j`.
- No mention of timeout configuration in the TIB.

**Recommended brief amendment:**
- Specify that the wiring class configures the `OkHttpClient` (or whatever transport `HttpService` uses) with connect/read/write timeouts derived from `ProviderEntry.timeoutSeconds`.
- Add a wiring test asserting the `HttpService` timeout equals the configured value.

---

## Finding 10 — No retry/backoff policy for transient RPC failures is specified

**Issue:** The TIB says a genuine RPC/transport error in `getTx` propagates as an unchecked exception. It does not specify retry, backoff, or circuit-breaker behavior. Transient provider errors are common; without a policy, every blip becomes a hard failure for the caller and the polling loop.

**Severity:** Low-Medium — affects reliability and provider-health metrics (R5).

**Evidence:**
- TIB §Acceptance Criteria AC2: "a mocked `Web3j` I/O exception propagates unchecked."
- `spec/crypto-service/requirements.md` R5: providers can become "unhealthy, lagging, or repeatedly disagreeing."
- No retry/backoff discussion in the TIB.

**Recommended brief amendment:**
- Document the retry policy explicitly: e.g., "no retry in T06; callers (quorum/watcher) treat exceptions as provider failures and may retry at their own layer." Or, if retry is desired, add a small scalar config (`themistra.crypto.adapter.ethereum.max-retries`) and wire it into the Web3j transport.
- Add a test verifying the chosen behavior.

---

## Summary of requested brief amendments

| # | Amendment | Priority |
|---|-----------|----------|
| 1 | Specify how the resolved API key is attached to the Web3j HTTP request. | High |
| 2 | Define `getTx` behavior for pending/unmined transactions. | Medium-High |
| 3 | Define `getFinalityStatus` behavior for nonexistent transactions. | Medium |
| 4 | Specify `subscribeAddress` initial cursor and scheduling mode. | Medium |
| 5 | Clarify virtual-thread usage for the polling loop. | Medium |
| 6 | Document the `getTx` confirmations formula convention. | Low-Medium |
| 7 | Clarify `fromAddress` source for ERC-20 transfers. | Low-Medium |
| 8 | Expand Required Tests to cover pending tx, not-found finality, native transfer, etc. | Medium |
| 9 | Wire `ProviderEntry.timeoutSeconds` into the HTTP client. | Low-Medium |
| 10 | Document retry/backoff policy (even if "none"). | Low-Medium |

(End of design challenge review.)
