<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T03 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T03 — Config & resource server |
| **Spec section** | Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 Task Implementation Brief. Findings only.

---

## Finding 1 · Profile-conditional validation for `LinkProperties.baseUrl` is unspecified

**Severity:** High

**Evidence:** AC5 requires startup to fail outside the `local` profile when `LinkProperties.baseUrl` is missing, but `design.md` §4c sets `themistra.notification.link.base-url=${AUTH_EMAIL_LINK_BASE_URL:}`, which binds to blank by default. The TIB states the non-blank requirement must be "conditional on the active profile, unlike the other three groups' fields", but it does not specify the implementation mechanism. `ScreeningProperties` (the cited precedent) achieves conditional validation through a boolean `enabled` flag in the record constructor — not through Spring profiles — so it is not a directly applicable pattern.

**Recommended brief amendment:** Explicitly choose and document the mechanism, for example:
- A custom Spring `Validator` or `ConstraintValidator` that injects `Environment` and skips `@NotBlank` when `local` is active; or
- Two `@Profile`-specific configuration classes, one importing a `LinkProperties` record with `@NotBlank` and one without; or
- A record constructor that throws only when `baseUrl` is blank and a non-local profile is active (requires passing `Environment` into the record, which is awkward).

Also state the exact test strategy: a `@SpringBootTest` with `spring.profiles.active=dev` (or `staging`/`prod`) and no `AUTH_EMAIL_LINK_BASE_URL` must fail context startup with a validation error.

---

## Finding 2 · `ResourceServerConfig` "mirrors crypto exactly" contradicts "no scope/authority check"

**Severity:** Medium

**Evidence:** The TIB says `ResourceServerConfig` should "mirror `crypto-service`'s own `ResourceServerConfig` exactly — no custom scope/authority check here". Crypto's `ResourceServerConfig` declares `INTERNAL_SCOPE_AUTHORITY` and applies `hasAuthority(INTERNAL_SCOPE_AUTHORITY)` to `/internal/v1/**`. Notification-service has no `/internal/v1/**` endpoints in T03 (they don't exist until task 13), so mirroring crypto "exactly" would add a dead scope check that has no matching endpoints. At the same time, the TIB says there should be "no custom scope/authority check here".

**Recommended brief amendment:** Replace "mirrors ... exactly" with "mirrors the structural shape of crypto's resource-server filter chain (stateless, CSRF disabled, `PublicEndpoints` permitAll, JWT bearer, RFC 9457 entry/denied handlers, `.anyRequest().authenticated()`) but omits the `/internal/v1/**` scope-authority line because no such endpoints exist in T03." This makes the expected code unambiguous.

---

## Finding 3 · No test-only controller is mentioned for the resource-server integration test

**Severity:** Medium

**Evidence:** The Required Tests section calls for "a resource-server integration test proving an unauthenticated request to a non-public path is rejected ... and a validly-signed JWT is accepted", mirroring `crypto-service`'s `ResourceServerConfigIntegrationTest`. That test relies on `InternalTestController` (a test-only `@RestController`) to provide a non-public path to hit. The TIB does not list this test-only controller as a file to create, nor does it state that the test should use an existing controller (none exists in notification-service yet).

**Recommended brief amendment:** Add the test-only controller to the Files to Create list (under test sources), e.g. `services/notification/src/test/java/com/themistra/notification/common/ResourceServerTestController.java`, with one or more arbitrary secured endpoints. Alternatively, state that the test may import a minimal stand-in controller class defined in the same test file.

---

## Finding 4 · Validation constraints for email format and transport values are unspecified

**Severity:** Low-Medium

**Evidence:** The TIB says the four property records should be `@ConfigurationProperties`-annotated and `@Validated`, and calls for "simple `@NotBlank`/`@Min`-style tests". It does not specify whether `EmailProperties.from` should be validated as an email address (e.g. `@Email`) or whether `EmailProperties.transport` / `InappProperties.transport` should be restricted to known values. `design.md` §4c gives default values (`no-reply@checky.pro`, `ses`, `sse`) but does not state the allowed set. Without explicit guidance, an implementer might add overly strict constraints now (e.g., an enum that prevents the capturing fake transport in tests) or omit useful validation entirely.

