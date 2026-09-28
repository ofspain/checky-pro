# notification · T09 · Phase 2 — Task Implementation Brief

## Task

Add `Template` + `TemplateRepository` + `TemplateRenderer` — a small, call-directly (not
event-driven) module resolving a template name/channel to its currently-versioned rendered
subject/body, substituting `{{key}}` placeholders from caller-supplied event data, with the
renderer itself computing the 5 known link placeholders from `LinkProperties.baseUrl()`.

## Purpose

Builds the second piece of the actual delivery-decision pipeline — "render what the recipient
sees" — as an isolated, directly-callable unit `DeliveryOrchestrator` (task 11) will later call as
the second of its own four steps ("resolve prefs → render → dispatch → log").

## Scope

**In:**
- `template/Template.java` — `@Entity` mapping onto `templates`'s 6 columns (`id`, `name`,
  `channel`, `version`, `subject` nullable, `body`, `createdAt`). Read-only in practice — this task
  never writes a row (`templates` is "seeded/versioned, not runtime-edited," L9).
- `template/TemplateRepository.java` — one read query:
  `findTopByNameAndChannelOrderByVersionDesc(String name, String channel)` returning
  `Optional<Template>` — the highest-`version` row for the pair (AC2). `extends JpaRepository` per
  this module's own established precedent (T04/T05/T08).
- `template/TemplateRenderer.java` — the public entry point:
  `RenderedMessage render(String name, String channel, Map<String, String> eventData)`, where
  `RenderedMessage` is a new small record `(String subject, String body, int version)`. Throws
  `IllegalArgumentException` if no template exists for `(name, channel)` — a caller bug (an unknown
  template name/channel pair), not a case to silently tolerate, consistent with T08's own
  null-argument caller-contract precedent.
  - Every `{{key}}` in the fetched template's `subject`/`body` is replaced from a merged values map:
    `eventData` first, then the 5 computed link placeholders (below) overlaid on top, taking
    precedence over any caller-supplied value under the same key. A placeholder with no value in
    the merged map substitutes to an empty string (AC3) — never a raw `{{key}}`, never an exception.
  - **Link placeholder computation (AC6, Q4):** `TemplateRenderer` is injected with `LinkProperties`
    and computes, whenever the corresponding raw source is present in `eventData`:
    - `verificationLink` = `baseUrl + "/verify-email?token=" + eventData.get("token")` (only if
      `eventData` has `"token"`).
    - `resetLink` = `baseUrl + "/reset-password?token=" + eventData.get("token")` (same source key
      as `verificationLink` — both are always computed whenever `token` is present; harmless, since
      `substitute` only replaces `{{key}}` patterns actually present in a given template's own
      text, and no single seeded template body contains both).
    - `getStartedLink` = `baseUrl` itself, always computed (no raw source needed — a generic
      dashboard link).
    - `invoiceLink` = `baseUrl + "/invoices/" + eventData.get("invoiceUuid")` (only if present).
    - `receiptLink` = `baseUrl + "/receipts/" + eventData.get("receiptUuid")` (only if present).
    This is a first-cut, disclosed-as-provisional path/query convention (no other artifact in this
    repo pins one) — mirrors `V3`'s own precedent of disclosing the `{{variable}}` placeholder
    syntax itself as "a reasonable, engine-agnostic default... may require revisiting."
- `db/migration/V7__notification_app_templates_grant.sql` — `SELECT` only.

**Out:**
- Any write path for `templates` — no `TemplateController`/API exists anywhere in this spec.
- `DeliveryOrchestrator` (task 11) — the only intended caller; not wired up here.
- A real templating library (Mustache/FreeMarker/etc.) — plain `{{key}}` regex substitution only
  (O6, resolved Phase 0/1: low-risk, no new dependency justified for single-level, no-loop/
  no-conditional placeholders against 14 static seed rows).

## Business Rules

