# payment · T03 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T03/artifacts/08-independent-review.md`  
Consumed: Phase 6 implementation, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/payment-service/agents.md`  
Scope: T03 only. Findings only — no rewrite.

---

### Executive summary

- Build: `mvn -pl services/payment test` passes — **61/61 tests green**.
- Phase 7 finding 1 (`PublicEndpoints.PATTERNS` exact content never asserted) was applied: `PublicEndpointsTest.java` now mirrors `notification-service`'s identical guard.
- Phase 7 finding 2 (`InvoiceProperties.amountToleranceBaseUnits` is a `long`) remains a documented, accepted narrowness — no change.
- Four new issues found below, three of them in test coverage.

---

### 1. `ConfigPropertiesBindingTest.receiptPropertiesFailWhenBucketIsMissingEntirely` uses a bare `hasFailed()` assertion

- **Issue:** The frozen brief's Required Tests explicitly require failures to be asserted via `rootCause().isInstanceOf(...).hasMessageContaining(...)`, calling a bare `hasFailed()` a named lesson to avoid. Every other negative test in this file follows that style, but the "missing entirely" case for `receipt.bucket` does not. A future regression that fails the context for a different reason (e.g., a broken `@EnableConfigurationProperties` import) would still pass this assertion.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/common/config/ConfigPropertiesBindingTest.java:144-148`
  - `services/payment/src/test/java/com/themistra/payment/common/config/ConfigPropertiesBindingTest.java:70-73` and `:96-99` (correct root-cause style)
  - `artifacts/04-frozen-task-brief.md:161-164` (Required Tests)
- **Recommendation:** Rewrite to `rootCause().isInstanceOf(BindValidationException.class).hasMessageContaining("bucket")`, matching the sibling tests.
- **Confidence:** High.

---

### 2. `AccessDeniedHandlerTest` does not assert the `type` field of the RFC 9457 403 body

- **Issue:** The 403 direct-invocation test checks `title`, `status`, and `detail`, but not `type`. RFC 9457 mandates `type`, and `agents.md` requires a stable `type` catalogue. The 401 test in `ResourceServerConfigTest` already asserts `$.type` is `about:blank`; the 403 test should be equally complete.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/common/AccessDeniedHandlerTest.java:31-33`
  - `services/payment/src/test/java/com/themistra/payment/common/ResourceServerConfigTest.java:53` (401 test asserts `type`)
  - `spec/payment-service/agents.md` Security rule (RFC 9457, stable `type` catalogue)
- **Recommendation:** Add `assertThat(body).contains("\"type\":\"about:blank\"")` to `AccessDeniedHandlerTest`.
- **Confidence:** Medium.

---

### 3. No source-text guard for the resource-server JWT properties

- **Issue:** `ApplicationPropertiesContentTest` enforces AC2, AC6, AC7, AC8, and the secret/YAML negative-proof, but never asserts that `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` and `issuer-uri` are present in `application.properties`. The `@WebMvcTest` security slice does not load a real `JwtDecoder`, so those properties could be removed without breaking any test, even though AC3 depends on them.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/common/ApplicationPropertiesContentTest.java`
  - `services/payment/src/main/resources/application.properties:44-45`
  - `services/payment/src/test/java/com/themistra/payment/common/ResourceServerConfigTest.java` (uses `jwt()` post-processor; no decoder exercised)
- **Recommendation:** Add source-text assertions in `ApplicationPropertiesContentTest` for both `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` and `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
- **Confidence:** Medium.

---

### 4. Missing "missing entirely" test for `receipt.kms-public-keys-url`

- **Issue:** `receipt.bucket` has both a blank test and a missing-property test; `receipt.kms-public-keys-url` only has a blank test. As one of the two genuinely required fields, it deserves the same symmetry — a prefix typo or accidental rename would be caught only by the blank test, not by a missing-property test.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/common/config/ConfigPropertiesBindingTest.java:132-148`
- **Recommendation:** Add `receiptPropertiesFailWhenKmsPublicKeysUrlIsMissingEntirely`, asserted via root-cause `BindValidationException` and `kmsPublicKeysUrl`.
- **Confidence:** Low.

---

### 5. Positive confirmation: T03 implementation matches the frozen brief

- **Issue:** Not a finding — positive confirmation. The five `@ConfigurationProperties` records are `@Validated`, bound to the exact `design.md` §4c keys, and the only forced-required fields are `receipt.bucket` and `receipt.kms-public-keys-url`. `PublicEndpoints.PATTERNS` is exactly the three allowed paths, `ResourceServerConfig` wires JWT resource-server validation with RFC 9457 401/403 handlers, and `application.properties` includes all required items (datasource, JPA, Flyway disabled, Kafka group-id, actuator exposure, `show-details=never`, and the five `themistra.payment.*` groups).
- **Evidence:**
  - `services/payment/src/main/resources/application.properties`
  - `services/payment/src/main/java/com/themistra/payment/common/config/*Properties.java`
  - `services/payment/src/main/java/com/themistra/payment/common/PublicEndpoints.java`
  - `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java`
  - `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java`
- **Recommendation:** None.
- **Confidence:** High.

---

### 6. Positive confirmation: Phase 7 finding 1 is resolved by `PublicEndpointsTest`

- **Issue:** Not a finding — positive confirmation. `PublicEndpointsTest` directly asserts `PublicEndpoints.PATTERNS` contains exactly the three declared paths, parameterizes public-path reachability, parameterizes sensitive actuator paths as unauthorized, and source-scans `ResourceServerConfig` for exactly one `.permitAll()` call scoped to `PublicEndpoints.PATTERNS`. This directly addresses the Phase 7 finding.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/common/PublicEndpointsTest.java`
- **Recommendation:** None.
- **Confidence:** High.
