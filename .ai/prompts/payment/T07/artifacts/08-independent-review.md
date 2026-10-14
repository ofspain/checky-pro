# payment · T07 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T07/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, current implementation, and test files.

---

## Executive summary

- `mvn -pl services/payment test`: **141 tests run, 0 failures**.
- The implementation matches the frozen brief: `@Transactional(readOnly = true)` on reads, `amount` serialized as a decimal string, `watchId` excluded, and cross-merchant lookup returns a uniform `404`.
- One major coverage gap: **the new `GET /api/v1/invoices` and `GET /api/v1/invoices/{invoiceUuid}` endpoints have no automated tests at all.** `InvoiceControllerTest` still contains only `POST` tests, and the required `InvoiceServiceReadTest.java` does not exist. The read-path acceptance criteria (AC1–AC4) are therefore not regression-protected.

---

## 1. Read endpoints have zero automated tests

- **Issue:** `InvoiceController` now exposes `GET /api/v1/invoices` and `GET /api/v1/invoices/{invoiceUuid}`, and `InvoiceService` exposes `listForMerchant` / `getForMerchant`. Neither the controller nor the service read paths are exercised by any test. The required test file `InvoiceServiceReadTest.java` (listed in the frozen brief §Files to Create) is missing, and `InvoiceControllerTest.java` contains only `POST` tests.
- **Severity:** High — implementation is correct but unguarded; future regressions on R28/AC1–AC4 would not fail CI.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java:70-89` (new `list` / `get` methods)
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:76-92` (new read methods)
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java` — all 9 tests are `POST`-only
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceReadTest.java` — does not exist
  - Frozen brief §Required Tests lists `shouldScopeInvoiceReadsToOwningMerchant`, `shouldReturnOnlyCallersOwnInvoicesFromList`, `shouldReturnInvoiceForOwningMerchant`, `shouldReturn404ForNonOwningMerchant`, `shouldReturn404ForNonexistentInvoiceUuid`, `shouldRejectNonMerchantCallerOnReadEndpoints`, and `shouldSerializeAmountAsDecimalStringNotJsonNumber` — none are present.
- **Recommendation:** Create `InvoiceServiceReadTest.java` and add the missing read-path tests to `InvoiceControllerTest.java` in Phase 10.
- **Confidence:** High.

---

## 2. Full-view `InvoiceResponse` mapper is not exercised in controller tests

- **Issue:** The existing `shouldCreateInvoiceAndRegisterWatch` only asserts `$.invoiceUuid` and `$.payToAddress`. Because the GET tests are missing, no test verifies that `InvoiceResponse.from(Invoice)` actually serializes the new fields (`merchantAccountUuid`, `chain`, `tokenContractAddress`, `tokenDecimals`, `amount` as string, `state`, `expiresAt`, `createdAt`, `updatedAt`) or that `watchId` is absent.
- **Severity:** Medium — a regression that accidentally drops a field, changes `amount` to a number, or leaks `watchId` would go unnoticed.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java:102-107`
  - `services/payment/src/main/java/com/themistra/payment/invoice/dto/InvoiceResponse.java:17-47`
- **Recommendation:** Extend `shouldCreateInvoiceAndRegisterWatch` (or add a dedicated test) to assert the full JSON shape, including `$.amount` is a quoted string and the response does not contain `watchId`.
- **Confidence:** High.

---

## 3. `InvoiceController.list` returns a bare JSON array

- **Issue:** `list` returns `List<InvoiceResponse>` directly, so the response body is a JSON array. This is fine for the current scope, but it leaves no natural place for future pagination metadata (`total`, `next`, etc.) without a breaking change.
- **Severity:** Low — the brief explicitly excludes pagination, but this is a forward-compatibility note.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java:73-78`
- **Recommendation:** Document in the brief (or a backlog note) that the list response is intentionally a bare array and that introducing pagination later will require a wrapper-object breaking change.
- **Confidence:** Medium.

---

## 4. Role-enforcement test only covers `POST`

- **Issue:** `shouldRejectNonMerchantCallerWithForbidden` exercises `POST /api/v1/invoices`. The `@PreAuthorize` annotation is method-level, so a copy-paste error or future refactor that accidentally removed it from `list` or `get` would not be caught.
- **Severity:** Low.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java:150-159`
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java:71-89`
- **Recommendation:** Add `shouldRejectNonMerchantCallerOnReadEndpoints` (matching the brief's named test) that performs `GET /api/v1/invoices` and `GET /api/v1/invoices/{invoiceUuid}` with a non-merchant JWT.
- **Confidence:** High.

---

## 5. No test proves the identical 404 shape for cross-merchant vs nonexistent UUID

- **Issue:** The handler correctly omits any `detail` for `InvoiceNotFoundException`, but no test asserts that the response bodies for "non-owner" and "nonexistent" are byte-for-byte identical. R28's anti-enumeration property is therefore not under automated test.
- **Severity:** Medium — the code is correct, but the invariant is not guarded.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java:49-55`
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:87-92`
  - Missing tests in `InvoiceControllerTest.java`
- **Recommendation:** Add the two named AC2 tests (`shouldReturn404ForNonOwningMerchant` and `shouldReturn404ForNonexistentInvoiceUuid`) and assert both status and full JSON body.
- **Confidence:** High.

---

## 6. Positive confirmations

- **Decimal-string amount:** `InvoiceResponse.amount` is a `String` produced by `invoice.getAmount().toPlainString()`, satisfying `agents.md` and AC4.
- **`watchId` excluded:** `InvoiceResponse.from` does not map `watchId`, satisfying Phase 3 finding 2 and AC4.
- **Read-only transactions:** Both `listForMerchant` and `getForMerchant` are `@Transactional(readOnly = true)`, satisfying Phase 3 finding 4 and AC5.
- **No enumeration oracle:** `InvoiceService.getForMerchant` uses a single `findByInvoiceUuidAndMerchantAccountUuid` query and throws the same `InvoiceNotFoundException` for both missing and non-owned invoices.
- **Schema integration test updated:** `SchemaBaselineMigrationIntegrationTest` now passes with V3 (18 tests), confirming the T06 integration-test stale issue was resolved.
- **Outbox serialization tested:** `InvoiceCreatedPayloadSerializationTest` proves `amount` is a JSON string and `expiresAt` is ISO-8601, closing the T06 Phase 11 gap.

---

## Recommended follow-up order

1. Create `InvoiceServiceReadTest.java` with unit tests for `listForMerchant` and `getForMerchant`.
2. Add the missing GET tests to `InvoiceControllerTest.java` (AC1, AC2, AC3, AC4).
3. Extend the existing POST response assertion to exercise the full `InvoiceResponse` field set.
4. Add the identical-404-shape comparison test.
