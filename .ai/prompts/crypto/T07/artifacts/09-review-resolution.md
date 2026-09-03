# crypto · T07 · Phase 9 — Review Resolution

**Human Approval gate. Approved 2026-09-03.** Combines Phase 7 (self-review, 7 findings) and Phase 8
(Kimi independent review, 10 findings) into one resolution log. Substantial overlap between the two
passes (6 of Kimi's findings independently confirmed 5 of the self-review's) increased confidence
these are real issues, not noise. Only accepted comments were applied.

## Resolution log

| # | Comment (source) | Decision | Reason | Change made |
|---|---|---|---|---|
| 1 | Local Tron provider URL (`http://localhost:9903/fake-tron-a`) is shaped like an HTTP URL, not a valid gRPC target; `ManagedChannelBuilder.forTarget` parses it synchronously at `ApiWrapperBuilder.build()` time, not lazily (self-review Finding 1 / Kimi Issue 1) | **ACCEPTED** | Bytecode-confirmed via `buildChannel`'s use of `io.grpc.ManagedChannelBuilder.forTarget(String)`; the value was a T03-era leftover never revisited for Tron's transport | `application.properties`'s TRON provider entry `url` changed to `localhost:9903` (bare host:port), with a comment explaining why this differs from the ETHEREUM entries' HTTP URLs |
| 2 | `getContract`'s not-found behavior was unverified; the `fetchContract` `null` guard was written defensively without the same bytecode discipline applied elsewhere (self-review Finding 2 / Kimi Issue 4), and any unexpected `Trc20Contract`/`symbol()`/`decimals()` failure propagated with no provider/address context (Kimi Issue 9) | **ACCEPTED, merged into one fix** | Full bytecode trace of `getContract` confirmed it never throws and never returns `null` — it unconditionally wraps whatever the gRPC call returns, making the `null` guard dead code. Rather than two narrow patches, one unified fix covers both: wrap `getTokenInfo`'s entire body and convert any unexpected failure into this class's usual named exception | `getTokenInfo` now wraps its body in `try/catch (RuntimeException e)`, rethrowing as `IllegalStateException` with provider name + `contractAddress`. The now-dead `fetchContract` helper (and its ineffective `null` check) was removed |
| 3 | `findTransferLog` (used by `getTx`) only required `getTopicsCount() > 0`, while the structurally identical `isMatchingTransferLog` (used by the poll loop) correctly required `>= 3` — a log with a colliding `topic[0]` but fewer than 3 topics would pass the weaker filter and then throw an unguarded `IndexOutOfBoundsException` (self-review Finding 3 / Kimi Issue 3) | **ACCEPTED** | Real inconsistency between two methods that should enforce the identical matching rule | `findTransferLog`'s filter now also requires `getTopicsCount() >= 3`, matching `isMatchingTransferLog` exactly |
| 4 | `pollOnce` had no exception boundary; `ScheduledExecutorService.scheduleWithFixedDelay` silently and permanently cancels all future executions of a task that throws — one transient RPC failure (or a malformed address reaching `topicForAddress`) would silently end a subscription's polling forever (self-review Finding 4 / Kimi Issues 2 and 7) | **ACCEPTED** | Real, severe reliability gap; Kimi's Issue 7 (invalid address compounding this) is fully covered by the same fix, no separate change needed | `pollOnce` renamed to `pollOnceUnguarded`; a new `pollOnce` wraps it in `try/catch (RuntimeException e)`, logging at error level via a new SLF4J logger field and swallowing the failure so the next tick still runs |
| 5 | `buildNativeTransferResult` indexed `tx.getRawData().getContract(0)` with no `getContractCount() == 0` guard — an empty list (not structurally impossible per the protobuf schema) would throw a bare `IndexOutOfBoundsException` (self-review Finding 5 / Kimi Issue 5) | **ACCEPTED** | Cheap, consistent with this class's error-handling style everywhere else | Added an explicit `getContractCount() == 0` guard throwing a named `IllegalStateException` with provider + `txHash` context |
| 6 | `fetchTransactionInfoByBlockNum` dereferenced `apiWrapper.getTransactionInfoByBlockNum(blockNum)`'s result directly with no `null` guard; a `null` response (never confirmed impossible) would NPE with no provider/block context (Kimi Issue 6, refined the self-review's more general Finding 7) | **ACCEPTED** | Concrete, cheap fix; Kimi's framing (explicit `null` check) is more actionable than the self-review's broader "verify the empty-block convention" framing, and supersedes it | Added an explicit `null` check on the `TransactionInfoList` response before calling `.getTransactionInfoList()`, throwing a named `IllegalStateException` with block-number context if `null` |
| 7 | `close()` called `apiWrapper.close()` before `scheduler.shutdown()` with no exception safety — if `apiWrapper.close()` threw, the scheduler (and its virtual-thread polling tasks) would never shut down (Kimi Issue 10) | **ACCEPTED** | Cheap, real resource-leak risk on partial failure | `close()` now shuts down the scheduler first (so no further poll tick can start), wrapped in `try/finally` so `apiWrapper.close()` always runs even if `scheduler.shutdown()` somehow threw (it doesn't, per its own contract, but the ordering plus `finally` is the safer pattern either way) |
| 8 | `ApiWrapperBuilder.withTimeout`'s unit was unverified — the brief/code assumed milliseconds from the field name alone, with a plausible 1000x error risk if wrong (Kimi Issue 8) | **RESOLVED VIA VERIFICATION, no code change** | Bytecode trace of `TimeoutInterceptor` confirmed `CallOptions.withDeadlineAfter(timeout, TimeUnit.MILLISECONDS)` — the existing `Duration.ofSeconds(entry.timeoutSeconds()).toMillis()` conversion in `TronAdapterConfig` was already correct | None — verification only |
| 9 | Zero-address `Trc20Contract` owner convention and `decimals().intValueExact()`'s unchecked-overflow risk are reasonable but unverified against a real provider (self-review Finding 6) | **ACKNOWLEDGED, not a Phase 9 action** | Both are already reasonable, documented engineering judgment; Phase 10 test coverage is the right venue, not a code change now | No change |
| 10 | `EthereumAdapter.pollOnce` (T06) has the identical exception-boundary exposure as Finding 4 above (Kimi Issue 2's own recommendation raised this) | **ACKNOWLEDGED, explicitly out of scope** | `adapter/eth/*` is on T07's Files-NOT-to-Modify list (frozen/shipped, T06) — fixing it here would violate "work only on T07." Flagged as a follow-up worth a deliberate decision, not silently left inconsistent | No change to `EthereumAdapter.java` |

**7 accepted (1 as a merged fix covering two comments), 1 resolved via verification with no code
change needed, 2 acknowledged with no action (one explicitly out of this task's scope).**

## Files changed this phase

- `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapter.java` — items 2, 3, 4,
  5, 6, 7.
- `services/crypto/src/main/resources/application.properties` — item 1.

Both files were already on the frozen brief's Files-to-Create/Modify list — no file outside that list
was touched. `mvn -pl services/crypto -am compile` — `BUILD SUCCESS`, zero warnings.
`mvn -pl services/crypto -am test` — 163 tests, 0 failures, same 3 pre-existing Docker-unavailable
errors as before this phase; no regression. No public API was changed — `TronAdapter`'s constructor
and all `ChainAdapter` method signatures are unchanged; `pollOnce` was split into a public-surface-
invisible `pollOnce`/`pollOnceUnguarded` pair (both private); `fetchContract` (private, never part of
any public contract) was removed as dead code, not a public-API change.
