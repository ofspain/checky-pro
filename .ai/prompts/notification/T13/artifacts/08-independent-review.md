<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T13 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T13 — In-app channel + SSE/read API |
| **Spec section** | In-app delivery / read API |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T13 implementation. Findings only.

---

## Finding 1 · `InAppChannel` directly imports and constructs an `inapp`-module entity — an L11 violation

**Severity:** High

**Issue:** `InAppChannel` (package `channel`) directly imports `InappNotification` and `InappNotificationRepository` from package `inapp`, constructs the entity, and calls `save`. L11 states "no feature module imports another feature module's entity." This is the same Finding #1 from the self-review; it remains unaddressed in the committed code.

**Evidence:**
- `channel/InAppChannel.java` lines 3–5: imports `InappNotification`, `InappNotificationRepository`, and `InappStreamRegistry` from `inapp`.
- Line 74: `new InappNotification(...)` constructed inside `channel`.
- `inapp/InappNotificationRepository.java` is `public` specifically to allow this cross-package access, contrary to every sibling repository's package-private convention.

**Recommendation:** Introduce a same-package (`inapp`) public gateway class (e.g., `InappNotificationAppender`) with a method like `append(UUID accountUuid, String category, String title, String body, Instant createdAt)`. `InAppChannel` should depend only on that gateway and on `InappStreamRegistry` if necessary. Once the gateway exists, `InappNotificationRepository` can revert to package-private.

**Confidence:** High

---

## Finding 2 · No T13-specific tests exist

**Severity:** High

**Issue:** T13 introduces the service's first REST/SSE surface and a new channel. The `inapp` test package does not exist; there are no tests for `InAppChannel`, `InappStreamRegistry`, `InappStreamController`, `InappReadController`, `InappNotification`, `InappNotificationRepository`, or `ApiExceptionHandler`.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/inapp/` does not exist.
- `services/notification/src/test/java/com/themistra/notification/channel/` contains only T12 tests; no `InAppChannelTest`.
- `services/notification/src/test/java/com/themistra/notification/common/` has no `ApiExceptionHandlerTest`.

**Recommendation:** Add the required test set before considering T13 complete:
- `InAppChannelTest`: persistence call, `View` passed to registry, push deferred to afterCommit, body validation.
- `InappStreamRegistryTest`: register/push/deregister, concurrent register/push, dead-emitter removal.
- `InappStreamControllerTest` (MockMvc + `jwt()`): returns `SseEmitter`, rejects unauthenticated request.
- `InappReadControllerTest` (MockMvc + `jwt()`): returns only caller's unread rows, rejects unauthenticated, cross-account isolation.
- `InappNotificationRepository` integration test (real Postgres): query returns only unread for account.
- `ApiExceptionHandlerTest`: invalid sub claim → 400, unexpected exception → 500 problem detail.
- End-to-end integration test: real JWT, real Postgres, dispatch via `DeliveryOrchestrator`, assert row in DB and (optionally) captured SSE event.

**Confidence:** High

---

## Finding 3 · `ApiExceptionHandler` blanket `Exception` handler shadows Spring framework defaults

**Severity:** Medium

**Issue:** `@ExceptionHandler(Exception.class)` catches every unhandled exception from any `@RestController`. Spring Boot's own default handling for `NoResourceFoundException` (404), `HttpRequestMethodNotSupportedException` (405), etc., may be overridden, producing generic 500 responses for routine client errors.

**Evidence:**
- `common/ApiExceptionHandler.java` line 44: `@ExceptionHandler(Exception.class)`.

**Recommendation:** Narrow the catch-all. Either exclude specific Spring exceptions (e.g., `NoResourceFoundException`, `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`) from the handler, or replace the broad `Exception` handler with handlers for application-specific exceptions plus a fallback for `RuntimeException` that still logs and returns 500. Verify the behavior with a MockMvc test for an unmapped path.

**Confidence:** Medium

---

## Finding 4 · `InappStreamRegistry` never removes empty emitter lists from the map

**Severity:** Medium

**Issue:** `deregister` removes the emitter from the list but leaves an empty `CopyOnWriteArrayList` in the map under the account UUID. Over time, the map accumulates a key for every account that ever connected, even after all their emitters are gone. This is a slow, unbounded memory leak.

**Evidence:**
- `inapp/InappStreamRegistry.java` lines 71–76: `emitters.remove(emitter)` but no `emittersByAccount.remove(accountUuid)` when empty.

**Recommendation:** After removing the emitter, check `if (emitters.isEmpty()) { emittersByAccount.remove(accountUuid); }`. Do this atomically under the list lock or use `computeIfPresent` to avoid race conditions.

**Confidence:** High

---

## Finding 5 · `InappStreamRegistry` relies on no timeout for dead-connection detection

**Severity:** Low/Medium

