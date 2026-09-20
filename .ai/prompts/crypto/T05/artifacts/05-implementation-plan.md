# crypto · T05 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/crypto/src/main/java/com/themistra/crypto/adapter/Chain.java`
2. `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TxResult.java`
3. `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TokenInfo.java`
4. `services/crypto/src/main/java/com/themistra/crypto/adapter/model/FinalityStatus.java`
5. `services/crypto/src/main/java/com/themistra/crypto/adapter/model/Subscription.java`
6. `services/crypto/src/main/java/com/themistra/crypto/adapter/ObservationSink.java`
7. `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java`
8. `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapter.java`
9. `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapterTest.java`
10. `services/crypto/src/test/java/com/themistra/crypto/adapter/ChainAdapterShapeTest.java`

No files modified; no files outside this list.

## Public methods (signatures)

**`Chain`**
```java
public enum Chain {
    ETHEREUM, TRON;
    // Javadoc only (amendment #3): bridge from T03's regex-constrained config String is the
    // built-in Chain.valueOf(String) - no custom converter method needed or added.
}
```

**`TxResult`** (record, `adapter/model/`)
```java
public record TxResult(
        boolean exists, String txHash, String fromAddress, String toAddress,
        String tokenContractAddress, BigDecimal amount, int confirmations, long blockNumber) {}
```

**`TokenInfo`** (record, `adapter/model/`)
```java
public record TokenInfo(String contractAddress, String symbol, int decimals) {}
```

