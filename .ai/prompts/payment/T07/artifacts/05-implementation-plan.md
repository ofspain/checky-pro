# payment · T07 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T07 — Merchant-scoped reads |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceNotFoundException.java`

(`InvoiceServiceReadTest.java` and the `InvoiceControllerTest.java` additions are Phase 10.)

## Files to modify

1. `invoice/dto/InvoiceResponse.java`
2. `invoice/InvoiceRepository.java`
3. `invoice/InvoiceService.java`
4. `invoice/InvoiceController.java`
5. `invoice/InvoiceExceptionHandler.java`

## Public methods (signatures)

- `InvoiceNotFoundException()` — package-private, no-arg, extends `RuntimeException`.
- `InvoiceResponse(UUID invoiceUuid, UUID merchantAccountUuid, String chain, String
  tokenContractAddress, short tokenDecimals, String amount, String payToAddress, String
  reference, InvoiceState state, Instant expiresAt, Instant createdAt, Instant updatedAt)` —
  record, extended shape.
- `InvoiceResponse.from(Invoice invoice): InvoiceResponse` — static mapper;
  `amount = invoice.getAmount().toPlainString()`.
- `InvoiceRepository.findByMerchantAccountUuidOrderByCreatedAtDesc(UUID merchantAccountUuid):
  List<Invoice>`.
- `InvoiceRepository.findByInvoiceUuidAndMerchantAccountUuid(UUID invoiceUuid, UUID
  merchantAccountUuid): Optional<Invoice>`.
- `InvoiceService.listForMerchant(UUID merchantAccountUuid): List<Invoice>` —
  `@Transactional(readOnly = true)`.
- `InvoiceService.getForMerchant(UUID merchantAccountUuid, UUID invoiceUuid): Invoice` —
  `@Transactional(readOnly = true)`; throws `InvoiceNotFoundException`.
- `InvoiceController.list(Jwt jwt): List<InvoiceResponse>` — `@GetMapping`,
  `@PreAuthorize("hasRole('MERCHANT')")`.
- `InvoiceController.get(UUID invoiceUuid, Jwt jwt): InvoiceResponse` —
  `@GetMapping("/{invoiceUuid}")`, `@PreAuthorize("hasRole('MERCHANT')")`.
- `InvoiceExceptionHandler.onInvoiceNotFound(InvoiceNotFoundException): ProblemDetail` → `404`.

## Private methods

- None new beyond the existing `InvoiceController.merchantAccountUuidFrom(Jwt)` (T06, reused
  as-is by both new handlers).

## Entities used

- `Invoice` (unchanged).

## Repositories used

- `InvoiceRepository` (extended).

## Services used

- `InvoiceController` depends on `InvoiceService` (unchanged dependency, new methods called).

## Unit/integration tests required (Phase 10, not this phase)

- `InvoiceServiceReadTest` (plain JUnit, Mockito): `shouldScopeInvoiceReadsToOwningMerchant`,
  `shouldReturnOnlyCallersOwnInvoicesFromList`, `shouldReturnInvoiceForOwningMerchant`,
  `shouldReturn404ForNonOwningMerchant`/`shouldReturn404ForNonexistentInvoiceUuid` (identical
  exception type/shape), null-guard tests for both methods' two parameters.
- `InvoiceControllerTest` additions: `shouldRejectNonMerchantCallerOnReadEndpoints`,
  `shouldSerializeAmountAsDecimalStringNotJsonNumber` (real JSON body assertion, not just the
  Java object), a `404` body-shape test for the controller's own `InvoiceNotFoundException` →
  `InvoiceExceptionHandler` path.

## Execution order

1. `invoice/dto/InvoiceResponse.java` (extend + add `from(Invoice)`; no dependency on anything
   new).
2. `invoice/InvoiceNotFoundException.java`.
3. `invoice/InvoiceRepository.java` (add the two query methods).
4. `invoice/InvoiceService.java` (add `listForMerchant`/`getForMerchant`).
5. `invoice/InvoiceController.java` (add the two `@GetMapping` handlers; switch `create`'s own
   response construction to `InvoiceResponse.from(...)`).
6. `invoice/InvoiceExceptionHandler.java` (add the `404` mapping).
7. Compile-check (`mvn -pl services/payment -am compile`) before any test is written.
8. *(Phase 10)* `InvoiceServiceReadTest`, `InvoiceControllerTest` additions.
