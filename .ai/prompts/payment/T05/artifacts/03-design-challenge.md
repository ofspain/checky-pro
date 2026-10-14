# payment · T05 · Phase 3 — Design Challenge Findings

Artifact: `.ai/prompts/payment/T05/artifacts/03-design-challenge.md`  
Consumes: `.ai/prompts/payment/T05/artifacts/02-task-implementation-brief.md`  
Authoritative standing rules: `spec/payment-service/agents.md`, `spec/payment-service/design.md` §4a, `spec/payment-service/package.md` §8  
Scoped to T05 only. Findings only — no redesign, no implementation.

---

### 1. `InvalidInvoiceRequestException` visibility is not specified

- **Issue:** The brief says the exception "mirrors `crypto-service`'s own `InvalidWatchRequestException` naming/shape exactly." In `crypto-service`, `InvalidWatchRequestException` is package-private (`class InvalidWatchRequestException`), because the `watch` package's own controller and exception handler live in the same package. Payment's future `InvoiceController` (task 6) will almost certainly live in a different package (e.g., `com.themistra.payment.invoice.api` or `com.themistra.payment.api`), so a package-private exception there cannot be caught or mapped to an RFC 9457 response without moving the controller into the `invoice` package.
- **Severity:** Medium — a visibility detail that can become a compile-time blocker for the next task.
- **Evidence:**
  - `services/crypto/src/main/java/com/themistra/crypto/watch/InvalidWatchRequestException.java:9` (package-private)
  - `artifacts/02-task-implementation-brief.md:25-26` (mirrors crypto shape)
  - `artifacts/02-task-implementation-brief.md:78` (expected output location is `invoice` package)
- **Recommended brief amendment:** State explicitly that `InvalidInvoiceRequestException` is `public`, while still keeping the same simple `RuntimeException` constructor shape.

---

### 2. JPA mapping details needed for `Invoice` to pass `ddl-auto=validate`

- **Issue:** T04 set `spring.jpa.hibernate.ddl-auto=validate`, so the `Invoice` entity must match the real `invoices` table exactly. The brief lists the 13 columns but does not specify the JPA annotations required to make Hibernate's generated DDL line up with the schema: `state` must be `@Enumerated(EnumType.STRING)` (default `ORDINAL` would mismatch `VARCHAR(16)`), `amount` should be `@Column(precision = 78, scale = 0)` (default `NUMERIC(19,2)` would fail validate), and `tokenDecimals` must map to `SMALLINT` (an `Integer` would map to `INTEGER`). These are implementation details, but a single wrong default turns the first context boot into a validation failure.
- **Severity:** Medium — easy to get right, but also easy to overlook if the brief doesn't state them.
- **Evidence:**
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:203-221`
  - `artifacts/02-task-implementation-brief.md:18` (all 13 real columns)
  - `services/payment/src/main/resources/application.properties:24` (`ddl-auto=validate`, set in T04)
- **Recommended brief amendment:** Add a short mapping note to AC3 or the Scope section: `state` → `@Enumerated(STRING)`, `amount` → `@Column(precision=78, scale=0)`, `tokenDecimals` → `short` (or annotated `SMALLINT`).

---

### 3. No happy-path test is listed in Required Tests

- **Issue:** AC4 requires that a valid `create(...)` call persists an `Invoice` in `OPEN` state, with a freshly generated `invoiceUuid` and an `expiresAt` computed from `InvoiceProperties.defaultTtlMinutes()`. The Required Tests section only lists the two rejection tests (R3/R4) and the ArchUnit test (L6). Without a named happy-path test, the most important success behavior of `InvoiceService.create` has no permanent regression guard.
- **Severity:** Medium — violates `package.md` §9's checklist that every acceptance criterion has a passing named test.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:115-118` (AC4)
  - `artifacts/02-task-implementation-brief.md:117-123` (Required Tests, only rejection/ArchUnit)
  - `spec/payment-service/package.md:113` (verification checklist)
- **Recommended brief amendment:** Add a Required Test such as `shouldCreateInvoiceWithOpenStateAndComputedExpiry` (or similar) verifying the valid path: returned/persisted `Invoice` has `state == OPEN`, non-null `invoiceUuid`, and `expiresAt == clock.instant() + defaultTtlMinutes`.

