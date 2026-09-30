# notification · T10 · Phase 9 — Review Resolution

Disposition of the 7 findings from `artifacts/08-independent-review.md`. All 7 independently
verified against real source before disposition. **Findings #2 and #3 are FALSE as stated** — both
describe the *original* Phase 2 brief's own text, not the current, authoritative Phase 4 frozen
brief, which already resolved both concerns before Phase 6 implementation began.

## Finding 1 (Kimi) — no T10-specific tests exist yet

**Disposition: DEFERRED to Phase 10, as planned.** Verified true: `common/` has no
`SecretSafeLoggingTest.java` yet. Same disposition as T03/T04/T05/T06/T08/T09's own Phase 9
precedent — Phase 6's own explicit rule ("tests are Phase 10's job") defers all new tests. Kimi's
own recommended test shape is retained as part of Phase 10's own required-test list.

## Finding 2 (Kimi) — the static-scan test design only checks presence of `toString()`, not content

**Disposition: FALSE, as stated — already resolved at Phase 4, no new action.** Kimi's own
evidence cites "the brief's AC5" without checking which brief. Verified directly against
`artifacts/04-frozen-task-brief.md` line 51: "AC5 now explicit that the scan verifies `toString()`'s
own body content" — this is exactly Kimi's own Phase 3 Finding #1 (design-challenge phase, this
same task), already accepted and folded into the frozen brief *before* Phase 6 implementation
began. Kimi's Phase 8 review appears to describe the superseded Phase 2 brief's own text, not the
current, authoritative Phase 4 one. The underlying recommendation itself is correct and was never
in dispute — it's simply already planned (Phase 5's own implementation plan, item 8, explicitly
describes the content-checking scan) — so there is nothing new to disposition here beyond
confirming Phase 10 will build it exactly as already planned.

## Finding 3 (Kimi) — inconsistency between the static-scan field list and `redact()`'s own regex

**Disposition: FALSE, as stated — already resolved at Phase 4, no new action.** Kimi's own
evidence cites the scan's field list as including `key`, attributed to "Brief AC5." Verified
directly: `artifacts/04-frozen-task-brief.md` line 12 (Finding #2's own disposition) explicitly
drops `key` from the scan's own field-name list — "Remaining terms: `token`, `secret`, `password`,
`apiKey`/`api_key`" — matching `SecretSafeLogging.java`'s own real regex exactly (verified by
direct source inspection: no `key` alternative anywhere in the compiled pattern). The two lists
Kimi describes as inconsistent are, in the actual current brief and the actual current code,
identical. Same likely cause as Finding #2 — a stale premise, not a real implementation gap.

## Finding 4 (Kimi) — no test verifies `SecretSafeLogging` cannot be instantiated

**Disposition: DEFERRED to Phase 10.** Verified true: no test exercises the private constructor.
Retained as a required Phase 10 test (AC6).

## Finding 5 (Kimi) — no committed test verifies `redact()` against a real rendered message body

**Disposition: DEFERRED to Phase 10, risk already closed once.** Self-review's own scratch test
(Phase 7) already proved exactly this — rendered `email.password_reset` with a real fake token via
T09's own real `TemplateRenderer`, confirmed the raw token appears before redaction and is replaced
by `token=***` after — then was deleted per this pipeline's own established scratch-test
discipline. Kimi's own recommended test is retained verbatim as a required Phase 10 test, matching
the deleted scratch test exactly (already anticipated by Phase 4/5's own Finding #5 disposition).

## Finding 6 (Kimi) — no test documents the whitespace-boundary behavior

**Disposition: DEFERRED to Phase 10, risk already closed once.** Self-review's own scratch test
already proved `redact("password=hello world")` → `"password=*** world"`. Retained as a required
Phase 10 test (already planned per Phase 4/5's own Finding #6 disposition).

## Finding 7 (Kimi) — no test verifies byte-for-byte passthrough for non-matching text

**Disposition: DEFERRED to Phase 10.** Verified true: no committed test asserts this specific
property, though self-review's own scratch sanity sweep did cover a no-match case informally.
Retained as a required Phase 10 test (AC3), per Kimi's own suggestion to include special
characters/unicode in the non-matching fixture for a stronger proof.

## Verification

`mvn -pl services/notification clean verify` — 174 tests, 0 failures, unchanged (no production or
test code modified this phase — Findings #2/#3 required no fix, only a documented correction of
Kimi's own stale premise). `git status -s services/auth services/crypto services/payment` — empty;
no sibling service touched.
