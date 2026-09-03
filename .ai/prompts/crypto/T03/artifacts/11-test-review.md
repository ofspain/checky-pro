<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review) for crypto · T03. -->

# crypto · T03 · Phase 11 — Test Review Findings

**Scope:** Review the Phase 10 test suite against the frozen brief's acceptance criteria, named tests, and `spec/crypto-service/agents.md`/`design.md` to identify coverage gaps, weak assertions, false positives, and missing edge cases.

**Directive:** Do not rewrite production code or tests. Return gaps as **Gap · Why it matters · Suggested test.**

---

## Gap 1 — `anyRequest().authenticated()` creates a latent security tier that no test exercises

**Why it matters:** `ResourceServerConfig` falls back to `anyRequest().authenticated()` for anything that is neither in `PublicEndpoints` nor under `/internal/v1/**`. Because `PublicEndpoints` is intended to be the exhaustive public allowlist, the default should require `internal.crypto:write` (or `denyAll()`). A future controller added outside `/internal/v1/**` would be reachable with any valid JWT, regardless of scope. The current suite has no test for this default-policy edge.

**Suggested test:** Add a test case in `ResourceServerConfigIntegrationTest` that hits an arbitrary non-public, non-internal path (e.g., `/some/other/path`) with a JWT carrying only `SCOPE_something.else` and expects 403, and another with no token expecting 401. Alternatively, assert that `anyRequest()` maps to `hasAuthority(INTERNAL_SCOPE_AUTHORITY)` by inspecting the built `SecurityFilterChain`.

---

## Gap 2 — Failure assertions in config tests only check `hasFailed()`, not the cause

**Why it matters:** Tests such as `failsWhenQuorumThresholdExceedsConfiguredProviderCount` assert `context.hasFailed()` generically. If the context fails for a different reason (e.g., a malformed property key, a validator regression, or a duplicate-chain bug), the test still passes, giving false confidence.

**Suggested test:** Change the failure assertions to verify the thrown exception type/message, e.g., `assertThat(context.getFailure()).isInstanceOf(IllegalStateException.class).hasMessageContaining("quorum-threshold")`. Apply this pattern to all `ApplicationContextRunner` failure tests across the five properties test classes.

---

## Gap 3 — Problem+json response body is only substring-checked

**Why it matters:** The security tests assert the response contains `"status":401` or `"status":403` but do not validate the full RFC 9457 structure (`type`, `title`, `status`, `detail`). A malformed body that happens to contain that substring would pass, and regressions in title/detail would go unnoticed.

**Suggested test:** Parse the response body with `ObjectMapper` or JSONPath and assert exactly the expected fields and values, e.g., `type=about:blank`, `title=Unauthorized`, `status=401`, and a non-null `detail`. Apply to both 401 and 403 test cases.

---

## Gap 4 — Public actuator positive cases pass even when endpoints are not exposed

**Why it matters:** `PublicEndpointsTest.declaredPublicPathsAreNotBlockedBySecurity` asserts only that the returned status is not 401/403. Because actuator handlers are not registered in the `@WebMvcTest` slice, these requests legitimately return 404. That means the test would still pass even if `management.endpoints.web.exposure.include` were removed from `application.properties` — the endpoints would 404, which is not 401/403. The test therefore does not actually prove the declared public paths are reachable.

**Suggested test:** Add a test that loads the real `application.properties` (or a test copy) and asserts `management.endpoints.web.exposure.include` contains `health,info,prometheus` and `management.endpoint.health.probes.enabled=true`. Alternatively, use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with actuator on the classpath and assert the declared paths return 200 (or the appropriate non-401 status), constrained to when Docker/DB is unavailable.

---

## Gap 5 — No test proves `PublicEndpoints.PATTERNS` is the source of truth in `ResourceServerConfig`

**Why it matters:** `patternsListExactlyTheFourDeclaredPaths` asserts the constant's value, but it does not prove `ResourceServerConfig` actually calls `auth.requestMatchers(PublicEndpoints.PATTERNS).permitAll()`. A refactor could inline the same paths in the config and the constant test would still pass while the wiring diverges.

