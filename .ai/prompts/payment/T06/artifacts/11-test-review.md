# payment · T06 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T06/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `artifacts/09-review-resolution.md`, and the Phase 10 test files.

---

## Executive summary

- `mvn -pl services/payment test`: **111 tests run, 3 failures, 0 errors**.
- The three failures are all in the pre-existing T02 integration test `SchemaBaselineMigrationIntegrationTest`, which has not been updated for the V3 migration added in Phase 9. All new Phase 10 tests pass.
- The new tests are solid and cover the named acceptance criteria, but several gaps remain — especially around proving the controller's UUID hand-off and transaction ordering, and around serializing the `invoice.created` payload.

---

## 1. Build-breaking issue — `SchemaBaselineMigrationIntegrationTest` is stale

- **Issue:** `SchemaBaselineMigrationIntegrationTest` was written for T02 when only V1/V2 existed and `payment_app` had no grants on `invoices`/`outbox`. Phase 9 added `V3__payment_app_invoice_and_outbox_grants.sql`, so three assertions now fail:
  - `bothMigrationsAreRecordedAsSuccessfulInFlywayHistory` — unexpectedly sees `"V3"`.
  - `informationSchemaShowsNoGrantRowsForPaymentAppOnUngrantedTables` — unexpectedly sees `"invoices"`, `"outbox"`.
  - `paymentAppHasNoAccessAtAllToTablesOutsideAc3Scope` — unexpectedly sees `"invoices"`, `"outbox"`.
- **Severity:** High — CI is red.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java:88-105, 132-149, 152-169`
  - `services/payment/src/main/resources/db/migration/V3__payment_app_invoice_and_outbox_grants.sql`
- **Recommendation:** Update the T02 test to expect V1/V2/V3 and to reflect the new grant set. Add an assertion that `payment_app` now has exactly the expected privileges on `invoices` and `outbox`.
- **Confidence:** High.

---

## 2. `InvoiceControllerTest.shouldTransitionCreatedToWatchingOnWatchAck` does not prove the same UUID reaches both calls

- **Issue:** The test captures the UUID passed to `CryptoClient.registerWatch`, but verifies `InvoiceService.create` with `any(UUID.class)`. The comment inside the test even admits this. The test name and intent (R6 — the same `invoiceUuid` reaches both `registerWatch` and `create`) are therefore not fully satisfied.
- **Severity:** Medium — the architectural invariant is present in the code but not strictly guarded by the test.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java:120-135`
- **Recommendation:** Add an `ArgumentCaptor<UUID>` for `InvoiceService.create` and assert it equals `watchInvoiceUuidCaptor.getValue()`.
- **Confidence:** High.

---

## 3. No `InOrder` verification that `registerWatch` happens before `create`

- **Issue:** The whole point of the Phase 4 redesign is to call the Crypto Service *outside* the DB transaction. A Mockito `InOrder` check between `cryptoClient.registerWatch` and `invoiceService.create` would make that ordering a tested regression guard. None of the controller tests currently assert ordering.
- **Severity:** Medium.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java:49-65, 82-93`
- **Recommendation:** Add `InOrder inOrder = inOrder(cryptoClient, invoiceService)` and verify `registerWatch` before `create` in the happy-path test.
- **Confidence:** High.

---

## 4. No test for malformed or missing JWT `sub` claim

- **Issue:** `InvoiceController` extracts `merchantAccountUuid` from the JWT `sub` claim and throws `InvalidInvoiceRequestException` if it is missing or not a UUID. The controller tests only use a valid UUID subject. A missing/malformed subject path is uncovered.
- **Severity:** Low/Medium — edge case, but cheap to guard.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java:68-79`
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java:28-47` (`jwtWithSub()` always sets a valid UUID)
- **Recommendation:** Add two tests: one with a JWT that has no `sub` claim → 400, and one with `sub = "not-a-uuid"` → 400.
- **Confidence:** High.

---

## 5. `InvoiceControllerTest` does not assert the 403 response body

- **Issue:** `shouldRejectNonMerchantCallerWithForbidden` checks only the status. Phase 9 added an `AuthorizationDeniedException` handler to `ApiExceptionHandler` so that 403s produce RFC 9457 `application/problem+json` bodies. That handler is not exercised by the controller test.
- **Severity:** Low.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java:64-71`
  - `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java:43-53`
- **Recommendation:** Extend the forbidden test to assert `Content-Type: application/problem+json`, `$.status: 403`, and `$.title: "Forbidden"`.
- **Confidence:** High.

---

## 6. `InvoiceServiceTest` does not inspect the outbox payload

- **Issue:** `shouldCreateInvoice` only verifies that `outboxPublisher.publish(anyString(), anyString(), any())` was called once. It does not assert the aggregate type/topic, the event type, or that the `InvoiceCreatedPayload` fields — especially `amount` as a decimal string — are correct.
- **Severity:** Medium — R2 (emit `invoice.created`) and the L6 string-amount convention are not guarded against future regressions.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceTest.java:60-73`
- **Recommendation:** Capture the payload object, deserialize it with `ObjectMapper`, and assert `amount` equals `"1000"` (not a number), `invoiceUuid`/`merchantAccountUuid` match, and the topic/aggregateType are correct.
- **Confidence:** High.

---

## 7. No real serialization test for `InvoiceCreatedPayload`

- **Issue:** `OutboxPublisherTest` mocks the `ObjectMapper`, so it never exercises the real Jackson serialization of `InvoiceCreatedPayload`. `CryptoClientTest` does exercise real serialization for `RegisterWatchRequest` (and incidentally proves `expiresAt` is an ISO-8601 string), but the outbound event side has no equivalent guard.
- **Severity:** Medium — sibling consumers will deserialize the event; a numeric `Instant` or numeric `amount` would break them.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/events/OutboxPublisherTest.java:35-100`
  - `services/payment/src/main/java/com/themistra/payment/invoice/event/InvoiceCreatedPayload.java:12-22`
