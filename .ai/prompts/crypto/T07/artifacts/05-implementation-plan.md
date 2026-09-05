# crypto · T07 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

All trident types referenced below were confirmed present, with the exact signatures shown, via
direct `javap`/class inspection of `trident-1.0.0.jar` (no sources jar is available locally) — the
same discipline this session used for `web3j` in T06. One item remains genuinely unconfirmed and is
called out explicitly rather than guessed: whether `getTransactionById` signals "not found" via an
empty/default `Transaction` or via `IllegalException` — see Open Items below.

## Files to create

1. `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapter.java`
2. `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapterConfig.java`
3. `services/crypto/src/test/java/com/themistra/crypto/adapter/tron/TronAdapterTest.java`
4. `services/crypto/src/test/java/com/themistra/crypto/adapter/tron/TronAdapterConfigTest.java`

## Files to modify

1. `services/crypto/src/main/resources/application.properties` — add
   `themistra.crypto.adapter.tron.poll-interval-ms` (default e.g. `3000`, matching Tron's ~3s block
   time rather than reusing Ethereum's `15000` default — a poll interval much longer than the block
   time would defeat the per-block catch-up cap's purpose, amendment #5).

No files outside this list.

## Public methods (signatures)

**`TronAdapter`**
```java
public class TronAdapter implements ChainAdapter {
    private static final String TRANSFER_EVENT_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"; // same TVM/EVM
            // keccak256("Transfer(address,address,uint256)") - Tron's TVM is EVM-bytecode-compatible,
            // so a ported TRC-20 contract emits the identical event signature hash (reused from
            // EthereumAdapter, not re-derived).
    private static final int MAX_BLOCKS_PER_POLL = 50; // amendment #5 catch-up cap; exact value TBD
            // against Tron's ~3s block time and the configured poll interval - placeholder here,
            // final value decided in Phase 6 once both are fixed together.

    public TronAdapter(ApiWrapper apiWrapper, String providerName, ScheduledExecutorService scheduler,
                        Duration pollInterval);

    @Override public Chain chain(); // Chain.TRON

    @Override public TxResult getTx(String txHash);
    @Override public TokenInfo getTokenInfo(String contractAddress);
    @Override public Subscription subscribeAddress(String address, ObservationSink sink);
    @Override public FinalityStatus getFinalityStatus(String txHash);

    @Override public void close(); // AutoCloseable, mirroring EthereumAdapter - shuts down the
            // scheduler; ApiWrapper's own close/shutdown method is confirmed absent from its public
            // surface (not listed in the inspected method set) - Phase 6 to confirm whether
            // ManagedChannel shutdown needs to be reached via a different trident accessor, or
            // whether ApiWrapper has no explicit lifecycle to close at all.
}
```

**`TronAdapterConfig`**
```java
@Configuration
public class TronAdapterConfig {
    @Bean
    public List<TronAdapter> tronAdapters(
            ProviderProperties providerProperties, Environment environment,
            @Value("${themistra.crypto.adapter.tron.poll-interval-ms}") long pollIntervalMs);
}
```
Builds one `TronAdapter` per `ProviderProperties` chain entry whose `chain()` equals `"TRON"`,
iterating its `providers()` list — mirrors `EthereumAdapterConfig`'s exact precedent. Per amendment
#9, `ApiWrapperBuilder.withApiKey(...)` is only called when the resolved credential is non-blank;
`.withTimeout(...)` is always called from `entry.timeoutSeconds()` (amendment #8, unit conversion
confirmed empirically in Phase 6 — the builder's internal field is named `timeoutMs`, strongly
suggesting milliseconds, but not proven from the signature alone).

## Private methods

**`TronAdapter`**:
- `private Transaction fetchTransaction(String txHash)` — calls `apiWrapper.getTransactionById(txHash)`
  wrapped in try/catch for `IllegalException`, rethrown as this adapter's unchecked transport-failure
  exception (matching `EthereumAdapter.fetchTransaction`'s pattern). Returns `null` for a detected
  "not found" case (see Open Items — exact detection mechanism confirmed in Phase 6).
- `private TransactionInfo fetchTransactionInfo(String txHash)` — calls
  `apiWrapper.getTransactionInfoById(txHash)`, same exception-wrapping pattern; returns `null` on a
  detected empty/missing result (amendment #7's mined-tx/null-`TransactionInfo` case).
- `private Optional<TransactionInfo.Log> findTransferLog(TransactionInfo info)` — scans
  `info.getLogList()` for one whose `getTopics(0)` (decoded to hex) equals `TRANSFER_EVENT_TOPIC` —
  direct structural mirror of `EthereumAdapter.findTransferLog`.
- `private TxResult buildTxResultFromTransferLog(String txHash, TransactionInfo.Log log, long
  currentBlock)` — decodes `from`/`to` from `log.getTopics(1)`/`getTopics(2)`, amount from
  `log.getData()`, mirroring `EthereumAdapter`'s Transfer-log decode path.
- `private TxResult buildTxResultFromNativeTransfer(String txHash, Transaction tx, long txBlock, long
  currentBlock)` — amendment #2: unpacks `tx.getRawData().getContract(0).getParameter()` as
  `Contract.TransferContract` (only when `getType() == ContractType.TransferContract`, confirmed
  present via inspection), reading `ownerAddress`/`toAddress`/`amount` (SUN units).
- `private int computeConfirmations(long currentBlock, long txBlock)` — same `+1`, negative-guard
  logic as `EthereumAdapter.computeConfirmations`, reused verbatim in spirit (not literally shared code
  — no shared base class exists between the two adapters per L15/L14, each adapter is a standalone
  `ChainAdapter` implementation).
- `private void pollOnce(String address, ObservationSink sink, AtomicLong lastScannedBlock)` — computes
  `fromBlock`/`toBlock` (capped at `MAX_BLOCKS_PER_POLL`, amendment #5), calls
  `apiWrapper.getTransactionInfoByBlockNum(blockNum)` for each block in range, scans each
  transaction's logs for the Transfer topic + recipient-topic match, pushes matched observations to
  `sink` directly from the matched log (mirroring `EthereumAdapter`'s Phase 9 Finding 1 fix — never
  round-trips through `getTx` again).
- `private String topicForAddress(String base58Address)` — amendment #1:
  `Base58.decode(base58Address)` → strip the leading `0x41` prefix byte → left-pad the remaining 20
  bytes to a 32-byte hex topic string.
- `private byte[] toRawAddress(String base58Address)` — `Base58.decode(...)`, used wherever trident's
  own calls (`getContract`, `Trc20Contract` construction) need a raw/hex form instead of Base58Check;
  exact target encoding confirmed in Phase 6 once `Trc20Contract`'s actual construction path is
  exercised (amendment #3 pins the *input* encoding to `TronAdapter.getTokenInfo`, not necessarily
  trident's own internal expectation).

**`TronAdapterConfig`**: mirrors `EthereumAdapterConfig`'s private `buildAdapter`/credential-resolution
helpers, adapted for `ApiWrapperBuilder` instead of `HttpService`/`OkHttpClient`.

## Entities / Repositories / Services used

None — identical to T06: `TronAdapter` touches no persistence, no repository, no Spring service layer.
It returns `TxResult`/`TokenInfo`/`FinalityStatus` (T05, unchanged) and pushes to `ObservationSink`
(T05, unchanged).

## Unit / integration tests required

Traced to the frozen brief's Required Tests, using Mockito to mock `ApiWrapper` (AC6) — no real
gRPC/network call:

**`TronAdapterTest`**:
- `getFinalityStatus`:
  - usesSolidityAndCurrentBlockQueriesNotAConfirmationCount (AC1).
  - throwsForANotFoundTransaction / throwsForAPendingUnminedTransaction.
  - throwsWhenSolidifiedBlockExceedsCurrentBlock (AC1, amendment #4 — direct mirror of
    `EthereumAdapter`'s Phase 11 Gap 10 test).
- `getTx`:
  - returnsExistsFalseForANotFoundTransaction / ForAPendingUnminedTransaction (AC2).
  - returnsFullyPopulatedResultForAFoundTrc20TransferWithAddressesFromTheTransferLog (AC10) —
    deliberately different transaction-level owner vs. log-level from, proving the source, mirroring
    T06's own proof pattern.
  - returnsNativeTrxValueWhenNoTransferLogIsPresent (AC10, amendment #2) — asserts the value comes
    from `Contract.TransferContract`, scoped to a plain-TRX contract type.
  - fallsBackToNativeValueWhenTransactionInfoIsNullButTransactionIsMined (amendment #7 — direct mirror
    of T06's Phase 11 Gap 4 test).
  - confirmationsEqualsOneWhenTxBlockEqualsCurrentBlock.
  - throwsWhenCurrentBlockIsEarlierThanTxBlock.
  - propagatesAMockedTransportExceptionUnchecked (AC2).
- `getTokenInfo`:
  - decodesSymbolAndDecimalsFromAMockedTrc20ContractResponse (AC3), Base58Check input (amendment #3).
- `subscribeAddress`:
  - base58AddressProducesTheExpectedThirtyTwoByteTopic (AC7, amendment #1) — pins the exact conversion
    with a known address/topic pair, not just "doesn't throw."
  - blockScanPollHasNoContractAddressRestrictionOnlyTheRecipientTopic (AC7).
  - cursorInitializesToCurrentBlockNotGenesis (AC9).
  - cancellingTheSubscriptionStopsFurtherPolling (AC8).
  - pollBuildsObservationsDirectlyFromTheMatchedLogNotViaGetTx (mirrors T06's Phase 9 Finding 1 proof).
  - catchUpIsCappedWhenManyBlocksArePending (AC9, amendment #5) — asserts at most
    `MAX_BLOCKS_PER_POLL` blocks are scanned in one tick even when far more are pending, and the
    cursor reflects partial progress (not a jump straight to the chain head).
- `close`:
  - closeShutsDownTheScheduler (exact `ApiWrapper` lifecycle interaction TBD Phase 6 per the Open Item
    above).

**`TronAdapterConfigTest`** (mirrors `EthereumAdapterConfigTest`'s structure exactly):
- credentialReachesApiWrapperBuilderWithApiKey (AC5).
- credentialIsSkippedWhenTheResolvedValueIsNullOrBlank (AC5, amendment #9).
- timeoutSecondsReachesApiWrapperBuilderWithTimeout (AC11).
- buildsOneAdapterPerConfiguredTronProviderEntry.
- buildsNoAdaptersWhenNoTronChainIsConfigured (mirroring T06's own Phase 11 Gap 12 addition, included
  from the start here rather than added after a later review pass).

## Execution order

1. `application.properties` — add the Tron poll-interval property first.
2. `TronAdapter` — depends only on already-frozen T05 types + `trident:1.0.0`.
3. `TronAdapterConfig` — depends on `TronAdapter` (2) and `ProviderProperties` (T03, unmodified).
4. `TronAdapterTest` — depends on step 2. **Empirically resolve the Open Items below while writing
   this** (mirrors how T06's own not-found/error-signaling details were nailed down during Phase 6
   implementation, not fully pre-solved in Phase 5).
5. `TronAdapterConfigTest` — depends on step 3.
6. `mvn -pl services/crypto -am compile / test-compile / test` — full verification; no Docker
   dependency (mocked `ApiWrapper`, no persistence, no Testcontainers).

## Open Items carried into Phase 6 (not blockers, resolved empirically during implementation)

1. **Not-found signaling for `getTransactionById`/`getTransactionInfoById`.** `IllegalException` is a
   checked exception with a generic "Query failed" message (confirmed via inspection) — plausibly a
   general transport/parameter-error signal, not specifically "not found." The likely mechanism,
   consistent with protobuf/gRPC convention, is that a nonexistent transaction returns a
   default/empty `Transaction`/`TransactionInfo` instance (`getRawData().getContractCount() == 0` is
   the concrete check planned) rather than throwing. Phase 6 must confirm this empirically (unit-test
   against a real trident response shape, or by writing the adapter and testing behavior against the
   library's actual runtime output) before finalizing `fetchTransaction`/`fetchTransactionInfo`.
2. **`ApiWrapper`'s own resource-lifecycle method.** No `close()`/`shutdown()`-named method appeared in
   the inspected public method list. Phase 6 must confirm whether `ApiWrapper` needs explicit channel
   shutdown at all (possible it manages this internally, or exposes it under a name this session's
   keyword-filtered inspection missed) before finalizing `TronAdapter.close()`.
3. **`Trc20Contract`'s exact construction path from a bare contract address.** `Trc20Contract`'s
   constructor takes `(Contract, String ownerAddress, ApiWrapper)`, and `ApiWrapper.getContract(String)`
   returns the first `Contract` argument — but what a read-only adapter should pass as `ownerAddress`
   (this service holds no Tron account of its own) is unconfirmed. Phase 6 must resolve whether an
   empty/zero address is accepted for a pure read (`symbol()`/`decimals()` are constant calls, not
   state-changing, so this is plausible but unverified).
4. **Amendment #12 (single-URL-for-two-gRPC-endpoints, carried from Phase 4).** Still open; Phase 6
   must attempt the same-URL approach and record whether `ApiWrapperBuilder`'s 3-arg constructor
   tolerates it.
