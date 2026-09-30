<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T13 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T13 — In-app channel + SSE/read API |
| **Spec section** | In-app delivery / read API |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the T13 Phase 2 brief.

---

## Finding 1 · The source of `category` for the `inapp_notifications` row is not specified

**Severity:** High

**Evidence:**
- `inapp_notifications.category` is `VARCHAR(16) NOT NULL` (`V1__notifications_baseline.sql`).
- `InAppChannel.send(UUID accountUuid, String recipient, RenderedMessage message)` receives only `accountUuid`, `recipient`, and the rendered message.
- `RenderedMessage` contains only `subject`/`body`/`version`; it carries no category.
- The brief explicitly forbids modifying `NotificationChannel` or `TemplateRenderer`.

**Issue:** There is no way for `InAppChannel` to populate the required `category` column without either (a) extending the data passed across the `NotificationChannel` interface, (b) deriving category from the rendered body (unreliable), or (c) looking it up somewhere else. The brief as written cannot be implemented.

**Recommended brief amendment:**
Choose one of:
1. Add `category` (and optionally `notificationKind`/`templateName`) to the `NotificationChannel.send` signature or to a new `NotificationContext` parameter. This changes `NotificationChannel`, but it is the cleanest design.
2. Add a `category` field to `RenderedMessage` and have `TemplateRenderer` populate it from the mapping table. This changes `TemplateRenderer`, contrary to the brief.
3. Have `InAppChannel` call `DeliveryOrchestrator`'s mapping table or `PreferenceResolver` to infer category from the account/template. This couples `InAppChannel` to orchestrator internals, violating L11.

Option 1 is recommended. If the project insists on not changing `NotificationChannel`, then the table must drop the `NOT NULL` constraint on `category` or the column must be nullable in the entity, which weakens the data model.

---

## Finding 2 · The source of `title` for the `inapp_notifications` row is not specified

**Severity:** High

**Evidence:**
- `inapp_notifications.title` is `VARCHAR(256) NOT NULL`.
- The brief states `RenderedMessage.subject()` is always `null` for `IN_APP` templates.
- `InAppChannel.send` does not receive a separate title.

**Issue:** The required `title` column has no defined source. A null or generated placeholder would violate the NOT NULL constraint or produce poor user experience.

**Recommended brief amendment:**
Either:
1. Use the first N characters of `body` as the title (document the truncation rule), or
2. Pass a title through the channel interface/context (preferred), or
3. Make `title` nullable in the DB/entity and document that IN_APP notifications have no title at launch.

Option 2 is recommended if the channel interface is being extended for category anyway.

---

## Finding 3 · The source of `link` for the `inapp_notifications` row is not specified

**Severity:** Medium

**Evidence:**
- `inapp_notifications.link` is `VARCHAR(512)` and nullable.
- `RenderedMessage.body()` may contain a link (e.g., `verificationLink`, `resetLink`, `getStartedLink`).
- The brief does not say whether to extract a link, which link to extract, or how to handle multiple links.

**Issue:** A notification with a call-to-action link will store a plain body while the link is embedded in text, making the read API less useful. Conversely, blindly extracting the first URL from the body is fragile.

**Recommended brief amendment:**
Specify the link policy:
- Extract the first URL found in the rendered body via a simple regex and store it in `link`, leaving `body` unchanged; or
- Leave `link` null for all launch templates and document that deep links are rendered inline in `body`.

If the channel interface is extended, a structured `link` field could be passed explicitly.

---

## Finding 4 · The SSE push registry design is underspecified

**Severity:** Medium

**Evidence:**
- AC3 requires a best-effort same-replica push after persistence.
- The brief mentions an "in-process connection registry" must be thread-safe but does not define its API, lifecycle, or eviction policy.

**Issue:** Without specifics, the implementation could:
- Leak dead `SseEmitter` instances if disconnects/timeouts are not handled.
- Push only to one connection per account instead of all.
- Fail to remove emitters that throw on send, causing repeated errors.

**Recommended brief amendment:**
Specify:
1. A `ConcurrentHashMap<UUID, List<SseEmitter>>` (or `CopyOnWriteArrayList`) registry inside a small `@Component` (e.g., `InappStreamRegistry`).
2. On `SseEmitter.onCompletion`/`onTimeout`/`onError`, remove the emitter from the registry.
3. `InAppChannel.send` iterates all emitters for the account and calls `send(SseEmitter.event()...)`; any `IOException`/`IllegalStateException` from a dead emitter removes it.
4. A missed push is silently ignored (not a `send` failure).

---

## Finding 5 · Controller URL paths and response shapes are not specified

**Severity:** Medium

**Evidence:**
- AC4/AC5 describe behavior but do not give endpoint paths or response formats.
- The brief says this is the first REST/web surface for the service.

