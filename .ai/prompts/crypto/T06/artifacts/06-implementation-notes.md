# crypto · T06 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). Only `src/main` files touched — no tests (Phase 10 scope).

## Files created

- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java` — all 5
  `ChainAdapter` methods, plus the amendment-driven behaviors: pending/not-found → `exists=false`
  (amendment #2); `getFinalityStatus` throws for a not-mined tx (amendment #3); confirmations = `current
  - tx + 1` (amendment #6); `fromAddress`/`toAddress` decoded from the Transfer log's topics, not the
  raw transaction (amendment #7); polling cursor starts at `LATEST`, fixed-delay scheduling (amendment
  #4); Transfer-log poll filter has no contract-address restriction (AC6).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfig.java` — one
  `EthereumAdapter` per configured Ethereum `ProviderProperties` entry; non-throwing `{apiKey}`
  URL-placeholder substitution (amendment #1); `OkHttpClient` timeouts from `timeoutSeconds`
  (amendment #9); virtual-thread-backed `ScheduledExecutorService` (amendment #5).

## Files modified

- `application.properties` — added `themistra.crypto.adapter.ethereum.poll-interval-ms`.

## Deviation forced by reality (flagged, not hidden)

**The Phase 5 plan's `decodeUint256`/`fetchLogs` signatures needed adjustment to compile against the
real web3j 6.0.0 API** — this wasn't caught until actually writing the code against the real library.
Before writing any code, I extracted and inspected the real `core-6.0.0.jar`/`abi-6.0.0.jar` classes
via `javap` (not relying on memory of the API), which caught most signature details correctly up
front, but two generic-variance issues only surfaced at compile time:
1. `FunctionReturnDecoder.decode(String, List<TypeReference<Type>>)` requires an *invariant*
   `List<TypeReference<Type>>` — a `List<TypeReference<Uint256>>` built directly via
   `Collections.singletonList(TypeReference.create(Uint256.class))` doesn't satisfy it (Java generics
   are invariant). Fixed with the same unchecked-cast pattern web3j's own `Function.getOutputParameters()`
   uses internally (confirmed via `javap` that its return type is `List<TypeReference<Type>>` despite
   its constructor accepting `List<TypeReference<?>>` — i.e., web3j's own library does this same cast).
2. `EthLog.getLogs()` returns `List<EthLog.LogResult<?>>`, not the raw `List<EthLog.LogResult>` the
   plan's sketch used — fixed by using the properly-parameterized type throughout `fetchLogs`/`pollOnce`.

Both are typing/signature corrections only — no behavioral or design change from the frozen brief.

## Mapping to acceptance criteria

- **AC1 (R6, L4)** — `getFinalityStatus` calls `fetchBlockNumber(DefaultBlockParameterName.FINALIZED)`
  explicitly (verified against the real enum via `javap`, confirmed present); throws
  `IllegalStateException` when the tx isn't mined. Not yet test-verified (Phase 10).
- **AC2** — `getTx` returns `exists=false` for both not-found and pending (`tx.getBlockNumber() ==
  null`) via one shared `notObservedResult` path; a transport `IOException` from any `web3j` call is
  wrapped in an unchecked `IllegalStateException`. Reasoned through the code; not yet test-verified.
- **AC3 (L7)** — `getTokenInfo` calls `symbol()`/`decimals()` keyed only by `contractAddress`, no
  allowlist reference anywhere in the class.
- **AC4** — `EthereumAdapterConfig.resolveUrl` is the only place credential/URL construction happens;
  no literal endpoint or key in `EthereumAdapter` itself.
- **AC5** — `EthereumAdapter`'s constructor takes `Web3j` (an interface); no static/global
  construction anywhere in the class — fully mockable. Not yet exercised (Phase 10).
- **AC6** — `pollOnce`'s `EthFilter` is built with `Collections.emptyList()` for the address
  parameter (no contract restriction) and only the recipient topic filtered — verified by direct
  inspection.
- **AC7** — `subscribeAddress` returns `() -> future.cancel(false)`; cancelling a
  `ScheduledFuture` prevents further executions by JDK contract.
- **AC8 (amendment #4)** — cursor initializes via `fetchLatestBlockNumber()` at `subscribeAddress`
  call time, never a hardcoded/genesis value; `scheduleWithFixedDelay` (not `scheduleAtFixedRate`) is
  used explicitly.
- **AC9 (amendment #6)** — `confirmations = currentBlock.subtract(txBlock).add(BigInteger.ONE)`,
  giving `1` when `txBlock == currentBlock`, not `0`.
- **AC10 (amendment #7)** — `fromAddress`/`toAddress` are decoded from `log.getTopics().get(1)`/`.get(2)`
  (the Transfer event's own `from`/`to` topics) whenever a Transfer log is found; the transaction's raw
  `tx.getFrom()`/`tx.getTo()` are used only as a fallback when no Transfer log exists (a native-value
  transfer).
- **AC11 (amendment #9)** — `EthereumAdapterConfig` builds an `OkHttpClient` with
  `connectTimeout`/`readTimeout` both set from `entry.timeoutSeconds()`, passed into `HttpService`.

## Verification performed this phase

- **Verified web3j's actual API surface directly, not from memory**, by extracting the real
  `core-6.0.0.jar`/`abi-6.0.0.jar`/`utils-6.0.0.jar` from the local Maven cache and inspecting classes
  with `javap` before and during implementation — confirmed `DefaultBlockParameterName.FINALIZED`
  exists, exact method signatures for `Web3j`/`Ethereum`, `Transaction`, `TransactionReceipt`, `Log`,
  `EthFilter`, `FunctionEncoder`/`FunctionReturnDecoder`/`TypeEncoder`, and `HttpService`'s
  constructors — rather than assuming any of this from training-data recall, given how consequential a
  wrong assumption would be for a task this central to the whole service.
- `mvn -pl services/crypto -am compile` — clean after fixing the two generic-variance issues Findings
  above.
- `mvn -pl services/crypto -am test-compile` — clean; confirms this change doesn't break any existing
  test source.
- No Docker dependency in this task's own main-scope code (no persistence, no Testcontainers) — the
  real-world-behavior risk here is entirely "does this compile against and correctly use the real
  library," which was directly verified via `javap`, not "is a real database/broker reachable."

## Not yet verified (honest, not hidden)

No unit test has actually exercised any of this code's runtime behavior yet — Phase 6 verified it
compiles correctly against the real library and matches the frozen brief's design by direct code
inspection; Phase 10 is what will actually prove `getTx`/`getFinalityStatus`/`getTokenInfo`/
`subscribeAddress` behave correctly against a mocked `Web3j`.
