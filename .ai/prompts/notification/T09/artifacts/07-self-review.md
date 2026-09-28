# notification · T09 · Phase 7 — Self-Review

## Files reviewed

- `template/{Template,TemplateRepository,TemplateRenderer}.java`
- `db/migration/V7__notification_app_templates_grant.sql`
- `T01SkeletonRegressionTest.java`, `NotificationBaselineMigrationIntegrationTest.java` (both diffs)

## Verification performed — empirical, not just inspection

Same discipline as T08's own Phase 7: `TemplateRenderer.render`'s real logic had never actually
been executed before this review. Wrote a temporary, uncommitted scratch test
(`ScratchTemplateRendererVerificationTest`, deleted before this artifact was written) exercising 6
scenarios against a real Postgres instance with the real seeded `V3` rows and a real, non-blank,
trailing-slash `baseUrl` (`https://checky.pro/`):

1. Rendering the real seeded `email.verify`/`EMAIL` template with `displayName`/`token` — subject
   and body both substitute correctly, `verificationLink` computes to
   `https://checky.pro/verify-email?token=abc123` (proving the trailing-slash normalization
   actually collapses to a single `/`, not merely that the code compiles). **Passed.**
2. A missing `eventData` key (`displayName`, never supplied) and an explicit `null` value (`token`)
   both render as empty string. **Passed.**
3. A `displayName` value containing `$1` and backslashes renders correctly (no
   `IllegalArgumentException` from `Matcher.appendReplacement`'s own group-reference parsing). **Passed.**
4. A `token` containing `&`, `=`, and a space renders as a correctly URL-encoded query parameter
   (`token=a%26b%3Dc+d`), not the raw, multi-parameter-breaking string. **Passed.**
5. Versioned lookup picks the higher of two seeded versions for the same `(name, channel)` pair.
   **Passed** (after fixing a real bug in the scratch test itself — see Findings below).
6. An unknown `(name, channel)` pair throws `IllegalArgumentException`. **Passed.**

**No defect found in `TemplateRenderer` itself.** All 6 scenarios behaved exactly as the frozen
brief intended.

## Findings

### Finding 1 — the scratch test's own first draft had a real test-order-dependency bug

**Severity:** N/A (methodology, not a `TemplateRenderer` defect — caught and fixed within this
review, not carried into any committed artifact)

**Evidence:** The scratch test's own first draft of the versioned-lookup scenario inserted a
`version = 2` row directly onto the real, shared, already-seeded `email.verify`/`EMAIL` pair — the
same pair two *other* scratch test methods also render. Running the full scratch class together
(not each method in isolation) failed exactly one test:
`valuesContainingDollarAndBackslashDoNotBreakSubstitution`, which asserted against `email.verify`'s
own real `v1` body text — because JUnit's default (non-source-order) method ordering happened to
run the versioned-lookup test first in that run, leaving a `v2` row in place that
`TemplateRenderer`'s own highest-version-wins logic then correctly (and expectedly) preferred over
`v1` for every subsequent call in the same test class.

**This confirms `TemplateRenderer` behaved exactly as designed** — the bug was entirely in the
scratch test's own shared-mutable-fixture design, not in production code. Fixed by using a
dedicated, non-seeded template name (`scratch.versioned`) for the versioned-lookup scenario instead
of mutating shared seeded state. Re-ran the full scratch class 3 times after the fix; all 3 clean
(6/6 each run).

## Confirmed non-issue — no concurrent-access test needed

Same reasoning as T08's own Phase 7: `TemplateRenderer.render` is pure read logic with no write
path at all in this task's own scope. No race to defend against.

## Verification performed

- `mvn -pl services/notification clean verify` — 149 tests, 0 failures, unchanged from Phase 6's
  own final record (the scratch test above was deleted before this run).
- Scratch verification (described above, deleted before this commit): confirmed
  `TemplateRenderer`'s full behavior (real-seed rendering, missing/null-value handling, `$`/`\`
  safety, URL encoding, trailing-slash normalization, versioned lookup, unknown-template exception)
  against a real Postgres instance and a real non-blank `baseUrl`.
