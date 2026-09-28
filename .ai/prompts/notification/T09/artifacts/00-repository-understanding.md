# notification · T09 · Phase 0 — Repository Understanding

## 1. Architecture summary

`notification-service` as of T08: schema/config/security (T02/T03), idempotency ledger (T04),
recipient-contact projection (T05), a real `AuthEventConsumer` (T06) dispatching to a temporary
`NoOpNotificationDispatcher` seam, and a read-only `PreferenceResolver` (T08, no real caller yet).
T07 (payment event consumer) is deferred — `services/payment` is unbuilt. T09 adds the next piece
of the actual delivery pipeline: turning a template name + channel + raw event data into an actual
rendered subject/body, versioned for the dispute-grade delivery log (L9).

## 2. Existing code this task touches

**Already exists, directly reusable (not to be duplicated or reworked):**
- `templates` table (`V1__notifications_baseline.sql`, T02) — `id`, `name`, `channel`, `version`,
  `subject` (nullable), `body`, `created_at`; unique on `(name, channel, version)`. Seeded with 14
  rows, all `version = 1` (`V3__seed_launch_templates.sql`, T02's own real seed content) — 7 name
  mappings × 2 channels (`EMAIL`/`IN_APP`). **Never granted to `notification_app`** — same
  incremental-grant gap T04/T05/T06/T08 each hit in turn; this task is the one that first needs
  runtime read access.
- `{{variable}}` placeholder syntax — already committed in the real seed content (`V3`'s own header
  comment: "a reasonable, engine-agnostic default; O6... is task 9's own open decision"). The 14
  seeded bodies use `{{displayName}}`, `{{verificationLink}}`, `{{resetLink}}`,
  `{{getStartedLink}}`, `{{amount}}`, `{{currency}}`, `{{invoiceLink}}`, `{{invoiceId}}`,
  `{{receiptLink}}` — 9 distinct placeholder names across the 14 rows.
- `LinkProperties.baseUrl()` / `LinkPropertiesStartupValidation` (T03) — the config side of Q4 is
  already answered in practice: `themistra.notification.link.base-url=${AUTH_EMAIL_LINK_BASE_URL:}`,
  fails startup outside `local` if blank. What's still undecided is the **path/query convention**
  appended to that base URL to build `{{verificationLink}}`/`{{resetLink}}` (see Open Questions).
- `AuthEventConsumer`'s own `NotificationDispatcher.dispatch(UUID, String notificationKind, Map<String,
  String> eventData)` (T06) — today, `eventData` only ever carries `{"token": ...}` (both
  email-requested kinds) or an empty map (`user.registered`). No `displayName`, no `amount`/
  `currency`/`invoiceId` reaches this seam from anywhere in this codebase yet.

**Not yet built, referenced by this task's own text but NOT this task's own scope (later tasks):**
- `delivery/{DeliveryLog,DeliveryOrchestrator}.java` — task 11's own deliverable. `TemplateRenderer`
  is called BY `DeliveryOrchestrator`, not the other way around; this task builds the renderer in
  isolation, with no real caller yet (same "seam ahead of its caller" shape as T06's
  `NotificationDispatcher` and T08's `PreferenceResolver`).
- `channel/{NotificationChannel,EmailChannel,InAppChannel}.java` — tasks 11/12/13.
- `PaymentEventConsumer` (T07, skipped) — means `invoice.created`/`payment.seen`/
  `payment.finalized`/`receipt.issued` (8 of the 14 seeded template rows, 4 of 9 distinct names)
  have **no real producer of eventData anywhere in this codebase today**. `TemplateRenderer` can
  still be built and tested against literal, hand-constructed `Map<String, String>` inputs for
  these — it doesn't need a live consumer to exist, only a template name/channel/data map, same as
  T08's `PreferenceResolver` needing no live `DeliveryOrchestrator` to be testable.

## 3. Established patterns to follow

**JPA entity/repository/service shape** — `ChannelPreference`/`ChannelPreferenceRepository` (T08)
is the closest precedent: read-only entity, no write API anywhere in this spec (`templates` is
"seeded/versioned, not runtime-edited," L9's own text — no `TemplateController`/write path is named
anywhere in `tasks.md` through T20), `extends JpaRepository` per this module's own established
precedent despite unused inherited mutators.

**Incremental grant pattern** (T04/T05/T06/T08) — this task adds
`V7__notification_app_templates_grant.sql`, `SELECT` only (next unused Flyway version; V6 was T08's
own `channel_preferences` grant).

**Versioned lookup** — `templates` permits multiple rows per `(name, channel)` at different
`version` numbers (the `UNIQUE` constraint is on all three columns together, not just
`(name, channel)`). Today every seeded row is `version = 1`, so this ambiguity is currently
unobservable, but `TemplateRenderer`'s own query must decide: always fetch the row with the
**highest** `version` for a given `(name, channel)`, matching L9's own "each rendered message
records the template version used" framing (a later dispute reconstructs exactly what was shown —
implying the *current* version is what's rendered going forward, with history preserved for
already-sent messages via the delivery log's own recorded version, not by re-rendering an old one).

## 4. Testing conventions

Unit tests: plain JUnit, no Spring context needed for pure substitution logic. Integration:
Testcontainers Postgres, mirroring `PreferenceResolverIntegrationTest`'s own shape, to prove the
`V7` grant and the real repository query against the real seeded rows. Named test
(`package.md` §8): `shouldRenderTemplateWithEventDataAndSelectedChannel` (R14).

## 5. Known gaps / unknowns

**Not blockers requiring escalation** (all resolvable within this task's own design judgment,
unlike T05/T06/T07's own genuine cross-service/unbuilt-producer blockers) — carried forward to
Phase 1/2 for a concrete decision:

1. **`{{displayName}}` has no data source anywhere** (T05's own already-disclosed risk, not new
   here) — `contact_projection.display_name` is permanently `NULL` in this codebase. Phase 1/2 must
   decide `TemplateRenderer`'s own behavior for a placeholder with no supplied value: render as
   empty string, leave the literal `{{displayName}}` text in place, or throw. Recommend empty
   string — matches L6-adjacent "never fail" spirit and avoids a broken-looking raw placeholder in
   a real message.
2. **Link-building convention undefined** — `LinkProperties.baseUrl()` exists, but nothing in this
   codebase specifies what path/query is appended to build `{{verificationLink}}`/`{{resetLink}}`
   from a raw `token`. Phase 1/2 must pin a concrete convention (e.g.
   `baseUrl + "/verify-email?token=" + token`) — this is genuinely this task's own design decision
   to make (Q4's own "or per-event links already built upstream" option is foreclosed, since T06's
   own `eventData` only ever carries the bare `token`, never a pre-built link).
3. **Versioned-lookup semantics** (above) — "always highest version" recommended, not yet pinned.
4. **Template engine approach (O6)** — recommend the simplest possible: literal `{{key}}` →
   `Map.get(key)` string substitution via regex, no new dependency (a full templating library like
   Mustache/FreeMarker is unwarranted for single-level, no-loop/no-conditional placeholder
   substitution against 14 static seed rows). Low-risk per O6's own "propose... proceed if
   low-risk" framing.
5. **Missing-placeholder-value behavior for non-`displayName` keys** (e.g. an `IN_APP` template
   rendered without a `{{verificationLink}}` in the supplied data map) — same class of question as
   #1, needs one consistent answer covering every placeholder, not just `displayName` specifically.
