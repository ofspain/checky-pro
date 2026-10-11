STATUS: FROZEN (approved 2026-10-11, Human Approval gate)

# payment · T03 · Frozen Task Brief

## Phase 3 disposition (7 of 8 findings ACCEPTED, 1 REJECTED)

1. **403 unreachable with T03's own filter chain.** Real: `permitAll`+`authenticated` alone
   produces no authority-restricted path, so no request can trigger the `AccessDeniedHandler`
   until a scoped endpoint lands in a later task. Confirmed `notification-service` hit the exact
   same gap — caught late (its own Phase 11, not T03) and fixed by invoking the handler bean
   directly with mock request/response objects
   (`ResourceServerConfigIntegrationTest.accessDeniedHandlerWritesRfc9457ProblemJson`). **Applied
   now, not deferred:** the same direct-invocation test is required below (Required Tests).
2. **Required vs. optional fields unenumerated.** Real gap, **refined rather than applied
   verbatim**: re-reading `design.md` §4c's own defaults, only `receipt.bucket` and
   `receipt.kms-public-keys-url` have genuinely empty (`${VAR:}`) fallbacks — true placeholders
   meant to be filled in. `crypto.base-url`'s own fallback (`http://crypto-service`) is a
   legitimate, usable k8s-DNS-style default, not a placeholder, and is NOT forced-required.
   Everything else (cron expressions, timeouts, TTL, tolerance) keeps its own sensible default,
   validated only for format (non-blank/positive), never forced-missing.
