# notification · T09 · Phase 1 — Specification Extraction

## Business Rules

- **R14.** WHEN rendering a message, THEN the system SHALL populate the versioned template for the
  event type and selected channel from the event data.

## Locked Decisions

- **L9.** Templates are versioned — "Each rendered message records the template version used (in
  the delivery log)... Templates are seeded/versioned, not runtime-edited." This task's own
  `TemplateRenderer` must surface which version it rendered (for `DeliveryOrchestrator`, task 11, to
  record later); it must never write to `templates` itself.

## Files involved

**Already exists, read-only precedent/dependency:**
- `templates` table (`V1__notifications_baseline.sql`, T02) — migrated since T02, seeded with 14
  real rows (`V3__seed_launch_templates.sql`), never granted to `notification_app`.
- `preference/{ChannelPreference,ChannelPreferenceRepository,PreferenceResolver}.java` (T08) —
  direct structural precedent: read-only entity, `extends JpaRepository` despite unused mutators,
  a small `@Service` with no live caller yet.
- `common/config/LinkProperties.java` / `LinkPropertiesStartupValidation.java` (T03) — the base-URL
  config half of Q4 is already answered (`AUTH_EMAIL_LINK_BASE_URL`, startup-validated outside
  `local`); this task is the first to actually consume `LinkProperties.baseUrl()`.

**New, this task's own deliverable (per `design.md` §6, `template/` package):**
- `template/Template.java` — entity mapping onto `templates`'s existing 6 columns.
- `template/TemplateRepository.java` — read path: a query returning the highest-`version` row for a
  given `(name, channel)` (Phase 0's own resolved versioned-lookup question).
- `template/TemplateRenderer.java` — the public entry point: given a template name, channel, and
  raw event data, returns the rendered subject/body plus the version used.
- A grant migration, `V7__notification_app_templates_grant.sql` — `SELECT` only.

## Dependencies

`spring-boot-starter-data-jpa` (present), `LinkProperties` (T03, first real consumer). No `Clock`,
no Kafka — pure read-and-render logic, callable directly with plain arguments, not event-driven
(same shape as T08's `PreferenceResolver`).

## Acceptance Criteria

1. **AC1.** `Template` maps exactly onto the 6 existing `templates` columns.
2. **AC2.** `TemplateRenderer.render` fetches the row with the **highest** `version` for the given
   `(name, channel)` — not merely "a" row — when multiple versions exist for the same pair.
3. **AC3.** Every `{{key}}` placeholder in the fetched template's `subject`/`body` is substituted
   from the supplied event-data map; a placeholder with no corresponding entry renders as an empty
   string (Phase 0's own resolved decision) — never a raw `{{key}}` left in the output, never an
   exception.
4. **AC4.** The render result exposes the template's own `version` (for a future `DeliveryLog`
   write, task 11) alongside the rendered `subject`/`body`.
5. **AC5.** `V7` grants `notification_app` `SELECT` only on `templates`; `INSERT`/`UPDATE`/`DELETE`
   all still denied.
6. **AC6 (Q4).** The 5 known link-shaped placeholders (`verificationLink`, `resetLink`,
   `getStartedLink`, `invoiceLink`, `receiptLink`) are computed from `LinkProperties.baseUrl()` plus
   a fixed, pinned per-placeholder path convention (Phase 0's own resolved design, detailed in Phase
   2), not read directly from the caller's own event-data map like every other placeholder — the
   renderer itself is where "wiring the link base URL" (task 9's own literal text) happens, keeping
   `TemplateRenderer` the single place that knows both the base URL and every link's own shape.

## Tests required

Named (`package.md` §8): `shouldRenderTemplateWithEventDataAndSelectedChannel` (R14). Plus,
implied by AC2/AC3/AC4/AC6: a versioned-lookup test (two versions seeded for the same
`(name, channel)`, highest wins), a missing-placeholder-renders-empty test, a version-surfaced test,
and a link-placeholder-computed-from-base-URL test for at least one of the 5 known link keys.

## Open Questions

All 5 of Phase 0's own carried-forward questions are resolved here as concrete design decisions,
not blockers:

1. **`{{displayName}}` with no data source** — resolved: missing placeholders render as empty
   string (AC3), uniformly for `displayName` and everything else. No special-casing.
2. **Link-building convention** — resolved (AC6): `TemplateRenderer` itself computes the 5 known
   link placeholders from `LinkProperties.baseUrl()` + a fixed path per placeholder name, detailed
   in Phase 2's own implementation brief. This keeps link-building out of `DeliveryOrchestrator`
   (task 11, doesn't exist yet) and out of `AuthEventConsumer` (T06, already frozen, not reopened).
3. **Versioned-lookup semantics** — resolved (AC2): always the highest `version` for a given
   `(name, channel)`.
4. **Template engine approach (O6)** — resolved: literal `{{key}}` regex substitution, no new
   dependency.
5. **Missing-placeholder-value behavior for non-`displayName` keys** — resolved: folded into AC3,
   one uniform rule for every placeholder.
