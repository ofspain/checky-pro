# notification · T11 · Phase 0 — Repository Understanding

## 1. Architecture summary

`notification-service` as of T10: schema/config/security (T02/T03), idempotency ledger (T04),
recipient-contact projection (T05), a real `AuthEventConsumer` (T06) calling into a temporary
`NoOpNotificationDispatcher` seam, a read-only `PreferenceResolver` (T08), a real `TemplateRenderer`
(T09), and a reusable `SecretSafeLogging.redact` (T10, no real caller yet). T11 is the task that
**wires all three together for the first time**: `DeliveryOrchestrator` is described as
"resolve prefs → render → dispatch → log" — the exact sequence T08/T09's own components exist to
support, none of which has ever been called by anything real until now. This is a substantially
larger, more structurally significant task than T08/T09/T10 — it is the first place multiple prior
tasks' own "seam built ahead of its caller" components finally get a real caller.

## 2. Existing code this task touches

**Already exists, directly reusable:**
- `PreferenceResolver.resolve(UUID, String category, String channel)` (T08) — no real caller yet.
- `TemplateRenderer.render(String name, String channel, Map<String,String> eventData)` (T09) — no
  real caller yet.
- `SecretSafeLogging.redact(String)` (T10) — no real caller yet.
- `delivery_log` table (`V1__notifications_baseline.sql`, T02) — **already granted**
  `INSERT, SELECT` to `notification_app` (`V2__notification_app_role_and_grants.sql`, T02's own
  literal scope) — the *only* baseline table already grant-ready before this task; no new grant
  migration is needed here, unlike every prior implementation task.
- `NotificationDispatcher`/`NoOpNotificationDispatcher` (T06) — `NoOpNotificationDispatcher`'s own
  Javadoc explicitly pre-authorizes its own replacement: "replaced (this file deleted, the real
  implementation added in its place) by whichever task first provides real dispatch (task 11:
  `DeliveryOrchestrator`...)." **This task is expected to delete `NoOpNotificationDispatcher.java`
  and make `DeliveryOrchestrator` the new, real `NotificationDispatcher` implementation** — a
  cross-task file modification, but one pre-authorized since T06 itself, not a new deviation.

