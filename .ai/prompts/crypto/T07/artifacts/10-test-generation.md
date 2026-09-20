# crypto · T07 · Phase 10 — Test Generation

## Files created

- `services/crypto/src/test/java/com/themistra/crypto/adapter/tron/TronAdapterTest.java` — 24 tests.
  `ApiWrapper` and `ScheduledExecutorService` are both Mockito mocks; every protobuf response fixture
  (`TransactionInfo`, `Chain.Block`, `Chain.Transaction`, `Contract.TransferContract`,
  `TransactionInfoList`) is built via trident's own real builders, not mocked — no real gRPC call
  anywhere (AC6).
- `services/crypto/src/test/java/com/themistra/crypto/adapter/tron/TronAdapterConfigTest.java` — 7
  tests. Deliberately structured differently from `EthereumAdapterConfigTest` (full
  `ApplicationContextRunner`): `ApiWrapperBuilder.build()` eagerly constructs a real gRPC
  `ManagedChannel` (bytecode-confirmed, Phase 7/8) and `ApiWrapper` exposes no reflectable field for
  its applied timeout the way `HttpService`'s `OkHttpClient` did, so these tests intercept
  `ApiWrapperBuilder`'s construction via Mockito's `mockConstruction` and call
  `TronAdapterConfig.tronAdapters`/`.shutdown()` directly rather than through a Spring context — see
  the class Javadoc for the full reasoning. No real gRPC channel is ever built.

## Test-to-AC / Required-Tests mapping

Traced to the frozen brief's Required Tests, informed by the Phase 9 review-resolution fixes that
postdated the original Phase 5 plan:

