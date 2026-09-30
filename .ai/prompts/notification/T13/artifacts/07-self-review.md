# notification · T13 · Phase 7 — Self Review

Self-review of the Phase 6 diff against the frozen brief and `agents.md`. Findings only — no code
changed here.

---

## Finding 1 · `InAppChannel` directly imports and constructs an `inapp`-module entity — a real L11
violation

**Severity:** High

**Evidence:** `channel/InAppChannel.java` imports `com.themistra.notification.inapp.InappNotification`
and `com.themistra.notification.inapp.InappNotificationRepository` directly, constructs a
`new InappNotification(...)`, and calls `repository.save(...)` on it — all from a different
module (`channel`) than the entity's own owning module (`inapp`).

**Issue:** L11 states explicitly: "no feature module imports another feature module's entity." Every
other cross-module interaction in this codebase honors this without exception — `DeliveryOrchestrator`
(module `delivery`) never imports `ContactProjection` (module `preference`); it calls
`ContactProjectionUpdater.findEmail(UUID) -> Optional<String>`, a same-package public service method
returning a primitive, never the entity itself. `InAppChannel`'s own design breaks that pattern for
the first time in this pipeline — a real architectural inconsistency, not a stylistic nitpick, since
L11 is an explicitly LOCKED decision this task's own frozen brief claimed to honor.

**Recommendation:** Introduce a same-package (`inapp`) public gateway — mirroring
`ContactProjectionUpdater`'s own established role for `ContactProjectionRepository` — that
`InAppChannel` calls with primitive arguments (`accountUuid`, `category`, `title`, `body`, `link`,
`createdAt`) and that returns only what `InAppChannel` needs (e.g. the notification's own view, or
nothing). `InAppChannel` itself should never reference `InappNotification`/
`InappNotificationRepository` directly. This also reopens the Phase 5 rationale for
`InappNotificationRepository` being `public` — with a proper gateway in place, the repository could
revert to package-private, matching every sibling repository's own convention exactly, rather than
being the sole exception.

---

## Finding 2 · `ApiExceptionHandler`'s blanket `Exception` handler may shadow Spring's own
framework-level error handling

**Severity:** Medium

**Evidence:** `common/ApiExceptionHandler.java`'s `handleUnexpected` is
`@ExceptionHandler(Exception.class)` — the broadest possible catch, inside a globally-applied
`@RestControllerAdvice`.

**Issue:** Spring Boot's own default exception resolution (e.g. `NoResourceFoundException` for an
unmapped path, method-not-allowed, etc.) is itself implemented via exception handling internal to
the framework. A blanket `@ExceptionHandler(Exception.class)` registered in application code takes
precedence over those framework defaults for any exception type it's broad enough to match,
potentially turning what should be an ordinary 404/405 for an unrelated, mistyped URL into a generic
500 instead — a worse, less accurate response than Spring's own default would have produced.

**Recommendation:** Narrow the catch-all, e.g. by excluding
`org.springframework.web.servlet.resource.NoResourceFoundException`/
`HttpRequestMethodNotSupportedException` (let Spring's own default handling manage those), or by
checking whether this concern is already avoided in practice (Spring Boot's own `ErrorController`
fallback may run first regardless, depending on servlet container wiring) before deciding this needs
a code change at all.

---

## Finding 3 · `InappStreamRegistry`'s dead-emitter cleanup is lazy, not guaranteed

**Severity:** Low

**Evidence:** `inapp/InappStreamRegistry.java` creates every `SseEmitter` with `NO_TIMEOUT = 0L` (no
timeout at all). Cleanup for a broken connection relies on the servlet container detecting the break
and firing `onError`/`onCompletion`, or on the *next* `push()` attempt for that same account hitting
an `IOException`/`IllegalStateException`.

**Issue:** An account that connects and then vanishes (e.g. a network drop the container doesn't
promptly detect) without ever triggering another notification leaves its own dead `SseEmitter`
sitting in the registry's own map indefinitely — a slow, unbounded memory-growth path under
sustained real-world usage, even though each individual leaked entry is small.

**Recommendation:** Consider a bounded timeout (e.g. a long but finite value, with the client expected
to reconnect) instead of `0L`, or document this as an accepted, disclosed limitation at this task's
own scale (launch traffic is not expected to accumulate enough dead connections for this to matter
in practice).

---

## Finding 4 · `deriveTitle`'s own truncation can split a UTF-16 surrogate pair

**Severity:** Low

**Evidence:** `channel/InAppChannel.java`'s `deriveTitle` calls `body.substring(0, TITLE_MAX_LENGTH)`
directly on `String.length()`/`String.substring`, which count/cut UTF-16 code units, not Unicode code
points.

**Issue:** If `message.body()` ever contains a character outside the Basic Multilingual Plane (e.g. an
emoji) whose own surrogate pair straddles exactly the 100-code-unit boundary, the truncated title
would end in a dangling, malformed surrogate. Not currently reachable — every real launch template
body is plain ASCII text (verified directly) — but a real, disclosed defensive gap should a future
template ever include such a character.

**Recommendation:** Use `body.codePointAt`/`Character.offsetByCodePoints` (or a small buffer of
codepoints) instead of `substring` if this is ever judged worth defending against, or document the
limitation explicitly.

---

## Finding 5 · `InappReadController.unread` returns an unbounded list

**Severity:** Low

**Evidence:** `inapp/InappReadController.java`'s `unread` returns the full result of
`findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc` with no pagination or limit.

**Issue:** An account with a very large unread backlog (e.g. one that never connects to the stream
and accumulates for months) would receive a correspondingly large, unbounded JSON response.

**Recommendation:** Not required by R17's own literal wording ("return their unread notifications"),
and no acceptance criterion names a page size — document as a disclosed, launch-scale limitation
rather than adding pagination speculatively.
