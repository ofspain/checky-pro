# payment · T03 · Phase 1 — Specification Extraction

## Business Rules

- **R29** — any 4xx this task's resource-server wiring produces (401 unauthenticated, 403
  insufficient-authority) is RFC 9457 `application/problem+json`, stable `type`, no stack traces,
  no internal detail.

No other R-number applies — this task is foundational wiring, like T01/T02.

## Locked Decisions

- **L10** — zero trust at the edge: every non-internal endpoint requires a valid JWT validated as
  an OAuth2 resource server against the Auth Service JWKS; the service never assumes ingress
  authenticated the caller. (This task's own `PublicEndpoints` allowlist and `ResourceServerConfig`
  are precisely what makes L10 real — no code exists yet that L10 governs beyond actuator/health,
  but the filter chain itself is the mechanism.)
- **L13** — secrets discipline: validated `@ConfigurationProperties` fail startup on missing/
  invalid config in non-local profiles — this is the task statement's own last sentence, verbatim.

## Files involved

**New:**
- `services/payment/src/main/resources/application.properties` — first creation for this service.
- `services/payment/src/main/java/com/themistra/payment/common/config/*.java` — five
  `@ConfigurationProperties` records (resolved by precedent, not an open question: `design.md`
  §4c's own five comment-delimited groups map 1:1 — `CryptoClientProperties`, `InvoiceProperties`,
  `ReceiptProperties`, `LedgerAnchorProperties`, `InvoiceExpirySweepProperties` — rather than
  merging `InvoiceProperties`/`InvoiceExpirySweepProperties` just because they share a
  `themistra.payment.invoice` property-name prefix; Spring Boot binds each `@ConfigurationProperties`
  class by its own declared prefix independently, so a "stacked" prefix causes no conflict).
- `services/payment/src/main/java/com/themistra/payment/common/PublicEndpoints.java` — narrower
  than crypto's own list (no well-known path exists for this service): `/actuator/health/**`,
  `/actuator/info`, `/actuator/prometheus` only, matching the task statement's own parenthetical.
- `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java`.

**Existing, to modify:**
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java` — gains
  `@ConfigurationPropertiesScan` (T01's own Javadoc already pre-announced this exact task).

## Dependencies

- Auth Service's real, already-built JWKS/issuer endpoint (`http://localhost:8080/oauth2/jwks`,
  `http://localhost:8080`) — standard Spring Boot resource-server properties, no custom `JwtDecoder`
  bean (mirrors both siblings).
- `payment_app` DB role (T02, already real) — this task's own `spring.datasource.*` properties
  connect as it, never the admin/migration role.
- No contract file is read or written by this task (the header's listed contracts describe the
  service's eventual surface, not anything T03 touches directly).

## Acceptance Criteria

- **AC1** — five `@ConfigurationProperties` records exist, each validated (`@Validated` +
  `jakarta.validation` constraints appropriate to its own fields), bound to the exact property
  keys `design.md` §4c names.
- **AC2** — `PaymentServiceApplication` carries `@ConfigurationPropertiesScan`.
- **AC3** — `ResourceServerConfig` wires JWT validation against the Auth JWKS/issuer; every
  non-public path requires authentication; `PublicEndpoints.PATTERNS` is exactly
  `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`.
- **AC4** — 401/403 responses are RFC 9457 `application/problem+json` (R29), mirroring both
  siblings' identical `AuthenticationEntryPoint`/`AccessDeniedHandler` shape.
- **AC5** — startup fails in a non-local profile (`dev`/`staging`/`prod`) when a required config
  value is missing/invalid (L13); startup succeeds in `local` with the fixture values this task's
  own `application.properties` provides.
- **AC6** — `management.endpoints.web.exposure.include=health,info,prometheus` and
  `management.endpoint.health.probes.enabled=true` are set — without these, `PublicEndpoints`'
  own allowlisted paths 404 regardless of the security config (named lesson, confirmed in both
  siblings' own application.properties).

## Tests required

No pre-mapped named test in `package.md` §8 (same situation as T01/T02 — no R-number). Per
Phase 0's own cited conventions:
- `com.themistra.payment.common.ResourceServerConfigTest` (or similar) — `@WebMvcTest` +
  `SecurityMockMvcRequestPostProcessors.jwt()`, Docker-free: public paths reachable without a
  token, everything else 401 without one, RFC 9457 shape on 401/403 (AC3, AC4).
- One `ApplicationContextRunner`-based test per `@ConfigurationProperties` record (5 total, or
  fewer if several share one test class), each asserting: valid config binds correctly; a specific
  missing/invalid field fails startup with `rootCause().isInstanceOf(X.class)
  .hasMessageContaining(...)` — not a bare `hasFailed()` (named lesson from crypto's own T03,
  re-confirmed in Phase 0: a generic failure assertion can pass for the wrong reason) (AC1, AC5).
- A guard asserting `PaymentServiceApplication` carries `@ConfigurationPropertiesScan` (AC2) — a
  simple source-text check, same convention as T01's own Application-class checks.

## Open Questions

None that block this task. Both items Phase 0 flagged are resolved above:
- Five separate `@ConfigurationProperties` records, not merged — resolved by re-reading
  `design.md` §4c's own five comment-delimited groups directly (not a design choice invented
  here).
- `server.port`: **resolved to claim `8083`** — auth uses `8080`, notification already claimed
  `8082` specifically to avoid that exact collision; `8083` is the next free value and follows the
  same, already-established reasoning rather than repeating crypto's still-unfixed gap. (If this
  choice should instead be deferred to a dedicated infra/ports task, that is a genuine judgment
  call worth flagging to the human at Phase 4's own gate, not resolved silently past that point.)
