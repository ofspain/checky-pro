<!-- MODEL: Claude — Phase 9 (Review Resolution, human-gated). -->

# notification · T11 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T11 — Delivery orchestrator + log |
| **Consumes** | `artifacts/08-independent-review.md` (Kimi) |
| **Produces** | `artifacts/09-review-resolution.md` |

Disposition of all 8 Phase 8 findings. All code fixes below were applied directly to the
implementation, compiled (`mvn -pl services/notification test-compile`), and verified against the
full suite (`mvn -pl services/notification clean verify` → 196 tests, 0 failures, 0 errors — same
count as before this batch; no new tests were added at this phase, only production-code fixes).

---

## Finding 1 · No committed T11-specific tests exist

**Disposition: DEFERRED to Phase 10.**

Correct as stated — the only committed guards today are `T01SkeletonRegressionTest` (file
inventory) and `IdempotencyGuardIntegrationTest` (bean-type assertion). This matches every prior
task's own pipeline discipline: Phase 6/7 focuses on implementation + empirical self-review (the
Phase 7 scratch test, `ScratchDeliveryOrchestratorVerificationTest`, already exercised all 8
scenarios Kimi lists — happy path both channels, suppression, missing recipient, render failure,
unknown kind, rollback — against real Postgres with zero defects found), and the *committed* test
suite is Phase 10's own scope, not Phase 6/8/9's. No action taken now; Phase 10 will cover the
exact scenario list Kimi enumerates.

---

## Finding 2 · Auth event schemas do not provide `displayName`

**Disposition: ACCEPTED (Option 2), FIXED.**

Kimi's own Option 2 — widen `ContactProjectionUpdater`'s read surface rather than touch the
cross-service event schema — was taken. `ContactProjectionUpdater.findDisplayName(UUID)` was
added (`@Transactional(readOnly = true)`, mirroring `findEmail`), and `DeliveryOrchestrator.dispatch`
now resolves it and merges it into the render-data map (`renderData.put("displayName", ...)`)
before calling `TemplateRenderer.render`, only when non-null.

This is wiring completeness, **not** a fix to the underlying missing-data problem: `display_name`
is always `null` today because no data source for it exists anywhere in `auth-service`'s own
domain (verified directly, unchanged since T05 — already disclosed at T05/T09's own Phase 12
verifications). Every auth-originated message still renders `{{displayName}}` as empty until a
future task adds a real source upstream. That known launch limitation is now documented in
`DeliveryOrchestrator`'s own class-level Javadoc, per Kimi's own "at minimum, document" fallback
recommendation, in addition to (not instead of) doing the wiring.

---

## Finding 3 · `DeliveryLogRepository` is package-private, limiting where tests can live

**Disposition: REJECTED (recommendation), underlying need addressed differently.**

Kimi's own cited justification — "This matches `TemplateRepository`, `ContactProjectionRepository`,
etc." — was verified against source and is **factually false**. Direct check:

```
$ grep -n "^interface\|^public interface" \
    services/notification/src/main/java/com/themistra/notification/template/TemplateRepository.java \
    services/notification/src/main/java/com/themistra/notification/preference/ContactProjectionRepository.java \
    services/notification/src/main/java/com/themistra/notification/preference/ChannelPreferenceRepository.java \
    services/notification/src/main/java/com/themistra/notification/consumer/ProcessedEventRepository.java \
    services/notification/src/main/java/com/themistra/notification/delivery/DeliveryLogRepository.java
```

All five are declared as bare `interface ... extends JpaRepository<...>` with **no** `public`
modifier — every one of them is package-private, exactly matching `DeliveryLogRepository`'s own
current visibility. Making `DeliveryLogRepository` public would not "match" the established
pattern; it would **break** it, since it would be the only public repository in the module.

The underlying, real need Kimi identifies (a Phase 10 test needing to assert `delivery_log` rows)
is legitimate and is addressed the same way every prior task's own integration test has addressed
the identical constraint: place the future test class in the *same* package
(`com.themistra.notification.delivery`), exactly mirroring `PreferenceResolverIntegrationTest`
(in `preference/`), `TemplateRendererIntegrationTest` (in `template/`), and
`IdempotencyGuardIntegrationTest` (in `consumer/`). No visibility change made.

