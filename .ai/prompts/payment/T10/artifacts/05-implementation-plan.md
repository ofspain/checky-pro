# payment · T10 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T10 — Verification record |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecord.java`
2. `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordRepository.java`
3. `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordService.java`
4. `services/payment/src/test/java/com/themistra/payment/verification/VerificationRecordServiceTest.java`
5. `services/payment/src/test/java/com/themistra/payment/verification/VerificationRecordRepositoryIntegrationTest.java`

## Files to modify

1. `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`

## Public methods (signatures)

```java
// VerificationRecord.java
@Entity
@Table(name = "verification_records")
public class VerificationRecord {

    public static VerificationRecord create(Long paymentId, PaymentState fromState, PaymentState toState,
            String triggerEventKey, Integer confirmations, Map<String, Object> detail, Instant occurredAt);

    public Long getId();
    public Long getPaymentId();
    public PaymentState getFromState();
    public PaymentState getToState();
    public String getTriggerEventKey();
    public Integer getConfirmations();
    public Map<String, Object> getDetail();
    public Instant getOccurredAt();
}
```

```java
// VerificationRecordRepository.java
interface VerificationRecordRepository extends JpaRepository<VerificationRecord, Long> {
    // no custom method this task - package-private, mirrors InvoiceRepository's own precedent
}
```

```java
// VerificationRecordService.java
@Component
public class VerificationRecordService {

    public VerificationRecordService(VerificationRecordRepository repository, Clock clock);

    @Transactional
    public VerificationRecord record(Long paymentId, PaymentState fromState, PaymentState target,
            String triggerEventKey, Integer confirmations, Map<String, Object> detail);
    // Objects.requireNonNull(paymentId), Objects.requireNonNull(target) only.

    @Transactional
    public VerificationRecord recordIgnored(Long paymentId, PaymentState currentState,
            String triggerEventKey, Map<String, Object> detail);
    // delegates to record(paymentId, null, currentState, triggerEventKey, null, detail) -
    // Finding 1's own decided fromState=NULL shape.
}
```

## Private methods

None needed in `VerificationRecordService` — `recordIgnored` is a one-line delegation to
`record`, and `record` itself is a single `requireNonNull` pair plus one `repository.save(...)`
call; a private helper would be over-structuring for this size.

`VerificationRecord` needs a protected no-arg constructor (JPA only) and no other private method
— mirrors `Invoice`'s own exact shape, including a `@PrePersist onCreate()` last-resort `Instant.now()`
fallback for `occurredAt` (mirrors `Invoice.createdAt`'s own identical precedent and rationale).

`ArchitectureTest`: no new private method — `featureModuleOf`/`onlyBeAccessedFromTheSameFeatureModule`
already exist (T09); only the `FEATURE_MODULES` list literal and its own Javadoc change.

## Entities used

`VerificationRecord` (new, this task's own). No other entity — `paymentId` is a plain `Long`.

## Repositories used

`VerificationRecordRepository` (new).

## Services used

None — `VerificationRecordService` has exactly two dependencies, `VerificationRecordRepository`
and `Clock`, both already available (the latter via `ClockConfig`, T04).

## Unit/integration tests required

1. **`VerificationRecordServiceTest`** (plain JUnit + Mockito, mirrors `InvoiceServiceTest`'s own
   `@ExtendWith(MockitoExtension.class)` + `Clock.fixed` shape):
   - `shouldWalkBackwardConfirmingToSeenOnReorg` (AC1, named) — `record(id, CONFIRMING, SEEN,
     key, null, null)`, verify `repository.save` called with a `VerificationRecord` whose
     `fromState=CONFIRMING`, `toState=SEEN`, `triggerEventKey=key`, `occurredAt=NOW`.
   - `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` (AC2, named) — same shape for
     `SEEN → WATCHING`.
   - `shouldIgnoreOutOfOrderOrStaleChainEvents` (AC3, named) — `recordIgnored(id, FINALIZED, key,
     detail)`, verify the saved record has `fromState=null`, `toState=FINALIZED`,
     `confirmations=null`, `detail=detail`.
   - `shouldRecordAnOrdinaryForwardTransition` (AC4) — e.g. `record(id, CREATED, WATCHING, key,
     null, null)`.
   - 2 null-argument tests (AC7): `record(null, ...)` and `record(..., null target...)` → NPE;
     mirror for `recordIgnored`'s own two `NOT NULL`-backed parameters (`paymentId`,
     `currentState`) — 4 tests total across both methods.
   - A source-text test (AC5) reading all three new production files, asserting none contains
     `.delete(`/`.deleteById(`/`.deleteAll(`.
2. **`VerificationRecordRepositoryIntegrationTest`** (Testcontainers, mirrors
   `InvoiceRepositoryIntegrationTest`'s own `TestConfig`/`@SpringBootTest`/`@DynamicPropertySource`
   shape exactly, connecting as `payment_app`):
   - `shouldRoundTripDetailJsonbMapping` (AC8) — saves a `VerificationRecord` with a non-trivial
     nested `Map<String, Object>` `detail` (e.g. a string, a number, and a nested map, to prove
     the JSON mapping handles more than a flat string map), re-fetches it via
     `repository.findById(...)`, asserts the retrieved `detail` equals the original map.
   - `shouldPersistRecordWithNullDetailAndNullFromState` (AC3's own real-DB counterpart) — proves
     a `recordIgnored`-shaped row (null `detail`, null `fromState`) round-trips too, since the
     schema allows both to be null and the JSONB mapping must not choke on a null value.
3. **`ArchitectureTest`** (modified) — no new test method; the 3 existing canaries
   (`shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`,
   `...ActuallyFailsAgainstAGenuineViolation`, `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule`)
   re-run after `FEATURE_MODULES` gains `"verification"` — this is itself the regression proof
   (AC6); no `VerificationRecord`-specific rogue fixture is needed since this task introduces no
   entity that imports across a module boundary.

## Execution order

1. `VerificationRecord.java` (no dependencies beyond `PaymentState`, T09).
2. `VerificationRecordRepository.java` (depends on the entity).
3. `VerificationRecordService.java` (depends on both above, plus `Clock`).
4. `VerificationRecordServiceTest.java` (depends on all three; write and run before touching
   `ArchitectureTest`).
5. `ArchitectureTest.java` modification (`FEATURE_MODULES` + Javadoc) — run its own 3 canaries to
   confirm no regression.
6. `VerificationRecordRepositoryIntegrationTest.java` (Testcontainers; written last since it
   exercises the fully-assembled entity/repository pair against a real schema — cannot be run
   locally this session, Docker unavailable, disclosed).
7. Full `mvn -pl services/payment -am test -Dtest='!SchemaBaselineMigrationIntegrationTest,!InvoiceRepositoryIntegrationTest,!VerificationRecordRepositoryIntegrationTest'`
   run to confirm the non-Docker suite is still green and grew by exactly this task's own new
   tests.

Every file above traces to the frozen brief's own "Files to Create"/"Files to Modify" sections;
nothing added beyond them.
