# payment · T07 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T07 — Merchant-scoped reads |
| **Consumes** | — (entry phase) |

## 1. Architecture summary

As of T06, `payment-service` has a real `POST /api/v1/invoices` endpoint
(`InvoiceController`, `ROLE_MERCHANT`-protected), `InvoiceService.create(...)`, `Invoice`/
`InvoiceRepository` (package-private, no query methods beyond what `JpaRepository` provides),
and the `InvalidInvoiceRequestException`/`InvoiceExceptionHandler` pair (both package-private-
compatible — `InvoiceController` turned out to live in the *same* package as `InvoiceService`,
`com.themistra.payment.invoice`, making T05's own "made public because the future controller
lives elsewhere" reasoning factually stale, though harmless). T07 adds the first read paths.

## 2. Existing code this task touches

- **Existing, extended**: `InvoiceRepository` — needs two new query methods (merchant-scoped
  list, merchant-scoped single lookup). `InvoiceController` — gains two `@GetMapping` handlers.
  `InvoiceService` — gains the read methods (list/get), each merchant-scoped.
- **New**: a 404-mapped "not found or not yours" exception, following the exact established
  cross-service precedent below.
- **Reused as-is**: `Invoice`'s own getters (no entity change needed — every field T07 needs to
  expose already exists).

## 3. Established patterns to follow

- **The exact "404, not 403, for cross-owner access, no enumeration oracle" pattern already
  exists in this codebase**: `auth-service`'s own `SessionService.revokeOne` calls
  `familyRepository.findByFamilyIdAndPrincipalName(familyId, accountUuid.toString())
  .orElseThrow(SessionNotFoundException::new)` — the *same* exception for "doesn't exist" and
  "exists but isn't yours," by design, confirmed in `SessionNotFoundException`'s own Javadoc:
  "so a caller probing family ids they don't own can't distinguish 'doesn't exist' from 'exists
  but isn't yours' (no enumeration oracle)." `SessionNotFoundException` is package-private (same
  package as `SessionService`/`SessionExceptionHandler`) and has no-arg constructor — the direct
  template for T07's own equivalent.
- **Merchant/account identity from the JWT `sub` claim**: `InvoiceController` already has this
  (T06, `merchantAccountUuidFrom(Jwt)` private helper) — T07's new `@GetMapping` handlers reuse
  the identical extraction, not a new mechanism.
- **RFC 9457 mapping**: `InvoiceExceptionHandler` (T06) is the existing home for this module's
  domain exceptions; T07 adds one more `@ExceptionHandler` method to it, not a new class.

## 4. Testing conventions

- `InvoiceControllerTest` (T06, `@WebMvcTest` + `SecurityMockMvcRequestPostProcessors.jwt()`)
  is the direct template for the two new GET endpoints' own controller tests.
- `InvoiceServiceTest` (T05/T06, plain JUnit + Mockito) is the template for the new read-method
  unit tests.

## 5. Known gaps / unknowns (I do not know — not assumed)

1. **Exact `InvoiceResponse` shape for a GET.** The existing `InvoiceResponse` (T06) has only
   `invoiceUuid`/`payToAddress` — enough for `POST`'s own R1 text, but a merchant reading back
   an invoice would reasonably expect the full resource (state, amounts, chain, expiry, etc.).
   `contracts/api/payments.yaml` does not exist yet (deferred to a later task per this session's
   own prior investigation), so there is no contract to conform to yet. Reading: extend
   `InvoiceResponse` with the remaining fields and reuse it for `POST`'s own response too (a
   superset, not a breaking change — `invoiceUuid`/`payToAddress` stay present). Flagged for
   Phase 3 challenge.
2. **List endpoint ordering/pagination.** Task 7's own text says nothing about either. No
   pagination requirement is stated anywhere in `requirements.md` for this endpoint. Reading:
   simple `ORDER BY created_at DESC`, no pagination (not a named requirement, not worth
   introducing speculative infrastructure for).
3. **Is a new exception class needed, or does `InvalidInvoiceRequestException` get reused for
   the 404 case too?** No — that exception is `400`-mapped (R3/R4, malformed requests);
   conflating it with a `404` "not found or not yours" case would be a real semantic error, not
   a simplification. A new, `404`-mapped exception is required.

Phase 1 (Specification Extraction) resolves what the spec itself answers; anything still open
after that goes to Phase 3 (Kimi design challenge) as a named finding.
