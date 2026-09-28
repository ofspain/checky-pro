# notification · T06 · Phase 1 — Specification Extraction

## Business Rules

- **R1.** WHEN an `auth.email.requested` event with purpose `verify_email` is consumed, THEN the
  system SHALL send the recipient an email-verification message containing the verification link.
- **R2.** WHEN an `auth.email.requested` event with purpose `password_reset` is consumed, THEN the
  system SHALL send the recipient a password-reset message containing the reset link.
- **R6.** WHEN an `auth.user.lifecycle` event with type `user.registered` is consumed, THEN the
  system SHALL send the user a welcome/onboarding message.

All three literally require the system to **send** a message. Nothing in this codebase can send an
email until task 12 (`EmailChannel`) lands — see Open Questions.

## Locked Decisions

- **L1.** Idempotent by event key — this task's own literal "Idempotent per Task 4" instruction:
  every consumed message must be deduped via `IdempotencyGuard.recordIfNew` before any further
  processing.
- **L2.** Consume-only — no synchronous cross-service call anywhere in this consumer.
- **L5.** Channels behind one interface (`NotificationChannel`) — not yet built (task 12); this task
  cannot dispatch to a channel that doesn't exist.
- **L9.** Templates are versioned — the `templates` table (T02) and its own `Template` entity (task
  9, not yet built) are what "the corresponding templates" ultimately resolves to.

## Files involved

**Already exists, directly reusable:**
- `consumer/IdempotencyGuard.java` (T04) — `recordIfNew(eventKey, eventType)`.
- `preference/ContactProjectionUpdater.java` (T05) — `upsertEmail(accountUuid, email, occurredAt)`.
- `contracts/events/auth/{email-requested,user-lifecycle}.v1.schema.json` — both now carry `email`
  (auth-service commit `64557d3`).

**Not yet built, referenced by "→ the corresponding templates" but explicitly later tasks:**
- `template/{Template,TemplateRepository,TemplateRenderer}.java` — task 9.
- `delivery/{DeliveryOrchestrator,DeliveryLog,DeliveryLogRepository}.java` — task 11.
- `channel/{NotificationChannel,EmailChannel}.java` — task 12.
- `preference/{ChannelPreference,PreferenceResolver}.java` — task 8.

**New, this task's own deliverable (per `design.md` §6, `consumer/` package), scope pending Open
Question resolution:**
- `consumer/AuthEventConsumer.java` — the actual `@KafkaListener`.
- `consumer/dto/` — deserialization records matching `contracts/events/auth/*` (not `auth-service`'s
  own classes — cross-service source dependencies are forbidden).

## Dependencies

`spring-kafka` (present since T01, unused until now), `IdempotencyGuard` (T04), `ContactProjectionUpdater`
(T05). No Kafka consumer configuration exists anywhere in this module yet (group id, deserializer,
error handling, offset strategy) — this task establishes that convention for the first time in this
codebase.

## Acceptance Criteria

**Cannot be finalized without resolving the Open Question below.** Provisionally, whatever this
task's own real scope turns out to be, it must:
1. Consume both `auth.email.requested` and `auth.user.lifecycle` via a real `@KafkaListener`.
2. Deserialize against the real, current contract shape (`accountUuid`, `purpose`/`token`/`email` or
   `status`/`email`, `occurredAt`).
3. Call `IdempotencyGuard.recordIfNew` first; skip all further processing on `false`.
4. Call `ContactProjectionUpdater.upsertEmail` to refresh the projection.
5. Never make a synchronous call to `auth-service` or anywhere else (L2).
6. Resolve *which template name* applies per event/purpose — a pure mapping decision, not a DB read
   (no `Template` entity exists yet to query).

## Tests required

Named (`package.md` §8): `shouldSendVerificationEmailOnAuthEmailRequestedVerify`,
`shouldSendPasswordResetEmailOnAuthEmailRequestedReset`, `shouldWelcomeUserOnUserRegistered` — all
three require an actual send, which this task alone cannot achieve. Whether these are satisfied
*partially* now (asserting the consumer resolves the correct template name and reaches whatever seam
this task defines) or deferred *entirely* to task 12 is the same open question below. Boundary tests
implied regardless of that resolution: idempotent redelivery (a second delivery of the same event
must not re-process), and each of the 3 event/purpose combinations routing to a distinct template
name.

## Open Questions

1. **BLOCKER, carried forward from Phase 0, not resolved here.** `AuthEventConsumer`'s own "→ the
   corresponding templates" scope depends on `Template`/`TemplateRenderer` (task 9),
   `DeliveryOrchestrator` (task 11), and `EmailChannel` (task 12) — none of which exist yet. R1/R2/R6
   all literally require the system to **send** a message; this task alone cannot make that true.
   Two real paths forward, neither decided here:
   - (a) This task defines a narrow **seam** — e.g., an interface (`NotificationDispatcher` or
     similar) with a single method this consumer calls once it has resolved a template name and
     recipient, which task 11/12 later implement fully. This task's own tests would then prove the
     consumer calls that seam with the correct arguments, not that an email was actually sent.
   - (b) This task's own scope is narrowed to *only* the consumer + dedupe + projection-update
     wiring, with template-name resolution and the "→ templates" routing explicitly deferred to a
     later task (9 or 11) that has the actual pieces to act on it. The 3 named tests would then be
     tracked as *not yet satisfiable* until that later task, not attempted here.
   **This requires an explicit human decision before Phase 2**, mirroring T05's own Phase 1/2
   resolution process for its own real blocker.
