# payment · T05 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T05/artifacts/08-independent-review.md`  
Consumed: Phase 6 implementation, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/payment-service/agents.md`  
Scope: T05 only. Findings only — no rewrite.

---

### Executive summary

- Build: `mvn -pl services/payment test` passes — **102/102 tests green**, but that count is misleading because the two test files this task is required to create are missing.
- Phase 7 finding 1 (JPA mapping not empirically verified) is carried forward and deepened by the missing tests.
- Phase 7 finding 2 is a positive self-check; the amount-validation logic is correct by inspection.
- Four issues found: the two missing required tests, a fallback-`Clock` convention gap in the entity lifecycle, and a latent NPE-vs-business-exception inconsistency.

---

### 1. Required `InvoiceServiceTest` is missing

- **Issue:** The frozen brief lists `InvoiceServiceTest.java` under Files to Create and Required Tests (AC1, AC2, AC4). It does not exist in `services/payment/src/test/java/com/themistra/payment/invoice/`, so none of the R3/R4 rejection paths or the AC4 happy path has a permanent, CI-enforced regression guard.
- **Evidence:**
  - `artifacts/04-frozen-task-brief.md:116` (Files to Create)
  - `artifacts/04-frozen-task-brief.md:140-145` (Required Tests)
  - Direct filesystem check: no `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceTest.java`
- **Recommendation:** Create `InvoiceServiceTest.java` with at least: R3 rejection cases (unrecognized chain, null/blank token contract address), R4 rejection cases (null, zero, negative, fractional scale, >78-digit precision), and the AC4 happy path (OPEN state, generated UUID, expiresAt computed from `InvoiceProperties.defaultTtlMinutes()` and the fixed `Clock`).
- **Confidence:** High.

---

### 2. Required `ArchitectureTest` is missing

- **Issue:** The frozen brief lists `ArchitectureTest.java` under Files to Create and Required Tests (AC3/L6). It does not exist, so there is no build-time enforcement that no `float`/`double`/`Float`/`Double` field, method return type, or method parameter is introduced anywhere under `com.themistra.payment`.
- **Evidence:**
  - `artifacts/04-frozen-task-brief.md:115` (Files to Create)
  - `artifacts/04-frozen-task-brief.md:143-144` (Required Tests)
  - `artifacts/04-frozen-task-brief.md:131-132` (AC3)
  - Direct filesystem check: no `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`
- **Recommendation:** Create `ArchitectureTest.java` using the plain-`ArchRule`-field + `@Test`-canary shape (never `@ArchTest`), mirroring `services/notification/src/test/java/com/themistra/notification/ArchitectureTest.java`. Include a canary that imports a deliberately violating fixture class and proves the rule can actually fail.
- **Confidence:** High.

---

### 3. `Invoice` `@PrePersist`/`@PreUpdate` fallback uses `Instant.now()` instead of the injected `Clock`

- **Issue:** The normal path through `InvoiceService.create` sets `createdAt`/`updatedAt` from the injected `Clock`, so this fallback is unreachable in current code. If a future path ever persists or updates an `Invoice` without going through that method, the fallback violates `agents.md`'s convention to use an injectable `Clock` and makes timestamps non-deterministic in tests.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java:110-123`
  - `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java:26-34` (acknowledges the fallback pattern)
  - `spec/payment-service/agents.md:28` ("use `java.time` with an injectable `Clock`")
- **Recommendation:** Either remove the fallback and enforce that all persistence goes through `Invoice.create`/`InvoiceService.create`, or document this as the same accepted limitation already used for `OutboxEvent` (and add a test that the fallback path sets a non-null timestamp, if it is retained).
- **Confidence:** Low.

---

### 4. `merchantAccountUuid` and `payToAddress` throw `NullPointerException`, not `InvalidInvoiceRequestException`

- **Issue:** The brief deliberately distinguishes spec-named validations (chain, tokenContractAddress, amount) from defensive null guards on `merchantAccountUuid`/`payToAddress`, so this is not a bug against the brief. From an independent review perspective, it is an inconsistency: a caller passing null for any required field gets a runtime exception whose type depends on which field is null, and a future HTTP exception handler must map both `InvalidInvoiceRequestException` and `NullPointerException` (or rely on Spring's default) for the same API call.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:39-40`
  - `artifacts/04-frozen-task-brief.md:153-155` (null-handling constraint)
- **Recommendation:** No change required if the brief's distinction is intentional, but consider folding `merchantAccountUuid` and `payToAddress` null/blank checks into `InvalidInvoiceRequestException` when the DTO/controller land in task 6, so the controller-facing exception type is uniform.
- **Confidence:** Low.

---

### 5. Positive confirmation: `Invoice` entity mapping matches the schema

- **Issue:** Not a finding — positive confirmation. The entity uses `@Enumerated(EnumType.STRING)` for `state`, `@Column(precision = 78, scale = 0)` for `amount`, `short` for `tokenDecimals`, and `Instant` for timestamp columns, all matching `V1__payments_baseline.sql`.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java`
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:203-221`
- **Recommendation:** None.
- **Confidence:** High.

---

### 6. Positive confirmation: amount validation correctly covers all four R4 cases

- **Issue:** Not a finding — positive confirmation. `InvoiceService.validateAmount` rejects null, non-positive values, non-zero scale, and unscaled values exceeding 78 digits. The ordering avoids the JDK zero-scale edge case by checking `signum() <= 0` before `stripTrailingZeros()`.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:68-85`
- **Recommendation:** None.
- **Confidence:** High.

---

### 7. Positive confirmation: `InvalidInvoiceRequestException` is public and `InvoiceService.create` is `@Transactional`

- **Issue:** Not a finding — positive confirmation. The exception class is `public`, so task 6's controller/handler in a different package can catch it. `create(...)` is annotated `@Transactional`, matching AC5 and the forward-looking requirement to emit the outbox event in the same transaction.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvalidInvoiceRequestException.java:10`
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:36`
- **Recommendation:** None.
- **Confidence:** High.
