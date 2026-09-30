STATUS: FROZEN

# notification · T13 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 10 findings assessed against the Phase 2 brief and this codebase's own established conventions
before disposition. Findings #1/#2 were independently re-verified by reading `NotificationChannel.java`
and `DeliveryOrchestrator.dispatchOneChannel` directly: `channelBean.send(accountUuid, recipient, message)`
is confirmed to carry no `category`, and `mapping.category()` is confirmed already available as a
local variable at that exact call site — Kimi's own claim that "the brief as written cannot be
implemented" is correct, not overstated.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | `category` has no source in `NotificationChannel.send` | High | **ACCEPTED (interface change)** | `NotificationChannel.send`'s signature gains a `String category` parameter, positioned before `message`: `send(UUID accountUuid, String recipient, String category, TemplateRenderer.RenderedMessage message)`. `DeliveryOrchestrator.dispatchOneChannel`'s own `channelBean.send(...)` call passes `mapping.category()` (already a local variable there today - a one-line change). `EmailChannel` (T12) gains the same parameter and ignores it (SES has no concept of "category"). This is a genuine, disclosed amendment to two already-frozen tasks' own artifacts (T11's `NotificationChannel`/`DeliveryOrchestrator`, T12's `EmailChannel`), not silently absorbed - see "Cross-task ripple," below. |
| 2 | `title` has no source | High | **ACCEPTED (derived from body, no further interface change)** | `title` is derived by `InAppChannel` itself: the first 100 characters of `message.body()`, with a `"…"` suffix if truncated. No further signature change - compounding Finding #1's own already-necessary change with a second one for `title` alone was rejected as disproportionate; there is no existing source for a real title anywhere in the current data model (no `IN_APP` template ever has a non-null `subject`), and adding one (e.g. a new templates-table column) would be a materially bigger, out-of-proportion schema change for this task's own scope. |
| 3 | `link` has no source | Medium | **ACCEPTED (always null at launch)** | `link` is always `null` for every notification this task writes. Regex-extracting a URL from free rendered text was considered and rejected as fragile (Kimi's own stated concern). Documented as a disclosed launch limitation - deep links render inline in `body` for now. |
| 4 | SSE push registry underspecified | Medium | **ACCEPTED**, adopting Kimi's own concrete proposal | New `inapp/InappStreamRegistry.java` (`@Component`): `ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>`. `InappStreamController` registers/deregisters an emitter on connect and on `onCompletion`/`onTimeout`/`onError`. `InAppChannel` (via the registry) iterates every emitter for an account and sends; any `IOException`/`IllegalStateException` from a dead emitter removes it from the registry. A missed push is silently ignored, never a `send` failure (AC3, unchanged). |
| 5 | Controller paths/response shapes unspecified | Medium | **ACCEPTED, with Kimi's own specific path prefix REJECTED** | Kimi's own suggested `/api/v1/inapp/...` prefix does not match this monorepo's own actual convention - verified directly: no service anywhere uses an `/api/v1` (or any version) prefix; `auth-service`'s own `AccountController` uses plain, unprefixed resource paths, including a `/accounts/me` pattern for "the caller's own resource, no client-supplied identifier" - the exact shape this task needs. Adopted paths: `GET /notifications/stream` (SSE, `text/event-stream`) and `GET /notifications/unread` (JSON array). No `/me/` segment needed - unlike `auth-service`, every endpoint in this service is inherently the caller's own view; there is no admin/other-account variant to disambiguate from. SSE event name: `notification`; event data: JSON matching the read endpoint's own DTO shape (`notificationUuid`, `category`, `title`, `body`, `link`, `createdAt`). |
| 6 | `send`'s transaction boundary + pre-commit push race unspecified | Medium | **ACCEPTED, resolved more strongly than Kimi's own literal recommendation** | `InAppChannel.send` is `@Transactional` (default `REQUIRED`), mirroring `ContactProjectionUpdater.upsertEmail`'s own established "explicit `@Transactional` on every write-path method" convention (T05) - joins `dispatch`'s own already-open transaction. Kimi's own recommendation for the pre-commit push race was to merely *document* it; instead, the push is deferred via `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()` - a standard, idiomatic Spring primitive for exactly this need, cheap to implement, and it eliminates the race entirely (a client can never receive a live push for a row the read API can't yet see) rather than merely disclosing it. If no transaction is active on the calling thread (defensive - not expected given the only real caller is `DeliveryOrchestrator`), the push fires immediately instead. |
| 7 | JWT `sub` parsing strategy unspecified | Medium | **ACCEPTED** | `sub` is parsed via `UUID.fromString`. A malformed `sub` is a **400 Bad Request** (RFC 9457, via `ApiExceptionHandler`), not 401 - the token already passed Spring Security's own signature/expiry validation (genuinely authenticated), so the problem is discovered only at the controller/business layer once its claims are read, not during authentication itself. |
| 8 | SSE initial-connect behavior unspecified | Low | **ACCEPTED**, exactly as recommended | The stream sends only notifications pushed *after* the connection is established; no backfill on connect. The read API is the sole backfill path (already resolved at Phase 1). |
| 9 | `ApiExceptionHandler` scope unspecified | Low | **ACCEPTED (narrowed)** | Handles exactly two cases: a malformed `sub` claim (Finding #7) → 400; any other unexpected exception → 500, generic detail, no stack trace. Kimi's own mention of "`IllegalArgumentException` from `InAppChannel` validation" is dropped - `InAppChannel` is an internal channel with no HTTP-facing validation surface of its own, so that case cannot occur through either controller. 401/403 remain exclusively `ResourceServerConfig`'s own responsibility, not duplicated. |
| 10 | `InappProperties.transport` unused | Low | **ACCEPTED (Option 2, Kimi's own Option 1 REJECTED)** | Unlike T12's `EmailProperties.transport` (which selects between a real AWS call and a CI-safe fake, a genuine safety-driven split), an in-process SSE push has no external, CI-unsafe side effect requiring a fake counterpart - manufacturing an artificial "fake" `InAppChannel` variant would serve no real purpose. `transport` stays reserved, unconsulted, forward-looking configuration for a possible future WebSocket alternative (matching O3's own literal "SSE vs WebSocket" framing); `InAppChannel`/`InappStreamController` are simply always SSE-based in this task's own scope. |

### Cross-task ripple (disclosed, not silent)

Finding #1's own interface change touches already-committed, already-frozen artifacts from **two
prior tasks**:
- `channel/NotificationChannel.java` (T11) — signature change.
- `delivery/DeliveryOrchestrator.java` (T11) — one call-site line, passing `mapping.category()`
  (already a local variable there).
- `channel/EmailChannel.java` (T12) — signature change, parameter ignored.
- `delivery/DeliveryOrchestratorTest.java` (T11) — ~17 `verify(...).send(any(), any(), any())`-shaped
  assertions each need a 4th `any()` matcher (mechanical).
- `channel/EmailChannelTest.java` (T12) — ~7 direct `channel.send(accountUuid, recipient, message)`
  calls each need an explicit `category` literal added (mechanical).

This is a real, necessary amendment surfaced by adversarial review catching a genuine interface gap
neither T11 nor T12's own review rounds caught (since neither task's own scope ever needed
`category` downstream of the channel boundary until now) - not scope creep, and not silently
absorbed into T13's own commit without this explicit note.

## Task

Unchanged from Phase 2, with all 10 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- `channel/NotificationChannel.java` (T11) — add `category` parameter to `send`.
- `delivery/DeliveryOrchestrator.java` (T11) — pass `mapping.category()` at the one call site.
- `channel/EmailChannel.java` (T12) — add and ignore the same parameter.
- `delivery/DeliveryOrchestratorTest.java`, `channel/EmailChannelTest.java` — mechanical updates for
  the new parameter.
- `inapp/InappStreamRegistry.java` — new (Finding #4).

**Out:** Unchanged from Phase 2, plus: no `templates` table/seed-data change (title is derived, not
sourced from a new column); no URL-extraction-from-body logic (`link` stays null); no WebSocket
implementation (reserved, unconsulted `transport` property only).

## Business Rules

Unchanged from Phase 1's own extraction (R16, R17).

## Locked Decisions

Unchanged from Phase 1's own extraction: L5, L8, L11.

## Dependencies

Unchanged from Phase 2, plus: `jakarta.servlet.http.HttpServletResponse`/Spring MVC's own
`SseEmitter` (already available via `spring-boot-starter-web`, no new Maven dependency),
`TransactionSynchronizationManager` (Spring's own transaction-synchronization API, already
transitively available via `spring-boot-starter-data-jpa`).

## Acceptance Criteria

Unchanged from Phase 1/2's own AC1-6, with AC2/AC3/AC4/AC5 now explicit per the dispositions above,
plus:
7. **AC7.** `category` and a derived `title` (first 100 chars of `body`, `"…"`-truncated) are
   persisted on every row; `link` is always `null` at launch.
8. **AC8.** `InAppChannel.send` is `@Transactional` (default `REQUIRED`); the best-effort SSE push
   fires only after the surrounding transaction commits (`TransactionSynchronizationManager`), never
   before - eliminating the read-API race Finding #6 identified, not merely disclosing it.
9. **AC9.** A malformed/unparseable JWT `sub` claim on either controller results in a
   `400 Bad Request` RFC 9457 response via `ApiExceptionHandler`, not a 401 and not a raw stack
   trace.
10. **AC10.** Controller paths are `GET /notifications/stream` (SSE) and `GET /notifications/unread`
    (JSON); the SSE event name is `notification`; both response shapes share one DTO
    (`notificationUuid`, `category`, `title`, `body`, `link`, `createdAt`).

## Files to Create / Modify / Delete

Unchanged from Phase 2, now itemized with the Finding #1/#4 additions:

**Create:** `channel/InAppChannel.java`, `inapp/InappNotification.java`,
`inapp/InappNotificationRepository.java`, `inapp/InappStreamController.java`,
`inapp/InappReadController.java`, `inapp/InappStreamRegistry.java`, `common/ApiExceptionHandler.java`.

**Modify:** `channel/NotificationChannel.java`, `delivery/DeliveryOrchestrator.java`,
`channel/EmailChannel.java` (all three: Finding #1's own signature ripple),
`delivery/DeliveryOrchestratorTest.java`, `channel/EmailChannelTest.java` (mechanical test updates),
`T01SkeletonRegressionTest.java`.

**Delete:** `channel/NoOpInAppChannel.java`, `channel/NoOpInAppChannelTest.java`.

## Required Tests

Unchanged from Phase 2, plus: a `NotificationChannel.send` signature-change regression proof (every
existing `DeliveryOrchestratorTest`/`EmailChannelTest` call site still compiles and passes with the
new parameter); `InappStreamRegistry`'s own register/deregister/dead-emitter-removal behavior;
`InAppChannel`'s own after-commit push timing (a rollback must never trigger a push at all); a
malformed-`sub` 400-response test for both controllers; a title-truncation boundary test (exactly
100 chars vs. over).

## Constraints

Unchanged from Phase 2, plus: `InappStreamRegistry`'s own map/lists must be safe for concurrent
register/deregister/iterate (SSE connections arrive and drop on arbitrary HTTP threads,
independently of the Kafka-listener threads calling `InAppChannel.send`).

## Open Questions

No blockers. All 10 Phase 3 findings resolved above, including one (Finding #6) resolved more
strongly than literally requested, and two (Findings #5, #10) where Kimi's own specific suggestion
was rejected in favor of an alternative better supported by this monorepo's own real, verified
conventions.
