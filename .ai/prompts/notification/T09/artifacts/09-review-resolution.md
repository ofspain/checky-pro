# notification · T09 · Phase 9 — Review Resolution

Disposition of the 8 findings from `artifacts/08-independent-review.md`. All 8 independently
verified against real source before disposition — none were false.

## Finding 1 (Kimi) — no T09-specific tests exist yet

**Disposition: DEFERRED to Phase 10, as planned.** Verified true: `template/` has no test directory
yet. Same disposition as T03/T04/T05/T06/T08's own Phase 9 precedent — Phase 6's own explicit rule
("tests are Phase 10's job") defers all new tests. Kimi's own recommended test shape (unit +
integration, covering substitution/version/exception/link-override) is retained verbatim as Phase
10's own required-test list.

## Finding 2 (Kimi) — `RenderedMessage`'s auto-generated `toString()` may leak tokens/PII

**Disposition: ACCEPTED, fixed.** Verified true and immediately actionable, unlike Kimi's other 7
findings (all test-shaped): `RenderedMessage` is a record, so its default `toString()` prints
`subject`/`body` in full — for `email.verify`/`email.password_reset`, `body` contains the raw
verification/reset token embedded in a computed link, a direct L4 violation if ever logged.
Overridden `toString()` to print only `version` and each field's own *length*, never content —
mirrors `EmailRequestedEvent`'s own established T06 precedent for the identical concern at the DTO
layer. Fixed now (Phase 9), not deferred, since this is a real production-code gap, not a missing
test — mirrors T06's own Phase 9 precedent of fixing genuinely actionable findings immediately
rather than waiting for Phase 10.

## Finding 3 (Kimi) — no test proves computed links override caller-supplied values (AC6)

**Disposition: DEFERRED to Phase 10.** Verified true: no test passes a caller-supplied
`verificationLink` and confirms the computed value wins. Retained as a required Phase 10 test.

## Finding 4 (Kimi) — no test proves `RenderedMessage.version()` surfaces correctly

**Disposition: DEFERRED to Phase 10, risk already closed once.** Self-review's own scratch test
(Phase 7) already proved this empirically against a real seeded row (`version` 1) and a real
inserted second version (`version` 2). Retained as a required Phase 10 test (mocked-repository
form, per Kimi's own suggestion, complementing the already-proven real-DB form).

## Finding 5 (Kimi) — no test proves blank/`null` `baseUrl` behavior

**Disposition: DEFERRED to Phase 10, risk partially closed once.** Self-review's own scratch test
proved the non-blank, trailing-slash case against a real `LinkProperties` override
(`https://checky.pro/` → single `/` at the join point). The `null`/blank case specifically was not
separately exercised. Retained as a required Phase 10 test, using a mocked `LinkProperties` (or a
second `@SpringBootTest` property override) for both the `null` and empty-string cases.

## Finding 6 (Kimi) — no test proves the placeholder regex rejects invalid keys

**Disposition: DEFERRED to Phase 10.** Verified true: no test exercises `{{}}`/`{{123}}`/
`{{a-b}}`/unclosed `{{key}`. Retained as a required Phase 10 test — documents that malformed
placeholders are left as literal text (the regex simply doesn't match them, so `substitute` never
touches that span).

## Finding 7 (Kimi) — no test proves `TemplateRenderer` is a resolved Spring bean

**Disposition: DEFERRED to Phase 10.** Verified true. Mirrors T06/T08's own identical Kimi finding
resolution (`IdempotencyGuardIntegrationTest.theRealNoOpDispatcherIsTheResolvedSpringBean`/
`preferenceResolverIsAResolvedSpringBean`) — the same test class is the natural home for a
`TemplateRenderer`-is-a-bean assertion too.

## Finding 8 (Kimi) — `TemplateRepository` still exposes inherited mutators

**Disposition: ALREADY TRACKED, no new action.** Identical concern to Finding #6 of this task's own
Phase 3 design challenge, already dispositioned at Phase 4: kept `extends JpaRepository` for
consistency with T04/T05/T08's own established precedent, documented via Javadoc rather than
narrowed, with the `V7` `SELECT`-only grant as the real defense-in-depth layer.

## Verification

`mvn -pl services/notification clean verify` — 149 tests, 0 failures (only `RenderedMessage`'s own
`toString()` override changed; source-compatible with every existing caller — there are none yet
outside this task's own files). `git status -s services/auth services/crypto services/payment` —
empty; no sibling service touched.
