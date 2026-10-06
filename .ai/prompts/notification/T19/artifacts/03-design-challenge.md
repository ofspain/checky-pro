<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T19 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T19 — Delivery-log dispute-grade / append-only verification |
| **Spec section** | R10, R11, R12, R13, L3, L9 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/01-specification-extraction.md` + delivery_log schema + existing tests |
| **Produces** | `artifacts/03-design-challenge.md` |

Phase 3 adversarial design review of the T19 extraction before implementation.

---

## Finding 1 · The test must connect to Postgres as `notification_app`, not as the migration owner

**Challenge:** AC1 requires proving that the runtime application role cannot `UPDATE` or `DELETE` from `delivery_log`. The existing integration tests connect `spring.datasource` as `notification_app` for the application, but they use a separate admin connection for schema setup. The AC1 proof needs a JDBC connection authenticated as `notification_app` so the attempt is evaluated against that role's grants.

**Resolution:** Use the same `notification_app` credentials that the Spring context uses. In `@BeforeAll`, after creating the role and setting its password, open a JDBC connection with `POSTGRES.getJdbcUrl()`, username `notification_app`, password `NOTIFICATION_APP_PASSWORD`, and attempt the forbidden DML. Expect a `PSQLException` with SQLState `42501` (or a message containing "permission denied").

---

## Finding 2 · Test class belongs in the `delivery` package

**Challenge:** The test needs to inject `DeliveryLogRepository` and either `DeliveryOrchestrator` or `RetryScheduler`, all of which are package-private in `com.themistra.notification.delivery`.

**Resolution:** Place the new test in `com.themistra.notification.delivery`. This matches `DeliveryOrchestratorIntegrationTest` and gives direct access to the repositories and scheduler needed to drive the retry chain.

---

## Finding 3 · AC2/AC3 need a deterministic transient-failure → success chain

**Challenge:** To prove the log accumulates rows for a single source event, the test must force an EMAIL channel failure on the first attempt and then a success on the second. The production code schedules a `delivery_retry` row on transient failure; replaying it requires either waiting for the scheduler or calling `RetryScheduler.processOne` directly.

**Resolution:** Mirror the established pattern from `DeliveryOrchestratorIntegrationTest`:
- Define a local `@TestConfiguration` that provides a `ControllableEmailTransport` (primary `EmailTransport`) wrapping the real `FakeEmailTransport`.
- Configure it to fail the next N attempts.
- Call `DeliveryOrchestrator.dispatch(...)` to create the first `FAILED` row and a `delivery_retry` row.
- Call `retryScheduler.processOne(retryRow)` to replay, producing a second `SENT` row.

This avoids live timing dependencies while still exercising the real Postgres transaction path.

---

## Finding 4 · AC4 suppression must use a non-SECURITY category

**Challenge:** `design.md` states that SECURITY-category email cannot be disabled. Attempting to suppress `verify_email` would fail to produce a SUPPRESSED row because the preference resolver hard-codes SECURITY channels to enabled.

**Resolution:** Drive suppression with a PAYMENT or MARKETING category event (e.g., `invoice.created`) and a `channel_preferences` row with `enabled = false` for `(category, channel)`. This produces a `SUPPRESSED` EMAIL row while the IN_APP channel still sends, matching the existing `shouldSuppressChannelWhenRecipientOptedOut` test pattern.

---

## Finding 5 · AC3 only applies to rendered rows

**Challenge:** `template_version` is populated only when `TemplateRenderer.render` is called. SUPPRESSED rows and rows for missing-email failures are written before rendering, so they legitimately carry `template_version = null`. A blanket "every row has template_version" assertion would fail.

**Resolution:** Assert `template_version` is non-null only on rows whose `outcome` implies rendering occurred (`SENT`, `FAILED` after successful render). Explicitly allow `SUPPRESSED` and `FAILED`-no-recipient rows to have `null` template_version, documenting this as the disclosed scope.

---

## Finding 6 · AC5 is verified by code inspection plus AC1

**Challenge:** AC5 requires proving no application code path updates or deletes an existing `delivery_log` row.

**Resolution:** Two independent proofs:
1. **Static inspection:** Search `services/notification/src/main/java` for any `deliveryLogRepository.save(existingEntity)` with a set ID, any `delete`, any `update`, or any native `UPDATE`/`DELETE` statement targeting `delivery_log`. Expected result: none.
2. **Runtime proof (AC1):** Even if such a path existed, the `notification_app` role lacks `UPDATE`/`DELETE` privileges, so PostgreSQL would reject it.

Record the static-inspection result in the implementation notes / Phase 12 artifact.

---

## Finding 7 · A single test class can cover all acceptance criteria

**Challenge:** The extraction calls for "one integration test class" but lists five acceptance criteria.

**Resolution:** Implement one class with multiple focused test methods:
- `deliveryLogRejectsUpdateAndDeleteAsNotificationAppRole` → AC1
- `retryChainAppendsMultipleRowsForTheSameSourceEventKey` → AC2, AC3
- `suppressedChannelLeavesASuppressedDeliveryLogRow` → AC4
- `noApplicationCodeUpdatesOrDeletesDeliveryLogRows` → AC5 (static inspection, documented)

This keeps each AC traceable without creating multiple classes.

---

## Finding 8 · No production code changes

**Challenge:** This task is a verification-only task. The delivery log behavior is already implemented.

**Resolution:** Confirm no modifications to `DeliveryLog.java`, `DeliveryLogRepository.java`, `DeliveryOrchestrator.java`, `RetryScheduler.java`, migrations, or any other production file. The only new file is the test class and any local test-only configuration/fixture it needs.

---

## Decisions Made

1. **New test class:** `com.themistra.notification.delivery.DeliveryLogDisputeGradeIntegrationTest`.
2. **Postgres setup:** same Testcontainers + Flyway + schema/password pattern as existing integration tests.
3. **AC1 proof:** JDBC connection as `notification_app`; expect permission-denied errors on `UPDATE` and `DELETE`.
4. **AC2/AC3 proof:** `ControllableEmailTransport` fails once, then succeeds; `RetryScheduler.processOne` replays; assert two rows share `source_event_key`, differ by `attempt`, both have `template_version`.
5. **AC4 proof:** insert `channel_preferences` row disabling EMAIL for PAYMENT; dispatch `invoice.created`; assert `SUPPRESSED` row.
6. **AC5 proof:** static code inspection documented, reinforced by AC1 runtime grant restriction.
7. **No production code changes.**
