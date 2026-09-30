# notification · T13 · Phase 1 — Specification Extraction

## Business Rules

- **R16.** WHEN an authenticated recipient connects to the in-app notification stream, THEN the
  system SHALL stream only that recipient's notifications and SHALL reject unauthenticated
  connections.
- **R17.** WHEN an authenticated recipient calls the in-app read API, THEN the system SHALL return
  their unread notifications and SHALL NOT return another account's notifications.

## Locked Decisions

- **L8.** Zero trust on the in-app surface — the stream and read API validate the recipient's JWT as
  an OAuth2 resource server against the Auth JWKS and scope results to the caller's own `sub`. No
  endpoint here is public (`PublicEndpoints`, T03, already excludes everything but actuator).
- **L5.** Channels behind one interface — `InAppChannel implements NotificationChannel`;
  `DeliveryOrchestrator` (T11) requires no change, mirroring T12's own identical, already-proven
  experience.
- **L11.** Module boundaries — `inapp/` is its own top-level package (per `design.md` §6's own file
  map), not nested under `channel/`; no cross-module entity import.

## Files involved

**Existing, read-only (interfaces/contracts this task must honour):**
- `channel/NotificationChannel.java` (T11) — the interface `InAppChannel` implements.
- `template/TemplateRenderer.RenderedMessage` (T09) — `send`'s third parameter; `subject` is always
  `null` for every seeded `IN_APP` template row.
- `common/ResourceServerConfig.java` / `common/PublicEndpoints.java` (T03) — the already-complete
  resource-server filter chain; not modified.
- `common/config/InappProperties.java` (T03) — `transport` already `"sse"`; O3/Q3 already resolved,
  not re-litigated here.

**Existing, to delete (mirrors T06→T11 and T11→T12's own identical precedent):**
- `channel/NoOpInAppChannel.java` — replaced by the real `InAppChannel`.
- `channel/NoOpInAppChannelTest.java` (T11) — necessary consequence once its own subject class is
  gone.

**Existing, requires zero migration (a first for this pipeline):**
- `inapp_notifications` table — already fully shaped by T02's own `V1__notifications_baseline.sql`
  (11 columns incl. the partial unread index); no new Flyway migration is authored by this task.

**New, this task's own deliverable (per `design.md` §6):**
- `inapp/InappNotification.java` / `InappNotificationRepository.java`.
- `channel/InAppChannel.java` (real, replacing `NoOpInAppChannel` — `design.md`'s own file map spells
  it `InAppChannel.java`; `tasks.md`'s own prose "InappChannel" is read as a typo, per Phase 0's own
  finding, not a distinct naming decision).
- `inapp/InappStreamController.java` (SSE, R16, L8).
- `inapp/InappReadController.java` (unread REST endpoint, R17, L8).
- `common/ApiExceptionHandler.java` — listed in `design.md`'s own file map alongside the two
  controllers above; this task is the first to introduce any REST surface at all, so it is the first
  task that could possibly need one. Read as in-scope here, minimally (only whatever the read API's
  own realistic error cases require — 401/403 already have handlers via `ResourceServerConfig`).

## Dependencies

`NotificationChannel` (T11), `TemplateRenderer.RenderedMessage` (T09), `Clock` (existing
`ClockConfig` bean, T04 precedent — `inapp_notifications.created_at` has a DB-level default, but
every prior entity-writing task in this service explicitly injects `Clock` instead of relying on
one). Spring Security's own `Jwt`/resource-server support (already configured, T03) for extracting
the caller's own `sub` claim. `spring-security-test`'s
`SecurityMockMvcRequestPostProcessors.jwt()` (already a `pom.xml` test dependency) for constructing
a mock authenticated caller in tests. No Kafka dependency of its own — `InAppChannel` is a
synchronous collaborator called by `DeliveryOrchestrator`, exactly like `EmailChannel` (T12); the
two controllers are synchronous HTTP entry points with no consumer of their own.

## Acceptance Criteria

1. **AC1.** `InAppChannel implements NotificationChannel`; `channel()` returns `"IN_APP"`.
   `NoOpInAppChannel` and its own test are deleted.
2. **AC2.** `InAppChannel.send` persists a new `inapp_notifications` row (account UUID, category,
   title, body, link if present, a freshly-generated `notification_uuid`, `created_at` from an
   injected `Clock`, `read_at` left `null`) for every call — mirrors `DeliveryLog`'s own established
   "always insert, application controls the identifier and the timestamp" precedent (T11), not the
   table's own DB-level `now()` default.
3. **AC3.** After persisting, `InAppChannel.send` makes a best-effort attempt to push the new
   notification to any currently-connected SSE stream for that same account. A missed live push
   (e.g. the connection is held by a different replica, or no connection is open at all) is not a
   failure of `send` itself — the persisted row remains the durable source of truth a client recovers
   from via reconnect and/or the read API (`package.md`'s own explicit "in-flight in-app streams
   reconnect from the persisted unread set" language).
4. **AC4.** `InappStreamController`'s own SSE endpoint requires a valid JWT (rejects unauthenticated
   connections, R16) and streams only the notifications belonging to the `sub` claim's own account —
   never a client-supplied account identifier. The endpoint's own path/parameters carry no account
   identifier at all; the caller's identity comes exclusively from the validated token, eliminating
   the cross-account attack surface by construction rather than by a runtime comparison alone.
5. **AC5.** `InappReadController`'s own unread endpoint requires a valid JWT and returns only unread
   (`read_at IS NULL`) notifications for the `sub` claim's own account, same "no client-supplied
   identifier" construction as AC4 (R17, L8).
6. **AC6.** Both controllers reject an unauthenticated request per `ResourceServerConfig`'s own
   already-existing filter chain (no new security configuration needed) — verified, not merely
   assumed, since this task is the first to actually exercise that chain against a real endpoint.

## Tests required

Named (`package.md` §8): `shouldStreamInAppNotificationsToAuthenticatedRecipientOnly` (R16),
`shouldReturnUnreadInAppNotificationsForCaller` (R17). Plus, implied by AC1-6: `InAppChannel`'s own
persistence + push-attempt behavior (unit + integration, mirroring `EmailChannel`/`FakeEmailTransport`'s
own T12 shape where useful); a rejected-unauthenticated-request test for both controllers; a
rejected-cross-account test proving account A can never see/stream account B's notifications even
under adversarial input; a real end-to-end integration test exercising the full
consume→persist→stream and consume→persist→read paths against a real Postgres instance with a real
(`spring-security-test`-constructed) JWT.

## Open Questions

No genuine blockers — both non-trivial questions Phase 0 flagged resolve via ordinary design
judgment, the same class of decision every prior task in this pipeline has made internally:

- **Cross-replica live push** (Phase 0's own flagged unknown): resolved by AC3 above — a
  best-effort, same-replica-only in-process push, with the persisted table as the durable fallback a
  client recovers from on reconnect. `package.md`'s own explicit language ("reconnect from the
  persisted unread set") already anticipates and accepts this exact tolerance; introducing new
  cross-replica infrastructure (Postgres `LISTEN`/`NOTIFY`, a pub/sub broker) that exists nowhere
  else in this codebase today would be disproportionate to a single task-statement sentence and is
  not adopted.
- **SSE reconnection/backfill contract** (Phase 0's own flagged unknown): resolved the same way — no
  `Last-Event-ID` replay mechanism is built; the read API is the durable backfill path a
  reconnecting client is expected to also call, exactly as `package.md` describes.
