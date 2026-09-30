<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T13 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T13 — In-app channel + SSE/read API |
| **Spec section** | In-app delivery / read API |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T13 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T13 regression-guard tests against the acceptance criteria and `package.md` §8 named tests.

---

## Gap 1 · No test proves `ApiExceptionHandler` does not turn a 404 into a 500

**Why it matters:** `ApiExceptionHandler` uses `@ExceptionHandler(RuntimeException.class)`. `NoResourceFoundException` (Spring 6.1's 404 signal) is itself a `RuntimeException`. Without a more specific exclusion, the handler could convert an ordinary 404 for a mistyped path into a generic 500 problem detail. The implementation's Javadoc claims this was verified directly, but the test suite does not lock that behavior.

**Suggested test:** In `InappReadControllerTest` or a new `ApiExceptionHandlerTest`, perform a `GET` to a non-existent path with an authenticated JWT and assert `status().isNotFound()` (and that the response is not the generic 500 problem detail). Also assert `HttpRequestMethodNotSupportedException` (405) is not swallowed.

---

## Gap 2 · No test exercises `deriveTitle` with non-BMP Unicode

**Why it matters:** `deriveTitle` truncates with `String.substring`, which cuts on UTF-16 code units. A character outside the Basic Multilingual Plane (e.g., an emoji) straddling the 100-unit boundary would produce an invalid surrogate pair. The existing tests only use ASCII 'x' repeated 100 or 101 times.

**Suggested test:** Add a test in `InAppChannelTest` with a body containing a supplementary character near the boundary and assert truncation preserves valid code points (or document the limitation). This protects against future template content that includes emoji or other non-BMP characters.

---

## Gap 3 · No test verifies `InappReadController` ordering or large-result behavior

**Why it matters:** The read endpoint returns `findByAccountUuidAndReadAtIsNullOrderByCreatedAtDesc`. The current test uses a single notification, so it does not prove descending order or expose the unbounded-list risk noted in Phase 8 Finding #5.

**Suggested test:** Add a test where the repository returns two notifications with different `createdAt` values and assert the JSON array is ordered newest-first. Optionally, add a test that documents/locks a launch-scale assumption about maximum unread count (or add a repository-level limit if one is chosen).

---

## Gap 4 · No test proves `InappNotificationRepository` excludes already-read rows

**Why it matters:** The query method name says `AndReadAtIsNull`, but no test ever inserts a row with a non-null `read_at` and asserts it is excluded. A regression in the method name or Spring Data query derivation would silently change behavior.

**Suggested test:** Add an `InappNotificationRepositoryIntegrationTest` (or extend `InAppChannelIntegrationTest`) that persists one unread and one read row for the same account and asserts the read endpoint returns only the unread one.

---

## Gap 5 · No test exercises `InappStreamRegistry` timeout/error callbacks

**Why it matters:** Dead-emitter cleanup is tested via `emitter.complete()`, which fires the `onCompletion` callback. The `onTimeout` and `onError` callbacks are not exercised. While they delegate to the same `deregister` method, a future refactor could diverge.

**Suggested test:** Use reflection or package-private access to trigger `onTimeout`/`onError` on a registered emitter and assert the emitter is removed and the account entry is cleaned up when empty.

---

## Gap 6 · No test exercises concurrent register and push for the same account

**Why it matters:** The registry uses a `ConcurrentHashMap` of `CopyOnWriteArrayList`s, but no test interleaves `register` and `push` on the same account from multiple threads. A race between adding a new emitter and pushing to the list could theoretically drop a message or throw.

**Suggested test:** Add a concurrent test where one thread repeatedly registers emitters for an account while another thread repeatedly pushes to that account, then assert no exceptions and that all registered emitters are still present at the end.

---

## Gap 7 · No end-to-end HTTP integration test for the controllers

**Why it matters:** `InappStreamControllerTest` and `InappReadControllerTest` are `@WebMvcTest` slices with mocked collaborators. `InAppChannelIntegrationTest` calls the controllers directly (not via HTTP) and uses a real DB. There is no test that boots the full context with a real HTTP port and hits `/notifications/stream` or `/notifications/unread` through the actual servlet container + security filter chain.

**Suggested test:** Add a full `@SpringBootTest(webEnvironment = RANDOM_PORT)` integration test (with Testcontainers Postgres) that uses `TestRestTemplate` or `WebTestClient` with a JWT to call the read endpoint and assert the real JSON response. For SSE, a `WebTestClient` can consume the stream; at minimum, assert the response status and content type.

This is lower priority than the other gaps because `@WebMvcTest` already proves security scoping and the integration test proves DB round-trips, but it would close the HTTP-wire loop.

---

## Gap 8 · No test verifies the SSE response content type

**Why it matters:** AC4 says the stream endpoint is SSE. The current `InappStreamControllerTest` only asserts `asyncStarted()`, not that the response content type is `text/event-stream`.

**Suggested test:** In `InappStreamControllerTest`, add `.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE))`.

---

## Gap 9 · No test verifies `InAppChannel` with a non-SECURITY category

**Why it matters:** All `InAppChannelTest` cases use `"SECURITY"`. The category is passed through unchanged, but a test for `"PAYMENT"` would lock that the channel does not accidentally hardcode or transform it.

**Suggested test:** Add a parameterized or duplicate test asserting the appender receives `"PAYMENT"` when passed.

---

## Gap 10 · No test verifies `InappStreamController` rejects a request with an empty/missing subject claim

**Why it matters:** The malformed-subject test uses `"not-a-uuid"`. An empty or missing `sub` claim is a different failure shape; Spring Security may reject it at the filter chain, but the behavior is not locked.

**Suggested test:** Add tests for a JWT with `subject("")` and a JWT with no subject at all, asserting the appropriate 401/400 response.

---

## Summary

The T13 test suite is now very strong: 35 new tests cover `InAppChannel` validation and title derivation, `InappNotification` mapping, `InappNotificationAppender` persistence and immediate push, `InappStreamRegistry` register/push/deregister bookkeeping (including the empty-map-entry cleanup fix), controller authentication and scoping via `@WebMvcTest`, and a real end-to-end integration test for both named `package.md` §8 tests (R16/R17) with post-commit push timing and rollback behavior. The Phase 8 findings are largely addressed in both production code and tests.

The most important remaining gap is **Gap 1**: without a test for 404/405 preservation, the `RuntimeException` fallback handler is a latent regression risk. **Gap 2** is the next most substantive (Unicode truncation). Gaps 3–10 are smaller coverage and robustness items.