| Plan item | Test method | Notes |
|---|---|---|
| `getFinalityStatus` uses solidity/current queries (AC1) | `usesSolidityAndCurrentBlockQueriesNotAConfirmationCount` | |
| `getFinalityStatus` throws for not-found (AC1) | `throwsForANotFoundOrPendingTransaction` | Deliberately one test, not two — see below |
| — (added, amendment #4) | `throwsWhenSolidifiedBlockExceedsCurrentBlock` | Phase 9 guard fix |
| `getTx` exists=false for not-found (AC2) | `getTxReturnsExistsFalseForANotFoundOrPendingTransaction` | Deliberately one test, not two — see below |
| `getTx` addresses from Transfer log (AC10) | `getTxReturnsFullyPopulatedResultForAFoundTrc20TransferWithAddressesFromTheTransferLog` | Distinct tx-level owner vs. log-level from, mirroring T06's proof pattern; also asserts `getTransactionById` is `never()` called |
| `getTx` native-TRX fallback (AC10, amendment #2) | `getTxReturnsNativeTrxValueWhenNoTransferLogIsPresent` | Real `TransferContract` fixture, unpacked via `Any.pack`/`.unpack` |
| — (added, amendment #10) | `getTxReportsExistenceOnlyForANonTransferContractType` | TRC-10/other contract type — existence only, no fact-bearing fields |
| — (added) | `getTxThrowsWhenTransactionInfoExistsButTransactionDoesNot` | The genuine-inconsistency branch |
| — (added, Phase 9 Kimi Issue 5) | `getTxThrowsWhenTransactionHasNoContracts` | Empty-contract-list guard |
| `getTx` confirmations=1 (AC9-equivalent) | `getTxConfirmationsEqualsOneWhenTxBlockEqualsCurrentBlock` | |
| — (added) | `getTxThrowsWhenCurrentBlockIsEarlierThanTxBlock` | |
| `getTx` propagates transport failure (AC2) | `getTxPropagatesATransportFailureUnchecked` | |
| — (added, Phase 9 Kimi Issue 3) | `getTxDoesNotMatchATransferLogWithFewerThanThreeTopics` | Proves the `findTransferLog` guard fix — falls back to native path instead of throwing |
| `getTokenInfo` decodes symbol/decimals (AC3) | `getTokenInfoDecodesSymbolAndDecimalsFromAMockedTrc20ContractResponse` | Real `Contract` built via its 6-arg constructor (bytecode-verified field mapping); ABI hex payloads reused from T06's own proven encoding |
| — (added, Phase 9 Kimi Issues 4+9) | `getTokenInfoThrowsWithContractContextWhenTheUnderlyingCallFails` | Proves the unified try/catch wrapping |
| `subscribeAddress` filter has no contract restriction (AC7) | `blockScanPollHasNoContractAddressRestrictionOnlyTheRecipientTopic` | Two different token contracts, same recipient — both must match |
| `subscribeAddress` Base58→topic conversion (AC7, amendment #1) | `logFilterBase58AddressProducesTheExpectedThirtyTwoByteTopic` | Pins the conversion formula in isolation |
| `subscribeAddress` cursor at current block (AC9) | `subscribeAddressCursorInitializesToCurrentBlockNotGenesis` | |
| `subscribeAddress` cancel stops polling (AC8) | `cancellingTheSubscriptionStopsFurtherPolling` | |
| — (added) | `pollBuildsObservationsDirectlyFromTheMatchedLogNotViaGetTx` | Mirrors T06's Phase 9 Finding 1 proof — `getTransactionById`/`getTransactionInfoById` both `never()` called during a poll |
| — (added, amendment #5) | `catchUpIsCappedWhenManyBlocksArePending` | 400 blocks pending, only 50 scanned |
| — (added, Phase 9 Kimi Issues 2/7) | `pollDoesNotPropagateAnUnexpectedFailure` | Proves the exception boundary — verified via negative-proof mutation (below) |
| — (added, Phase 9 Kimi Issue 6) | `nullResponseFromTransactionInfoByBlockNumThrowsANamedExceptionCaughtByThePollBoundary` | |
| — (added, Phase 9 Kimi Issue 10) | `closeShutsDownTheSchedulerBeforeClosingApiWrapper` | Ordering, via `Mockito.inOrder` |
| `TronAdapterConfig` credential reaches builder (AC5) | `credentialReachesApiWrapperBuilderWithApiKey` | |
| — (added, amendment #9) | `credentialIsSkippedWhenTheResolvedValueIsNullOrBlank` | |
| `TronAdapterConfig` timeout reaches builder (AC11, amendment #8) | `timeoutSecondsReachesApiWrapperBuilderWithTimeout` | Confirms the seconds→millis conversion against the bytecode-verified `TimeUnit.MILLISECONDS` unit |
| — (added, amendment #12) | `grpcEndpointSolidityReceivesTheSameUrlAsGrpcEndpoint` | Pins the provisional same-URL plan |
| `TronAdapterConfig` one adapter per entry | `buildsOneAdapterPerConfiguredTronProviderEntry` | Two TRON + one ETHEREUM entry, ETHEREUM filtered out |
| `TronAdapterConfig` no adapters when no TRON chain | `buildsNoAdaptersWhenNoTronChainIsConfigured` | |
| — (added) | `shutdownClosesEveryBuiltAdapterAndItsScheduler` | Direct `config.shutdown()` call — see class Javadoc for why this isn't exercised through a full Spring `@PreDestroy` lifecycle here |

31 tests total (24 + 7). Every Required-Tests-section item is covered, either as named or as a
documented, deliberate collapse/addition.

## Deliberate test-count deviations from the Ethereum precedent (explained, not hidden)

- **No separate "pending" test for `getFinalityStatus`/`getTx`.** T06 had two distinct tests
  (`ThrowsForANotFoundTransaction` / `ThrowsForAPendingUnminedTransaction`) because `web3j`'s
  `Transaction.getBlockNumber()` gave Ethereum a genuinely separate "found but not yet mined" signal.
  Phase 6 established that trident's `Chain.Transaction` has no equivalent field for Tron — both cases
  collapse to the identical `IllegalException("TransactionInfo not found: ...")` catch. Writing two
  structurally-identical tests differing only in name would be a vacuous distinction; one test
  (`throwsForANotFoundOrPendingTransaction` / `getTxReturnsExistsFalseForANotFoundOrPendingTransaction`)
  covers both, with a comment explaining why no second variant exists.

## Verification performed

- `mvn -pl services/crypto -am compile` / `test-compile` — `BUILD SUCCESS`. One informational
  deprecation warning in `TronAdapterTest` (`ApiWrapper.constantCall`, used internally by trident's
  own `Trc20Contract.symbol()`/`.decimals()`, which the test must mock to match — not a defect in this
  task's own code, since this task doesn't call `constantCall` directly).
- `mvn -pl services/crypto test -Dtest=TronAdapterTest` — 24/24 pass.
- `mvn -pl services/crypto test -Dtest=TronAdapterConfigTest` — 7/7 pass.
- `mvn -pl services/crypto -am test` (full module) — 194 tests, 0 failures, 3 pre-existing Docker/
  Testcontainers errors (unrelated to T07, same known limitation carried forward from T03/T04/T06). No
  new failures introduced anywhere else in the module.
- **Negative-proof mutation test** on the highest-value Phase 9 fix (the `pollOnce` exception
  boundary, Kimi Issues 2/7): temporarily removed the `try/catch` wrapping `pollOnceUnguarded(...)` in
  `pollOnce`. Re-ran `pollDoesNotPropagateAnUnexpectedFailure` and
  `nullResponseFromTransactionInfoByBlockNumThrowsANamedExceptionCaughtByThePollBoundary` — both
  failed exactly as expected (the un-caught exception propagated out of the mutated `pollOnce`).
  Reverted via `diff` against a pre-mutation backup and confirmed `TronAdapterTest`
  (24/24) and `TronAdapterConfigTest` (7/7) passed again clean.

## Deviations from the frozen brief / prior-phase plans

None to the production code (already closed at Phase 9). One test-plan deviation, explained above
(the collapsed not-found/pending test pair). Several tests were added beyond the frozen brief's
minimum Required Tests list to cover Phase 6/9 findings that postdated the brief itself — matching the
precedent set by T06's own Phase 10/11 additions.