**Issue:** `SseEmitter` is created with `NO_TIMEOUT = 0L` (no timeout). Cleanup depends entirely on the servlet container promptly detecting a disconnect and firing `onCompletion`/`onError`, or on a subsequent push attempt failing. A silently dropped TCP connection may never trigger either, leaking the emitter.

**Evidence:**
- `inapp/InappStreamRegistry.java` line 40: `new SseEmitter(NO_TIMEOUT)` with `NO_TIMEOUT = 0L`.

**Recommendation:** Use a long but finite timeout (e.g., 30 minutes) with client-reconnect documentation, or add a periodic heartbeat/keepalive to detect dead connections. Alternatively, document this as a launch-scale limitation.

**Confidence:** Medium

---

## Finding 6 · `deriveTitle` can split a UTF-16 surrogate pair

**Severity:** Low

**Issue:** `body.substring(0, TITLE_MAX_LENGTH)` cuts on UTF-16 code units, not Unicode code points. A supplementary character (e.g., emoji) whose surrogate pair straddles the boundary would produce an invalid string.

**Evidence:**
- `channel/InAppChannel.java` line 86: `body.substring(0, TITLE_MAX_LENGTH)`.

**Recommendation:** Use `Character.offsetByCodePoints(body, 0, TITLE_MAX_LENGTH)` to truncate on code-point boundaries, or document that launch templates are ASCII-only and this is a known limitation.

**Confidence:** Low

---

## Finding 7 · `InappReadController.unread` returns an unbounded list

**Severity:** Low

**Issue:** The read endpoint returns all unread notifications for the caller with no pagination or limit. A long-dormant account could receive a very large response.

**Evidence:**
- `inapp/InappReadController.java` line 30: `findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc(accountUuid)` with no limit.

**Recommendation:** Document as a launch-scale limitation, or add a conservative default limit (e.g., most recent 100) with a follow-up task for proper pagination.

**Confidence:** Low

---

## Finding 8 · `InAppChannel` does not validate `category`

**Severity:** Low/Medium

**Issue:** `InAppChannel.send` accepts `category` from `DeliveryOrchestrator` and passes it directly to the DB. If `DeliveryOrchestrator` ever passes a null or blank category (e.g., due to a mapping bug), the insert will fail with a NOT NULL constraint violation rather than a clear `IllegalArgumentException`. Because `InAppChannel` is the boundary that owns the `inapp_notifications` row, it should validate its inputs.

**Evidence:**
- `channel/InAppChannel.java` line 65: `send(UUID accountUuid, String recipient, String category, ...)`.
- Line 75: `category` passed directly to `new InappNotification(...)`.

**Recommendation:** Add a null/blank check for `category` (and optionally `accountUuid`) with a clear `IllegalArgumentException`, mirroring the existing `message.body()` validation.

**Confidence:** Low

---

## Finding 9 · `InAppChannel` imports `InappStreamRegistry` from another module

**Severity:** Low

**Issue:** Even after resolving Finding #1 (entity/repository direct access), `InAppChannel` will still depend on `InappStreamRegistry` from the `inapp` package. This is a cross-module dependency, though not an entity import. The architecture encourages package-by-feature; a channel depending on an inapp registry is pragmatic but should be documented.

**Evidence:**
- `channel/InAppChannel.java` line 5: import `InappStreamRegistry`.

**Recommendation:** If the gateway pattern from Finding #1 is adopted, consider whether the live push should also move behind the gateway (e.g., `InappNotificationAppender.appendAndPush(...)`), so `InAppChannel` depends on a single `inapp` service boundary rather than two. This is a style/encapsulation preference, not a correctness defect.

**Confidence:** Low

---

## Finding 10 · Endpoint paths are not versioned

**Severity:** Low

**Issue:** The frozen brief did not specify paths; the implementation chose `/notifications/stream` and `/notifications/unread`. These are unversioned. If future API versions are introduced, there is no `/api/v1/` prefix to distinguish them.

**Evidence:**
- `inapp/InappStreamController.java` line 35: `@GetMapping("/notifications/stream")`.
- `inapp/InappReadController.java` line 27: `@GetMapping("/notifications/unread")`.

**Recommendation:** Document the chosen paths or migrate to `/api/v1/notifications/stream` and `/api/v1/notifications/unread` if the project's API convention requires versioning. This is not a defect unless a convention exists.

**Confidence:** Low

---

## Summary

The T13 implementation correctly addresses the central Phase 3 design challenge by extending `NotificationChannel.send` with `category` and updating `DeliveryOrchestrator` accordingly. It also makes sensible decisions on title derivation, deferred post-commit push, JWT-scoped controllers, and RFC 9457 error handling. However, the two highest-severity issues are **Finding #1 (L11 violation: `InAppChannel` directly imports `inapp` entities/repositories)** and **Finding #2 (no T13-specific tests exist)**. Finding #3 (blanket exception handler) and Finding #4 (empty list leak in stream registry) are medium-severity and should be fixed. Findings #5–10 are smaller quality or documentation items.
