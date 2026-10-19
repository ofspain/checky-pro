# notification · T20 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi, commit `34bc0a97`).

Human decision (2026-10-19): accept Gaps 3 and 4; reject Gaps 1, 2, 5 and 6. Each gap was checked against the tests before it was put to the human.

Verification: `mvn -o -pl services/notification verify` → **422/422, BUILD SUCCESS**. No new test methods; two tests were strengthened. Mutation T12 was killed and restored byte-identical.

## Accepted

**Gap 3 — template version not pinned.**
- **Change:** `SecretLeakEndToEndIntegrationTest.shouldNotLeakTheVerificationTokenIntoRenderedMessagesOrLogs` now asserts the `delivery_log` rows are exactly `EMAIL:email.verify:1` and `IN_APP:user.verify:1`.
- **Effect:** when FU-T20-1 seeds an in-app `user.verify` version 2 without the link, this line fails first, with a message that says why.
- No mutation was run, because a hypothetical V15 would be a production migration. The assertion reads the real column directly.

**Gap 4 — §8 list integrity.**
- **Change:** `T01SkeletonRegressionTest.everyTestNamedInTheTestPlanExists` now:
  - parses each `` - `name` → <target> `` line
  - requires the 20 names to be distinct
  - requires the targets to be exactly R1–R19 plus L11, each once
- **Mutation:** T12 retargeted `shouldWelcomeUserOnUserRegistered` from R6 to R5; it was killed.

## Rejected

**Gap 1 — guard on the §11 notes.**
- This is the same proposal as Phase 8 F6, which the human rejected at Phase 9. It also says Q8 is "intentionally open", but Q8 is **Resolved (2026-07-20)**.

**Gap 2 — track FU-T20-1 in `tasks.md`.**
- `tasks.md` has no status or follow-up fields, and the frozen brief keeps it out of scope (F7, Phase 4).
- FU-T20-1 is recorded in the `package.md` header, §9 item 6, these artifacts and the PR notes.

**Gap 5 — the deviation notes in `design.md` / `requirements.md`.**
- Already covered by `specKeepsItsDatedDeviationNotesBesideTheTextTheyQualify` (Phase 10), which checks:
  - the L2 interpretation
  - the §4c "Recipients as shipped" note and its three deviations
  - the R3/R5 partial notes
- It killed mutations T10 and T11.

**Gap 6 — the opt-out E2E's in-app half.**
- Already asserted. `PaymentEmailOptOutIntegrationTest` requires exactly `EMAIL:SUPPRESSED` **and** `IN_APP:SENT` rows, plus one `PAYMENT` in-app row containing `1.5`. It is listed in `06-implementation-notes.md`.

## Files changed

- `services/notification/src/test/java/com/themistra/notification/common/SecretLeakEndToEndIntegrationTest.java`
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
