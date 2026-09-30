# notification · T09 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T09: template renderer (Template, TemplateRenderer)`

## Commit message

```
notification-service T09: template renderer

Add Template (maps onto T02's already-migrated, already-seeded
templates table) and TemplateRepository, with one derived query,
findTopByNameAndChannelOrderByVersionDesc - always the highest-version
row for a (name, channel) pair, since templates are seeded/versioned,
not runtime-edited (L9). Add TemplateRenderer: render(name, channel,
eventData) substitutes every {{key}} placeholder (regex
\{\{([a-zA-Z][a-zA-Z0-9_]*)}}) from a merged values map, treating a
missing key and an explicit null value identically (empty string),
using Matcher.quoteReplacement so values containing $ or \ never break
substitution. Throws IllegalArgumentException for an unknown
(name, channel) pair.

Wires LinkProperties.baseUrl() (T03) for the first time: the 5 known
link placeholders (verificationLink, resetLink, getStartedLink,
invoiceLink, receiptLink) are computed by TemplateRenderer itself -
never read from the caller's own eventData, even if a caller supplied
a value under one of those keys - with a disclosed-as-provisional path
convention (/verify-email, /reset-password, /invoices/{uuid},
/receipts/{uuid}), a trailing slash stripped from baseUrl before every
join, and every raw token/UUID URL-encoded. invoiceLink/receiptLink
read from invoiceUuid/receiptUuid, matching payment-service's own real,
already-designed receipt.issued event schema field names - distinct
from {{invoiceId}}, a separate, already-seeded ordinary display
placeholder (a naming inconsistency predating this task, disclosed not
fixed - V3 is a prior task's own committed file).

RenderedMessage's own toString() is overridden to log only version and
field lengths, never subject/body content - both can carry the raw
verification/reset token embedded in a computed link (L4).

Add V7__notification_app_templates_grant.sql - SELECT only, the second
grant in this module that isn't INSERT-capable (after T08's own V6).

Updates NotificationBaselineMigrationIntegrationTest per the frozen
brief's own explicit authorization: templates moves from
UNGRANTED_TABLES to a dedicated grant-proof test reading the real
seeded email.verify row directly (no admin-inserted fixture needed,
unlike channel_preferences); Flyway-history expectation widens to
"1".."7". Updates T01SkeletonRegressionTest per an undisclosed-in-brief
but required deviation (same recurring gap T04/T05/T06/T08 each hit):
3 new production files, 23-file authorized list becomes 26. Adds a
TemplateRenderer bean-resolution proof to IdempotencyGuardIntegrationTest.

174 tests total (148 T01-T08 unaffected + 26 new: 18 unit, 6 real-
Postgres integration, plus 2 folded in from Phase 11's own addendum
already counted above). Two Kimi review rounds (Phase 8: 8 findings;
Phase 11: 8 gaps) both fully resolved - all 18 verified against real
source before disposition, none false; one finding (RenderedMessage's
token-leaking toString()) fixed immediately at Phase 9, a genuine L4
gap, not deferred. One real mutation test performed and reverted clean:
swapped the computed-link merge order, confirmed exactly 1 test fails.
One real methodology bug caught and fixed during Phase 7's own self-
review (a test-order dependency in the scratch test itself, not a
production defect - confirmed the versioned-lookup logic actually
works as designed).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/template/Template.java`
- `services/notification/src/main/java/com/themistra/notification/template/TemplateRepository.java`
- `services/notification/src/main/java/com/themistra/notification/template/TemplateRenderer.java`
- `services/notification/src/main/resources/db/migration/V7__notification_app_templates_grant.sql`
- `services/notification/src/test/java/com/themistra/notification/template/TemplateRendererTest.java`
- `services/notification/src/test/java/com/themistra/notification/template/TemplateRendererIntegrationTest.java`

**Modified**
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (26-file authorized list, renamed method)
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  (`templates` grant-proof coverage; Flyway-history widened to `"1".."7"`)
- `services/notification/src/test/java/com/themistra/notification/consumer/IdempotencyGuardIntegrationTest.java`
  (`TemplateRenderer` bean-resolution proof)

