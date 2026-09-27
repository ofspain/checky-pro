<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T03 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T03 — Config & resource server |
| **Spec section** | Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T03 implementation. Findings only.

---

## Finding 1 · Required T03 automated tests are missing

**Issue:** The TIB's Required Tests section lists several automated tests, but the current codebase contains only the two pre-existing test files (`T01SkeletonRegressionTest.java` and `NotificationBaselineMigrationIntegrationTest.java`). None of the T03-specific tests exist: no per-record validation tests, no `LinkPropertiesStartupValidationTest`, no `PublicEndpointsTest`, no `ResourceServerConfigIntegrationTest`, and no `ApplicationProperties*Test` asserting the committed `application.properties` keys.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/` contains only `T01SkeletonRegressionTest.java` and `NotificationBaselineMigrationIntegrationTest.java`.
- `services/notification/src/main/java/com/themistra/notification/common/config/LinkPropertiesStartupValidation.java` has no corresponding test class.
- `services/notification/src/main/java/com/themistra/notification/common/ResourceServerConfig.java` has no corresponding test class.
- `services/notification/src/main/java/com/themistra/notification/common/PublicEndpoints.java` has no corresponding test class.

The self-review acknowledges one slice of this gap (real property binding for `EmailProperties`/`RetryProperties`/`InappProperties` should be added in Phase 10) but does not acknowledge the absence of the security/config tests.

**Recommendation:** Add the missing test classes before considering T03 complete:
- `EmailPropertiesTest`, `RetryPropertiesTest`, `InappPropertiesTest` (constructor validation / compact-constructor behavior).
- `LinkPropertiesStartupValidationTest` (or a `@SpringBootTest` with `@ActiveProfiles("dev")`) proving startup fails when `AUTH_EMAIL_LINK_BASE_URL` is unset and succeeds when it is set.
- `PublicEndpointsTest` mirroring crypto's own.
- `ResourceServerConfigIntegrationTest` with a test-only controller, proving 401 rejection and JWT acceptance.
- `ApplicationPropertiesSecurityConfigTest` and `ApplicationPropertiesJpaConfigTest` (or a single combined file) asserting the committed keys.

**Confidence:** High

---

## Finding 2 · No automated proof that `PublicEndpoints.PATTERNS` is exactly the three actuator paths

**Issue:** `PublicEndpoints.java` correctly declares only the three allowed actuator paths, but there is no test that fails if a future edit adds a fourth path or removes one. AC4 depends on this invariant.

**Evidence:**
- `PublicEndpoints.java` lines 11–15: the array is correct.
- No test asserts `PublicEndpoints.PATTERNS` contains exactly `/actuator/health/**`, `/actuator/info`, and `/actuator/prometheus`.

**Recommendation:** Add a simple unit test mirroring crypto's `PublicEndpointsTest.patternsListExactlyTheFourDeclaredPaths()` (adapted to three paths) and a MockMvc-based parameterized test proving the declared paths are not blocked while sensitive actuator paths (e.g., `/actuator/env`) return 401.

**Confidence:** High

---

## Finding 3 · No automated proof that `ResourceServerConfig` rejects unauthenticated requests and accepts JWTs

**Issue:** `ResourceServerConfig.java` wires a stateless JWT resource-server filter chain, but there is no test exercising it. AC3 depends on this behavior. Without a test, a regression such as accidentally changing `.anyRequest().authenticated()` to `.anyRequest().permitAll()`, dropping `oauth2ResourceServer()`, or misconfiguring the problem+json handlers would not be caught by the build.

**Evidence:**
- `ResourceServerConfig.java` lines 35–55: the filter chain is configured correctly but untested.
- No integration test file imports `ResourceServerConfig` and exercises it with `MockMvc` + `SecurityMockMvcRequestPostProcessors.jwt()`.

