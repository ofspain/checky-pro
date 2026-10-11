# payment · T03 · Task Implementation Brief (TIB)

## Task

T03 — Config & resource server.

## Purpose

Give the service its first real `application.properties`, five validated
`@ConfigurationProperties` records covering Crypto client / invoice / receipt / ledger-anchor /
expiry-sweep settings, and a resource-server security filter chain validating JWTs against
auth-service's JWKS — so non-local startup fails loudly on missing config (L13) and every
non-actuator endpoint is zero-trust by construction (L10) before any business endpoint exists.

## Scope

**In:**
- `application.properties`: app name, profile, `server.port=8083`, datasource (as `payment_app`),
  JPA (`ddl-auto=validate`, `open-in-view=false` — safe and set now even with zero entities,
  mirroring `notification-service`'s own explicit precedent), `spring.flyway.enabled=false`,
  Kafka bootstrap servers, resource-server `jwk-set-uri`/`issuer-uri`, actuator exposure, and the
  five new `themistra.payment.*` groups.
- Five `@ConfigurationProperties` records: `CryptoClientProperties`, `InvoiceProperties`,
  `ReceiptProperties`, `LedgerAnchorProperties`, `InvoiceExpirySweepProperties`.
- `PublicEndpoints` (3 patterns), `ResourceServerConfig` (filter chain + RFC 9457 401/403 bodies).
- `PaymentServiceApplication` gains `@ConfigurationPropertiesScan`.

**Out:**
- Any entity, repository, controller, or consumer — tasks 5+.
- `@EnableScheduling` (not needed until task 4's outbox relay).
- ShedLock's actual programmatic wiring (the `ledger.anchor.cron`/`invoice.expiry-sweep.cron`
  properties are declared now; the jobs that read them land in tasks 21 (skipped/deferred, Q4)
  and 8 respectively).

## Business Rules

- **R29** — 401/403 responses from this task's own filter chain are RFC 9457
  `application/problem+json`.

## Locked Decisions

- **L10** — zero trust: every non-public path requires a valid JWT against the Auth JWKS.
- **L13** — validated `@ConfigurationProperties` fail startup on missing/invalid config in
  non-local profiles; no secret/credential committed anywhere in `application.properties` (the
  datasource password placeholder is the same, already-documented local-dev carve-out from T02).

## Dependencies

- Auth Service's real JWKS/issuer (`http://localhost:8080/oauth2/jwks`, `http://localhost:8080`).
- `payment_app` role (T02).

## Inputs

- `design.md` §4c's own five property-group comment blocks (copied verbatim as defaults where the
  spec gives one, e.g. `default-ttl-minutes=60`).
- `services/crypto/.../common/config/*.java` and `services/notification/.../application.properties`
  (structural templates).

## Outputs

- `services/payment/src/main/resources/application.properties` (new).
- `services/payment/src/main/java/com/themistra/payment/common/config/{CryptoClientProperties,
  InvoiceProperties,ReceiptProperties,LedgerAnchorProperties,InvoiceExpirySweepProperties}.java`
  (new).
- `services/payment/src/main/java/com/themistra/payment/common/{PublicEndpoints,
  ResourceServerConfig}.java` (new).

## State Changes

None (no database or runtime state — configuration and security wiring only).

## Files to Create

- `services/payment/src/main/resources/application.properties`
- `services/payment/src/main/java/com/themistra/payment/common/config/CryptoClientProperties.java`
- `services/payment/src/main/java/com/themistra/payment/common/config/InvoiceProperties.java`
- `services/payment/src/main/java/com/themistra/payment/common/config/ReceiptProperties.java`
- `services/payment/src/main/java/com/themistra/payment/common/config/LedgerAnchorProperties.java`
- `services/payment/src/main/java/com/themistra/payment/common/config/InvoiceExpirySweepProperties.java`
- `services/payment/src/main/java/com/themistra/payment/common/PublicEndpoints.java`
- `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java`
- `services/payment/src/test/java/com/themistra/payment/common/ResourceServerConfigTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/config/ConfigPropertiesBindingTest.java`
  (one class covering all five records' binding/validation behavior, or split if that reads
  better in Phase 5's own plan — not decided here)

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java` —
  add `@ConfigurationPropertiesScan`.

## Files NOT to Modify

- `services/payment/pom.xml`, `V1__payments_baseline.sql`, `V2__payment_app_role_and_grants.sql`
  (T01/T02's own files).
- Anything under `spec/`.

## Acceptance Criteria

- **AC1** — all five `@ConfigurationProperties` records exist, `@Validated`, bound to the exact
  `design.md` §4c keys, each with constraints appropriate to its own fields (e.g. a URL/base-path
  string non-blank, timeouts positive, cron expressions non-blank).
- **AC2** — `PaymentServiceApplication` carries `@ConfigurationPropertiesScan`.
- **AC3** — `ResourceServerConfig` wires JWT validation; `PublicEndpoints.PATTERNS` is exactly
  `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`; everything else authenticated.
- **AC4** — 401/403 bodies are RFC 9457 `application/problem+json`.
- **AC5** — startup fails in a non-local profile when a required value is missing/invalid;
  succeeds in `local` with this task's own fixture values.
- **AC6** — `management.endpoints.web.exposure.include=health,info,prometheus` and
  `management.endpoint.health.probes.enabled=true` set.

## Required Tests

- `ResourceServerConfigTest` (`@WebMvcTest`, Docker-free): public paths reachable without a
  token; everything else 401 without one; RFC 9457 shape on 401/403 (AC3, AC4).
- `ConfigPropertiesBindingTest` (`ApplicationContextRunner`, Docker-free): valid config binds for
  each of the five records; a specific missing/invalid field per record fails startup, asserted
  via `rootCause().isInstanceOf(X.class).hasMessageContaining(...)` — not a bare `hasFailed()`
  (AC1, AC5).
- A source-text guard confirming `@ConfigurationPropertiesScan` on the Application class (AC2).

## Constraints

- **Security:** no secret/credential committed; `aud` is deliberately not validated (same
  rationale already established for crypto/notification — `client_credentials` tokens carry the
  caller's own client id as `aud`, not a resource indicator).
- **Thread-safety / transaction boundaries / module boundaries / null handling / performance:**
  not applicable — no behavior beyond config binding and the security filter chain.

## Open Questions

No blockers. `server.port=8083` is included in Scope as a direct, precedent-based call (Phase 1) —
flagged for the human to override at the Phase 4 gate if it should be decided differently.
