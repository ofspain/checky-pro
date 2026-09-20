# crypto · T07 · Phase 7 — Self Review

Self-review of the Phase 6 diff (`TronAdapter.java`, `TronAdapterConfig.java`,
`application.properties`) against the frozen brief and `agents.md`. No code changed in this phase —
findings only, per the phase directive.

---

## Finding 1 — Local-profile Tron provider URL is very likely an invalid gRPC target, and channel construction is not lazy

**Severity:** High

**Evidence:** `TronAdapter.java:52-54` (`TronAdapterConfig.buildAdapter`) passes `entry.url()` directly
as `ApiWrapperBuilder`'s `grpcEndpoint` (and `grpcEndpointSolidity`). Direct `javap -c` inspection of
`ApiWrapper`'s private `buildChannel` method confirmed it calls
`io.grpc.ManagedChannelBuilder.forTarget(String)` — grpc-java's standard target-string API, which
expects a bare authority (`host:port`) or a URI whose scheme matches a registered `NameResolver`
(`dns:`, `unix:`, etc.). The existing `local`-profile Tron provider entry in `application.properties`
(inherited from T03, never revisited for Tron) is `http://localhost:9903/fake-tron-a` — shaped like an
HTTP URL, not a gRPC target, and `"http"` is not a scheme grpc-java's default resolver registry
recognizes. Unlike T06's `HttpService`/`Web3j.build(...)` (confirmed lazy — construction alone never
does I/O), `ManagedChannelBuilder.forTarget(...)`'s target-string parsing can throw synchronously
during channel construction, which happens inside `ApiWrapperBuilder.build()` — i.e. at Spring context
startup, not deferred to the first real RPC call.

**Recommendation:** Verify empirically in Phase 10 (a context-boot test against the actual local
fixture value will either pass or fail cleanly). If it fails as suspected, update the local Tron
provider entry's `url` in `application.properties` to a valid `host:port` form — that file is already
on the frozen brief's Files-to-Modify list, just a larger edit than the brief's own stated intent
("add a Tron poll-interval property") anticipated.

---

## Finding 2 — `getContract`'s not-found behavior is unverified; `getTokenInfo` may NPE instead of failing clearly

**Severity:** Medium-High

**Evidence:** `TronAdapter.java:351-358` (`fetchContract`)'s `null` guard was written defensively,
without the same bytecode-level not-found verification applied to `fetchTransaction`/
`fetchTransactionInfo` (which confirmed those throw `IllegalException` with a distinguishable message,
not a `null`/default return). If `getContract` actually behaves like the verified methods (throws
rather than returns `null`), this `null` check is dead code and an unknown-contract-address call
surfaces as an unhandled `IllegalException` instead of this class's usual named `IllegalStateException`
pattern. If it instead returns a non-null but empty/garbage `Contract` for an unknown address (a third
possibility, also unverified), `getTokenInfo` (`:140-148`) could NPE deep inside `Trc20Contract`'s own
field access rather than failing with a clear, diagnosable message.

**Recommendation:** Trace `getContract`'s full bytecode (mirroring the discipline already applied to
the other fetch methods), or empirically test against a known-nonexistent contract address, and align
`fetchContract`'s handling with the confirmed behavior.

---

## Finding 3 — `findTransferLog`'s topic-count guard is weaker than `isMatchingTransferLog`'s, risking an unguarded `IndexOutOfBoundsException`

**Severity:** Medium

