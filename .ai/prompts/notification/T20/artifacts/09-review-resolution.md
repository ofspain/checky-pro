# notification · T20 · Phase 9 — Review Resolution

Consumes:
- `artifacts/07-self-review.md`
- `artifacts/08-independent-review.md` (Kimi, commit `b6d0a3fd`)

Human sign-off: 2026-10-19, with three decisions:
1. Accept F1+F3, F2, F4 and F5.
2. Reject F6 and F7.
3. **New:** the verification-token finding becomes partial plus a follow-up.

Verification: `mvn -o -pl services/notification verify` → **421/421, BUILD SUCCESS** (+2 tests).

Mutations:
- T7 and T8 are new; both were killed.
- T9, a should-pass control, passed as intended.
- T1–T6 were re-run after the guard changes; all six were killed again.
- Every file was restored byte-identical.

## Accepted

**Kimi F1 + F3 = self-review S1 + S2 — no §8 guard; the rule-field branch is dead.**
- **Change:** new `T01SkeletonRegressionTest.everyTestNamedInTheTestPlanExists`.
  - It parses the twenty `` - `should…` → `` lines in §8 and requires each one to be an enabled JUnit test in some test file.
  - For L11, it accepts `shouldPreventCrossModuleEntityImports` as an `ArchRule` field that its class `.check(`s, which now gives the rule-field branch a real caller.
- **Mutation:** T8 renamed a §8 name; it was killed.

**Kimi F2 — annotation order.**
- **Change:** `declaresTestMethod` now collects the whole annotation block in front of `void <name>(` and looks for `@Test` or `@ParameterizedTest` anywhere in it, simple or fully qualified.
- **Control:** T9 put `@DisplayName` before `@Test`; the guard still passed.

**Kimi F5 — "pass" isn't guarded.**
- **Change:** the guard now rejects a method or class marked `@Disabled`, simple or qualified.
  - My first version matched only the short `@Disabled`, so T7 (`@org.junit.jupiter.api.Disabled`) **survived**. It was fixed with a qualified-name pattern, and T7 is now killed.
- §9 item 1 now reads: "All 20 §8 names exist as enabled tests (guarded by `T01SkeletonRegressionTest.everyTestNamedInTheTestPlanExists`) and the full suite passes".

**Kimi F4 = self-review S3 — the verify-token path is untested.**
- **Change:** new `SecretLeakEndToEndIntegrationTest.shouldNotLeakTheVerificationTokenIntoRenderedMessagesOrLogs`.
- **It exposed a real gap** (next section).

## New finding from F4, decided by the human: partial + follow-up

- **What happens:** the in-app `user.verify` template (`V3__seed_launch_templates.sql:21`) contains `{{verificationLink}}`. So the raw one-time verification token is shown in-app and **stored in `inapp_notifications.body`**.
- **Why:** this is T13's design ("deep links render inline in body"). The password-reset in-app template deliberately leaves its link out, so the two are inconsistent.
- **Spec impact:** §9 item 6's claim "no token ever appears in a rendered message body" is false for that message.
- **Decision:** no production change in T20.
- **Changes:**

| Location | Change |
|---|---|
| §9 item 6 | `[ ] **Partial —**` with the reason and **FU-T20-1**: a `version=2` in-app `user.verify` template without the link, matching password reset |
| Header disclosure | Gains the FU-T20-1 clause, and now points to §9 items 1–3 and 6 |
| `verificationChecklistKeepsItsPartialItemsUnticked` | Items 1–3 and 6 are partial; 4, 5 and 7–11 are ticked; exactly 7 ticked; item 6 must keep `FU-T20-1` |
| `notificationSpecIsBumpedToReadyForImplVersionZeroPointTwo` | Also requires `FU-T20-1` in the header |
| The new test | Pins today's behaviour. The token appears exactly once in each body (email and in-app), always inside `…/verify-email?token=`, and never in the in-app title or link field, the delivery log, or any log line. |

## Rejected

**Kimi F6 — guard that the §11 notes stay dated.**
- The notes are prose under PR review. A regex on "Resolved" plus a date adds maintenance for little protection.
- No change.

**Kimi F7 — Q8.**
- Its premise is wrong. Q8 is already **Resolved (2026-07-20)**. Its "open follow-up" (a repo-root `agents.md`) is an optional platform idea outside this spec.
- No change.

## Self-review items needing no action

S4–S8 were informational. S8, the O6 annotation, wasn't challenged by Kimi, so it stays.

## Files changed in Phase 9

| File | Change |
|---|---|
| `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java` | +1 test; the guard helpers were hardened |
| `services/notification/src/test/java/com/themistra/notification/common/SecretLeakEndToEndIntegrationTest.java` | +1 test |
| `spec/notification-service/package.md` | Header disclosure; §9 items 1 and 6 |