- **Recommendation:** Add a contract-style test that serializes `InvoiceCreatedPayload` with the Spring `ObjectMapper` and asserts `amount` is a textual decimal and `expiresAt` is an ISO-8601 string.
- **Confidence:** Medium.

---

## 8. `CryptoServiceTokenProviderTest` does not cover the safety-margin edge

- **Issue:** `shouldRefreshTokenBeforeExpiry` uses a 60-second token and waits 2 seconds, then asserts refresh. It does not exercise the exact 30-second safety margin: a token that expires in 31 seconds should be considered valid, while a token that expires in 29 seconds should trigger refresh.
- **Severity:** Low.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/common/CryptoServiceTokenProviderTest.java:160-200`
  - `services/payment/src/main/java/com/themistra/payment/common/CryptoServiceTokenProvider.java:59-64`
- **Recommendation:** Add a parameterized or boundary test that pins the 30-second margin precisely.
- **Confidence:** Medium.

---

## 9. `CryptoServiceTokenProviderTest` does not prove failures are not cached

- **Issue:** `shouldThrowOnServerError` calls `getAccessToken()` once and expects an exception. It does not verify that a second call re-attempts the token endpoint rather than returning the cached exception.
- **Severity:** Low — the current implementation does not cache failures, but this is not regression-protected.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/common/CryptoServiceTokenProviderTest.java:130-155`
- **Recommendation:** Add a test that calls `getAccessToken()` twice after a 500 response and asserts the token endpoint was hit twice.
- **Confidence:** Medium.

---

## 10. `CryptoServiceTokenProviderTest` does not exercise concurrent refresh near expiry

- **Issue:** `shouldRefreshOnlyOnceUnderConcurrentLoad` starts with an empty cache. It does not test the race when many threads call `getAccessToken()` while the cached token is still present but has crossed the expiry margin.
- **Severity:** Low.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/common/CryptoServiceTokenProviderTest.java:90-125`
- **Recommendation:** Pre-populate the cache with a token that expires in, say, 29 seconds, then fire concurrent requests and assert only one refresh call occurs.
- **Confidence:** Low.

---

## 11. `CryptoClientTest` read-timeout threshold may flake on slow runners

- **Issue:** `shouldThrowOnReadTimeout` asserts the elapsed time is `< 1000ms`. The configured read timeout is 200ms, so the test usually passes, but CI runners under heavy load could occasionally exceed 1 second between scheduling and assertion.
- **Severity:** Low — nuisance flakiness risk.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/common/CryptoClientTest.java:75-90`
- **Recommendation:** Tighten the assertion to `< 500ms` (still far above 200ms plus scheduling jitter) or drop the elapsed-time assertion and rely solely on the exception type.
- **Confidence:** Low.

---

## 12. Positive confirmations

- **Real HTTP server for `CryptoClientTest`:** The test stands up `com.sun.net.httpserver.HttpServer`, captures the actual request body, and asserts `expectedAmount` is a textual decimal. It also incidentally proves `expiresAt` is an ISO-8601 string, resolving Phase 8 finding 3.
- **Status validation coverage:** `shouldValidateRegisterWatchResponseStatus` verifies that a non-`REGISTERED` status throws `CryptoServiceUnavailableException`, covering Phase 8 finding 8.
- **Concurrency coverage:** `CryptoServiceTokenProviderTest.shouldRefreshOnlyOnceUnderConcurrentLoad` uses a `CountDownLatch` and an `AtomicInteger` to prove single-flight refresh under concurrent load, covering AC7.
- **Security slice:** `InvoiceControllerTest` uses `@WebMvcTest` with explicit `@Import({ResourceServerConfig.class, JwtRoleAuthoritiesConverter.class, ApiExceptionHandler.class})`, giving a realistic security slice.
- **Secret guard updated:** `ApplicationPropertiesContentTest` was correctly updated in Phase 9 to treat `client-secret=` as a documented local-dev placeholder.
- **DB grant fix:** V3 migration was added and the runtime flow is now viable (once the stale T02 test is fixed).

---

## 13. Recommended follow-up order

1. Fix `SchemaBaselineMigrationIntegrationTest` for V3 (CI is red).
2. Strengthen `InvoiceControllerTest.shouldTransitionCreatedToWatchingOnWatchAck` with a second `ArgumentCaptor` for `InvoiceService.create`.
3. Add `InOrder` verification in the happy-path controller test.
4. Add outbox payload assertions to `InvoiceServiceTest` and a real `InvoiceCreatedPayload` serialization contract test.
5. Add boundary tests for the token-provider safety margin and failure non-caching.
