# crypto · T06 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java`
2. `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfig.java`
3. `services/crypto/src/test/java/com/themistra/crypto/adapter/eth/EthereumAdapterTest.java`
4. `services/crypto/src/test/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfigTest.java`

## Files to modify

1. `services/crypto/src/main/resources/application.properties` — add
   `themistra.crypto.adapter.ethereum.poll-interval-ms` (default e.g. `15000`).

No files outside this list.

## Public methods (signatures)

**`EthereumAdapter`**
```java
public class EthereumAdapter implements ChainAdapter {
    private static final String TRANSFER_EVENT_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"; // keccak256("Transfer(address,address,uint256)")

    public EthereumAdapter(Web3j web3j, String providerName, ScheduledExecutorService scheduler,
                            Duration pollInterval);

    @Override public Chain chain(); // Chain.ETHEREUM

    @Override public TxResult getTx(String txHash);
    @Override public TokenInfo getTokenInfo(String contractAddress);
    @Override public Subscription subscribeAddress(String address, ObservationSink sink);
    @Override public FinalityStatus getFinalityStatus(String txHash);
}
```

**`EthereumAdapterConfig`**
```java
@Configuration
public class EthereumAdapterConfig {
    @Bean
    public List<EthereumAdapter> ethereumAdapters(
            ProviderProperties providerProperties, Environment environment,
            @Value("${themistra.crypto.adapter.ethereum.poll-interval-ms}") long pollIntervalMs);
}
```
Builds one `EthereumAdapter` per `ProviderProperties` chain entry whose `chain()` equals
`"ETHEREUM"`, iterating its `providers()` list — matches `ChainAdapter`'s own "each provider is one
instance" framing and the two Ethereum entries already fixture'd in `application.properties` (T03).

## Private methods

**`EthereumAdapter`**:
- `private TxResult buildTxResult(String txHash, Transaction tx, TransactionReceipt receipt, BigInteger currentBlock)`
  — assembles the record; `exists=false` short-circuit callers use before this ever runs.
- `private Optional<Log> findTransferLog(TransactionReceipt receipt)` — scans
  `receipt.getLogs()` for one whose `topics.get(0)` equals `TRANSFER_EVENT_TOPIC`.
- `private TokenInfo callErc20Metadata(String contractAddress)` — two manual ABI `eth_call`s
  (`symbol()`, `decimals()`) via `FunctionEncoder`/`FunctionReturnDecoder`.
- `private void pollOnce(String address, ObservationSink sink, AtomicLong lastScannedBlock)` —
  builds an `EthFilter` for `[fromBlock=lastScannedBlock+1, toBlock=LATEST]`, topics
  `[TRANSFER_EVENT_TOPIC, null, <address padded to 32 bytes>]`, **no contract address restriction**
  (AC6); for each returned log, calls `getTx(log.getTransactionHash())` and pushes to `sink`; advances
  `lastScannedBlock`.

**`EthereumAdapterConfig`**:
- `private EthereumAdapter buildAdapter(ProviderProperties.ProviderEntry entry, Environment environment, long pollIntervalMs)`
  — resolves the URL, builds the `OkHttpClient`/`HttpService`/`Web3j`, constructs the
  virtual-thread-backed scheduler, returns a new `EthereumAdapter`.
- `private String resolveUrl(ProviderProperties.ProviderEntry entry, Environment environment)` —
  **non-throwing**: if `entry.url()` contains the literal token `{apiKey}` AND
  `environment.getProperty(entry.apiKeySecretName())` resolves a value, substitutes it; otherwise
  returns `entry.url()` unchanged. This is deliberate, not an oversight — it's what keeps `local`
  boot safe: T03's existing local fixture URLs (`http://localhost:9901/fake-eth-a`) contain no
  `{apiKey}` token at all, so no substitution is ever attempted for them, and construction never
  fails at wiring time regardless of whether a credential resolves. `Web3j.build(...)`/`HttpService`
  construction is itself lazy (no network call until a method is invoked), so an unreachable/fake URL
  is harmless at boot — this task has no caller yet to invoke anything against it.

## Entities / Repositories / Services used

None — no persistence.

## Unit tests required

Traced to the frozen brief's Required Tests, using Mockito to mock `Web3j` and its request/response
chain (`Request<?, T>` objects) — never a real network call (AC5):

**`EthereumAdapterTest`**:
- `getFinalityStatus`:
  - `usesFinalizedAndLatestBlockTagsNotAConfirmationCount` — mocked `Web3j` distinguishes calls by
    block-tag argument; asserts both are used (AC1).
  - `throwsForANotFoundTransaction` (amendment #3, AC1).
- `getTx`:
  - `returnsExistsFalseForANotFoundTransaction` (AC2).
  - `returnsExistsFalseForAPendingUnminedTransaction` (no receipt / no block number — amendment #2,
    AC2).
  - `returnsFullyPopulatedResultForAFoundErc20TransferWithAddressesFromTheTransferLog` (AC10 —
    asserts `fromAddress`/`toAddress` equal the log's topics, not the mocked transaction's own `from`
    field, which is deliberately set to a *different* value in this test to prove the source).
  - `returnsNativeValueWithNullTokenContractWhenNoTransferLogIsPresent`.
  - `confirmationsEqualsOneWhenTxBlockEqualsCurrentBlock` (amendment #6, AC9).
  - `propagatesAMockedIoExceptionUnchecked` (AC2).
- `getTokenInfo`:
  - `decodesSymbolAndDecimalsFromAMockedAbiResponse` (AC3).
- `subscribeAddress`:
  - `logFilterHasNoContractAddressRestrictionOnlyTheRecipientTopic` (AC6) — captures the constructed
    `EthFilter` argument and asserts its address list is empty/unset.
  - `cursorInitializesToLatestNotGenesis` (amendment #4, AC8).
  - `cancellingTheSubscriptionStopsFurtherPolling` (AC7) — verifies the scheduled task is cancelled,
    no further `ethGetLogs` calls occur.

**`EthereumAdapterConfigTest`**:
- `substitutesResolvedCredentialIntoAUrlContainingThePlaceholder` (AC4).
- `leavesUrlUnchangedWhenNoPlaceholderIsPresent` — proves `local`'s fixture URLs are unaffected.
- `leavesPlaceholderUnsubstitutedWhenTheEnvironmentValueIsAbsent` — proves no exception is thrown at
  wiring time.
- `configuresHttpClientTimeoutFromProviderEntryTimeoutSeconds` (AC11).
- `buildsOneAdapterPerConfiguredEthereumProviderEntry` — with the real (`local`-profile) T03 fixture
  values (two Ethereum entries), asserts exactly two `EthereumAdapter`s are produced.

## Execution order

1. `application.properties` — add the poll-interval property first.
2. `EthereumAdapter` — depends only on already-frozen T05 types + `web3j:core`.
3. `EthereumAdapterConfig` — depends on `EthereumAdapter` (2) and `ProviderProperties` (T03,
   unmodified).
4. `EthereumAdapterTest` — depends on step 2.
5. `EthereumAdapterConfigTest` — depends on step 3.
6. `mvn -pl services/crypto -am compile / test-compile / test` — full verification; no Docker
   dependency (mocked `Web3j`, no persistence, no Testcontainers).
