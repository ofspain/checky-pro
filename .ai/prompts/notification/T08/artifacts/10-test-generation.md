# notification · T08 · Phase 10 — Test Generation

All tests deferred from Phase 6 and tracked at Phase 9 (Kimi Phase 8 Findings #1-#7) are added here.
`package.md` §8's 3 named tests for this task (`shouldResolveChannelPreferencesPerRecipient`,
`shouldSuppressChannelWhenRecipientOptedOut`, `shouldFallBackToDefaultPreferenceWhenNoneSet`) are
included. 3 new test files, 15 new tests (143 total: 128 T01-T06/Phase-6 unaffected + 15 new).

## Files created

- `preference/PreferenceResolverTest.java` — 8 tests, mocked `ChannelPreferenceRepository`, no
  Spring context, no Docker.
- `preference/PreferenceResolverIntegrationTest.java` — 6 tests, `@Testcontainers` +
  `@SpringBootTest` (the third test class in this module needing a real Spring context, after
  T04/T05's own precedents).

## Files modified

- `consumer/IdempotencyGuardIntegrationTest.java` — added
  `preferenceResolverIsAResolvedSpringBean` (Finding #7), mirroring T06's own identical
  `theRealNoOpDispatcherIsTheResolvedSpringBean` precedent in the same file.

## Test manifest

| Test method | Verifies | Finding / AC |
|---|---|---|
| `PreferenceResolverTest.shouldResolveChannelPreferencesPerRecipient` | Stored row's own `enabled` value is returned | `package.md` §8, AC2 |
| `PreferenceResolverTest.shouldSuppressChannelWhenRecipientOptedOut` | Stored `enabled=false` row suppresses | `package.md` §8, AC2 |
| `PreferenceResolverTest.shouldFallBackToDefaultPreferenceWhenNoneSet` | All 6 documented defaults returned when no row exists | `package.md` §8, AC3 |
| `PreferenceResolverTest.securityEmailNeverQueriesTheRepository` | Hard floor never even queries the repository | Finding #1, AC4 |
| `PreferenceResolverTest.categoryAndChannelAreUppercasedBeforeTheRepositoryQuery` | Normalization reaches the repository call itself | Finding #2 (unit half), AC7 |
| `PreferenceResolverTest.unrecognizedPairsResolveFalseWhenNoRowExists` | `WEBHOOK`/`PUSH`/unknown category all resolve `false` | Findings #1/#3/#4, AC6 |
| `PreferenceResolverTest.resolveRejectsNullArguments` | 3 null-argument cases throw `NullPointerException` | Finding #6 |
| `PreferenceResolverTest.defaultsMapMatchesTheDesignDocVerbatimTableExactly` | No drift between `DEFAULTS` and `design.md` §4c | Finding #3 |
| `PreferenceResolverIntegrationTest.storedRowTakesPrecedenceOverTheDocumentedDefault` | Real-DB stored-row precedence | AC2 |
| `PreferenceResolverIntegrationTest.noRowFallsBackToTheDocumentedDefault` | Real-DB default fallback | AC3 |
| `PreferenceResolverIntegrationTest.securityEmailIsTrueEvenWithARealStoredFalseRow` | Hard floor against a real adversarial row | Finding #2, AC4 |
| `PreferenceResolverIntegrationTest.webhookAndPushResolveFalseWithNoRow` | Real-DB unsupported-channel coverage | Finding #4, AC6 |
| `PreferenceResolverIntegrationTest.unknownCategoryResolvesFalseWithNoRow` | Real-DB unknown-category coverage | Finding #4, AC6 |
| `PreferenceResolverIntegrationTest.lowercaseQueryArgumentsMatchAnUppercaseStoredRow` | Case normalization reaches a real stored row | Finding #5, AC7 |
| `IdempotencyGuardIntegrationTest.preferenceResolverIsAResolvedSpringBean` | `PreferenceResolver` is component-scanned | Finding #7 |

## Negative-proof (mutation testing)

Removed the `SECURITY`+`EMAIL` early-return branch entirely from `PreferenceResolver.resolve`,
re-ran `PreferenceResolverTest` and `PreferenceResolverIntegrationTest` separately: exactly 1 test
failed in each (`securityEmailNeverQueriesTheRepository` in the unit suite, catching the
now-real repository call via `verifyNoInteractions`;
`securityEmailIsTrueEvenWithARealStoredFalseRow` in the integration suite, catching the now-wrong
`false` result against the adversarial stored row) — no other test in either class failed,
confirming the mutation's blast radius was caught precisely by the two tests designed for exactly
this property, not accidentally over- or under-covered. (Every other unit test using `SECURITY`/
`EMAIL` implicitly happens to still pass under the mutation, since the default table's own
`SECURITY:EMAIL` entry is also `true` — the "never queries" test is the only one that actually
proves the floor is unconditional, not merely default-shaped.) Reverted (`git checkout --`);
`git status -s` on the file empty afterward; both targeted test classes re-ran clean (8/8, 6/6).

## Verification

`mvn -pl services/notification clean verify` — 143 tests, 0 failures. No production code left
modified in this phase (the mutation above was reverted before the final verification run).

## Addendum (post Phase 11) — 5 of 7 gaps accepted and added; 2 dispositioned with no action

Kimi's Phase 11 review raised 7 gaps. All verified against source before acting. 5 are added
(148 tests → from 143):

- **Gap #1** (no permanent guard that the `SECURITY`+`EMAIL` early return itself exists, in the
  right place) — added `resolveContainsTheSecurityEmailEarlyReturnBeforeAnyRepositoryCall`, a
  static text-scan test asserting the early return appears before the repository call in source
  order.
- **Gap #2** (no test for `SECURITY`/`IN_APP` opt-out, the hard floor's own non-floored sibling) —
  added `securityInAppStillHonoursAStoredOptOutUnlikeSecurityEmail`, proving the floor is scoped
  exactly to `SECURITY`+`EMAIL`, not the whole `SECURITY` category. (The symmetric "stored `true`
  row on `SECURITY`/`EMAIL`" half of this gap was judged not worth a dedicated test — trivially
  implied by the unconditional-`true` early return already proven by
  `securityEmailNeverQueriesTheRepository`.)
- **Gap #3** (no test for a stored row on an unsupported channel) — added
  `storedRowOnAnUnsupportedChannelTakesPrecedenceOverItsMissingDefault`
  (`PAYMENT`/`WEBHOOK`, `enabled=true`, real Postgres).
- **Gap #4** (whitespace/empty-string input behavior undocumented) — added
  `whitespacePaddedInputIsNotTrimmedAndFallsBackToFalse`, documenting the current behavior (not
  trimmed, a caller-bug case treated the same as the already-accepted null-argument contract,
  Finding #6) rather than changing `resolve` to trim - no real caller exists yet (task 11 doesn't
  exist), and inventing defensive behavior for a hypothetical caller was judged out of this task's
  own scope.
- **Gap #5** (no test verifies `ChannelPreference`'s own column mapping directly) — added
  `channelPreferenceEntityMapsAllSixColumnsCorrectly`, reading a real stored row via
  `ChannelPreferenceRepository` directly (not through `PreferenceResolver`) and asserting all 6
  getters.
- **Gap #6** (verbatim-drift test's own regex is sensitive to `design.md` formatting) —
  **no action**. The regex's sensitivity to reformatting is the point, not a flaw: if `design.md`'s
  own default table is ever restated in a different shape, the drift test failing loudly (via the
  existing `lineCount == 3` assertion, which already produces a clear actual-vs-expected message)
  is the correct outcome, not something to soften. Generalizing the regex to tolerate
  reformatting would weaken exactly the guarantee this test exists to provide.
- **Gap #7** (no test reads `channel_preferences` via `notification_app` from `PreferenceResolver`'s
  own perspective) — **no action**, per Kimi's own concession: already covered by
  `NotificationBaselineMigrationIntegrationTest.notificationAppCanSelectButNotInsertUpdateOrDeleteOnChannelPreferences`
  (Phase 6), which proves the runtime role's read privilege directly; a second, narrower proof
  through `PreferenceResolver` specifically would be redundant.

**Verification:** `mvn -pl services/notification clean verify` — 148 tests, 0 failures.
