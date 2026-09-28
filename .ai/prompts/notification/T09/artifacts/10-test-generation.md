# notification · T09 · Phase 10 — Test Generation

All tests deferred from Phase 6 and tracked at Phase 9 (Kimi Phase 8 Findings #1, #3-#7) are added
here. `package.md` §8's named test for this task (`shouldRenderTemplateWithEventDataAndSelectedChannel`)
is included. 3 new test files, 17 new tests (166 total: 149 T01-T08/Phase-6 unaffected + 17 new).

## Files created

- `template/TemplateRendererTest.java` — 12 tests, mocked `TemplateRepository`, a real (not mocked)
  `LinkProperties` record instance, no Spring context, no Docker.
- `template/TemplateRendererIntegrationTest.java` — 4 tests, `@Testcontainers` + `@SpringBootTest`
  (the fourth test class in this module needing a real Spring context, after T04/T05/T08's own
  precedents).

## Files modified

- `consumer/IdempotencyGuardIntegrationTest.java` — added
  `templateRendererIsAResolvedSpringBean` (Finding #7), mirroring T06/T08's own identical precedent
  in the same file.

## Test manifest

| Test method | Verifies | Finding / AC |
|---|---|---|
| `TemplateRendererTest.shouldRenderTemplateWithEventDataAndSelectedChannel` | Basic substitution, subject + body | `package.md` §8, AC3 |
| `TemplateRendererTest.missingPlaceholderRendersAsEmptyString` | Missing key → empty string | AC3 |
| `TemplateRendererTest.nullValueInEventDataRendersAsEmptyString` | Explicit `null` value → empty string | Finding #9 (T04 brief), AC3 |
| `TemplateRendererTest.valuesContainingDollarAndBackslashDoNotBreakSubstitution` | `Matcher.quoteReplacement` safety | Finding #1 (T04 brief), AC8 |
| `TemplateRendererTest.renderedMessageSurfacesTheFetchedTemplatesOwnVersion` | `RenderedMessage.version()` | Finding #4, AC4 |
| `TemplateRendererTest.unknownTemplateThrowsIllegalArgumentException` | Unknown pair throws | AC7 |
| `TemplateRendererTest.computedLinkPlaceholdersOverrideCallerSuppliedValues` | Computed link wins over caller-supplied value | Finding #3, AC6 |
| `TemplateRendererTest.malformedPlaceholdersAreLeftAsLiteralText` | `{{}}`/`{{123}}`/`{{a-b}}`/unclosed left as-is | Finding #6, Finding #8 (T04 brief) |
| `TemplateRendererTest.renderedMessageToStringExcludesSubjectAndBodyContent` | `toString()` doesn't leak tokens/PII | Finding #2, L4 |
| `TemplateRendererTest.blankBaseUrlProducesARelativeLink` | Blank `baseUrl` → relative link | Finding #5 |
| `TemplateRendererTest.nullBaseUrlProducesARelativeLinkNotAnException` | `null` `baseUrl` → relative link, no throw | Finding #5, Finding #4 (T04 brief) |
| `TemplateRendererTest.resolveRejectsNullArguments` | 3 null-argument cases throw `NullPointerException` | Constraints |
| `TemplateRendererIntegrationTest.rendersTheRealSeededEmailVerifyTemplateWithATrailingSlashBaseUrlNormalized` | Real-seed rendering + trailing-slash normalization | AC2/AC3/AC9 |
| `TemplateRendererIntegrationTest.tokenWithReservedUrlCharactersIsUrlEncodedInTheComputedLink` | Real URL-encoding | Finding #3 (T04 brief), AC8 |
| `TemplateRendererIntegrationTest.inAppChannelHasNoSubjectRow` | `IN_APP` rows have `subject = null` | AC1 |
| `TemplateRendererIntegrationTest.versionedLookupUsesTheHighestVersionWhenTwoExistForTheSamePair` | Real-DB highest-version lookup | AC2 |
| `IdempotencyGuardIntegrationTest.templateRendererIsAResolvedSpringBean` | `TemplateRenderer` is component-scanned | Finding #7 |

## Negative-proof (mutation testing)

Swapped the merge order in `render` — `values = new HashMap<>(computeLinkPlaceholders(eventData));
values.putAll(eventData);` instead of the reverse — so a caller-supplied value under a link key
would win instead of the computed one. Re-ran `TemplateRendererTest` alone: exactly 1 test failed
(`computedLinkPlaceholdersOverrideCallerSuppliedValues`, the test designed for exactly this
property) — no other test in the class failed, confirming the mutation's blast radius was caught
precisely. Reverted (`git checkout --`); `git status -s` on the file empty afterward; the targeted
test re-ran clean (12/12).

## Verification

`mvn -pl services/notification clean verify` — 166 tests, 0 failures. No production code left
modified in this phase (the mutation above was reverted before the final verification run).
