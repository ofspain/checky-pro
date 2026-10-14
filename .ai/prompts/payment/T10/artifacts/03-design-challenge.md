# payment · T10 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T10/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `spec/payment-service/design.md` §4c/L1/L5/L7/L11/L12, `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql`, `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql`, `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`.

---

## 1. `recordIgnored` stores a self-transition, which may be ambiguous in the audit log

- **Issue:** The brief decides that ignored stale events are recorded with `fromState == toState == currentState`. The schema allows `from_state` to be `NULL`, which would be a more explicit "no transition occurred" signal. A future reader querying `verification_records` may not immediately distinguish a self-transition row (ignored event) from a real transition row unless they know the convention or inspect `detail`.
- **Severity:** Low — the dedicated `recordIgnored` method makes the intent explicit at the call site, but the persisted shape is not self-describing.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:23-24, 45-47, 111-113` — `recordIgnored` stores `fromState == toState`
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:62` — `from_state VARCHAR(16)` is nullable
- **Recommended brief amendment:** Either (a) document in the brief that `from_state == to_state` in a `verification_records` row means "ignored stale/out-of-order event" and require a test asserting the stored values, or (b) store `fromState = NULL` for ignored events and test that the row has a null `from_state`.
- **Confidence:** Low.

---

## 2. Append-only source-text guard covers only two of the three new production files

- **Issue:** AC5 proposes a source-text check over `VerificationRecordService.java` and `VerificationRecordRepository.java`. The third new production file, `VerificationRecord.java`, is also part of this task's own code and could theoretically contain a deletion helper (e.g., a `@PreRemove` lifecycle callback or a static method that calls a repository). The current scope of the source-text check leaves that file unexamined.
- **Severity:** Low — an entity class is unlikely to trigger deletions, but the guarantee is weaker than it appears.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:117-120, 153-157` — source-text check limited to two files
  - `artifacts/02-task-implementation-brief.md:86-90` — three production files are created
- **Recommended brief amendment:** Extend the source-text check to all three new production files (`VerificationRecord.java`, `VerificationRecordRepository.java`, `VerificationRecordService.java`). Alternatively, replace the source-text regex with a structural ArchUnit rule that forbids any class in `com.themistra.payment.verification` from calling delete/update methods on `VerificationRecordRepository`.
- **Confidence:** Low.

---

## 3. No test verifies the JSONB `detail` mapping round-trips correctly

- **Issue:** `detail` is mapped as `Map<String, Object>` with Hibernate 6's `@JdbcTypeCode(SqlTypes.JSON)`. A unit test that mocks `VerificationRecordRepository` will never exercise this mapping. If the annotation, import, or column type is wrong, the error will only surface at runtime when task 11/12 persists a record with non-null detail.
- **Severity:** Low/Medium — a mapping defect would be discovered late.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:67-69` — JSONB mapping plan
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:66` — `detail JSONB`
- **Recommended brief amendment:** Add an integration-style test (e.g., `@DataJpaTest` with an embedded database, or a Testcontainers test similar to `InvoiceRepositoryIntegrationTest`) that saves a `VerificationRecord` with a non-null `detail` map and asserts the stored/retrieved values match. If Docker remains unavailable, add at least a smoke test that the Spring context can create the `VerificationRecord` entity without a mapping exception.
- **Confidence:** Medium.

---

## 4. No validation or documentation of `triggerEventKey` shape

- **Issue:** L5 says `triggerEventKey` carries the deterministic `chain:txhash:eventtype` key. The service accepts any `String` (or null). A malformed key will be persisted silently, making later audit correlation harder. The brief correctly states this service does not dedupe, but it does not say whether the key shape should be validated or simply passed through.
- **Severity:** Low — validation may belong to the caller (task 11), but the recorder is the natural place to enforce a stable shape.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:51-53` — `triggerEventKey` carries the key, no validation
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:64` — `trigger_event_key VARCHAR(200)`
- **Recommended brief amendment:** Explicitly state that `triggerEventKey` is passed through unvalidated (caller responsibility), or add a lightweight format check (e.g., must contain at least two colons when non-null) and a corresponding test.
- **Confidence:** Low.

---

## 5. `FEATURE_MODULES` update is necessary but not sufficient to keep ArchUnit documentation accurate

- **Issue:** The brief correctly says to add `"verification"` to `ArchitectureTest.FEATURE_MODULES`. The existing Javadoc in `ArchitectureTest` describes the list as "three real top-level packages that own an `@Entity` today" and says "`events` is included ... even though it is cross-cutting infrastructure." After adding `verification`, that prose will be stale unless updated.
- **Severity:** Low — cosmetic, but misleading.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java:99-114`
  - `artifacts/02-task-implementation-brief.md:25-27, 95-96` — add `"verification"`
- **Recommended brief amendment:** Update the `FEATURE_MODULES` Javadoc to list all four modules (`invoice`, `payment`, `events`, `verification`) and note that `verification` is added by T10.
- **Confidence:** Low.

---

## Positive confirmations

- V2 already grants `INSERT, SELECT` on `verification_records`, so no new migration is needed — correct least-privilege reasoning.
- `paymentId` is modeled as a plain `Long`, avoiding a premature `Payment` entity (task 11's scope).
- Null-check scope (only `paymentId` and `toState`/`currentState`) matches the real `NOT NULL` columns exactly.
- `VerificationRecordService` correctly does not reference `PaymentStateMachine`, preserving the separation between "recorder" and "enforcer."
- `@Transactional` on `record`/`recordIgnored` will join a caller transaction when task 11/12 wires them in.
