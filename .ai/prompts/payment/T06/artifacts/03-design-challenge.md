# payment · T06 · Phase 3 — Design Challenge Findings

Artifact: `.ai/prompts/payment/T06/artifacts/03-design-challenge.md`  
Consumes: `.ai/prompts/payment/T06/artifacts/02-task-implementation-brief.md`  
Authoritative standing rules: `spec/payment-service/agents.md`, `spec/payment-service/design.md` §4a, `contracts/api/crypto-internal.yaml`  
Scoped to T06 only. Findings only — no redesign, no implementation.

---

### 1. Holding a DB transaction across two synchronous network calls is a resilience risk

- **Issue:** `InvoiceService.create` is `@Transactional` and now keeps the transaction open while `CryptoClient.registerWatch` runs, which in turn depends on `CryptoServiceTokenProvider` fetching an access token from `auth-service` on the first call. A slow or unavailable `auth-service`/`crypto-service` will hold a Postgres connection for the duration, exhausting the 10-connection Hikari pool under load. The brief already flags this as a deliberate deviation from best practice, but does not set any guardrails (HTTP timeouts, pool sizing, or a future saga ticket).
- **Severity:** High — a runtime availability/coupling risk, even if it satisfies R1/R2's atomicity wording.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:145-150` (transaction constraint)
  - `services/payment/src/main/resources/application.properties:16` (`maximum-pool-size=10`, `connection-timeout=5000`)
  - `services/payment/src/main/java/com/themistra/payment/common/config/CryptoClientProperties.java` (connect/read timeouts from T03)
- **Recommended brief amendment:** Cap `CryptoClient` connect/read timeouts at a value well below the Hikari connection timeout (e.g., `connect-timeout-ms=1000`, `read-timeout-ms=3000`), document the connection-pool headroom assumption, and add an Open Question or backlog item to revisit saga/compensation if load testing shows pool exhaustion.

---

### 2. A successful `registerWatch` followed by a failed outbox publish leaves an orphan watch

- **Issue:** The natural order is: validate → register watch (external side effect) → persist invoice → emit outbox event. Because the outbox write is inside the same transaction as the invoice insert, an `OutboxPublisher` serialization failure (or any late DB error) rolls back the invoice but **not** the already-registered Crypto Service watch. The watch will then fire on future transactions for an invoice UUID that does not exist in this service.
- **Severity:** Medium — a consistency gap that only surfaces on the failure path.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:31-33` (register watch, persist, emit, all inside `@Transactional`)
  - `artifacts/02-task-implementation-brief.md:147-148` (no compensating-transaction/saga pattern)
  - `spec/payment-service/agents.md:36-37` (outbox in same transaction)
- **Recommended brief amendment:** Explicitly state the accepted failure mode (orphan watch is tolerable until a future cleanup job exists) **or** require `CryptoClient.registerWatch` to be the *last* successful side effect before commit and document that any failure after it is an orphan-watch scenario. Do not silently assume the transaction boundary makes everything atomic.

---

### 3. Method security is required but not wired in the brief

- **Issue:** AC1/L10 requires `POST /api/v1/invoices` to be `MERCHANT`-role only. The brief says add `JwtRoleAuthoritiesConverter` + `JwtAuthenticationConverter` bean in `ResourceServerConfig`, but it does not say to enable method security (`@EnableMethodSecurity`) or to wire the converter into the resource-server DSL. Without both, `@PreAuthorize("hasRole('MERCHANT')")` on the controller is ignored.
- **Severity:** High — a security-configuration gap.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:28-30` and `:126-128` (AC1)
  - `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java` (current chain has no method security and no `jwtAuthenticationConverter` wiring)
  - `services/auth/src/main/java/com/themistra/auth/token/JwtRoleAuthoritiesConverter.java` (the mirrored class)
- **Recommended brief amendment:** Add to Scope/Files to Modify that `ResourceServerConfig` must (a) declare `@EnableMethodSecurity` and (b) wire the converter via `.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))`, or use an authority-based `requestMatchers` rule if `@PreAuthorize` is not the chosen shape.

---

### 4. `Invoice` has no way to store the returned `watchId`