R14 (stated in full in Phase 1's own extraction) — this task proves the *render* decision only;
recording the rendered output/version into `delivery_log` is `DeliveryOrchestrator`'s job (task 11).

## Locked Decisions

- **L9.** Templates are versioned — `TemplateRenderer` always resolves the highest `version` for a
  `(name, channel)` pair and surfaces it in `RenderedMessage`, never writes to `templates`.

## Dependencies

`spring-boot-starter-data-jpa` (present), `LinkProperties` (T03, first real consumer here). No
`Clock`, no Kafka.

## Inputs

`templates` table (`V1__notifications_baseline.sql` + `V3__seed_launch_templates.sql`, T02, already
migrated and seeded, never granted); `LinkProperties.baseUrl()` (T03).

## Outputs

`Template.java`, `TemplateRepository.java`, `TemplateRenderer.java`, `RenderedMessage` (as a nested
record inside `TemplateRenderer.java` — no separate file, mirroring how small value-shape records
elsewhere in this module aren't split out), `V7`.

## State Changes

None — this task is pure read-and-render logic; it introduces no new writer of any kind.

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/template/Template.java`
- `.../template/TemplateRepository.java`
- `.../template/TemplateRenderer.java`
- `services/notification/src/main/resources/db/migration/V7__notification_app_templates_grant.sql`

## Files to Modify

None expected. (Historical note: T04/T05/T06/T08 each discovered a required, undisclosed-in-brief
`T01SkeletonRegressionTest.java` update once their own new production files existed — same class of
gap is expected to recur here and will be disclosed at Phase 6/9 exactly as those four tasks did.)

## Files NOT to Modify

- `services/notification`'s own T01-T08 files (`pom.xml`, prior migrations, `AuthEventConsumer*`,
  `ChannelPreference*`/`PreferenceResolver`, `ClockConfig`, config records, `ResourceServerConfig`,
  `PublicEndpoints`).
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment` — no cross-service dependency exists for
  this task.

## Acceptance Criteria

1. **AC1.** `Template` maps exactly onto the 6 existing `templates` columns.
2. **AC2.** `TemplateRenderer.render` fetches the highest-`version` row for `(name, channel)` when
   multiple versions exist.
3. **AC3.** Every `{{key}}` placeholder substitutes from the merged values map; a placeholder with
   no value renders as an empty string.
4. **AC4.** `RenderedMessage.version()` equals the fetched template's own `version`.
5. **AC5.** `V7` grants `notification_app` `SELECT` only on `templates`; `INSERT`/`UPDATE`/`DELETE`
   all still denied.
6. **AC6.** `verificationLink`/`resetLink`/`getStartedLink`/`invoiceLink`/`receiptLink` are computed
   by `TemplateRenderer` itself from `LinkProperties.baseUrl()` plus the pinned path convention
   above — not read directly from the caller's own `eventData`, even if a caller happened to supply
   a value under one of those 5 keys.
7. **AC7.** `render` throws `IllegalArgumentException` for an unknown `(name, channel)` pair.

## Required Tests

Named (`package.md` §8): `shouldRenderTemplateWithEventDataAndSelectedChannel` (R14). Plus, implied
by AC2/AC3/AC4/AC6/AC7: a versioned-lookup test (two seeded versions, highest wins), a
missing-placeholder-renders-empty test, a version-surfaced test, one test per computed link
placeholder (5), an unknown-template-throws test.

## Constraints

- **No new dependency** — plain regex substitution, not a templating library.
- **Link convention is provisional** — disclosed as a first-cut, not validated against any real
  frontend route; a future task correcting the actual path shape is expected and acceptable.
- **Null handling:** `render`'s three arguments follow the same non-null caller-contract precedent
  as `PreferenceResolver.resolve` (T08) — not decided further here, Phase 5's own job to confirm
  the exact mechanism (`Objects.requireNonNull` vs. relying on a `NullPointerException` from first
  use).

## Open Questions

No blockers. All 5 of Phase 0/1's own carried-forward questions are resolved as concrete design
decisions above, not re-opened here.
