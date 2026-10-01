<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T14 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T14 — Bounded retry + dead-letter |
| **Spec section** | Retry / dead-letter |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T14 implementation. Findings only.

---

## Finding 1 · `DeliveryRetry.attempt` and `RetryProperties.maxAttempts` are not bounded to `short` range

**Severity:** Medium

**Issue:** `DeliveryRetry.attempt` is a `short` and `DeliveryLog.attempt` is a `short`. `RetryProperties.maxAttempts` is an `@Min(1) int` with no `@Max`. A configuration value above `Short.MAX_VALUE` would cause the scheduler to compute `newAttempt` values that overflow when cast to `short`, corrupting the `delivery_retry` and `delivery_log` rows.

**Evidence:**
- `common/config/RetryProperties.java` line 19: `@Min(1) int maxAttempts`.
- `delivery/DeliveryRetry.java` line 52: `private short attempt;`.
- `delivery/RetryScheduler.java` line 98: `short newAttempt = (short) (retry.getAttempt() + 1);`.
- `delivery/DeliveryOrchestrator.java` line 313: `short attemptNumber = (short) (attemptsAlreadyMade + 1);`.

**Recommendation:** Add `@Max(Short.MAX_VALUE)` to `RetryProperties.maxAttempts` (or a smaller operational cap like 62, which also closes the bit-shift Finding #1). Document the cap.

---

## Finding 2 · `RetryScheduler.computeNextAttemptAt` bit-shift wraps for pathological `attemptsAlreadyMade`

**Severity:** Medium

**Issue:** `(1L << (attemptsAlreadyMade - 1))` takes the shift distance modulo 64. For `attemptsAlreadyMade >= 65`, the result wraps to a small value, so the backoff cap may be applied to a *smaller*-than-initial delay instead of correctly saturating at `maxBackoffSeconds`.

**Evidence:**
- `delivery/RetryScheduler.java` line 110: `long uncappedSeconds = (long) retryProperties.initialBackoffSeconds() * (1L << (attemptsAlreadyMade - 1));`.
- `RetryProperties.maxAttempts` has no `@Max`, so a misconfiguration (e.g., 100) could theoretically hit this.

**Recommendation:** Cap the exponent before shifting, e.g.:
```java
int exponent = Math.min(attemptsAlreadyMade - 1, 62);
long uncappedSeconds = (long) retryProperties.initialBackoffSeconds() * (1L << exponent);
```
Or add `@Max(62)` to `maxAttempts`. Either way, the behavior should degrade safely rather than silently producing a short delay.

---

## Finding 3 · `DeliveryOrchestrator.replay` does not re-resolve `displayName`

**Severity:** Medium

**Issue:** The original `dispatch` path resolves `displayName` from `ContactProjectionUpdater.findDisplayName` and merges it into `renderData` before rendering. `replay` uses the `eventDataJson` captured at the time of the original failure, which does include whatever `displayName` was present then — but if the projection is updated between the original attempt and the retry, the retry will use stale (or no) display name, while the original path would have used the updated one. More importantly, the code re-resolves `email` on replay but not `displayName`, creating an inconsistency in how fresh the two recipient-projection fields are.

**Evidence:**
- `delivery/DeliveryOrchestrator.java` lines 152–161: original dispatch resolves and merges `displayName`.
- Lines 311–355: `replay` re-resolves `email` (line 323) but never calls `findDisplayName`.

**Recommendation:** Either:
1. Resolve `displayName` in `replay` and merge it into `eventData` before rendering (mirroring the original dispatch), or
2. Document that retries intentionally use the recipient/display state captured at the original attempt.

Option 1 is recommended for consistency.

---

## Finding 4 · `DeliveryOrchestrator.replay` lacks the `templateName == null` guard present in `dispatchOneChannel`

**Severity:** Low

**Issue:** `dispatchOneChannel` skips a channel entirely if the mapping has no template for it (`if (templateName == null) { return; }`). `replay` does not have this guard, so a `null` template name would be passed to `TemplateRenderer.render`, throwing `NullPointerException` from `Objects.requireNonNull`. The `catch (Exception e)` around render would convert it to a `FAILED` row and `PERMANENT_FAILURE`, deleting the retry row — functionally safe, but inconsistent with the original path and producing a spurious `FAILED` row.

**Evidence:**
- `delivery/DeliveryOrchestrator.java` line 192: guard in `dispatchOneChannel`.
- Lines 321–340: `replay` resolves `templateName` but does not check for null before rendering.

**Recommendation:** Add the same `if (templateName == null) { return PERMANENT_FAILURE; }` guard in `replay`, or document why it is intentionally absent (all current mappings have both templates).

---

## Finding 5 · `RetryScheduler.processOne` switch is not compiler-enforced exhaustive

**Severity:** Low

**Issue:** A switch *statement* with arrow cases covering all current enum constants compiles today, but a future 6th `DeliveryOutcome` value would silently fall through, leaving the `delivery_retry` row neither deleted nor rescheduled — stuck forever.

**Evidence:**
- `delivery/RetryScheduler.java` lines 89–102: switch statement with no `default`.

**Recommendation:** Convert to a switch *expression* (which the compiler requires to be exhaustive) or add an explicit `default -> throw new IllegalStateException("unhandled outcome: " + outcome)` branch.

---

## Finding 6 · `DeliveryOrchestrator.replay` duplicates the pre-send guard sequence

**Severity:** Medium

**Issue:** As noted in the self-review, `replay` independently re-implements template-name resolution, recipient resolution, preference check, render, and channel-bean lookup — roughly 25 lines duplicated from `dispatchOneChannel`. A future change to one guard risks diverging from the other.

**Evidence:**
- `delivery/DeliveryOrchestrator.java` lines 188–225 (`dispatchOneChannel`) and lines 311–355 (`replay`).

**Recommendation:** Extract a shared private helper that resolves and renders (or returns an early outcome) given account/channel/notification kind/event data. This reduces duplication and makes the two paths inherently consistent. Not a correctness blocker for launch.

---

## Finding 7 · `RetryScheduler.sweep` fetches every due row without a limit

**Severity:** Low

**Issue:** The due-rows query is unbounded. A sustained outage across many accounts could produce a backlog large enough to cause memory pressure or a long single-replica sweep.

**Evidence:**
- `delivery/RetryScheduler.java` lines 56–57: `findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(clock.instant())` with no `Pageable`.

**Recommendation:** Add a `Pageable.ofSize(...)` limit (e.g., 100 or 500 rows per sweep) and document the batching behavior. This is a launch-scale robustness improvement, not a blocker.

---

## Finding 8 · A real channel send runs synchronously inside `RetryScheduler.processOne`'s open transaction

**Severity:** Low

**Issue:** As noted in the self-review, `processOne` is `@Transactional` and calls `DeliveryOrchestrator.replay`, which synchronously calls `channelBean.send`. This holds a DB connection open for the duration of an external SES or in-app DB write, just like the original dispatch path. The risk is now extended to replays.

**Evidence:**
- `delivery/RetryScheduler.java` line 68: `@Transactional` on `processOne`.
- `delivery/DeliveryOrchestrator.java` line 354: `replay` calls `attemptSend`, which calls `channelBean.send`.

**Recommendation:** Document as an accepted architectural consequence of T11's design (as T11/T12 did). A real fix would require moving the external send outside the DB transaction, which is out of T14's scope.

---

## Finding 9 · `RetryScheduler.sweep` does not limit the scheduled method's own execution time

**Severity:** Low

**Issue:** A very large due backlog could cause a single sweep to exceed the ShedLock `lockAtMostFor` value (`5m`), allowing another replica to acquire the lock while the first sweep is still running.

**Evidence:**
- `delivery/RetryScheduler.java` line 54: `lockAtMostFor = "5m"`.
- Line 55–65: `sweep()` iterates all due rows with no internal timeout.

**Recommendation:** Either reduce the batch size (Finding #7) so sweeps complete well under 5 minutes, or increase `lockAtMostFor` to a value comfortably above the worst-case batch processing time. Document the expected sweep duration.

---

## Finding 10 · `event_data_json` stores raw tokens in `delivery_retry`

**Severity:** Low (acknowledged, bounded lifetime)

**Issue:** As noted in the Phase 3 design challenge, `eventDataJson` contains the raw verification/reset token. The implementation acknowledges this in `DeliveryRetry`'s Javadoc and uses `Object`'s default `toString()` to prevent accidental logging. This is acceptable for launch but is a standing security surface.

**Evidence:**
- `delivery/DeliveryRetry.java` lines 22–26: Javadoc acknowledges raw token persistence.
- `delivery/DeliveryOrchestrator.java` line 290: `objectMapper.writeValueAsString(eventData)` includes the token.

**Recommendation:** No code change needed for launch, but ensure the runbook / ADR documents that `delivery_retry` rows are short-lived and contain the same secrets as the rendered message body. Consider encryption at rest if a future threat model requires it.

---

## Summary

The T14 implementation correctly delivers bounded retry and dead-lettering: `DeliveryLog` now accepts a real attempt number, `DeliveryRetry` captures everything needed for replay, `RetryScheduler` is ShedLock-guarded and processes rows per-transaction, the replay path re-uses `attemptSend` and re-checks preferences, and poison-pill rows are dead-lettered. The schema and grant migrations are present, and `RetryProperties` includes the new scheduler interval with cross-field validation.

The most important remaining issues are **Finding #1** (`maxAttempts` not capped to the `short` columns), **Finding #2** (bit-shift wrap for extreme `attemptsAlreadyMade`), and **Finding #3** (replay not re-resolving `displayName`). Findings #4–10 are smaller consistency, robustness, or documentation items. The self-review findings are all reproduced here with consistent severity and recommendations.
