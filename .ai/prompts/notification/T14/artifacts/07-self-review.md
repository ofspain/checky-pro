# notification · T14 · Phase 7 — Self Review

Self-review of the Phase 6 implementation against the frozen brief and `agents.md`. Findings only —
no fixes applied here (Phase 9's own job).

## Finding 1 · No upper bound on `maxAttempts`; the backoff formula's bit-shift silently wraps for a pathological value

**Severity:** Medium

**Evidence:**
- `common/config/RetryProperties.java:19` — `@Min(1) int maxAttempts` has no corresponding `@Max`.
- `delivery/RetryScheduler.java:110` — `long uncappedSeconds = (long) retryProperties.initialBackoffSeconds() * (1L << (attemptsAlreadyMade - 1));`

**Issue:** Java's `long` shift operators take the shift distance modulo 64 (JLS 15.19) - for
`attemptsAlreadyMade >= 65`, `1L << (attemptsAlreadyMade - 1)` wraps around to a *small* value
(e.g. `attemptsAlreadyMade=65` → shift amount 64 → `64 mod 64 = 0` → `1L << 0 = 1`) instead of a
huge one. The `Math.min(..., maxBackoffSeconds)` cap immediately after this line assumes the
uncapped value only ever grows, so a wrapped result could silently produce a backoff *shorter* than
intended rather than correctly saturating at `maxBackoffSeconds`. Unreachable at the real configured
value (`max-attempts=5`), but nothing stops a future misconfiguration (e.g. `max-attempts=100`) from
hitting this silently - no exception, no log, just a wrong delay.

**Recommendation:** Either add `@Max(62)` (or similar) to `RetryProperties.maxAttempts`, or make
`computeNextAttemptAt` saturate the exponent itself before shifting (e.g.
`Math.min(attemptsAlreadyMade - 1, 62)`), so an extreme configuration fails loudly or degrades
safely rather than silently.

## Finding 2 · `DeliveryOrchestrator.replay` duplicates `dispatchOneChannel`'s own pre-send guard sequence

**Severity:** Medium

**Evidence:**
- `delivery/DeliveryOrchestrator.java:188` (`dispatchOneChannel`) and `delivery/DeliveryOrchestrator.java:311`
  (`replay`) each independently implement: resolve the template name from the mapping, resolve the
  recipient, check `preferenceResolver.resolve`, render via `TemplateRenderer`, look up the channel
  bean - roughly 25 lines duplicated with only the `save`/`attemptSend` call's own attempt-number
  argument differing.

**Issue:** The frozen brief's own constraint required `attemptSend` (the post-send classification)
to be shared, which it is - but the *pre*-send guard sequence was left duplicated, disclosed at
Phase 2/5 as an accepted trade-off. Two independent copies of the same business logic must now be
kept in lockstep by hand; a future change to one guard (e.g. a new pre-send validation) risks being
applied to only one of the two paths without a compiler or test forcing the other to be updated.

**Recommendation:** Low-risk for now given the small, stable guard sequence, but worth extracting a
shared private helper (e.g. `resolveAndRender(...)` returning either a ready-to-send tuple or an
early `DeliveryOutcome`) if this sequence grows any further in a later task.

## Finding 3 · `RetryScheduler.processOne`'s `switch` over `DeliveryOutcome` has no compiler-enforced exhaustiveness

**Severity:** Low

**Evidence:** `delivery/RetryScheduler.java:89-92` - a `switch` *statement* (not expression) with
two arrow-cases covering all 5 current `DeliveryOutcome` values, no `default` branch.

**Issue:** A switch statement (as opposed to a switch expression used to produce a value) is not
required by javac to be exhaustive - it compiles fine today because the two cases happen to cover
every existing enum constant, but a future 6th `DeliveryOutcome` value added without also updating
this switch would silently fall through doing nothing (the row would never be deleted or
rescheduled, stuck in `delivery_retry` forever) rather than failing to compile or throwing at
runtime.

**Recommendation:** Add an explicit `default -> throw new IllegalStateException("unhandled
DeliveryOutcome: " + outcome)` branch, or convert to a switch *expression* assigned to a local
variable, so the compiler enforces exhaustiveness the next time this enum changes.

## Finding 4 · A real channel send (e.g. live SES) now also runs inside `processOne`'s own open DB transaction

**Severity:** Low (inherited, not new)

**Evidence:** `delivery/RetryScheduler.java:68-69` (`processOne`, `@Transactional`) calls
`DeliveryOrchestrator#replay`, which calls the same `attemptSend` (`delivery/DeliveryOrchestrator.java:257`)
the original dispatch path uses - including a real, synchronous `channelBean.send(...)` call.

**Issue:** T11/T12's own already-disclosed, accepted architectural risk ("a real SES call runs
synchronously inside `DeliveryOrchestrator`'s own open DB transaction, increasing connection-pool
pressure and lock duration under load") now also applies to every replay, not only the original
attempt - the same risk, extended to a second call site, not a new category of problem.

**Recommendation:** No action needed beyond what T11/T12 already accepted; noting it here so a
future revisit of that risk accounts for the replay path too, not only the original one.

## Finding 5 · `RetryScheduler.sweep`'s own due-rows query has no limit/pagination

**Severity:** Low

**Evidence:** `delivery/RetryScheduler.java:55-57` -
`retryRepository.findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(clock.instant())`
returns every due row in one unbounded list.

**Issue:** Under normal operation the `delivery_retry` backlog should stay small (bounded by
`maxAttempts` per failing event), but a sustained outage affecting many accounts simultaneously
could grow it large enough that fetching the entire backlog into memory every sweep interval
becomes a real resource concern.

**Recommendation:** Not a blocker for launch scale: no pagination exists elsewhere in this
codebase's own query methods either, and `agents.md` sets no explicit volume target for this table.
Worth a `Pageable`/batch-size limit if this table's own real-world row count ever becomes large in
practice - not before.

## Open Questions

No blockers. All 5 findings above are either genuine-but-unreachable-at-current-config (Finding 1),
disclosed trade-offs worth re-surfacing (Findings 2, 4), or defensive-robustness suggestions
(Findings 3, 5) - none contradicts a LOCKED decision or `agents.md`.
