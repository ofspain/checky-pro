# payment · T07 · Phase 11 — Test Review Resolution

Consumes `artifacts/11-test-review.md` (Kimi 2.7). All 8 gaps accepted and fixed — none
required a human disposition decision (no code defects, only test-coverage gaps with an
obvious, uncontroversial fix).

## Gap 1 — List ordering not exercised

**FIXED.** Added `shouldPreserveRepositoryOrderingNewestFirst` (`InvoiceServiceReadTest`) —
stubs two invoices with different `createdAt` values in newest-first order, asserts the
service's own pass-through preserves it.

## Gap 2 — Single-resource happy path doesn't assert the full field set

**FIXED.** Extended `shouldReturnInvoiceForOwningMerchant` with the same JSON-path assertions
`shouldReturnOnlyCallersOwnInvoicesFromList` already has.

## Gap 3 — Role-rejection test only checks status

**FIXED.** Added `Content-Type`/`type`/`title`/`status` assertions to both `GET` calls in
`shouldRejectNonMerchantCallerOnReadEndpoints`.

## Gap 4 — No test for a malformed `invoiceUuid` path variable

**FIXED.** Added `shouldReturn400ForMalformedInvoiceUuidPathVariable`.

## Gap 5 — No test for unauthenticated callers on read endpoints

**FIXED.** Added `shouldRejectUnauthenticatedCallerOnReadEndpoints`.

## Gap 6 — Controller tests don't verify service invocation arguments

**FIXED.** Added `verify(invoiceService).listForMerchant(...)` /
`verify(invoiceService).getForMerchant(...)` to the respective happy-path tests.

## Gap 7 — Service tests don't verify repository invocation arguments

**FIXED.** Added `verify(repository)...` to `shouldReturnOnlyCallersOwnInvoicesFromList` and
`shouldReturnInvoiceForOwningMerchant`.

## Gap 8 — No test for `reference: null` in the response body

**FIXED.** Added `shouldSerializeNullReferenceAsJsonNull` — confirms the field serializes as
JSON `null`, not omitted.

## Verification performed this phase

- `mvn -pl services/payment -am test -Dtest='InvoiceServiceReadTest,InvoiceControllerTest'` —
  27/27 passing (up from 23).
- Mutation check 1: reversed `listForMerchant`'s own pass-through order, confirmed
  `shouldPreserveRepositoryOrderingNewestFirst` fails, reverted via `Edit`, confirmed clean.
- Gap 4's own mutation (changing the path-variable type to bypass the `UUID` conversion) was
  not attempted — it would require a disproportionate cascading type change through
  `InvoiceService.getForMerchant`, and the underlying `MethodArgumentTypeMismatchException` →
  `400` handling was already mutation-verified in T06; this test confirms the same established
  mechanism applies to this controller's own new path variable, not a new mechanism.
- Full non-Docker-dependent suite: **141/141 passing** (up from 137).

## Net result

All 8 findings fixed. No production code changed. Proceeding to Phase 12.
