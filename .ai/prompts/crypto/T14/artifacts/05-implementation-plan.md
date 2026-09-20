# crypto · T14 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (STATUS: FROZEN). No code written in this phase.

## Files to create

All three trace directly to the frozen brief's "Files to Create" section — no additions.

1. `services/crypto/src/main/java/com/themistra/crypto/finality/FinalityPolicy.java`
2. `services/crypto/src/main/java/com/themistra/crypto/finality/EthereumFinalityPolicy.java`
3. `services/crypto/src/main/java/com/themistra/crypto/finality/TronFinalityPolicy.java`

## Files to modify

None (frozen brief: "Files to Modify: None").

## Public methods (signatures)

**`FinalityPolicy` (interface):**
```java
Chain chain();
boolean isFinal(FinalityStatus status);
```

**`EthereumFinalityPolicy implements FinalityPolicy` (`@Component`):**
```java
public Chain chain();                          // returns Chain.ETHEREUM
public boolean isFinal(FinalityStatus status);  // returns status.txBlockNumber() <= status.finalizedBlockNumber(); throws NPE on null
```

**`TronFinalityPolicy implements FinalityPolicy` (`@Component`):**
```java
public Chain chain();                          // returns Chain.TRON
public boolean isFinal(FinalityStatus status);  // returns status.txBlockNumber() <= status.finalizedBlockNumber(); throws NPE on null
```

## Private methods

None. Each `isFinal` is a single `Objects.requireNonNull` guard plus a one-line comparison — no
extraction warranted (mirrors the frozen brief's Constraints: O(1), no persistence, no external call).

## Entities used

None.

## Repositories used

None.

## Services used

None — no dependency on any other package (`observation`, `provider`, `quorum`, `token`, `events`,
`adapter` beyond the two named types).

## Unit/integration tests required

(Test files are not created in this phase — Phase 6's own directive defers all test-writing to Phase
10; listed here only to confirm every planned test traces to the frozen brief's Required Tests.)

**`EthereumFinalityPolicyTest`** (new, `finality/` test package):
- `shouldRequireBeaconFinalizedCheckpointForEthereumFinality` (named test, AC1)
- boundary: `txBlockNumber == finalizedBlockNumber` → final
- boundary: `txBlockNumber == finalizedBlockNumber + 1` → not final
- scripted-heads: `currentBlockNumber` far ahead of both other fields → decision unaffected
- `chain()` returns `Chain.ETHEREUM`
- `isFinal(null)` throws `NullPointerException`

**`TronFinalityPolicyTest`** (new, `finality/` test package):
- `shouldRequireSolidifiedBlockForTronFinality` (named test, AC2)
- boundary: `txBlockNumber == finalizedBlockNumber` → final
- boundary: `txBlockNumber == finalizedBlockNumber + 1` → not final
- scripted-heads: `currentBlockNumber` far ahead of both other fields → decision unaffected
- `chain()` returns `Chain.TRON`
- `isFinal(null)` throws `NullPointerException`

**`FinalityModuleBoundaryTest`** (new, `finality/` test package; AC6, Phase 3 Finding 2):
- Mirrors `TokenModuleBoundaryTest`/`ProviderModuleBoundaryTest`'s exact source-scan style (a plain
  static file scan over `import` lines, not ArchUnit) — one difference from those two precedents:
  `token`'s/`provider`'s own boundary tests forbid `com.themistra.crypto.adapter` entirely, but
  `finality/` legitimately needs two specific types from it. The scan therefore uses two rules instead
  of one flat forbidden-prefix list:
  - Any import starting with `com.themistra.crypto.observation`, `com.themistra.crypto.provider`,
    `com.themistra.crypto.quorum`, `com.themistra.crypto.token`, or `com.themistra.crypto.events` fails
    outright (fully forbidden, same as precedent).
  - Any import starting with `com.themistra.crypto.adapter` must be exactly
    `import com.themistra.crypto.adapter.Chain;` or `import com.themistra.crypto.adapter.model.FinalityStatus;`
    (an allow-list of two exact lines) — anything else under `adapter` (e.g. `adapter.ChainAdapter`,
    `adapter.eth.*`, `adapter.model.TxResult`) fails.

No integration test — no persistence, no Docker dependency, consistent with `AddressValidator`'s (T12)
and `AddressPoisoningDetector`'s (T13) own precedent for a pure-predicate package.

## Execution order

1. `finality/FinalityPolicy.java` (interface) — no dependents yet, defines the contract both
   implementations satisfy.
2. `finality/EthereumFinalityPolicy.java` — implements the interface.
3. `finality/TronFinalityPolicy.java` — implements the interface.
4. `mvn -pl services/crypto compile` — confirm the three new files compile cleanly against the
   already-existing `Chain`/`FinalityStatus` types.
5. (Phase 7 self-review, then Phase 10) test files, in the same 1-2-3 order as their production
   counterparts, plus `FinalityModuleBoundaryTest` last (it scans the finished package).

No schema/migration/DAO step applies — this task has no persistence layer (frozen brief: "State
Changes: None").
