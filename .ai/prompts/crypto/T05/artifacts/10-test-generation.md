# crypto · T05 · Phase 10 — Test Generation

Test-only phase. No production code changed — `mvn -pl services/crypto -am compile` output is
identical to Phase 9's. 3 test files added (1 fixture, 2 test classes), 11 tests, all mapped below to
the frozen brief's acceptance criteria. **11/11 passing.** No Docker dependency anywhere in this
task's test suite (pure Java, no persistence, no Spring context) — unlike T03/T04, everything here
actually ran.

## Files created

- `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapter.java` — the scripted
  test double named in the task statement itself.
- `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapterTest.java` — 10 tests.
- `services/crypto/src/test/java/com/themistra/crypto/adapter/ChainAdapterShapeTest.java` — 1 test.

## Test manifest

| Test | AC | Notes |
|---|---|---|
| `FakeChainAdapterTest.agreeIsTwoInstancesScriptedWithAnEqualResultForTheSameTxHash` | AC3 | Agree is emergent from scripting, not a special mode |
| `...disagreeIsTwoInstancesScriptedWithDifferingResultsForTheSameTxHash` | AC3 | |
| `...lagIsARelativelyBehindOrUnobservedResult` | AC3 | Covers both forms of "lag": fewer confirmations, and `exists=false` |
| `...reorgReScriptsTheTxAndPushesTheNewResultToAMatchingSubscription` | AC3 | The one behavior needing real mechanism (amendment #2's address-matching) |
| `...reorgDoesNotPushToASubscriptionForAnUnrelatedAddress` | AC3 (negative proof) | Proves the address-matching is selective, not a blanket broadcast |
| `...unscriptedGetTxThrows` / `...GetTokenInfoThrows` / `...GetFinalityStatusThrows` | **AC6** | Each asserts `IllegalStateException` naming the unscripted key |
| `...subscribeDoesNotReplayAnAlreadyScriptedTransaction` | **AC7** | |
| `...cancelledSubscriptionReceivesNoFurtherObservations` | Boundary case (Kimi Phase 8 Finding 7's contract) | Not separately named in the frozen brief's Required Tests, added since Phase 9 documented `cancel()`'s contract explicitly — worth proving, not just asserting in Javadoc |
| `ChainAdapterShapeTest.chainAdapterHasExactlyTheFiveMethodsDesignSpecifiesVerbatim` | **AC1** | Reflection-based, scoped per amendment #9 (name/return-type/parameter-types only) |

**AC1–AC7: all covered.** AC2 (`Chain` has exactly `ETHEREUM`/`TRON`) and AC4/AC5 (L7/L4 discipline
rules) have no dedicated test — AC2 is trivially true by inspection of a 2-constant enum with nothing
to regress against; AC4/AC5 are documentation-only contracts with no code behavior to assert (Phase 9
explicitly rejected structural enforcement for AC4, and AC5 is "the record has no extra field," already
verified by direct inspection in Phase 7/9).

## Negative-proof (mutation testing) — two mechanisms verified

1. **`FakeChainAdapter`'s reorg address-matching**: temporarily made `pushToMatchingSubscriptions`
   push unconditionally (`matches = true`) regardless of address. Re-ran `FakeChainAdapterTest` alone:
   **1 test failed** — `reorgDoesNotPushToASubscriptionForAnUnrelatedAddress`, exactly the test
   proving selectivity. Reverted via `diff` (byte-identical); full suite green again.
2. **`ChainAdapterShapeTest`'s own assertion logic**: temporarily changed the test's *expected* shape
   for `chain()` to return `String` instead of `Chain`. Re-ran alone: **1 test failed** with a clean
   `AssertionFailedError`, proving the comparison isn't vacuously true. Reverted, `diff`-confirmed.

**A third mechanism was checked and found to be an even stronger safety net than the reflection test
alone**: attempting to mutate `ChainAdapter.getTx`'s own signature (adding a parameter) **failed at
`mvn test-compile`**, before the shape test ever ran — because `FakeChainAdapter`'s `@Override
getTx(String)` no longer matches the interface. Any real or fake implementer breaks the build
immediately on an interface-shape change; `ChainAdapterShapeTest` is a second, explicit, self-
documenting guard on top of that implicit one (and the only one that would catch an *addition* of a
6th method nothing implements yet, which wouldn't break any implementer's compile).

## Verification

```
mvn -pl services/crypto -am compile / test-compile   → BUILD SUCCESS
mvn -pl services/crypto test -Dtest='FakeChainAdapterTest,ChainAdapterShapeTest'
  → Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```
