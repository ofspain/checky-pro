# notification · T11 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T11: delivery orchestrator + log (DeliveryOrchestrator)`

## Commit message

```
notification-service T11: delivery orchestrator + log

Add DeliveryOrchestrator, replacing NoOpNotificationDispatcher
(pre-authorized since T06) as the real NotificationDispatcher: resolve
preferences per channel -> render the resolved template -> dispatch via
a NotificationChannel bean -> append a delivery_log row per attempt with
outcome (L3, R10, R11). The first task wiring PreferenceResolver (T08),
TemplateRenderer (T09), and SecretSafeLogging (T10) to a real caller,
and introduces NotificationChannel itself (no earlier task owned it)
plus two temporary, real (not stub) implementations, NoOpEmailChannel/
NoOpInAppChannel, for the two launch channels.

A VERBATIM 7-entry lookup table (design.md's own topic->template table)
maps each notificationKind to its per-channel template name and
preference category; user.registered resolves to SECURITY by
elimination (named in neither category's own list) and safe-default
reasoning (a welcome message must not silently default to MARKETING's
own OFF/OFF); account.suspended is deliberately excluded (unreachable,
unseeded). An unrecognized kind is a silent no-op.

dispatch is @Transactional (default REQUIRED) - joins AuthEventConsumer's
own already-open transaction, so a delivery_log row written here rolls
back together with everything else if the caller's transaction later
rolls back for an unrelated reason. dispatch itself must never throw
(AC9, the hardest constraint this task carries): every internal failure
- an unknown kind, a failed render, a failed channel send, a failed
preference resolution, or a failure before the per-channel loop even
starts - is caught and converted into a logged, non-propagating FAILED
row, with a synthetic-key fallback when sourceEventKey is unavailable.
Catches Exception, not Throwable; a genuine Error is allowed to
propagate. Every errorDetail is redacted (SecretSafeLogging's own first
real caller) before persistence.

AuthEventConsumer's own eventData map gains "sourceEventKey" (its own
already-computed idempotency key) on both listener methods, since
dispatch's frozen signature carries no dedicated event-key parameter.
ContactProjectionUpdater gains its first two read paths, findEmail and
findDisplayName - the latter completes wiring for a still-unpopulated
column (no data source exists anywhere in auth-service's own domain,
disclosed since T05) so a future task that finally populates it needs
no further plumbing change here.

239 tests total (196 T01-T10 unaffected + 43 new: 30 at Phase 10 closing
Kimi's "no committed tests" finding, 13 more at the Phase 11 addendum).
Three adversarial review rounds (Kimi Phase 3: 10 findings; Phase 8: 8
findings, one - "DeliveryLogRepository should be public" - verified
FALSE by direct grep of all 5 sibling repositories; Phase 11: 8 gaps,
one - a per-channel failure silently dropping its own delivery_log row,
e.g. a throwing PreferenceResolver - confirmed as a real, live bug and
fixed, not merely noted) were each independently verified against actual
source before disposition, never taken on the reviewer's own word.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryLog.java`
- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryLogRepository.java`
- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryOrchestrator.java`
- `services/notification/src/main/java/com/themistra/notification/channel/NotificationChannel.java`
- `services/notification/src/main/java/com/themistra/notification/channel/NoOpEmailChannel.java`
- `services/notification/src/main/java/com/themistra/notification/channel/NoOpInAppChannel.java`
- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryOrchestratorTest.java`
- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryOrchestratorIntegrationTest.java`
- `services/notification/src/test/java/com/themistra/notification/channel/NoOpEmailChannelTest.java`
- `services/notification/src/test/java/com/themistra/notification/channel/NoOpInAppChannelTest.java`

**Modified**
- `services/notification/src/main/java/com/themistra/notification/preference/ContactProjectionUpdater.java`
  (adds `findEmail`, `findDisplayName`)
- `services/notification/src/main/java/com/themistra/notification/consumer/AuthEventConsumer.java`
  (adds `sourceEventKey` to both `eventData` maps)
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (authorized file-inventory list, renamed method)
- `services/notification/src/test/java/com/themistra/notification/consumer/AuthEventConsumerTest.java`
  (5 exact-match assertions updated for `sourceEventKey`)
- `services/notification/src/test/java/com/themistra/notification/consumer/AuthEventConsumerIntegrationTest.java`
  (1 assertion updated for `sourceEventKey`)
- `services/notification/src/test/java/com/themistra/notification/consumer/IdempotencyGuardIntegrationTest.java`
  (bean-type assertion updated to `DeliveryOrchestrator`)
