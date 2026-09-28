STATUS: FROZEN

# notification · T09 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 10 findings independently verified against source before disposition.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | Regex substitution breaks on `$`/`\` in values | High | **ACCEPTED** | Every replacement value passed through `Matcher.quoteReplacement(...)` before `appendReplacement`. |
| 2 | Seeded `{{invoiceId}}` vs. link-computation source key `invoiceUuid` | Medium | **ACCEPTED (clarified, not renamed)** | Verified against `spec/payment-service/design.md`'s own `receipt.issued` schema (the real, already-pinned future producer): the authoritative field name is `invoiceUuid`, not `invoiceId` — `TemplateRenderer`'s own link-computation source key is correct as designed. `{{invoiceId}}` is a *separate*, ordinary display placeholder (shows the raw ID as text, seeded in T02's own `V3`, a prior task's committed file, out of this task's own scope to edit) — not the same thing as the computed `{{invoiceLink}}` URL. Documented explicitly in `TemplateRenderer`'s own Javadoc so a future reader doesn't assume they must share one source key. Flagged as a disclosed, out-of-scope naming inconsistency in `V3`'s own seed content (a future template *version* bump, not an edit to `V3` itself, would rename `{{invoiceId}}` to `{{invoiceUuid}}` for consistency — not this task's own job). |
| 3 | Computed link values are not URL-encoded | Medium | **ACCEPTED** | `token`/`invoiceUuid`/`receiptUuid` values are passed through `URLEncoder.encode(value, StandardCharsets.UTF_8)` before appending to any computed link. |
| 4 | Behavior when `baseUrl` is blank/null unspecified | Medium | **ACCEPTED** | Blank `baseUrl` (the `local` profile's own already-established, intentional state) produces relative-path links (`"" + "/verify-email?token=..."` = `"/verify-email?token=..."`) — no special-casing, consistent with `LinkPropertiesStartupValidation`'s own existing enforcement that `baseUrl` is non-blank *outside* `local` (this task doesn't re-validate that). Defensively, a `null` `baseUrl` (should not occur in practice — `themistra.notification.link.base-url=${AUTH_EMAIL_LINK_BASE_URL:}`'s own `:` syntax always binds at least an empty string) is treated as empty string too, not a `NullPointerException`. |
| 5 | Trailing slash in `baseUrl` produces `//` | Low | **ACCEPTED** | `TemplateRenderer` strips a single trailing `/` from `baseUrl` (if present) before every concatenation. |
| 6 | `TemplateRepository extends JpaRepository` exposes write methods | Medium | **ACCEPTED (documented, not narrowed)** | Same disposition as T08's identical Finding #4/#8 (`ChannelPreferenceRepository`) — kept `extends JpaRepository` for consistency with T04/T05/T08's own established precedent, documented via Javadoc, not narrowed. `V7`'s `SELECT`-only grant is the real defense-in-depth layer. |
| 7 | `NotificationBaselineMigrationIntegrationTest` not named in "Files to Modify" | Medium | **ACCEPTED** | Added to Files to Modify below — Flyway-history expectation widens to `"1".."7"`; a new dedicated test proves `notification_app` can `SELECT` but not `INSERT`/`UPDATE`/`DELETE` on `templates`, mirroring T08's own identical precedent for `channel_preferences`. |
| 8 | Placeholder key character set unspecified | Low | **ACCEPTED** | Pinned: `\{\{([a-zA-Z][a-zA-Z0-9_]*)\}\}` — matches every key already seeded in `V3` (all camelCase alphanumeric), permits future digits/underscores, rejects empty/malformed `{{}}`/nested placeholders (left as literal, unmatched text — not an error). |
| 9 | `null` values inside `eventData` unspecified | Low | **ACCEPTED** | A key present in `eventData` with an explicit `null` value is treated identically to a missing key — both substitute to empty string. (`Map.getOrDefault` alone is insufficient for this, since it only applies the default when the key is *absent*, not when present with a `null` value — the substitution logic explicitly null-checks the looked-up value.) |
| 10 | `TemplateRenderer`'s own Spring bean lifecycle unspecified | Low | **ACCEPTED** | `@Service`, consistent with `PreferenceResolver`/`ContactProjectionUpdater`. |

## Task

Unchanged from Phase 2, with all 10 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- All replacement values pass through `Matcher.quoteReplacement` (Finding #1) and, for the 5
  computed link values specifically, `URLEncoder.encode` on the raw token/UUID before appending
  (Finding #3).
- `baseUrl`'s own trailing slash is stripped before every concatenation (Finding #5); blank/`null`
  `baseUrl` produces a relative link, never an exception (Finding #4).
- `TemplateRepository`'s own Javadoc documents the inherited-mutators-unused warning (Finding #6).
- Placeholder key regex pinned exactly: `\{\{([a-zA-Z][a-zA-Z0-9_]*)\}\}` (Finding #8).
- `eventData` values of `null` treated identically to a missing key (Finding #9).
- `TemplateRenderer` is `@Service`-annotated (Finding #10).
- `TemplateRenderer`'s own Javadoc documents the `invoiceId`/`invoiceUuid` distinction (Finding #2).

**Out:** Unchanged from Phase 2. Explicitly still out: editing `V3__seed_launch_templates.sql`'s
own already-committed placeholder names (Finding #2's own disclosed, out-of-scope naming note).

## Business Rules

Unchanged from Phase 2.

## Locked Decisions

Unchanged from Phase 2: L9.

## Dependencies

Unchanged from Phase 2.

## Inputs

Unchanged from Phase 2, plus: `spec/payment-service/design.md`'s own `receipt.issued` schema (the
real, already-pinned source of the `invoiceUuid`/`receiptUuid`/`paymentUuid` field-naming
convention this task's own link computation now explicitly cites as its own justification).

## Outputs

Unchanged from Phase 2.

## Files to Create

Unchanged from Phase 2:
- `services/notification/src/main/java/com/themistra/notification/template/Template.java`
- `.../template/TemplateRepository.java`
- `.../template/TemplateRenderer.java`
- `services/notification/src/main/resources/db/migration/V7__notification_app_templates_grant.sql`

## Files to Modify

- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  (Finding #7) — Flyway-history expectation `"1".."7"`; new grant-proof test for `templates`.
- `T01SkeletonRegressionTest.java` — not pre-authorized here (same reasoning as prior tasks), but
  expected per T04/T05/T06/T08's own unbroken precedent.

## Files NOT to Modify

Unchanged from Phase 2, plus explicitly: `db/migration/V3__seed_launch_templates.sql` (Finding #2's
own disclosed, out-of-scope naming inconsistency — not fixed by this task).

## Acceptance Criteria

Unchanged from Phase 2's AC1-7, with AC3 now explicit about `Matcher.quoteReplacement`/`null`
handling, AC6 now explicit about URL-encoding and trailing-slash stripping, plus:
8. **AC8.** A replacement value containing `$`, `\`, `&`, `=`, a space, or a non-ASCII character
   renders correctly (no exception, no corruption, correctly encoded when part of a computed link).
9. **AC9.** `baseUrl` with a trailing slash produces a single `/` at the join point, never `//`.

## Required Tests

Unchanged from Phase 2, plus: a `$`/`\`-in-value test (Finding #1), a URL-encoding test for at
least one computed link with a token containing `&`/`=`/space (Finding #3), a blank-`baseUrl`
relative-link test (Finding #4), a trailing-slash test (Finding #5), a `null`-value-in-`eventData`
test (Finding #9), the `V7` grant-proof test (Finding #7).

## Constraints

Unchanged from Phase 2, plus: placeholder key regex pinned exactly (Finding #8); `V3`'s own
already-committed seed content is never edited by this task (Finding #2).

## Open Questions

No blockers. All 10 Phase 3 findings resolved above.
