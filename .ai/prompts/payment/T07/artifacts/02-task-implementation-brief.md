# payment · T07 · Phase 2 — Task Implementation Brief

## Task

T07 — Merchant-scoped reads.

## Purpose

Let a merchant read back their own invoices — a list and a single-resource lookup — both
scoped to the caller's own account, with no way to distinguish "doesn't exist" from "exists but
belongs to someone else."

## Scope

**In:**
- `InvoiceRepository.findByMerchantAccountUuidOrderByCreatedAtDesc(UUID)` and
  `findByInvoiceUuidAndMerchantAccountUuid(UUID, UUID)` (new query methods).
- `InvoiceService.listForMerchant(UUID merchantAccountUuid)` and
  `getForMerchant(UUID merchantAccountUuid, UUID invoiceUuid)` (the latter throws
  `InvoiceNotFoundException` if absent or not owned).
- `InvoiceNotFoundException` (new, package-private, `invoice`) — mirrors `auth-service`'s own
  `SessionNotFoundException` exactly: no-arg constructor, same exception for "doesn't exist" and
  "not yours."
- `InvoiceController.list(Jwt)` (`GET /api/v1/invoices`) and `get(UUID, Jwt)`
  (`GET /api/v1/invoices/{invoiceUuid}`), both `@PreAuthorize("hasRole('MERCHANT')")`.
- `InvoiceExceptionHandler` gains one `@ExceptionHandler(InvoiceNotFoundException.class)` → `404`.
- `InvoiceResponse` extended to a full view (superset of its current two fields) with a static
  `InvoiceResponse.from(Invoice)` mapper; `InvoiceController.create`'s own existing response
  construction switches to this mapper too (same DTO, no breaking change — `invoiceUuid`/
  `payToAddress` stay present).

**Out:**
- Receipts (no resource exists yet — R28's own receipt half is a later task).
- `InvoiceExpirySweepJob` (task 8).
- Pagination (not a named requirement).

## Business Rules

- **R28** — both endpoints scoped to the caller's `sub`; cross-merchant single lookup → `404`.

## Locked Decisions

- **L10** — merchant-scoped reads, `404` not `403` for cross-merchant.

## Dependencies

- `InvoiceController`'s existing `merchantAccountUuidFrom(Jwt)` helper (T06) — reused as-is.

## Inputs

- `auth-service`'s own `SessionService.revokeOne`/`SessionNotFoundException` — exact template
  for the scoped-lookup-with-no-enumeration-oracle pattern.

## Outputs

- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceNotFoundException.java` (new).
- `InvoiceRepository.java`, `InvoiceService.java`, `InvoiceController.java`,
  `InvoiceExceptionHandler.java`, `invoice/dto/InvoiceResponse.java` (modified).

## State Changes

None — both new methods are read-only.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceNotFoundException.java`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceReadTest.java` (Phase 10; extending the existing `InvoiceServiceTest` was considered and rejected — that file is already large and T05/T06-scoped, a dedicated file keeps the read-path tests discoverable)
- (Controller read-path tests land inside the existing `InvoiceControllerTest.java`, Phase 10 — not a new file, same controller.)

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/dto/InvoiceResponse.java`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java` (new read-path tests)

## Files NOT to Modify

- `V1__payments_baseline.sql`, `V2`/`V3` migrations (immutable).
- Anything under `spec/`.

## Acceptance Criteria

- **AC1 (R28)** — list returns only the caller's own invoices.
- **AC2 (R28)** — single lookup: owner → `200` + full invoice view; non-owner or nonexistent
  UUID → identical `404` shape.
- **AC3** — both endpoints require `ROLE_MERCHANT`.
- **AC4** — `InvoiceResponse.from(Invoice)` exposes the full field set; `POST`'s own response
  now uses the same mapper.

## Required Tests

- `shouldScopeInvoiceAndReceiptReadsToOwningMerchant` (R28, named).
- `shouldReturnOnlyCallersOwnInvoicesFromList` (AC1).
- `shouldReturnInvoiceForOwningMerchant` / `shouldReturn404ForNonOwningMerchant` /
  `shouldReturn404ForNonexistentInvoiceUuid` (AC2) — the latter two assert the identical response
  shape, proving no enumeration oracle.
- `shouldRejectNonMerchantCallerOnReadEndpoints` (AC3).

## Constraints

- **Security:** both new endpoints `@PreAuthorize("hasRole('MERCHANT')")`, matching the
  existing controller-wide posture.
- **Module boundaries:** no change — everything stays inside `invoice`.
- **Null handling:** `InvoiceService`'s two new methods null-guard `merchantAccountUuid`/
  `invoiceUuid` the same way `create` already does (`Objects.requireNonNull`), consistent with
  T05/T06's own established defensive-guard convention.

## Open Questions

No blockers.
