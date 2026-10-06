# notification · T19 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T19 — Dispute-log check: append-only and reconstructable by source event key |
| **Consumes** | All T19 artifacts, Phases 0–11 (including the Phase 9 and Phase 11 addenda) |
| **Produces** | `artifacts/12-specification-verification.md` |

## Traceability matrix

| Requirement | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **L3** — append-only; a retry adds a row and never overwrites the prior attempt | Yes | DB grant `V2__notification_app_role_and_grants.sql:40` (`INSERT, SELECT` only); `DeliveryLog.java` has no setters; the sole write path `DeliveryOrchestrator.java:388` constructs a new entity | `deliveryLogRejectsUpdateAndDeleteAsNotificationAppRole` (UPDATE, DELETE, TRUNCATE all rejected with SQLState `42501`) | No | No |
| **L3** — dispute-grade record of every attempt (recipient, channel, source key, template version, outcome, timestamp) | Yes | `delivery_log` columns; `created_at` set on insert | `retryChainAppendsMultipleRowsForTheSameSourceEventKey` asserts recipient, timestamp, and outcome per row | No | No |
| **L9** — template version recorded for each rendered message | Yes | Rendered rows carry `template_version` (`DeliveryOrchestrator` renders before saving) | Same retry test asserts non-null on every rendered row, EMAIL and IN_APP (Phase 9 fix) | No | No |
| **R11** — every attempt appends a log record with recipient, channel, source key, template version, outcome, timestamp | Yes | Same evidence as L3 and L9 | Same as above | No | Scope-disclosed at Phase 1: rows written before rendering legitimately have no template version |
| **R12** — a transient failure is marked FAILED, retained in the log, and retried | Yes | `DeliveryOrchestrator.dispatch` writes the `FAILED` row and schedules `delivery_retry`; `RetryScheduler.processOne` replays it | `retryChainAppendsMultipleRowsForTheSameSourceEventKey`: FAILED then SENT, attempts `{1, 2}` | No | No |
| **R13** — terminal `DEAD_LETTERED` outcome after max attempts | Yes (T14, not re-tested here) | T14 `RetryScheduler` and `DeliveryOrchestrator.recordUnrecoverableFailure` | Covered by T14's own tests; this task does not re-prove it | No | No — out of T19's own scope, which is append-only and reconstructability |
| **R10** — opted-out channel is suppressed and the suppression recorded in the log | Yes | `DeliveryOrchestrator.java:199` writes a `SUPPRESSED` row with a null template version | `suppressedChannelLeavesASuppressedDeliveryLogRow` | No | No |
| **AC1** — UPDATE, DELETE, and TRUNCATE as `notification_app` are rejected by PostgreSQL | Yes | Grant evidence above; the migration's own note that table owners bypass grants, so the proof runs as the app role | The AC1 method, live | No | No |
| **AC2** — a retry chain shares one source key, and rows are distinguishable by attempt | Yes | Retry driven by `processOne` on a real `delivery_retry` row | The AC2 method, with exact attempt sequence `{1, 2}` | No | No |
| **AC3** — rendered rows record a template version | Yes | As above | The AC2 method, all rendered rows | No | Scope as disclosed in Phase 1 |
| **AC4** — suppression leaves a SUPPRESSED row; no email is sent | Yes | As above | The AC4 method, including a null template version on the suppressed row | No | No |
| **AC5** — no application code path updates or deletes a `delivery_log` row | Yes, by static scan plus the live grant | Scan of `src/main/java` in the AC5 method; the grant is the authoritative guarantee (AC1) | `noApplicationCodeUpdatesOrDeletesDeliveryLogRows`, shown to fail on a planted violation in Phase 6 | No | Textual scan is best-effort; disclosed Phase 7 and Phase 9 |

## Answers

**(1) Is the task fully complete?** Yes. The append-only property is proven live against the real
database role, and the reconstructability property is proven live through a real retry chain and a
real suppression. Every acceptance criterion has a named, executed test.

**(2) Does it satisfy every acceptance criterion?** Yes. AC1 is the strongest: it proves the grant
rejects UPDATE, DELETE, and TRUNCATE at the database. The TRUNCATE check was added in Phase 10 after
the audit found the append-only claim was not fully tested. AC5's static scan is the weakest link,
which is why AC1, not the scan, carries the authoritative guarantee.

**(3) Does it violate any LOCKED decision?** No. L3 and L9 hold as written. No production code was
changed for this task.

**(4) Remaining risks?**
- **AC5 static scan is textual.** It would not catch mutations built through `EntityManager` or
  dynamically assembled SQL. The database grant still rejects those, so the property holds, but the
  static evidence alone is narrower than the claim. Disclosed.
- **The "was the merchant notified?" evidence depends on the delivery log being written.** This task
  proves the log is append-only and reconstructable when it is written. It does not prove every
  possible failure path writes a row. R13's dead-letter path and T14's other paths carry that proof,
  and it is not re-proven here.
- **Intermittent flake in another task.** `PreferenceResolverIntegrationTest` (T08) compares a
  database-written timestamp against the JVM clock and failed once in a full run. It is not in T19's
  scope, is recorded as a follow-up, and is not caused by this task.
- **Template version is absent on rows written before rendering** (SUPPRESSED, no-recipient FAILED).
  This is correct per R11's own meaning, since nothing was rendered, and it is scoped and disclosed.

## Verdict

**PASS.** T19 satisfies L3 and L9, the R10–R13 behaviors it touches, and every acceptance criterion
AC1–AC5 within its own scope. The one property that rests on a static guard (AC5) is backed by the
live grant proof (AC1). The full suite is green at 373 tests, 0 failures, 0 errors.
