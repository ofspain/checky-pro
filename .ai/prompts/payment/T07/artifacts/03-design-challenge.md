# payment · T07 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T07/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `spec/payment-service/agents.md`, `spec/payment-service/design.md`, `spec/payment-service/requirements.md`, `spec/payment-service/package.md`, existing T05/T06 code.

---

## 1. Merchant-facing response will serialize `amount` as a JSON number, violating `agents.md`

- **Issue:** The brief says `InvoiceResponse` should become a "full view (superset of its current two fields)" with a static `InvoiceResponse.from(Invoice)` mapper. `Invoice.amount` is a `BigDecimal`. Jackson's default serializer emits a `BigDecimal` as a JSON number. `agents.md` has a standing, non-negotiable rule: "On the wire, monetary values are decimal strings, never JSON numbers." The sibling event schemas and the Crypto Service internal contract also use decimal strings for amounts. If the mapper simply copies `invoice.getAmount()` into a `BigDecimal` record field, the merchant-facing API will break the platform-wide money-on-the-wire rule and can lose precision in clients.
- **Severity:** High.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java:62-63` (`amount` is `BigDecimal`)
  - `services/payment/src/main/java/com/themistra/payment/invoice/dto/InvoiceResponse.java:6-9` (current two-field record; extension unspecified)
  - `spec/payment-service/agents.md:30` — "On the wire, monetary values are decimal strings, never JSON numbers."
  - `spec/payment-service/design.md:345` — receipt event schema uses `"type": "string"` for `amount`.
- **Recommended brief amendment:** Explicitly state that `InvoiceResponse.amount` is a `String` produced by `invoice.getAmount().toPlainString()`, and add an acceptance criterion (and test) asserting that the JSON response body contains amount as a quoted decimal string.

---

## 2. `watchId` is internal to the Crypto Service but may be exposed in the "full view"

- **Issue:** The brief says `InvoiceResponse` will be a "full view (superset of its current two fields)". `Invoice.watchId` is the Crypto Service's internal watch identifier; it has no merchant-facing meaning and exposing it leaks an internal dependency identifier. The design.md §4c treats watches as an internal integration concern.
- **Severity:** Medium.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java:75-76` (`watchId`)
  - `spec/payment-service/design.md:180-184` — watch endpoints are internal; merchant never interacts with them.
- **Recommended brief amendment:** Explicitly exclude `watchId` from `InvoiceResponse`. The response fields should be the merchant-relevant set: `invoiceUuid`, `merchantAccountUuid`, `chain`, `tokenContractAddress`, `tokenDecimals`, `amount` (string), `payToAddress`, `reference`, `state`, `expiresAt`, `createdAt`, `updatedAt`.

---

## 3. No merchant-facing API contract exists for the new endpoints

- **Issue:** This is the first merchant-facing read API in `payment-service`. There is no `contracts/api/payments.yaml` in the repository, so the response shape, field types, and error schemas are not contract-guarded. `agents.md` says models are generated from `contracts/` and contract tests validate payloads against `contracts/`.
- **Severity:** Medium.
- **Evidence:**
  - Glob search for `contracts/api/*payment*` returns no files.
  - `spec/payment-service/agents.md:33` — "Event schemas live in `contracts/events/`, are versioned... Contract tests validate payloads against `contracts/`."
- **Recommended brief amendment:** Add `contracts/api/payments.yaml` (or note it as a deferred output with owner) and add a contract test that validates the `GET` responses against it.

---

## 4. `InvoiceService` read methods lack `@Transactional(readOnly = true)`

- **Issue:** The brief does not specify transaction annotations for `listForMerchant` and `getForMerchant`. JPA read-only methods benefit from a `readOnly=true` transaction (no dirty checking, clearer semantics). Without it, the service relies on the Open-EntityManager-in-View filter, which is not described in the brief.
- **Severity:** Low.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:52` (`create` has `@Transactional`; new methods are not specified)
- **Recommended brief amendment:** Add `@Transactional(readOnly = true)` to both `listForMerchant` and `getForMerchant`.

---

## 5. Named test references receipts, which are out of scope

- **Issue:** `package.md` §8 names `shouldScopeInvoiceAndReceiptReadsToOwningMerchant` for R28. The brief correctly excludes receipts ("no resource exists yet"), but the required test name still implies receipt reads will be verified. Implementing the named test literally is impossible in T07.
- **Severity:** Low.
- **Evidence:**
  - `spec/payment-service/package.md:105` — `shouldScopeInvoiceAndReceiptReadsToOwningMerchant → R28`
  - `artifacts/02-task-implementation-brief.md:32-34` — Receipts are out.
- **Recommended brief amendment:** Rename the T07 test to `shouldScopeInvoiceReadsToOwningMerchant` and add a backlog note that a receipt-scoping test will be added when the receipt resource is built.

---

## 6. List endpoint is unbounded

- **Issue:** `GET /api/v1/invoices` returns every invoice for the merchant, ordered by `createdAt` desc, with no pagination, limit, or cursor. The brief explicitly says pagination is not a named requirement, but a merchant with a long history could receive a very large payload.
- **Severity:** Low — latent risk, accepted out-of-scope.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:14-15, 35` — Pagination is "Out" and "not a named requirement."
  - Repository method name `findByMerchantAccountUuidOrderByCreatedAtDesc` implies unbounded fetch.
- **Recommended brief amendment:** Add a non-functional limitation note: the endpoint returns all invoices for the caller; pagination/limiting is a future task. Optionally add a defensive maximum result cap or a warning in the brief.

---

## 7. Existing DB index may not optimally support the list ordering

- **Issue:** `V1__payments_baseline.sql` creates `idx_invoices_merchant(merchant_account_uuid)`. The list query filters by `merchant_account_uuid` and sorts by `created_at DESC`. The existing single-column index supports the filter but not the sort; PostgreSQL may need to sort all rows for a merchant.
- **Severity:** Low — performance concern, likely fine for MVP volumes.
- **Evidence:**
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:27` — `idx_invoices_merchant`
  - `artifacts/02-task-implementation-brief.md:16-17` — `findByMerchantAccountUuidOrderByCreatedAtDesc`
- **Recommended brief amendment:** Either add a new migration `V4__invoice_merchant_created_at_idx.sql` creating `idx_invoices_merchant_created_at(merchant_account_uuid, created_at DESC)`, or explicitly note that the existing index is acceptable for launch volumes.

---

## Positive confirmations

- **404 vs 403 for cross-merchant access** is correctly locked (R28/L10) and the brief uses a single `InvoiceNotFoundException` to avoid enumeration oracle.
- **JWT `sub` extraction** reuses the existing `merchantAccountUuidFrom(Jwt)` helper, avoiding duplication.
- **Read-only state** is correctly noted: no state changes in this task.
- **Security posture** matches T06: `@PreAuthorize("hasRole('MERCHANT')")` on both new endpoints.
- **Repository query methods** are simple Spring Data derived queries that fit the task scope.
