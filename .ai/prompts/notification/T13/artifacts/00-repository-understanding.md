# notification · T13 · Phase 0 — Repository Understanding

## 1. Architecture summary

Same overall architecture as every prior task (package-by-feature under
`com.themistra.notification`, single `notifications` Postgres schema, Flyway DDL-only, consume-only
at launch). **This is the first task in the entire service to add a REST/web surface** —
`spring-boot-starter-web` and `spring-boot-starter-oauth2-resource-server` have been `pom.xml`
dependencies since T03, and `ResourceServerConfig`/`PublicEndpoints` (T03) already build a complete
resource-server filter chain, but **zero `@RestController` classes exist anywhere in this module
today**. `ResourceServerConfig`'s own Javadoc already anticipates this exact task by name: *"the
in-app stream/read API this chain protects doesn't exist until task 13."*

The delivery path this task's own `InAppChannel` plugs into is fully wired since T11:
`DeliveryOrchestrator` resolves the `IN_APP` channel bean generically via
`channelsByName.get("IN_APP")` — no orchestrator change is needed for a new implementation to be
picked up, mirroring T12's own identical, already-proven experience replacing `NoOpEmailChannel`.

## 2. Existing code this task touches

**Already exists, directly reusable, no migration needed:**
- `inapp_notifications` table — already migrated by T02's own `V1__notifications_baseline.sql`
  (lines 65-76): `id` (identity PK), `notification_uuid UUID NOT NULL UNIQUE`,
  `account_uuid UUID NOT NULL`, `category VARCHAR(16) NOT NULL`, `title VARCHAR(256) NOT NULL`,
  `body TEXT NOT NULL`, `link VARCHAR(512)` (nullable), `read_at TIMESTAMPTZ` (nullable — null means
  unread), `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, plus a partial index
  `idx_inapp_unread ON inapp_notifications(account_uuid, created_at) WHERE read_at IS NULL` —
  clearly designed for exactly the unread-query this task's own `InappReadController` needs. **This
  is the first entity-backing task in this pipeline that needs zero new Flyway migration** — the
  table has waited, fully shaped, since T02.
- `common/config/InappProperties.java` (T03) — `transport` field, deliberately unconstrained
  (mirrors `EmailProperties.transport`'s own identical T03 precedent exactly). `application.properties`
  already sets `themistra.notification.inapp.transport=sse` — **O3/Q3 (SSE vs. WebSocket) was
  already resolved in favor of SSE at T03**, exactly as O2/Q2 (SES vs. SendGrid) was already resolved
  in favor of SES before T12 began. This task does not re-litigate the transport choice.
- `common/ResourceServerConfig.java` / `common/PublicEndpoints.java` (T03) — the complete resource-server
  filter chain (stateless, JWT bearer, RFC 9457 401/403 handlers) already exists and already protects
  every non-actuator path by default (`auth.anyRequest().authenticated()`). No new security
  configuration is needed for basic authentication — only the recipient-scoping logic (L8's own
  "scope results to the caller's `sub`") is genuinely new.
- `channel/NotificationChannel.java` (T11) — the interface `InAppChannel` implements.
- `channel/NoOpInAppChannel.java` (T11) — the placeholder this task replaces. Its own Javadoc doesn't
  explicitly pre-authorize a specific future class name the way `NoOpEmailChannel`'s did for
  `EmailChannel`, but the established "temporary NoOp → real implementation, same interface" pattern
  (T06→T11, T11→T12) applies identically here.
- `template/TemplateRenderer.RenderedMessage` (T09) — the already-rendered `subject`/`body`/`version`
  `InAppChannel.send` receives (its own `subject` is always `null` for `IN_APP` templates per T02's
  own seed data — every `IN_APP`-channel row in `V3__seed_launch_templates.sql` has a `NULL` subject
  column).
- **A real, verified precedent for what the JWT's own `sub` claim contains**: `auth-service`'s own
  `ApiKeyTokenIssuer.java:75` sets `.subject(accountUuid.toString())` when minting a token — the
  `sub` claim is the account UUID's own string form, the same correlation key already used
  throughout this service (`contact_projection.account_uuid`, `delivery_log.account_uuid`,
  `inapp_notifications.account_uuid`).

**New, this task's own deliverable (per `design.md` §6):**
- `inapp/InappNotification.java` / `InappNotificationRepository.java` — the first genuinely
  constructible entity/repository pair for this table.
- `channel/InAppChannel.java` — real, replacing `NoOpInAppChannel`. **Naming note**: `tasks.md`'s own
  task-13 prose says `InappChannel`, but `design.md`'s own file map (§6, line 212) says
  `InAppChannel.java`, exactly matching `NoOpInAppChannel`'s own already-established capitalization.
  I read this as a prose typo in `tasks.md`, not a real naming decision — `InAppChannel` is the
  correct name, consistent with the established precedent this task itself replaces.
- `inapp/InappStreamController.java` — SSE endpoint (R16, L8).
- `inapp/InappReadController.java` — unread REST endpoint (R17, L8).
- Likely `common/ApiExceptionHandler.java` — `design.md`'s own file map lists this under `common/`
  but it does not exist yet; no controller has ever needed RFC 9457 error responses for anything
  other than the 401/403 cases `ResourceServerConfig` already covers. Whether this task must build it
  (vs. deferring generic validation-error handling) is a Phase 1/2 scoping question.

## 3. Established patterns to follow

- **Package-by-feature, `inapp/` as its own top-level module** — matches `design.md` §6's own file
  map exactly (`inapp/` sits alongside `consumer/`, `preference/`, `template/`, `delivery/`,
  `channel/`, not nested under `channel/`).
- **Explicit `Clock` injection, never a DB default, for any timestamp the application controls**
  (T04 precedent, reused at T05/T11) — `inapp_notifications.created_at` has a DB-level
  `DEFAULT now()`, but every prior entity-writing task in this service (`ProcessedEvent`,
  `ContactProjection`, `DeliveryLog`) explicitly passes a `Clock`-derived instant rather than relying
  on that default, for testability. The same pattern should very likely apply here — a candidate
  finding to confirm at Phase 1/3, not assumed as settled without saying so.
- **Application-side UUID generation for an entity's own external identifier** (`DeliveryLog`'s
  DB-generated identity `id` vs. `notification_uuid`'s own separate, presumably app-generated column
  — mirrors no exact precedent yet, since every prior entity's own "external identifier" was supplied
  by an inbound event, not generated fresh; this is the first task minting a brand-new external UUID
  itself).
- **RFC 9457 for every error response** (`agents.md` Security rule) — `ResourceServerConfig` already
  covers 401/403; this task's own controllers need to decide how a 404 (unknown/foreign notification)
  or a 400 (malformed request) is rendered.
- **Zero trust, `sub`-scoped results** (L8) — this task is the first to need the "extract the
  caller's own identity from the validated JWT and scope a query/stream to it" pattern anywhere in
  the codebase.

## 4. Testing conventions

- Unit/integration split, fixed `Clock`, Testcontainers (Postgres) — same as every prior task.
- **`spring-security-test` is already a `pom.xml` test dependency** (confirmed), which provides
  `SecurityMockMvcRequestPostProcessors.jwt()` — the standard mechanism for constructing a mock,
  already-validated JWT with a chosen `sub` claim in a `MockMvc`-based controller test, without
  needing a real Auth JWKS endpoint reachable in tests.
- **No established SSE-testing pattern exists anywhere in this codebase** — this will likely need
  either `MockMvc`'s own async-request support (`.andExpect(request().asyncStarted())` +
  `asyncDispatch(...)`) for a controller-level test, or a real embedded server
  (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) with a real HTTP/SSE client for a genuine
  end-to-end proof. A real design decision for Phase 5, not assumed here.

## 5. Known gaps / unknowns

- **I do not know how a live push reaches the correct service replica.** `agents.md` mandates
  "EKS ≥ 2 replicas in one consumer group" — if `InAppChannel.send()` runs on whichever replica
  happened to consume the triggering Kafka partition, and a given account's own SSE connection is
  held open on a *different* replica, a naive in-process-only push mechanism would never reach that
  client. This is the single most consequential open architectural question for this task — I have
  not found any existing infrastructure (Postgres `LISTEN`/`NOTIFY`, a fan-out topic, a shared
  pub/sub) anywhere in this codebase that would already solve it. Flagging honestly rather than
  guessing; this is squarely Phase 3's own job to resolve.
- **I do not know the exact reconnection/backfill contract for the SSE stream.**
  `design.md` §4b-O3 says only "include how the persisted unread set backs reconnects," and
  `package.md` says "in-flight in-app streams reconnect from the persisted unread set" — but neither
  states the actual mechanism (e.g., does the client always re-call the unread REST endpoint on
  every reconnect/mount regardless of the stream's own state, treating SSE as live-only with no
  history; or does the stream itself replay missed notifications via SSE's own standard
  `Last-Event-ID` reconnection header?).
- **I do not know whether `InAppChannel.send()` is responsible for both persisting the row and
  pushing it live**, or whether persistence and live delivery are two separate, decoupled concerns
  (e.g., the channel only persists, and a completely separate mechanism watches for new rows to
  push). Directly related to the replica-fan-out question above.
- **`common/ApiExceptionHandler.java`'s own scope is unclear** — `design.md`'s file map lists it, but
  no controller has ever needed one before; whether building it is genuinely required by this task's
  own acceptance criteria (vs. Spring Boot's own default error handling being sufficient for the
  read API's own small surface) is a Phase 1/2 scoping question, not resolved here.
