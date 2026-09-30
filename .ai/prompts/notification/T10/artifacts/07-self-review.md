# notification · T10 · Phase 7 — Self-Review

## Files reviewed

- `common/SecretSafeLogging.java`
- `T01SkeletonRegressionTest.java` (diff)

## Verification performed — empirical, not just inspection

Same discipline as T08/T09's own Phase 7: `SecretSafeLogging.redact`'s real behavior had never
been exercised against T09's own actual rendered output before this review (Kimi Phase 3 Finding
#5's own concern — the whole reason this utility exists). Wrote a temporary, uncommitted scratch
test (`ScratchSecretSafeLoggingVerificationTest`, deleted before this artifact was written)
exercising:

1. **The real, load-bearing scenario**: rendered `email.password_reset` (T09's own real
   `TemplateRenderer`, real seeded row, real Testcontainers Postgres) with a genuine fake token
   (`super-secret-reset-token-xyz`) embedded in the computed `resetLink` — confirmed the rendered
   body does contain the raw token (proving the test fixture itself is meaningful, not vacuous),
   then confirmed `redact()` removes it and leaves `token=***` in its place. **Passed.**
2. A short sanity sweep beyond the real-output case: no-match passthrough, case-insensitive key
   matching with original casing preserved in the output, multiple distinct matches in one input,
   the documented `password=hello world` → `password=*** world` boundary behavior, and `null` →
   `null`. **All passed**, exactly as designed.

**No defect found.** `SecretSafeLogging.redact` behaves exactly as the frozen brief intended,
including against a real, non-synthetic rendered message body — not just hand-constructed test
strings.

## Findings

No new findings this review — the implementation matches the frozen brief exactly, and the one
property most worth empirically confirming (real T09 output, not just synthetic strings) was now
exercised, not merely asserted correct by inspection.

## Confirmed non-issue — no injection/escaping risk from caller-supplied values

Unlike `TemplateRenderer.substitute` (which must guard against `$`/`\` in *caller-supplied* values
appearing in its own output, T09's own Kimi Finding #1), `redact`'s replacement is always the fixed
literal `<matched-key>=***` — the matched *value* is captured but never included in the output at
all, and `Matcher.replaceAll(Function<MatchResult, String>)`'s own documented behavior treats the
function's returned string as literal, not subject to further backreference processing. There is no
path by which a value's own content (however adversarial) can affect the replacement text.
Deliberately not planning a T09-style `$`/`\`-in-value regression test for this specific method,
since the class of bug that Finding class targets does not apply here by construction.

## Verification performed

- `mvn -pl services/notification clean verify` — 174 tests, 0 failures, unchanged from Phase 6's
  own final record (the scratch test above was deleted before this run).
- Scratch verification (described above, deleted before this commit): confirmed `redact()`'s full
  behavior — including against T09's own real rendered output — matches the frozen brief exactly.
