# notification · T11 · Phase 2 — Task Implementation Brief

## Task

Implement `DeliveryOrchestrator` (replacing `NoOpNotificationDispatcher`): resolve preferences per
channel → render the resolved template → dispatch via a `NotificationChannel` bean → append a
`delivery_log` row per attempt with outcome. Introduce `NotificationChannel` itself (no earlier
task owns it) plus two temporary, real implementations (`EMAIL`/`IN_APP`).

## Purpose

The first task wiring `PreferenceResolver` (T08), `TemplateRenderer` (T09), and
`SecretSafeLogging` (T10) to a real caller, and the second-and-final piece (after `AuthEventConsumer`,
T06) of the auth-event delivery path — from "auth published an event" to "we recorded exactly what
happened, per channel, for dispute resolution."

## Scope

**In:**
- `delivery/DeliveryLog.java` — a real, constructible `@Entity` (unlike every prior task's own
  read-mostly entities) mapping onto `delivery_log`'s 11 columns; a public constructor taking every
  field except `id` (generated) and an explicit `createdAt` (an injected `Clock`, matching T04's own
  established testability precedent — never the DB's own `now()` default).
- `delivery/DeliveryLogRepository.java` — `extends JpaRepository<DeliveryLog, Long>`, no custom
  query: this is the first module repository where a plain `save()` is the correct, idiomatic
  write path (append-only, no upsert/conflict logic needed).
- `delivery/DeliveryOrchestrator.java` — `@Component`, `implements NotificationDispatcher`. A
  VERBATIM-copied `Map<String, NotificationMapping>` (a small private record:
  `emailTemplateName`/`inAppTemplateName`/`category`) covering all 7 reachable-or-not mappings from
  `design.md` §4c (Phase 1's own resolved `user.registered` → `SECURITY`, `account.suspended`
  excluded). `dispatch` never throws (AC9) — every internal failure is caught and converted to a
  logged `delivery_log` row.
- `channel/NotificationChannel.java` — the interface this task introduces: `String channel()`,
  `void send(UUID accountUuid, String email, TemplateRenderer.RenderedMessage message)`.
- `channel/NoOpEmailChannel.java` / `channel/NoOpInAppChannel.java` — temporary, real (not stub)
  implementations, `channel()` returning `"EMAIL"`/`"IN_APP"` respectively, logging safely (via
  `message`'s own already-redacted `toString()`) and completing normally.
- `preference/ContactProjectionUpdater.java` (T05, extended) — new public method
  `Optional<String> findEmail(UUID accountUuid)`, the first read path this class exposes.
- `consumer/AuthEventConsumer.java` (T06, extended) — both `@KafkaListener` methods add
  `"sourceEventKey"` → the method's own already-computed `eventKey` into the `eventData` map
  passed to `dispatch`.

**Out:**
- Real email/in-app sending (tasks 12/13's own scope) — the two temporary channels here are
  deliberately as inert as `NoOpNotificationDispatcher` was.
- Retry/attempt-increment/dead-letter logic (task 14) — `attempt` is always `1`.
- `PaymentEventConsumer` (T07, skipped) — the 4 payment-derived mappings are built but unreachable.

## Business Rules

R10, R11 (stated in full in Phase 1's own extraction).

## Locked Decisions

L3, L4, L5, L6, L9 (stated in full in Phase 1's own extraction) — this task is where all five
converge for the first time.

## Dependencies

`PreferenceResolver`, `TemplateRenderer`, `SecretSafeLogging`, `ContactProjectionUpdater`
(extended), `Clock` (existing `ClockConfig` bean, T04), Spring's own multi-bean injection
(`List<NotificationChannel>`, resolved to both temporary implementations at this point in the
pipeline).

## Acceptance Criteria

Unchanged from Phase 1's own AC1-9, stated in full there.

## Required Tests

Unchanged from Phase 1's own extraction: `shouldRecordEveryDeliveryAttemptAndOutcomeInLog` (R11),
`shouldSuppressChannelWhenRecipientOptedOut` (R10, delivery-log-reaching version), per-outcome
coverage for both channels, the unknown-kind no-op, render-failure/channel-failure
non-propagation.

## Constraints

- **`dispatch` must never throw** (AC9) — the single hardest constraint this task has; verified
  by Phase 7's own empirical review, not merely asserted.
- **No new grant migration** — `delivery_log` was already granted `INSERT, SELECT` at T02.
- **`ContactProjectionRepository` stays package-private** — `findEmail` is added to
  `ContactProjectionUpdater` (already public, already the sanctioned gateway), not by exposing the
  repository itself more broadly.
- **The lookup table is VERBATIM** — copied exactly from `design.md` §4c's own two tables, not
  paraphrased or reordered.

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryLog.java`
- `.../delivery/DeliveryLogRepository.java`
- `.../delivery/DeliveryOrchestrator.java`
- `.../channel/NotificationChannel.java`
- `.../channel/NoOpEmailChannel.java`
- `.../channel/NoOpInAppChannel.java`

## Files to Modify

- `services/notification/src/main/java/com/themistra/notification/preference/ContactProjectionUpdater.java`
  — add `findEmail`.
- `services/notification/src/main/java/com/themistra/notification/consumer/AuthEventConsumer.java`
  — add `sourceEventKey` to both `eventData` maps passed to `dispatch`.
- `T01SkeletonRegressionTest.java` — expected, per T04/T05/T06/T08/T09/T10's own unbroken
  precedent (not pre-authorized with exact content here).

## Files to Delete

- `services/notification/src/main/java/com/themistra/notification/consumer/NoOpNotificationDispatcher.java`
  — pre-authorized since T06's own Javadoc; `DeliveryOrchestrator` is its real replacement.

## Files NOT to Modify

- `services/notification`'s own other T01-T10 files (`pom.xml`, migrations, `PreferenceResolver`,
  `TemplateRenderer`, `SecretSafeLogging`, config records, `ResourceServerConfig`,
  `PublicEndpoints`).
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment` — no cross-service dependency exists for
  this task.

## Open Questions

No blockers. All 6 of Phase 0/1's own carried-forward questions are resolved as concrete design
decisions, not re-opened here.
