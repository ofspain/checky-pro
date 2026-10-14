# payment · T07 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T07/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `InvoiceServiceReadTest.java`, `InvoiceControllerTest.java`.

---

## Executive summary

- `mvn -pl services/payment test`: **155 tests run, 0 failures**.
- Phase 10 closed the Phase 8 coverage holes well: read-path controller tests now cover the full JSON shape, `amount` as a decimal string, absence of `watchId`, identical 404 bodies, and role rejection on `GET` endpoints.
- A few gaps remain — mostly around ordering, unauthenticated read paths, malformed path variables, and verifying service-method invocation. None are blockers.

---

## Gap 1 — List ordering is not actually exercised

- **Why it matters:** The repository method is named `findByMerchantAccountUuidOrderByCreatedAtDesc`, but every test returns a list of length 0 or 1. A regression that dropped or reversed the `OrderByCreatedAtDesc` clause would leave all tests green.
- **Suggested test:** In `InvoiceServiceReadTest`, add `shouldReturnInvoicesOrderedByCreatedAtDescending` that stubs the repository with two invoices having different `createdAt` values and asserts the returned list is newest-first.

---

## Gap 2 — `shouldReturnInvoiceForOwningMerchant` does not assert the full field set

- **Why it matters:** The list test already exercises the full field set, but the single-resource happy path only asserts `invoiceUuid`, `amount`, and the absence of `watchId`. A bug that dropped, say, `reference` or `state` from the single-resource mapper path would not be caught.
- **Suggested test:** Extend `shouldReturnInvoiceForOwningMerchant` to mirror the JSON-path assertions in `shouldReturnOnlyCallersOwnInvoicesFromList` (merchant UUID, chain, token, decimals, pay address, reference, state, expiresAt, createdAt, updatedAt).

---

## Gap 3 — Read-endpoint role-rejection test only checks status

- **Why it matters:** `shouldRejectNonMerchantCallerOnReadEndpoints` asserts `403` but not the RFC 9457 body shape. The POST equivalent already checks `type`/`title`/`status`, but a method-security misconfiguration on the `GET` paths could still return a generic body.
- **Suggested test:** Add `andExpect(content().contentTypeCompatibleWith("application/problem+json"))` plus `jsonPath("$.type")`, `$.title`, and `$.status` assertions to both `GET` calls.

---

## Gap 4 — No test for malformed `invoiceUuid` path variable

- **Why it matters:** `GET /api/v1/invoices/{invoiceUuid}` uses `@PathVariable UUID invoiceUuid`. A non-UUID segment (e.g., `not-a-uuid`) should return `400` via `ApiExceptionHandler.onTypeMismatch`, but this path is not exercised.
- **Suggested test:** Add `shouldReturn400ForMalformedInvoiceUuid` in `InvoiceControllerTest` performing `GET /api/v1/invoices/not-a-uuid` and asserting `400` with the problem+json body.

---

## Gap 5 — No test for unauthenticated callers on read endpoints

- **Why it matters:** `shouldRejectUnauthenticatedCallerWithUnauthorized` covers only `POST`. Unauthenticated `GET` requests should also return `401`, but there is no test proving the security filter applies to the read paths.
- **Suggested test:** Add `shouldRejectUnauthenticatedCallerOnReadEndpoints` performing both `GET /api/v1/invoices` and `GET /api/v1/invoices/{invoiceUuid}` without a JWT and asserting `401`.

---

## Gap 6 — Controller read tests do not verify service is called with the right arguments

- **Why it matters:** The tests mock `InvoiceService` and assert the response, but they never verify that `listForMerchant` was called with the JWT-derived merchant UUID or that `getForMerchant` received the path UUID. A controller that silently returned hard-coded data would pass.
- **Suggested test:** Add `verify(invoiceService).listForMerchant(MERCHANT_ACCOUNT_UUID)` to `shouldReturnOnlyCallersOwnInvoicesFromList` and `verify(invoiceService).getForMerchant(MERCHANT_ACCOUNT_UUID, INVOICE_UUID)` to `shouldReturnInvoiceForOwningMerchant`.

---

## Gap 7 — Service read tests do not verify repository call arguments

- **Why it matters:** `InvoiceServiceReadTest` stubs the repository and asserts the returned value, but it does not explicitly `verify` that `findByMerchantAccountUuidOrderByCreatedAtDesc` / `findByInvoiceUuidAndMerchantAccountUuid` were called with the expected UUIDs.
- **Suggested test:** Add `verify(repository).findByMerchantAccountUuidOrderByCreatedAtDesc(MERCHANT_ACCOUNT_UUID)` and `verify(repository).findByInvoiceUuidAndMerchantAccountUuid(INVOICE_UUID, MERCHANT_ACCOUNT_UUID)` in the relevant tests.

---

## Gap 8 — No test for `reference: null` in the response body

- **Why it matters:** `reference` is optional in the request and nullable in the entity. The current tests always use a non-null reference, so it is unclear whether the response omits the field or serializes it as `null` — a distinction clients may care about.
- **Suggested test:** Add a controller test with an invoice whose reference is `null` and assert the JSON shape (either `"reference": null` or field absent, whichever is the intended contract).

---

## Positive confirmations

- `shouldReturnOnlyCallersOwnInvoicesFromList` is strong: it asserts the full field set, decimal-string `amount`, and `watchId` absence.
- `shouldReturnIdentical404ShapeForNonOwningMerchantAndNonexistentInvoiceUuid` correctly compares response bodies after normalizing the UUID, guarding the anti-enumeration requirement.
- `InvoiceServiceReadTest` cleanly separates read-path unit tests from the larger T05/T06 service test file.
- Mutation checks performed in Phase 10 (adding `watchId` back, adding non-deterministic `detail`) confirm the tests fail on real regressions.