**Recommended brief amendment:** Either (a) explicitly state that no format/enum validation is added in T03 beyond `@NotBlank`/`@Min` — transport/value validation is deferred to the tasks that implement the corresponding channels — or (b) specify the allowed values, e.g. `EmailProperties.transport` ∈ `{ses, fake}` and `InappProperties.transport` ∈ `{sse, websocket}`, with a note that `fake` is test-only.

---

## Finding 5 · Cross-field retry validation is not addressed

**Severity:** Low

**Evidence:** `RetryProperties` has `initial-backoff-seconds` (default 30) and `max-backoff-seconds` (default 3600). The TIB mentions only `@Min`-style validation. It does not state whether `max-backoff-seconds` must be greater than or equal to `initial-backoff-seconds`. A misconfigured property file could set `max < initial`, and the brief's current validation rules would accept it.

**Recommended brief amendment:** Add a cross-field validation rule (record-constructor check or custom validator) requiring `max-backoff-seconds >= initial-backoff-seconds`, with a corresponding test case.

---

## Finding 6 · Required class-level annotations for `ResourceServerConfig` are not specified

**Severity:** Low

**Evidence:** The TIB describes `ResourceServerConfig` as a class with one `SecurityFilterChain` bean and RFC 9457 handler beans, but it does not state whether the class should be annotated with `@Configuration` and `@EnableWebSecurity`. Crypto's precedent uses both. Omitting `@EnableWebSecurity` can cause subtle differences in Spring Security's default configuration, and omitting `@Configuration` would prevent the beans from being registered.

**Recommended brief amendment:** Explicitly require `@Configuration` and `@EnableWebSecurity` on `ResourceServerConfig`, matching the crypto precedent.

---

## Finding 7 · `PublicEndpointsTest` scope is ambiguous

**Severity:** Low

**Evidence:** The TIB calls for a `PublicEndpoints` sweep test that mirrors crypto's `PublicEndpointsTest`. Crypto's test does two things: (1) asserts the exact contents of `PublicEndpoints.PATTERNS`, and (2) uses MockMvc to verify that declared public paths are not blocked by security and that sensitive actuator paths return 401. The TIB only says "no `permitAll()` pattern exists outside the declared allowlist", which could be interpreted as a static source-code scan rather than the MockMvc behavior test.

**Recommended brief amendment:** State explicitly that the sweep test includes both:
- a static assertion on `PublicEndpoints.PATTERNS` containing exactly `/actuator/health/**`, `/actuator/info`, and `/actuator/prometheus`; and
- a MockMvc-based parameterized test proving those paths are not blocked and that other paths (e.g., `/actuator/env`, `/actuator/beans`) require authentication.

---

## Finding 8 · `application.properties` keys are listed but not asserted in the TIB

**Severity:** Low

**Evidence:** AC2 requires `application.properties` to contain every key from `design.md` §4c plus standard sections. The Required Tests section mentions "a new assertion ... that `application.properties` exists with the expected keys (mirrors `crypto-service`'s own `ApplicationPropertiesSecurityConfigTest`)". Crypto has *two* such tests: `ApplicationPropertiesSecurityConfigTest` (security/actuator keys) and `ApplicationPropertiesJpaConfigTest` (JPA/Flyway keys). The TIB only names the security one.

**Recommended brief amendment:** Expand the required test to cover both security/actuator and JPA/datasource/Flyway/Kafka keys, or explicitly list the property keys that must be asserted (e.g. `spring.datasource.url`, `spring.datasource.username=notification_app`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=false`, `spring.kafka.bootstrap-servers`, `spring.application.name`).

---

## Summary

The T03 brief is directionally correct and consistent with `agents.md` L8/L10. The highest-severity gap is the unspecified profile-conditional validation mechanism for `LinkProperties.baseUrl` (Finding 1), because it directly affects AC5 and is not implementable from the current text alone. Findings 2 and 3 are medium-severity ambiguities in the security test/code structure. Findings 4–8 are lower-severity precision items that reduce implementer guesswork and align the brief with the crypto precedent it cites.
