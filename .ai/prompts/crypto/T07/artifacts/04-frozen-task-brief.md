# crypto · T07 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-03. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in.

## Task

Tron adapter. Implement `TronAdapter` (TronGrid / java-tron gRPC) against the same interface.

## Purpose

The second real `ChainAdapter` implementation, and the first task to talk to Tron. Has a direct
sibling precedent (`EthereumAdapter`/`EthereumAdapterConfig`, T06) to compare every design choice
against, and a materially richer client library (`trident`) than `web3j`'s raw JSON-RPC surface.

## Scope

**In:**
- `TronAdapter implements ChainAdapter` — all 5 methods, backed by a constructor-injected
  `org.tron.trident.core.ApiWrapper` (confirmed non-final, standard-Mockito-mockable — amendment #6).
- Credential attachment via `ApiWrapperBuilder.withApiKey(String)` — trident's own first-class
  mechanism (no URL-templating needed, unlike T06). **Amendment #9: if the resolved credential value
  is `null` or blank, `.withApiKey(...)` is skipped entirely** (the `ApiWrapper` is built without a
  key) — required so the `local`-profile Tron fixture (whose `apiKeySecretName` resolves to nothing
  locally) keeps working, since Tron's gRPC target has no `{apiKey}`-placeholder token to gate on the
  way T06's URL did.
- Finality (R7, L4): `ApiWrapper.getNowBlock()` (current) vs. `ApiWrapper.getNowBlockSolidity()`
  (solidified) — direct structural parallel to T06's `LATEST`/`FINALIZED`. **Amendment #4: a guard
  throws `IllegalStateException` if `finalizedBlockNumber > currentBlockNumber`**, mirroring
  `EthereumAdapter`'s own Phase 11 Gap 10 fix exactly — the two block queries are independent gRPC
  round trips subject to the same inconsistency risk.
- `getTx`: `ApiWrapper.getTransactionById(String, NodeType...)` + `getTransactionInfoById(String,
  NodeType...)` (block number + event logs, the receipt-equivalent). `fromAddress`/`toAddress` sourced
  from the TRC-20 Transfer log's topics (`TransactionInfo.Log.getTopics()`), not a raw transaction-level
  field — same reasoning as T06 amendment #7. **Amendment #7: if `getTransactionById` returns a mined
  transaction but `getTransactionInfoById` returns `null` or errors, treat it as `exists=true` with the
  native-TRX fallback (amendment #2) and no log-derived fields** — the direct Tron analogue of T06's
  Phase 11 Gap 4 (mined-tx/null-receipt) handling.
- **Amendment #2: the native-TRX fallback (no TRC-20 Transfer log present) reads
  `org.tron.trident.proto.Contract.TransferContract`'s `ownerAddress`/`toAddress`/`amount` fields**
  (confirmed present via inspection; `amount` is `long`, SUN units) — explicitly scoped to plain TRX
  transfers only, not TRC-10 or other Tron contract types.
- `getTokenInfo`: `org.tron.trident.core.contract.Trc20Contract.symbol()`/`.decimals()` — no manual ABI
  encoding needed (trident provides this directly). **Amendment #3: `contractAddress` is expected in
  Base58Check encoding** (matching user-facing/watch addresses); `TronAdapter` converts internally to
  whatever raw form `ApiWrapper.getContract(...)`/`Trc20Contract` needs.
- `subscribeAddress` transport (O2, **for Tron, this task only**): polling via block-by-block scan —
  `ApiWrapper.getTransactionInfoByBlockNum(long, NodeType...)` for each new block since the
  last-scanned cursor, filtering each transaction's logs for the TRC-20 Transfer topic + recipient.
  **Amendment #1: the watch address (Base58Check) is converted to the 32-byte topic filter value via
  `org.tron.trident.core.utils.Base58.decode(address)` → strip the leading `0x41` Tron
  address-prefix byte → left-pad the remaining 20 bytes to a 32-byte hex topic** — the direct Tron
  analogue of `EthereumAdapter.topicForAddress`, required because a Tron address is 21 raw bytes
  (prefix + 20-byte EVM-compatible body) while the TRC-20 event log topic is a plain 32-byte-padded
  20-byte EVM address, same as Ethereum's. **Amendment #5: the number of blocks scanned per poll tick
  is capped** (exact cap value, e.g. a small constant or config field, is Phase 5 design work); the
  adapter does not attempt unbounded catch-up in a single tick if the cursor falls far behind.
- `timeoutSeconds` mapped to `ApiWrapperBuilder.withTimeout(long)` (**amendment #8** — confirmed
  present via inspection; exact unit conversion, given the builder's internal field is named
  `timeoutMs`, is Phase 5 to confirm empirically, not guessed here).
- A Spring wiring class building `ApiWrapper`/`TronAdapter` instance(s) — one per configured TRON
  `ProviderEntry`, mirroring `EthereumAdapterConfig`'s precedent.

**Out:**
- `EthereumAdapter` (T06, shipped) — untouched.
- Any change to `ChainAdapter`, `Chain`, `adapter/model/*`, `ProviderProperties` (all frozen) —
  consumed, not modified.
- **Defensive Base58Check address (re-)validation inside `TronAdapter` — decision: not built here**
  (mirrors T06's EIP-55 deferral exactly). **Amendment #11: `TronAdapter` explicitly assumes every
  address it receives (via `subscribeAddress`/`getTokenInfo`) has already been validated by
  `AddressValidator` (task 12, not yet built).** An invalid address is a caller bug that may surface as
  an unchecked transport/gRPC exception from this adapter, not a clean validation failure — this
  assumption must be enforced by watcher/attest boundary tests in later tasks, never silently relied
  upon. trident's own `Base58` utility exists and is available to whichever task actually owns address
  validation; this task does not use it for that purpose.
- Any signing/write operation (`transfer`, `freezeBalance`, `voteWitness`, `signTransaction`, etc.) —
  `TronAdapter` is read-only, consistent with L11 (KMS-only signing, confined to the attest module).
- **TRC-10 tokens — amendment #10: explicitly out of scope.** `TronAdapter` detects and reports only
  TRC-20 Transfer events and native TRX transfers; a TRC-10 transfer is neither detected by
  `subscribeAddress`'s poll nor a valid input to `getTokenInfo`.
- Retry/backoff logic — same rationale as T06 amendment #10: the quorum model absorbs single-provider
  transient failure; provider-health tracking (task 10) is the correct layer.
- The specific real provider vendor names (O1/Q1) — this task builds against trident's provider-agnostic
  `ApiWrapper`, not hardcoded to TronGrid specifically.

## Business Rules

- **R7.** `getFinalityStatus` reports the real solidified block number (`getNowBlockSolidity()`),
  never a confirmation-count approximation.

## Locked Decisions

- **L4.** `getFinalityStatus` returns raw data only — `txBlockNumber`, `currentBlockNumber`
  (`getNowBlock()`), `finalizedBlockNumber` (`getNowBlockSolidity()`, guarded by amendment #4). No
  finality *decision* computed here — that's `TronFinalityPolicy`'s job (task 14).
- **L7.** `getTokenInfo` returns whatever `Trc20Contract.symbol()`/`.decimals()` report — no allowlist
  check.
- **L13.** No credential committed; `apiKeySecretName` resolved via `Environment` at wiring time only,
  passed to `ApiWrapperBuilder.withApiKey(...)` when non-blank (amendment #9).
- **L14/L15.** `TronAdapter` implements the plain `ChainAdapter` interface with no extra capability;
  lives under `adapter/tron/`.

## Amendments folded in from Phase 3 (Kimi) design challenge, human-approved 2026-09-03

All 12 Phase 3 findings accepted; see the numbered amendments (#1–#11) woven into Scope/Locked
Decisions above, plus #12 below (elevated to Open Questions, not resolved at this gate):

1. Base58Check-to-topic conversion mechanism specified (Kimi Issue 4).
2. Native-TRX fallback data source pinned to `Contract.TransferContract` (Kimi Issue 3).
3. `getTokenInfo` `contractAddress` encoding pinned to Base58Check (Kimi Issue 5).
4. Current-vs-solidity inconsistency guard added, mirroring T06's Phase 11 Gap 10 (Kimi Issue 10).
5. Per-block-scan catch-up cap added (Kimi Issue 6).
6. `ApiWrapper` mockability confirmed non-final via direct inspection — no wrapper interface needed
   (Kimi Issue 9).
7. Partial `getTx` failure (mined tx, null/erroring `TransactionInfo`) defined, mirroring T06's Phase
   11 Gap 4 (Kimi Issue 8).
8. `timeoutSeconds` → `ApiWrapperBuilder.withTimeout(long)` mapping specified (Kimi Issue 7).
9. Empty/unresolved `apiKeySecretName` handling: skip `.withApiKey(...)` when the resolved value is
   null/blank — **scope expanded beyond Kimi's literal ask** (which only named the empty-string case)
   because the same gap also applies to the null/unresolved case, which would otherwise break the
   `local`-profile Tron fixture (Kimi Issue 11).
10. TRC-10 explicitly excluded from scope (Kimi Issue 12).
11. Base58Check address-validation deferral made an explicit, documented caller-validates assumption
    rather than a silent one (Kimi Issue 2).
12. **Single `ProviderEntry.url` serving both `grpcEndpoint`/`grpcEndpointSolidity` — elevated to a
    formal Open Question (Kimi Issue 1), not resolved at this gate.** See Open Questions below.

**12 accepted, 0 rejected.**

## Dependencies

- `io.github.tronprotocol:trident:1.0.0` (present) — `ApiWrapper`/`ApiWrapperBuilder` (incl.
  `withApiKey`, `withTimeout`), `NodeType.{FULL_NODE,SOLIDITY_NODE}`, `Trc20Contract`,
  `Response.TransactionInfo`/`Log`, `Contract.TransferContract`, `utils.Base58` — all confirmed present
  and shaped as described via direct `javap`/class inspection, not assumed.
- `ProviderProperties.ProviderEntry` (T03) — `url`, `apiKeySecretName`, `timeoutSeconds`. Single-`url`
  vs. dual-gRPC-endpoint question remains open (amendment #12 / Open Questions).
- Spring `Environment` — credential resolution (established mechanism, T06).
- `ScheduledExecutorService`, constructor-injected, virtual-thread-backed — same as T06.
- Heavier transitive dependency surface than `web3j:core` — a `dependency:tree -Dverbose` conflict
  check is warranted once `ApiWrapper` is actually constructed in code (T06's Jackson-annotations
  lesson).

## Inputs / Outputs / State Changes

Inputs: `ProviderProperties`' TRON entries at wiring time; method calls from future callers (quorum/
watcher, no current callers in this task's own scope). Outputs: `TxResult`/`TokenInfo`/`FinalityStatus`
per contract; `ObservationSink.onObservation(TxResult)` pushes from the poll loop. State: in-memory
"last block scanned" cursor per active `Subscription`, initialized to the current block at subscribe
time, held only for that subscription's lifetime.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapter.java`
  ```java
  public class TronAdapter implements ChainAdapter {
      public TronAdapter(ApiWrapper apiWrapper, String providerName, ScheduledExecutorService scheduler,
                          Duration pollInterval);

      @Override public Chain chain(); // Chain.TRON

      @Override public TxResult getTx(String txHash);
          // exists=false (not throw) if not found OR pending/unmined.
          // fromAddress/toAddress from the TRC-20 Transfer log's topics when present;
          // else native-TRX fallback via Contract.TransferContract (amendment #2), scoped to plain
          // TRX only; mined-tx/null-TransactionInfo handled per amendment #7.

      @Override public TokenInfo getTokenInfo(String contractAddress);
          // Base58Check contractAddress (amendment #3) -> Trc20Contract.symbol()/decimals().

      @Override public Subscription subscribeAddress(String address, ObservationSink sink);
          // cursor = current block at call time; fixed-delay poll on the virtual-thread scheduler;
          // per-block scan via getTransactionInfoByBlockNum, capped catch-up (amendment #5);
          // Base58-to-topic conversion per amendment #1.

      @Override public FinalityStatus getFinalityStatus(String txHash);
          // throws if not found; else txBlockNumber/currentBlockNumber(getNowBlock)/
          // finalizedBlockNumber(getNowBlockSolidity), guarded per amendment #4 (R7).
  }
  ```
- Spring `@Configuration` wiring class (exact name Phase 5) — builds `ApiWrapper` per Tron
  `ProviderEntry`: resolves `apiKeySecretName` via `Environment`, calls `.withApiKey(...)` only when
  non-blank (amendment #9), calls `.withTimeout(...)` from `timeoutSeconds` (amendment #8), constructs
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
  computation; throws if `finalizedBlockNumber > currentBlockNumber` (amendment #4).
- **AC2 (ChainAdapter contract).** `getTx` for a transaction trident reports as not found returns
  `TxResult(exists=false, ...)`; a genuine transport/gRPC error propagates unchecked; mined-tx/null-
  `TransactionInfo` handled per amendment #7.
- **AC3 (L7, amendment #3).** `getTokenInfo` keyed by `contractAddress` (Base58Check) alone, via
  `Trc20Contract`.
- **AC4.** `chain()` returns `Chain.TRON`.
- **AC5 (amendment #9).** Provider endpoint/credential come from `ProviderProperties` via the wiring
  class, passed to `ApiWrapperBuilder`, not hardcoded; a null/blank resolved credential skips
  `.withApiKey(...)` rather than failing or attaching a nonsensical empty key.
- **AC6.** No unit test makes a real network/gRPC call.
- **AC7 (amendment #1).** The block-scan poll filters by recipient topic only, via the specified
  Base58-to-topic conversion.
- **AC8.** `Subscription.cancel()` stops the scheduled poll.
- **AC9 (amendment #5).** Polling cursor initializes to the current block at subscribe time, not block
  0; fixed-delay scheduling; catch-up per poll tick is capped.
- **AC10 (amendment #2).** `fromAddress`/`toAddress` reflect the Transfer log's topics for TRC-20
  transfers; the native-TRX fallback reads `Contract.TransferContract`, scoped to plain TRX only.
- **AC11 (amendment #8).** `timeoutSeconds` maps to `ApiWrapperBuilder.withTimeout(...)`.

## Required Tests

- `getFinalityStatus`: `getNowBlockSolidity()`/`getNowBlock()` used correctly (AC1); throws for
  not-found/unmined; throws when `finalizedBlockNumber > currentBlockNumber` (AC1, amendment #4).
- `getTx`: `exists=false` for not-found/pending (AC2); fully-populated `TxResult` for a found TRC-20
  transfer with `fromAddress`/`toAddress` from the log (AC10); native-TRX fallback via
  `TransferContract` when no Transfer log present (AC10); mined-tx/null-`TransactionInfo` handled per
  amendment #7; a mocked transport exception propagates unchecked.
- `getTokenInfo` decodes a mocked `Trc20Contract` response correctly, Base58Check input (AC3).
- `chain()` returns `Chain.TRON` (AC4).
- Wiring: credential reaches `ApiWrapperBuilder.withApiKey(...)` when present (AC5); is skipped when
  the resolved value is null/blank (AC5, amendment #9); `timeoutSeconds` reaches `.withTimeout(...)`
  (AC11).
- `subscribeAddress`: a known Base58Check address produces the expected 32-byte topic (AC7, amendment
  #1); block-scan poll has no contract-address restriction; cursor starts at the current block (AC9);
  cancelling stops further polling (AC8); catch-up is capped across multiple pending blocks (AC9,
  amendment #5).

## Constraints

- **Module boundaries (L15):** `TronAdapter` under `adapter/tron/`; wiring under `common/` or
  `adapter/tron/` (Phase 5 to finalize).
- **Thread-safety:** polling runs on a virtual-thread-backed scheduler; `sink.onObservation(...)` may
  be invoked off the caller's thread.
- **Money (agents.md):** TRC-20/TRX amounts decoded as `BigInteger`/`long` → `BigDecimal`, base units
  (SUN for native TRX), never scaled by `decimals` at this layer.
- **Secrets:** credential values never logged; `apiKeySecretName` (the reference) may be.
- **Reliability:** no retry/backoff in this task (mirrors T06 amendment #10).

## Open Questions

**One carried forward, not a blocker for building/testing this task with the `local`-profile fixture,
but a real risk for a genuine production deployment:**

1. **Single `ProviderEntry.url` serving both `grpcEndpoint` and `grpcEndpointSolidity` (amendment
   #12 / Kimi Issue 1).** `ApiWrapperBuilder` accepts these as separate parameters; `ProviderEntry` has
   only one `url` field. The provisional plan (same URL for both) has not been empirically validated
   against trident's actual channel-construction behavior or a real Tron provider's endpoint
   convention — both are Phase 5/6 work. If the same-URL approach proves insufficient, this becomes a
   `ProviderProperties` config-shape gap outside this task's authority to fix (frozen, T03) and must be
   escalated to the author, not silently worked around.
