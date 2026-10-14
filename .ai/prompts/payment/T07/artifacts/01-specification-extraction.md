# payment · T07 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T07 — Merchant-scoped reads |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

- **R28** — `GET /api/v1/invoices` and `GET /api/v1/invoices/{invoiceUuid}` return only the
  caller's own invoices (scoped by JWT `sub`); a cross-merchant single-resource lookup returns
  `404`, never `403` (the receipt half of R28 is a later task's scope — no receipt resource
  exists yet).

## Locked Decisions

- **L10** — merchant-scoped reads filtered to the caller's account UUID (`sub`), `404` for
  cross-merchant access. Directly implements this task.
- **L12** — `invoice` package owns `Invoice`/its exceptions; no change to module boundaries.

## Files involved

**New:**
- A `404`-mapped "not found or not owned by caller" exception (package-private, `invoice`),
  mirroring `auth-service`'s own `SessionNotFoundException`.

**Existing, extended:**
- `InvoiceRepository` — two new query methods.
- `InvoiceService` — two new read methods, both merchant-scoped.
- `InvoiceController` — two new `@GetMapping` handlers.
- `InvoiceExceptionHandler` — one new `@ExceptionHandler` method.
- `InvoiceResponse` (T06) — extended with the remaining invoice fields (superset, not breaking).

**Not to modify:** `V1__payments_baseline.sql`, `V2`/`V3` migrations, anything under `spec/`.

## Dependencies

- `InvoiceRepository`, `Invoice` (T05).
- `InvoiceController`'s own existing `merchantAccountUuidFrom(Jwt)` helper (T06) — reused, not
  duplicated.

## Acceptance Criteria

- **AC1 (R28)** — `GET /api/v1/invoices` returns only invoices whose `merchantAccountUuid`
  matches the caller's `sub`.
- **AC2 (R28)** — `GET /api/v1/invoices/{invoiceUuid}` returns the invoice if (and only if) it
  belongs to the caller; otherwise `404`, identical response shape whether the UUID doesn't
  exist at all or belongs to a different merchant (no enumeration oracle).
- **AC3** — both endpoints require `ROLE_MERCHANT` (the existing `@PreAuthorize` convention on
  this controller — T07 doesn't introduce a new security posture, just extends the existing one).
- **AC4** — `InvoiceResponse` exposes the full invoice view (state, chain, tokenContractAddress,
  tokenDecimals, amount, payToAddress, reference, expiresAt, createdAt), reused by `POST`'s own
  response too.

## Tests required

- `shouldScopeInvoiceAndReceiptReadsToOwningMerchant` (R28) — named, `package.md` §8 (the
  receipt half is out of this task's own scope — no receipt resource exists yet).
- Implied: list returns only the caller's own invoices, excluding another merchant's; single-
  invoice lookup succeeds for the owner; single-invoice lookup returns `404` for a different
  merchant's invoice; single-invoice lookup returns `404` for a genuinely nonexistent UUID
  (same response shape as the cross-merchant case); both endpoints reject a non-`MERCHANT`
  caller.

## Open Questions

None — all three Phase 0 unknowns are resolved above with direct evidence; flagged for Phase 3
challenge if any resolution is wrong, not treated as settled beyond challenge.
