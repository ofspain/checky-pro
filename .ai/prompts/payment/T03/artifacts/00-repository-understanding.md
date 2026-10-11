# payment · T03 · Phase 0 — Repository Understanding

## 1. Architecture summary (planned, per spec — T01/T02 built skeleton + schema only)

Per `design.md` §4c, five new configuration key groups are named by this task's own text: Crypto
Service client (`themistra.payment.crypto.*`), invoice/expiry (`themistra.payment.invoice.*`),
receipt/S3 (`themistra.payment.receipt.*`), ledger anchor cron (`themistra.payment.ledger.*`), and
invoice expiry-sweep cron (`themistra.payment.invoice.expiry-sweep.*`). This service is
resource-server-only (never an OIDC issuer, T01's own established finding) — `JwtDecoder` is
Spring Boot's own autoconfigured bean from standard `spring.security.oauth2.resourceserver.jwt.*`
properties, pointed at auth-service's real, already-built JWKS/issuer.

Current real state: `services/payment` has a pom (T01), a bare `PaymentServiceApplication`, and
the `payments` schema + `payment_app` role, migrated for real against local Postgres (T02). No
`application.properties` exists yet — this task creates it for the first time.

## 2. Existing code this task touches

Nothing in `services/payment/src/main/java` beyond `PaymentServiceApplication.java` exists yet.
This task adds:
- `services/payment/src/main/resources/application.properties` (new).
- `services/payment/src/main/java/com/themistra/payment/common/config/*Properties.java` (new,
  package doesn't exist yet).
- `services/payment/src/main/java/com/themistra/payment/common/{PublicEndpoints,
  ResourceServerConfig}.java` (new).
- `PaymentServiceApplication.java` gains `@ConfigurationPropertiesScan` (T01's own Javadoc already
  states this is deferred to "the task that actually introduces the thing it enables" — this is
  that task).

## 3. Established patterns to follow (read directly from both already-built sibling services'
real T03-equivalent work, not assumed)

- **One `@ConfigurationProperties` record per concern**, package `com.themistra.<service>.common
  .config`, named `<Concern>Properties`, `@Validated` with `jakarta.validation` constraints
  (confirmed: `services/crypto/.../FinalityProperties.java`, `ProviderProperties.java`). This
  task's own five property groups map to five records (or four, if invoice/expiry-sweep are
  naturally nested under one `InvoiceProperties` — flagged for Phase 2, not decided here).
- **`PublicEndpoints`**: a `final` class, private constructor, a `String[] PATTERNS` constant.
  Confirmed shape in `services/crypto/.../PublicEndpoints.java`. This task's own text says
  "health/info/prometheus only" — narrower than crypto's own list (no well-known path exists for
  payment-service).
- **`ResourceServerConfig`**: `@Configuration @EnableWebSecurity`, one `SecurityFilterChain` bean,
  stateless session policy, CSRF disabled (bearer-only API, no cookie session ever exists),
  `PublicEndpoints.PATTERNS` permitted, everything else authenticated, RFC 9457 401/403 bodies via
  a custom `AuthenticationEntryPoint`/`AccessDeniedHandler` — confirmed identical shape in both
  `services/crypto` and (by convention) `services/notification`. No `aud` validation (same
  rationale already established for crypto: `client_credentials` tokens carry the caller's own
  client id as `aud`, not a resource indicator).
- **`application.properties` baseline, confirmed identical across both siblings' own real files**:
  `spring.application.name`, `spring.profiles.active=local`, a **service-specific `server.port`**
  (notification explicitly set `8082` specifically to avoid clashing with auth's `8080` — its own
  comment notes crypto still has this latent, undocumented gap, unfixed since fixing a sibling's
  own file is out of scope for whichever task found it). `spring.datasource.*` connecting as the
  service's own least-privilege role (never the admin/migration role — T02's own already-learned
  lesson, cited verbatim in both siblings' comments), with `connection-init-sql=SET search_path TO
  <schema>, public`. `spring.jpa.hibernate.ddl-auto=validate` + `spring.jpa.open-in-view=false` —
  **notification's own comment explicitly states this is safe and intentional even with zero
  `@Entity` classes yet** ("a read-only startup check that stays harmless with zero entities, and
  is set now so no later task needs to revisit this line. Mirrors services/auth and
  services/crypto's own identical setting") — superseding crypto's own earlier, stricter
  T04-deferral of the same two lines. `spring.flyway.enabled=false` (migrations only ever run via
  the Maven plugin, confirmed convention, directly relevant given T02's own real migration run).
  `spring.kafka.bootstrap-servers=localhost:9094` (both siblings' shared local broker port — not
  9092, the Kafka default; `services/auth/compose.local.yaml`'s own EXTERNAL listener mapping).
  `spring.security.oauth2.resourceserver.jwt.{jwk-set-uri,issuer-uri}` (both required together —
  `issuer-uri` alone triggers Spring Boot's `JwtIssuerValidator`, confirmed named lesson from
  crypto's own T03). `management.endpoints.web.exposure.include=health,info,prometheus` +
  `management.endpoint.health.probes.enabled=true` (Spring Boot only exposes `health` over HTTP by
  default — a named lesson from crypto's own T03, re-confirmed by notification's own identical
  addition).
- **`PaymentServiceApplication` gains `@ConfigurationPropertiesScan`** in this exact task — T01's
  own Javadoc already pre-announced this ("added in T03, the first task to introduce any
  `@ConfigurationProperties` class" — crypto's exact wording; payment's own T01 Javadoc makes the
  equivalent, generic claim).

## 4. Testing conventions

Both siblings' own T03-equivalent work used `@WebMvcTest` + `SecurityMockMvcRequestPostProcessors
.jwt()` for the resource-server filter chain (no real JWT decoding, no Docker) and a separate
`ApplicationContextRunner`-based test for each `@ConfigurationProperties` record's own binding/
validation behavior (confirmed named lesson from crypto's own T03: `ApplicationContextRunner`
generic `hasFailed()` alone can pass for the wrong reason — must assert
`rootCause().isInstanceOf(X.class).hasMessageContaining(...)` to prove the *specific* validation
rule under test actually fired, not just that *some* constraint anywhere failed). Neither test
style needs Docker/Testcontainers, unlike T02's own suite.

## 5. Known gaps / unknowns

- **I do not know** whether the five config groups the task statement names should become five
  separate `@ConfigurationProperties` records or fewer (e.g., invoice settings + the expiry-sweep
  cron nested under one `InvoiceProperties`, since both live under the `themistra.payment.invoice`
  prefix at the property-name level). Flagging for Phase 2 rather than guessing — the frozen brief
  should decide this explicitly.
- **I do not know** whether payment-service should claim a specific `server.port` now (mirroring
  notification's own explicit `8082` choice) or leave it unset (crypto's still-latent gap). Given
  auth uses `8080` and notification uses `8082`, `8083` would be the next free, non-colliding
  value if this task's own scope is read to include it — flagging rather than assuming task 3's
  own text ("Config & resource server") implicitly covers `server.port`, since it isn't named
  explicitly.
- Task 3's own text does not mention `spring.jpa.hibernate.ddl-auto`/`open-in-view` explicitly
  either, but notification's own precedent treats both as part of the baseline "Config" task
  regardless of entity count — flagging the same way, for Phase 2 to decide explicitly rather than
  silently following precedent without recording the decision.
