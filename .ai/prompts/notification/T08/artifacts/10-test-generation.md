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
