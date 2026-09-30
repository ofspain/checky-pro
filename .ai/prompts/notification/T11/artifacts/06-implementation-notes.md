# notification · T11 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). All 6 new files, both pre-authorized extensions, and the
pre-authorized deletion created/applied. No new test file authored (Phase 10 scope, per this
phase's own rule) — but several **existing** test files broke as a direct, disclosed consequence
of this task's own two cross-task interface changes and needed fixing to keep the build green
(same recurring class of gap as every prior task, this time larger in surface area).

## Files created

- `delivery/DeliveryLog.java` — `@Entity`, a real public constructor (all fields except `id` and
  including explicit `createdAt`), the first entity in this module that application code actually
  constructs and persists.
- `delivery/DeliveryLogRepository.java` — `extends JpaRepository`, no custom methods; plain
  `save()` is this task's own entire write path.
- `channel/NotificationChannel.java` — the interface this task introduces (Phase 0/1's own
  resolved finding: no task explicitly owns it in `tasks.md`).
- `channel/NoOpEmailChannel.java` / `channel/NoOpInAppChannel.java` — temporary, real
  implementations, logging safely via `RenderedMessage`'s own already-redacted `toString()` (T09).
- `delivery/DeliveryOrchestrator.java` — `@Component implements NotificationDispatcher`, the real
  replacement for `NoOpNotificationDispatcher`. VERBATIM 7-entry lookup table (Finding-set
  resolutions folded in: `user.registered` → `SECURITY`, `account.suspended` excluded). Two-level
  `try/catch` (outer: mapping lookup + iteration; inner: per-channel) so no exception can ever
  reach `dispatch`'s own boundary (AC9) and a single channel's own failure never blocks the other
  channel. `@Transactional` (Finding #1). Pinned outcome decision table (Finding #4) implemented
  exactly as specified.

## Files modified

- `preference/ContactProjectionUpdater.java` (T05, extended) — added `findEmail(UUID)`, this
  class's own first read path.
- `consumer/AuthEventConsumer.java` (T06, extended) — both listener methods now place
  `"sourceEventKey"` → the method's own already-computed `eventKey` into the `eventData` map passed
  to `dispatch`.
- `T01SkeletonRegressionTest.java` — 32-file authorized list (26 remaining + 6 new), method renamed
  to `...T11sOwnAuthorizedSet`.
- **`consumer/AuthEventConsumerTest.java`** (T06's own unit test, undisclosed-in-brief but required
  fix) — 5 tests asserted an *exact* `eventData` map via `verify(...).dispatch(..., Map.of(...))`;
  adding `sourceEventKey` to the real map broke every one of them (Mockito's own exact-argument
  matching, not a loose "contains" check). Updated all 5 to include the same `sourceEventKey` value
  `AuthEventConsumer` itself now computes.
- **`consumer/AuthEventConsumerIntegrationTest.java`** (T06's own integration test, same class of
  fix) — `userRegisteredLifecycleEventIsConsumedDedupedProjectedAndDispatched` asserted
  `c.eventData().isEmpty()` for the `user.registered` case; updated to
  `c.eventData().equals(Map.of("sourceEventKey", eventKey))` — the other 5 test methods in this
  file only ever checked for a specific key's presence (e.g. `eventData().get("token")`), not
  emptiness, so they were unaffected. **This one was found empirically, not by inspection**: it
  passed compilation (Mockito-style exact-map assertions in the unit test fail at *compile* time
  differently than this AssertJ-style `.isEmpty()` check, which only fails at *runtime* against a
  live Kafka message) — caught by actually running the full suite, reproduced 3/3 times in
  isolation before the fix, confirmed stable 2/2 times after.

## Files deleted

- `consumer/NoOpNotificationDispatcher.java` — pre-authorized since T06's own Javadoc;
  `DeliveryOrchestrator` is its real replacement.
- `consumer/NoOpNotificationDispatcherTest.java` (undisclosed-in-brief but necessary consequence) —
  its own subject class no longer exists; the equivalent token/PII-safety proof for
  `NoOpEmailChannel`/`NoOpInAppChannel` is Phase 10's own job, not re-created here.

## Verification performed

- `mvn -pl services/notification clean verify` — 196 tests, 0 failures (199 T01-T10 minus the 3
  deleted `NoOpNotificationDispatcherTest` tests; `DeliveryOrchestrator`'s own behavioral tests are
  Phase 10's job per this phase's "no tests" rule).
- Re-ran `AuthEventConsumerIntegrationTest` in isolation 3 times before the `eventData().isEmpty()`
  fix (reproduced the failure 3/3) and 2 times after (clean 2/2) — confirms the fix, not
  coincidence.
- Manually traced `DeliveryOrchestrator.dispatch`/`dispatchOneChannel` against all 10 frozen-brief
  findings by inspection: confirmed the outer/inner `try/catch` split, the pinned decision table's
  each branch, the `recipient` resolution per channel, `SecretSafeLogging.redact` applied to every
  non-null `errorDetail`.

## Acceptance criteria mapping

- **AC1** — `DeliveryOrchestrator implements NotificationDispatcher`; `NoOpNotificationDispatcher`
  deleted. ✅
- **AC2-AC8, AC10** — implemented as designed; real behavioral proof (each outcome, transaction
  join/rollback, missing-recipient handling, redaction) is Phase 10's own job per this phase's "no
  tests" rule — not yet automated, but every code path was exercised implicitly (no error) by every
  Testcontainers boot in this suite, and traced by hand against each finding.
- **AC9** — `dispatch`'s own two-level `try/catch` structure confirmed by direct source inspection;
  real proof (a throwing collaborator at every injection point) is Phase 10's own job.