**Suggested test:** Use reflection or Spring Security's `SecurityFilterChain` API to inspect the built filter chain's `RequestMatcher` list and assert that the `permitAll` matchers are exactly `PublicEndpoints.PATTERNS`. This makes the "exhaustive, CI-enforced allowlist" claim enforceable.

---

## Gap 6 — `missingTokenEntirely` is redundant with the parameterized unauthenticated test

**Why it matters:** The parameterized `shouldRequireInternalScopeForWatchAndAttestEndpoints_rejectsUnauthenticated` already performs bare requests (no `.with(jwt())`) against all three internal paths. `missingTokenEntirely` repeats the same scenario for only `POST /internal/v1/attest`, adding no new coverage.

**Suggested test:** Remove the redundant test or convert it into a distinct edge case that adds value, such as a request with a malformed `Authorization` header or a non-Bearer scheme.

---

## Gap 7 — No test exercises the actual `scope` claim-to-authority conversion

**Why it matters:** The entire suite uses `SecurityMockMvcRequestPostProcessors.jwt().authorities(...)` to inject a pre-built `JwtAuthenticationToken`. This bypasses Spring's `JwtGrantedAuthoritiesConverter`, which is the component that reads the `scope` JSON array from `contracts/api/token-claims.md` Path 2 and converts it into `SCOPE_*` authorities. A regression or misconfiguration in the decoder/converter would not be caught.

**Suggested test:** Add a focused test that injects a `JwtDecoder` mock returning a JWT whose `scope` claim is `["internal.crypto:write"]` and verify the request reaches the controller. This tests the actual claim shape from the contract, not just the authority string.

---

## Gap 8 — `issuer-uri` configuration is not exercised

**Why it matters:** `application.properties` now sets `spring.security.oauth2.resourceserver.jwt.issuer-uri`, but no test verifies it is actually wired into the resource server. A typo in the property key or a failure of Spring Boot autoconfiguration would leave issuer validation disabled while all unit tests still pass.

**Suggested test:** Inspect the `JwtDecoder` bean in an `ApplicationContextRunner` or `@WebMvcTest` context and assert that its validator list includes an `IssuerValidator` (or `JwtIssuerValidator`) with the expected issuer URI. Alternatively, assert that a JWT with a mismatched `iss` claim is rejected.

---

## Gap 9 — Screening reverse-direction guard only covers `baseUrl`; no test covers `apiKeySecretName` alone

**Why it matters:** `ScreeningProperties` throws if `baseUrl` is set while `enabled=false`, but it does not guard the case where only `apiKeySecretName` is set with `enabled=false`. If an operator supplies the API key secret name but forgets to enable screening, the misconfiguration silently no-ops. The existing tests exercise `baseUrl` + `apiKeySecretName` together, but not `apiKeySecretName` alone.

**Suggested test:** Add `failsWhenApiKeySecretNameSetButNotEnabled` setting only `themistra.crypto.screening.api-key-secret-name=fake-secret` (no `base-url`, `enabled` false/omitted) and expect failure. Also consider updating the production guard to treat either screening-related field as a signal that screening should be enabled.

---

## Gap 10 — No test for invalid timeout/retry values in `ScreeningProperties`

**Why it matters:** `ScreeningProperties` annotates `connectTimeoutSeconds` and `readTimeoutSeconds` with `@Min(1)` and `retryMaxAttempts` with `@Min(0)`, but none of the six tests supply invalid values. A regression that removes or weakens these constraints would not be detected.

**Suggested test:** Add negative tests for `connect-timeout-seconds=0`, `read-timeout-seconds=-1`, and `retry-max-attempts=-1`, each expecting context failure.

---

## Gap 11 — No test for placeholder-looking values in non-local profiles

**Why it matters:** `application.properties` contains values like `local-only-fake-kms-key-id` and `local-only-fake-provider-key`. The brief/L13 requires that non-local profiles fail on invalid config, but there is no test proving that placeholder-shaped values are rejected when `local` is not active. If a higher environment forgets to override a placeholder, the service could boot with fake credentials.

**Suggested test:** Add a parameterized test that activates a non-local profile (e.g., `dev`) with an otherwise valid config but one placeholder-shaped value, and assert context failure. Alternatively, add a validator that rejects `local-only-*`/`fake-*` patterns and test it directly.

---

## Gap 12 — No test for case-sensitive chain identifiers

