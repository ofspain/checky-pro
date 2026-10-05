<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T17 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T17 — End-to-end `auth.email.requested(verify)` redelivery/idempotency proof |
| **Spec section** | R1, R7, R8, L1, L3 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + final test class |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T17 regression-guard tests against the acceptance criteria.

---

## What is covered

- **AC1 — Real Kafka event → exactly one captured email via real wiring**
  - Produces a real `auth.email.requested` message with `purpose=verify_email`.
  - Does not import any spy/test dispatcher configuration.
  - Asserts exactly one `FakeEmailTransport` message for the account.
- **AC2 — Redelivery produces no second email**
  - Sends the identical JSON a second time.
  - Asserts the captured email count remains 1 and `delivery_log` rows remain 2.
- **AC3 — Real `delivery_log` rows with outcome `SENT`**
  - Asserts two rows (EMAIL + IN_APP), both `SENT`.
  - Asserts both rows carry the computed `source_event_key`.
  - Asserts the EMAIL row's recipient is the event email and the IN_APP row's recipient is the account UUID.
- **AC4 — Payment scenario explicitly out of scope**
  - Javadoc and task artifacts state the test covers only `auth.email.requested(verify_email)`.

---

## Gap 1 · Maven verification claim cannot be confirmed in this environment

**Why it matters:** `artifacts/10-test-generation.md` states `mvn -pl services/notification test -Dtest=VerifyEmailRedeliveryIntegrationTest` ran 1/1 and `mvn -pl services/notification clean verify` ran 369 tests, 0 failures. This environment does not have `mvn` available.

**Evidence:**
- `shell`/`mvn` returns `command not found` in this workspace.
- The test class is syntactically consistent, but no local execution was performed.

**Suggested action:** Run the Maven commands in an environment where `mvn` is available before considering the task fully verified.

---

## Gap 2 · Captured email content and `inapp_notifications` row are intentionally not asserted

**Why it matters:** These are real parts of the end-to-end outcome that the test does not inspect.

**Evidence:**
- The test asserts the count and recipient correlation of `delivery_log` rows but does not inspect the rendered email subject/body or the persisted `inapp_notifications` row.

**Assessment:** This matches the task's deliberately bounded scope. Template content is covered by `TemplateRenderer`/`DeliveryOrchestratorIntegrationTest` tests; `InAppChannel` persistence is covered by `InAppChannelIntegrationTest` (T13). Re-asserting them here would be scope creep.

**Suggested action:** None — documented as out of scope.

---

## Gap 3 · Captured email `to` field is not directly asserted

**Why it matters:** The test filters `FakeEmailTransport.sentMessages()` by `accountUuid` and asserts one message. It does not assert the message's `to()` address equals `e2e@example.com`.

**Evidence:**
- `DeliveryOrchestratorIntegrationTest.dispatchCapturesARealSentEmailWithCorrectFields` already asserts `to()` against the contact projection email.
- This test only asserts the `delivery_log` EMAIL row recipient.

**Assessment:** The `delivery_log` assertion indirectly proves the real email address reached the log. Adding a direct `to()` assertion on the captured message would be a small extra correlation but is not required by any AC.

**Suggested action:** Optional Phase 9/12 enhancement; not blocking.

---

## Gap 4 · No test for a different event key producing a new delivery

**Why it matters:** The test proves idempotency for the *same* key. It does not prove that a *different* key results in a new email, which is the positive counterpart of idempotency.

**Evidence:**
- The test sends the same JSON twice with the same `accountUuid`/`occurredAt`.

**Assessment:** This is implicit in the design and covered by the first-delivery half of this test (a never-before-seen key is processed). A dedicated second-account scenario would add confidence but is not required by R7/R8.

**Suggested action:** Optional future test; not blocking for this task.

---

## Gap 5 · Shared local Kafka broker state can affect repeated local runs

**Why it matters:** The Phase 7/10 artifacts note a harmless, non-reproducing listener exception caused by leftover messages from earlier manual reruns against the persistent shared broker.

**Evidence:**
- Service convention uses a shared local broker, not Testcontainers Kafka.
- The consumer is already proven to recover and continue processing.

**Assessment:** Environmental, not a test defect. CI uses a fresh broker per build.

**Suggested action:** None. Document in Phase 12 for future debugging context.

---

## Summary

T17's single integration test satisfies all four acceptance criteria. Phase 9 added `source_event_key` and recipient correlation; Phase 10 added the symmetric IN_APP recipient assertion. The remaining uncovered areas are either environmental (Maven verification, shared broker state) or intentionally out of scope (email content, `inapp_notifications`). No additional tests are required to meet the brief.
