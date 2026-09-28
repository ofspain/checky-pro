# notification · T08 · Phase 0 — Repository Understanding

## 1. Architecture summary

`notification-service` as of T06: schema (T02), config + resource-server wiring (T03), idempotency
ledger (T04), recipient-contact projection (T05), and a real `AuthEventConsumer` (T06) that
consumes `auth.email.requested`/`auth.user.lifecycle`, dedupes, refreshes `contact_projection`, and
hands off to a temporary `NoOpNotificationDispatcher` seam. T07 (payment event consumer) is
deferred — `services/payment` is unbuilt. T08 adds the first piece of the actual delivery-decision
logic: **which channels should a given notification actually go out on**, independent of and
upstream from both consumers already built.

## 2. Existing code this task touches

**Already exists, directly reusable (not to be duplicated or reworked):**
- `channel_preferences` table (`V1__notifications_baseline.sql`, T02) — `account_uuid`, `category`
  (`SECURITY`/`PAYMENT`/`MARKETING`, CHECK-constrained), `channel` (`EMAIL`/`IN_APP`/`WEBHOOK`/
  `PUSH`, CHECK-constrained), `enabled BOOLEAN DEFAULT TRUE`, unique on
  `(account_uuid, category, channel)`. Exists in the DB since T02 but **has never been granted to
  `notification_app`** — `V2__notification_app_role_and_grants.sql`'s own comment explicitly
  defers this table's grant to "the task that first needs runtime access to it" (T04/T05/T06's own
  established incremental-grant pattern). This task is that task.
- `NotificationDispatcher`/`AuthEventConsumer` (T06) — do **not** call `PreferenceResolver` yet;
  T06's own frozen brief deliberately deferred channel/preference resolution to whichever task
  built it (this one). This task does not need to wire `PreferenceResolver` into `AuthEventConsumer`
  — that integration is `DeliveryOrchestrator`'s own job (task 11), which doesn't exist yet.

**Not yet built, referenced by this task's own text but NOT this task's own scope (later tasks):**
- `template/{Template,TemplateRepository,TemplateRenderer}.java` — task 9.
- `delivery/{DeliveryLog,DeliveryOrchestrator,DeliveryRetry}.java` — task 11. `design.md` §6
  describes `DeliveryOrchestrator` as "resolve prefs → render → dispatch → log" — `PreferenceResolver`
  is the first of those four steps, built in isolation here, called by task 11 later.
- `channel/{NotificationChannel,EmailChannel,InAppChannel}.java` — tasks 11/12/13. This task's own
  `channel` column values (`EMAIL`/`IN_APP`/`WEBHOOK`/`PUSH`) are stored as plain strings/enums;
  nothing in this task needs the `NotificationChannel` interface itself.

## 3. Established patterns to follow

**JPA entity/repository/service shape** — `ContactProjection`/`ContactProjectionRepository`/
`ContactProjectionUpdater` (T05) is the closer precedent than `ProcessedEvent` (T04): like
`ContactProjection`, `ChannelPreference` maps onto a table this task does not own the full
lifecycle of writes for (read-mostly), though unlike `ContactProjection` there is **no write path
anywhere in this spec** — no `PreferenceController`/API task exists in `tasks.md` for any task
through T20. `ChannelPreferenceRepository` is very likely read-only (`SELECT` only) — the grant
migration should reflect that (contrast T05's `INSERT, SELECT, UPDATE` for a table this service
does write).

**Incremental grant pattern** (T04/T05/T06) — this task adds
`V6__notification_app_channel_preferences_grant.sql`, the next unused Flyway version number (V5
was T05's `contact_projection` grant).

**Default-when-absent + hard-floor business rule** — `design.md` §4c's own VERBATIM default table
(lines ~44–47) must be copied exactly, not re-derived:
```
SECURITY  (verify, reset, suspended, security_alert): email=ON,  in_app=ON  (email cannot be disabled)
PAYMENT   (invoice, payment.*, receipt):               email=ON,  in_app=ON
MARKETING (future):                                     email=OFF, in_app=OFF
```
The parenthetical "(email cannot be disabled)" on `SECURITY` is not merely a *default* — it reads
as a hard floor: L6 says "never fail," and the task text says "Enforce that SECURITY-category email
cannot be disabled," which is a stronger claim than "default to enabled." Given there is no write
API anywhere in this spec, a stored `channel_preferences` row with `category=SECURITY,
channel=EMAIL, enabled=false` can currently only arise from a direct DB write (an admin/support
action, or a future task this spec doesn't yet name) — but the resolver's own contract should not
assume that can never happen.

## 4. Testing conventions

Unit tests: plain JUnit, no Spring context needed for pure preference-resolution logic (a lookup +
fallback function). Integration: Testcontainers Postgres, mirroring `ContactProjectionUpdaterIntegrationTest`'s
own shape, to prove the grant (`SELECT`, presumably no `INSERT`/`UPDATE`/`DELETE`) and the real
repository query. Named tests (`package.md` §8): `shouldResolveChannelPreferencesPerRecipient` (R9),
`shouldSuppressChannelWhenRecipientOptedOut` (R10), `shouldFallBackToDefaultPreferenceWhenNoneSet`
(R18).

## 5. Known gaps / unknowns

**Primary open question — same shape as T05/T06's own Phase 0 findings, smaller in scope:** the
frozen default table's "(email cannot be disabled)" parenthetical needs a concrete resolution
decision at Phase 1/2, not assumed here: does `PreferenceResolver.resolve(accountUuid, category,
channel)` (a) simply never consult the stored row for `SECURITY`+`EMAIL` and always return `true`
unconditionally, or (b) consult the stored row as normal but the *only* way such a row could ever
exist with `enabled=false` is an out-of-band DB write this spec never names a legitimate path for,
making (a) and (b) behaviorally identical in practice but differently defensive in code? Recommend
(a) at Phase 1/2 — matches "never fail" (L6) and needs no assumption about what wrote the row.

**Secondary**: `PreferenceResolver`'s own exact method signature isn't specified anywhere
(`design.md` §6 only names the class, not its API) — Phase 1/2's own job to design, mirroring
`ContactProjectionUpdater.upsertEmail`'s own precedent of a small, single-purpose public method.
