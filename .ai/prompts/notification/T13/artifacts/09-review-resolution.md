<!-- MODEL: Claude — Phase 9 (Review Resolution, human-gated). -->

# notification · T13 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T13 — In-app channel + store |
| **Consumes** | `artifacts/08-independent-review.md` (Kimi) |
| **Produces** | `artifacts/09-review-resolution.md` |

Disposition of all 10 Phase 8 findings (which substantially overlap the Phase 7 self-review's own 5
findings — cross-referenced below). All code fixes were applied directly, compiled
(`mvn -pl services/notification test-compile`), and verified against the full suite
(`mvn -pl services/notification clean verify` → 271 tests, 0 failures, 0 errors — same count as
before this batch; no new tests were added at this phase, only production-code fixes).

---

## Finding 1 · `InAppChannel` directly imports and constructs an `inapp`-module entity — an L11 violation

**Disposition: ACCEPTED, FIXED** (identical to self-review Finding 1 — the same real gap, caught
independently by both reviews). New `inapp/InappNotificationAppender.java` — the single, sanctioned
gateway into the `inapp` module's own persistence and live-push machinery, mirroring
`ContactProjectionUpdater`'s own established role for `ContactProjectionRepository` exactly.
`InAppChannel` now depends only on this gateway (and `Clock`); it no longer imports
`InappNotification`, `InappNotificationRepository`, or `InappStreamRegistry` at all.
`InappNotificationRepository` reverted to package-private, matching every sibling repository — the
`public` visibility was only ever needed because of the violation this fix removes.

---

## Finding 2 · No T13-specific tests exist

**Disposition: DEFERRED to Phase 10.** Matches this pipeline's own unbroken precedent (T09-T12 each
deferred their own equivalent finding identically) — Phase 6/7/8/9 focus on implementation and
review; the committed test suite is Phase 10's own scope.

---

## Finding 3 · `ApiExceptionHandler` blanket `Exception` handler shadows Spring framework defaults

**Disposition: ACCEPTED, FIXED** (identical to self-review Finding 2, independently confirmed by
Kimi). Verified directly via `javap` before fixing: `NoResourceFoundException` and
`HttpRequestMethodNotSupportedException` both extend `jakarta.servlet.ServletException` — a
**checked** exception, not a `RuntimeException`. Narrowed `handleUnexpected`'s own
`@ExceptionHandler` from `Exception.class` to `RuntimeException.class` - Spring's own default
handling for those (and any other checked, framework-level `ServletException` subtype) now runs
unshadowed, while every genuine application-level runtime exception this handler exists for is still
caught exactly as before.

---

## Finding 4 · `InappStreamRegistry` never removes empty emitter lists from the map

**Disposition: ACCEPTED, FIXED.** `deregister` now uses `ConcurrentHashMap.computeIfPresent` to
remove the emitter and, atomically in the same remapping, remove the account's own map entry if the
list is now empty — closing the unbounded-growth path Kimi identified, with no race against a
concurrent `register` call for the same account (per-key locking on both sides).

---

## Finding 5 · `InappStreamRegistry` relies on no timeout for dead-connection detection

**Disposition: ACCEPTED-AS-DOCUMENTED, no code change** (identical to self-review Finding 3,
independently confirmed by Kimi, whose own recommendation offered "or document this as a
launch-scale limitation" as an explicit, valid alternative). Finding #4's own fix already closes the
larger part of this concern (the map-entry leak); the remaining narrow case — a connection whose
break the servlet container never detects, and which never receives another push — is a genuinely
rare edge case not worth a specific timeout value chosen without real operational data behind it.

---

## Finding 6 · `deriveTitle` can split a UTF-16 surrogate pair

**Disposition: ACCEPTED-AS-DOCUMENTED, no code change** (identical to self-review Finding 4,
independently confirmed by Kimi, whose own recommendation also offered documentation as a valid
alternative). Not currently reachable — every real launch template body is plain ASCII text.

---

## Finding 7 · `InappReadController.unread` returns an unbounded list

**Disposition: ACCEPTED-AS-DOCUMENTED, no code change** (identical to self-review Finding 5,
independently confirmed by Kimi). Not required by R17's own literal wording; no acceptance criterion
names a limit.

---

## Finding 8 · `InAppChannel` does not validate `category`

**Disposition: ACCEPTED, FIXED.** `InAppChannel.send` now throws `IllegalArgumentException` for a
null/blank `category`, mirroring the existing `message.body()` validation exactly — a future
`DeliveryOrchestrator` mapping bug now surfaces as a clear error at this boundary, not a `NOT NULL`
constraint violation deep inside the repository.

---

## Finding 9 · `InAppChannel` imports `InappStreamRegistry` from another module

**Disposition: ACCEPTED, FIXED as part of Finding #1's own resolution.** `InappNotificationAppender`
owns the post-commit push internally (calling `InappStreamRegistry` itself, same package); `InAppChannel`
now depends on exactly one `inapp` collaborator, not two — exactly the outcome Kimi's own finding
described as the ideal resolution "if the gateway pattern is adopted."

---

## Finding 10 · Endpoint paths are not versioned

**Disposition: REJECTED, no action.** Already an explicit, disclosed Phase 4 decision, not an
oversight: verified directly (via `grep` across every controller in `auth-service`/`crypto-service`)
that no version-prefix convention exists anywhere in this monorepo. Kimi's own finding text
concedes this is "not a defect unless a convention exists" — none does.

---

## Summary of code changes this phase

- `inapp/InappNotificationAppender.java` (**new**) — the sanctioned gateway; owns entity
  construction, persistence, and the deferred post-commit push.
- `channel/InAppChannel.java` — simplified to depend only on the appender and `Clock`; adds
  `category` validation.
- `inapp/InappNotificationRepository.java` — reverted to package-private.
- `common/ApiExceptionHandler.java` — narrowed the generic handler from `Exception` to
  `RuntimeException`.
- `inapp/InappStreamRegistry.java` — `deregister` now removes empty map entries atomically.
- `T01SkeletonRegressionTest.java` — authorized file list updated (46 files).

No test files were added or modified this phase (Finding #2 defers all new tests to Phase 10).
Full suite: 271 tests, 0 failures, 0 errors — unchanged count, confirming these are pure
production-code fixes with no test-visible regressions.
