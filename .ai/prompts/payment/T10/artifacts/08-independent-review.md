# payment · T10 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T10/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `VerificationRecord.java`, `VerificationRecordRepository.java`, `VerificationRecordService.java`, `ArchitectureTest.java`.

---

## Executive summary

- T10 production code is complete and matches the frozen brief: `VerificationRecord` maps the table exactly, `VerificationRecordService` records transitions/reversals and ignored events with `fromState = null`, the repository is append-only (no delete calls), and `ArchitectureTest.FEATURE_MODULES` now includes `verification`.
- `mvn -pl services/payment test` — **207 tests run, 0 failures, 2 errors** — all errors are in the unrelated T08 `InvoiceRepositoryIntegrationTest` (missing transaction around a `@Modifying` call). T10 itself is green.
- The main code-quality finding is that the `detail` JSONB map breaks the entity's stated immutability because it is stored and returned by reference.

---

## 1. `detail` is stored and exposed by reference, weakening the immutability claim

- **Issue:** `VerificationRecord.create(...)` assigns the supplied `Map<String, Object>` directly to the field, and `getDetail()` returns that same reference. A caller can mutate the map after calling `record(...)` (or after retrieving the entity), which changes the entity's state before Hibernate flushes it and violates the class-level "immutable" claim.
- **Severity:** Medium — functional now because callers currently pass small maps and do not mutate them, but the contract is unsafe.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecord.java:79-91` — direct assignment
  - `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecord.java:124-126` — direct return
- **Recommendation:** Make the entity defensively copy the map on creation and return an unmodifiable copy (or copy) from the getter:
  - In `create`: `record.detail = detail == null ? null : Map.copyOf(detail);` (or `Collections.unmodifiableMap(new HashMap<>(detail))` if null keys/values are needed)
  - In `getDetail`: `return detail == null ? null : Map.copyOf(detail);`
- **Confidence:** High.

---

## 2. No test exercises the JSONB `detail` mapping

- **Issue:** `detail` is the first JSONB column in this service and uses Hibernate 6's `@JdbcTypeCode(SqlTypes.JSON)`. With only mocked-repository unit tests planned, the mapping is not verified against a real database. A wrong import, missing annotation, or type mismatch would only surface when task 11/12 writes a non-null detail payload in an integration path.
- **Severity:** Low/Medium — a runtime-only mapping defect.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecord.java:68-70`
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:66`
- **Recommendation:** Add an integration test (Testcontainers `@DataJpaTest` or a simple repository integration test) that persists and retrieves a `VerificationRecord` with a non-null `detail` map and asserts round-trip equality. If Docker is unavailable, at least add a Spring-context smoke test that loads `VerificationRecord` without a mapping exception.
- **Confidence:** Medium.

---

## 3. `VerificationRecord` fields are non-final despite the "immutable" documentation

- **Issue:** The Javadoc says the entity is immutable, but all fields are non-final and there are setters-by-field-assignment inside `create`. This is a JPA convention (proxies, reflection), not a runtime bug, but it is inconsistent with the class's own description.
- **Severity:** Low — cosmetic/documentation.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecord.java:20-21, 47-73`
- **Recommendation:** Either (a) make fields `final` if Hibernate bytecode enhancement / build-time enhancement is configured in the project, or (b) soften the Javadoc to "effectively immutable after creation; fields are non-final to satisfy JPA proxy requirements."
- **Confidence:** Low.

---

## 4. Build context — unrelated T08 integration test is still failing

- **Issue:** `InvoiceRepositoryIntegrationTest` fails with `TransactionRequiredException` in two tests because it calls `InvoiceRepository.updateStateIfCurrentState` directly without an active transaction. This is a T08 test defect, not a T09/T10 production defect.
- **Severity:** Medium — CI is red.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceRepositoryIntegrationTest.java`
  - `services/payment/target/surefire-reports/com.themistra.payment.invoice.InvoiceRepositoryIntegrationTest.txt`
- **Recommendation:** Add `@Transactional` to the test class or call the repository through a transactional helper. This belongs to T08, not T10.
- **Confidence:** High.

---

## Positive confirmations

- `record(...)` treats forward transitions and reversals identically — correct per R11/R12.
- `recordIgnored(...)` stores `fromState = null`, `toState = currentState` — matches the Phase 4 decision and is unambiguous.
- Null checks in `VerificationRecordService` cover exactly the non-nullable columns: `paymentId` and `toState`/`currentState`.
- `VerificationRecordService` does not import or reference `PaymentStateMachine`, preserving the recorder/enforcer separation.
- `VerificationRecordRepository` is package-private, consistent with `InvoiceRepository`.
- No `.delete`/`.deleteById`/`.deleteAll` calls exist across `VerificationRecord.java`, `VerificationRecordRepository.java`, or `VerificationRecordService.java`.
- `ArchitectureTest.FEATURE_MODULES` was updated to include `"verification"`, and all 5 ArchUnit tests pass.
- `payment_app` already has the correct `INSERT`/`SELECT` grants from V2; no new migration is needed.
- `VerificationRecord` maps columns exactly: `payment_id`, `from_state`, `to_state`, `trigger_event_key`, `confirmations`, `detail`, `occurred_at`.
- `occurredAt` is set explicitly from the injected `Clock`, with a `@PrePersist` `Instant.now()` fallback.

---

## Recommended follow-up order

1. Fix the `detail` mutability issue (defensive copy on creation and getter).
2. Add a JSONB round-trip integration test in Phase 10.
3. Clarify the "immutable" Javadoc or make fields final if the build supports it.
4. Fix the unrelated T08 `InvoiceRepositoryIntegrationTest` transaction issue so the full build is green.
