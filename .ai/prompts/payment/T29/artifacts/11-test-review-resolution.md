# payment · T29 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi). Every gap was checked against `T01SkeletonRegressionTest`, and all seven are accurate.

## Human decisions (2026-10-19)

| Gap | Decision | Change |
|---|---|---|
| 1 — a cited method may be a non-test helper | **Accept** | The new `declaresTestMethod` requires `@Test` or `@ParameterizedTest` directly before `void <name>(`. Other annotations, such as `@CsvSource`, may sit in between. |
| 3 — a cited class may contain no tests | **Accept** | A bare `…Test` citation must resolve to a file containing `@Test` or `@ParameterizedTest`. |
| 2 — the ticked items aren't guarded | **Accept** | Items 2–12 must start with `- [x] `. Exactly 11 items are ticked. Every marker is `- [ ] ` or `- [x] `. |
| 4 — the exemption is hard-coded | **Accept** | The `should…` names in item 14 are parsed and must equal `DEFERRED_UNWRITTEN_TESTS`. |
| 5 — item 1's reason could be dropped | **Accept** | Item 1 must contain `FU-T29-1`. |
| 6 — duplicate header rows aren't caught | **Accept** | The header table must have exactly one Version row and one Status row. |
| 7 — the guards prove a test exists, not that it passes | **Accept, as documentation only** | See below. |

### Gap 7

The claim that a cited test passes rests on the full `mvn -o -pl services/payment verify` run, not on these guards. That run gave 535/535.

As of this phase:
- No payment test carries `@Disabled`, `@DisabledIf` or `@EnabledIf`.
- Neither the payment pom nor the root pom configures Surefire includes, excludes or groups.

So no cited test is skipped or filtered today.

## A finding from the first run

The new `@Test` requirement initially flagged `EventTopicsTest.shouldRouteEachPublishedEventToItsPaymentsTopic`. That test is a genuine `@ParameterizedTest` with a multi-line `@CsvSource`, so the check was widened to accept `@ParameterizedTest`. This was a false positive in my first draft, not a spec defect.

## Verification

**Mutations.** Each was restored and confirmed with `cmp`, including the test-source mutation M10:

| # | Mutation | Fails |
|---|---|---|
| M10 | `@Test` removed from the cited `ArchitectureTest.shouldRejectFloatingPointMoneyTypesAtArchUnit` | citation guard |
| M11 | a cited `NoTestsHereTest` (temporary file with no test) | citation guard, "(test class)" |
| M12 | item 5 unticked | "item 5 stays ticked" |
| M13 | item 5 marked `[~]` | "item 5 stays ticked" (see below) |
| M14 | `FU-T29-1` dropped from item 1 | "item 1 keeps its R28 follow-up" |
| M15 | item 14's deferred name drifts | "item 14 names exactly the exempted deferred tests" |
| M16 | a duplicate `` | Version | `0.1` | `` row added in the header | "exactly one Version row" |

**On M13.** The per-item assertions fail before the marker check runs, because every one of the 14 items is now pinned individually. The `allMatch` marker check is therefore a backstop that can't fail on its own today, and I am reporting it as such rather than counting it as separately proven.

**Full suite.** `mvn -o -pl services/payment verify`: **535/535, BUILD SUCCESS**. The test count is unchanged because the additions are assertions inside the three existing T29 tests. `T01SkeletonRegressionTest`: 12/12.