- `services/notification/src/test/java/com/themistra/notification/preference/ContactProjectionUpdaterIntegrationTest.java`
  (5 new tests for `findEmail`/`findDisplayName`)

**Deleted**
- `services/notification/src/main/java/com/themistra/notification/consumer/NoOpNotificationDispatcher.java`
  (pre-authorized since T06's own Javadoc)
- `services/notification/src/test/java/com/themistra/notification/consumer/NoOpNotificationDispatcherTest.java`
  (necessary consequence — its own subject class no longer exists)

**Process artifacts**
- `.ai/prompts/notification/T11/artifacts/00-13-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

The largest, most structurally significant task in this pipeline so far: the first task wiring
three previously-caller-less "seam" components (`PreferenceResolver`, `TemplateRenderer`,
`SecretSafeLogging`) together for the first time, while simultaneously introducing a fourth new
seam (`NotificationChannel`) for tasks 12/13. `DeliveryOrchestrator` is now the real, single place
"auth published an event" becomes "we recorded exactly what happened, per channel, for dispute
resolution" — closing the loop `AuthEventConsumer` (T06) opened.

## Testing performed

- `mvn -pl services/notification clean verify` — 239 tests, 0 failures, 0 errors, `BUILD SUCCESS`.
- Phase 7 empirical self-review: 5 real end-to-end scenarios (both channels `SENT`, suppression,
  missing-recipient, unknown kind, transaction rollback) against real Postgres with zero defects
  found, via a scratch test written, run, and deleted before commit.
- Phase 11 addendum mutation-style proof: `preferenceResolverThrowingRecordsAFailedRowForThatChannelAndDoesNotPropagate`
  fails against the pre-fix code (no row was ever written) and passes against the fix — a real,
  before/after proof, not just a new assertion against already-correct code.
- `git status -s services/auth services/crypto services/payment` — empty throughout every one of
  this task's own commits; no sibling service touched.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 11 ("Delivery orchestrator + log").
- **Requirements:** R10, R11.
- **LOCKED decisions:** L3 (scoped), plus L4, L5, L6, L9 — all five converge in this task for the
  first time (Phase 1's own finding).
- **Resolved ambiguity:** `user.registered`'s preference category is not literally named in either
  `design.md`'s own `SECURITY`/`PAYMENT` parenthetical lists — resolved to `SECURITY` by elimination
  and safe-default reasoning at Phase 0/1, disclosed explicitly, not silently picked.

## Known, deliberate gaps (not this task's scope)

- **`display_name` is, and will remain, `null` for every real row** until some future task adds an
  actual data source in `auth-service`'s own domain — the wiring (`findDisplayName` + merge into
  render data) is complete and tested end-to-end; the underlying data gap predates this task (T05)
  and is explicitly out of its scope.
- **The four payment-derived mappings are built but unreachable** until `PaymentEventConsumer` (T07)
  exists — T07 remains skipped pending `payment-service`, a pre-existing condition.
- **Retry/attempt-increment/dead-letter logic is task 14's own scope** — `attempt` is always `1`.
- **Real email/in-app sending is tasks 12/13's own scope** — `NoOpEmailChannel`/`NoOpInAppChannel`
  are deliberately as inert as `NoOpNotificationDispatcher` was.

## Reviewer notes

- **Kimi Phase 8 Finding #3's own cited justification was verified FALSE**: it claimed making
  `DeliveryLogRepository` public "matches `TemplateRepository`, `ContactProjectionRepository`,
  etc." — a direct grep of all 5 sibling repositories showed every one of them is package-private,
  the opposite of the claim. The recommendation was rejected; the real underlying testability need
  was met by placing the new tests in the same package instead (`delivery/`), matching every other
  module's own established convention.
- **Kimi Phase 11 Gap #1/#5 was a real, live bug, not a hypothetical edge case**: `dispatchOneChannel`'s
  own outer catch (wrapping `preferenceResolver.resolve` and this method's own `save()` calls) only
  logged on failure — it never recorded a fallback row, unlike `dispatch`'s own outer catch (Finding
  #4). A throwing `PreferenceResolver`, or a `save()` call itself failing (e.g. a `NOT NULL`
  violation from a missing `sourceEventKey`), silently dropped that channel's delivery attempt from
  the log entirely, a direct violation of R11. Fixed by applying the identical fallback-save pattern
  already used in `dispatch`'s own outer catch. Worth a reviewer's specific attention as the
  strongest evidence in this task that adversarial review caught something real.
- Two Phase 8 findings (#6, #7) were already-tracked, intentional design decisions requiring no new
  action; one Phase 11 gap (#4, `displayName` merge precedence) was documented/locked rather than
  changed, since the current behavior is correct and unreachable in production today.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T11.**
