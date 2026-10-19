# notification · T20 · Phase 10 — Test Manifest

Consumes:
- `artifacts/04-frozen-task-brief.md`
- `artifacts/09-review-resolution.md`

No production code was changed in this phase.

Verification: `mvn -o -pl services/notification verify` → **422/422, BUILD SUCCESS**. This phase adds 1 guard. Mutations T10 and T11 were both killed and restored byte-identical.

## Acceptance criteria → tests

| AC | Tests |
|---|---|
| AC1: header bump with disclosure | `T01SkeletonRegressionTest.notificationSpecIsBumpedToReadyForImplVersionZeroPointTwo`. It reads the header table only, requires one Version row and one Status row, and requires the disclosure IDs FU-T07-1/2/4, FU-T29-1 and FU-T20-1. Killed T6. |
| AC2: dated Q1–Q7 resolutions | Manual review (Kimi F6, a guard for this, was rejected at Phase 9). The class citations were checked against the source in Phases 6 and 7. |
| AC3: every §9 item ticked with evidence or partial | `everyTestNamedInTheVerificationChecklistExists`: ≥ 25 `Class.method` citations, each an enabled test in its own class (killed T5). `verificationChecklistKeepsItsPartialItemsUnticked`: items 1–3 and 6 partial with their reasons, exactly 7 ticked (killed T3). |
| AC3 (item 1's claim) | `everyTestNamedInTheTestPlanExists`: all 20 §8 names are enabled tests; L11 is a checked `ArchRule` field (killed T7, T8; control T9). |
| AC4: annotations only; §4c unchanged | `verbatimSection4cIsUnchanged` (killed T4). **New** `specKeepsItsDatedDeviationNotesBesideTheTextTheyQualify` (killed T10, T11). |
| AC5: the two new behaviour tests | `SecretLeakEndToEndIntegrationTest.shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs` (R15, killed T1); `SecretLeakEndToEndIntegrationTest.shouldNotLeakTheVerificationTokenIntoRenderedMessagesOrLogs` (pins the FU-T20-1 behaviour); `PaymentEmailOptOutIntegrationTest.shouldSuppressOptedOutPaymentEmailButStillDeliverInApp` (R10/L6, killed T2) |
| AC6: suite green | The full `verify`, 422/422 |
| AC7: no change outside the spec and the test tree | `git diff --stat` for T20's commits: only `spec/notification-service/*` and `services/notification/src/test/**` |

## Added in this phase (gap audit)

**`specKeepsItsDatedDeviationNotesBesideTheTextTheyQualify`**

*Gap.* §9 items 2 and 3 count as "no silent deviation" only because dated notes sit beside the text they qualify. Nothing stopped someone deleting those notes.

*What it pins:*
- the L2 interpretation sits directly under L2
- the §4c "Recipients as shipped" note comes after the mapping, inside §4c, and keeps its three bolded deviations
- R3 and R5 keep their **partial** notes

*Mutations.* T10 renamed the §4c note; it was killed. T11 removed the word "partial" from R5's note; it was killed.

## Mutation summary (Phases 6–10)

All are killed: T1–T8, T10 and T11. T9 is a should-pass control, and it passed.

## Not tested (disclosed)

- **The §11 resolution wording.** The F6 guard was rejected; this is left to manual and PR review.
- **The FU-T20-1 fix itself.** Out of scope. The current behaviour is pinned instead.
- **R15's "full API keys".** No consumed event carries one; T10's `SecretSafeLoggingTest.redactMasksApiKeyAndApiUnderscoreKeyParameters` covers redaction.
