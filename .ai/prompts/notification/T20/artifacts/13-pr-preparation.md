# notification · T20 · Phase 13 — PR Preparation

Consumes: `artifacts/12-specification-verification.md`. Verdict: **PASS**, so this phase proceeds.

## Branch note

The work is committed phase by phase on `spec/service-specs-and-ai-framework`, the pipeline's established branch. It has not been merged into `main`, so `main` stays deployable.

For a dedicated PR, branch off `main` and cherry-pick T20's change commits:
- `3473097e` (Phase 6)
- `c890e02d` (Phase 9)
- `7bac639f` (Phase 10)
- `5d805c65` (Phase 11)

Notification T07 (`0583d399` and earlier) should land first, because the spec's citations name T07 tests.

## Commit title

`notification: bump spec to READY FOR IMPL 0.2 with disclosed gaps (T20)`

## Commit message

```
notification: bump spec to READY FOR IMPL 0.2 with disclosed gaps (T20)

Record dated resolutions for §11 Q1-Q7 against the shipped code, tick
the §9 items that tests prove, and mark the rest partial with their
reasons and follow-ups. Annotate design.md and requirements.md with
dated notes only; §4c's verbatim blocks are unchanged.

- add the missing R15 named test (reset and verify tokens, end to end,
  including every log line) and the PAYMENT email opt-out E2E from T17
- guard the bump: header rows, §9 citations and partial items, the 20
  §8 named tests (enabled, distinct, R1-R19 + L11), the §4c hash and the
  deviation notes

Partial: merchant-only payment notifications (FU-T07-1), no receipt
link (FU-T07-2, payment FU-T29-1), L2 as interpreted at T07, and the
in-app verify message storing its one-time link (FU-T20-1).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

The prompt template's trailer names an older model. This trailer follows the repo's current commit attribution.

## Files changed

6 files, about +690 / −25.

**Spec:**

| File | Change |
|---|---|
| `spec/notification-service/package.md` | Header, §9, §11 |
| `spec/notification-service/design.md` | L2 note; O1, O2, O4, O5 and O6 resolved; §4c recipients note |
| `spec/notification-service/requirements.md` | R3, R4 and R5 notes |

**Tests:**

| File | Change |
|---|---|
| `services/notification/src/test/java/com/themistra/notification/common/SecretLeakEndToEndIntegrationTest.java` | New, 2 tests |
| `services/notification/src/test/java/com/themistra/notification/delivery/PaymentEmailOptOutIntegrationTest.java` | New, 1 test |
| `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java` | +6 guards |

No production code changed.

## Summary

The notification spec is now `0.2` / `READY FOR IMPL`. Its status names its remaining gaps.

- **Open questions.** Every §11 question has a dated resolution that cites the real classes.
- **Checklist.** §9 is honest: 7 items are ticked with evidence, and 4 are partial with reasons.
- **Named tests.** The missing R15 test now exists. It proves no reset or verification token reaches any log line, title or delivery-log row.
- **Payment opt-out.** T17's payment opt-out case is now tested end to end.
- **Guards.** They make any later silent change to the status, the checklist, the named-test list, §4c's verbatim artifacts or the deviation notes fail the build.

**One new finding.** The in-app email-verification message stores its one-time token inside the link, which password reset deliberately avoids. It is logged as **FU-T20-1**: a version-2 in-app template without the link. A test pins the current behaviour.

## Testing performed

- **Full suite:** `mvn -o -pl services/notification verify` → **422/422, BUILD SUCCESS** (2026-10-19).
- **Isolated containers:** the two new integration tests each run their own Postgres plus `apache/kafka:3.8.0`.
- **Mutations:** 11 deliberate breaks, all killed, and one should-pass control (T9) that passed. Each file was restored byte-identical. The breaks:
  - a token in a log line
  - an ignored opt-out
  - a partial item ticked
  - a §4c fence edited
  - a missing citation
  - the version reverted
  - a disabled §8 test (fully qualified `@Disabled`)
  - a renamed §8 name
  - a §4c note removed
  - "partial" dropped from R5
  - a duplicate §8 target
- **Reviews:**
  - Kimi design challenge: 6 accepted (F6 with corrected class names), 2 rejected.
  - Independent review: 4 accepted, 2 rejected, plus the FU-T20-1 decision.
  - Test review: 2 accepted, 4 rejected as already covered, out of scope, or previously rejected.

## Specification references

| Reference | IDs |
|---|---|
| Task | `spec/notification-service/tasks.md` task 20 |
| Requirements certified | R1–R19 (§9 item 1; R3 and R5 partial); R10 and R15 newly tested end to end |
| LOCKED decisions | L1–L11 (§9 item 2; L2 as interpreted); L4 (R15); L6 (opt-out) |
| Open questions | Q1–Q7 resolved (Q4 partial, Q7 deviating); Q8 already resolved |
| Follow-ups | FU-T07-1, FU-T07-2, FU-T07-4, payment FU-T29-1, **FU-T20-1** (new) |
