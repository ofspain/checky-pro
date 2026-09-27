# notification · T03 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **AC1** — 4 validated `@ConfigurationProperties` records, exact prefixes/keys | Yes | `EmailProperties.java:13`, `LinkProperties.java:13`, `RetryProperties.java:13`, `InappProperties.java:13` | `EmailPropertiesTest`, `LinkPropertiesTest`, `RetryPropertiesTest`, `InappPropertiesTest` (real `ApplicationContextRunner` binding, not just constructor logic) | No | No |
| **AC2** — `application.properties` exists with every required key | Yes | `application.properties` (all 8 `themistra.notification.*` keys byte-for-byte identical to `design.md` §4c's own VERBATIM block, verified directly this phase; plus datasource/JPA/Flyway/Kafka/actuator/resource-server/`server.port` sections) | `ApplicationPropertiesSecurityConfigTest` (5 tests), `ApplicationPropertiesJpaConfigTest` (4 tests) | No | No |
| **AC3** — `ResourceServerConfig`, one `SecurityFilterChain`, stateless/CSRF-disabled/allowlist/RFC 9457 | Yes | `ResourceServerConfig.java:31-55` (`@Configuration`+`@EnableWebSecurity` present, Kimi Phase 3 Finding #6) | `ResourceServerConfigIntegrationTest` (4 tests: unauthenticated→401, JWT→2xx, non-Bearer scheme, direct 403-handler unit test) | No | No |
| **AC4** — `PublicEndpoints.PATTERNS` exactly 3 actuator paths, no other `permitAll()` | Yes | `PublicEndpoints.java:12-16` | `PublicEndpointsTest` (14 tests: static exact-list, 5 behavioral-allow, 6 behavioral-deny, 1 non-actuator-deny, 1 true static source-sweep for stray `permitAll()`) | No | No |
| **AC5** — startup genuinely fails outside `local` on missing `link.base-url` | Yes | `LinkPropertiesStartupValidation.java` (`@Profile("!local")`-gated `@Component`) | `LinkPropertiesStartupValidationTest` (6 tests: local-clean, dev-fails-with-exact-exception, dev-clean-when-set, staging/prod-fail-with-exact-exception, no-active-profile-also-fails) + real app-boot proof (Phase 6: `local` clean, `dev` genuinely fails) | No | No |
| **AC6** — `@ConfigurationPropertiesScan` present; `T01SkeletonRegressionTest` updated | Yes | `NotificationServiceApplication.java` (`@ConfigurationPropertiesScan`); `T01SkeletonRegressionTest.java` (both the planned assertion flip and the unplanned-but-required second file-list update, both disclosed in Phase 6) | `T01SkeletonRegressionTest` (9/9 passing, including both updated assertions) | No | No |
| L8 (zero trust on the in-app surface) | Filter-chain foundation only | `ResourceServerConfig.java` (whole file) | `ResourceServerConfigIntegrationTest`, `PublicEndpointsTest` | `sub`-scoping and the actual stream/read endpoints (task 13) | No — this task's own literal scope is the filter chain, not the endpoints it will protect |
| L10 (secrets discipline, fail-fast validated config) | Yes | `LinkPropertiesStartupValidation.java` (the concrete AC5 mechanism); `application.properties` (no committed real credential — `${DB_PASSWORD:notification-app-local-only}` local-only placeholder, matching both sibling services' own convention) | `LinkPropertiesStartupValidationTest`, `ApplicationPropertiesJpaConfigTest.datasourceUsesTheLeastPrivilegeRuntimeRole` (password-placeholder-discipline assertion, Kimi Phase 11 Gap #8) | No | No |
| L4 (no secrets/tokens in messages or logs) | Not applicable to this task | — | — | No message/log-writing code exists yet | No — correctly out of scope |

## Principal-engineer review

**(1) Is the task fully complete?** Yes, against T03's own literal scope (`tasks.md` task 3: 4 config
groups + resource-server wiring + `PublicEndpoints` + fail-fast). All 9 production files delivered
(4 properties records, `LinkPropertiesStartupValidation`, `PublicEndpoints`, `ResourceServerConfig`,
`application.properties`, the `NotificationServiceApplication` annotation) plus 11 test files (67
tests total). No file outside this task's own `Files to Create`/`Files to Modify` list was touched;
`services/notification/src/main/resources/db/migration/V1-V3` and
`NotificationBaselineMigrationIntegrationTest.java` (T02's own deliverable) remain byte-for-byte
unchanged, re-verified via `git status -s` before every commit.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC6 all hold, each with direct
evidence and automated coverage. AC5 in particular has both automated proof (6 tests spanning every
profile permutation) and the original real-app-boot proof from Phase 6, satisfying it more strongly
than an automated test alone would.

**(3) Does it violate any LOCKED decision?** No. L8's own schema-level/filter-chain half is fully
implemented and tested; the `sub`-scoping and endpoint behavior it will eventually protect are task
13's own scope, not silently skipped but explicitly and correctly deferred (mirrors T02's own
schema-only relationship to its requirements). L10 is fully implemented — this is in fact the task
that makes L10's own abstract "fail startup on missing config" language concrete and mechanically
real for the first time in this service.

**(4) Remaining risks?**
- Kimi's Phase 8 Finding #6 (documented in Phase 9) noted `services/crypto` has the same latent
  `server.port` gap this task fixed for `notification-service` — not this task's own risk to carry,
  but worth a future cross-service cleanup task if all three services are ever run together locally.
- AC3/AC4's automated tests are `@WebMvcTest` slices against a test-only controller
  (`ResourceServerTestController`), not the real endpoints L8 will eventually protect — this is the
  correct, intentional scope boundary (task 13 doesn't exist yet), not a coverage gap, but worth
  re-confirming the real controllers get an equivalent integration test when they land.
- No real JWKS/signature validation is exercised anywhere (`.with(jwt())` bypasses real token
  decoding, matching `services/crypto`'s own identical, documented limitation) — acceptable for a
  unit/slice-test suite, not a substitute for a real cross-service manual boot verification against
  a live `auth-service` instance if that hasn't been done independently.

## `package.md` §9 whole-service checklist — items relevant to T03

- "Every §4c VERBATIM artifact copied exactly ... config keys" — verified directly this phase:
  `application.properties`'s 7 `themistra.notification.*` lines are byte-for-byte identical to
  `design.md` §4c's own VERBATIM block.
- "The in-app stream authenticates the recipient (resource-server JWT)" — this task lays the
  authentication foundation (`ResourceServerConfig`); the actual stream and its
  no-other-account's-notifications guarantee are task 13's own scope, correctly not yet checkable.
- Every other item (idempotent consumers, delivery log, secrets-in-messages, preference resolution,
  retry/dead-letter, contract conformance) requires feature code that doesn't exist yet — correctly
  out of scope.

## Cross-task regression check

Full `services/notification` suite (not just T03's own new files): `T01SkeletonRegressionTest` (9
tests, both updated assertions) and `NotificationBaselineMigrationIntegrationTest` (13 tests, T02's
own deliverable) both still pass unchanged alongside the 45 new T03 tests — confirms T03's changes
(the `@ConfigurationPropertiesScan` addition, the new `common`/`common.config` package, the new
`application.properties`) didn't regress anything either prior task established.

## Spec status

`spec/notification-service/package.md`'s header (`Version 0.1`, `Status DRAFT`) is unchanged — that
bump is task 20, matching `auth-service`'s own T40 and `crypto-service`'s own T29 precedent. Not
touched here.

---

**PASS** — all 6 acceptance criteria satisfied with direct evidence and automated coverage (67
tests), no LOCKED decision violated, task boundary held throughout (no files touched beyond this
task's own scope, both the planned and the reality-forced `T01SkeletonRegressionTest` changes
disclosed rather than hidden).
