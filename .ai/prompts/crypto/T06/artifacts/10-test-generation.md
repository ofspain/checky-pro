# crypto · T06 · Phase 10 — Test Generation

## Files created

- `services/crypto/src/test/java/com/themistra/crypto/adapter/eth/EthereumAdapterTest.java` — 18
  tests. `Web3j` and `ScheduledExecutorService` are both Mockito mocks; no real network call, no
  Spring context (AC5).
- `services/crypto/src/test/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfigTest.java` —
  5 tests, using `ApplicationContextRunner` (the established pattern from `KafkaProducerConfigTest`/
  `ProviderPropertiesTest`) with a real `HttpService`/`Web3j.build(...)`, since these tests exist
  specifically to prove the wiring — URL substitution and OkHttpClient timeouts — that only exists at
  that boundary.

## Test-to-AC / Required-Tests mapping

Traced to the frozen brief's Required Tests (Phase 5 plan §"Required Tests"):

| Plan item | Test method | Notes |
|---|---|---|
| `getFinalityStatus` uses FINALIZED/LATEST tags (AC1) | `getFinalityStatusUsesFinalizedAndLatestBlockTagsNotAConfirmationCount` | |
| `getFinalityStatus` throws for not-found (AC1) | `getFinalityStatusThrowsForANotFoundTransaction` | |
| — (added) | `getFinalityStatusThrowsForAPendingUnminedTransaction` | Not in the original plan list; added to cover the pending/unmined branch of the same guard, distinct from not-found |
| `getTx` exists=false for not-found (AC2) | `getTxReturnsExistsFalseForANotFoundTransaction` | |
| `getTx` exists=false for pending (AC2) | `getTxReturnsExistsFalseForAPendingUnminedTransaction` | |
| `getTx` addresses from Transfer log (AC10) | `getTxReturnsFullyPopulatedResultForAFoundErc20TransferWithAddressesFromTheTransferLog` | Transaction-level `from` deliberately differs from the log's `from` topic, to prove the source |
| — (added) | `getTxUsesTheFirstTransferLogWhenAReceiptContainsMultiple` | Documents the Phase 9 Finding 7 limitation recorded in the class Javadoc |
| `getTx` native value, no log | `getTxReturnsNativeValueWithNullTokenContractWhenNoTransferLogIsPresent` | |
| `getTx` confirmations=1 at tx block (amendment #6, AC9) | `getTxConfirmationsEqualsOneWhenTxBlockEqualsCurrentBlock` | |
| `getTx` propagates IOException (AC2) | `getTxPropagatesAMockedIoExceptionUnchecked` | |
| — (added) | `getTxThrowsWhenCurrentBlockIsEarlierThanTxBlock` | Phase 9 Finding 8's negative-confirmations guard |
| `getTokenInfo` decodes symbol/decimals (AC3) | `getTokenInfoDecodesSymbolAndDecimalsFromAMockedAbiResponse` | Hand-built ABI hex for a dynamic `Utf8String` + `uint8` |
| — (added) | `getTokenInfoThrowsWithContractContextWhenTheCallIsReverted` | Phase 9 Finding 2's `ethCall` error-checking |
| `subscribeAddress` filter has no contract restriction (AC6) | `subscribeAddressLogFilterHasNoContractAddressRestrictionOnlyTheRecipientTopic` | |
| `subscribeAddress` cursor starts at LATEST (amendment #4, AC8) | `subscribeAddressCursorInitializesToLatestNotGenesis` | |
| `subscribeAddress` cancel stops polling (AC7) | `cancellingTheSubscriptionStopsFurtherPolling` | |
| — (added) | `pollBuildsObservationsDirectlyFromTheMatchedLogNotViaGetTx` | Phase 9 Finding 1's fix — the highest-value regression test in this suite; see Mutation testing below |
| — (added) | `closeShutsDownWeb3jAndScheduler` | Phase 9 Finding 3's `AutoCloseable`/resource-lifecycle fix |
| `substitutesResolvedCredentialIntoAUrlContainingThePlaceholder` (AC4) | same | |
| `leavesUrlUnchangedWhenNoPlaceholderIsPresent` | same | Proves the `local` fixture's plain URLs are unaffected |
| `leavesPlaceholderUnsubstitutedWhenTheEnvironmentValueIsAbsent` (original plan) | **superseded by** `throwsWhenPlaceholderIsPresentButTheEnvironmentValueIsAbsent` | Phase 9 Resolution item 5 changed this case from silent to fail-fast; the original plan text describes the pre-Phase-9 behavior and is stale — the test now asserts the frozen (post-resolution) contract instead |
| `configuresHttpClientTimeoutFromProviderEntryTimeoutSeconds` (AC11) | same | |
| `buildsOneAdapterPerConfiguredEthereumProviderEntry` | same | Two ETHEREUM + one TRON entry (mirrors the real `local` fixture shape) — asserts exactly 2 adapters, TRON filtered out |

23 tests total (18 + 5). Every Required-Tests-section item is covered, either as named or as a
renamed/superseded variant with the reason recorded above.

## A pre-existing build defect found and fixed this phase

`EthereumAdapterConfigTest` initially failed all 5 tests with `NoClassDefFoundError:
com/fasterxml/jackson/annotation/JsonSerializeAs` while constructing a real `HttpService`. Root
cause, confirmed by inspecting the resolved dependency tree (`mvn dependency:tree -Dverbose`) and
the actual jar contents:

- `web3j:core:6.0.0` depends on `tools.jackson.core:jackson-databind:3.1.0`, whose
  `JacksonAnnotationIntrospector` static-references `com.fasterxml.jackson.annotation.JsonSerializeAs`
  — a class that exists only from `jackson-annotations:2.21` onward (confirmed absent in 2.12.6
  through 2.19.2, present in 2.21, by listing each candidate jar's contents).
- Spring Boot 3.5.4 imports `jackson-bom:2.19.2`, which manages `jackson-annotations` down to 2.19.2
  and — via the parent's dependency-management import — was winning dependency mediation over
  whatever `jackson-databind:3.1.0` itself requested.
- This is **not test-only**: `EthereumAdapterConfig` constructs a real `HttpService`/`Web3j` for
  every configured provider at application startup (`ethereumAdapters` bean method) — with the
  unpatched dependency graph, the real app would crash with the same `NoClassDefFoundError` the
  moment any Ethereum provider is configured, entirely independent of anything this task's own code
  does.
- Fix: added an explicit `com.fasterxml.jackson.core:jackson-annotations:2.21` entry to
  `services/crypto/pom.xml`'s own `<dependencyManagement>` (nearer than the parent's imported BOM,
  so it wins). `jackson-annotations` is additive/back-compat within the 2.x line, so this is safe for
  Spring's own Jackson 2 usage elsewhere in the module. Verified via
  `mvn dependency:tree -Dverbose | grep jackson-annotations` — every resolution now lands on 2.21,
  no more "omitted for duplicate" downgrade.

This was necessary, minimal (one dependencyManagement entry, no other file touched), and squarely
within T06's scope — without it, `EthereumAdapterConfigTest` cannot pass and the adapter this task
delivers cannot actually run.

## Verification performed

- `mvn -pl services/crypto -am compile` / `test-compile` — `BUILD SUCCESS`.
- `mvn -pl services/crypto test -Dtest=EthereumAdapterTest` — 18/18 pass.
- `mvn -pl services/crypto test -Dtest=EthereumAdapterConfigTest` — 5/5 pass (after the pom fix
  above).
- `mvn -pl services/crypto -am test` (full module) — 154 tests, 0 failures, 3 pre-existing Docker/
  Testcontainers errors (`ChainBaselineMigrationIntegrationTest`, `OutboxGrantMigrationIntegrationTest`,
  `OutboxTransactionIntegrationTest` — Docker unavailable in this environment; unrelated to T06, same
  known limitation recorded for T03/T04). No new failures introduced anywhere else in the module.
- **Negative-proof mutation test** (this session's established convention) on the highest-value new
  logic — the Phase 9 Finding 1 fix: temporarily changed `pollOnce` to call
  `sink.onObservation(getTx(log.getTransactionHash()))` instead of
  `sink.onObservation(buildTxResultFromLog(log, toBlock))`. Re-ran
  `pollBuildsObservationsDirectlyFromTheMatchedLogNotViaGetTx` — it failed (the mutated code path hit
  an unstubbed `web3j.ethGetTransactionByHash(...)`, which is exactly what the test's `never()`
  verifications exist to catch). Reverted via `diff` against a pre-mutation backup and confirmed the
  full `EthereumAdapterTest` suite (18/18) passed again clean.

## Deviations from the frozen brief / Phase 5 plan

None to the production code (already closed at Phase 9). Two test-plan deviations, both explained in
the mapping table above: one test renamed to match the Phase 9-superseded fail-fast contract, and
several tests added beyond the plan's minimum list to cover Phase 7/8/9 findings that postdated the
Phase 5 plan itself.