**Recommendation:** Add `ResourceServerConfigIntegrationTest` (mirroring crypto's own) with a test-only controller and tests proving:
- unauthenticated request to a non-public path returns 401 with `application/problem+json` and `WWW-Authenticate: Bearer`;
- authenticated JWT request to a non-public path returns 2xx.

**Confidence:** High

---

## Finding 4 · No automated proof of the profile-conditional `LinkProperties.baseUrl` validation

**Issue:** `LinkPropertiesStartupValidation` is the mechanism that satisfies AC5 (startup fails outside `local` when link base URL is missing). The self-review says Phase 6 manually booted the app in `local` and `dev` profiles to verify this, but there is no automated regression guard. A future refactor could break the `@Profile("!local")` annotation or the blank check without failing any test.

**Evidence:**
- `LinkPropertiesStartupValidation.java` lines 16–25: the validation logic is present.
- No test file exercises this component with a real Spring context under different profiles.

**Recommendation:** Add a `@SpringBootTest` test class with two test methods:
- `@ActiveProfiles("local")` + no `AUTH_EMAIL_LINK_BASE_URL` → context loads.
- `@ActiveProfiles("dev")` + no `AUTH_EMAIL_LINK_BASE_URL` → context fails to load with the expected `IllegalStateException` message.

**Confidence:** High

---

## Finding 5 · No automated proof that `application.properties` contains the expected keys

**Issue:** AC2 requires `application.properties` to contain the design.md config keys plus standard datasource/JPA/Flyway/Kafka/security/actuator sections. The file is present and correct, but a silent deletion or typo of a key would not fail any test.

**Evidence:**
- `application.properties` lines 1–62: all expected keys are present.
- No test reads this file from the classpath and asserts key presence/values.

**Recommendation:** Add an `ApplicationPropertiesConfigTest` (mirroring crypto's two-file precedent) that loads `application.properties` and asserts:
- resource-server keys (`jwk-set-uri`, `issuer-uri`) are non-blank;
- actuator exposure is exactly `health,info,prometheus` and health probes are enabled;
- JPA `ddl-auto=validate` and `open-in-view=false`;
- Flyway is disabled;
- datasource username is `notification_app`;
- design.md keys (`themistra.notification.email.from`, `themistra.notification.link.base-url`, etc.) are present.

**Confidence:** Medium

---

## Finding 6 · `server.port` is not set, creating a local-dev conflict with auth-service

**Issue:** `services/auth/src/main/resources/application.properties` sets `server.port=8080`. Notification-service's `application.properties` does not set `server.port`, so it also defaults to 8080. A developer running both services locally (which the comments explicitly envision) will hit a port conflict unless they remember to override `SERVER_PORT` externally.

**Evidence:**
- `services/auth/src/main/resources/application.properties` contains `server.port=8080`.
- `services/notification/src/main/resources/application.properties` contains no `server.port` entry.
- The notification `application.properties` comment (lines 31–35) says it "Points at the local auth-service instance a developer runs alongside this one."

**Recommendation:** Add `server.port=${SERVER_PORT:8082}` (or another free port) to notification's `application.properties`, with a comment explaining that auth-service owns 8080. This matches the intent of running services side-by-side and mirrors the need already acknowledged by auth pinning its own port.

**Confidence:** Low-Medium

---

## Finding 7 · `AccessDeniedHandler` is wired but untriggered in T03

**Issue:** `ResourceServerConfig` declares both an `AuthenticationEntryPoint` (for 401) and an `AccessDeniedHandler` (for 403). Because T03 has no scope/authority checks and no endpoints that would trigger access-denied, the 403 handler has no path that exercises it. This is not a bug, but it means the 403 problem+json body is currently dead code that could be silently broken before task 13 introduces the endpoints that need it.

**Evidence:**
- `ResourceServerConfig.java` lines 49–52: both handlers are wired.
- No authorization rule in the filter chain ever delegates to the `AccessDeniedHandler` in T03.

**Recommendation:** Keep the handler (it is needed later), but ensure the future `ResourceServerConfigIntegrationTest` includes a test case that triggers a 403 once task 13 adds authority-scoped endpoints. For T03, at minimum assert that the handler bean exists and writes problem+json by invoking it directly in a unit test, or accept this as a known coverage gap with a TODO comment.

**Confidence:** Low

---

## Summary

The T03 production code is correct and well-structured: the four property records, profile-conditional link validation, PublicEndpoints, and ResourceServerConfig all match the brief and the crypto precedent. The dominant issue is the absence of the T03-specific automated tests required by the brief (Findings 1–5). Finding 6 is a local-dev ergonomics issue, and Finding 7 is a forward-looking coverage note. None of these findings indicate a logic error in the current code; they identify missing verification and a latent port conflict.
