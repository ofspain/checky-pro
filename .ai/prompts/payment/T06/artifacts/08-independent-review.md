# payment · T06 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T06/artifacts/08-independent-review.md`  
Consumed: Phase 6 implementation, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/payment-service/agents.md`  
Scope: T06 only. Findings only — no rewrite.

---

### Executive summary

- Build: `mvn -pl services/payment test` passes — **110/110 tests green**, but that is mostly unit/Mockito coverage; the runtime flow will fail on the first real invoice creation because the DB role lacks grants on the tables T06 now writes to.
- Phase 7 fixed two High-severity issues (timeout wiring, global exception handler) before this review.
- Four new issues found, two of them High/High-ish; plus several residual risks and test-coverage gaps.

---

### 1. Missing Flyway grant migration for `invoices` and `outbox`

- **Issue:** `V2__payment_app_role_and_grants.sql` grants `INSERT, SELECT` only on `ledger_entries` and `verification_records`. T06 is the first task that actually inserts into `invoices` (via `InvoiceService.create`) and into `outbox` (via `OutboxPublisher.publish`). Without a new `V3__...` migration granting `payment_app` the required privileges on these two tables, the runtime application will fail with `PSQLException: permission denied for table invoices` as soon as a merchant calls `POST /api/v1/invoices`. The existing tests pass only because `InvoiceServiceTest` mocks the repository and `OutboxPublisherTest` mocks the outbox repository.
- **Severity:** High — the service is not runnable against the migrated schema.
- **Evidence:**
  - `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql:34-40`
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:65-70` (saves `Invoice`, publishes outbox event)
  - `services/payment/src/main/resources/db/migration/` contains only `V1` and `V2`
- **Recommendation:** Add `V3__payment_app_invoice_and_outbox_grants.sql` (or similar) granting `INSERT, SELECT` (and `UPDATE` on `invoices` if later state transitions are expected to run as `payment_app`) on `payments.invoices` and `payments.outbox` to `payment_app`, mirroring the incremental-grant pattern documented in `V2`.
- **Confidence:** High.

---

### 2. New `client-secret` placeholder is an undocumented credential in `application.properties`

- **Issue:** `application.properties` now contains `themistra.payment.crypto.auth.client-secret=${PAYMENT_SERVICE_CLIENT_SECRET:payment-local-only}`. This is a real credential-shaped local-dev placeholder, separate from the already-documented datasource-password and Flyway-password carve-outs. `ApplicationPropertiesContentTest.noUndocumentedSecretAndNoYamlFileExists` only counts lines containing "password" and scans for `<secret>|apiKey=|accessKey=|accessToken=`, so it does not flag `client-secret=payment-local-only`.
- **Severity:** Medium — a secret-management process gap, even though the value is local-only.
- **Evidence:**
  - `services/payment/src/main/resources/application.properties:74`
  - `services/payment/src/test/java/com/themistra/payment/common/ApplicationPropertiesContentTest.java:83-99`
- **Recommendation:** Either update the secret scan test to treat `client-secret=` as a second documented local-dev carve-out (exactly one occurrence, matching the placeholder), or move the local default into an env-var-only configuration and leave the property value empty in committed `application.properties`.
- **Confidence:** Medium.

---

### 3. `Instant` fields in `RegisterWatchRequest` and `InvoiceCreatedPayload` may serialize as numeric timestamps

- **Issue:** Both DTOs use `java.time.Instant` for `expiresAt`. Spring Boot's default ObjectMapper may serialize `Instant` as an epoch-seconds numeric value unless `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` is disabled. Sibling services explicitly disable that feature in their contract tests. `contracts/api/crypto-internal.yaml` requires `expiresAt` to be a `date-time` string, and the event schema for `invoice.created` almost certainly expects ISO-8601 strings as well. AC5 only covers `expectedAmount` as a decimal string; it does not cover `expiresAt`.
- **Severity:** Medium — a contract-mismatch risk that tests currently do not exercise.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/common/crypto/RegisterWatchRequest.java:11-18`
  - `services/payment/src/main/java/com/themistra/payment/invoice/event/InvoiceCreatedPayload.java:12-22`
  - `contracts/api/crypto-internal.yaml:115-117` (`expiresAt: type: string, format: date-time`)
  - Sibling services disable `WRITE_DATES_AS_TIMESTAMPS` in contract tests (e.g., `services/crypto/src/test/java/com/themistra/crypto/common/CryptoInternalOpenApiContractTest.java:56`)
- **Recommendation:** Add `spring.jackson.serialization.write-dates-as-timestamps=false` to `application.properties`, or annotate every `Instant` field in these DTOs with `@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")`. Add a contract/serialization test that asserts the JSON string shape.
- **Confidence:** Medium.

---

### 4. Successful `registerWatch` followed by a failed `InvoiceService.create` leaves an orphan watch

- **Issue:** The Phase 4 redesign correctly moved `CryptoClient.registerWatch` outside the DB transaction, but the orphan-watch failure window remains: if `InvoiceService.create` throws after `registerWatch` succeeds (DB error, outbox serialization failure, etc.), the Crypto Service watch exists but there is no corresponding invoice in this service. The brief accepts this as a documented limitation; from a runtime-consistency perspective it is still a real gap.
- **Severity:** Medium — accepted limitation, but not cost-free.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java:48-65` (orchestration order)
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:65-70` (DB/outbox writes inside `@Transactional`)
  - `artifacts/04-frozen-task-brief.md:44-47` and `:102` (accepted limitation)
