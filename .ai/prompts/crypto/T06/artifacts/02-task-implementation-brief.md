# crypto · T06 · Phase 2 — Task Implementation Brief (TIB)

## Task

Ethereum adapter. Implement `EthereumAdapter` (web3j): `getTx`, `getTokenInfo`, `subscribeAddress`,
`getFinalityStatus`. Provider credentials via config (O1/Q1).

## Purpose

The first real `ChainAdapter` implementation — the thing every earlier "shape only" task (T05) was
built for. Also the first task to actually consume `ProviderProperties` (T03, shipped since but never
used) and the first to touch a real external system, making agents.md's "real RPC providers are never
called in tests or CI" directly operative.

## Scope

**In:**
- `EthereumAdapter implements ChainAdapter` — all 5 methods, backed by a constructor-injected `Web3j`
  (an interface; injectable/mockable per Phase 0/1's own testability finding).
- Resolving `subscribeAddress`'s transport (O2) **for Ethereum, this task only**: HTTP polling, not
  websocket. Rationale: works with any HTTP-only provider tier (no assumption about websocket
  availability), needs no new config field (existing `ProviderEntry.url` suffices), and O2's own
  wording ("Recommend one; proceed if low-risk," unlike O5's explicit "author approval required")
  delegates this choice to the implementer.
- Resolving `apiKeySecretName`'s resolution mechanism **for this task**: Spring's `Environment`
  abstraction (`Environment.getProperty(apiKeySecretName)`) — the configured value names an
  environment variable (or system property) `Environment` resolves through its standard precedence,
  matching how every other env-driven config value in this service already works
  (`${KAFKA_BOOTSTRAP_SERVERS:...}`-style placeholders). No new dependency; test-injectable via
  `MockEnvironment`.
- A concrete design for `subscribeAddress`'s actual detection mechanism (a genuine gap identified in
  Phase 1 — `ChainAdapter.subscribeAddress` takes no token-contract parameter, yet ERC-20 payments'
  real recipient lives in an event log, not the transaction's own `to` field): poll `eth_getLogs` for
  the standard ERC-20 `Transfer(address,address,uint256)` event topic, filtered by the *recipient*
  topic (`address`) but with **no contract-address filter** — catching a Transfer-shaped log from any
  contract. This deliberately does not require `EthereumAdapter` to know the token allowlist; a
  spoofed/non-allowlisted contract emitting a fake Transfer is exactly what downstream
  `TokenValidator` (task 11) rejects as `UNKNOWN_TOKEN` (L7/R14) — the adapter reports what it saw,
  it doesn't pre-judge legitimacy.
- Whatever Spring wiring constructs `Web3j` instance(s) — one per configured Ethereum
  `ProviderEntry` — from `ProviderProperties`.

**Out:**
- `TronAdapter` (task 7).
- Any change to `ChainAdapter`, `Chain`, or the `adapter/model/*` types (T05, frozen).
- Any change to `ProviderProperties` (T03, frozen) — this task only *consumes* it.
- Defensive EIP-55 address (re-)validation inside `EthereumAdapter` — **decision: not built here.**
  `AddressValidator` (task 12) doesn't exist yet; `EthereumAdapter` trusts the caller (eventually
  `WatchService`, task 15) already validated the address before calling `subscribeAddress`. Documented
  as an explicit boundary assumption, not silently assumed.
- Native-ETH-transfer detection in `subscribeAddress` — the platform's own premise is stablecoin
  (ERC-20) payments (`token_allowlist`); only Transfer-log-based detection is built. `getTx` still
  reports a native-value transfer's `amount` (with `tokenContractAddress=null`) if queried directly by
  hash and no Transfer log is present, but `subscribeAddress` does not proactively watch for these.
- The specific real provider vendor names (O1/Q1) — explicitly a "blocker for real deployment, not
  for fake-provider tests" per `package.md` §11 Q1's own text; this task builds a provider-agnostic
  client, not a vendor-specific one.

## Business Rules

- **R6.** `getFinalityStatus` reports the real `finalized`-tagged block number
  (`DefaultBlockParameterName.FINALIZED`), never a confirmation-count approximation.

## Locked Decisions

- **L4.** `getFinalityStatus` returns raw data only — `txBlockNumber` (from the tx's receipt),
  `currentBlockNumber` (`LATEST` tag), `finalizedBlockNumber` (`FINALIZED` tag). No finality
  *decision* is computed here.
- **L7.** `getTokenInfo` returns whatever `symbol()`/`decimals()` the contract at `contractAddress`
  reports (raw ABI calls) — no allowlist check, no identity beyond the address itself.
- **L13.** No credential is committed; `apiKeySecretName` names an environment variable resolved at
  wiring time, never a literal key in `application.properties` beyond the already-existing
  obviously-fake `local` placeholders (T03).
- **L14/L15.** `EthereumAdapter` implements the plain `ChainAdapter` interface with no capability
  beyond it; lives under `adapter/eth/`.

## Dependencies

- `org.web3j:core:6.0.0` (present) — `Web3j`, `HttpService`, `DefaultBlockParameterName.FINALIZED`,
  `FunctionEncoder`/`FunctionReturnDecoder` (manual ERC-20 ABI calls — no codegen module).
- `ProviderProperties.ProviderEntry` (T03) — `url`, `apiKeySecretName`, `timeoutSeconds`.
- Spring `Environment` — credential resolution.
- A scheduler for the polling loop — `ScheduledExecutorService`, constructor-injected (test-injectable,
  no hidden global state), not a Spring `@Scheduled` method (there can be multiple `EthereumAdapter`
  instances, one per configured provider — not a single `@Component` singleton the way `OutboxRelay`
  is).
- New, small scalar config: `themistra.crypto.adapter.ethereum.poll-interval-ms` (default e.g.
  `15000`), matching T04's own precedent (a plain `@Value` scalar, not a new
  `@ConfigurationProperties` class, for one setting) — added to `application.properties`, not a new
  field on the already-shipped `ProviderProperties`.

## Inputs

- `ProviderProperties`' Ethereum `chains[]` entries at wiring time.
- Method calls from `ChainAdapter` consumers (quorum module, task 9; watcher layer, task 16) — none
  exist yet, so this task's own tests are the only caller in this task's scope.

## Outputs

- `TxResult`/`TokenInfo`/`FinalityStatus` per `ChainAdapter`'s contract.
- `ObservationSink.onObservation(TxResult)` calls, pushed from the polling loop when a new matching
  Transfer log is found.

## State Changes

None — `EthereumAdapter` holds no persistent state; its polling loop's only in-memory state is "the
last block number scanned," held for the lifetime of the `Subscription`.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java`
  ```java
  public class EthereumAdapter implements ChainAdapter {
      public EthereumAdapter(Web3j web3j, String providerName, ScheduledExecutorService scheduler,
                              Duration pollInterval);

      @Override public Chain chain(); // returns Chain.ETHEREUM

      @Override public TxResult getTx(String txHash);
          // eth_getTransactionByHash + eth_getTransactionReceipt; exists=false (not throw) if not
          // found; decodes an ERC-20 Transfer log from the receipt if present for
          // tokenContractAddress/amount/toAddress, else tokenContractAddress=null and amount
          // reflects the native tx value; confirmations = currentBlock - txBlock.

      @Override public TokenInfo getTokenInfo(String contractAddress);
          // eth_call symbol() and decimals() via manual ABI encode/decode.

      @Override public Subscription subscribeAddress(String address, ObservationSink sink);
          // schedules a periodic eth_getLogs poll for the standard ERC-20 Transfer event topic,
          // filtered by recipient topic = address, no contract-address filter; for each new
          // matching log, calls getTx on that log's transactionHash and pushes the result to sink.
          // returned Subscription.cancel() stops the scheduled poll.

      @Override public FinalityStatus getFinalityStatus(String txHash);
          // txBlockNumber from the tx's own block; currentBlockNumber via LATEST;
          // finalizedBlockNumber via FINALIZED (R6).
  }
  ```
- Whatever Spring `@Configuration` class builds `Web3j`/`EthereumAdapter` instance(s) from
  `ProviderProperties` — exact name/shape is Phase 5 design work (mirrors the "not spec-named but
  functionally necessary" situation T04 hit with `KafkaProducerConfig`).

## Files to Modify

- `services/crypto/src/main/resources/application.properties` — add
  `themistra.crypto.adapter.ethereum.poll-interval-ms`.

## Files NOT to Modify

- `adapter/ChainAdapter.java`, `adapter/Chain.java`, `adapter/model/*.java`, `adapter/ObservationSink.java`,
  `adapter/FakeChainAdapter.java` (T05) — frozen.
- `common/config/ProviderProperties.java` (T03) — consumed, not modified.
- `services/crypto/pom.xml` — no new dependency expected (see Dependencies).
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R6, L4).** `getFinalityStatus` uses the `FINALIZED` block tag, never a confirmation-count
  computation.
- **AC2 (ChainAdapter contract).** `getTx` for a transaction `Web3j` reports as not found returns
  `TxResult(exists=false, ...)`; a genuine RPC/transport error propagates as an unchecked exception.
- **AC3 (L7).** `getTokenInfo` is keyed by `contractAddress` alone; `symbol`/`decimals` come from the
  contract's own ABI response.
- **AC4 (task statement).** Provider URL and credential come from `ProviderProperties` via the
  wiring class — no hardcoded endpoint or key anywhere in `EthereumAdapter` itself.
- **AC5 (testability).** No `EthereumAdapter` unit test makes a real network call — `Web3j` and the
  `ScheduledExecutorService` are both mocked/substituted.
- **AC6 (subscribeAddress design).** The Transfer-log poll filters by recipient topic only, no
  contract-address filter — proven by inspecting the constructed `EthFilter`/log-query parameters in
  a test, not just behavior.
- **AC7.** `Subscription.cancel()` (returned by `subscribeAddress`) stops the scheduled poll — no
  further `ObservationSink` calls after cancellation.

## Required Tests

- `getFinalityStatus` uses `FINALIZED`/`LATEST` tags correctly (AC1).
- `getTx` returns `exists=false` for a not-found transaction; returns a fully-populated `TxResult`
  (including decoded Transfer-log fields) for a found ERC-20 transfer; a mocked `Web3j` I/O exception
  propagates unchecked (AC2).
- `getTokenInfo` decodes a mocked `symbol()`/`decimals()` ABI response correctly (AC3).
- Wiring test: provider URL/credential from `ProviderProperties` reach the constructed client, not a
  hardcoded value (AC4).
- `subscribeAddress`'s log-filter construction has no contract-address restriction, only the
  recipient-topic filter (AC6).
- Cancelling the returned `Subscription` stops further polling (AC7).

## Constraints

- **Module boundaries (L15):** `EthereumAdapter` under `adapter/eth/`; any wiring class under
  `common/` or `adapter/eth/` (Phase 5 to finalize).
- **Thread-safety:** the polling `ScheduledExecutorService` runs on its own thread(s); `sink.onObservation(...)`
  may be invoked from a non-caller thread — callers (future watcher layer) must be prepared for that,
  same as any real async subscription would require.
- **Money (agents.md):** ERC-20 `amount` decoded from ABI as `BigInteger` (base units, matching the
  contract's raw integer representation) then converted to `BigDecimal` for `TxResult.amount` — never
  floating point, never scaled by `decimals` at this layer (base-unit-exact per agents.md; scaling for
  display is a later concern, not this adapter's).
- **Secrets:** no credential value is ever logged; `apiKeySecretName` (the reference) may appear in
  logs, the resolved value never does.
- **Null handling:** `getTx`'s `tokenContractAddress`/`fromAddress`/`toAddress` may be `null` for a
  native-value-only transaction with no decodable Transfer log — documented, not a defect.

## Open Questions

No blockers. (O1/Q1's specific provider vendor names remain open per the spec's own framing, but
explicitly do not block this task — confirmed in Phase 1.)
