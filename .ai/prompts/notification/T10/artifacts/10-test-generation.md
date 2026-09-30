# notification · T10 · Phase 10 — Test Generation

All tests deferred from Phase 6 and tracked at Phase 9 (Kimi Phase 8 Findings #1, #4-#7 — Findings
#2/#3 already verified false at Phase 9, no test change needed for them beyond what was already
planned) are added here. 3 new test files, 18 new tests (192 total: 174 T01-T09/Phase-6 unaffected
+ 18 new).

## Files created

- `common/SecretSafeLoggingTest.java` — 11 tests, plain JUnit, no Spring context, no Docker.
- `common/SensitiveFieldsHaveSafeToStringTest.java` — 5 tests, plain JUnit, no Spring context, no
  Docker. The static-scan test (AC5).
- `common/SecretSafeLoggingIntegrationTest.java` — 2 tests, `@Testcontainers` + `@SpringBootTest`
  (needs a real `TemplateRenderer` bean — the fifth test class in this module needing a real Spring
  context, after T04/T05/T08/T09's own precedents).

## Test manifest

| Test method | Verifies | Finding / AC |
|---|---|---|
| `SecretSafeLoggingTest.redactMasksASingleTokenParameter` | `token=` masked | AC1 |
| `SecretSafeLoggingTest.redactMasksASingleSecretParameter` | `secret=` masked | AC1 |
| `SecretSafeLoggingTest.redactMasksASinglePasswordParameter` | `password=` masked | AC1 |
| `SecretSafeLoggingTest.redactMasksApiKeyAndApiUnderscoreKeyParameters` | `apikey=`/`api_key=` masked | AC1 |
| `SecretSafeLoggingTest.redactMasksMultipleDistinctSecretsInOneInput` | Multiple matches | AC2 |
| `SecretSafeLoggingTest.redactLeavesNonMatchingTextCompletelyUnchanged` | Byte-for-byte passthrough, special chars/unicode | AC3, Finding #7 |
| `SecretSafeLoggingTest.redactReturnsNullForNullInput` | `null` → `null` | AC4 |
| `SecretSafeLoggingTest.redactIsCaseInsensitiveOnTheKeyButPreservesItsOwnOriginalCasing` | Case handling | AC1 |
| `SecretSafeLoggingTest.redactOnlyMasksUpToTheNextAmpersandOrWhitespace` | Documented boundary | Finding #6 |
| `SecretSafeLoggingTest.redactMasksAnEmptyValueToo` | Empty value edge case | AC1 |
| `SecretSafeLoggingTest.cannotBeInstantiated` | Private constructor, final class | AC6, Finding #4 |
| `SensitiveFieldsHaveSafeToStringTest.everyRealProductionFileWithASensitiveFieldHasASafeToString` | Real-codebase scan, 0 violations | AC5 |
| `SensitiveFieldsHaveSafeToStringTest.scanCatchesAToStringThatStillPrintsTheSensitiveField` | Scan logic catches a leaking `toString()` | Finding #1 (Phase 3), Finding #2 (Phase 8, false-as-stated but the underlying property is now locked) |
| `SensitiveFieldsHaveSafeToStringTest.scanCatchesARecordWithASensitiveComponentAndNoExplicitToString` | Scan logic catches a record relying on auto-generated `toString()` | Finding #7 (Phase 3) |
| `SensitiveFieldsHaveSafeToStringTest.scanAcceptsAToStringThatExcludesTheSensitiveField` | No false positive on a genuinely safe class | AC5 |
| `SensitiveFieldsHaveSafeToStringTest.scanIgnoresFilesWithNoSensitiveField` | No false positive on an irrelevant class | AC5 |
| `SecretSafeLoggingIntegrationTest.redactsTheRealTokenFromARealRenderedPasswordResetBody` | Real T09 output, real token, real redaction | Finding #5 |
| `SecretSafeLoggingIntegrationTest.redactsTheRealTokenFromARealRenderedVerificationBody` | Same, second real template | Finding #5 |

## Negative-proof (mutation testing)

Added a real, temporary token reference to `EmailRequestedEvent.toString()`'s own body (`", token="
+ token`) — a real regression on a real, already-shipped (T06) production file, not a synthetic
fixture. Re-ran `SensitiveFieldsHaveSafeToStringTest` alone: exactly 1 test failed
(`everyRealProductionFileWithASensitiveFieldHasASafeToString`, the real-codebase scan) — the 4
synthetic-fixture tests were unaffected (they don't touch real files), confirming the scan's own
logic genuinely catches a real leak in real production code, not merely its own synthetic fixtures.
Reverted (`git checkout --`); `git status -s` on the file empty afterward; the full test class
re-ran clean (5/5).

## Verification

`mvn -pl services/notification clean verify` — 192 tests, 0 failures. No production code left
modified in this phase (the mutation above was reverted before the final verification run).

## Addendum (post Phase 11) — 6 of 7 gaps fixed with real code changes; 1 already satisfied

Kimi's Phase 11 review raised 7 gaps. All verified against source before acting — **Gaps #2 and #3
turned out to be real, live bugs in the scan's own logic**, not merely hypothetical edge cases:
a direct grep of the real codebase found `TemplateRenderer.computeLinkPlaceholders`'s own local
variable `String token = eventData.get("token")` (Gap #2 — a local variable, not a field, that the
original scan's field-name regex could not distinguish from a real sensitive field) and confirmed
`TemplateRenderer.java` genuinely has two type declarations, the outer class and the nested
`RenderedMessage` record (Gap #3 — the original scan checked whichever `toString()` appeared first
in the file, regardless of which type actually owned the sensitive-looking match). Both were
already accidentally passing before this addendum (two wrongs cancelling out — the local variable
false-positive happened to be checked against `RenderedMessage`'s own safe `toString()`), which is
exactly the kind of latent, coincidental correctness a real fix — not just documentation — closes
properly. Rewrote `SensitiveFieldsHaveSafeToStringTest`'s own scanning logic (192 tests → 199 total):

- **Gap #1** (no permanent negative-proof that the scan catches real leaks, only a one-time manual
  mutation test) — **already satisfied, no new action**: the existing synthetic-fixture tests
  (`scanCatchesAToStringThatStillPrintsTheSensitiveField`,
  `scanCatchesARecordWithASensitiveComponentAndNoExplicitToString`) already call the exact same
  shared scanning logic the real-codebase test uses, so a future regression in that logic fails
  those permanent tests immediately — re-confirmed by re-running the real-file mutation test after
  this addendum's own rewrite (still caught, see below).
- **Gap #2** (local variables mistaken for fields) — **fixed**: `FIELD_DECLARATION_PATTERN` now
  requires a leading access modifier (`private`/`protected`/`public`); a separate
  `RECORD_COMPONENT_PATTERN` requires a leading `(`/`,`. Added
  `scanIgnoresALocalVariableThatHappensToShareASensitiveName`. Multi-field declarations
  (`String token, secret;`) and non-`String` secret types (`byte[] password`) remain disclosed,
  unfixed limitations (neither occurs anywhere in this codebase today, verified).
- **Gap #3** (wrong type's `toString()` checked in a multi-type file) — **fixed**: each sensitive
  match is now scoped to its own nearest-enclosing type (the span between the nearest preceding
  type declaration and the next one), not the whole file. Added
  `scanChecksEachTypesOwnToStringNotTheFirstOneInTheFile` and
  `scanDoesNotFalselyFlagATypeWhoseSensitiveFieldIsSafelyExcludedEvenWhenAnotherTypeInTheSameFileIsAlsoSafe`.
- **Gap #4** (comment-mentioned names) — **fixed**: `stripComments` removes `//` and `/* */`
  content before any pattern runs. Added `scanIgnoresSensitiveNamesMentionedOnlyInComments`.
- **Gap #5** (`redact()` with an internal `=` in the value) — added
  `redactMasksTheFullValueEvenWhenItContainsAnEqualsSign`.
- **Gap #6** (no record-shaped positive fixture) — added
  `scanAcceptsARecordWithAnExplicitToStringThatExcludesTheSensitiveComponent`.
- **Gap #7** (no `IN_APP`-channel integration coverage) — added
  `redactsTheRealTokenFromARealRenderedInAppVerificationBody`.

**Re-verification of the original Phase 10 mutation test after the rewrite**: re-added the same
real `token=` reference to `EmailRequestedEvent.toString()`'s own body, re-ran
`SensitiveFieldsHaveSafeToStringTest`: exactly 1 test failed (the real-codebase scan), confirming
the rewritten logic still catches this real regression. Reverted; `git status -s` on the file empty
afterward; full class re-ran clean (10/10).

**Verification:** `mvn -pl services/notification clean verify` — 199 tests, 0 failures.
