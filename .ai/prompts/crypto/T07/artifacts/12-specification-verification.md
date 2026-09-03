# crypto · T07 · Phase 12 — Specification Verification

Principal-engineer sign-off pass over the final implementation + tests against `requirements.md`,
`design.md`, `tasks.md`, and the frozen brief (`artifacts/04-frozen-task-brief.md`), for T07 only.

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **AC1 (R7, L4)** — `getFinalityStatus` uses `getNowBlock(SOLIDITY_NODE)`, guarded | Yes | `TronAdapter.java:180-203` (guards at :190-195 finalized-vs-current, :196-202 tx-vs-current, the latter added Phase 11 Gap 13) | Yes — `usesSolidityAndCurrentBlockQueriesNotAConfirmationCount`, `throwsWhenSolidifiedBlockExceedsCurrentBlock`, `throwsWhenTxBlockExceedsCurrentBlock`, plus both block-RPC transport-failure tests | No | No |
| **AC2** — `getTx` exists=false / unchecked transport error | Yes | `getTx` (:114-141), `fetchTransactionInfo`/`fetchTransaction` (bytecode-verified not-found signaling) | Yes — 4 tests covering not-found/pending, the fallback-inconsistency throw, and both transport-failure call sites | No | No — see Deviation note below (amendment #7's literal mechanism vs. actual behavior) |
| **AC3 (L7)** — `getTokenInfo` keyed by `contractAddress` (Base58Check), amendment #3 | Yes | `getTokenInfo` (:145-166), unified try/catch (Phase 9) | Yes — happy path, failure-wrapping, and the `Uint8`-boundary (255) test (Phase 11 Gap 8, revised after the original "overflow" premise was found false) | No | No |
| **AC4** — `chain()` returns `Chain.TRON` | Yes | :109-111 | Yes — `chainReturnsTron` (Phase 11 Gap 1) | No | No |
| **AC5** — provider endpoint/credential from config, null/blank-safe (amendment #9) | Yes | `TronAdapterConfig.java` `buildAdapter` | Yes — credential-reaches / credential-skipped / one-adapter-per-entry / no-TRON-chain / `@PreDestroy`-through-a-real-context (Phase 11 Gap 11) | No | No |
| **AC6** — no unit test makes a real gRPC call | Yes | `TronAdapterTest` mocks `ApiWrapper` entirely; `TronAdapterConfigTest` intercepts `ApiWrapperBuilder` construction via `mockConstruction` — no real `ManagedChannel` is ever built in either file | Yes (by construction, all 45 tests) | No | No |
| **AC7 (amendment #1)** — block-scan poll filters by recipient topic only, Base58→topic conversion | Yes | `pollOnceUnguarded` (:234-262), `topicForAddress` (:334-339) | Yes — `blockScanPollHasNoContractAddressRestrictionOnlyTheRecipientTopic`, `logFilterBase58AddressProducesTheExpectedThirtyTwoByteTopic` | No | No |
| **AC8** — `Subscription.cancel()` stops the scheduled poll | Yes | :175-177 | Yes — `cancellingTheSubscriptionStopsFurtherPolling` | No | No |
| **AC9 (amendment #5)** — cursor at current block, fixed-delay, capped catch-up | Yes | :169-177 (cursor/schedule), :239-241 (cap) | Yes — cursor-init, scheduler-delay/period, cursor-advances-after-poll, no-new-blocks-early-return, catch-up-cap (5 tests, Phase 10/11 combined) | No | No |
| **AC10 (amendment #2)** — log-sourced addresses; native-TRX fallback via `TransferContract` | Yes | `buildTxResultFromLog` (:266-273), `buildNativeTransferResult` (:275-289) | Yes — found-transfer (distinct owner vs. log-from proving the source), native-fallback, non-`TransferContract`-type, multiple-logs-uses-first (Phase 11 Gap 9), empty-contract-list guard | No | No |
| **AC11 (amendment #8)** — `timeoutSeconds` → `ApiWrapperBuilder.withTimeout` (ms) | Yes | `TronAdapterConfig.java` | Yes — `timeoutSecondsReachesApiWrapperBuilderWithTimeout`, unit confirmed via bytecode (`TimeoutInterceptor` uses `TimeUnit.MILLISECONDS`) | No | No |
| **R7** — Tron finality via solidified block, not a confirmation count | Yes | Same as AC1 | Yes | No | No |
| **L4** — `getFinalityStatus` raw data only; `finalizedBlockNumber` carries the solidified block | Yes | :180-203 | Yes | No | No |
| **L7** — `getTokenInfo` no allowlist awareness | Yes | :145-166 (no allowlist lookup anywhere in this class) | Yes (indirectly — no test asserts an allowlist check because none exists) | No | No |
| **L13** — no committed credential; resolved via `Environment` at wiring time only | Yes | `TronAdapterConfig.java` (`environment.getProperty(...)`, non-blank guard) | Yes | No | No |
| **L14** — sidecars translation-only | N/A | This task ships a direct Java adapter (trident), not a TS sidecar | N/A | No | No |
| **L15** — module boundaries | Yes | All new files under `adapter/tron/` (main) / `adapter/tron/` (test) — no cross-feature import | N/A (structural) | No | No |

No `R`-numbered requirement beyond R7 is independently claimed as satisfied by this task, matching
T06's own precedent — this task ships the second real `ChainAdapter` implementation; quorum (task 9),
observation persistence (task 8), and event emission (task 17) consume its output later.

## Frozen-brief file-list compliance

`git status --short services/crypto` (current session's uncommitted changes; everything before this —
the Phase 6 implementation, Phase 9's first round of fixes, Phase 10's initial test suite — was already
auto-committed earlier in this session) shows changes confined to `TronAdapter.java`,
`TronAdapterTest.java`, `TronAdapterConfigTest.java` — all on the frozen brief's Files-to-Create list.
The `application.properties` Tron-URL fix (Phase 9, Kimi Issue 1) was applied and committed in an
earlier turn this session; it is disclosed there and in `09-review-resolution.md` as a necessary,
in-scope edit to an already-authorized file. No file under `spec/` was touched at any point in this
task.

## Open Question carried forward, not resolved at this gate

**Amendment #12 (single `ProviderEntry.url` serving both `grpcEndpoint`/`grpcEndpointSolidity`)**
remains open, exactly as scoped at Phase 4. The provisional same-URL plan is implemented and tested
(`grpcEndpointSolidityReceivesTheSameUrlAsGrpcEndpoint`), but whether a real Tron provider's actual
endpoint topology tolerates it is unverified — no real gRPC call is made anywhere in this task's own
scope (AC6), and this can only be confirmed against a real or containerized Tron node. Not a blocker
for this task; a real-deployment risk to carry into whichever task first stands up a live Tron
provider connection.

## Answers

**(1) Is the task fully complete?** Yes. All four `ChainAdapter` methods (`getTx`, `getTokenInfo`,
`subscribeAddress`, `getFinalityStatus`) are implemented on `TronAdapter`, backed by `trident`, against
exactly the same interface `EthereumAdapter` (T06) implements — matching the task statement's own
"against the same interface" clause. `TronAdapterConfig` builds one adapter per configured Tron
`ProviderEntry`, with credential resolution, timeout wiring, and resource lifecycle
(`AutoCloseable` + `@PreDestroy`, verified through a real Spring context) all in place.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC11 all have passing tests
(45/45 for this task's two test classes; 208/208 module-wide excluding the 3 pre-existing
Docker-unavailable integration tests, unchanged from before this task and unrelated to it).

**(3) Does it violate any LOCKED decision?** No. L4, L7, L13, L14 (not applicable — this is not a
sidecar), and L15 are all respected, as detailed in the traceability matrix above. R7 is satisfied by
construction (`getNowBlock(NodeType.SOLIDITY_NODE)`, never a confirmation-count approximation).

**(4) Remaining risks?**
- **Amendment #12, unresolved** (see Open Question above) — the single largest real-deployment risk
  this task carries forward.
- **`getTx`'s multiple-Transfer-log limitation** — identical in kind to `EthereumAdapter`'s own
  documented limitation (T06): a direct `getTx(txHash)` call for a transaction carrying more than one
  TRC-20 Transfer event reports only the first, with no way to disambiguate, because
  `ChainAdapter.getTx(String)` (frozen at T05) takes no recipient parameter.
`subscribeAddress`'s own polling does not share this ambiguity, for the same reason T06's doesn't.
- **`EthereumAdapter.pollOnce`'s own exception-boundary exposure remains unfixed** (Phase 9 Kimi Issue
  2's own observation, explicitly out of T07's scope per the Files-NOT-to-Modify list) — `TronAdapter`
  and `EthereumAdapter` now differ in this one respect. A deliberate, disclosed inconsistency, not an
  oversight — worth a dedicated decision in a future task, not silently left to bit-rot.
- **No retry/backoff in this adapter (amendment #10, by design)** — same architectural bet as T06,
  unverifiable within this task's own scope; only the quorum evaluator (task 9) and provider-health
  tracking (task 10) landing will confirm it holds in practice for Tron specifically, which produces
  blocks roughly 4x faster than Ethereum and so amplifies any per-block RPC cost more than Ethereum's
  own polling does (amendment #5's catch-up cap is this task's own mitigation, not a full answer).

## Verdict

**PASS** — every acceptance criterion in T07's scope is implemented, tested, and traceable to the
frozen brief; all Phase 8 (independent review) and Phase 11 (test review) findings were triaged with
recorded reasoning and either applied, corrected-after-verification (Gap 8's false premise), or
explicitly acknowledged; the one deviation from the frozen brief's literal text (amendment #7's
mechanism, superseded by Phase 6's bytecode-verified finding that Tron has no separate pending signal)
is disclosed and justified as forced by the real library's shape, not an oversight.
