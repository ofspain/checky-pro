# crypto · T07 · Phase 6 — Implementation Notes

## Files changed

- `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapter.java` (new, 380
  lines).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapterConfig.java` (new, 73
  lines).
- `services/crypto/src/main/resources/application.properties` (modified) — added
  `themistra.crypto.adapter.tron.poll-interval-ms` (default `3000`, matching Tron's ~3s block time).

All three were already on the frozen brief's Files-to-Create/Modify list — no file outside that list
was touched. `mvn -pl services/crypto -am compile` — `BUILD SUCCESS`, zero warnings (a deprecation
warning surfaced and was resolved during implementation, see Deviations below). `mvn -pl services/crypto
-am test` — 163 tests, 0 failures, same 3 pre-existing Docker-unavailable errors as before this task;
no regression anywhere else in the module.

## Mapping to the plan and acceptance criteria

| Frozen brief item | Implementation |
|---|---|
| AC1/R7/L4 — `getFinalityStatus` uses solidified block, guarded | `getFinalityStatus` (`TronAdapter.java:163-182`) via `fetchSolidifiedBlockNumber()`, guard at :173-178 |
| AC2 — `getTx` exists=false / unchecked transport error | `getTx` (:109-136), `fetchTransactionInfo`/`fetchTransaction` (:313-332) |
| AC3/L7 — `getTokenInfo` keyed by `contractAddress`, Base58Check | `getTokenInfo` (:140-147) |
| AC4 — `chain()` returns `Chain.TRON` | :104-106 |
| AC5/amendment #9 — credential via config, skip when unresolved | `TronAdapterConfig.buildAdapter` (:52-70) |
| AC6 — no real network call in tests | N/A here (Phase 10); `ApiWrapper` is constructor-injected, non-final (verified) |
| AC7/amendment #1 — recipient-topic-only filter, Base58→topic | `pollOnce`/`topicForAddress` (:190-217, :280-285) |
| AC8 — `Subscription.cancel()` stops polling | :156-159 |
| AC9/amendment #5 — cursor at current block, capped catch-up | :153, :191-199 |
| AC10/amendment #2 — log-sourced addresses, native-TRX fallback | `buildTxResultFromLog`/`buildNativeTransferResult` (:224-248) |
| AC11/amendment #8 — `timeoutSeconds` → `withTimeout` | `TronAdapterConfig.java:54` |
| Amendment #4 — current-vs-solidity guard | `TronAdapter.java:173-178` |
| Amendment #10 — TRC-10 out of scope | `buildNativeTransferResult`'s `ContractType` check (:236-241) — anything but `TransferContract` returns existence with no fact-bearing fields rather than guessing |
| Amendment #11 — caller-validates address assumption | Documented in the class Javadoc at the top of the file; no runtime validation added, matching `EthereumAdapter`'s precedent |

## Resolution of Phase 5's Open Items (empirical, via direct bytecode inspection during
implementation)

1. **Not-found signaling — resolved, differently than hypothesized.** Direct `javap -c` inspection of
   `ApiWrapper.getTransactionById`/`getTransactionInfoById`'s bytecode showed both throw a checked
   `IllegalException` with a distinguishable message prefix (`"Transaction not found: "` /
   `"TransactionInfo not found: "`) — not the default/empty-instance pattern the Phase 5 plan
   guessed. Implemented as message-prefix matching in `fetchTransaction`/`fetchTransactionInfo`.
2. **`ApiWrapper`'s resource lifecycle — resolved.** A public `close()` method exists (confirmed via
   inspection); `TronAdapter.close()` calls it directly, mirroring `EthereumAdapter`'s pattern.
3. **`Trc20Contract`'s owner-address parameter — resolved by convention.** Since `symbol()`/`decimals()`
   are constant (view) calls that touch no state and require no funded account, `getTokenInfo` passes
   the well-known all-zero Tron address (`zeroAddressBase58()`, :303-307) as the owner-address context
   — the standard placeholder for a caller identity that doesn't matter to a constant call. Not
   independently verified against a real node in this phase (no real network call is made anywhere in
   this task); Phase 10's mocked tests exercise the code path, but only a real integration environment
   would prove a real provider accepts this convention. Flagged, not hidden.
4. **Amendment #12 (single URL for two gRPC endpoints) — implemented per the provisional plan,
   still open for a real deployment.** `TronAdapterConfig` uses the 1-arg `ApiWrapperBuilder`
   constructor plus `.withGrpcEndpointSolidity(entry.url())`, pointing both at the same address — this
   also has the pleasant side effect of avoiding the 3-arg constructor's `hexPrivateKey` parameter
   entirely (this adapter never signs anything, L11). Whether a real Tron provider's actual endpoint
   topology tolerates this remains unverified without a real deployment — Open Question carried
   forward unchanged, not resolved by this phase.

## Deviations forced by reality (flagged per the Phase 6 directive, not hidden)

1. **`getTx`'s call order changed from the Phase 5 plan's Ethereum-mirrored structure.** The plan
   assumed (mirroring `EthereumAdapter`) fetching `Transaction` first, then `TransactionInfo`. Direct
   inspection showed trident's `Chain.Transaction` carries **no block-membership field of its own** —
   unlike `web3j`'s `Transaction.getBlockNumber()`, there is nothing on `Transaction` itself that
   signals "is this mined." Only `TransactionInfo`'s existence does. `getTx` was implemented to check
   `TransactionInfo` *first* (the only reliable mined-vs-not-mined signal), fetching the raw
   `Transaction` only when the native-TRX fallback path actually needs it (no Transfer log present).
2. **Amendment #7's literal scenario ("mined tx, null `TransactionInfo`") does not materialize as a
   distinct code branch.** The frozen brief wrote this amendment mirroring T06's exact Ethereum
   precedent (an indexing-lag race between two independently-queryable signals). For Tron, there is no
   second, independent "is this tx mined" signal to race against — `TransactionInfo`'s own existence
   *is* the mined signal. A transaction that is "pending" and one that is "mined but info not yet
   indexed" are indistinguishable through trident's API and correctly collapse to the same
   `TxResult(exists=false, ...)` outcome via the same not-found catch. This satisfies AC2/L4's actual
   intent (both are "not yet observed as mined") even though the mechanism differs from the amendment's
   literal text — see the class Javadoc for the full reasoning.
3. **`getNowBlockSolidity()` is deprecated.** Not identified in Phase 0-5 (the method's presence was
   confirmed via `javap -p`, which does not surface deprecation). `javac -Xlint:deprecation` caught it
   during this phase's own compile. Its non-deprecated replacement, confirmed via the same inspection
   that showed every dedicated `*Solidity` convenience method is deprecated in favor of the unified
   `NodeType`-parameterized methods, is `getNowBlock(NodeType.SOLIDITY_NODE)` — same `Chain.Block`
   return type as the plain `getNowBlock()` call, which also simplified the code (no need to handle
   `getNowBlockSolidity()`'s distinct `Response.BlockExtention` return type at all).
4. **Two distinct address-encoding helpers, not one.** Not explicitly anticipated in Phase 5.
   `TransferContract.ownerAddress`/`toAddress` are full 21-byte Tron-native addresses (already
   prefixed); TRC-20 event log topics/addresses are plain 20-byte EVM-style values (Tron's TVM is
   EVM-bytecode-compatible, so a ported TRC-20 contract emits standard Ethereum-shaped event data with
   no Tron prefix). Conflating them would silently corrupt one of the two sources, so
   `encodeNativeAddress`/`encodeEvmStyleAddress` are implemented separately (:287-302), each documented
   with which source it's for.
5. **`org.tron.trident.utils.Base58Check` used instead of `org.tron.trident.core.utils.Base58`.** The
   Phase 4/5 plan referenced `core.utils.Base58` (plain Base58, no checksum) based on an earlier,
   shallower inspection pass. Deeper bytecode tracing of `ApiWrapper.parseAddress`'s own internals (the
   method trident itself uses for every address-string input) showed it actually calls
   `org.tron.trident.utils.Base58Check.base58ToBytes`/`.bytesToBase58` — a different, checksum-aware
   utility class in a different package. Using the same one trident uses internally, rather than the
   plain-Base58 one named in the plan, is the correct and consistent choice; `core.utils.Base58` is
   unused by this implementation.

## Money / secrets / thread-safety

- TRC-20 amounts decoded as unsigned big-endian `BigInteger` from raw log data, then `BigDecimal` —
  base units, no `decimals` scaling at this layer (agents.md). Native TRX amounts are `long` (SUN
  units) from `TransferContract.getAmount()`, converted via `BigDecimal.valueOf(long)`.
- No credential value is ever logged; exception messages reference `providerName`, never the resolved
  API key. `apiKeySecretName` (the reference) may appear in logs.
- Polling runs on a virtual-thread-backed `ScheduledExecutorService`, fixed-delay (never fixed-rate),
  identical to `EthereumAdapter`. `sink.onObservation(...)` may be invoked off the caller's thread.

## Not yet done (explicitly out of this phase)

Tests (`TronAdapterTest`, `TronAdapterConfigTest`) are Phase 10's job per the Phase 6 directive — none
were written in this phase. Several implementation choices above (the zero-address `Trc20Contract`
convention, the address-encoding helpers, the not-found message-prefix matching) are informed by
verified library inspection but not yet exercised by any test — Phase 10 must write tests that actually
construct realistic mocked responses (topic bytes, `TransferContract` payloads, `IllegalException`
messages) to catch any remaining wrong assumption before this task can claim PASS.
