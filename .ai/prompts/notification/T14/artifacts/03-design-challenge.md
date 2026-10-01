<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T14 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T14 — Bounded retry + dead-letter |
| **Spec section** | Retry / dead-letter |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the T14 Phase 2 brief.

---

## Finding 1 · `DeliveryLog` must be modified to accept a variable `attempt`, but the brief does not list it

**Severity:** High

**Evidence:**
- AC4 requires every replay to append a new `delivery_log` row with "the correct, real `attempt` number" and explicitly says it is "no longer hardcoded to `1`."
- The current `DeliveryLog` constructor hardcodes `this.attempt = 1` and takes no attempt parameter.
- The brief's "Files to Modify" list does not include `delivery/DeliveryLog.java`.

**Issue:** If `DeliveryLog` is not modified, retries cannot record incremented attempt numbers. The brief is unimplementable as written.

**Recommended brief amendment:**
Add `delivery/DeliveryLog.java` to "Files to Modify." Specify the constructor change: accept `short attempt` as a parameter (or add a second constructor/factory) and remove the hardcoded `1`. Update all existing call sites in `DeliveryOrchestrator` to pass `(short) 1` for initial attempts.

---

## Finding 2 · `event_data_json` will store raw secrets/tokens in `delivery_retry`

**Severity:** High

**Evidence:**
- `eventDataJson` is serialized from the same `Map<String,String>` that contains `"token"` for `verify_email`/`password_reset`.
- The brief says use Jackson's `ObjectMapper` to serialize/deserialize it.
- L4/R15 forbid secrets in messages or logs, but `delivery_retry` is a persistent DB table, not a log.

**Issue:** The raw verification/reset token will sit in `delivery_retry.event_data_json` until the row is deleted. If the DB is compromised or audited, the token is exposed. This is a real security consideration, even if functionally necessary for re-rendering the link.

**Recommended brief amendment:**
Explicitly acknowledge and document that `event_data_json` contains the same raw token as the original Kafka event and the rendered message body, and that the row lifetime is bounded by retry completion. Consider whether encryption at rest is required (likely out of scope for launch, but should be a disclosed risk). Ensure `DeliveryRetry.toString()` never logs `eventDataJson`.

---

## Finding 3 · The retry path's preference re-check behavior is unspecified

**Severity:** Medium

**Evidence:**
- The original `dispatchOneChannel` checks `preferenceResolver.resolve(...)` before rendering/sending.
- A retry replays the failed channel. Between the original attempt and the retry, the recipient could disable that channel/category.
- The brief does not say whether the replay should re-check preferences or honor the original decision.

**Issue:** If preferences are re-checked and the user has opted out, the retry would record `SUPPRESSED` and delete the retry row — which may be correct, but is surprising because the original failure happened on an enabled channel. If preferences are not re-checked, the retry may send to a channel the user now wants disabled.

**Recommended brief amendment:**
Specify the behavior: re-check preferences on replay; if the channel is now disabled, record `SUPPRESSED` and delete the retry row. This respects the user's current preferences and is consistent with the safe-default principle. Add a test for this scenario.

---

## Finding 4 · The replay method's contract (return vs. throw) is unspecified

**Severity:** Medium

**Evidence:**
- The brief says `RetryScheduler` calls "a new package-visible `DeliveryOrchestrator` method the scheduler calls to actually replay one channel's send."
- It does not say whether that method returns an outcome, returns void and lets exceptions propagate, or returns a result object.

**Issue:** The scheduler needs to know whether the replay succeeded, failed transiently, or failed permanently. Without a clear contract, the scheduler cannot decide whether to delete, reschedule, or dead-letter the retry row.

**Recommended brief amendment:**
Define the replay method signature, e.g.:
```java
DeliveryOutcome replay(UUID accountUuid, String channel, String notificationKind,
                       String sourceEventKey, Map<String,String> eventData, int attempt)
```
where `DeliveryOutcome` is an enum (`SENT`, `TRANSIENT_FAILURE`, `PERMANENT_FAILURE`). The method should catch channel exceptions internally, classify them, save the appropriate `delivery_log` row, and return the outcome. The scheduler then decides what to do with the `delivery_retry` row.

---

## Finding 5 · The `attempt` numbering and backoff indexing are ambiguous

**Severity:** Medium

**Evidence:**
- The brief says `delivery_retry` stores "the most recent `attempt` number."
- AC2 says backoff for attempt *n* is `min(initialBackoffSeconds * 2^(n-1), maxBackoffSeconds)`.
- It is unclear whether the `attempt` column in `delivery_retry` is the number of attempts already made or the next attempt number.

**Issue:** If the column means "already made attempts," the first retry row stores `1` and the next delay is `initialBackoffSeconds * 2^1` = double the intended initial delay. If it means "next attempt number," the first retry row stores `2` and the delay is `initialBackoffSeconds * 2^1` = also double. Either way, the formula needs careful mapping to the stored value.

