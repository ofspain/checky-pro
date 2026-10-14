# payment · T05 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T05 — Invoice domain |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceState.java`
2. `services/payment/src/main/java/com/themistra/payment/invoice/InvalidInvoiceRequestException.java`
3. `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java`
4. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
5. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`
6. `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`
7. `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceTest.java`

(6 and 7 are listed for completeness per the brief's own Files to Create — written in Phase 10,
not this phase, per the established phase-separation discipline.)

## Files to modify

None.

## Public methods (signatures)

- `InvoiceState` — enum constants only: `OPEN, PAID, UNDERPAID, OVERPAID, EXPIRED, HELD`.
- `InvalidInvoiceRequestException(String message)` — public constructor, extends
  `RuntimeException`.
- `Invoice` — no public constructor (JPA-only, protected no-arg); static factory:
  `public static Invoice create(UUID invoiceUuid, UUID merchantAccountUuid, String chain,
  String tokenContractAddress, short tokenDecimals, BigDecimal amount, String payToAddress,
  String reference, Instant expiresAt)`; getters for all 13 fields; no setters.
- `InvoiceRepository` — `interface InvoiceRepository extends JpaRepository<Invoice, Long>`
  (package-private, mirrors `OutboxEventRepository`; no custom query methods needed by this
  task).
- `InvoiceService` — `public Invoice create(UUID merchantAccountUuid, String chain, String
  tokenContractAddress, short tokenDecimals, BigDecimal amount, String payToAddress, String
  reference)`.

## Private methods

- `InvoiceService`:
  - `private static void validateChainAndTokenAddress(String chain, String
    tokenContractAddress)` — R3 (AC1).
  - `private static void validateAmount(BigDecimal amount)` — R4 (AC2): null/`<=0`/non-zero-
    scale/>78-digit checks.
- `Invoice`:
  - `private void onCreate()` (`@PrePersist`) — sets `createdAt`/`updatedAt` from the value
    already passed to the factory (not `Instant.now()` — the factory itself receives the
    `Clock`-derived instant from `InvoiceService`, consistent with `OutboxEvent`'s own T04
    pattern of computing time in the service/factory, not inside the entity).
  - `private void onUpdate()` (`@PreUpdate`) — refreshes `updatedAt`. No caller exists yet
    within this task's own scope (no update path), but required by AC4/Phase 3 finding 7 as a
    standing structural guarantee for every later task that does update this entity.

## Entities used

- `Invoice` (new, this task).

## Repositories used

- `InvoiceRepository` (new, this task).

## Services used

- `InvoiceService` depends on: `InvoiceRepository`, `InvoiceProperties` (T03), `Clock` (T04).
  No dependency on `OutboxPublisher` or any cross-service client (task 6's own scope).

## Unit/integration tests required (Phase 10, not this phase)

- `InvoiceServiceTest` (plain JUnit, fixed `Clock`, mocked `InvoiceRepository`):
  - `shouldRejectInvoiceWithNonAllowlistedTokenReferenceShape` — unrecognized chain, blank
    chain, blank/null `tokenContractAddress`.
  - `shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat` — null, zero, negative, non-zero-
    scale, >78-digit amount.
  - `shouldCreateInvoiceWithOpenStateAndComputedExpiry` — valid call persists `OPEN`, correct
    `expiresAt`, non-null `invoiceUuid`, `createdAt`/`updatedAt` from the fixed `Clock`.
- `ArchitectureTest` (plain `@Test` canary shape, no `@ArchTest`):
  - `shouldRejectFloatingPointMoneyTypesAtArchUnit` — fields, method returns, and parameters.

## Execution order

1. `InvoiceState` (no dependencies).
2. `InvalidInvoiceRequestException` (no dependencies).
3. `Invoice` (depends on `InvoiceState`; schema-verified against `V1__payments_baseline.sql`
   before writing, mirroring `OutboxEvent`'s own T04 verification discipline).
4. `InvoiceRepository` (depends on `Invoice`).
5. `InvoiceService` (depends on 1-4, plus `InvoiceProperties`/`Clock`).
6. Compile-check (`mvn -pl services/payment -am compile`) before any test is written.
7. *(Phase 10)* `ArchitectureTest`, `InvoiceServiceTest`.
