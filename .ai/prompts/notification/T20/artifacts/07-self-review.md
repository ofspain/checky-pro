# notification · T20 · Phase 7 — Self-Review

Consumes: `artifacts/06-implementation-notes.md` (commit `3473097e`).

This is a review of the diff against `04-frozen-task-brief.md` and `agents.md`. It records findings only; fixes belong to Phase 9.

## Findings

| # | Issue | Severity | Evidence | Recommendation |
|---|---|---|---|---|
| S1 | **§9 item 1's "all 20 §8 names exist and pass" isn't enforced.** The new guard only checks the tests §9 *cites*. Nothing checks the §8 list itself, so a later rename of, say, `shouldWelcomeUserOnUserRegistered` would leave §8 pointing at nothing while item 1's text still claims all 20 exist. This is the exact drift T20 found for R15. | Medium | `T01SkeletonRegressionTest.everyTestNamedInTheVerificationChecklistExists` (it parses §9 only); `package.md` §8 (20 bullet names); §9 item 1 | Add `everyTestNamedInTheTestPlanExists`. It parses the `` `should…` `` names in §8 and requires each to be a JUnit test method somewhere under `src/test/java`, or, for L11 only, an `ArchRule` field run by a canary. |
| S2 | **The guard's `ArchRule`-field branch is dead today.** Item 1 cites the L11 *canary method*, so no §9 citation exercises the field branch of `declaresTestMethod`. Untested logic in a guard is a false-confidence risk. | Low | `declaresTestMethod` (`checkedRule` branch) | If S1 is accepted, the §8 guard uses that branch for `shouldPreventCrossModuleEntityImports`, which gives it a real caller. Otherwise remove the branch. |
| S3 | **The R15 test covers only the reset-token path.** It doesn't produce a `verify_email` event, whose token takes the same renderer path to a different link. T10's `SecretSafeLoggingIntegrationTest.redactsTheRealTokenFromARealRenderedVerificationBody` covers the redaction, not the end-to-end log capture. | Low | `SecretLeakEndToEndIntegrationTest` (one event) | Optionally add a `verify_email` event to the same test, with a second distinctive token and the same assertions. |
| S4 | **The R15 test changes a shared logger's level.** `com.themistra` is set to DEBUG and restored in `@AfterEach`. It is restored even on failure, but if the Spring context cache reuses this context, or another class runs in parallel in the same JVM, DEBUG lines could appear elsewhere during the window. Surefire runs classes serially here, so the effect is noise only. | Info | `captureEveryLogLineIncludingDebug` / `stopCapturing` | No change. Noted for anyone enabling parallel test execution. |
| S5 | **The checklist guard hard-codes 11 items and exactly 8 ticked.** Adding a §9 item later breaks the test on purpose, which forces whoever edits it to revisit the partial/ticked split. That is intended, and it is the same as the payment T29 precedent. | Info | `verificationChecklistKeepsItsPartialItemsUnticked` | No change. |
| S6 | **The header says "verified through tasks 1–20" before T20's own Phases 7–13 are done.** It will be true at merge. The payment T29 header was worded the same way at its Phase 6. | Info | `package.md` Status row | No change, unless Phase 8–11 changes alter T20's scope. |
| S7 | **A dated sub-bullet sits under L2 inside §4a ("implement exactly, do NOT deviate").** It is sanctioned by D2 and records a human-granted interpretation, not an edit to L2's sentence. A reader skimming §4a now sees the interpretation directly under L2, which is the point (no silent deviation). | Info | `design.md` L2 + sub-bullet | No change. |
| S8 | **O6 was annotated beyond the brief's list.** Disclosed in 06. T09 settled the template-engine choice, and leaving it "OPEN" would have been stale. | Info | `design.md` O6 suffix | Kimi or the human may confirm; it can be reverted trivially if rejected. |

## Checklist

| Area | Assessment |
|---|---|
| Correctness of claims | Every §11 note and §9 citation was checked against the source while writing. The citation guard passes with ≥ 25 citations. "Provisional" paths are confirmed at `TemplateRenderer.java:23`. The `RetryProperties` values match `application.properties:77`–`:79`. |
| Honesty (no false tick) | Items 1–3 are partial with reasons and pinned unticked. The ticked items 4–11 each cite passing tests. |
| Verbatim integrity | The §4c fences' hash is unchanged (`verbatimSection4cIsUnchanged`, mutation T4 killed). |
| Scope | Only `spec/notification-service/` and `services/notification/src/test/` changed. No production code (AC7). |
| Tests | Both new integration tests are isolated on their own Kafka containers. Mutations T1–T6 were all killed. |
| Security | The R15 test's token is synthetic, and no real secret is in any fixture. |
| Readability | The spec notes are dated, attributed to their task and phase, and cite follow-up IDs. |

## Open Questions

None blocking. S1 is the one that matters most; Kimi Phase 8 can weigh in.