3. **Missing `spring.threads.virtual.enabled=true`.** **REJECTED.** Verified directly: neither
   `services/auth` nor `services/notification` has this property at all — it is a
   `crypto-service`-specific choice tied to its own watcher-polling design (confirmed in that
   service's own `application.properties` comment: "agents.md names the watcher layer as the
   intended beneficiary"), not a missed platform convention. No change made.
4. **Missing `health.show-details=never`.** Confirmed present in both siblings — added.
5. **Missing Kafka `consumer.group-id`.** Confirmed present in `notification-service` — added
   (`payment-service`), even though the first real listener is a later task.
6. **`@WebMvcTest` needs a dummy controller.** Confirmed `notification-service` solved exactly
   this with a test-only `ResourceServerTestController` (`@RestController`, test scope only,
   itself originally a Kimi finding in that service's own Phase 3) — mirrored below.
7. **No secret/YAML negative-proof guard.** Real gap, extends T01's established convention —
   added to Required Tests.
8. **Missing Hikari pool / `search_path` settings.** Confirmed present in both siblings — added.

## Task

T03 — Config & resource server.

## Purpose

Give the service its first real `application.properties`, five validated
`@ConfigurationProperties` records, and a resource-server security filter chain validating JWTs
against auth-service's JWKS — so non-local startup fails loudly on missing config (L13) and every
non-actuator endpoint is zero-trust by construction (L10) before any business endpoint exists.

## Scope

**In:**
- `application.properties`: app name, profile, `server.port=8083`, datasource (as `payment_app`,
  with Hikari pool settings and `connection-init-sql=SET search_path TO payments, public`), JPA
  (`ddl-auto=validate`, `open-in-view=false`), `spring.flyway.enabled=false`, Kafka bootstrap
  servers + `consumer.group-id=payment-service`, resource-server `jwk-set-uri`/`issuer-uri`,
  actuator exposure including `health.show-details=never`, and the five new
  `themistra.payment.*` groups.
- Five `@ConfigurationProperties` records, with `receipt.bucket`/`receipt.kms-public-keys-url`
  the only two fields forced-required (no usable default) in non-local profiles.
- `PublicEndpoints` (3 patterns), `ResourceServerConfig` (filter chain + RFC 9457 401/403 bodies,
  including a real, now-reachable 403 proof via direct handler invocation).
- `PaymentServiceApplication` gains `@ConfigurationPropertiesScan`.
- A test-only `ResourceServerTestController` (never shipped in `src/main`).

**Out:**
- `spring.threads.virtual.enabled` (rejected, Phase 3 finding 3 — crypto-specific, not this
  service's own convention).
- Any entity, repository, controller (production), or consumer — tasks 5+.
- `@EnableScheduling` (task 4).

## Business Rules

- **R29** — 401/403 responses from this task's own filter chain are RFC 9457
  `application/problem+json`.

## Locked Decisions

- **L10** — zero trust: every non-public path requires a valid JWT against the Auth JWKS.
- **L13** — validated `@ConfigurationProperties` fail startup on missing/invalid config in
  non-local profiles, for the two genuinely required fields named above; no secret/credential
  committed anywhere.

## Dependencies

- Auth Service's real JWKS/issuer (`http://localhost:8080/oauth2/jwks`, `http://localhost:8080`).
- `payment_app` role (T02).

## Inputs

- `design.md` §4c's five property-group comment blocks.
- `services/crypto`/`services/notification`'s own `common/config/*.java`, `application.properties`,
  `ResourceServerConfig.java`, `ResourceServerTestController.java`, and
  `ResourceServerConfigIntegrationTest.java` (structural templates, including the
  direct-handler-invocation 403 test).

## Outputs

- `services/payment/src/main/resources/application.properties` (new).
- `services/payment/src/main/java/com/themistra/payment/common/config/{CryptoClientProperties,
  InvoiceProperties,ReceiptProperties,LedgerAnchorProperties,InvoiceExpirySweepProperties}.java`
  (new).
- `services/payment/src/main/java/com/themistra/payment/common/{PublicEndpoints,
  ResourceServerConfig}.java` (new).

## State Changes

None.

## Files to Create

- `services/payment/src/main/resources/application.properties`
- `services/payment/src/main/java/com/themistra/payment/common/config/CryptoClientProperties.java`
- `services/payment/src/main/java/com/themistra/payment/common/config/InvoiceProperties.java`
- `services/payment/src/main/java/com/themistra/payment/common/config/ReceiptProperties.java`
- `services/payment/src/main/java/com/themistra/payment/common/config/LedgerAnchorProperties.java`
- `services/payment/src/main/java/com/themistra/payment/common/config/InvoiceExpirySweepProperties.java`
- `services/payment/src/main/java/com/themistra/payment/common/PublicEndpoints.java`
- `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java`
- `services/payment/src/test/java/com/themistra/payment/common/ResourceServerTestController.java`
  (test scope only, never shipped)
- `services/payment/src/test/java/com/themistra/payment/common/ResourceServerConfigTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/config/ConfigPropertiesBindingTest.java`

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java` —
  add `@ConfigurationPropertiesScan`.

## Files NOT to Modify

- `services/payment/pom.xml`, `V1__payments_baseline.sql`, `V2__payment_app_role_and_grants.sql`.
- Anything under `spec/`.

## Acceptance Criteria

- **AC1** — all five `@ConfigurationProperties` records exist, `@Validated`, bound to the exact
  `design.md` §4c keys; `receipt.bucket`/`receipt.kms-public-keys-url` are the only two fields
  with no usable default (forced-required); every other field keeps its own sensible default.
- **AC2** — `PaymentServiceApplication` carries `@ConfigurationPropertiesScan`.
- **AC3** — `ResourceServerConfig` wires JWT validation; `PublicEndpoints.PATTERNS` is exactly
  `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`; everything else authenticated.
- **AC4** — 401 responses (via a real request against the test-only secured path) and 403
  responses (via direct `AccessDeniedHandler` invocation, since no authority-restricted path
  exists yet) are both RFC 9457 `application/problem+json`.
- **AC5** — startup fails in a non-local profile when `receipt.bucket` or
  `receipt.kms-public-keys-url` is missing/blank; succeeds in `local` with this task's own
  fixture values.
- **AC6** — `management.endpoints.web.exposure.include=health,info,prometheus`,
  `management.endpoint.health.probes.enabled=true`, and
  `management.endpoint.health.show-details=never` are all set.
- **AC7** (Phase 3 finding 5) — `spring.kafka.consumer.group-id=payment-service` is set.
- **AC8** (Phase 3 finding 8) — Hikari pool settings and
  `connection-init-sql=SET search_path TO payments, public` are set.

## Required Tests

- `ResourceServerConfigTest` (`@WebMvcTest`, Docker-free, using the test-only
  `ResourceServerTestController`): public paths reachable without a token; the test controller's
  own path is 401 without one, with an RFC 9457 body (AC3, AC4's 401 half).
- A direct `AccessDeniedHandler` invocation test (mock request/response, no Spring context),
  proving the 403 body is RFC 9457-shaped (AC4's 403 half) — mirrors
  `notification-service`'s own `accessDeniedHandlerWritesRfc9457ProblemJson` exactly.
- `ConfigPropertiesBindingTest` (`ApplicationContextRunner`, Docker-free): valid config binds for
  each of the five records; `receipt.bucket`/`receipt.kms-public-keys-url` missing/blank fails
  startup, asserted via `rootCause().isInstanceOf(X.class).hasMessageContaining(...)` — not a bare
  `hasFailed()` (AC1, AC5).
- A source-text guard confirming `@ConfigurationPropertiesScan` on the Application class (AC2).
- A negative-proof guard (Phase 3 finding 7): `application.properties` contains no credential
  string beyond the documented datasource-password carve-out, and no `.yaml`/`.yml` file exists
  under `src/main/resources`.

## Constraints

- **Security:** no secret/credential committed beyond the documented carve-out; `aud` is
  deliberately not validated (same rationale already established for crypto/notification).
- **Thread-safety / transaction boundaries / module boundaries / null handling / performance:**
  not applicable.

## Open Questions

None. `server.port=8083` remains a direct, precedent-based call (Phase 1), not escalated further
at this gate.
