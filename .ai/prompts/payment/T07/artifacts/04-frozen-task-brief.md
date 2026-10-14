STATUS: FROZEN (approved 2026-10-14, Human Approval gate)

# payment · T07 · Frozen Task Brief

## Phase 3 disposition (4 findings ACCEPTED, 3 REJECTED/deferred)

1. **`InvoiceResponse.amount` must be a decimal `String`.** ACCEPTED — a real violation of
   `agents.md`'s own standing rule ("On the wire, monetary values are decimal strings, never
   JSON numbers") that the Phase 2 TIB missed. `amount` is `invoice.getAmount().toPlainString()`,
   matching `RegisterWatchRequest`'s and `InvoiceCreatedPayload`'s own identical T06 convention.
2. **`watchId` excluded from the merchant-facing response.** ACCEPTED — it is the Crypto
   Service's own internal identifier, no merchant-facing meaning.
3. **No `contracts/api/payments.yaml` to contract-test against.** REJECTED (as an action for
   this task) — already established, separately, as task 24's own scope. Not authored early.
4. **`@Transactional(readOnly = true)` on both new read methods.** ACCEPTED.
5. **Named test references receipts, out of scope.** ACCEPTED — renamed to
   `shouldScopeInvoiceReadsToOwningMerchant`; a backlog note added for the receipt half once
   that resource exists.
6. **List endpoint is unbounded.** ACCEPTED as already-disclosed; no new action — not a named
   requirement, pagination is a future task's own scope if ever needed.
7. **Existing DB index doesn't optimally support the sort order.** REJECTED for this task —
   low severity, MVP-acceptable; a composite index is a reasonable future addition, not required
   now.

## Task

T07 — Merchant-scoped reads.

## Purpose

Let a merchant read back their own invoices — list and single-resource — scoped to the caller's
own account, with no way to distinguish "doesn't exist" from "exists but belongs to someone
else."

## Scope

**In:**
- `InvoiceRepository.findByMerchantAccountUuidOrderByCreatedAtDesc(UUID)` and
  `findByInvoiceUuidAndMerchantAccountUuid(UUID, UUID)`.
- `InvoiceService.listForMerchant(UUID)` and `getForMerchant(UUID, UUID)`, both
  `@Transactional(readOnly = true)` (Phase 3 finding 4); the latter throws
  `InvoiceNotFoundException` if absent or not owned.
- `InvoiceNotFoundException` (new, package-private) — mirrors `auth-service`'s own
  `SessionNotFoundException`.
- `InvoiceController.list(Jwt)` / `get(UUID, Jwt)`, both `@PreAuthorize("hasRole('MERCHANT')")`.
- `InvoiceExceptionHandler` gains one `@ExceptionHandler(InvoiceNotFoundException.class)` → `404`.
- `InvoiceResponse` extended to: `invoiceUuid`, `merchantAccountUuid`, `chain`,
  `tokenContractAddress`, `tokenDecimals`, `amount` (**String**, Phase 3 finding 1),
  `payToAddress`, `reference`, `state`, `expiresAt`, `createdAt`, `updatedAt` — **no `watchId`**
  (Phase 3 finding 2). A static `InvoiceResponse.from(Invoice)` mapper; `InvoiceController.create`
  switches to it too (superset, not breaking — `invoiceUuid`/`payToAddress` stay present).

**Out:**
- Receipts, `contracts/api/payments.yaml` (task 24), pagination, the composite index migration,
  `InvoiceExpirySweepJob` (task 8).

## Business Rules

- **R28** — both endpoints scoped to `sub`; cross-merchant single lookup → `404`, identical
  shape to a genuinely nonexistent UUID.

## Locked Decisions

- **L10** — held, directly implemented by this task.

## Dependencies

- `InvoiceController`'s existing `merchantAccountUuidFrom(Jwt)` helper (T06), reused.

## Inputs

- `auth-service`'s own `SessionService.revokeOne`/`SessionNotFoundException` template.

## Outputs

- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceNotFoundException.java` (new).
- `InvoiceRepository.java`, `InvoiceService.java`, `InvoiceController.java`,
  `InvoiceExceptionHandler.java`, `invoice/dto/InvoiceResponse.java` (modified).

## State Changes

None — both new methods are read-only.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceNotFoundException.java`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceReadTest.java`

## Files to Modify

- `InvoiceRepository.java`, `InvoiceService.java`, `InvoiceController.java`,
  `InvoiceExceptionHandler.java`, `invoice/dto/InvoiceResponse.java`,
  `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java`

## Files NOT to Modify

- `V1__payments_baseline.sql`, `V2`/`V3` migrations (immutable). Anything under `spec/`.

## Acceptance Criteria

- **AC1 (R28)** — list returns only the caller's own invoices.
- **AC2 (R28)** — single lookup: owner → `200` + full view; non-owner or nonexistent UUID →
  identical `404` shape.
- **AC3** — both endpoints require `ROLE_MERCHANT`.
- **AC4** — `InvoiceResponse.from(Invoice)` exposes the full merchant-facing field set, `amount`
  as a decimal string, no `watchId`.
- **AC5 (Phase 3 finding 4)** — `listForMerchant`/`getForMerchant` are
  `@Transactional(readOnly = true)`.

## Required Tests

- `shouldScopeInvoiceReadsToOwningMerchant` (R28, renamed per Phase 3 finding 5 — a backlog note
  covers the receipt half once that resource exists).
- `shouldReturnOnlyCallersOwnInvoicesFromList` (AC1).
- `shouldReturnInvoiceForOwningMerchant` / `shouldReturn404ForNonOwningMerchant` /
  `shouldReturn404ForNonexistentInvoiceUuid` (AC2, identical response shape for the latter two).
- `shouldRejectNonMerchantCallerOnReadEndpoints` (AC3).
- `shouldSerializeAmountAsDecimalStringNotJsonNumber` (AC4, Phase 3 finding 1).

## Constraints

- **Transaction:** both new service methods `readOnly = true`.
- **Security:** `@PreAuthorize("hasRole('MERCHANT')")`, matching the existing controller.
- **Null handling:** `Objects.requireNonNull` on both parameters, matching T05/T06's convention.

## Open Questions

None. All resolved above.