- **Issue:** `InvoiceService.create` must set `Invoice.watchId` from the `RegisterWatchResponse`. The current `Invoice` entity has a private `watchId` field and only getters; there is no setter or package-private mutator. The brief does not list any modification to `Invoice.java`.
- **Severity:** Medium — a compile-time blocker unless the implementation adds a mutator.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java:75-76`
  - `artifacts/02-task-implementation-brief.md:31-33` (store returned watchId)
  - `artifacts/02-task-implementation-brief.md:109-114` (Files to Modify does not include `Invoice.java`)
- **Recommended brief amendment:** Add `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java` to Files to Modify, with a package-private `assignWatchId(UUID watchId)` method (keeps the entity's mutation surface narrow).

---

### 5. `CreateInvoiceRequest` should validate `tokenDecimals` range and `reference` length now

- **Issue:** T05 deferred `tokenDecimals` range and `reference` max-length validation because no DTO existed. T06 introduces `CreateInvoiceRequest`, so bean validation can and should enforce `0 <= tokenDecimals <= 255` (matches `SMALLINT`) and `reference` ≤ 140 characters (matches `VARCHAR(140)`). Without these, invalid requests reach `InvoiceService.create` and fail at the DB layer with a less useful error.
- **Severity:** Medium — an API-validation gap that leaks DB errors to merchants.
- **Evidence:**
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:209,212`
  - `artifacts/02-task-implementation-brief.md:155-156` (bean validation mentioned but not enumerated)
  - `artifacts/02-task-implementation-brief.md:62-63` (T05 deferred validations)
- **Recommended brief amendment:** Add `@Min(0) @Max(255)` (or `@Digits`) on `tokenDecimals` and `@Size(max = 140)` on `reference` in `CreateInvoiceRequest`, with corresponding test cases.

---

### 6. `InvoiceController.create` response status and merchant UUID source are ambiguous

- **Issue:** AC4 says the response is `201`/`200`, leaving the status code unsettled. More importantly, the brief never says whether `merchantAccountUuid` comes from the JWT `sub` claim or from the request body. R28 (merchant-scoped reads) implies the caller's identity is authoritative; letting the request specify a different merchant UUID would break scoping.
- **Severity:** Medium — an API-design ambiguity.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:132-133` (AC4)
  - `contracts/api/token-claims.md` (`sub` = account UUID)
  - `spec/payment-service/package.md:105` (R28 — scope reads to owning merchant)
- **Recommended brief amendment:** Specify that `CreateInvoiceRequest` does **not** contain `merchantAccountUuid`; the controller extracts it from the JWT `sub` claim and passes it to `InvoiceService.create`. Return `201 Created` on success.

---

### 7. `CryptoServiceTokenProvider` thread-safety and first-call-inside-transaction risk

- **Issue:** The brief proposes an explicit, in-memory token cache with expiry-aware refresh but does not specify synchronization. On cache expiry, multiple concurrent invoice-creation requests could each fetch a new token, and the first request's fetch happens inside the open DB transaction. It also does not specify the property keys for client-id/secret/token-uri.
- **Severity:** Medium — a concurrency/operational ambiguity.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:24-27` and `:162-169` (proposed minimal token provider)
  - `services/auth/src/main/resources/application.properties:54-56` (the `payment-service` client entry that supplies the credential)
- **Recommended brief amendment:** Specify a thread-safe refresh strategy (e.g., `AtomicReference` with a single-flight `synchronized` block or `ReentrantLock`); pre-warm the token on application startup or fetch it lazily outside any DB transaction; and define the configuration keys the provider reads (e.g., `themistra.payment.crypto.auth.client-id`, `client-secret`, `token-uri`).

---

### 8. No required test verifies the `400` response is RFC 9457-shaped

- **Issue:** R29 (`shouldReturnProblemJsonWithNoInternalDetailOn4xx`) is service-wide, but the first merchant-visible 4xx path is T06's `InvalidInvoiceRequestException`. The Required Tests list `shouldRejectNonMerchantCallerWithForbidden` for 403, but there is no test asserting that a validation failure returns `application/problem+json` with no stack trace or internal detail.
- **Severity:** Low — a coverage gap against R29.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:137-141` (Required Tests)
  - `spec/payment-service/package.md:106` (R29)
  - `spec/payment-service/agents.md:47-48` (RFC 9457, no internal detail)
- **Recommended brief amendment:** Add `shouldReturnProblemJsonForInvalidInvoiceRequest` to Required Tests, asserting content type, `title`, `status`, and absence of stack-trace/internal fields.

---

### 9. `InvoiceCreatedPayload` shape is not specified

- **Issue:** The brief says emit `invoice.created` via `OutboxPublisher` with an `InvoiceCreatedPayload` body, but it never lists the fields. A mismatch with `contracts/events/payments/invoice.created.v1.schema.json` (or whatever contract exists) will only surface at contract-test time later.
- **Severity:** Low — a contract-clarity gap.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:17` (`InvoiceCreatedPayload`)
  - `artifacts/02-task-implementation-brief.md:31-33` (emit event)
  - `contracts/events/payments/receipt-issued.v1.schema.json` (adjacent event schema precedent)
- **Recommended brief amendment:** List the payload fields (e.g., `invoiceUuid`, `merchantAccountUuid`, `chain`, `tokenContractAddress`, `tokenDecimals`, `amount`, `payToAddress`, `reference`, `expiresAt`) and note that the amount must be a decimal string.