**Not yet built, referenced by this task's own text but NOT this task's own scope (later tasks):**
- `channel/EmailChannel.java` (task 12), `channel/InAppChannel.java` (task 13) — the only two real
  channel implementations. **`channel/NotificationChannel.java` (the interface itself) is named
  nowhere in `tasks.md` as any specific task's own deliverable** — task 12's own text ("Implement
  `EmailChannel` behind `NotificationChannel`") assumes the interface already exists by then. Since
  no earlier task creates it either, **this task must introduce `NotificationChannel` itself**,
  mirroring exactly how T06 introduced `NotificationDispatcher` one task ahead of its own real
  implementer. This task therefore also needs its own temporary, real (not stub) implementation of
  `NotificationChannel` for both `EMAIL`/`IN_APP` — mirroring `NoOpNotificationDispatcher`'s own
  precedent — since neither real channel exists yet.
- `DeliveryRetry`/`RetryScheduler` (task 14) — bounded retry/dead-letter is explicitly out of this
  task's own scope; "dispatch on each enabled channel" here means a single, synchronous attempt per
  channel, with `FAILED` outcomes simply recorded, not retried (task 14's own job).
- `PaymentEventConsumer` (T07, skipped) — 4 of the 7 VERBATIM topic→template mappings
  (`invoice.created`/`payment.seen`/`payment.finalized`/`receipt.issued`) have no real caller.

## 3. Established patterns to follow

**Seam-ahead-of-caller** (T06/T08/T09/T10) — this task both *consumes* three such seams for the
first time and *creates* a new one (`NotificationChannel`) for tasks 12/13.

**VERBATIM lookup tables as small, hardcoded `Map`s** (`TemplateRenderer.DEFAULTS`,
Kimi-reviewed and mutation-tested) — `DeliveryOrchestrator` needs its own such table mapping
`notificationKind` → `(emailTemplateName, inAppTemplateName, category)`, copied from `design.md`
§4c's own topic→template table (quoted below) plus the default-preferences table's own category
groupings.

## 4. Testing conventions

Given this task both consumes and creates seams, both unit (mocked collaborators) and
Testcontainers-Postgres-and-real-collaborators integration tests are expected, mirroring
`AuthEventConsumerTest`/`AuthEventConsumerIntegrationTest`'s own split for a similarly
multi-collaborator class. Named tests (`package.md` §8):
`shouldRecordEveryDeliveryAttemptAndOutcomeInLog` (R11),
`shouldSuppressChannelWhenRecipientOptedOut` (R10, already partially covered by T08's own
`PreferenceResolverTest`, but this task's own version proves suppression *reaches the delivery
log*, which T08 alone cannot prove).

## 5. Known gaps / unknowns

**Not blockers requiring escalation** — all resolvable within this task's own design judgment,
carried forward to Phase 1/2 for concrete decisions:

1. **`NotificationDispatcher.dispatch`'s own signature has no field for the delivery log's own
   required `source_event_key` column** (L3/R11's own "recipient, channel, source event key,
   template version, outcome, timestamp"). Verified directly: `dispatch(UUID accountUuid, String
   notificationKind, Map<String, String> eventData)` carries no event-key parameter, and
   `AuthEventConsumer` (T06) never passes its own already-computed `eventKey` (used for
   `IdempotencyGuard`) through to `dispatch` at all. Two options: (a) change
   `NotificationDispatcher`'s own interface signature to add a fourth parameter (a breaking change
   to an already-frozen T06 interface, plus both of `AuthEventConsumer`'s own call sites); (b) have
   `AuthEventConsumer` additionally place its own `eventKey` into `eventData` under a well-known
   key (e.g. `"sourceEventKey"`), matching the established pattern of extending `eventData`'s own
   map contents rather than changing method signatures (T09's own `token`/`invoiceUuid`/
   `receiptUuid` precedent). Recommend (b) — smaller, more localized, consistent with precedent;
   requires one disclosed, cross-task edit to `AuthEventConsumer.java` (a T06 file), which — like
   deleting `NoOpNotificationDispatcher.java` — needs explicit disclosure but is a natural
   consequence of this task's own arrival, not a new violation.
2. **`user.registered`/`user.welcome`'s own category is genuinely ambiguous in `design.md`'s own
   VERBATIM default-preferences table.** That table's own parenthetical lists name
   `SECURITY (verify, reset, suspended, security_alert)` and `PAYMENT (invoice, payment.*,
   receipt)` — `user.registered`/welcome is named in neither list. Recommend `SECURITY` by
   elimination (clearly not payment-domain) and by safe-default reasoning (a welcome message should
   not default to `MARKETING`'s own `OFF`/`OFF` — a new user would then never be welcomed by
   default), not decided finally here.
3. **`auth.user.lifecycle (user.suspended) -> account.suspended`** is marked "(optional; Q7)" in
   the VERBATIM table and was never seeded in `V3` (confirmed at T02's own time) — and is also
   structurally unreachable today regardless, since `AuthEventConsumer` (T06) only ever dispatches
   for `eventType = "user.registered"`, never `user.suspended`. Recommend excluding this mapping
   entirely from `DeliveryOrchestrator`'s own lookup table (not a template `render()` could resolve
   even if asked), rather than including a mapping to a template that doesn't exist.
4. **`NotificationChannel`'s own interface shape** is not specified anywhere — this task's own job
   to design (Phase 1/2), mirroring how T06 designed `NotificationDispatcher`'s own shape from
   nothing.
5. Same "T07 skipped" pattern as T09: 4 of 7 VERBATIM mappings (`invoice.created`/`payment.seen`/
   `payment.finalized`/`receipt.issued`) will be built into the lookup table but never exercised by
   a real caller until `payment-service` exists.