---

## Finding 4 · A failure before the per-channel loop can leave no delivery-log row

**Disposition: ACCEPTED, FIXED.**

`dispatch`'s outer `catch (Exception e)` now iterates `LAUNCH_CHANNELS` and attempts a best-effort
`save(...)` of a `FAILED` row per channel (redacted `e.getMessage()` as `errorDetail`), rather than
only logging. This keeps R11's "every delivery attempt and outcome" guarantee honest for pre-loop
failures (e.g. `findEmail` itself throwing), not just per-channel ones.

That fallback save has its own inner `try/catch`, since the very reason the outer block failed
could itself make the fallback save fail too (concretely: a genuinely null `sourceEventKey`,
Finding #6 below) — falls back to a synthetic key (`"unknown:" + accountUuid`) and, if even that
throws, to a log-only record. `dispatch` still never throws (AC9 preserved).

---

## Finding 5 · `NoOpEmailChannel`/`NoOpInAppChannel` log at `INFO` for every dispatch

**Disposition: ACCEPTED, FIXED.**

Both `send` methods changed from unconditional `log.info(...)` to `log.debug(...)` guarded by
`if (log.isDebugEnabled())`. No message-content change — already safe per T09 Phase 8 Finding #2
(`RenderedMessage.toString()` excludes subject/body).

---

## Finding 6 · Null `sourceEventKey` loses graceful per-channel FAILED rows for both channels

**Disposition: ALREADY MITIGATED as a side effect of Finding #4's own fix; remains a disclosed,
acceptable limitation for the only real caller.**

Kimi's own recommendation (wrap the fallback save in its own try/catch with a synthetic key) is
exactly what Finding #4's fix now does, since the same `save(...)` helper and the same
`sourceEventKey == null ? "unknown:" + accountUuid : sourceEventKey` fallback is used for both the
per-channel path and the outer pre-loop path. `AuthEventConsumer` — the only real caller today —
always supplies `sourceEventKey`, so this remains a disclosed limitation for a hypothetical future
caller, not a live bug. No further action.

---

## Finding 7 · `DeliveryOrchestrator` catches `Exception`, not `Throwable`

**Disposition: ALREADY TRACKED, intentional (Kimi Phase 3 Finding #9, confirmed at Phase 8).**
No new action. `Error` (e.g. `OutOfMemoryError`) is deliberately allowed to propagate, per the
class-level Javadoc. Kimi's own secondary note — ensure AC9's own tests only exercise `Exception`
subclasses, never `Error` — will be honored when Phase 10 writes that test.

---

## Finding 8 · `ContactProjectionUpdater.findEmail` has no `@Transactional(readOnly = true)`

**Disposition: ACCEPTED, FIXED.**

Added `@Transactional(readOnly = true)` to `findEmail`, matching the same annotation added to the
new `findDisplayName` (Finding #2). Documents read-only intent explicitly rather than relying on
Spring Data's own default transactional behavior.

---

## Summary of code changes this phase

- `ContactProjectionUpdater.java` — `@Transactional(readOnly = true)` added to `findEmail`; new
  `findDisplayName(UUID) -> Optional<String>` method added with the same annotation.
- `DeliveryOrchestrator.java` — `dispatch` now resolves and merges `displayName` into render data;
  outer `catch` now records a best-effort `FAILED` row per `LAUNCH_CHANNELS` entry with its own
  inner safety net; class-level Javadoc extended to document both behaviors.
- `NoOpEmailChannel.java` / `NoOpInAppChannel.java` — `log.info` → `log.isDebugEnabled()`-guarded
  `log.debug`.

No test files were added or modified this phase (Finding #1 defers all new tests to Phase 10).
Full suite: 196 tests, 0 failures, 0 errors — unchanged count, confirming these are pure
production-code fixes with no test-visible regressions.
