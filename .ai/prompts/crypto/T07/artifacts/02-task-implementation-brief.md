# crypto · T07 · Phase 2 — Task Implementation Brief (TIB)

## Task

Tron adapter. Implement `TronAdapter` (TronGrid / java-tron gRPC) against the same interface.

## Purpose

The second real `ChainAdapter` implementation, and the first task to talk to Tron. Unlike T06
(Ethereum), this task has a direct sibling precedent (`EthereumAdapter`/`EthereumAdapterConfig`) to
compare every design choice against, and a materially different client library shape (`trident`, a
gRPC client with a much richer, higher-level API than `web3j`'s raw JSON-RPC surface).

## Scope

**In:**
- `TronAdapter implements ChainAdapter` — all 5 methods, backed by a constructor-injected
  `org.tron.trident.core.ApiWrapper` (trident's central client class; confirmed via direct class
  inspection of `trident-1.0.0.jar`, not assumed).
- Credential attachment: `ApiWrapperBuilder.withApiKey(String)` — a **first-class mechanism trident
  itself provides**, confirmed via `javap` inspection of `ApiWrapperBuilder`. This resolves Phase 1's
  Open Question #3 cleanly: unlike T06's URL-templating workaround (necessary because `HttpService`
  takes a bare URL), trident's builder has a named credential-attachment method, so `apiKeySecretName`
  resolves via the same `Environment.getProperty(...)` mechanism T06 established, then passes directly
  to `.withApiKey(...)` — no string substitution needed.
- Finality (R7, L4): `ApiWrapper.getNowBlock()` (current block) vs. `ApiWrapper.getNowBlockSolidity()`
  (solidified block) — confirmed present via direct inspection, a direct structural parallel to T06's
  `LATEST`/`FINALIZED` tag pair. `getNowBlockSolidity()` queries trident's separate solidity-node gRPC
  stub (`WalletSolidityGrpc`), matching Tron's own full-node/solidity-node architectural split
  (`org.tron.trident.core.NodeType.{FULL_NODE,SOLIDITY_NODE}`, also confirmed present). This resolves
  Phase 1's Open Question #4: trident *does* expose a direct solidified-block query, no
  confirmation-count computation needed.
- `getTx`: `ApiWrapper.getTransactionById(String, NodeType...)` (existence/basic info) +
  `ApiWrapper.getTransactionInfoById(String, NodeType...)` (block number + event logs — the
  receipt-equivalent). Confirmed via inspection that `TransactionInfo` carries `getBlockNumber()` and
  `getLogList()`, and each `Log` carries `getAddress()`/`getTopicsList()`/`getData()` — structurally
  identical to web3j's `Log` shape, since TRC-20 Transfer events are EVM-style topics+data. Mirrors
  T06's exact design: `fromAddress`/`toAddress` sourced from the Transfer log's topics (amendment #7's
  reasoning transfers directly — a Tron smart-contract-wallet or DEX-router transfer has the same
  "tx-level owner isn't the real sender" problem Ethereum has), not a raw transaction-level field.
- `getTokenInfo`: `org.tron.trident.core.contract.Trc20Contract.symbol()`/`.decimals()` — a
  **ready-made TRC-20 helper trident already provides**, confirmed via inspection. No manual ABI
  encoding needed here, unlike T06's hand-rolled `FunctionEncoder`/`FunctionReturnDecoder` calls for
  ERC-20 (`web3j:core` has no such convenience class).
