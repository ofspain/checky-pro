# notification · T19 · Phase 0 — Repository Understanding

## Task

`tasks.md` task 19: "Dispute-log check. Verify the delivery log is append-only and every attempt is
reconstructable from the source event key — the 'was the merchant notified?' evidence
(`ARCHITECTURE.md` §3.5)."

## State confirmed directly from source

**Database level (strongest enforcement):**
- `V2__notification_app_role_and_grants.sql:40`: `GRANT INSERT, SELECT ON notifications.delivery_log
  TO notification_app;` — with an explicit inline comment "no UPDATE, no DELETE, ever." The app role
  cannot modify or remove an existing `delivery_log` row at the database, regardless of application code.
- No migration grants `UPDATE` or `DELETE` on `delivery_log` to any role.

**Entity level:**
- `DeliveryLog.java` declares only getters (`getId`, `getAccountUuid`, `getRecipient`, `getChannel`,
  `getSourceEventKey`, `getTemplateName`, `getTemplateVersion`, `getAttempt`, `getOutcome`,
  `getErrorDetail`, `getCreatedAt`). No setters. Values are set only through the constructor.

**Application write path:**
- The only `delivery_log` write is `DeliveryOrchestrator.java:388`:
  `deliveryLogRepository.save(new DeliveryLog(...))`. Every call constructs a new entity with a null
  id, so JPA performs an INSERT, never an UPDATE.
- `DeliveryLogRepository` declares no custom methods and no delete path (its own Javadoc states
  "append-only with no upsert/conflict logic").
- The only `delete(...)` calls in `delivery/` target `DeliveryRetryRepository` (`RetryScheduler`
  lines 81 and 90), which is the retry *queue*, not the log. Confirmed by the field's own declared type.

**Reconstructability:**
- Every `delivery_log` row carries `source_event_key` (NOT NULL in V1), and an index
  `idx_delivery_log_event ON delivery_log(source_event_key)` exists (`V1__notifications_baseline.sql:61`).
- Retries pass the same `sourceEventKey` through `replay(...)` and `attemptSend(...)`, so attempt
  rows for one event share a key and differ by `attempt`.

## Real findings to test, not assume

The static picture is strong, but the claim is about *behavior*, so Phase 1 must prove it live:
1. A real `UPDATE` and a real `DELETE` on an existing `delivery_log` row, executed as the
   `notification_app` role, must be rejected by PostgreSQL (permission denied).
2. A real delivery sequence (verify email → retry path) must leave multiple rows that all share one
   `source_event_key`, distinguishable by `attempt`, and retrieving by that key must return the full
   attempt chain.
3. Nothing in the application can rewrite an existing row, verified by the absence of any mutation
   path and by test, not just by reading code.

## Scope note

Task 19 asks for a *check*. The retry-path evidence depends on `DeliveryRetry`/`RetryScheduler`
(T14), already built and tested. No blocker found; no user decision needed at this phase.
