<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# notification · T17 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T17 — End-to-end `auth.email.requested(verify)` redelivery/idempotency proof |
| **Spec section** | R1, R7, R8, L1, L3 |
| **Model** | Kimi 2.7 |
| **Consumes** | Implementation + `artifacts/07-self-review.md` + frozen brief |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the T17 implementation and self-review.

---

## Finding 1 (concur with self-review Finding 1) · Diagnostic messages are now consistent

**Severity:** Low

**Evidence:**
- `VerifyEmailRedeliveryIntegrationTest.java` lines 139–155: every `await()`/`assertThat()` block now has a `.as("...")` description.

**Assessment:** The self-review correctly identified that the initial Phase 6 version lacked `.as(...)` on the first two waits, making timeout failures hard to diagnose. The fix is present and correct.

**Recommendation:** No further action needed.

---

## Finding 2 · `delivery_log.source_event_key` propagation is not asserted

**Severity:** Low

**Evidence:**
- The test computes `eventKey = accountUuid + ":verify_email:" + occurredAt`.
- It asserts `processedEventExists(eventKey)` and two `SENT` `delivery_log` rows, but never checks that `delivery_log.source_event_key` equals `eventKey`.

**Assessment:** AC3 requires a real `delivery_log` row produced by the real chain with outcome `SENT`. The test satisfies that. However, a key part of the dispute-grade log design (L3) is that every delivery attempt is correlatable to the source event key. The `AuthEventConsumer` → `DeliveryOrchestrator` hand-off places the key into `eventData` under `"sourceEventKey"`; asserting it lands in `delivery_log` would prove the real chain preserves correlation end-to-end.

**Recommendation:** Extend the `DeliveryLogRow` record to include `sourceEventKey` and assert it equals the computed `eventKey` for both rows. This is a small strengthening of AC3 without expanding scope.

---

## Finding 3 · The `delivery_log` row is not linked to the captured email recipient

**Severity:** Very low / informational

**Evidence:**
- The test asserts one captured email for the account and two `SENT` rows, but does not assert the `EMAIL` row's `recipient` equals the event's email address (`e2e@example.com`).

**Assessment:** This does not violate any AC. It would, however, catch a regression where the email is captured but the `delivery_log` records a wrong/null recipient. `DeliveryOrchestratorIntegrationTest` already asserts recipient equality.

**Recommendation:** Optional Phase 9 enhancement: include `recipient` in `DeliveryLogRow` and assert the `EMAIL` row's recipient is `e2e@example.com`. Not blocking.

---

## Finding 4 · No assertion that the second Kafka message is actually consumed

**Severity:** Very low / informational

**Evidence:**
- Redelivery assertion (lines 159–168) waits 3 seconds, then asserts the email count and `delivery_log` row count are unchanged.

**Assessment:** If the second message were never consumed at all (e.g., the consumer group offset jumped past it), the test would still pass. The intended proof is "redelivery does not produce a second email," which is satisfied by the unchanged counts. Proving the second message was actually consumed and deduped is a subtly stronger claim.

**Recommendation:** Optional: add a debug/log-scrape assertion or a metric that the second record was seen by the listener. Not required for AC2 and consistent with `AuthEventConsumerIntegrationTest`'s redelivery assertion, which has the same shape.

---

## Finding 5 · Shared local Kafka broker state can leak between manual reruns

**Severity:** Very low / informational

**Evidence:**
- Self-review Observation describes a one-time listener exception trace during repeated local reruns, attributed to leftover messages from earlier runs on the shared local broker.
- The service's established convention is a shared local broker (not Testcontainers Kafka), so state persists across test invocations in the same session.

**Assessment:** This is environmental, not a code defect. The consumer is already proven to recover from exceptions and continue (`AuthEventConsumerIntegrationTest.malformedMessageDoesNotPermanentlyPoisonTheListener`). CI runs with a fresh broker lifetime per build will not exhibit this.

**Recommendation:** No code change. Document the observation in the task artifact so future interactive debugging does not mistake it for a regression.

---

## Finding 6 · Implementation chose JDBC for `delivery_log` rather than `DeliveryLogRepository`

**Severity:** Informational

**Evidence:**
- Phase 3 design challenge recommended placing the test in `delivery` and injecting `DeliveryLogRepository` directly, querying `processed_events` via JDBC.
- The implemented test uses JDBC for both `processed_events` and `delivery_log`.

**Assessment:** This is a valid, arguably simpler choice. It avoids any package-private access concerns and keeps the query shape explicit. The trade-off is slightly more boilerplate than a repository method. Acceptable.

**Recommendation:** No change needed. Update Phase 3 design-challenge artifact if desired to reflect the actual choice, or leave it as a documented alternative.

---

## Cross-check against acceptance criteria

| Criterion | Status | Notes |
|---|---|---|
| AC1 — Real Kafka event → exactly one captured email via real wiring | ✅ | Test produces to `auth.email.requested`, waits for `processed_events`, and asserts one captured email. No spy config is imported. |
| AC2 — Redelivery produces no second email | ✅ | Same message resent; email count and `delivery_log` row count stay at 1 and 2 respectively. |
| AC3 — Real `delivery_log` row with outcome `SENT` | ✅ | Two `SENT` rows (EMAIL + IN_APP) asserted. Optional strengthening: assert `source_event_key`. |
| AC4 — Payment scenario explicitly out of scope | ✅ | Javadoc states the test is scoped to `auth.email.requested(verify_email)` only. |

---

## Verdict

The implementation satisfies all acceptance criteria. The self-review finding was fixed inline. The only material improvement opportunity is Finding 2 (asserting `source_event_key` in `delivery_log`), which strengthens AC3 without scope creep. The other findings are optional or environmental. No production code changes are required.