**Evidence:** `findTransferLog` (`:257-262`, used by `getTx`'s direct-lookup path) only requires
`getTopicsCount() > 0` before matching on `topic[0]`. `isMatchingTransferLog` (`:218-222`, used by the
poll/`subscribeAddress` path) requires `getTopicsCount() >= 3` — the correct guard, since a standard
Transfer event always carries exactly 3 topics (signature + from + to). A log with a colliding
`topic[0]` value but fewer than 3 topics would pass `findTransferLog`'s weaker filter, then throw an
unguarded `IndexOutOfBoundsException` inside `buildTxResultFromLog` (`:226-227`, reading `getTopics(1)`/
`getTopics(2)`) instead of either failing clearly or simply not matching.

**Recommendation:** Align `findTransferLog`'s guard to also require `getTopicsCount() >= 3`, matching
`isMatchingTransferLog` exactly — the two methods should apply the identical matching rule.

---

## Finding 4 — `pollOnce` has no exception boundary; an uncaught failure silently and permanently kills the subscription's polling

**Severity:** Medium

**Evidence:** `pollOnce` (`:190-216`) is the `Runnable` passed to `scheduler.scheduleWithFixedDelay(...)`
(`:155-157`) with no surrounding try/catch. Per `ScheduledExecutorService`'s documented contract, an
uncaught exception thrown from a `scheduleWithFixedDelay` task silently and permanently cancels all
future executions of that task — a single transient RPC failure during one poll tick (e.g.
`fetchTransactionInfoByBlockNum` failing for one block in the scanned range) would silently end that
subscription's polling forever, with no visible error unless something explicitly inspects the returned
`Future`.

**Recommendation:** Wrap `pollOnce`'s body in a try/catch that logs and swallows an unexpected failure,
so one bad poll tick doesn't silently end the subscription. Note: `EthereumAdapter`'s `pollOnce` (T06)
has the identical structural exposure — fixing this here without a matching fix there would leave an
inconsistency between the two adapters worth deciding on explicitly, not silently.

---

## Finding 5 — `buildNativeTransferResult` indexes the contract list with no explicit empty-list guard

**Severity:** Medium

**Evidence:** `TronAdapter.java:236`: `tx.getRawData().getContract(0)` — no `getContractCount() == 0`
check beforehand. In practice every real broadcast Tron transaction carries exactly one contract, but
the protobuf schema's `repeated` cardinality does not itself guarantee this, and an empty list here
would surface as a bare `IndexOutOfBoundsException` rather than this class's usual named, contextual
`IllegalStateException`.

**Recommendation:** Add an explicit `getContractCount() == 0` guard that throws a named
`IllegalStateException` with context, consistent with the error-handling style used everywhere else in
this class.

---

## Finding 6 — Two Phase 6 conventions (zero-address owner, `intValueExact()`) are reasonable but unverified against a real provider

**Severity:** Low

**Evidence:** `TronAdapter.java:140-145` (all-zero-address owner context for `Trc20Contract`'s constant
calls) and `:147` (`Trc20Contract.decimals().intValueExact()`, an unchecked `ArithmeticException` on
overflow — extremely unlikely for a real `decimals()` value, but unguarded). Both are documented,
reasonable engineering judgment calls, but neither has been exercised by any test or real provider
response yet.

**Recommendation:** Phase 10 tests should exercise both paths explicitly (a mocked large-`decimals()`
response to confirm the failure mode is at least clear; the zero-address convention should be at least
provable against a mocked `Trc20Contract` construction, since no real provider is ever called in
tests).

---

## Finding 7 — Empty-block handling in `fetchTransactionInfoByBlockNum` is assumed, not verified

**Severity:** Low

**Evidence:** `TronAdapter.java:341-349`. A block with zero transactions is assumed to yield an empty
`TransactionInfoList` rather than an `IllegalException`, by analogy with typical bulk-query API
conventions (distinct from the single-entity not-found methods, which were independently verified).
Not traced at the bytecode level.

**Recommendation:** Low priority given the convention is standard for list-returning queries; a Phase
10 test asserting `pollOnce` tolerates an empty block gracefully would close the gap cheaply.

---

## Summary table

| # | Issue | Severity |
|---|-------|----------|
| 1 | Local Tron fixture URL likely invalid for grpc's `forTarget`; channel construction isn't lazy | High |
| 2 | `getContract` not-found behavior unverified; possible NPE instead of clear failure | Medium-High |
| 3 | `findTransferLog` guard weaker than `isMatchingTransferLog` — possible `IndexOutOfBoundsException` | Medium |
| 4 | `pollOnce` has no exception boundary — one failure silently kills all future polling | Medium |
| 5 | `contract.getContract(0)` has no empty-list guard | Medium |
| 6 | Zero-address owner convention / `intValueExact()` unverified | Low |
| 7 | Empty-block handling in block-scan assumed, not verified | Low |

(End of self-review.)
