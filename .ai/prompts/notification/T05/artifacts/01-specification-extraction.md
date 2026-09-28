# notification · T05 · Phase 1 — Specification Extraction

## Business Rules

No individual R-numbered requirement is directly implemented by this task — like T02/T04, it lays
down infrastructure (a projection table's own population mechanism) that later requirements depend
on. Relationship to requirements: **R1, R2, R6** (verification/reset/welcome emails) all require a
real recipient email address, which `contact_projection` is meant to supply — this task is a
blocker for all three, matching the task statement's own "(blocker for email delivery)" framing.

## Locked Decisions

- **L2.** Consume-only at launch — this service "initiates no domain state and makes no synchronous
  cross-service call on the delivery path... The only permitted projection read is the
  recipient-contact projection (O1), which is itself fed by consumed auth events, not a live call."
  This directly forecloses one of the two real options Phase 0 identified for the blocker below (a
  live Auth internal endpoint call) — whatever this task's data source turns out to be, it cannot be
  a synchronous call to `auth-service`.

## Files involved

**Already exists, read-only precedent/dependency:**
- `services/notification/src/main/resources/db/migration/V1__notifications_baseline.sql` (T02) —
  `notifications.contact_projection` table, already migrated: `account_uuid UUID PRIMARY KEY`,
  `email CITEXT`, `display_name VARCHAR(200)`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
  Currently fully ungranted to `notification_app`.
- `consumer/{ProcessedEvent,ProcessedEventRepository,IdempotencyGuard}.java`, `common/ClockConfig.java`
  (T04) — structural precedent (entity/repository/service shape, explicit schema mapping, injectable
  `Clock`), not a functional precedent (T04 is insert-or-skip; this task is more plausibly an upsert
  given `updated_at`'s own presence).
- `contracts/events/auth/{user-lifecycle,email-requested}.v1.schema.json` and the real auth-service
  payload classes that produce them (`UserLifecycleEventPayload`, `EmailRequestedEventPayload`) —
  read directly this task's own Phase 0, not assumed: neither carries an email address or display
  name (see Open Questions).

**New, this task's own deliverable (per `design.md` §6, `preference/` package):**
- `services/notification/src/main/java/com/themistra/notification/preference/ContactProjection.java`
- `.../preference/ContactProjectionRepository.java`
- A grant migration (`V5`) for `contact_projection`, if this task's own resolved scope needs real DB
  access proven (mirroring T02/T04's own proactive-test precedent).
- Whatever projection-update logic the resolved Open Question below implies — undetermined until
  that's resolved.

## Dependencies

`spring-boot-starter-data-jpa` (present), `notification_app`'s DB role + a new grant, `Clock`
(existing `ClockConfig` bean, T04). Whether this task depends on a real `@KafkaListener`/consumer
infrastructure is itself unresolved (Phase 0's own secondary open question).

## Acceptance Criteria

**Cannot be finalized without resolving the Open Question below.** Provisionally, whatever this task
delivers must:
1. Map `ContactProjection` exactly onto `contact_projection`'s existing 4 columns.
2. Provide an update mechanism that is a genuine upsert (`account_uuid` may already have a row;
   `updated_at` implies revision over time), not merely an insert-once ledger like T04's own
   `ProcessedEvent`.
3. Never make a synchronous call to `auth-service` (L2).
4. Populate `email`/`display_name` from *some* real, spec-defensible data source — which source is
   exactly the open question.

## Tests required

`package.md` §8 has no test named for contact projection specifically (its 19 named tests all map to
R1-R19/L11, none of which is "populate contact_projection"). Whatever this task's own required tests
turn out to be depends entirely on resolving the Open Question — an upsert-logic unit/integration
test is plausible (mirroring T04's own `IdempotencyGuardUnitTest`/`IdempotencyGuardIntegrationTest`
split), but cannot be specified further here.

## Open Questions

1. **BLOCKER, escalated from Phase 0, not resolved here.** Neither `auth.user.lifecycle` nor
   `auth.email.requested` — the two events this task's own literal text names as the data source —
   actually carries an email address or display name anywhere in their real, current payload
   (verified against both the JSON contracts and the real auth-service Java classes that produce
   them). `design.md` §4b-O1's own recommended design explicitly assumes "reading the email from
   `auth.email.requested` payloads," which is not true of the current contracts. `package.md` §11's
   own Q1 already names this class of problem ("Blocker for email delivery... Recommended default...
   is (c) a projection — confirm") but its own text (option (a): "events... already include the
   email") appears to assume a payload shape that doesn't exist. This task cannot be meaningfully
   scoped into a Task Implementation Brief until this is resolved — the real choices, given L2
   forecloses a live Auth call, are narrower than Q1's own text suggests: either (i) a Contact
   projection populated with `account_uuid`/`updated_at` only, `email`/`display_name` left `NULL`
   until some other, not-yet-identified event or mechanism supplies them (making this task genuinely
   partial, deferring the "blocker for email delivery" problem rather than solving it), or (ii) a
   cross-service contract change to one or both auth events to add the missing fields — entirely
   outside this task's own repo boundary and this pipeline's own authority to decide unilaterally.
   **This requires an explicit human decision before Phase 2.**
2. Whether this task owns a real `@KafkaListener` or only the projection-update logic a later task's
   listener calls (Phase 0's own secondary finding) — not a blocker for extraction, but material to
   Phase 2's own Scope section once Open Question 1 is resolved.