---

### 4. `tokenDecimals`, `reference` length, and `amount` precision are not validated

- **Issue:** R3 validates `chain` and `tokenContractAddress`; R4 validates `amount` null/zero/scale. The `create(...)` signature also accepts `tokenDecimals` and `reference`, but the brief does not say how (or whether) to validate them. An out-of-range `tokenDecimals` (negative or > 32767) will fail at the DB layer with a less-friendly error. A `reference` longer than 140 characters will also fail at the DB layer. An `amount` with more than 78 significant digits is "not representable as an exact `NUMERIC`" per R4's own wording, but the scale-only check would allow it.
- **Severity:** Low — not a functional defect today, but ambiguous scope that will surface when the API is exposed in task 6.
- **Evidence:**
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:203-221` (`token_decimals SMALLINT NOT NULL`, `reference VARCHAR(140)`, `amount NUMERIC(78,0)`)
  - `artifacts/02-task-implementation-brief.md:46-54` (R3/R4 validation)
  - `spec/payment-service/requirements.md:9-10` (R3/R4 text)
- **Recommended brief amendment:** State explicitly whether `tokenDecimals` range, `reference` max length, and `amount` max precision are validated in T05 or deferred to task 6. If validated now, add corresponding test cases.

---

### 5. `InvoiceService.create` transaction boundary not specified

- **Issue:** The brief says "no transactional boundary concern beyond the single `save`." However, task 6 will emit `invoice.created` through the outbox in the same transaction as the invoice insert (L8, agents.md). If `create(...)` is not `@Transactional` now, a future developer must remember to add it when wiring the outbox publish; forgetting would silently break the atomicity guarantee. Adding `@Transactional` now is harmless for a single `save` and makes the later task safer.
- **Severity:** Low — latent ordering/correctness risk.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:23-24` (`InvoiceService.create` scope)
  - `artifacts/02-task-implementation-brief.md:126-127` (transaction constraint)
  - `spec/payment-service/agents.md:36-37` (outbox in same transaction)
- **Recommended brief amendment:** Require `InvoiceService.create` to be `@Transactional` (default `REQUIRED`), with a note that this is forward-looking for task 6's outbox emit.

---

### 6. `ArchitectureTest` L6 rule scope may miss floating-point money in parameters

- **Issue:** The brief states the ArchUnit rule checks "any `float`/`double`/`Float`/`Double` field or method return type." A method that *accepts* a floating-point amount as a parameter (e.g., a future DTO constructor or helper) would not be caught by a fields-and-returns-only rule. The spirit of L6 is that no floating-point type carries money anywhere in the service's API or state.
- **Severity:** Low — defensive rule-hardening.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:27-30` (ArchUnit rule scope)
  - `spec/payment-service/design.md:10` (L6 text)
- **Recommended brief amendment:** Extend the rule to also forbid `float`/`double`/`Float`/`Double` method parameters under `com.themistra.payment`, or document that parameters are intentionally out of scope because DTO validation will be covered separately.

---

### 7. `Invoice` `createdAt`/`updatedAt` lifecycle is not specified

- **Issue:** The `invoices` table has `created_at` and `updated_at` columns that are `NOT NULL` with DB defaults. The brief focuses on `expiresAt` and `invoiceUuid` for AC4 but does not say whether the entity sets `createdAt`/`updatedAt` in Java or relies on the DB defaults. Relying on DB defaults works for inserts but makes `updatedAt` stale on any future update; setting them via `@PrePersist`/`@PreUpdate` is more explicit and matches the agents.md convention to use `java.time` with an injectable `Clock`.
- **Severity:** Low — implementation detail, but worth freezing.
- **Evidence:**
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:216-217`
  - `artifacts/02-task-implementation-brief.md:18` (all 13 columns)
- **Recommended brief amendment:** Specify that `Invoice` uses `@PrePersist`/`@PreUpdate` to set `createdAt`/`updatedAt` from the injected `Clock`, or explicitly accept DB defaults as intentional.
