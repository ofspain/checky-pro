# payment · T07 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T07 — Merchant-scoped reads |
| **Consumes** | `artifacts/06-implementation-notes.md` |

Findings only — no fixes applied here (Phase 9).

## Review checklist

- **Correctness/no enumeration oracle:** `InvoiceExceptionHandler.onInvoiceNotFound` returns an
  identical body (no `detail`, same `title`/`status`/`type`) regardless of whether the UUID
  doesn't exist at all or belongs to a different merchant — traced directly, both paths
  terminate in the same `.orElseThrow(InvoiceNotFoundException::new)`, no branch distinguishes
  them anywhere in `InvoiceService.getForMerchant`.
- **`UUID` uniqueness assumption:** `InvoiceRepository.findByInvoiceUuidAndMerchantAccountUuid`
  returns `Optional<Invoice>`, not `List<Invoice>` — correct, `invoice_uuid` is
  `UNIQUE` in the real schema (`V1__payments_baseline.sql`, confirmed by direct read), so at
  most one row can ever match regardless of the second predicate.
- **Malformed path variable:** `GET /api/v1/invoices/{invoiceUuid}` with a non-UUID path segment
  — `@PathVariable UUID invoiceUuid` throws `MethodArgumentTypeMismatchException`, already
  mapped to `400` by the existing `ApiExceptionHandler.onTypeMismatch` (T06) — no new gap, no
  new handler needed.
- **Lazy-loading risk under `readOnly = true`:** `Invoice` has no lazy associations/collections
  of its own, so returning it from a `@Transactional(readOnly = true)` method and mapping it to
  a DTO afterward (inside `InvoiceController`, technically outside the transaction by the time
  `InvoiceResponse.from` runs) carries no `LazyInitializationException` risk — confirmed by
  re-reading `Invoice.java`'s own field list, no relationship fields exist.
- **Null handling:** both new service methods null-guard their own parameters directly; the
  controller's existing `merchantAccountUuidFrom(Jwt)` (T06) already guarantees a non-null value
  before either is ever called — no redundant-but-wrong assumption here.
- **Thread-safety:** no mutable state introduced; both new service methods are stateless reads.
- **Response shape regression:** `InvoiceController.create`'s own existing response construction
  switched from a direct two-arg `InvoiceResponse` constructor call to `InvoiceResponse.from(...)`
  — re-verified the extended record still serializes `invoiceUuid`/`payToAddress` under their
  original field names (no `@JsonProperty` renames anywhere), so T06's own existing
  `shouldCreateInvoiceAndRegisterWatch` JSON-path assertions keep passing without modification
  (confirmed: 123/123 unchanged in Phase 6's own verification).

## No findings

Nothing to disclose this phase — the implementation matches the frozen brief exactly, and the
checklist above surfaced no real correctness, security, or consistency gap.

## Verdict

No fixes required before Phase 8.
