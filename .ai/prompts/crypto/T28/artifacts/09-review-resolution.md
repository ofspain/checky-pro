# crypto · T28 · Phase 9 — Review Resolution (Human Approval Gate)

## Kimi Phase 8 findings — dispositions

All 7 findings independently verified against real source before disposition.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | `T01SkeletonRegressionTest` checks only the substring `closed`, not a real test citation | **ACCEPTED** | Added `assertThat(row).containsPattern("`[A-Za-z0-9]+\\.[a-zA-Z0-9]+`")` per row, requiring a real `ClassName.methodName`-shaped backtick citation, not just the word `closed`. |
| 2 | Regression guard doesn't enforce rows #3/#4/#5's documented caveats | **ACCEPTED** | Added three explicit assertions: row #3 contains `"not yet executed"`, row #4 contains `"code-path only"`, row #5 contains `"crypto-service portion"` — a future edit silently dropping any of these now fails the build. |
| 3 | `WatcherTest` covers only "two answer" and "one lags", not the literal "single-provider" shape | **ACCEPTED** | Added `doesNotEvaluateWithOnlyOneOfThreeProvidersAnswering`, mirroring the existing two-provider test exactly, delivering only `providerA` and asserting `verifyNoInteractions(quorumDecisionService, txLifecyclePublisher)`. ("Zero providers answer" was considered and not added — no observation exists at all in that case, making it a vacuous, not-test-worthy scenario.) Row #1's citation updated to include it. |
| 4 | Row #3 cites test classes, not specific named tests | **ACCEPTED, improved beyond the recommendation** | Replaced with exact methods: `EthereumFinalityPolicyTest.shouldRequireBeaconFinalizedCheckpointForEthereumFinality`, `TronFinalityPolicyTest.shouldRequireSolidifiedBlockForTronFinality`. For the "walk cursor backward" claim specifically, found a better citation than the originally-cited `ReorgDetectorTest` (which only covers event-payload/idempotency details): `WatcherTest.shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg` — the actual named test from `package.md` §8, verified to assert exactly the cursor-invalidation behavior the row describes. |
| 5 | Row #5's DB-grant claim has no cited test | **ACCEPTED, improved beyond the recommendation** | Rather than writing a new test (Kimi's suggestion), found an existing, exact one: `ChainBaselineMigrationIntegrationTest.cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnTheThreeGrantedTables` directly asserts INSERT/SELECT succeed and UPDATE/DELETE are denied on `chain.observations`. Cited it, disclosed as Docker-blocked (not yet executed here) like every other Testcontainers-based test in this suite. |
| 6 | `T01SkeletonRegressionTest`'s method name still says `Tracks` while asserting `closed` | **ACCEPTED — reverses Phase 7's own call** | Phase 7's self-review considered and rejected this exact rename, reasoning the only external reference (T01's own historical artifact) made the cost outweigh the benefit. Kimi flagging the identical issue independently, plus rechecking that the historical reference is one immutable, point-in-time artifact (not live documentation), tipped the balance: renamed to `threatModelClosesThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched`. |
| 7 | No automated check that cited tests exist on the classpath | **REJECTED — out of scope** | Kimi's own "optional." Same class of CI/tooling suggestion rejected in T27 Phase 11 (Findings #5/#7/#8 there) — building classpath-validation tooling is a different task's own scope, not this one's. |

## A finding beyond Kimi's own review

Implementing the strengthened `containsPattern` assertion (Finding #1) immediately caught a real gap
Kimi's own review had not flagged: row #4's citation was `KmsSignerArchitectureTest` — a bare class
name, the identical "class-level, not method-level" inconsistency Finding #4 raised specifically for
row #3, but present in row #4 too and missed by both reviews until the new regression guard ran against
it. Fixed by citing the exact canary method:
`KmsSignerArchitectureTest.shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild`.

## Verification performed

- `mvn -pl services/crypto -am test -Dtest=WatcherTest,T01SkeletonRegressionTest` — 74/74 passing
  (68 + 6), including the new test and the strengthened regression guard, after fixing the row #4 gap
  the new guard itself surfaced.
- `mvn -pl services/crypto -am verify` — 698 tests (697 + the new `WatcherTest` case), 0 failures, the
  same 14 already-disclosed Docker-only errors.
