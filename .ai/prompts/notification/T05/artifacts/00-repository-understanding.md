# notification · T05 · Phase 0 — Repository Understanding

## 1. Architecture summary

`notification-service` is a consume-only Kafka fan-out layer (L2). As of T04, the module has:
schema (T02), validated config + resource-server wiring (T03), and the idempotency ledger (T04) —
`ProcessedEvent`/`ProcessedEventRepository`/`IdempotencyGuard` under `consumer/`, plus `ClockConfig`.
No package under `com.themistra.notification` other than `common`/`common.config`/`consumer` exists
yet — no `preference`, `template`, `delivery`, `channel`, or `inapp` package, and **no
`@KafkaListener` anywhere in this module** — `spring-kafka` has been on the classpath since T01, but
nothing has used it yet.

## 2. Existing code this task touches

**Already exists (read-only, not to be duplicated or reworked):**
- `services/notification/src/main/resources/db/migration/V1__notifications_baseline.sql` (T02) —
  `notifications.contact_projection` table, already migrated: `account_uuid UUID PRIMARY KEY`,
  `email CITEXT`, `display_name VARCHAR(200)`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
- `notification_app`'s DB grants (T02+T04): `delivery_log` (INSERT/SELECT) and `processed_events`
  (INSERT/SELECT) only — `contact_projection` is currently fully ungranted (confirmed:
  `NotificationBaselineMigrationIntegrationTest`'s own `UNGRANTED_TABLES` still lists it). A new
  grant migration is a real, load-bearing dependency this task will need, mirroring T02/T04's own
  incremental-grant pattern.
- `consumer/{ProcessedEvent,ProcessedEventRepository,IdempotencyGuard}.java`, `common/ClockConfig.java`
  (T04) — structural precedent for entity/repository/service shape (protected no-arg constructor,
  explicit `@Table(schema = ...)`, package-private repository, injectable `Clock`).

**Not yet built, referenced by this task's own text but likely out of scope:**
- Any `@KafkaListener`. `design.md` §6's own package map places `AuthEventConsumer.java` (the actual
  Kafka listener for `auth.email.requested`/`auth.user.lifecycle`) under `consumer/`, as **task 6's**
  own deliverable ("Auth event consumer... Idempotent per Task 4"), separately from
  `ContactProjection`/`ContactProjectionRepository`, which that same package map places under
  `preference/` (O1). This strongly mirrors T04's own relationship to T06: T04 built the dedupe
  *mechanism* (`IdempotencyGuard`) without wiring any real consumer; by the same pattern, T05 likely
  builds the projection *mechanism* (an entity + repository + an update helper, callable with plain
  Java arguments) without wiring a real `@KafkaListener` either — T06's own `AuthEventConsumer` would
  then be the one class that actually deserializes Kafka messages and calls into both `T04`'s
  `IdempotencyGuard` and `T05`'s own projection-update logic together. **This is Phase 1/2's own call
  to confirm, not decided here** — the task statement's own literal wording ("Consume
  `auth.user.lifecycle`...") is ambiguous about whether "consume" means "own the Kafka listener" or
  "own the logic a later listener calls."

## 3. Established patterns to follow

