# notification · T09 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **AC1** — `Template` maps exactly onto the 6 existing `templates` columns | Yes | `Template.java` — `id`, `name`, `channel`, `version`, `subject`, `body`, `createdAt` | `templateEntityMapsAllSixColumnsCorrectly` (direct, via repository) | No | No |
| **AC2** — highest-`version` row wins for a `(name, channel)` pair | Yes | `TemplateRepository.findTopByNameAndChannelOrderByVersionDesc` | `versionedLookupUsesTheHighestVersionWhenTwoExistForTheSamePair` (real Postgres, 2 real rows) | No | No |
| **AC3** — every `{{key}}` substitutes; missing/`null`/empty value → empty string | Yes | `TemplateRenderer.substitute` | `missingPlaceholderRendersAsEmptyString`, `nullValueInEventDataRendersAsEmptyString`, `emptyStringEventDataValueRendersAsEmptyString` | No | No |
| **AC4** — `RenderedMessage.version()` equals the fetched template's own version | Yes | `render`'s own `return new RenderedMessage(..., template.getVersion())` | `renderedMessageSurfacesTheFetchedTemplatesOwnVersion` | No | No |
| **AC5** — `V7` grants `SELECT` only; `INSERT`/`UPDATE`/`DELETE` denied | Yes | `V7__notification_app_templates_grant.sql` | `notificationAppCanSelectButNotInsertUpdateOrDeleteOnTemplates` | No | No |
| **AC6** — the 5 computed link placeholders always win over a caller-supplied value under the same key | Yes | `render`'s own merge order: `eventData` copied first, computed links overlaid on top | `computedLinkPlaceholdersOverrideCallerSuppliedValues`, `renderMergesEventDataBeforeOverlayingComputedLinkPlaceholders` (static-scan, mutation-tested) | No | No |
| **AC7** — unknown `(name, channel)` throws `IllegalArgumentException` | Yes | `render`'s own `.orElseThrow(...)` | `unknownTemplateThrowsIllegalArgumentException` | No | No |
| **AC8** — `$`/`\`-safe substitution; reserved URL characters correctly encoded | Yes | `Matcher.quoteReplacement`, `URLEncoder.encode` | `valuesContainingDollarAndBackslashDoNotBreakSubstitution`, `tokenWithReservedUrlCharactersIsUrlEncodedInTheComputedLink` (real Postgres) | No | No |
| **AC9** — trailing-slash `baseUrl` produces a single `/` at the join point | Yes | `normalizeBaseUrl` | `rendersTheRealSeededEmailVerifyTemplateWithATrailingSlashBaseUrlNormalized` (real Postgres), `baseUrlWithAPathPrefixIsPreservedInTheComputedLink` | No | No |
| L9 (templates versioned, seeded/versioned not runtime-edited) | Yes | No write path exists anywhere in `TemplateRenderer`/`TemplateRepository`; `V7` grants `SELECT` only | Implicit — no writer exists to test the absence of | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes, against T09's own literal scope (`tasks.md` task 9:
`Template` + `TemplateRenderer`, versioned per event+channel, link base URL wired per Q4). All 3
production files delivered (`Template`, `TemplateRepository`, `TemplateRenderer`) plus `V7`, plus
across Phases 10-11, 2 test files (174 tests total, up from T08's own 148 — 26 new). The
`T01SkeletonRegressionTest.java` and `NotificationBaselineMigrationIntegrationTest.java` updates
were disclosed as required, undisclosed-in-brief deviations (same recurring class of gap as
T04/T05/T06/T08), not silent scope creep.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC9 all hold, each with
direct evidence and automated coverage, including the task's own most novel/adversarial property
(computed links always winning over a caller-supplied value under the same key) independently
mutation-tested rather than merely asserted correct by inspection.

**(3) Does it violate any LOCKED decision?** No. L9 holds — `TemplateRenderer`/`TemplateRepository`
introduce no write path of any kind; `templates` remains exactly as "seeded/versioned, not
runtime-edited" as it was before this task.

**(4) Remaining risks?**
- **`TemplateRenderer` has zero callers in production code today** — `DeliveryOrchestrator`
  (task 11) is the only intended caller and doesn't exist yet. Same "seam built ahead of its
  caller" shape as T06's `NotificationDispatcher` and T08's `PreferenceResolver`.
- **The link-building path convention (`/verify-email`, `/reset-password`, `/invoices/{uuid}`,
  `/receipts/{uuid}`) is explicitly disclosed as a first-cut, provisional guess** — no other
  artifact in this repo pins a real frontend route. Not validated against any actual SPA; a future
  task correcting the exact shape is expected and acceptable (disclosed at Phase 2/4).
- **`{{invoiceId}}` (an ordinary display placeholder, already seeded in T02's own `V3`) and
  `invoiceUuid` (the raw source key this task's own `invoiceLink` computation reads) are two
  different names for what may turn out to be the same real identifier** once `payment-service`
  (T07, currently unbuilt) actually exists and its own event schema is finalized — verified against
  `spec/payment-service/design.md`'s own already-pinned `receipt.issued` schema (`invoiceUuid` is
  the real field name there), so this task's own choice is well-justified, but the seeded template
  text's own `{{invoiceId}}` naming predates that schema and was never reconciled. Disclosed at
  Phase 4 (Finding #2), not fixed — `V3` is a prior task's own committed file, out of this task's
  own scope.
- **8 of the 14 seeded template rows (`invoice.created`/`payment.seen`/`payment.finalized`/
  `receipt.issued`, both channels) have no real event-data producer anywhere in this codebase** —
  `PaymentEventConsumer` (T07) is skipped pending `payment-service`. `TemplateRenderer` itself
  doesn't care (it renders whatever `(name, channel, eventData)` it's given), but these 4 template
  names have never been exercised against real, live-produced event data, only hand-constructed
  test maps.
- `{{displayName}}` remains permanently unpopulated by any real data source (T05's own
  already-disclosed risk, unchanged here) — `TemplateRenderer`'s own empty-string fallback (AC3)
  means this renders gracefully (no broken output), but every `displayName`-bearing message will
  render with a blank greeting until some future task supplies real display-name data.

## `package.md` §9 whole-service checklist — items relevant to T09

- [x] All §3 acceptance criteria have a passing named test from §8 — the 1 literally-named test
  (`shouldRenderTemplateWithEventDataAndSelectedChannel`) passes.
- [x] Every §4a LOCKED decision implemented as written — L9 holds.
- [~] Every §4c VERBATIM artifact copied exactly — the event→template map and DDL are unchanged
  (T02's own scope); the link-building convention is this task's own new artifact, explicitly
  disclosed as provisional, not a VERBATIM spec quote (none exists to copy).
- [ ] Every other checklist item (delivery log, preference honouring, in-app auth, retry/dead-letter,
  contract validation) — **out of scope**, unrelated to this task's own files.

## Cross-task regression check

Full `services/notification` suite: 174 tests, 0 failures. `T01SkeletonRegressionTest` (9 tests,
its own 26-file authorized list, updated this task) and `NotificationBaselineMigrationIntegrationTest`
(17 tests, `templates` now in grant-proof coverage) both still pass alongside T02's, T03's, T04's,
T05's, T06's, and T08's own test classes, all unmodified by this task except the two disclosed,
justified edits — confirms T09's changes didn't regress anything the prior six implementation
tasks established.

## Spec status

`spec/notification-service/package.md`'s header is unchanged — the version/status bump is task 20,
matching the established precedent (T02-T08 all left it untouched). Not touched here.

---

**PASS** — all 9 acceptance criteria satisfied with direct evidence and automated coverage (174
tests, the computed-link-override property independently mutation-tested, the versioned-lookup and
URL-encoding/normalization properties empirically verified against a real Postgres instance before
being trusted), no LOCKED decision violated, task boundary held throughout. Five residual risks are
disclosed above, none blocking: no real caller yet (task 11's own scope), a disclosed-as-provisional
link convention, a pre-existing `V3` naming inconsistency clarified but not fixed, 8 of 14 seeded
templates untested against real producer data (T07 still skipped), and `displayName`'s own
still-unresolved missing data source (T05's own disclosed risk, gracefully degraded here).
