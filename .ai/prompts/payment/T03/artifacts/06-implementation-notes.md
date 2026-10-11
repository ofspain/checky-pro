# payment · T03 · Phase 6 — Implementation Notes

## What changed

1. **`application.properties` (new)** — full baseline per the frozen brief's Scope: app name,
   profile, `server.port=8083`, datasource (as `payment_app`, Hikari pool, `search_path`),
   JPA (`ddl-auto=validate`, `open-in-view=false`), `spring.flyway.enabled=false`, Kafka
   (bootstrap servers + `consumer.group-id=payment-service`), resource server
   (`jwk-set-uri`/`issuer-uri`), actuator exposure (incl. `show-details=never`), and the five
   `themistra.payment.*` property groups.
2. **Five `@ConfigurationProperties` records (new)**: `CryptoClientProperties`,
   `InvoiceProperties`, `ReceiptProperties` (the two forced-required fields),
   `LedgerAnchorProperties`, `InvoiceExpirySweepProperties`.
3. **`PublicEndpoints`, `ResourceServerConfig` (new)** — mirror both siblings' exact shape,
   including the direct-handler-invocation 403 proof (Phase 3 finding 1).
4. **`PaymentServiceApplication` (modified)** — gains `@ConfigurationPropertiesScan`.
5. **`T01SkeletonRegressionTest` (modified)** — updated its own "exactly 1 class-level
   annotation" assertion to "exactly 2" and added a positive check for
   `@ConfigurationPropertiesScan`, mirroring `notification-service`'s own identical, already-real
   T01-test evolution at its own T03. Not in the plan's own file list, but required: leaving it
   unmodified would have left a known, certain-to-fail regression test in the suite the moment
   `PaymentServiceApplication` changed — not fixing it would have been worse than the narrow,
   justified scope extension.
6. **Test files (new)**: `ResourceServerTestController` (test-only), `ResourceServerConfigTest`,
   `AccessDeniedHandlerTest`, `ConfigPropertiesBindingTest`, `ApplicationPropertiesContentTest`.

## A real, disclosed deviation from `design.md` §4c's own verbatim text

`design.md` §4c gives `receipt.bucket`/`receipt.kms-public-keys-url` an **empty** `${VAR:}`
fallback. Implementing this literally would make `@NotBlank` fail in **every** profile, including
`local` — contradicting the frozen brief's own AC5 ("succeeds in local with this task's own
fixture values"). Resolved by giving both a concrete, non-blank, `local-only-fake-*`-style
default instead (a real env var still overrides it in any real environment) — the exact same
pattern `crypto-service` already uses for its own analogous S3 bucket field
(`themistra.crypto.snapshot.bucket=local-only-fake-observation-snapshots`). Disclosed in
`application.properties`'s own comment at the point of change, not silently diverged from the
spec's own verbatim block.

## A real bug found by actually running the tests, not assumed

Six of twelve `ConfigPropertiesBindingTest` methods failed on first run: I had assumed the root
cause of a `@ConfigurationProperties` validation failure (inside an `ApplicationContextRunner`)
would be `jakarta.validation.ConstraintViolationException`. The real root cause is Spring Boot's
own `org.springframework.boot.context.properties.bind.validation.BindValidationException` — its
own `ValidationBindHandler` wraps/reports the violation directly, never surfacing a bare
`ConstraintViolationException` as the deepest cause in this binding path. Fixed by changing the
assertion's expected exception type; re-ran, all pass.

## Mapping to the plan (Phase 5) and acceptance criteria

| Plan step | Done | AC |
|---|---|---|
| 1. `application.properties` | Yes (with the one disclosed deviation above) | AC1, AC6, AC7, AC8 |
| 2. Five `@ConfigurationProperties` records | Yes | AC1 |
| 3. `PaymentServiceApplication` + compile check | Yes | AC2 |
| 4. `PublicEndpoints`/`ResourceServerConfig` | Yes | AC3, AC4 |
| 5. Compile check | Yes (`mvn -pl services/payment compile` clean) | — |
| 6. `ResourceServerTestController` | Yes | — |
| 7. Tests | Yes, found and fixed 1 real bug (exception type) | AC1, AC2, AC3, AC4, AC5, AC6, AC7, AC8 |
| 8. Final check | Yes — `mvn -pl services/payment test`: **47/47 passing** (12 new config + 3 resource-server + 1 access-denied + 6 properties-content + 17 T02 + 8 T01, updated) | — |

Two mutation checks run beyond what Phase 5 required: removed `@NotBlank` from
`ReceiptProperties.bucket` → 2 failures (confirmed real); removed
`management.endpoint.health.show-details=never` → 1 failure (confirmed real). Both reverted;
`git status` confirms only the legitimate new/modified files remain.

## Deviations from the plan

One disclosed above (the `receipt.*` non-blank local default). One scope extension disclosed
above (`T01SkeletonRegressionTest` update, required by the change itself, not optional). No other
file outside the plan's own list was touched.
