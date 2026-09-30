# notification · T11 · Phase 1 — Specification Extraction

## Business Rules

- **R10.** IF a recipient has opted out of a channel for a given notification category, THEN the
  system SHALL suppress delivery on that channel and record the suppression in the delivery log.
- **R11.** WHEN a delivery is attempted on any channel, THEN the system SHALL append a
  delivery-log record capturing recipient, channel, source event key, template version, outcome,
  and timestamp.

## Locked Decisions

- **L3.** Dispute-grade delivery log — append-only, one row per attempt, never overwritten.
- **L5.** Channels behind one interface — `NotificationChannel` (this task's own new deliverable,
  no earlier task owns it) is that one interface; `DeliveryOrchestrator` itself is channel-agnostic.
- **L6.** Preference resolution with a safe default — `PreferenceResolver` (T08) gets its first
  real caller here.
- **L9.** Templates are versioned — `TemplateRenderer` (T09) gets its first real caller here.
- **L4.** No secrets/tokens in logs — `SecretSafeLogging.redact` (T10) gets its first real caller
  here (every `error_detail` written to `delivery_log` is redacted before being persisted).

## Files involved

**Already exists, directly reusable:**
- `PreferenceResolver`/`TemplateRenderer`/`SecretSafeLogging` (T08/T09/T10).
- `delivery_log` table, already granted `INSERT, SELECT` (T02) — no new grant migration needed.
- `NotificationDispatcher`/`NoOpNotificationDispatcher` (T06) — the latter is deleted, replaced by
  `DeliveryOrchestrator` itself implementing the former (pre-authorized since T06's own Javadoc).

**Already exists, requires one small, disclosed extension:**
- `ContactProjectionUpdater` (T05) — has no read path today, only `upsertEmail` (write). This task
  adds `Optional<String> findEmail(UUID accountUuid)` — a small, genuinely new capability
  `ContactProjectionUpdater` is the correct, already-sanctioned gateway for (it already owns the
  only legitimate access path to `ContactProjectionRepository`, which stays package-private).
  Needed for `delivery_log.recipient` (the real email, for a genuinely dispute-grade record) and
  for `EmailChannel` (task 12) to eventually know who to send to.
- `AuthEventConsumer` (T06) — both `@KafkaListener` methods add `"sourceEventKey"` →
  the method's own already-computed `eventKey` into the `eventData` map passed to `dispatch`
  (Phase 0's own resolved option (b)) — `delivery_log.source_event_key` has no other source, since
  `NotificationDispatcher.dispatch`'s own frozen signature carries no event-key parameter.

**New, this task's own deliverable (per `design.md` §6, `delivery/` and `channel/` packages):**
- `delivery/DeliveryLog.java` / `DeliveryLogRepository.java` — append-only entity/repository.
- `delivery/DeliveryOrchestrator.java` — implements `NotificationDispatcher`; the real orchestration
  logic.
- `channel/NotificationChannel.java` — the interface this task must introduce (no task explicitly
  owns it in `tasks.md`; task 12's own text assumes it already exists).
- Two temporary, real (not stub) implementations of `NotificationChannel`, one per launch channel
  (`EMAIL`, `IN_APP`) — mirroring `NoOpNotificationDispatcher`'s own established precedent, since
  neither real channel (tasks 12/13) exists yet.

## Dependencies

`PreferenceResolver`, `TemplateRenderer`, `SecretSafeLogging`, `ContactProjectionUpdater` (extended),
`DeliveryLogRepository` (new). No Kafka dependency of its own — `DeliveryOrchestrator` is called
synchronously, within `AuthEventConsumer`'s own already-open `@Transactional` boundary.

## Acceptance Criteria

1. **AC1.** `DeliveryOrchestrator implements NotificationDispatcher`; `NoOpNotificationDispatcher`
   is deleted.
2. **AC2.** A VERBATIM-copied lookup table maps each known `notificationKind` to
   `(emailTemplateName, inAppTemplateName, category)`, copied exactly from `design.md` §4c's own
   topic→template table plus the default-preferences table's own category groupings.
   `user.registered`/welcome is categorized `SECURITY` (Phase 0's own resolved reasoning — named in
   neither list, resolved by elimination and safe-default outcome). `auth.user.lifecycle
   (user.suspended) -> account.suspended` is excluded from the table entirely (unreachable,
   unseeded — Phase 0's own finding). An unrecognized `notificationKind` is a silent no-op (logged
   at `DEBUG`, no exception) — mirrors `AuthEventConsumer`'s own "never fail" outer-transaction
   philosophy.
3. **AC3.** For each of the 2 launch channels (`EMAIL`, `IN_APP`), preferences are resolved via
   `PreferenceResolver` *before* any rendering is attempted (matches the task's own literal
   ordering: "resolve prefs → render → dispatch → log"). A disabled channel appends a
   `delivery_log` row with `outcome = SUPPRESSED` and skips rendering/dispatch for that channel
   entirely.
4. **AC4.** For an enabled channel, `TemplateRenderer.render` is called with that channel's own
   resolved template name; if it throws (e.g. an unseeded template), the exception is caught here,
   not propagated — a `delivery_log` row is appended with `outcome = FAILED` and a redacted
   `error_detail`, and processing continues to the next channel.
5. **AC5.** The recipient's own email is resolved via `ContactProjectionUpdater.findEmail` once per
   `dispatch` call (not per channel) and used for `delivery_log.recipient` (`EMAIL` channel) and
   passed to `NotificationChannel.send` (channels that need it ignore it, e.g. `IN_APP`).
6. **AC6.** The resolved `NotificationChannel` bean (looked up by its own `channel()` name) is
   called; if `send` throws, the exception is caught here (never propagated) and a `delivery_log`
   row is appended with `outcome = FAILED` and a redacted `error_detail`; if `send` returns
   normally, `outcome = SENT`.
7. **AC7.** Every `delivery_log` row this task writes includes `account_uuid`, `recipient`
   (nullable), `channel`, `source_event_key` (from `eventData.get("sourceEventKey")`), `template_name`
   (nullable if rendering never happened, e.g. `SUPPRESSED`), `template_version` (nullable, same
   condition), `outcome`, `error_detail` (nullable, always passed through `SecretSafeLogging.redact`
   first). `attempt` is always `1` (the column's own `DEFAULT`) — incrementing it is task 14's own
   scope, not this task's.
8. **AC8.** `NotificationChannel` is introduced: `String channel()`, `void send(UUID accountUuid,
   String email, TemplateRenderer.RenderedMessage message)`. Two temporary implementations
   (`EMAIL`/`IN_APP`) log safely (redacted) and complete normally (report success) — real, not
   stub/TODO, matching `NoOpNotificationDispatcher`'s own established bar.
9. **AC9.** `dispatch` itself never throws for any input it can plausibly receive — every internal
   failure (unknown `notificationKind`, a failed render, a failed channel send) is caught and
   converted into a logged, non-propagating outcome. This is a hard requirement, not a nicety:
   `dispatch` runs inside `AuthEventConsumer`'s own `@Transactional` boundary, and an uncaught
   exception here would roll back the idempotency record too, causing Kafka to redeliver a message
   whose real problem (a broken template, a down email provider) redelivery cannot fix.

## Tests required

Named (`package.md` §8): `shouldRecordEveryDeliveryAttemptAndOutcomeInLog` (R11),
`shouldSuppressChannelWhenRecipientOptedOut` (R10, this task's own version proves suppression
reaches the delivery log, not merely `PreferenceResolver`'s own return value). Plus, implied by
AC2-AC9: per-outcome coverage (`SENT`/`FAILED`/`SUPPRESSED`) for both channels, the unknown-kind
no-op, the render-failure-doesn't-propagate case, and the channel-failure-doesn't-propagate case.

## Open Questions

All 6 of Phase 0's own carried-forward questions are resolved above as concrete design decisions,
not blockers. None required escalation — all six are internally resolvable via design judgment,
the same class of decision T06/T08/T09/T10 each made without user involvement.