**Process artifacts**
- `.ai/prompts/notification/T09/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

Delivers the second piece of the actual delivery-decision pipeline: turning a template name +
channel + raw event data into a real rendered subject/body, with the first real consumer of the
link-base-URL config wired in. `DeliveryOrchestrator` (task 11) will be this component's own first
real caller — none exists yet, by this task's own explicit scope boundary, same as T08's own
`PreferenceResolver`.

## Testing performed

- `mvn -pl services/notification clean verify` — 174 tests, 0 failures, `BUILD SUCCESS`.
- Real end-to-end verification against a live Testcontainers Postgres with the real seeded `V3`
  rows and a real non-blank, trailing-slash `baseUrl`, first as an uncommitted Phase 7 scratch
  check, then made permanent in Phase 10/11: `TemplateRendererIntegrationTest` (6 tests) proves
  real-seed rendering, URL encoding, `IN_APP`'s null subject, versioned lookup,
  `getStartedLink`/`user.welcome`, and the `Template` entity's own column mapping.
- One real mutation test, reverted clean (`git status -s` empty afterward): swapped the
  computed-link merge order (`eventData` overlaid on top of computed links instead of the reverse),
  confirmed exactly the 1 test that should catch it failed — no more, no fewer — then reverted and
  re-verified.
- One real methodology bug caught and fixed during Phase 7's own self-review, not a production
  defect: the scratch test's own first draft mutated a shared seeded row across test methods,
  causing an order-dependent failure — fixed by using a dedicated, non-seeded template name for the
  versioned-lookup scenario. If anything, this confirmed `TemplateRenderer`'s own highest-version-
  wins logic works exactly as designed.
- `git status -s services/auth services/crypto services/payment` — empty throughout this task's
  own commits; no sibling service touched.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 9 ("Template renderer").
- **Requirements:** R14.
- **LOCKED decision:** L9 (templates are versioned, seeded/versioned not runtime-edited).
- **Open question resolved:** `package.md` §11 Q4 (email link base URL) — the config-value half was
  already answered by T03; this task pins the concrete path convention that consumes it, disclosed
  as a first-cut, provisional guess (no other artifact in this repo pins a real frontend route).

## Known, deliberate gaps (not this task's scope)

- **No real caller exists yet** — `DeliveryOrchestrator` (task 11) is the only intended caller;
  same "seam built ahead of its caller" shape as T06's `NotificationDispatcher` and T08's
  `PreferenceResolver`.
- **The link-building path convention is disclosed as provisional** — not validated against any
  real frontend route; a future task correcting the exact shape is expected and acceptable.
- **`{{invoiceId}}` (T02's own seeded display placeholder) and `invoiceUuid` (this task's own
  link-computation source key) are two different names for what may be the same real identifier**
  — clarified via Javadoc, not fixed; `V3` is a prior task's own committed file, out of this task's
  own scope.
- **8 of the 14 seeded template rows have no real event-data producer anywhere in this codebase** —
  `PaymentEventConsumer` (T07) is skipped pending `payment-service`.
- `{{displayName}}` remains permanently unpopulated (T05's own already-disclosed risk, unchanged
  here) — renders gracefully as a blank greeting via this task's own empty-string fallback (AC3).
- `TemplateRepository` still technically exposes inherited `JpaRepository` mutators that nothing in
  this codebase calls — documented with a prominent Javadoc warning, not structurally prevented
  (same precedent as T05/T08).

## Reviewer notes

- Kimi's Phase 3 (design), Phase 8 (implementation), and Phase 11 (test) reviews raised 10, 8, and
  8 findings/gaps respectively — all 26 verified against actual source before disposition; all were
  real (no false findings this task). Finding #2 of Phase 3 required cross-checking
  `spec/payment-service/design.md`'s own real, already-pinned event schema before dispositioning —
  worth a reviewer's attention as an example of verifying against the *authoritative future*
  source, not just the currently-seeded template text, before accepting or rejecting a finding.
- `TemplateRepository`'s `extends JpaRepository` write-surface concern was raised independently by
  Kimi at both Phase 3 (Finding #6) and Phase 8 (Finding #8) — same disposition both times
  (documented via Javadoc, not narrowed), consistent with T05/T08's own identical precedent.
- `RenderedMessage`'s token-leaking `toString()` (Phase 8 Finding #2) is worth a reviewer's specific
  attention: a real L4 gap in this task's own new code, fixed immediately rather than deferred,
  mirroring `EmailRequestedEvent`'s own T06 precedent for the identical class of concern.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T09.**