- **Recommendation:** Document the orphan-watch scenario in an ADR or task backlog, and plan a compensating `unregisterWatch` call (task scope permitting) or an idempotent reconciliation job for watches that reference non-existent invoices.
- **Confidence:** Medium.

---

### 5. `InvoiceExceptionHandler` and `ApiExceptionHandler` do not set a `type` on `ProblemDetail`

- **Issue:** `ResourceServerConfig`'s manual 401/403 handlers include `"type": "about:blank"`, matching the RFC 9457 stable-type expectation in `agents.md`. The new `InvoiceExceptionHandler` and `ApiExceptionHandler` use Spring's `ProblemDetail` but never call `setType(...)`, so error responses omit the `type` field. This is inconsistent with the manual handlers and with the standing rule to use a stable `type` catalogue.
- **Severity:** Low — responses are still `application/problem+json` and contain no internal detail.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java:21-36`
  - `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java:41-88`
  - `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java:103` (sets `type` manually)
  - `spec/payment-service/agents.md:47-48`
- **Recommendation:** Set `problem.setType(URI.create("about:blank"))` (or a service-specific type URI) in both handlers, matching the manual handlers.
- **Confidence:** Low.

---

### 6. Controller, CryptoClient, and token-provider tests are not present

- **Issue:** The frozen brief lists `InvoiceControllerTest`, `CryptoClientTest`, and `CryptoServiceTokenProviderTest` as Phase 10 outputs. They do not exist yet, so AC1 (non-MERCHANT → 401/403), AC6 (watch-registration failure → 503), and AC7 (single-flight token refresh under concurrency) are not exercised by CI. This is expected for a Phase 8 review, but it means the riskiest parts of this task (method security, inter-service HTTP, token-cache concurrency) currently have no automated regression guard.
- **Severity:** Medium — a coverage gap, not a code defect.
- **Evidence:**
  - `artifacts/04-frozen-task-brief.md:161-162` (Files to Create for tests)
  - `services/payment/src/test/java/com/themistra/payment/invoice/` contains only `InvoiceServiceTest.java`
  - `services/payment/src/test/java/com/themistra/payment/common/` contains no `CryptoClientTest.java` or `CryptoServiceTokenProviderTest.java`
- **Recommendation:** Add the three test classes in Phase 10 as planned; prioritize `InvoiceControllerTest` for role enforcement and `CryptoServiceTokenProviderTest` for the single-flight refresh behavior.
- **Confidence:** High (that they are missing).

---

### 7. `ApiExceptionHandler` catch-all `Exception.class` swallows 404/405 for unmapped paths/methods

- **Issue:** This is the inherited limitation of the mirrored `crypto-service` template, already disclosed in the self-review. `NoResourceFoundException` and `HttpRequestMethodNotSupportedException` are caught by the generic `Exception.class` handler and returned as `500` instead of the correct `404`/`405`. `PublicEndpointsTest` passes because it only asserts the status is not 401/403, but a client hitting a typo URL gets a misleading 500 with a trace_id.
- **Severity:** Low — inherited, shared-template limitation.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java:78-88`
  - `artifacts/07-self-review.md:47-60` (self-review disclosure)
- **Recommendation:** Add explicit handlers for `NoResourceFoundException` → `404` and `HttpRequestMethodNotSupportedException` → `405` above the catch-all, or document the behavior as a known cross-service template defect.
- **Confidence:** Medium.

---

### 8. `CryptoClient.registerWatch` does not validate the response `status`

- **Issue:** The code checks that `response.watchId()` is non-null but ignores `response.status()`. If the Crypto Service ever returns a watchId with a non-`REGISTERED` status (e.g., a future `PENDING` or `QUEUED` state), `InvoiceController` will proceed as if the watch is active. The contract currently requires `status` but does not enumerate values.
- **Severity:** Low — defensive hardening.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java:61-65`
  - `contracts/api/crypto-internal.yaml:119-128`
- **Recommendation:** Either assert `"REGISTERED".equalsIgnoreCase(response.status())` and throw `CryptoServiceUnavailableException` otherwise, or document that any non-null watchId is treated as success.
- **Confidence:** Low.

---

### 9. Positive confirmation: transaction boundary redesign is correctly implemented

- **Issue:** Not a finding — positive confirmation. `InvoiceController` computes `invoiceUuid`/`expiresAt`, calls `CryptoClient.registerWatch` (no transaction), and only then calls `InvoiceService.create` (`@Transactional`). This avoids holding a DB connection across the network call and avoids the self-invocation AOP proxy gotcha.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java:48-65`
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:52-73`
- **Recommendation:** None.
- **Confidence:** High.

---

### 10. Positive confirmation: `CryptoServiceTokenProvider` is thread-safe and uses the configured timeouts

- **Issue:** Not a finding — positive confirmation. The provider uses a `volatile` cached token with a `ReentrantLock` double-checked refresh, and both `CryptoClient` and `CryptoServiceTokenProvider` configure `SimpleClientHttpRequestFactory` with `connectTimeoutMs`/`readTimeoutMs` from `CryptoClientProperties`.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/common/CryptoServiceTokenProvider.java:40-71`
  - `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java:34-46`
- **Recommendation:** None.
- **Confidence:** High.

---

### 11. Positive confirmation: method security and role conversion are wired

- **Issue:** Not a finding — positive confirmation. `ResourceServerConfig` has `@EnableMethodSecurity`, exposes a `JwtAuthenticationConverter` bean backed by the mirrored `JwtRoleAuthoritiesConverter`, and wires it into the resource-server DSL. The controller uses `@PreAuthorize("hasRole('MERCHANT')")`.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java:39-73`
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java:49`
- **Recommendation:** None.
- **Confidence:** High.
