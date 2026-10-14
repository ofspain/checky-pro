# payment · T09 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T09 — Payment state machine |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/payment/PaymentState.java`
2. `services/payment/src/main/java/com/themistra/payment/payment/PaymentStateMachine.java`
3. `services/payment/src/main/java/com/themistra/payment/payment/IllegalPaymentStateTransitionException.java`
4. `services/payment/src/test/java/com/themistra/payment/payment/PaymentStateMachineTest.java`
5. `services/payment/src/test/java/com/themistra/payment/payment/RogueInvoiceEntityReferencer.java`
6. `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RogueUnmappedEntity.java`

## Files to modify

1. `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`

## Public methods (signatures)

```java
// PaymentState.java
public enum PaymentState {
    CREATED, WATCHING, SEEN, CONFIRMING, FINALIZED, ATTESTED, HELD
}
```

```java
// PaymentStateMachine.java
@Component
public class PaymentStateMachine {

    public PaymentState transition(PaymentState current, PaymentState target);
    // Objects.requireNonNull both args; looks up the fixed edge set; returns target if the pair
    // is permitted; throws IllegalPaymentStateTransitionException otherwise.

    public boolean isPermitted(PaymentState current, PaymentState target);
    // Objects.requireNonNull both args; returns whether the pair is in the fixed edge set.
}
```

```java
// IllegalPaymentStateTransitionException.java
public class IllegalPaymentStateTransitionException extends RuntimeException {

    public IllegalPaymentStateTransitionException(PaymentState current, PaymentState target);
    // builds its own message: "Illegal payment state transition: <current> -> <target> is not permitted"

    public PaymentState getCurrent();
    public PaymentState getTarget();
}
```

## Private methods

- `PaymentStateMachine`: a single `private static final Map<PaymentState, Set<PaymentState>>
  PERMITTED_TRANSITIONS` (an `EnumMap<PaymentState, EnumSet<PaymentState>>`), built once via a
  static initializer block from the frozen brief's own 10-edge list. `isPermitted` is the one
  real piece of logic (`PERMITTED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(PaymentState.class)).contains(target)`);
  `transition` calls it and either returns `target` or throws. No other private method needed —
  the class is small enough that a separate private helper would be over-structuring.
- `ArchitectureTest`: `private static String featureModuleOf(JavaClass)` (mirrors
  `CrossModuleEntityArchitectureTest`'s own identical helper, scoped to this service's own two
  feature modules: `FEATURE_MODULES = List.of("invoice", "payment")`), and a private
  `ArchCondition<JavaClass> onlyBeAccessedFromTheSameFeatureModule()` factory method (same
  mirrored shape). No allowlist set needed — unlike crypto-service, this service has zero
  pre-existing cross-module entity dependencies to grandfather in.

## Entities used

`Invoice` (`invoice` package) — read-only, by `RogueInvoiceEntityReferencer` only, to exercise a
genuine cross-module violation in the new ArchUnit canary. No entity is used by production code
in this task.

## Repositories used

None.

## Services used

None — `PaymentStateMachine` has zero dependencies (frozen brief AC5).

## Unit/integration tests required

All plain JUnit 5, no Spring context, no Testcontainers (nothing in this task touches
persistence or Kafka):

1. **`PaymentStateMachineTest`**
   - One `@ParameterizedTest` sourced from a `static Stream<Arguments>` listing the 10 legal
     edges (frozen brief AC1) — asserts `transition(current, target)` returns `target`, and
     `isPermitted(current, target)` returns `true`. Named individually per the frozen brief's
     own list via `@MethodSource` + a descriptive `@DisplayName`, or 10 separate `@Test` methods
     each calling a shared private assertion helper — **decision: 10 separate named `@Test`
     methods**, matching the frozen brief's own Required Tests list verbatim name-for-name
     (`shouldTransitionCreatedToWatchingOnWatchAck`, etc.), since the brief names them
     individually and a single parameterized test would not satisfy "named tests" as literally
     as 10 distinct methods do.
   - One exhaustive test (AC2) computed programmatically, not 39 hand-written methods: iterate
     `EnumSet.allOf(PaymentState.class) × EnumSet.allOf(PaymentState.class)` (49 pairs), skip the
     10 legal pairs (reusing the same static legal-edge source the 10 named tests use, so the two
     can never silently drift apart), and for each of the remaining 39 assert `transition(...)`
     throws `IllegalPaymentStateTransitionException` carrying the correct `current`/`target`, and
     `isPermitted(...)` returns `false`. Explicitly asserted (not just incidentally covered) to
     include `(HELD, HELD)` and every `(HELD, x≠HELD)` pair (AC2/Finding 4).
   - 4 null-argument tests (AC4): `transition(null, SEEN)`, `transition(SEEN, null)`,
     `isPermitted(null, SEEN)`, `isPermitted(SEEN, null)` — each asserts `NullPointerException`.
   - 1 reflection-based test (AC5): `PaymentStateMachine.class.getDeclaredFields().length == 0`
     and `PaymentStateMachine.class.getDeclaredConstructors()` has exactly one constructor with
     zero parameters.
2. **`ArchitectureTest`** (modified)
   - `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` — the real canary,
     `.check(analyzedClasses())` on the full service.
   - `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` — narrow
     `ClassFileImporter().importClasses(RogueInvoiceEntityReferencer.class, Invoice.class)`,
     asserts the rule throws `AssertionError` mentioning both class names.
   - `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule` — narrow
     `ClassFileImporter().importClasses(RogueUnmappedEntity.class)`, asserts the rule throws
     `AssertionError` containing "does not reside in any module listed in FEATURE_MODULES".

## Execution order

1. `PaymentState.java` (no dependencies — everything else depends on it).
2. `IllegalPaymentStateTransitionException.java` (depends only on `PaymentState`).
3. `PaymentStateMachine.java` (depends on both above).
4. `PaymentStateMachineTest.java` (depends on all three; write and run before touching
   `ArchitectureTest`, so the state-machine half of this task is verified independently of the
   ArchUnit half).
5. `RogueInvoiceEntityReferencer.java` and `RogueUnmappedEntity.java` (test fixtures — needed
   before `ArchitectureTest`'s own new canaries can compile).
6. `ArchitectureTest.java` modification (depends on the two fixtures above plus the real
   `Invoice` entity already in the codebase).
7. Full `mvn -pl services/payment -am test` run (no Docker-dependent class touched by this task,
   so the full non-Docker suite should run unmodified in count except for this task's own net-new
   tests).

Every file above traces to the frozen brief's own "Files to Create"/"Files to Modify" sections;
nothing added beyond them.