**JPA entity/repository/service shape** — `T04`'s own `ProcessedEvent`/`ProcessedEventRepository`/
`IdempotencyGuard` triplet is the direct, working precedent in this exact module (not just a sibling
service): explicit `@Table(name = ..., schema = "notifications")`, protected no-arg constructor,
package-private repository interface, a small `@Service` for the actual write logic. Unlike
`ProcessedEvent`'s client-assigned natural key, `contact_projection.account_uuid` is also a natural
key (not a generated surrogate) — `ContactProjection`'s own `@Id` should follow the same
no-`@GeneratedValue` shape. Unlike `ProcessedEvent` (insert-once, never updated — `updated_at` history
isn't tracked), `contact_projection` is explicitly an upsert target (`display_name`/`email`
presumably change over an account's lifetime, and `updated_at` exists specifically to track that) —
this is a genuinely different write shape from T04's own `INSERT ... ON CONFLICT DO NOTHING`
(insert-or-skip); T05 more likely needs `INSERT ... ON CONFLICT (account_uuid) DO UPDATE SET ...` or
an equivalent upsert, not explored further here since Phase 0 doesn't design.

**Least-privilege DB grants** — same incremental-grant pattern (T02/T04's own precedent): a new
migration (`V5`, following T04's own `V4`) granting `notification_app` whatever privileges
`contact_projection` writes need.

**Injectable `Clock`** — `ClockConfig` (T04) already exists in `common/`; if `updated_at` needs
explicit setting (rather than relying on the column's own `DEFAULT now()`), this task reuses the
existing bean rather than adding a second one.

## 4. Testing conventions

Unit tests: plain JUnit, fixed `Clock` where relevant (`agents.md`). Integration: Testcontainers
Postgres, mirroring T04's own `IdempotencyGuardIntegrationTest` (the first test class in this module
needing a real Spring context) if this task's own update logic is similarly `@Transactional`-shaped.
No ArchUnit yet (task 16's own scope).

## 5. Known gaps / unknowns

**This is the single most consequential finding of this phase, not a minor gap**: **neither
`auth.user.lifecycle` nor `auth.email.requested` actually carries an email address or display name
anywhere in their real, current payload.** Verified directly against both the JSON contracts and
the real auth-service Java classes that produce them (not assumed from the task statement's own
wording):

- `contracts/events/auth/user-lifecycle.v1.schema.json` — fields: `accountUuid`, `status`,
  `occurredAt`. `additionalProperties: false`.
- `contracts/events/auth/email-requested.v1.schema.json` — fields: `accountUuid`, `purpose`,
  `token`, `occurredAt`. `additionalProperties: false`. Despite the event's own name, there is no
  `email` field.
- `services/auth/src/main/java/com/themistra/auth/account/event/UserLifecycleEventPayload.java` —
  `record UserLifecycleEventPayload(UUID accountUuid, AccountStatus status, Instant occurredAt)`.
- `services/auth/src/main/java/com/themistra/auth/account/event/EmailRequestedEventPayload.java` —
  `record EmailRequestedEventPayload(UUID accountUuid, String purpose, String token, Instant occurredAt)`.
- `EventTopics.java` confirms these are the *only* two account-related topics auth-service
  publishes (plus `auth.security.audit`, unrelated and also emailless).

This is not a contract-drift bug (the JSON schemas and the real Java payload classes agree with each
other) — it is a genuine, real gap between what `design.md` §4b-O1's own recommended design assumes
("reading the email from `auth.email.requested` payloads") and what auth-service actually publishes
today. **I do not know** how this task is meant to obtain a real email address or display name given
this — the spec's own `package.md` §11 Q1 already flags recipient resolution as an open
"Blocker for email delivery," and this phase's own direct verification confirms *why*: option (a)
in Q1's own text ("events... already include the email") is not actually true of the current
contracts, narrowing the real choices to (b) an Auth internal endpoint call (which L2 explicitly
forbids on the delivery path, though this projection-population path is arguably distinct from "the
delivery path") or a contract change to one or both auth events (cross-service, outside this
service's own repo, requiring coordination this pipeline has no authority to unilaterally decide).
This must be escalated explicitly at Phase 1/2, not silently worked around (e.g., by inventing a
placeholder email value) or silently narrowed (e.g., by only projecting `account_uuid`/`updated_at`
and leaving `email`/`display_name` permanently null, which would make the projection useless for its
own stated purpose).

**Secondary, smaller unknown**: whether `T05` owns a real `@KafkaListener` or only the projection
*logic* (see §2 above) — Phase 1/2's own call, not a blocker to extraction itself since either
interpretation can be extracted from the spec, but material to Phase 2's own Scope section.
