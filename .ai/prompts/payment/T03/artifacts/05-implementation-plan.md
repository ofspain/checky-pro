# payment · T03 · Phase 5 — Implementation Plan

Traces to the frozen brief (`04-frozen-task-brief.md`) only. No file or decision beyond it.

## Files to create

1. `services/payment/src/main/resources/application.properties`
2. `services/payment/src/main/java/com/themistra/payment/common/config/CryptoClientProperties.java`
3. `services/payment/src/main/java/com/themistra/payment/common/config/InvoiceProperties.java`
4. `services/payment/src/main/java/com/themistra/payment/common/config/ReceiptProperties.java`
5. `services/payment/src/main/java/com/themistra/payment/common/config/LedgerAnchorProperties.java`
6. `services/payment/src/main/java/com/themistra/payment/common/config/InvoiceExpirySweepProperties.java`
7. `services/payment/src/main/java/com/themistra/payment/common/PublicEndpoints.java`
8. `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java`
9. `services/payment/src/test/java/com/themistra/payment/common/ResourceServerTestController.java`
10. `services/payment/src/test/java/com/themistra/payment/common/ResourceServerConfigTest.java`
11. `services/payment/src/test/java/com/themistra/payment/common/config/ConfigPropertiesBindingTest.java`

## Files to modify

1. `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java` — add
   `@ConfigurationPropertiesScan`.

## Public methods (signatures)

- `CryptoClientProperties` (record): `baseUrl()`, `attestPath()`, `watchPath()`,
  `connectTimeoutMs()`, `readTimeoutMs()` — accessors only, no behavior.
- `InvoiceProperties` (record): `defaultTtlMinutes()`, `amountToleranceBaseUnits()`.
- `ReceiptProperties` (record): `bucket()`, `kmsPublicKeysUrl()` — both `@NotBlank`.
- `LedgerAnchorProperties` (record): `cron()`.
- `InvoiceExpirySweepProperties` (record): `cron()`.
- `PublicEndpoints`: no methods, one `public static final String[] PATTERNS`.
- `ResourceServerConfig`:
  `public SecurityFilterChain securityFilterChain(HttpSecurity, AuthenticationEntryPoint, AccessDeniedHandler)`
  (`@Bean`), `public AuthenticationEntryPoint problemJsonAuthenticationEntryPoint(ObjectMapper)`
  (`@Bean`), `public AccessDeniedHandler problemJsonAccessDeniedHandler(ObjectMapper)` (`@Bean`).
- `PaymentServiceApplication.main(String[])` — unchanged signature, only the class annotation list
  changes.

## Private methods

- `ResourceServerConfig.writeProblemJson(HttpServletResponse, ObjectMapper, HttpStatus, String title, String detail)`
  — shared RFC 9457 body writer, mirrors both siblings' identical private helper.

## Entities used

None.

## Repositories used

None.

## Services used

None.

## Unit / integration tests required

- `ResourceServerConfigTest` (`@WebMvcTest`, imports `ResourceServerConfig` +
  `ResourceServerTestController`):
  1. `publicActuatorPathsAreReachableWithoutAToken` — AC3.
  2. `testControllerPathIsUnauthorizedWithoutAToken` — AC3, AC4 (401 half).
  3. `unauthorizedResponseIsRfc9457ProblemJson` — AC4 (401 half).
- A plain-JUnit test (no Spring context) invoking `problemJsonAccessDeniedHandler` directly:
  4. `accessDeniedHandlerWritesRfc9457ProblemJson` — AC4 (403 half), mirrors
     `notification-service`'s own identical test exactly.
- `ConfigPropertiesBindingTest` (`ApplicationContextRunner`):
  5. `allFiveRecordsBindFromValidConfiguration` — AC1.
  6. `receiptBucketMissingFailsStartupInNonLocalProfile` — AC1, AC5 (asserts
     `rootCause().isInstanceOf(...)`, not bare `hasFailed()`).
  7. `receiptKmsPublicKeysUrlMissingFailsStartupInNonLocalProfile` — AC1, AC5 (same precision).
  8. `localProfileToleratesTheSameMissingValues` — AC5's own other half (no forced failure in
     `local`, matching every sibling's own established local/non-local split).
- A source-text guard (new or folded into an existing skeleton-style test):
  9. `applicationClassHasConfigurationPropertiesScan` — AC2.
  10. `applicationPropertiesContainsNoUndocumentedSecretAndNoYamlFileExists` — Phase 3 finding 7.
  11. `applicationPropertiesDeclaresTheKafkaGroupIdAndHikariSettings` — AC7, AC8.
  12. `managementEndpointsExposureIncludesShowDetailsNever` — AC6.

## Execution order

1. **`application.properties`** — written first and in full (all property groups at once, per
   the frozen brief's own complete Scope list), since every later step either binds against it or
   asserts its content.
2. **The five `@ConfigurationProperties` records** — written next, each bound to its own real
   property group; `receipt.bucket`/`receipt.kms-public-keys-url` get `@NotBlank`.
3. **`PaymentServiceApplication`** — add `@ConfigurationPropertiesScan` immediately after the
   records exist, so a quick `mvn compile` can confirm binding doesn't error before writing more
   code.
4. **`PublicEndpoints` / `ResourceServerConfig`** — the security filter chain, mirroring both
   siblings' exact shape (stateless, CSRF disabled, `PublicEndpoints.PATTERNS` permitted,
   `anyRequest().authenticated()`, RFC 9457 401/403 beans).
5. **Compile check** — `mvn -pl services/payment compile` before writing tests.
6. **`ResourceServerTestController`** (test scope) — added before the test that needs it.
7. **Tests** — written last, in the order listed above (config-binding tests first since they're
   fully Docker-free and fastest to iterate on, then the resource-server tests).
8. **Final check** — `mvn -pl services/payment test` (still Docker-free for this task — no
   Testcontainers dependency is exercised by anything in T03's own scope).
