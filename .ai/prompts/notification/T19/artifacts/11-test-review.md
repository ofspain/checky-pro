<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T19 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T19 — Delivery-log dispute-grade / append-only verification |
| **Spec section** | R10, R11, R12, R13, L3, L9 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + final test class |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T19 regression-guard tests against the acceptance criteria.

---

## What is covered

- **AC1 — `UPDATE`/`DELETE`/`TRUNCATE` rejected as `notification_app`**
  - Inserts rows via the real application path, then attempts `UPDATE`, `DELETE`, and `TRUNCATE` over a JDBC connection authenticated as `notification_app`.
  - Asserts SQLState `42501` (insufficient privilege) for all three operations.
  - Verifies the original rows remain unchanged.
- **AC2 — Retry chain appends multiple rows for the same source event key**
  - Forces one EMAIL transient failure, then replays via `RetryScheduler.processOne`.
  - Asserts two EMAIL rows (`FAILED` then `SENT`) with attempts `(1, 2)`.
  - Asserts every row for the event shares the same `source_event_key`.
- **AC3 — Rendered rows record `template_version`**
  - Asserts every rendered row (both EMAIL and IN_APP) carries a non-null `template_version`.
- **AC4 — Suppressed channel leaves a `SUPPRESSED` row**
  - Inserts a PAYMENT/EMAIL opt-out preference, dispatches `invoice.created`.
  - Asserts a SUPPRESSED EMAIL row and a separate IN_APP row.
  - Asserts no email was actually sent and that the SUPPRESSED row has `template_version == null`.
- **AC5 — No application code path updates or deletes `delivery_log`**
  - Static scan for raw SQL `UPDATE`/`DELETE ... delivery_log` and repository `delete*` calls.
  - Reinforced by AC1's live grant proof.

---

## Gap 1 · Maven verification claim cannot be confirmed in this environment

**Why it matters:** Phase 10 reports 373 tests, 0 failures, 0 errors across two consecutive full-suite runs. This workspace does not have `mvn` available.

**Evidence:**
- `shell`/`mvn` returns `command not found` in this workspace.
- The test class is syntactically consistent, but no local execution was performed.

**Suggested action:** Run `mvn -pl services/notification clean verify` in an environment with Maven before closing the task.

---

## Gap 2 · AC5 static scan remains a textual, partial guard

**Why it matters:** The static scan catches literal SQL and repository `delete*` calls but would miss mutations via `EntityManager`, `JdbcTemplate`, or dynamically built queries.

**Evidence:**
- `noApplicationCodeUpdatesOrDeletesDeliveryLogRows` uses two regex patterns against `src/main/java`.

**Assessment:** Phase 10 correctly identified this as acceptable because AC1 is the authoritative runtime backstop. The database grant rejects any mutation regardless of how it is issued.

**Suggested action:** Document in Phase 12 that AC5's static evidence is a best-effort textual guard and that AC1 carries the complete guarantee.

---

## Gap 3 · `PreferenceResolverIntegrationTest` timestamp flake is out of scope

**Why it matters:** Phase 10 observed an intermittent failure in another task's test (`channelPreferenceEntityMapsAllSixColumnsCorrectly`) due to Docker-host clock skew on `updatedAt`.

**Evidence:**
- Phase 10 artifact lines 17–29 describe the flake and two subsequent clean runs.

**Assessment:** Not a T19 defect. The T19 tests use separate Testcontainers databases and do not touch `channel_preferences`.

**Suggested action:** Track as a follow-up for T08; fix the assertion to compare against the database clock or allow a tolerance.

---

## Gap 4 · No test guards against other destructive DDL as `notification_app`

**Why it matters:** `DROP TABLE`, `ALTER TABLE`, etc., would also destroy the dispute log. The `notification_app` role has no such privileges, but there is no live test proving it.

**Evidence:**
- `V2__notification_app_role_and_grants.sql` grants only `CONNECT`, `USAGE`, `USAGE/SELECT` on sequences, `INSERT/SELECT` on `delivery_log`.

**Assessment:** Testing every possible denied DDL operation is disproportionate. The granted privileges are explicit, and AC1 already proves the runtime role is least-privilege for DML. Not a blocking gap.

**Suggested action:** None for this task.

---

## Summary

T19's test coverage is complete for its scope. Phase 10 closed the TRUNCATE gap, and the Phase 9 strengthenings (IN_APP template-version check, explicit attempt sequence `{1, 2}`, SUPPRESSED row null template-version) are all present. The only material uncertainty is the unverified Maven run claim (Gap 1), which is an environmental limitation, not a code defect.