**Recommended brief amendment:**
Pin the semantics. A clean choice:
- `delivery_retry.attempt` = the attempt number that just failed (i.e., the number of attempts already made).
- The scheduler computes `nextAttempt = attempt + 1`, delay = `min(initialBackoffSeconds * 2^(attempt), maxBackoffSeconds)`.
- The `delivery_log` row written for the replay uses `nextAttempt`.

Document this mapping and add tests for the first few attempts and the cap.

---

## Finding 6 · The transient/permanent classification rule may be too coarse

**Severity:** Medium

**Evidence:**
- The proposed rule: `IllegalArgumentException` from `channelBean.send` is permanent; every other exception is transient.
- `EmailChannel` throws `IllegalArgumentException` for null/blank recipient or subject/body.
- `EmailTransport.send` throws `EmailDeliveryException` wrapping AWS SDK errors. Some AWS errors are permanent (e.g., `MessageRejected` for unverified identity, `MailFromDomainNotVerified`), while others are transient (throttling, network).

**Issue:** A permanent AWS error (unverified sender) will be retried `maxAttempts` times before dead-lettering, wasting resources and delaying the terminal state. Conversely, if a future channel throws `IllegalStateException` for a permanent config error, it will be retried.

**Recommended brief amendment:**
Either:
1. Keep the simple rule but document it as a launch-scale simplification, accepting that some permanent AWS errors will be retried, or
2. Refine the rule: `IllegalArgumentException` and a curated list of known permanent `EmailDeliveryException` error codes (e.g., `MessageRejected`, `MailFromDomainNotVerified`) are permanent; everything else is transient.

Option 1 is simpler and sufficient for launch; option 2 is more accurate but adds complexity. Whichever is chosen, add tests for both shapes.

---

## Finding 7 · What happens when `event_data_json` cannot be deserialized is unspecified

**Severity:** Medium

**Evidence:**
- The scheduler deserializes `event_data_json` with `ObjectMapper`.
- A corrupted row (manual DB edit, schema mismatch, bug) could produce an `IOException`/`JsonProcessingException`.
- AC9 says a single row's failure must not abort the sweep, but it does not say what happens to the poison row.

**Issue:** A poison row would cause the scheduler to fail that row on every sweep, never deleting it, potentially spamming logs and never resolving.

**Recommended brief amendment:**
Specify poison-pill handling: on deserialization failure, log an error, write a `FAILED` or `DEAD_LETTERED` `delivery_log` row, and delete the `delivery_retry` row. This prevents an unprocessable row from living forever. Add a test.

---

## Finding 8 · The scheduled method's thread-pool configuration is unspecified

**Severity:** Low

**Evidence:**
- `@Scheduled` methods run on Spring's task scheduler thread pool.
- The default pool size is 1. A long sweep (many due rows) would block any other scheduled tasks.
- There are no other scheduled tasks today, but this could change.

**Issue:** Without explicit configuration, the scheduler is not isolated from future scheduled tasks.

**Recommended brief amendment:**
Add `spring.task.scheduling.pool.size` to `application.properties` (e.g., `2` or `3`) and document that the retry scheduler is the only scheduled task at launch. Alternatively, configure a dedicated `TaskScheduler` bean for retry sweeps.

---

## Finding 9 · The `delivery_retry.created_at` column is unused by the brief

**Severity:** Low

**Evidence:**
- `delivery_retry` has `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` from V1.
- The brief does not mention populating or using it.

**Issue:** If the entity does not set `created_at`, JPA/Hibernate may still use the DB default. But `ddl-auto=validate` might require the entity to map it. Leaving it unmapped could cause validation issues.

**Recommended brief amendment:**
Specify whether `DeliveryRetry` entity maps `createdAt` (read-only, set by DB default) or leaves it unmapped. If unmapped, ensure `ddl-auto=validate` accepts an unmapped column (it does, since Hibernate validates mapped columns, not the absence of columns). Either way, document the choice.

---

## Finding 10 · Whether the scheduler processes rows in any particular order is unspecified

**Severity:** Low

**Evidence:**
- `DeliveryRetryRepository.findByNextAttemptAtLessThanEqual` returns rows in undefined order.
- The brief says rows are processed sequentially but does not specify ordering.

**Issue:** Without an order, a backlog of due rows could be processed arbitrarily, making tests non-deterministic and making it hard to reason about fairness.

**Recommended brief amendment:**
Specify ordering: `ORDER BY next_attempt_at ASC, id ASC` (oldest first, FIFO tie-break). Update the repository method name to `findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc`. Add a test proving order.

---

## Summary

The T14 brief correctly scopes bounded retry through the existing channel seam, append-only logging, and ShedLock-guarded scheduling. The most critical gap is **Finding #1: `DeliveryLog` must be modified but is not listed**, making the brief unimplementable as written. **Finding #2** raises a real security consideration about storing raw tokens in `delivery_retry`. Findings #3–7 are medium-severity ambiguities in replay semantics, attempt numbering, classification, and poison-pill handling. Findings #8–10 are smaller operational details.
