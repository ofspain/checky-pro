# notification · T13 · Phase 2 — Task Implementation Brief

## Task

Implement `InAppChannel` (replacing `NoOpInAppChannel`): a real, non-stub `NotificationChannel` for
`IN_APP` that persists every notification to `inapp_notifications` and makes a best-effort attempt
to push it live to a connected SSE stream. Implement `InappStreamController` (SSE, recipient-scoped)
and `InappReadController` (unread REST endpoint), both authenticated and scoped exclusively to the
caller's own JWT `sub` claim, with no client-supplied account identifier anywhere in either
endpoint's own path or parameters.

## Purpose

The second, final "temporary NoOp → real implementation" conversion for the launch channels
(after T12's `EmailChannel`) — and the first task to give this service any REST/web surface at all.
Closes the loop for the `IN_APP` half of every notification `DeliveryOrchestrator` (T11) already
resolves and renders, and gives a recipient the two ways `package.md` promises to see it: a live
stream and a durable unread list.

## Scope

**In:**
- `channel/InAppChannel.java` — `@Component`, `implements NotificationChannel`, `channel()` returns
  `"IN_APP"`. Persists first, then attempts a best-effort same-replica live push; a missed push is
  not a `send` failure.
- `inapp/InappNotification.java` / `InappNotificationRepository.java` — the first genuinely
  constructible entity/repository for the already-migrated `inapp_notifications` table (T02); no new
  Flyway migration.
- `inapp/InappStreamController.java` — SSE endpoint, JWT-authenticated, scoped to the caller's own
  `sub`.
- `inapp/InappReadController.java` — unread REST endpoint, same authentication/scoping.
- `common/ApiExceptionHandler.java` — minimal RFC 9457 handling for whatever real error cases the
  read API introduces beyond the 401/403 `ResourceServerConfig` (T03) already covers.
- Delete `channel/NoOpInAppChannel.java` and `channel/NoOpInAppChannelTest.java` (mirrors T06→T11 and
  T11→T12's own identical precedent).

**Out:**
- Any change to `DeliveryOrchestrator` (T11) — it already resolves any `"IN_APP"`-returning
  `NotificationChannel` bean generically.
- Any change to `TemplateRenderer`/rendering logic (T09).
- Any cross-replica live-push infrastructure (Postgres `LISTEN`/`NOTIFY`, a pub/sub broker) — the
  persisted table is the durable fallback a client recovers from on reconnect (Phase 1's own
  resolved reading of `package.md`'s explicit language).
- Any SSE `Last-Event-ID` replay mechanism — the read API is the backfill path.
- Email channel (T12, done), retry/dead-letter logic (task 14) — untouched.

## Business Rules

- **R16.** The SSE stream authenticates the connection and streams only that recipient's own
  notifications.
- **R17.** The read API authenticates the caller and returns only their own unread notifications,
  never another account's.

## Locked Decisions

- **L5.** Channels behind one interface — `InAppChannel implements NotificationChannel`; no coupling
  to `DeliveryOrchestrator` beyond that.
- **L8.** Zero trust on the in-app surface — both endpoints validate the JWT against the Auth JWKS
  (already configured, T03) and scope every result to the caller's own `sub`, never a client-supplied
  identifier.
- **L11.** Module boundaries — `inapp/` is its own top-level package; no cross-module entity import.

## Dependencies

`NotificationChannel` (T11, interface), `TemplateRenderer.RenderedMessage` (T09, message type),
`Clock` (existing `ClockConfig` bean, T04 precedent), Spring Security's own resource-server `Jwt`
support (already configured, T03), `spring-security-test`'s `SecurityMockMvcRequestPostProcessors.jwt()`
(already a test dependency) for constructing an authenticated test caller.

## Inputs

`InAppChannel.send(UUID accountUuid, String recipient, TemplateRenderer.RenderedMessage message)` —
`recipient` is the account UUID's own string form (T11's established `IN_APP` convention); `message`
carries `body`/`version` (`subject` is always `null` for `IN_APP` templates). Both controllers'
own input is an incoming HTTP request carrying a validated JWT; neither accepts an account identifier
as a path/query parameter.

## Outputs

`InAppChannel.send`: `void` — success is a normal return; a persistence failure is a thrown
exception, left uncaught here (mirrors `EmailChannel`'s own T12 precedent — `DeliveryOrchestrator`'s
own outcome-recording stays the single source of truth). `InappStreamController`: an SSE event
stream of the caller's own notifications. `InappReadController`: a JSON list of the caller's own
unread notifications.

## State Changes

Inserts one `inapp_notifications` row per `InAppChannel.send` call. `InappReadController` does not
itself change `read_at` in this task's own scope (marking read is not named in the task statement or
in R17's own wording — reading it as out of scope unless Phase 3 finds otherwise).

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/channel/InAppChannel.java`
- `services/notification/src/main/java/com/themistra/notification/inapp/InappNotification.java`
- `services/notification/src/main/java/com/themistra/notification/inapp/InappNotificationRepository.java`
- `services/notification/src/main/java/com/themistra/notification/inapp/InappStreamController.java`
- `services/notification/src/main/java/com/themistra/notification/inapp/InappReadController.java`
- `services/notification/src/main/java/com/themistra/notification/common/ApiExceptionHandler.java`

## Files to Modify

- `T01SkeletonRegressionTest.java` — expected, per every prior task's own unbroken precedent.

## Files NOT to Modify

- `channel/NotificationChannel.java`, `delivery/DeliveryOrchestrator.java` (T11).
- `common/ResourceServerConfig.java`, `common/PublicEndpoints.java` (T03) — the existing filter
  chain already protects every non-actuator path; no new security configuration is expected.
- `common/config/InappProperties.java` (T03).
- `db/migration/V1__notifications_baseline.sql` — the table already exists as needed.
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment` — no cross-service dependency for this task.

## Acceptance Criteria

1. **AC1.** `InAppChannel implements NotificationChannel`; `channel()` returns `"IN_APP"`.
   `NoOpInAppChannel` and its own test are deleted.
2. **AC2.** `send` persists a new row with a freshly-generated `notification_uuid`, the account UUID,
   category, title, body, an optional link, `created_at` from an injected `Clock`, and `read_at` left
   `null`.
3. **AC3.** After persisting, `send` makes a best-effort attempt to push the notification to any
   currently-connected same-replica SSE stream for that account; a missed push never fails `send`
   itself.
4. **AC4.** `InappStreamController` rejects an unauthenticated connection and streams only the
   notifications belonging to the JWT's own `sub` claim; the endpoint accepts no client-supplied
   account identifier.
5. **AC5.** `InappReadController` rejects an unauthenticated request and returns only unread
   (`read_at IS NULL`) notifications for the JWT's own `sub` claim; same "no client-supplied
   identifier" construction.
6. **AC6.** Both controllers' authentication/authorization behavior is verified against the real,
   already-existing `ResourceServerConfig` filter chain (T03), not assumed.

## Required Tests

Named (`package.md` §8): `shouldStreamInAppNotificationsToAuthenticatedRecipientOnly` (R16),
`shouldReturnUnreadInAppNotificationsForCaller` (R17). Plus: `InAppChannel`'s own persistence +
push-attempt behavior; an unauthenticated-request rejection test for both controllers; a
cross-account isolation test proving account A never sees/streams account B's notifications; a real
end-to-end integration test (real Postgres, real `spring-security-test`-constructed JWT) for both the
stream and read paths.

## Constraints

- **`send` must not swallow a persistence failure** (mirrors AC4's own T12 precedent) —
  `DeliveryOrchestrator`'s own outcome table depends on seeing it.
- **No new grant migration** — `inapp_notifications` was already granted at T02.
- **Zero trust, no client-supplied account identifier anywhere** — the entire cross-account attack
  surface is eliminated by construction (L8), not by a runtime equality check alone.
- **Stateless where possible; any in-process connection registry for the best-effort push must be
  thread-safe** — `InAppChannel.send` is called concurrently across Kafka listener container threads
  (T06's own `concurrency: 2` precedent), and an SSE controller's own connections are inherently
  concurrent HTTP requests.
- **`DeliveryOrchestrator` (T11) is not modified** — its generic `List<NotificationChannel>`
  injection already picks up any correctly-`@Component`-annotated `"IN_APP"`-returning bean.

## Open Questions

No blockers. Both non-trivial questions carried from Phase 0/1 (cross-replica live push, SSE
reconnection/backfill) were already resolved via design judgment at Phase 1 — the same class of
internally-resolved decision every prior task in this pipeline has made without escalation.
