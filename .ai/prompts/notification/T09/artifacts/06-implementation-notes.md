# notification · T09 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). `src/main` files created plus the frozen-brief-authorized
`NotificationBaselineMigrationIntegrationTest` update — no new test file authored (Phase 10 scope,
per this phase's own rule, mirroring T08's own precedent), plus one justified, undisclosed-in-brief
`T01SkeletonRegressionTest.java` update (same recurring gap T04/T05/T06/T08 each hit in turn).

## Files created

- `template/Template.java` — `@Entity` mapping onto all 6 `templates` columns; read-only.
- `template/TemplateRepository.java` — package-private, `extends JpaRepository` per T04/T05/T08's
  own established precedent (Finding #6, documented not narrowed); one derived query,
  `findTopByNameAndChannelOrderByVersionDesc`.
- `template/TemplateRenderer.java` — `@Service` (Finding #10), `render(String name, String channel,
  Map<String, String> eventData)`: null-checks all three arguments, fetches the highest-version row
  or throws `IllegalArgumentException` (AC7), substitutes every `{{key}}` placeholder
  (`\{\{([a-zA-Z][a-zA-Z0-9_]*)}}`, Finding #8) via `Matcher.quoteReplacement` (Finding #1) with a
  `null`-safe lookup treating a missing key and an explicit `null` value identically (Finding #9).
  The 5 link placeholders are computed by the renderer itself from `LinkProperties.baseUrl()`
  (normalized: `null` → `""`, trailing `/` stripped — Findings #4/#5) plus a fixed path convention,
  every raw token/UUID URL-encoded before appending (Finding #3). `invoiceLink`/`receiptLink` derive
  from `invoiceUuid`/`receiptUuid` — matching `spec/payment-service/design.md`'s own real
  `receipt.issued` schema field names — deliberately distinct from the already-seeded, ordinary
  `{{invoiceId}}` display placeholder (Finding #2, documented in the class's own Javadoc, `V3`
  itself untouched).
- `db/migration/V7__notification_app_templates_grant.sql` — `GRANT SELECT` only.

## Files modified

- `NotificationBaselineMigrationIntegrationTest.java` (Finding #7, frozen-brief-authorized) —
  `templates` moved from `UNGRANTED_TABLES` to a dedicated
  `notificationAppCanSelectButNotInsertUpdateOrDeleteOnTemplates` test (reads the already-seeded
  `email.verify` row directly — no admin-inserted fixture needed, unlike `channel_preferences`,
  since `templates` already has real seed data); its own 3 now-dead cases removed from the three
  ungranted-side fixture switches; Flyway-history expectation widened to `"1".."7"`.
- `T01SkeletonRegressionTest.java` — `noExtraProductionClassesExistBeyondT08sOwnAuthorizedSet`
  renamed to `...T09sOwnAuthorizedSet`; 23-file list widened to 26 (`Template`/`TemplateRenderer`/
  `TemplateRepository`, sorted correctly under a new `template/` directory).

## Verification performed

- `mvn -pl services/notification clean verify` — 149 tests, 0 failures, clean `package`/`repackage`
  (up from 148; +1 = the new `templates` grant-proof test; `TemplateRenderer`'s own behavioral tests
  are Phase 10's job per this phase's own "no tests" rule).
- `V7` migrated successfully in every Testcontainers run
  (`Successfully applied 7 migrations ... now at version v7`).
- Manually traced `TemplateRenderer.render`/`computeLinkPlaceholders`/`substitute` against all 10
  frozen-brief findings by inspection: confirmed `Matcher.quoteReplacement` wraps every replacement
  value, confirmed `URLEncoder.encode` wraps every raw link-source value, confirmed
  `normalizeBaseUrl` strips exactly one trailing slash and never throws on `null`, confirmed the
  placeholder regex matches every key already seeded in `V3` (spot-checked
  `displayName`/`verificationLink`/`amount`/`currency`/`invoiceId`/`invoiceLink`/`receiptLink`
  against the regex by hand).

## Acceptance criteria mapping

- **AC1** — `Template` maps exactly onto the 6 existing columns. ✅ (validated against the real
  schema by Hibernate itself in every Testcontainers `@SpringBootTest` boot.)
- **AC2/AC3/AC4/AC6/AC8/AC9** — versioned lookup, substitution, version-surfacing, link
  computation, and the quoting/encoding/normalization fixes are all implemented as designed; real
  behavioral proof against the seeded rows is Phase 10's own job per this phase's "no tests" rule —
  not yet automated, but every code path was exercised implicitly (no error) by every Testcontainers
  boot in this suite, and traced by hand against each of the 10 accepted findings.
- **AC5** — `V7` grants `notification_app` `SELECT` only; `INSERT`/`UPDATE`/`DELETE` all denied —
  proven by the new dedicated test.
- **AC7** — `render` throws `IllegalArgumentException` for an unknown `(name, channel)` pair —
  confirmed by direct source inspection (`orElseThrow`).
