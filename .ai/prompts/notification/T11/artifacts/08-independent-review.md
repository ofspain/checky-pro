<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T11 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T11 — Delivery orchestrator + log |
| **Spec section** | Delivery orchestration |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T11 implementation. Findings only.

---

## Finding 1 · No committed T11-specific tests exist

**Severity:** High

**Issue:** The task introduces the first real `NotificationDispatcher` implementation, a new JPA entity, a new repository, two new channel beans, and extensions to `AuthEventConsumer` and `ContactProjectionUpdater`. The only automated guards are `T01SkeletonRegressionTest` (file inventory) and `IdempotencyGuardIntegrationTest` (bean-type assertion). There are no tests for the happy path, suppression, missing recipient, render failure, channel failure, unknown kind, or transaction-rollback behavior described in the brief and self-review.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/` contains no `delivery/` package tests.
- No test references `DeliveryOrchestrator.dispatch`, `DeliveryLogRepository`, `NoOpEmailChannel.send`, or `NoOpInAppChannel.send`.
- The self-review describes a scratch test that was deleted; its assertions are not preserved in the committed suite.

**Recommendation:** Add the full Phase 10 test set before considering T11 complete, covering at minimum:
- happy path (both channels SENT, correct recipient/sourceEventKey/templateVersion);
- per-channel suppression (one channel SUPPRESSED, the other SENT);
- missing contact projection (EMAIL FAILED, IN_APP SENT);
- render failure → FAILED row;
- channel send failure → FAILED row;
- unknown notificationKind → zero rows;
- `dispatch` never throws even when render and channel both throw;
- transaction rollback joins the caller's transaction.

**Confidence:** High

---

## Finding 2 · Auth event schemas do not provide `displayName`, so all auth templates render with an empty greeting

**Severity:** Medium

**Issue:** The seeded launch templates (`V3__seed_launch_templates.sql`) use `{{displayName}}` in `email.verify`, `email.password_reset`, and `user.welcome`. Neither `EmailRequestedEvent` nor `UserLifecycleEvent` (or their contract schemas) contain a `displayName` field, and `DeliveryOrchestrator` does not enrich `eventData` with a display name from `contact_projection` (which does have a `display_name` column). The result is that every auth-originated message renders with an empty placeholder.

**Evidence:**
- `V3__seed_launch_templates.sql` lines 18, 24, 30, 33 reference `{{displayName}}`.
- `EmailRequestedEvent.java` fields: `accountUuid`, `purpose`, `token`, `email`, `occurredAt` — no `displayName`.
- `UserLifecycleEvent.java` fields: `accountUuid`, `status`, `email`, `eventType`, `occurredAt` — no `displayName`.
- `AuthEventConsumer` passes only `token`/`sourceEventKey` (verify_email/password_reset) or `sourceEventKey` alone (user.registered) into `eventData`.
- `DeliveryOrchestrator` only resolves email via `ContactProjectionUpdater.findEmail`; it never adds `displayName`.

**Recommendation:** Either:
1. Add `displayName` to the auth event schemas/DTOs and propagate it through `AuthEventConsumer.eventData`, or
2. Have `ContactProjectionUpdater` expose `Optional<ContactProjection>` (or a dedicated `findDisplayName`) and have `DeliveryOrchestrator` add `displayName` to the values passed to `TemplateRenderer.render`.

Option 2 avoids a cross-service schema change but requires widening the updater's read surface slightly. At minimum, document the empty-greeting behavior as a known launch limitation if neither fix is in scope.

**Confidence:** High

---

## Finding 3 · `DeliveryLogRepository` is package-private, limiting where tests can live

**Severity:** Medium

**Issue:** `DeliveryLogRepository` is declared `interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long>` with no access modifier, making it package-private. Tests outside `com.themistra.notification.delivery` cannot autowire it to assert rows. Existing integration tests live in `consumer`, `preference`, `template`, and the root `notification` packages.

**Evidence:**
- `delivery/DeliveryLogRepository.java` line 12 has no `public` modifier.
- `DeliveryOrchestrator` is in the same `delivery` package, so production code compiles.
- No T11 test currently exists to verify accessibility, but the repository is the natural assertion point for delivery-log outcomes.

**Recommendation:** Make `DeliveryLogRepository` `public` so tests can autowire it regardless of package. This matches `TemplateRepository`, `ContactProjectionRepository`, etc.

**Confidence:** High

---

## Finding 4 · A failure before the per-channel loop can leave no delivery-log row

**Severity:** Medium

**Issue:** `DeliveryOrchestrator.dispatch` has an outer `try/catch` around the mapping lookup, email resolution, and channel loop. If `contactProjectionUpdater.findEmail` throws (or any other pre-loop code throws), the outer catch logs an error but does not save any `delivery_log` row. R11 requires "every delivery attempt and outcome" to be recorded; a caller-visible `dispatch` invocation that fails before attempting a channel is arguably an attempted delivery with no recorded outcome.

**Evidence:**
- `DeliveryOrchestrator.java` lines 95–113: outer `try` covers mapping lookup, `findEmail`, and loop; outer `catch` only logs.
- Lines 115–161: inner `try/catch` in `dispatchOneChannel` records `FAILED` for channel-level failures.

**Recommendation:** In the outer catch, save a single `FAILED` row with `channel = "UNKNOWN"` (or omit channel/account if truly unavailable) and the exception's redacted message. If even that save fails, fall back to logging. This keeps R11 honest for pre-loop failures. Alternatively, document that R11 is interpreted as "per-channel attempts" only.

**Confidence:** Medium

---

## Finding 5 · `NoOpEmailChannel` and `NoOpInAppChannel` log at `INFO` for every dispatch

**Severity:** Low

**Issue:** Both temporary channels log each no-op dispatch at `INFO`. In a busy system this generates a large volume of production log lines that do not correspond to any real external action. Since these are temporary no-op implementations, `DEBUG` would be more appropriate.

**Evidence:**
- `channel/NoOpEmailChannel.java` line 32: `log.info(...)`.
- `channel/NoOpInAppChannel.java` line 28: `log.info(...)`.

**Recommendation:** Change both to `log.debug(...)` and guard with `if (log.isDebugEnabled())` if argument construction becomes expensive. The message is already safe (uses `RenderedMessage.toString()`), so the only concern is volume.

**Confidence:** Low

---

## Finding 6 · Null `sourceEventKey` causes loss of graceful per-channel FAILED rows for both channels

**Severity:** Low

**Issue:** `delivery_log.source_event_key` is `NOT NULL`. If a future caller omits `"sourceEventKey"` from `eventData`, the first `save()` call inside `dispatchOneChannel` throws a constraint violation. That exception is caught by the inner catch, which tries a second `save()` to record `FAILED` — but `sourceEventKey` is still null, so the second save throws again. The second throw escapes `dispatchOneChannel` but is caught by the outer `dispatch` catch, so `dispatch` still never throws (AC9 holds). However, neither channel gets a `FAILED` row; only an error log is emitted.

**Evidence:**
- `DeliveryOrchestrator.java` lines 137–157: inner try/render/send/save; inner catch at line 158.
- Lines 163–168: `save()` always uses the original `sourceEventKey` parameter.
- `V1__notifications_baseline.sql` line 51: `source_event_key VARCHAR(200) NOT NULL`.
- The self-review already discloses this exact scenario as an informational limitation.

**Recommendation:** Since `AuthEventConsumer` is the only real caller and always supplies `sourceEventKey`, this is acceptable as a disclosed limitation. If robustness is desired, wrap the fallback `save()` in its own try/catch inside `dispatchOneChannel` so that at least one `FAILED` row can be attempted with a synthetic key such as `"missing:" + accountUuid`.

**Confidence:** Low

---

## Finding 7 · `DeliveryOrchestrator` catches `Exception`, not `Throwable`

**Severity:** Low

**Issue:** The outer and inner catches use `catch (Exception e)`. A genuine `Error` (e.g., `OutOfMemoryError`, `StackOverflowError`) will propagate out of `dispatch`, violating a literal reading of "dispatch must never throw." The implementation intentionally allows this, and the Javadoc explains why.

**Evidence:**
- `DeliveryOrchestrator.java` lines 109 and 158 use `catch (Exception e)`.
- Javadoc lines 34–36 explicitly state `Error` is allowed to propagate.

**Recommendation:** No code change required if the project accepts the documented trade-off. Ensure AC9 is tested only with `Exception` subclasses, not `Error`, to avoid a misleading test name.

**Confidence:** Low

---

## Finding 8 · `ContactProjectionUpdater.findEmail` has no `@Transactional(readOnly = true)`

**Severity:** Low

**Issue:** `findEmail` is a read-only query but is not annotated `@Transactional(readOnly = true)`. It will still work because Spring Data repository methods are transactional by default, but omitting the annotation means:
- the method does not document its read-only intent;
- if called outside an existing transaction, Spring creates a read/write transaction by default rather than a read-only one.

**Evidence:**
- `preference/ContactProjectionUpdater.java` lines 52–54: `public Optional<String> findEmail(UUID accountUuid)` with no `@Transactional`.

**Recommendation:** Add `@Transactional(readOnly = true)` to `findEmail` for clarity and to hint the transaction manager that no flush is needed.

**Confidence:** Low

---

## Summary

The T11 implementation is structurally sound and matches the frozen brief: `DeliveryOrchestrator` is `@Transactional` (joins the caller's transaction), `dispatch` never throws, channel failures are isolated, `DeliveryLog` is a real constructible entity, and the temporary no-op channels are wired correctly. The dominant issue is **Finding 1: no committed T11-specific tests exist**, leaving the task's correctness unguarded in the build. **Finding 2** identifies a real product/data-model mismatch: auth events lack `displayName`, so every launched auth template renders with an empty greeting. **Finding 3** is a practical testability limitation. Findings 4–8 are smaller robustness or style items.
