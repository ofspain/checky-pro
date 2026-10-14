# payment · T07 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T07 — Merchant-scoped reads |
| **Consumes** | `artifacts/05-implementation-plan.md` |

## Files created

- `invoice/InvoiceNotFoundException.java` — package-private, no-arg, mirrors `auth-service`'s
  own `SessionNotFoundException`.

## Files modified

- `invoice/dto/InvoiceResponse.java` — extended to the full merchant-facing field set; `amount`
  is `invoice.getAmount().toPlainString()` (Phase 3 finding 1); `watchId` deliberately excluded
  (Phase 3 finding 2). Added static `InvoiceResponse.from(Invoice)`.
- `invoice/InvoiceRepository.java` — `findByMerchantAccountUuidOrderByCreatedAtDesc`,
  `findByInvoiceUuidAndMerchantAccountUuid`.
- `invoice/InvoiceService.java` — `listForMerchant`/`getForMerchant`, both
  `@Transactional(readOnly = true)` (Phase 3 finding 4).
- `invoice/InvoiceController.java` — `list(Jwt)`/`get(UUID, Jwt)`, both
  `@PreAuthorize("hasRole('MERCHANT')")`; `create`'s own response construction switched to
  `InvoiceResponse.from(...)`.
- `invoice/InvoiceExceptionHandler.java` — `InvoiceNotFoundException` → `404`, no detail message
  (no signal distinguishing "doesn't exist" from "not yours").

## Not written this phase (deliberately deferred to Phase 10)

- `InvoiceServiceReadTest.java` (new file) and the `InvoiceControllerTest.java` read-path
  additions.

## Verification performed this phase

- `mvn -pl services/payment -am compile` — clean.
- `mvn -pl services/payment -am test -Dtest='!SchemaBaselineMigrationIntegrationTest'` —
  **123/123 passing, unchanged** from before this task — confirms `InvoiceResponse.from(...)`'s
  switch-in for `create`'s own response didn't break `shouldCreateInvoiceAndRegisterWatch`'s
  existing `invoiceUuid`/`payToAddress` JSON-path assertions (the superset shape still carries
  both under their original names).

## Traceability to frozen brief

- AC1/AC2 (R28) — implemented; not yet unit/controller-tested (Phase 10).
- AC3 — both new endpoints `@PreAuthorize("hasRole('MERCHANT')")`.
- AC4 — `InvoiceResponse.from` exposes the full field set, `amount` as `String`, no `watchId`.
- AC5 — both new service methods `@Transactional(readOnly = true)`.

No code deviates from the frozen brief.
