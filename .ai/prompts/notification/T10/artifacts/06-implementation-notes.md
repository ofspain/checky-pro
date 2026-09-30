# notification · T10 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). `src/main` file created plus the frozen-brief-authorized
`T01SkeletonRegressionTest.java` update — no new test file authored (Phase 10 scope, per this
phase's own rule, matching T08/T09's own precedent).

## Files created

- `common/SecretSafeLogging.java` — `public final class`, private constructor (Finding #3,
  never instantiated). `static String redact(String text)`: `null` → `null` (AC4, a deliberately
  lenient contract); otherwise matches `(?i)(token|secret|password|api_?key)=([^&\s]*)` and
  replaces each match with `<key>=***` via `Matcher.replaceAll(Function<MatchResult, String>)`
  (JDK 9+), preserving the matched key's own original casing, discarding the value entirely. Scope
  (query-string-shaped input only, value terminates at `&`/whitespace) documented explicitly in
  the class's own Javadoc per Findings #4/#6 — not merely implied by test coverage.

## Files modified

- `T01SkeletonRegressionTest.java` (Finding #8, frozen-brief-authorized, listed explicitly this
  time rather than discovered mid-implementation) — `noExtraProductionClassesExistBeyondT09sOwnAuthorizedSet`
  renamed to `...T10sOwnAuthorizedSet`; 26-file list widened to 27 (`common/SecretSafeLogging.java`,
  sorted correctly between `ResourceServerConfig.java` and the `common/config/` subdirectory).

## Verification performed

- `mvn -pl services/notification clean verify` — 174 tests, 0 failures, clean `package`/`repackage`
  (unchanged count from T09's own final record — `SecretSafeLogging`'s own behavioral tests, the
  static-scan test, and the real-T09-output redaction test are all Phase 10's job per this phase's
  own "no tests" rule).
- Manually traced `redact` against all 6 frozen-brief findings relevant to its own logic by
  inspection: confirmed the regex's own 4 key-name alternatives (`token`/`secret`/`password`/
  `api_?key`), confirmed `(?i)` makes key matching case-insensitive while `match.group(1)` preserves
  the matched key's own original casing in the replacement, confirmed the value character class
  `[^&\s]*` stops at `&`/whitespace exactly as documented, confirmed `null` short-circuits before
  the regex is ever touched.

## Acceptance criteria mapping

- **AC1/AC2/AC3/AC4** — `redact`'s own logic implemented exactly as designed; real behavioral proof
  (single match per key name, multiple matches, no-match passthrough, `null` handling) is Phase 10's
  own job per this phase's "no tests" rule — not yet automated, but the logic itself is in place.
- **AC5** — the static-scan test itself (the tightened, `toString()`-body-content-checking version
  per Kimi Finding #1) is Phase 10's own deliverable, not this phase's; nothing in `SecretSafeLogging`
  itself needs to satisfy AC5 — AC5 is a property of the *rest of the codebase*, enforced by a test
  this phase doesn't write.
- **AC6** — `SecretSafeLogging`'s sole constructor is `private`. ✅ (confirmed by direct source
  inspection.)