**Why it matters:** `ProviderProperties` and `FinalityProperties` constrain chains to `ETHEREUM|TRON` with a case-sensitive regex. The tests use `SOLANA` for the negative case but never test a lowercase variant such as `ethereum`. The spec consistently uses uppercase, but a lowercase value would bind successfully today, which may or may not be intended.

**Suggested test:** Add a negative test supplying `themistra.crypto.providers.chains[0].chain=ethereum` and expect failure, documenting that chain identifiers are uppercase only.

---

## Gap 13 — No test for duplicate chain entries

**Why it matters:** `ProviderProperties` and `FinalityProperties` accept lists of chains with no uniqueness constraint. A config file could accidentally list `ETHEREUM` twice, leading to double-processing or ambiguous finality policy lookups in later tasks.

**Suggested test:** Add negative tests that supply duplicate chain values in `chains` and `enabled-chains` and expect context failure (after adding a uniqueness validator) or at least document the behavior with a test.

---

## Gap 14 — No test links `FinalityProperties.enabledChains` to `ProviderProperties.chains`

**Why it matters:** The two properties classes are tested in isolation. A deployment could enable finality for a chain that has no configured providers, or configure providers for a chain not enabled for finality. Either misconfiguration would produce a runtime state that cannot advance.

**Suggested test:** Add an integration-style test using `ApplicationContextRunner` with both `ProviderProperties` and `FinalityProperties` registered that asserts every chain in `FinalityProperties.enabledChains` also appears in `ProviderProperties.chains` (and ideally vice versa). This would require a shared cross-property validator.

---

## Gap 15 — No full-context boot smoke test for the `local` profile

**Why it matters:** The Phase 10 manifest acknowledges this limitation, but it remains a real gap. The tests prove each slice in isolation, but no test proves the real `application.properties` as committed boots successfully in `local` profile. A typo in the committed property keys, an invalid index-based list entry, or a missing required property would not be caught until runtime.

**Suggested test:** Add a `@SpringBootTest` with `webEnvironment = RANDOM_PORT` and `spring.profiles.active=local` once Docker/Postgres is available, or use a lightweight `@ContextConfiguration` with an in-memory/H2 datasource substitute and the real `application.properties`. Alternatively, use `ApplicationContextRunner` with `CryptoServiceApplication.class` and a test datasource override to load the real committed properties.

---

## Summary table

| # | Gap | Risk | Suggested test approach |
|---|-----|------|-------------------------|
| 1 | `anyRequest().authenticated()` not exercised | Latent privilege escalation | Test arbitrary non-internal path with under-scoped token |
| 2 | Generic `hasFailed()` assertions | False positives | Assert specific exception/message |
| 3 | Problem+json only substring-checked | Regressions unnoticed | Parse and assert full RFC 9457 fields |
| 4 | Public actuator tests pass on 404 | Endpoints may still be unexposed | Assert management properties or use full Boot test |
| 5 | `PublicEndpoints.PATTERNS` wiring not proven | Constant/config drift | Inspect `SecurityFilterChain` matchers |
| 6 | `missingTokenEntirely` redundant | Noise | Remove or replace with malformed-auth test |
| 7 | Actual `scope` claim conversion not tested | Decoder/converter regressions | Mock `JwtDecoder` with JSON-array scope claim |
| 8 | `issuer-uri` not exercised | Issuer validation disabled silently | Inspect `JwtDecoder` validators |
| 9 | Screening guard only covers `baseUrl` | Silent misconfiguration | Test `apiKeySecretName` alone with `enabled=false` |
| 10 | Invalid screening timeouts/retry not tested | Constraint regressions | Negative tests for zero/negative values |
| 11 | Placeholder values in non-local profiles not tested | Boots with fake credentials | Non-local profile + placeholder value → failure |
| 12 | Chain case sensitivity not tested | Lowercase chains accepted | Negative test with `ethereum` |
| 13 | Duplicate chain entries not tested | Double-processing/ambiguity | Negative test for duplicate chains |
| 14 | Finality/provider chain consistency not tested | Runtime deadlock | Cross-property validator test |
| 15 | No full-context `local` boot smoke test | Committed properties untested | `@SpringBootTest` with real properties + test DB |

(End of test review.)
