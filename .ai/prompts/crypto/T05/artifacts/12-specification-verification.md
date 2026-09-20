# crypto · T05 · Phase 12 — Specification Verification

Principal-engineer sign-off pass over the final implementation + tests against `requirements.md`,
`design.md`, `tasks.md`, and the frozen brief (`artifacts/04-frozen-task-brief.md`), for T05 only.

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **AC1** — `ChainAdapter` matches design §4c verbatim | Yes | `ChainAdapter.java:32-43` (5 methods, identical names/signatures/inline comments to design §4c) | Yes — `ChainAdapterShapeTest` (2 tests: `isInterface`, method-shape comparison) | No | No |
| **AC2** — `Chain` has exactly `ETHEREUM`/`TRON` | Yes | `Chain.java:12-15` | Yes — `ChainTest.hasExactlyEthereumAndTron` | No | No |
| **AC3** — `FakeChainAdapter` supports agree/disagree/lag/reorg | Yes | `FakeChainAdapter.java` (scripting methods + `simulateReorg`'s address-matching push) | Yes — 8 `FakeChainAdapterTest` cases covering all four behaviors plus `toAddress`, multi-subscriber, and `exists=false`-reorg edge cases (Phase 11) | No | No |
| **AC4 (L7)** — `TokenInfo` identity is `contractAddress` only | Yes (documentation-enforced, not structural) | `TokenInfo.java:8-15` (Javadoc) | Yes — `TokenInfoTest` (2 tests: equality includes `symbol`, `contractAddress()` is the correct comparison) | No | No — documented deviation from naive record-equals expectations, deliberate per Phase 4 amendment #1 |
| **AC5 (L4)** — `FinalityStatus` carries no precomputed decision | Yes | `FinalityStatus.java:16-20` (3 raw `long` fields only) | Yes — `FinalityStatusShapeTest` (2 reflection tests) | No | No |
| **AC6** — unscripted `FakeChainAdapter` queries throw | Yes | `FakeChainAdapter.java` (`getTx`/`getTokenInfo`/`getFinalityStatus`, each `IllegalStateException` naming the key) | Yes — 3 tests in `FakeChainAdapterTest` | No | No |
| **AC7** — `subscribeAddress` does not replay | Yes | `FakeChainAdapter.subscribeAddress` (no scan of `scriptedTx` at subscribe time) | Yes — `subscribeDoesNotReplayAnAlreadyScriptedTransaction` | No | No |
| **L4/L7/L14/L15** | Yes | See AC4/AC5 above; L14 satisfied by `ChainAdapter` remaining a plain interface with no RPC-client assumption; L15 satisfied — every new file under `adapter/`/`adapter/model/` | N/A (structural, L14) / Yes (L4, L7) | No | No |

No `R`-numbered requirement is independently claimed as satisfied by this task (Phase 1 finding,
unchanged through implementation) — this task ships shape/plumbing that later tasks (6, 7, 9, 11, 14,
16) implement business logic against.

## Frozen-brief file-list compliance

`git status --porcelain services/crypto` (excluding `target/`) shows changes only in files already on
the frozen brief's Files to Create list: the 7 main files (`Chain`, `ChainAdapter`, `ObservationSink`,
and the 4 `model/` types) and their test-scope counterparts (`FakeChainAdapter` and its test classes,
plus the Phase 11 additions `ChainTest`, `TokenInfoTest`, `FinalityStatusShapeTest` — all under the
same `adapter/`/`adapter/model/` test-source tree the brief already authorized). `git status
--porcelain spec/` is empty — no specification file was touched at any point in this task. The Files
NOT to Modify list (T03's `ProviderProperties`/`FinalityProperties`, `pom.xml`) is respected —
confirmed untouched.

## Answers

**(1) Is the task fully complete?** Yes. `ChainAdapter`, `Chain`, and `FakeChainAdapter` (the task
statement's three named deliverables) are implemented and tested; the four supporting types the spec
left unshaped (`TxResult`, `TokenInfo`, `FinalityStatus`, `Subscription`) were designed to the minimum
needed for their known downstream consumers, with every non-obvious design choice (the
`finalizedBlockNumber` unification, the `TokenInfo` equality discipline, the failure-vs-negative-answer
contract) recorded directly in the affected class's own Javadoc, not left implicit.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC7 all have passing tests
(25/25 green, `mvn -pl services/crypto -am compile`/`test-compile` clean). This is the first task in
the series where every acceptance criterion was both written AND executed in this environment — no
Docker dependency exists anywhere in this task's scope, unlike T03/T04.

**(3) Does it violate any LOCKED decision?** No. L4 and L7 are respected and test-enforced (via
reflection/equality tests, not just Javadoc). L14 is respected — nothing in `ChainAdapter`'s shape
assumes a direct RPC client, keeping a sidecar-backed implementation possible. L15 is respected — no
file outside `adapter/` was touched. No LOCKED decision from any other task's scope (L1, L3, L5, L11,
L12, L13, etc.) was implicated or violated, since this task adds no persistence, no config, no
security surface, and no messaging.

**(4) Remaining risks?**
- **Every design choice this task made for the four unshaped supporting types is a bet on what T06
  ({@code EthereumAdapter}), T07 ({@code TronAdapter}), T09 ({@code QuorumEvaluator}), T11
  ({@code TokenValidator}), and T14 ({@code FinalityPolicy} implementations) will actually need.**
  Nothing in this task's own scope can validate that bet — only building those tasks will. The Javadoc
  contracts (failure-vs-negative-answer, no-allowlist-awareness, launch-scope-only `FinalityStatus`)
  are deliberately explicit so a mismatch surfaces as a documented contradiction rather than a silent
  surprise, but a mismatch is still possible.
- `Chain` was deliberately not retrofitted into T03's `String`-typed config fields (Phase 2/4
  decision) — the bridge (`Chain.valueOf(String)`) is now test-proven (Phase 11), but the two
  representations still exist in parallel; a future task introducing a third chain must update both
  T03's regex and this enum, with nothing but the (untested-until-then) `valueOf` bridge to catch a
  mismatch.
- No error/health-signaling channel exists on `ObservationSink` — deliberately deferred to task 10's
  own provider-health mechanism (Phase 9 decision); task 10 must actually address this when it lands,
  not assume it's already covered.

## Verdict

**PASS** — every acceptance criterion in T05's scope is implemented, tested, and traceable to the
frozen brief; all 25 tests pass in this environment (no Docker gap to carry forward, a first for this
task series); remaining risks are inherent to building foundational shapes ahead of their consumers,
not defects.
