# payment · T25 · Phase 7 — Self-Review

Consumes: `artifacts/06-implementation-notes.md`. The diff is commit `34b18ae6`: `ArchitectureTest.java`
gains 53 insertions, and `RoguePermitAllUser.java` is new.

Findings only; fixes, if any, are applied in Phase 9.

## Dimensions with no findings

| Dimension | Assessment |
|---|---|
| Correctness | The rule matches the frozen shape exactly. A mutation check (Phase 6) proved that the real-code canary fails at `ResourceServerConfig.java:57` when the exclusion is removed, so it is not vacuous. The violation canary fails on the fixture. |
| Null-safety, thread-safety, transactions, idempotency | Not applicable. These are static-analysis tests: no shared mutable state, no DB, no consumers. `ArchRule` fields are immutable `static final`. |
| Money types (L6) | No numeric types were introduced. The L6 rule is unchanged and green. |
| Module boundaries (L12) | The fixture lives under `archtestfixtures`, outside every feature module. The real-code canary uses `DoNotIncludeTests`, so the fixture can never reach it. |
| Secret handling, enumeration safety | Not applicable. There are no secrets, and no runtime path. |
| Surefire | There is no `@ArchTest` and no `@AnalyzeClasses`. Both new canaries appear by name in the surefire XML. |
| Readability, complexity | It follows the file's existing canary-pair conventions and Javadoc style. The single rule has no custom `ArchCondition`. |

## Findings

### Finding 1: `doNotBelongToAnyOf` also exempts nested and anonymous classes of `ResourceServerConfig`

- **Issue.** ArchUnit's `belongToAnyOf(X)` matches `X` *and any nested or anonymous class inside
  `X`*. So a future inner class of `ResourceServerConfig` that calls `permitAll()` would also be
  exempt from the new rule.
- **Severity.** Info.
- **Evidence.** `ArchitectureTest.java`, rule `shouldEnforcePublicEndpointAllowlist`, clause
  `.and().doNotBelongToAnyOf(ResourceServerConfig.class)`.
- **Why it is not a gap.**
  - Such an inner class is still in `ResourceServerConfig.java`. `PublicEndpointsTest`'s source
    sweep (`:80-93`) would then count a second `.permitAll()` line and fail. That is exactly the
    F1 guarantee split.
  - The exemption is also identical to auth and notification, so it keeps sibling parity.
- **Recommendation.** No change. Optionally add one Javadoc clause saying that nested classes
  are covered by `PublicEndpointsTest` rather than by this rule.

### Finding 2: the violation canary's `hasMessageContaining("permitAll")` does not discriminate

- **Issue.** ArchUnit's failure message always begins with the rule's own description:
  "no classes … should call method AuthorizedUrl.permitAll() …". The `"permitAll"` assertion
  therefore holds for *any* failure of this rule, whatever the violation.
- **Severity.** Low.
- **Evidence.** In `ArchitectureTest.java`, the last test is
  `shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation`, and the
  assertion in question is `.hasMessageContaining("permitAll")`.
- **Why it is not a gap.**
  - The discriminating assertion is `hasMessageContaining("RoguePermitAllUser")`. The rule
    description never contains that name; it appears only in a violation line about the
    fixture. That assertion alone satisfies AC4.
  - The extra assertion is harmless but adds nothing.
- **Recommendation.** Either keep it (harmless) or tighten it to the violation-line shape,
  `"RoguePermitAllUser.openUp"`. That makes the assertion pin the specific offending method.
  Phase 9 decides.

### Finding 3: the Phase 5 plan miscounted per-class tests (documentation only)

- **Issue.** The plan expected 13 tests in `ArchitectureTest`, but the real count is 11. The
  grep counted Javadoc mentions of `@Test`.
- **Severity.** Info. It was already disclosed in Phase 6, Deviation 1.
- **Evidence.** `artifacts/05-implementation-plan.md`, step 4; surefire shows `Tests run: 11`.
- **Recommendation.** None. The full-suite AC5 target (492) was correct and is met.

## Summary

There is no correctness, security or boundary defect. Finding 2 is the only candidate for a
code touch, an optional assertion tightening. Findings 1 and 3 are informational.