- `subscribeAddress` transport (O2, **for Tron, this task only** — T06 explicitly scoped its own
  HTTP-polling resolution to Ethereum): polling, not a subscription stream (trident exposes no
  websocket/streaming API in its public surface). Confirmed via inspection: no batch
  `eth_getLogs`-equivalent range-filtered log query exists on `ApiWrapper`. The working mechanism is
  block-by-block: `getNowBlock()` for the poll's upper bound, `ApiWrapper.getTransactionInfoByBlockNum
  (long, NodeType...)` for each new block since the last-scanned cursor (returns every transaction's
  `TransactionInfo`, including logs, for that block in one call), filtering each transaction's logs for
  the TRC-20 Transfer topic + recipient — the direct Tron-shaped analogue of T06's `eth_getLogs`
  recipient-filtered poll, just per-block rather than per-range (no evidence trident supports a
  range-batched query). Same cursor/fixed-delay/virtual-thread-scheduler pattern as T06 (amendments
  #4/#5) carries over unchanged — nothing about polling mechanics is chain-specific.
- A Spring wiring class building `ApiWrapper`/`TronAdapter` instance(s) — one per configured TRON
  `ProviderEntry`, mirroring `EthereumAdapterConfig`'s exact precedent.

**Out:**
- `EthereumAdapter` (T06, shipped) — untouched.
- Any change to `ChainAdapter`, `Chain`, `adapter/model/*`, `ProviderProperties` (all frozen) —
  consumed, not modified.
- Defensive Base58Check address (re-)validation inside `TronAdapter` — **decision: not built here**,
  mirroring T06's identical deferral for EIP-55. Note (not a scope change): trident *does* ship a
  ready-made `org.tron.trident.core.utils.Base58` utility, confirmed present — worth flagging for
  whichever task actually owns `AddressValidator` (task 12), not used by this task.
- Any signing/write operation — `ApiWrapper` exposes a large write/signing surface (`transfer`,
  `freezeBalance`, `voteWitness`, `signTransaction`, etc., confirmed via inspection) that this task
  does not touch at all. `TronAdapter` is read-only, consistent with L11 (KMS-only signing, confined
  to the attest module) — `hexPrivateKey`/`withPrivateKey` on `ApiWrapperBuilder` are never used here.
- Retry/backoff logic — same rationale as T06 amendment #10: the quorum model absorbs single-provider
  transient failure; provider-health tracking (task 10) is the correct layer.
- The specific real provider vendor names (O1/Q1) — "TronGrid + ? + ?" per package.md §11 Q1's own
  text; this task builds against trident's `ApiWrapper`, which is provider-agnostic (any gRPC endpoint
  speaking the Tron wallet/solidity protocol), not hardcoded to TronGrid specifically.

## Business Rules

- **R7.** `getFinalityStatus` reports the real solidified block number (`getNowBlockSolidity()`),
  never a confirmation-count approximation.

## Locked Decisions

- **L4.** `getFinalityStatus` returns raw data only — `txBlockNumber` (from `TransactionInfo`),
  `currentBlockNumber` (`getNowBlock()`), `finalizedBlockNumber` (`getNowBlockSolidity()`). No finality
  *decision* computed here — that's `TronFinalityPolicy`'s job (task 14).
- **L7.** `getTokenInfo` returns whatever `Trc20Contract.symbol()`/`.decimals()` report — no allowlist
  check.
- **L13.** No credential committed; `apiKeySecretName` resolved via `Environment` at wiring time only,
  passed to `ApiWrapperBuilder.withApiKey(...)`.
- **L14/L15.** `TronAdapter` implements the plain `ChainAdapter` interface with no extra capability;
  lives under `adapter/tron/`.

## Dependencies

- `io.github.tronprotocol:trident:1.0.0` (present) — `ApiWrapper`/`ApiWrapperBuilder`,
  `NodeType.{FULL_NODE,SOLIDITY_NODE}`, `Trc20Contract`, and the `Response.TransactionInfo`/`Log`
  proto types — all confirmed present and shaped as described above via direct `javap`/class
  inspection of `trident-1.0.0.jar`, not assumed from documentation or memory.
- `ProviderProperties.ProviderEntry` (T03) — `url`, `apiKeySecretName`, `timeoutSeconds`. **Open
  config-shape question, not yet resolved**: `ApiWrapperBuilder`'s constructor accepts a separate
  `grpcEndpointSolidity` alongside `grpcEndpoint`, but `ProviderEntry` has only one `url` field.
  Provisional plan: pass the same `url` value for both `grpcEndpoint` and `grpcEndpointSolidity`
  unless Phase 5/6 finds this doesn't work against trident's actual channel-construction behavior — if
  a real deployment genuinely needs two distinct endpoints, that is a `ProviderProperties` config-shape
  gap outside this task's authority to fix (frozen, T03) and becomes a Phase 3/9 Open Question, not a
  silent workaround.
- Spring `Environment` — credential resolution (established mechanism, T06).
- `ScheduledExecutorService`, constructor-injected, virtual-thread-backed — same as T06.
- Heavier transitive dependency surface than `web3j:core` (`grpc-*:1.81.0`, `netty-*:4.1.123.Final`,
  `protobuf-java:3.25.8`, `guava:33.0.0-jre`, `vertx-core:4.5.27`, `bouncycastle:1.84`) — a
  `dependency:tree -Dverbose` conflict check is warranted once `ApiWrapper` is actually constructed in
  code (T06's own Jackson-annotations lesson: a silently-downgraded transitive version doesn't surface
  until the affected class actually runs).

## Inputs

- `ProviderProperties`' TRON `chains[]` entries at wiring time.
- Method calls from `ChainAdapter` consumers (quorum module, task 9; watcher layer, task 16) — none
  exist yet.

## Outputs

- `TxResult`/`TokenInfo`/`FinalityStatus` per `ChainAdapter`'s contract.
- `ObservationSink.onObservation(TxResult)` calls, pushed from the block-scanning poll loop.

## State Changes

None — same as T06: the only in-memory state is the "last block scanned" cursor, held for the
`Subscription`'s lifetime.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapter.java`
- A Spring `@Configuration` wiring class (exact name Phase 5) — builds `ApiWrapper` per TRON
  `ProviderEntry`: resolves `apiKeySecretName` via `Environment`, calls `.withApiKey(...)`, constructs
  the virtual-thread-backed `ScheduledExecutorService`.

## Files to Modify

- `services/crypto/src/main/resources/application.properties` — add a Tron poll-interval property,
  mirroring `themistra.crypto.adapter.ethereum.poll-interval-ms` (exact key name Phase 5).

## Files NOT to Modify

- `adapter/ChainAdapter.java`, `adapter/Chain.java`, `adapter/model/*.java`, `adapter/ObservationSink.java`,
  `adapter/FakeChainAdapter.java`, `adapter/eth/*` (T05/T06) — frozen/shipped.
- `common/config/ProviderProperties.java` (T03) — consumed, not modified.
- `services/crypto/pom.xml` — no new dependency expected (trident already present, T01).
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R7, L4).** `getFinalityStatus` uses `getNowBlockSolidity()`, never a confirmation-count
  computation.
- **AC2 (ChainAdapter contract).** `getTx` for a transaction trident reports as not found returns
  `TxResult(exists=false, ...)`; a genuine transport/gRPC error propagates unchecked.
- **AC3 (L7).** `getTokenInfo` keyed by `contractAddress` alone, via `Trc20Contract`.
- **AC4.** `chain()` returns `Chain.TRON`.
- **AC5.** Provider endpoint/credential come from `ProviderProperties` via the wiring class, passed to
  `ApiWrapperBuilder`, not hardcoded.
- **AC6.** No unit test makes a real network/gRPC call.
- **AC7.** The block-scan poll filters by recipient topic only, mirroring T06's AC6 for the
  contract-address-agnostic detection rule.
- **AC8.** `Subscription.cancel()` stops the scheduled poll.
- **AC9.** Polling cursor initializes to the current block (`getNowBlock()`) at subscribe time, not
  block 0; fixed-delay scheduling.
- **AC10.** `fromAddress`/`toAddress` reflect the Transfer log's topics, not a raw transaction-level
  field, for TRC-20 transfers.

## Required Tests

- `getFinalityStatus` uses `getNowBlockSolidity()`/`getNowBlock()` correctly (AC1); throws for a
  not-found/unmined transaction.
- `getTx`: `exists=false` for not-found/pending (AC2); fully-populated `TxResult` for a found TRC-20
  transfer with `fromAddress`/`toAddress` from the log (AC10); native-value fallback when no Transfer
  log is present; a mocked transport exception propagates unchecked.
- `getTokenInfo` decodes a mocked `Trc20Contract` response correctly (AC3).
- `chain()` returns `Chain.TRON` (AC4).
- Wiring: credential reaches `ApiWrapperBuilder.withApiKey(...)` (AC5).
- `subscribeAddress`: block-scan poll has no contract-address restriction (AC7); cursor starts at the
  current block (AC9); cancelling stops further polling (AC8).

## Constraints

- **Module boundaries (L15):** `TronAdapter` under `adapter/tron/`; wiring under `common/` or
  `adapter/tron/` (Phase 5 to finalize), mirroring T06.
- **Thread-safety:** polling runs on a virtual-thread-backed scheduler; `sink.onObservation(...)` may
  be invoked off the caller's thread — identical constraint to T06.
- **Money (agents.md):** TRC-20 amount decoded as `BigInteger` → `BigDecimal`, base units, never
  scaled by `decimals` at this layer.
- **Secrets:** credential values never logged; `apiKeySecretName` (the reference) may be.
- **Reliability:** no retry/backoff in this task (mirrors T06 amendment #10).

## Open Questions

No blockers for building/testing this task. One genuine design uncertainty carried into Phase 3 for
adversarial review, not yet a blocker: **whether a single `ProviderEntry.url` value can correctly
serve both `ApiWrapperBuilder`'s `grpcEndpoint` and `grpcEndpointSolidity` parameters** for a real
provider (see Dependencies) — the provisional same-URL plan may prove insufficient once Phase 5/6
constructs a real `ApiWrapper` and inspects its channel behavior, or once a real Tron provider's actual
port/endpoint convention is known (still blocked on O1/Q1 either way).
