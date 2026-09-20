# crypto · T06 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-03. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in.

## Task

Ethereum adapter. Implement `EthereumAdapter` (web3j): `getTx`, `getTokenInfo`, `subscribeAddress`,
`getFinalityStatus`. Provider credentials via config (O1/Q1).

## Purpose

The first real `ChainAdapter` implementation and the first task to consume `ProviderProperties` (T03,
shipped but unused until now) and touch a real external system.

## Scope

**In:**
- `EthereumAdapter implements ChainAdapter` — all 5 methods, backed by a constructor-injected `Web3j`.
- `subscribeAddress` transport: HTTP polling (O2 resolved for Ethereum, this task only), via
  `eth_getLogs` scanning for the standard ERC-20 `Transfer(address,address,uint256)` event topic,
  filtered by *recipient* topic only — **no contract-address filter**, so a non-allowlisted contract's
  fake Transfer is reported (and rejected downstream by `TokenValidator`, task 11), not silently
  dropped here.
- Credential resolution: `apiKeySecretName` names an environment variable resolved via Spring's
  `Environment` abstraction. **Attachment mechanism (amendment #1): `ProviderEntry.url` may contain a
  literal `{apiKey}` placeholder token; the wiring class substitutes the resolved credential into it
  before constructing `HttpService`.** Covers URL-embedded-key providers (Alchemy/Infura/QuickNode
  and similar) — header-based credential injection is explicitly out of this task's scope.
- A Spring wiring class building `Web3j`/`EthereumAdapter` instance(s) — one per configured Ethereum
  `ProviderEntry` — including `ProviderEntry.timeoutSeconds` applied to the underlying HTTP transport
  (amendment #9).

**Out:**
- `TronAdapter` (task 7).
- Any change to `ChainAdapter`, `Chain`, `adapter/model/*`, `ProviderProperties` (all frozen from
  earlier tasks) — consumed, not modified.
- Defensive EIP-55 address (re-)validation inside `EthereumAdapter` — trusts the caller.
- Native-ETH-transfer watching in `subscribeAddress` — platform premise is stablecoins only.
- Retry/backoff logic for transient RPC failures (amendment #10) — **explicitly, by design**: the
  2-of-3 quorum model already absorbs one provider's transient failure without needing adapter-level
  retry; repeated failures are task 10's (provider-health) job to detect, not this task's to retry
  around.
- Header-based provider credential injection, non-URL-templated auth schemes, and the specific real
  provider vendor names (O1/Q1) — out of reach until Q1 resolves.

## Business Rules

- **R6.** `getFinalityStatus` reports the real `finalized`-tagged block number
  (`DefaultBlockParameterName.FINALIZED`), never a confirmation-count approximation.

## Locked Decisions

- **L4.** `getFinalityStatus` returns raw data only. **Amendment #3: for a transaction not found on
  chain, `getFinalityStatus` throws** (an `IllegalStateException`-family exception) — not a new
  decision, this applies `ChainAdapter`'s own class Javadoc (T05 Phase 9: "assumes the caller already
  knows the transaction exists... calling it for one is a caller error") concretely for the first
  time.
- **L7.** `getTokenInfo` returns whatever `symbol()`/`decimals()` the contract reports — no allowlist
  awareness.
- **L13.** No credential committed; `apiKeySecretName` names an environment variable, resolved at
  wiring time only.
- **L14/L15.** `EthereumAdapter` implements the plain `ChainAdapter` interface with no extra
  capability; lives under `adapter/eth/`.

### Amendments folded in from Phase 3 (Kimi) design challenge, human-approved 2026-09-03

1. **Credential attachment via URL templating** — see Scope/In. Resolves the biggest real-deployment
   gap Kimi identified (a resolved credential with nowhere documented to go).
2. **`getTx` for a pending/unmined transaction (no receipt, no `blockNumber`) returns
   `TxResult(exists=false, ...)`** — identical to a truly not-found transaction. This service only
   reports mined transactions; mempool visibility is unreliable across providers and is not a quorum-
   worthy signal. Mempool-awareness, if ever needed, belongs to a higher layer, not `getTx`.
3. **`getFinalityStatus` throws for a not-found transaction** — see Locked Decisions L4 above.
4. **`subscribeAddress`'s polling cursor starts at the chain's `LATEST` block number at the moment
   `subscribeAddress` is called** — never genesis/block 0, which would trigger a massive backlog scan.
   **The scheduler uses fixed-delay execution** (each poll's next run is scheduled only after the
   current one completes) — never fixed-rate, which could allow overlapping polls and duplicate/
   out-of-order log delivery. Matches `OutboxRelay`'s own established fixed-delay precedent (T04).
5. **The polling scheduler is virtual-thread-backed**:
   `Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory())`, removing any ambiguity about
   whether this is "the watcher layer" agents.md's virtual-thread rule names — it demonstrably is,
   given `subscribeAddress` is literally address-watching.
6. **`confirmations = currentBlockNumber - txBlockNumber + 1`** — not a bare difference. A
   transaction included in the current latest block has **1** confirmation, not 0, matching standard
   Ethereum convention and avoiding a "0 confirmations but already mined" contradiction that would
   confuse the Payment Service's own display of `chain.tx.confirmed` (R9).
7. **`fromAddress` and `toAddress` are both sourced from the ERC-20 `Transfer` event log's topics —
   not the transaction's own raw `from` field.** Resolved differently than Kimi's literal suggestion
   (which proposed tx-level `from`, log-level `to`): for a DEX router or smart-contract-wallet-
   initiated transfer, `tx.from` is the router/wallet contract's address, not the actual token
   sender — useless, even misleading, for R17's address-poisoning attribution, whose entire purpose
   is identifying the real counterparty a payer's wallet would show. Sourcing both fields
   consistently from the log (which represents the actual token movement) is both more correct for
   this system's specific purpose and simpler (one source of truth for both fields, not a mix of
   transaction-level and log-level data).
8. Required Tests expanded accordingly — see below.
9. **`ProviderEntry.timeoutSeconds` is applied to the underlying HTTP transport's connect/read
   timeouts** (an `OkHttpClient` built by the wiring class and passed into `HttpService`) — a shipped
   config field that previously had no wiring effect now has one.
10. **No retry/backoff policy in `EthereumAdapter` itself, by design** — documented, not a gap: the
    quorum model (L1) is architecturally designed to tolerate one provider's transient failure without
    needing adapter-level retry; provider-health tracking (task 10) is the correct layer to detect and
    react to repeated failures.

## Dependencies

- `org.web3j:core:6.0.0` — `Web3j`, `HttpService`, `DefaultBlockParameterName.{FINALIZED,LATEST}`,
  manual ABI encode/decode (`FunctionEncoder`/`FunctionReturnDecoder`).
- `ProviderProperties.ProviderEntry` — `url` (may contain `{apiKey}`), `apiKeySecretName`,
  `timeoutSeconds`.
- Spring `Environment` — credential resolution.
- `ScheduledExecutorService`, constructor-injected, virtual-thread-backed (amendment #5).
- `okhttp3.OkHttpClient` (already a transitive dependency of `web3j:core`) — timeout configuration
  (amendment #9).
- New scalar config: `themistra.crypto.adapter.ethereum.poll-interval-ms`.

## Inputs / Outputs / State Changes

Inputs: `ProviderProperties`' Ethereum entries at wiring time; method calls from future callers
(quorum/watcher, no current callers in this task's own scope). Outputs: `TxResult`/`TokenInfo`/
`FinalityStatus` per contract; `ObservationSink.onObservation(TxResult)` pushes from the poll loop.
State: in-memory "last block scanned" cursor per active `Subscription`, initialized to `LATEST`
(amendment #4), held only for that subscription's lifetime.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java`
  ```java
  public class EthereumAdapter implements ChainAdapter {
      public EthereumAdapter(Web3j web3j, String providerName, ScheduledExecutorService scheduler,
                              Duration pollInterval);

      @Override public Chain chain(); // Chain.ETHEREUM

      @Override public TxResult getTx(String txHash);
          // exists=false (not throw) if not found OR pending/unmined (amendment #2).
          // confirmations = currentBlock - txBlock + 1 (amendment #6).
          // fromAddress/toAddress from the Transfer log's topics when present (amendment #7);
          // else tokenContractAddress=null, amount = native tx value.

      @Override public TokenInfo getTokenInfo(String contractAddress);
          // manual ABI-encoded symbol()/decimals() calls.

      @Override public Subscription subscribeAddress(String address, ObservationSink sink);
          // cursor = LATEST at call time (amendment #4); fixed-delay poll (amendment #4) on the
          // virtual-thread scheduler (amendment #5); eth_getLogs, Transfer topic, recipient-only
          // filter, no contract-address filter; each new match -> getTx(logTxHash) -> sink.

      @Override public FinalityStatus getFinalityStatus(String txHash);
          // throws if not found (amendment #3); else txBlockNumber/currentBlockNumber(LATEST)/
          // finalizedBlockNumber(FINALIZED) (R6).
  }
  ```
- Spring `@Configuration` wiring class (exact name Phase 5) — builds `Web3j` per Ethereum
  `ProviderEntry`: resolves `apiKeySecretName` via `Environment`, substitutes into `url`'s `{apiKey}`
  placeholder, configures `OkHttpClient` timeouts from `timeoutSeconds` (amendment #9), constructs
  the virtual-thread-backed `ScheduledExecutorService` (amendment #5).

## Files to Modify

- `services/crypto/src/main/resources/application.properties` — add
  `themistra.crypto.adapter.ethereum.poll-interval-ms`.

## Files NOT to Modify

- `adapter/ChainAdapter.java`, `adapter/Chain.java`, `adapter/model/*.java`, `adapter/ObservationSink.java`,
  `adapter/FakeChainAdapter.java` (T05) — frozen.
- `common/config/ProviderProperties.java` (T03) — consumed, not modified.
- `services/crypto/pom.xml` — no new dependency.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R6, L4).** `getFinalityStatus` uses the `FINALIZED` tag; throws for a not-found tx
  (amendment #3).
- **AC2.** `getTx` returns `exists=false` for not-found **and** pending/unmined (amendment #2); a
  genuine transport error propagates unchecked.
- **AC3 (L7).** `getTokenInfo` keyed by `contractAddress` alone.
- **AC4.** Provider URL/credential come from config via `{apiKey}` substitution (amendment #1); no
  hardcoded endpoint or key.
- **AC5.** No unit test makes a real network call.
- **AC6.** The Transfer-log poll filters by recipient topic only, no contract-address filter.
- **AC7.** `Subscription.cancel()` stops the scheduled poll.
- **AC8 (amendment #4).** Polling cursor initializes to `LATEST`, not genesis; scheduling is
  fixed-delay.
- **AC9 (amendment #6).** `confirmations` for a tx in the current latest block equals `1`, not `0`.
- **AC10 (amendment #7).** `fromAddress`/`toAddress` reflect the Transfer log's topics, not the raw
  transaction `from` field, for ERC-20 transfers.
- **AC11 (amendment #9).** The constructed HTTP transport's timeout equals the configured
  `timeoutSeconds`.

## Required Tests

- `getFinalityStatus`: `FINALIZED`/`LATEST` tags used correctly (AC1); throws for not-found (AC1,
  amendment #3).
- `getTx`: `exists=false` for not-found (AC2); `exists=false` for pending/no-receipt (AC2, amendment
  #2); fully-populated `TxResult` for a found ERC-20 transfer with `fromAddress`/`toAddress` from the
  log (AC10); native-value transfer with no Transfer log returns `tokenContractAddress=null` and the
  native value; `confirmations` for `txBlock == currentBlock` equals `1` (AC9); a mocked `Web3j` I/O
  exception propagates unchecked (AC2).
- `getTokenInfo`: decodes a mocked ABI response correctly (AC3).
- Wiring: URL/credential substitution reaches the constructed client (AC4); `HttpService`'s timeout
  equals `timeoutSeconds` (AC11).
- `subscribeAddress`: log-filter has no contract-address restriction (AC6); cursor starts at `LATEST`
  (AC8); cancelling stops further polling (AC7).

## Constraints

- **Module boundaries (L15):** `EthereumAdapter` under `adapter/eth/`; wiring under `common/` or
  `adapter/eth/` (Phase 5 to finalize).
- **Thread-safety:** polling runs on a virtual-thread-backed scheduler (amendment #5); `sink.onObservation(...)`
  may be invoked off the caller's thread.
- **Money (agents.md):** ERC-20 amount decoded as `BigInteger` → `BigDecimal`, base units, never
  scaled by `decimals` at this layer.
- **Secrets:** credential values never logged; `apiKeySecretName` (the reference) may be.
- **Reliability:** no retry/backoff in this task (amendment #10, documented, not a gap).

## Open Questions

No blockers.
