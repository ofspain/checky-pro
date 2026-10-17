# payment · T22 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

No code in this artifact — planning only.

## Files to create

1. `services/payment/src/main/resources/db/migration/V10__payment_app_export_index.sql`
2. `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRow.java`
3. `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRepository.java`
4. `services/payment/src/main/java/com/themistra/payment/export/TransactionExportService.java`
5. `services/payment/src/main/java/com/themistra/payment/export/InvalidExportRequestException.java`
6. `services/payment/src/main/java/com/themistra/payment/export/ExportExceptionHandler.java`
7. `services/payment/src/main/java/com/themistra/payment/export/ExportController.java`

## Files to modify

None — this task adds a new, independent `export` module; no existing file is touched (confirmed
against the frozen brief's own "Files NOT to Modify").

## Public types/methods (signatures)

**`TransactionExportRow`** (record, frozen eleven-column shape, Phase 2/4):
```java
public record TransactionExportRow(
        UUID invoiceUuid, UUID paymentUuid, UUID receiptUuid,
        String chain, String txHash, String tokenContractAddress, short tokenDecimals,
        String amount, String payToAddress,
        Instant finalizedAt, Instant attestedAt, String receiptUri) {
}
```

**`TransactionExportRepository`** (`@Repository`, plain class — no entity, no `JpaRepository`):
```java
@Repository
class TransactionExportRepository {
    TransactionExportRepository(EntityManager entityManager) { ... }
    List<TransactionExportRow> findAttestedTransactions(UUID merchantAccountUuid, Instant from, Instant to) { ... }
}
```

**`TransactionExportService`** (`@Service`):
```java
@Service
public class TransactionExportService {
    TransactionExportService(TransactionExportRepository repository) { ... }
    @Transactional(readOnly = true)
    public List<TransactionExportRow> export(UUID merchantAccountUuid, Instant from, Instant to) { ... }
}
```

**`InvalidExportRequestException`** (mirrors `InvalidInvoiceRequestException` exactly):
```java
public class InvalidExportRequestException extends RuntimeException {
    public InvalidExportRequestException(String message) { super(message); }
}
```

**`ExportExceptionHandler`** (mirrors `InvoiceExceptionHandler` exactly):
```java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExportExceptionHandler {
    @ExceptionHandler(InvalidExportRequestException.class)
    ProblemDetail onInvalidRequest(InvalidExportRequestException e) { ... }
}
```

**`ExportController`** (`@RestController`):
```java
@RestController
@RequestMapping("/api/v1/exports/transactions")
public class ExportController {
    ExportController(TransactionExportService service) { ... }

    @GetMapping
    @PreAuthorize("hasRole('MERCHANT')")
    ResponseEntity<?> export(@RequestParam Instant from, @RequestParam Instant to,
                              @RequestParam(required = false) String format,
                              @RequestHeader(value = "Accept", required = false) String accept,
                              @AuthenticationPrincipal Jwt jwt) { ... }
}
```
(`from`/`to` as `@RequestParam Instant` lets Spring's own converter throw
`MethodArgumentTypeMismatchException` on a malformed value — already mapped to 400 by
`ApiExceptionHandler`, Finding 7's rejected disposition.)

## Private methods

- `ExportController`: `private static UUID merchantAccountUuidFrom(Jwt jwt)` — duplicated from
  `InvoiceController`'s own identical private helper (established "duplicate a small private
  helper per module" precedent, not shared plumbing).
- `ExportController`: `private static boolean wantsCsv(String format, String accept)` — resolves
  the frozen `format`-over-`Accept` precedence (Phase 4); throws `InvalidExportRequestException`
  if `format` is present and not exactly `csv`/`json` (case-insensitive).
- `ExportController`: `private static String toCsv(List<TransactionExportRow> rows)` — header +
  one escaped line per row.
- `ExportController`: `private static String csvField(String value)` — the frozen minimal RFC
  4180 escaper (Finding 1): wraps in `"..."` and doubles embedded quotes if the value contains
  `,`, `"`, or `\n`; otherwise returned unchanged.
- `TransactionExportRepository`: `private static TransactionExportRow toRow(Object[] row)` — maps
  one native-query result row to the frozen DTO shape, casting each column explicitly (Phase 0's
  own flagged "confirm empirically" unknown — resolved here in Phase 6, not assumed).

## Entities used

None. `Invoice`/`Payment`/`Receipt` are read only via raw column names in the native query — no
entity class imported anywhere in `export` (L12).

## Repositories used

`TransactionExportRepository` (new, this task's own). No existing repository is called — the
frozen brief's own "no `InvoiceRepository`/`PaymentRepository`/`ReceiptRepository` call" shape
(a direct three-table join, not a service-orchestrated assembly) was the Phase 2 design choice,
unchanged through Phase 4.

## Unit/integration tests required

A real Testcontainers test (the query is a multi-table join that cannot be meaningfully proven
against a mock) plus a couple of pure-unit tests for the CSV escaper and format-resolution logic
that need no database:

**`TransactionExportServiceIntegrationTest`** (or equivalently named; exact class name decided at
Phase 10):
1. `shouldExportTaxReadyHistoryForMerchantAndDateRange` (named, R27) — seed one `ATTESTED`
   payment end-to-end (via the same `LedgerService`/`ReceiptService` real path T17/T19's own
   tests use, not hand-inserted rows) for merchant A inside `[from, to]`; assert all eleven
   columns match exactly, including `receiptUri`'s frozen formula.
2. A second merchant's otherwise-matching payment is excluded (AC1/AC8).
3. A payment outside `[from, to]` is excluded; one exactly on each boundary is included
   (AC2, inclusive-range proof).
4. A `FINALIZED`-not-yet-`ATTESTED` payment is excluded (AC2/AC8, L2 continuity).
5. Empty result for a merchant/range with nothing matching (AC6).
6. Two matching payments seeded out of `finalizedAt` order are returned in ascending order,
   tie-broken by `paymentUuid` (AC10).
7. `amount`/`payToAddress` reflect `payments`, not `invoices`, using a seeded `OVERPAID`/
   `UNDERPAID` discrepancy or a payment to a different observed address than the invoice's
   expected one (AC9) — proves Finding 2's correction, not merely documents it.
8. A row with a deliberately comma-containing `payToAddress` (inserted via a raw admin
   connection, mirroring T20's own malformed-row-simulation precedent) round-trips as a
   structurally valid CSV row when escaped (AC12).

**Controller-level tests (`ExportControllerTest` or `@WebMvcTest`-style, exact shape at Phase
10):**
9. `format=csv`, `Accept: text/csv`, default JSON, and `format` overriding a conflicting
   `Accept` header (AC5).
10. `format=xml` → 400 (AC11).
11. Missing `from`, missing `to`, `from > to`, and a malformed/missing `sub` claim → 400 (AC7).
12. A malformed (non-Instant) `from`/`to` value → 400 via the existing generic handler (confirms
    Finding 7's rejected disposition still yields correct behavior, not just "unchanged").
13. `InvalidExportRequestException` → RFC 9457 400 with `title = "Invalid export request"`, not
    a 500 (AC13).

## Execution order

1. `V10__payment_app_export_index.sql` — no code dependency, front-loaded per the standard rule.
2. `TransactionExportRow` — no dependency.
3. `TransactionExportRepository` — depends on 2.
4. `TransactionExportService` — depends on 3.
5. `InvalidExportRequestException` — no dependency.
6. `ExportExceptionHandler` — depends on 5.
7. `ExportController` — depends on 4 and 5.
8. All test files (deferred to Phase 10, per the standard rule — this task is not test-only).

## Traceability check

Every file above appears in the frozen brief's own "Files to Create" list (Phase 4); no file is
added that the brief does not authorize, and no existing file is modified.