**`FinalityStatus`** (record, `adapter/model/`, amendment #8)
```java
public record FinalityStatus(long txBlockNumber, long currentBlockNumber, long finalizedBlockNumber) {}
```

**`Subscription`** (functional interface, `adapter/model/`)
```java
public interface Subscription {
    void cancel();
}
```

**`ObservationSink`** (functional interface, `adapter/`)
```java
public interface ObservationSink {
    void onObservation(TxResult result);
}
```

**`ChainAdapter`** (interface, `adapter/`, VERBATIM design §4c)
```java
public interface ChainAdapter {
    Chain chain();
    TxResult getTx(String txHash);
    TokenInfo getTokenInfo(String contractAddress);
    Subscription subscribeAddress(String address, ObservationSink sink);
    FinalityStatus getFinalityStatus(String txHash);
}
```

**`FakeChainAdapter`** (test scope, `adapter/`)
```java
public class FakeChainAdapter implements ChainAdapter {
    public FakeChainAdapter(Chain chain);

    public FakeChainAdapter scriptTx(String txHash, TxResult result);          // fluent
    public FakeChainAdapter scriptTokenInfo(String contractAddress, TokenInfo info);  // fluent
    public FakeChainAdapter scriptFinalityStatus(String txHash, FinalityStatus status); // fluent
    public void simulateReorg(String txHash, TxResult newResult);              // amendment #2

    @Override public Chain chain();
    @Override public TxResult getTx(String txHash);                // throws IllegalStateException if unscripted (AC6)
    @Override public TokenInfo getTokenInfo(String contractAddress); // throws IllegalStateException if unscripted (AC6)
    @Override public Subscription subscribeAddress(String address, ObservationSink sink); // no replay (AC7)
    @Override public FinalityStatus getFinalityStatus(String txHash); // throws IllegalStateException if unscripted (AC6)
}
```

## Private methods

**`FakeChainAdapter`**:
- `private void pushToMatchingSubscriptions(TxResult result)` — called only from `simulateReorg`
  (amendment #2): iterates active subscriptions, invokes `sink.onObservation(result)` for every
  subscription whose address equals `result.fromAddress()` or `result.toAddress()`. `scriptTx` itself
  never calls this — only `simulateReorg` pushes, keeping "configure the answer" (`scriptTx`) and
  "simulate a live update" (`simulateReorg`) distinct, per the frozen brief's own separation.
- Internal state: `Map<String, TxResult> scriptedTx`, `Map<String, TokenInfo> scriptedTokenInfo`,
  `Map<String, FinalityStatus> scriptedFinalityStatus`, and a `List<ActiveSubscription>` (a private
  local record `ActiveSubscription(String address, ObservationSink sink)`) rather than a
  `Map<String, ObservationSink>` — supports more than one active subscription per address without
  the second silently overwriting the first, since nothing in the frozen brief limits a fake to a
  single subscriber per address. `subscribeAddress` returns a `Subscription` lambda that removes its
  own entry from this list on `cancel()`.

**`ChainAdapterShapeTest`**: a private helper building the expected `(methodName, returnType,
List<parameterType>)` tuples to compare against `ChainAdapter.class.getDeclaredMethods()` — scoped
per amendment #9 (no parameter names, no annotations, no declaration-order assumption).

## Entities / Repositories / Services used

None — no persistence, no Spring, no config.

## Unit tests required

Traced to the frozen brief's "Required Tests":

- **`FakeChainAdapterTest`**:
  - `agree` — two `FakeChainAdapter` instances scripted with an equal `TxResult` for the same
    `txHash` return that same value from `getTx` — covers AC3.
  - `disagree` — two instances scripted with differing `TxResult`s for the same `txHash` — covers AC3.
  - `lag` — one instance scripted with `exists=false` (or lower `confirmations`/`blockNumber`) than
    another for the same `txHash` — covers AC3.
  - `reorg` — `scriptTx` an initial result, `subscribeAddress` on the relevant address, then
    `simulateReorg` with a differing result for the same `txHash`; asserts (a) `getTx` now returns the
    new result and (b) the subscribed `ObservationSink` received exactly the new result — covers AC3,
    amendment #2.
  - `unscriptedGetTxThrows` / `unscriptedGetTokenInfoThrows` / `unscriptedGetFinalityStatusThrows` —
    each asserts `IllegalStateException` naming the unscripted key — covers AC6, amendment #6.
  - `subscribeDoesNotReplayAlreadyScriptedTransaction` — `scriptTx` for an address, *then*
    `subscribeAddress` on it; asserts the sink receives nothing until a subsequent `simulateReorg` —
    covers AC7, amendment #7.
  - `cancelledSubscriptionReceivesNoFurtherObservations` — boundary case: `subscribeAddress`, `cancel()`
    the returned `Subscription`, then `simulateReorg`; asserts the sink is never invoked.
- **`ChainAdapterShapeTest`**: asserts `ChainAdapter`'s 5 declared methods match design §4c exactly
  by name/return-type/parameter-types (amendment #9) — covers AC1.

No test needed for `TokenInfo`'s equals/hashCode (amendment #1 explicitly rejected relying on or
special-casing record equality — nothing to test) or for `Chain.valueOf` (amendment #3 — it's a JDK
guarantee, not this task's own code).

## Execution order

No schema/config/persistence layer exists in this task, so ordering follows pure compile-time
dependency, innermost types first:

1. `Chain.java` — no dependencies.
2. `model/TxResult.java`, `model/TokenInfo.java`, `model/FinalityStatus.java` — no dependencies on
   each other or on `Chain`.
3. `model/Subscription.java` — no dependencies.
4. `ObservationSink.java` — depends on `TxResult` (step 2).
5. `ChainAdapter.java` — depends on `Chain` (1), all `model/` types (2, 3), and `ObservationSink` (4).
6. `FakeChainAdapter.java` (test scope) — depends on `ChainAdapter` (5) and all its referenced types.
7. `FakeChainAdapterTest.java` — depends on step 6.
8. `ChainAdapterShapeTest.java` — depends on step 5 only.
9. `mvn -pl services/crypto -am compile` / `test-compile` / `test` — full verification; no Docker
   dependency anywhere in this task (confirmed: no persistence, no Spring context, no Testcontainers).