**Issue:** Inconsistent or undocumented paths make client integration and testing harder.

**Recommended brief amendment:**
Pin the paths and response shapes, e.g.:
- `GET /api/v1/inapp/stream` — SSE, produces `text/event-stream`.
- `GET /api/v1/inapp/notifications/unread` — JSON array of objects with `notificationUuid`, `category`, `title`, `body`, `link`, `createdAt`.

Also specify the SSE event name and data format (e.g., event name `notification`, data as JSON matching the read-API DTO).

---

## Finding 6 · Whether `InAppChannel.send` is transactional is not specified

**Severity:** Medium

**Evidence:**
- `InAppChannel.send` persists a row and is called inside `DeliveryOrchestrator.dispatch`'s `@Transactional` boundary.
- The brief does not say whether `send` is `@Transactional`.

**Issue:** If `send` is not annotated, `repository.save()` creates its own short transaction. A failure after the save (e.g., during the best-effort push) would not roll back the insert, which is probably desired. However, if `send` is annotated `@Transactional`, the insert joins the orchestrator's transaction and rolls back with it, keeping `inapp_notifications` consistent with `delivery_log`. On the other hand, the best-effort push would then happen before commit, so a stream consumer immediately querying might not see the row.

**Recommended brief amendment:**
Specify the transaction boundary. A sensible default is `@Transactional(propagation = Propagation.REQUIRED)` so the insert joins the orchestrator's transaction, mirroring `delivery_log` consistency. Document that the best-effort push is intentionally pre-commit and therefore may race with the read API.

---

## Finding 7 · The JWT `sub` claim parsing strategy is not specified

**Severity:** Medium

**Evidence:**
- AC4/AC5 require scoping to the JWT's own `sub` claim.
- The `sub` claim is typically a UUID string, but the Auth Service could theoretically use a different format.

**Issue:** If `sub` is not a valid UUID, parsing it with `UUID.fromString` will throw. The controllers need a consistent strategy and error handling.

**Recommended brief amendment:**
State that `sub` is expected to be the account UUID string and that an unparseable `sub` results in a 401 or 400 problem response. Add a test for a JWT with a malformed `sub`.

---

## Finding 8 · SSE connection behavior on initial connect is not specified

**Severity:** Low

**Evidence:**
- AC3 mentions only live push; the read API is the backfill path.
- The brief explicitly excludes `Last-Event-ID` replay.

**Issue:** It is unclear whether the stream should send existing unread notifications when a client first connects, or only new ones pushed after connect.

**Recommended brief amendment:**
Specify that the SSE stream sends only notifications pushed after the connection is established. The client uses the read API to fetch unread history on reconnect. This keeps the scope minimal and matches the brief.

---

## Finding 9 · `ApiExceptionHandler` scope is not specified

**Severity:** Low

**Evidence:**
- The brief says add "minimal RFC 9457 handling for whatever real error cases the read API introduces beyond the 401/403 `ResourceServerConfig` already covers."
- It does not list which exceptions to handle.

**Issue:** Without specifics, the handler might be too broad (catching all `Exception`) or too narrow (missing validation errors).

**Recommended brief amendment:**
Specify that `ApiExceptionHandler` handles at least:
- `IllegalArgumentException` from `InAppChannel` validation → 400 Bad Request (if exposed to callers; in practice it may never be);
- `IllegalStateException` / data-access errors → 500 Internal Server Error with a generic problem detail;
- any unexpected exception → 500 with no stack trace.

Also state that 401/403 are handled by the security filter chain and should not be duplicated.

---

## Finding 10 · `InappProperties.transport` is not used

**Severity:** Low

**Evidence:**
- `InappProperties` has a `transport` field, deliberately unconstrained in T03.
- The brief does not say whether `InAppChannel` should check this property or what its allowed values are.

**Issue:** A configuration value exists with no effect, which is confusing.

**Recommended brief amendment:**
Either:
1. Use `transport=sse` to activate `InAppChannel` (mirroring T12's `EmailProperties.transport` design), or
2. Document that `transport` is reserved for future use and that `InAppChannel` is always active in this task.

Option 1 provides symmetry with T12.

---

## Summary

The T13 brief correctly scopes the replacement of `NoOpInAppChannel` with a real persistence + best-effort push channel and introduces the service's first authenticated REST/SSE surface. The most critical gaps are **Findings #1 and #2**: the required `category` and `title` columns in `inapp_notifications` have no source in the current `NotificationChannel.send` contract, making the brief unimplementable without extending the interface or weakening the data model. **Finding #3** (link source) and **Finding #4** (SSE registry details) are medium-severity design ambiguities. Findings #5–10 are smaller but worth pinning for consistent implementation and testing.
