# notification · T14 · Phase 9 — Review Resolution

**Human Approval gate.** Resolution log for the Phase 7 self-review (5 findings) and Phase 8
independent review (10 findings, 3 of which — #1, #3, #4 — were genuinely new, not already raised
at Phase 7; the remaining 7 restate or extend the same 5 self-review findings). Each comment below
is dispositioned, then the exact change made. No refactoring, no public-API changes, no renames
beyond what a disposition required.

## Phase 8 Finding 1 · `DeliveryRetry.attempt`/`DeliveryLog.attempt` are `short`, `maxAttempts` has no `@Max`

**ACCEPTED.** A `maxAttempts` configured above `Short.MAX_VALUE` would corrupt the persisted
`attempt` columns via silent `short` overflow.

**Change:** `common/config/RetryProperties.java` — `maxAttempts` gains `@Max(62)` (the same bound
closes Phase 8 Finding #2 below in one change — see its own disposition for why 62). New tests:
`RetryPropertiesTest.failsWhenMaxAttemptsExceedsSixtyTwo`,
`RetryPropertiesTest.succeedsWhenMaxAttemptsEqualsSixtyTwo`.

## Phase 8 Finding 2 · `RetryScheduler.computeNextAttemptAt`'s bit-shift wraps for pathological `attemptsAlreadyMade` (= Self-Review Finding 1)

**ACCEPTED.** Confirmed via Java's own shift semantics (JLS 15.19 - shift distance taken modulo 64):
`1L << (attemptsAlreadyMade - 1)` wraps to a small value at `attemptsAlreadyMade >= 65`, which would
let `Math.min(..., maxBackoffSeconds)` select a too-small, wrapped value instead of correctly
saturating.

**Change:** Rather than defensively capping the exponent inside `computeNextAttemptAt` itself (one
of the two options both reviews offered), the same `@Max(62)` on `RetryProperties.maxAttempts`
(Finding #1's own change) closes this at the root: 62 is comfortably below the 64-bit shift boundary,
so the wraparound condition can no longer be configured into existence. Chosen over the
belt-and-braces alternative (capping both the config *and* the arithmetic) as the simpler of two
correct fixes - the config bound is the actual root cause barrier, and duplicating the defense in
the arithmetic too would be the kind of speculative extra layer this codebase has consistently
avoided elsewhere. Documented in `RetryProperties`'s own updated Javadoc.

## Phase 8 Finding 3 · `DeliveryOrchestrator.replay` does not re-resolve `displayName`

**ACCEPTED.** New finding, not raised at Phase 7. Confirmed directly: the original `dispatch` path
calls `contactProjectionUpdater.findDisplayName` and merges it into `renderData`; `replay` re-resolved
`email` but reused the original `eventData` unchanged, leaving its own rendered content reflecting
whichever (always-null today, but not necessarily so in the future) `displayName` snapshot the
original attempt happened to capture - a real inconsistency between how fresh the two
recipient-projection fields are on replay.

**Change:** `delivery/DeliveryOrchestrator.java` - `replay` now calls
`contactProjectionUpdater.findDisplayName(accountUuid)` and merges it into a fresh `renderData` map
(copied from `eventData`) before calling `templateRenderer.render`, mirroring `dispatch`'s own
identical merge exactly. New test: `DeliveryOrchestratorTest.replayResolvesDisplayNameFreshAndMergesItIntoRenderData`.

## Phase 8 Finding 4 · `DeliveryOrchestrator.replay` lacks the `templateName == null` guard `dispatchOneChannel` has

**ACCEPTED.** New finding, not raised at Phase 7. Confirmed: `dispatchOneChannel` returns immediately
if `templateName` is `null`; `replay` had no equivalent guard, so a null template would reach
`TemplateRenderer.render` and throw - caught by the existing `catch (Exception e)`, converted to a
`FAILED` row and `PERMANENT_FAILURE`, so functionally safe today, but inconsistent with the original
path's own silent skip. Unreachable via any real `notificationKind` today (confirmed: all 7
`NOTIFICATION_MAPPINGS` entries supply both templates; a retry row for a channel could only exist if
the original attempt already passed this exact check for that same, static mapping entry) - added
for consistency against a future mapping that might omit one channel's template.

**Change:** `delivery/DeliveryOrchestrator.java` - `replay` gains the identical
`if (templateName == null) { return DeliveryOutcome.PERMANENT_FAILURE; }` guard, placed in the same
position (immediately after resolving `templateName`, before recipient resolution) as
`dispatchOneChannel`'s own. New structural test (behaviorally unreachable, per above):
`DeliveryOrchestratorTest.replayGuardsAgainstANullTemplateNameMirroringDispatchOneChannel`.

## Phase 8 Finding 5 · `RetryScheduler.processOne`'s `switch` is not compiler-enforced exhaustive (= Self-Review Finding 3)

**ACCEPTED.**

**Change:** `delivery/RetryScheduler.java` - added `default -> throw new IllegalStateException("unhandled
DeliveryOutcome: " + outcome);` to the switch statement. New structural test (behaviorally
unreachable - `DeliveryOutcome` has exactly 5 real values, none fabricable via Mockito):
`RetrySchedulerTest.processOneSwitchHasADefaultArmGuardingAFutureUnhandledOutcome`.

## Phase 8 Finding 6 · `replay` duplicates `dispatchOneChannel`'s own pre-send guard sequence (= Self-Review Finding 2)

**ACCEPTED-DOCUMENTED, no structural change.** Both reviews independently flagged the same real
duplication and both explicitly called it "not a correctness blocker for launch." Extracting a
shared helper now would be a larger, riskier change than this phase's own narrow fix-only scope
permits (the Phase 9 prompt's own guardrail: "do not refactor, do not optimize") - especially since
Findings #3/#4's own fixes just now made `replay`'s own guard sequence *more* different from
`dispatchOneChannel`'s (the displayName merge; the explicit early-return shape), reinforcing that a
clean, risk-free extraction is a larger undertaking better suited to its own future task, not a
same-phase addition on top of three other fixes. No code change; this resolution log itself is the
documentation.

## Phase 8 Finding 7 · `RetryScheduler.sweep`'s due-rows query has no limit/pagination (= Self-Review Finding 5)

**ACCEPTED-DOCUMENTED, no code change.** Both reviews agree this is a launch-scale non-blocker. No
pagination exists anywhere else in this codebase's own query methods either, and `agents.md` sets no
explicit volume target for this table. Revisit if this table's real-world row count ever grows large
in practice.

## Phase 8 Finding 8 · A real channel send runs inside `processOne`'s open transaction (= Self-Review Finding 4)

**ACCEPTED-DOCUMENTED, no code change.** Both reviews agree this is T11/T12's own already-accepted
architectural risk, now correctly recognized as extending to the replay path too. A real fix would
mean revisiting T11's own frozen transaction boundary - out of this task's scope.

## Phase 8 Finding 9 · `sweep`'s own execution time is not bounded against `lockAtMostFor`

**ACCEPTED-DOCUMENTED, no code change.** A real, legitimate operational concern, but speculative at
today's expected backlog size (Finding #7's own disposition applies identically - no real volume
data yet to size either the batch limit or the lock duration against). Revisit together with
Finding #7 if real operational data ever shows sweeps approaching the 5-minute ceiling.

## Phase 8 Finding 10 · `event_data_json` stores raw tokens in `delivery_retry`

**ACCEPTED-DOCUMENTED, no code change.** Already disclosed at Phase 3/4 and in `DeliveryRetry`'s own
Javadoc; Kimi's own text explicitly says "no code change needed for launch." No further action.

## Summary

Five real code/test changes applied: `RetryProperties.maxAttempts` gains `@Max(62)` (closes
Findings #1 and #2 together); `DeliveryOrchestrator.replay` gains a fresh `displayName` resolution
(Finding #3) and a `templateName == null` guard (Finding #4); `RetryScheduler.processOne`'s switch
gains a `default` throw (Finding #5). Five findings (#6-#10) are documented, accepted risks or
disclosed trade-offs with no code change, each with a stated reason. Full suite:
`mvn -pl services/notification clean verify` — 346 tests, 0 failures, 0 errors (341 + 5 new: 2 in
`RetryPropertiesTest`, 2 in `DeliveryOrchestratorTest`, 1 in `RetrySchedulerTest`).
