<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T03 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T03 — Config & resource server |
| **Spec section** | Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T03 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T03 regression-guard tests against the acceptance criteria and task statement.

---

## Gap 1 · `spring.profiles.active=local` is not regression-guarded

**Why it matters:** The entire profile-conditional validation strategy for `LinkProperties.baseUrl` relies on the `local` profile being active by default in local development. If `spring.profiles.active=local` is accidentally deleted or changed in `application.properties`, a developer running the app with no explicit profile will experience a startup failure even though they are in a local-dev context. No test currently asserts this key.

**Suggested test:** Add an assertion to `ApplicationPropertiesJpaConfigTest` (or `ApplicationPropertiesSecurityConfigTest`) that loads `application.properties` and asserts `spring.profiles.active` is exactly `local`.

---

## Gap 2 · `management.endpoint.health.show-details=never` is not asserted

**Why it matters:** `agents.md` Security rule and L4 require that errors/internal detail not leak. `management.endpoint.health.show-details=never` is present in `application.properties` (line 46) and is the defense against the health endpoint revealing details to unauthenticated callers. Its absence would be a security-relevant regression, but no test checks it.

**Suggested test:** Add an assertion in `ApplicationPropertiesSecurityConfigTest.exposesExactlyHealthInfoAndPrometheusOverActuator()` that `properties.getProperty("management.endpoint.health.show-details")` is equal to `never`.

---

## Gap 3 · `PublicEndpointsTest` is not a true static sweep for `permitAll()`

**Why it matters:** AC4 says `PublicEndpoints.PATTERNS` contains only the 3 actuator paths and "no other `permitAll()` anywhere in the module (swept by test)". The current tests verify behavior for a fixed list of paths, but they do not statically inspect `ResourceServerConfig.java` (or any other security config) for additional `permitAll()` calls. A future edit could add `auth.requestMatchers("/some/internal/path").permitAll()` to the filter chain, and the current parameterized tests would not catch it unless `/some/internal/path` happened to be in the test list.

**Suggested test:** Add a plain JUnit test that reads `services/notification/src/main/java/com/themistra/notification/common/ResourceServerConfig.java` as text and asserts it contains no `.permitAll()` calls outside of the `PublicEndpoints.PATTERNS` line. This is the literal "sweep" AC4 describes.

---

## Gap 4 · `LinkPropertiesStartupValidationTest` does not assert the specific failure for `staging`/`prod`

**Why it matters:** `stagingAndProdProfilesAlsoFailWithNoBaseUrl()` loops over `staging` and `prod` and only asserts `hasFailed()`. A failure for an unrelated reason (e.g., a profile-specific property loader bug) would satisfy this assertion just as well as the intended blank-base-url validation. The `dev`-profile test already asserts the root cause is an `IllegalStateException` with the expected message; the same precision should apply to `staging` and `prod`.

**Suggested test:** Change the staging/prod loop to assert `context.getStartupFailure().rootCause().isInstanceOf(IllegalStateException.class)` with the same message fragments checked in the `dev` test.

---

## Gap 5 · No default-profile (no active profile) behavior is tested

**Why it matters:** `ApplicationContextRunner` does not load the committed `application.properties`, so the tests that do not explicitly add a profile run with **no** active profile. `@Profile("!local")` matches in that case, meaning `LinkPropertiesStartupValidation` would run and fail if `baseUrl` is blank. This is the implicit behavior today, but it is not asserted. If the validation logic were ever changed to use `@Profile({"dev", "staging", "prod"})` instead of `@Profile("!local")`, the no-profile case would silently change.

**Suggested test:** Add a test `defaultProfileWithNoBaseUrlAlsoFails()` that runs `ApplicationContextRunner` with no active profile and a blank `base-url`, asserting startup fails with the expected `IllegalStateException`.

---

## Gap 6 · `connection-init-sql` exact search_path order is not asserted

**Why it matters:** `spring.datasource.hikari.connection-init-sql=SET search_path TO notifications, public` is critical: `notifications` must be first so runtime table names resolve to the service schema, and `public` must be present so the `citext` extension is visible. `ApplicationPropertiesJpaConfigTest.datasourceUsesTheLeastPrivilegeRuntimeRole()` only asserts that the value contains `"notifications"`. A misordered value such as `SET search_path TO public, notifications` would pass the test but could cause subtle resolution issues or break the `citext` assumption.

**Suggested test:** Assert the exact value is `SET search_path TO notifications, public` (or use a regex that enforces both order and presence of `public`).

---

## Gap 7 · `ResourceServerConfigIntegrationTest` does not exercise the 403 handler

**Why it matters:** `ResourceServerConfig` wires both a 401 `AuthenticationEntryPoint` and a 403 `AccessDeniedHandler`. Because T03 has no authority rules, there is no path that triggers the 403 handler. The handler is dead code that could be broken by a future refactor before task 13 introduces the endpoints that need it.

**Suggested test:** Either (a) add a direct unit test that invokes `problemJsonAccessDeniedHandler.handle(...)` with a mock request/response and asserts the response body is RFC 9457 problem+json, or (b) add a TODO/FIXME comment in the test file noting that a 403 case must be added once task 13 introduces scoped endpoints. Option (a) is preferred because it provides immediate regression coverage.

---

## Gap 8 · `spring.datasource.password` placeholder discipline is not asserted

**Why it matters:** L10 requires no committed secret/credential. The committed `application.properties` uses `${DB_PASSWORD:notification-app-local-only}`, which is the safe local-only pattern. A regression that hardcoded a real password or removed the env-var placeholder would not be caught by any current test.

**Suggested test:** Add an assertion in `ApplicationPropertiesJpaConfigTest.datasourceUsesTheLeastPrivilegeRuntimeRole()` that `spring.datasource.password` starts with `${DB_PASSWORD:` and ends with `}`. This mirrors the existing assertion style and enforces the env-var-placeholder discipline.

---

## Summary

The T03 test suite is now comprehensive: all four property records have real binding tests, the profile-conditional link validation is automated, `PublicEndpoints` and `ResourceServerConfig` are behaviorally tested, and the committed `application.properties` keys are guarded. The remaining gaps are precision and coverage refinements: guarding the default `local` profile, asserting `show-details=never`, turning the `PublicEndpoints` sweep into a true static scan, tightening the staging/prod failure assertions, covering the no-profile default case, enforcing the exact `search_path` order and password placeholder discipline, and providing immediate coverage for the 403 handler.
