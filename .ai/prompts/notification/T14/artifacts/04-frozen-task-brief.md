STATUS: FROZEN

# notification · T14 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 10 findings verified directly against actual source (`DeliveryLog.java`'s own hardcoded
constructor, `AuthEventConsumer.java`'s own `Map.of("token", event.token(), ...)` call site,
`EmailDeliveryException`/`SesEmailTransport`'s own sanitized-message shape) before disposition —
every one is real, not overstated. All 10 are **ACCEPTED**; none rejected. Several required an
explicit engineering judgment call beyond Kimi's own literal suggestion, recorded below.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | `DeliveryLog` hardcodes `attempt=1`, no parameter | High | **ACCEPTED** | `DeliveryLog`'s constructor gains a `short attempt` parameter, replacing the hardcoded literal. `DeliveryLog.java` added to Files to Modify. Every existing call site in `DeliveryOrchestrator.save(...)` passes an explicit attempt value (`1` for the original dispatch path, the real attempt number for a replay). |
| 2 | `event_data_json` persists the raw one-time token | High | **ACCEPTED, disclosed risk** | Documented explicitly (in `DeliveryRetry`'s own Javadoc) that `event_data_json` carries the identical raw token already present in the original Kafka event and the rendered message body — not a new exposure class, but a new *persistence surface* with a bounded lifetime (deleted on success, dead-letter, or permanent failure). `DeliveryRetry.toString()` (if one is ever written) must never include `eventDataJson`, mirroring `EmailMessage`'s/`EmailChannel`'s own T12 precedent. Encryption-at-rest is explicitly OUT of this task's own scope — it is a DB/infra-level control (mirrors L10's own "External Secrets Operator handles secrets injection, the app doesn't re-implement secret storage" boundary), not an application-code gap this task must close. |
| 3 | Preference re-check on replay unspecified | Medium | **ACCEPTED**, exactly as recommended | The replay path re-checks `PreferenceResolver.resolve` before re-rendering/re-sending. If the channel is now disabled, the replay writes `SUPPRESSED` and deletes the `delivery_retry` row — no further retry, no `DEAD_LETTERED` (the user's own current preference, not exhaustion, is why it stopped). |
| 4 | Replay method's contract (return vs. throw) unspecified | Medium | **ACCEPTED**, refined beyond Kimi's own 3-value proposal | `DeliveryOrchestrator` gains a package-visible `replay(...)` method returning a 4-value `DeliveryOutcome` enum: `SENT`, `PERMANENT_FAILURE`, `TRANSIENT_FAILURE` (retry again), `TRANSIENT_EXHAUSTED` (this attempt was transient but was also the last one allowed — `replay` itself already wrote the terminal `DEAD_LETTERED` row). `replay` never throws — it catches internally, classifies, writes exactly one new `delivery_log` row, and returns the outcome; `RetryScheduler` only ever decides the `delivery_retry` row's own fate (delete vs. reschedule) from the returned value, never writes `delivery_log` itself. |
| 5 | Attempt numbering / backoff indexing ambiguous | Medium | **ACCEPTED**, semantics pinned (see "Pinned semantics" below) | `delivery_retry.attempt` = the number of attempts already made for this channel/event. The exhaustion check (`attemptNumber >= maxAttempts`) is evaluated on **every** attempt, including the very first (dispatchOneChannel's own original call) — a `maxAttempts=1` configuration must dead-letter on the first transient failure with no retry row ever inserted, not schedule a retry it would immediately exhaust. |
| 6 | Transient/permanent classification may be too coarse (permanent AWS errors retried) | Medium | **ACCEPTED, Option 1 chosen** (documented simplification, Kimi's own Option 2 explicitly REJECTED) | `IllegalArgumentException` = permanent; every other exception = transient — unchanged from the Phase 2 proposal. Kimi's own Option 2 (a curated list of permanent AWS error codes) is rejected: it requires string-matching an already-sanitized `EmailDeliveryException` message (fragile — the message shape is `SesEmailTransport`'s own internal formatting detail, not a stable contract) and leaks an email-specific concern into `DeliveryOrchestrator`'s channel-agnostic classifier (against L5's own spirit). Documented as a disclosed, accepted limitation: a permanent SES error (e.g. an unverified sender identity) will exhaust the full retry budget before dead-lettering, rather than dead-lettering immediately — R12/R13 only require bounded termination, not an immediately-optimal one. Revisit only if this proves costly in a real environment. |
| 7 | Poison-pill `event_data_json` deserialization failure unhandled | Medium | **ACCEPTED**, exactly as recommended | A `JsonProcessingException` while deserializing a due row's own `event_data_json` is caught inside that row's own per-row try/catch (AC9): writes a `DEAD_LETTERED` `delivery_log` row (a corrupted row can never be meaningfully retried) and deletes the `delivery_retry` row, logged at `ERROR` through the existing `SecretSafeLogging`-redacted path. |
| 8 | Scheduled method's thread-pool sizing unspecified | Low | **ACCEPTED, documented not configured** (Kimi's own literal "add `spring.task.scheduling.pool.size`" REJECTED as premature) | `RetryScheduler` is, today, the *only* scheduled task in this service — sizing a dedicated pool now would be speculative configuration for a contention problem that cannot yet occur (YAGNI, consistent with this codebase's own repeated rejection of speculative config elsewhere, e.g. T12's rejected fake-`InAppChannel`-variant). Documented explicitly in `RetryScheduler`'s own Javadoc and a one-line `application.properties` comment: the default single-thread scheduler pool is adequate only while this remains the sole scheduled job; whichever future task adds a second one must revisit this. |
| 9 | `delivery_retry.created_at` unused/unmapped | Low | **ACCEPTED**, mapped explicitly | `DeliveryRetry` maps `createdAt` as a real constructor parameter set from the injected `Clock` at insertion time — mirrors `DeliveryLog`'s/`InappNotification`'s own identical established convention (every entity in this module sets its own timestamp from `Clock`, never a DB-side default), chosen over Kimi's own "leave it unmapped" alternative for consistency and because a `Clock`-sourced value is deterministically testable; a DB-side `now()` default is not. |
| 10 | Scheduler's own row-processing order unspecified | Low | **ACCEPTED**, exactly as recommended | `DeliveryRetryRepository.findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc` — oldest-due-first, `id` as a deterministic tie-break. |

### Pinned semantics (resolving Findings #4/#5 together)

- **`delivery_log.attempt`** — the real, 1-indexed attempt number of that specific row (`1` for the
  original attempt, `2` for the first retry, etc.) — no longer hardcoded.
- **`delivery_retry.attempt`** — the number of attempts already made for this channel/event
  (identical value to the most recent `delivery_log.attempt` written for it).
- **Backoff formula**: for an attempt count of `n` already made, the next attempt's delay is
  `min(initialBackoffSeconds * 2^(n-1), maxBackoffSeconds)` — so the very first retry (after 1
  attempt already made) waits exactly `initialBackoffSeconds`, doubling on each subsequent failure,
  capped at `maxBackoffSeconds`.
- **Exhaustion check**: before scheduling *any* retry (including from the very first, original
  attempt), compute `nextAttemptNumber = attemptsAlreadyMade + 1`. If
  `nextAttemptNumber > maxAttempts`, this failure is terminal: write `DEAD_LETTERED` directly (no
  `delivery_retry` row is ever inserted/kept). Otherwise write `FAILED` and insert/update the
  `delivery_retry` row with the new backoff delay. This correctly handles the degenerate
  `maxAttempts=1` case: a transient failure on the first-ever attempt dead-letters immediately, since
  there is no room for even one retry.
- **`replay`'s own internal logic** (called once per due row, with `attemptsAlreadyMade` from that
  row): re-checks preferences (Finding #3) → re-renders via `TemplateRenderer` → calls the channel →
  classifies the result using the same `IllegalArgumentException`-is-permanent rule and the same
  exhaustion check described above → writes exactly one `delivery_log` row → returns the
  `DeliveryOutcome` enum value. `RetryScheduler` deletes the `delivery_retry` row for `SENT`,
  `PERMANENT_FAILURE`, and `TRANSIENT_EXHAUSTED`; updates it (new attempt count, new
  `next_attempt_at`) only for `TRANSIENT_FAILURE`.

## Task

Unchanged from Phase 2, with all 10 Phase 3 dispositions folded in: implement `DeliveryRetry`/
`DeliveryRetryRepository`/`RetryScheduler`, extend `DeliveryOrchestrator` with a shared attempt-
classification path (used by both the original dispatch and the new `replay` method) and a
`DeliveryLog` constructor that accepts a real attempt number.

## Scope

**In (unchanged from Phase 2, plus the Phase 3 amendments):**
- `delivery/DeliveryLog.java` — constructor gains `short attempt` (Finding #1).
- `delivery/DeliveryRetry.java` / `DeliveryRetryRepository.java` — `createdAt` mapped from `Clock`
  (Finding #9); repository query ordered (Finding #10).
- `delivery/RetryScheduler.java` — ShedLock-guarded; per-row `try/catch` including JSON-deserialization
  poison-pill handling (Finding #7); documents the single-scheduled-task thread-pool note
  (Finding #8).
- `DeliveryOrchestrator` — shared classification logic (pinned semantics above), new `replay(...)`
  method (Finding #4) that re-checks preferences (Finding #3) before re-rendering/re-sending.
- `delivery_retry` schema amendment (`notification_kind`, `event_data_json` columns) — unchanged
  from Phase 2, now with `DeliveryRetry`'s own Javadoc explicitly disclosing the raw-token exposure
  (Finding #2).
- `RetryProperties` gains `schedulerIntervalSeconds` — unchanged from Phase 2.
- ShedLock starter + JDBC provider dependency; `@EnableScheduling`/`@EnableSchedulerLock`.

**Out:** Unchanged from Phase 2 — no channel-specific retry logic, no separate `dead_letter`
table/topic, no retroactive retry of any pre-existing `FAILED` row, no encryption-at-rest for
`event_data_json` (disclosed, deferred).

## Business Rules

Unchanged from Phase 1: R12, R13.

## Locked Decisions

Unchanged from Phase 1/2: L2, L3, L5, L7, L11.

## Dependencies

Unchanged from Phase 2, plus: `DeliveryLog`'s own now-required `attempt` constructor argument at
every existing call site in `DeliveryOrchestrator`.

## Files to Create

Unchanged from Phase 2:
- `delivery/DeliveryRetry.java`, `delivery/DeliveryRetryRepository.java`, `delivery/RetryScheduler.java`
- `db/migration/V9__delivery_retry_add_replay_columns.sql`
- `db/migration/V10__notification_app_delivery_retry_grant.sql`
- `db/migration/V11__notification_app_shedlock_grant.sql`

## Files to Modify

Unchanged from Phase 2, **plus** (Finding #1):
- `delivery/DeliveryLog.java` — constructor signature change (`attempt` parameter added).

Full list: `DeliveryOrchestrator.java`, `DeliveryLog.java`, `RetryProperties.java`,
`application.properties`, `NotificationServiceApplication.java`, `pom.xml`,
`NotificationBaselineMigrationIntegrationTest.java`, `T01SkeletonRegressionTest.java`.

## Files NOT to Modify

Unchanged from Phase 2: `EmailChannel.java`, `InAppChannel.java`, `NotificationChannel.java`,
`PreferenceResolver.java` (read, not modified — `replay` calls its existing public method, adds no
new method to it), `TemplateRenderer.java`, `V1`-`V8` migrations, `spec/`, sibling services.

## Acceptance Criteria

Unchanged AC1-AC10 from Phase 2, with AC2/AC3/AC5 now precise per the pinned semantics above, plus:
11. **AC11** (Finding #3). A replay re-checks `PreferenceResolver.resolve`; if now disabled, records
    `SUPPRESSED` and deletes the `delivery_retry` row.
12. **AC12** (Finding #7). A `delivery_retry` row whose `event_data_json` cannot be deserialized is
    dead-lettered and removed on its very first encountered sweep, never retried indefinitely.
13. **AC13** (Finding #1). `DeliveryLog.getAttempt()` reflects the real attempt number for every row
    this task writes, including replays — never hardcoded.
14. **AC14** (degenerate case, pinned semantics). With `maxAttempts=1`, a transient failure on the
    very first attempt dead-letters immediately; no `delivery_retry` row is ever inserted.

## Required Tests

Unchanged from Phase 2, plus: a preference-disabled-mid-retry test (Finding #3/AC11); a poison-pill
deserialization test (Finding #7/AC12); a `DeliveryLog`-attempt-number test across an original
attempt and at least one replay (Finding #1/AC13); the `maxAttempts=1` degenerate-case test (AC14);
an explicit backoff-value test confirming the first retry waits exactly `initialBackoffSeconds`, not
double it (pinned semantics).

## Constraints

Unchanged from Phase 2, plus: `replay`'s own internal classification and `dispatchOneChannel`'s
original-attempt classification MUST share one implementation (not two parallel copies) — Findings
#4/#5/#6 would otherwise need to be fixed twice and could silently drift apart.

## Open Questions

No blockers. All 10 Phase 3 findings resolved above. Two deliberate, disclosed choices where a
Phase 3-suggested option was not simply adopted verbatim: Finding #6 (Option 1 chosen over Option 2,
to avoid a fragile string-matched AWS-error-code list leaking channel-specific knowledge into the
channel-agnostic orchestrator) and Finding #8 (documented rather than configured, since no second
scheduled task exists yet to contend with the first).
