# notification · T03 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T03: validated config + JWT resource-server wiring`

## Commit message

```
notification-service T03: validated config + JWT resource-server wiring

Add four validated @ConfigurationProperties records - EmailProperties,
LinkProperties, RetryProperties, InappProperties - bound to design.md
§4c's own VERBATIM config keys. RetryProperties enforces a cross-field
check (max-backoff-seconds >= initial-backoff-seconds) beyond simple
@Min bounds.

Add LinkPropertiesStartupValidation, a @Profile("!local")-gated
component giving AC5 ("startup fails on missing config in non-local
profiles") a concrete, testable mechanism: LinkProperties.baseUrl has no
non-blank VERBATIM default (it's legitimately blank in local, per Q4's
own unresolved status), so its non-blank requirement is enforced only
outside local, not via an unconditional record-level constraint that
would break local startup.

Add PublicEndpoints (3-path actuator-only allowlist) and
ResourceServerConfig (one SecurityFilterChain: stateless, CSRF disabled,
allowlist permitted, everything else authenticated, RFC 9457 401/403
bodies) - structurally mirrors crypto-service's own ResourceServerConfig
but omits its /internal/v1/** scope-authority line, since no such
endpoints exist in this service.

Add application.properties (new file - didn't exist anywhere in this
module before this task): datasource pointed at notification_app (T02's
least-privilege role), JPA/Flyway/Kafka/actuator/resource-server
sections, all 7 themistra.notification.* keys byte-for-byte identical to
design.md's own VERBATIM block, and server.port=8082 to avoid colliding
with auth-service's own 8080 default.

Add @ConfigurationPropertiesScan to NotificationServiceApplication (the
first task introducing any @ConfigurationProperties class). Updates two
T01SkeletonRegressionTest assertions made stale by this task's own
authorized changes: the planned @ConfigurationPropertiesScan flip, and an
unplanned second conflict discovered during implementation (the bare-
production-tree file-count assertion, now naming all 8 authorized files
explicitly).

67 tests total (9 T01 + 13 T02 unaffected + 45 new): real
ApplicationContextRunner property-binding tests for all four records (not
just constructor-level unit tests), 6 profile-permutation tests for the
fail-fast mechanism, a WebMvcTest-slice resource-server integration test,
a true static source-sweep for stray permitAll() calls, and two
application.properties key-coverage tests.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/common/config/{Email,Link,Retry,Inapp}Properties.java`
- `.../common/config/LinkPropertiesStartupValidation.java`
- `.../common/{PublicEndpoints,ResourceServerConfig}.java`
- `services/notification/src/main/resources/application.properties`
- `services/notification/src/test/java/com/themistra/notification/common/config/{Email,Link,Retry,Inapp}PropertiesTest.java`
- `.../common/config/LinkPropertiesStartupValidationTest.java`
- `.../common/{PublicEndpointsTest,ResourceServerConfigIntegrationTest,ResourceServerTestController}.java`
- `.../ApplicationProperties{Security,Jpa}ConfigTest.java`

**Modified**
- `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
  (`@ConfigurationPropertiesScan`)
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (two assertions, both disclosed in Phase 6/9)

**Process artifacts**
- `.ai/prompts/notification/T03/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

Establishes the config-validation and resource-server-security foundation every later
notification-service feature task depends on, mirroring `crypto-service`'s own T03. Gives AC5's
abstract "fail startup on missing config in non-local profiles" a concrete, working, tested
mechanism for the one config value that genuinely needs it (`LinkProperties.baseUrl`), and fixes a
real local-dev ergonomics gap (`server.port` colliding with `auth-service`) surfaced during
independent review.

## Testing performed

- `mvn -pl services/notification -am verify` — 67 tests, 0 failures, `BUILD SUCCESS`.
- Real app boot (Phase 6): `local` profile starts clean (Tomcat on `8082`, no validator bean
  created); `dev` profile with no `AUTH_EMAIL_LINK_BASE_URL` genuinely fails context refresh with the
  intended `IllegalStateException`; a deliberately-invalid `RetryProperties` config produced a clear
  Spring Boot failure-analyzer report naming the exact property and reason.
- Two real mutation tests, both reverted clean (`git status -s` empty afterward): (1) changed
  `.anyRequest().authenticated()` to `.anyRequest().permitAll()` — 9 of 16 security tests failed
  immediately; (2) added a second, forgotten `.permitAll()` path — only the new static-sweep test
  (not the 13 pre-existing behavioral tests in the same class) caught it, directly validating the
  gap Kimi's own Phase 11 review identified.
- `git status -s services/auth services/crypto` — empty throughout; no sibling service touched.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 3 ("Config & resource server").
- **Requirements (foundation only, no behavior this task):** R1, R2, R5 (depend on `LinkProperties`
  existing), R16, R17 (depend on `ResourceServerConfig` existing).
- **LOCKED decisions:** L8 (zero trust on the in-app surface — filter-chain foundation only, the
  endpoints it protects are task 13's own scope), L10 (secrets discipline, validated config fails
  startup on missing/invalid values in non-local profiles — this task's own literal, primary
  deliverable).

## Known, deliberate gaps (not this task's scope)

- No entity/repository/feature-module code — later tasks' own scope per `design.md` §6.
- `EmailProperties.transport`/`InappProperties.transport` carry no enum/format constraint (Kimi Phase
  3 Finding #4) — deferred to the tasks that implement the corresponding channels.
- `ResourceServerConfig` has no scope/authority rule (unlike `crypto-service`'s own
  `/internal/v1/**` check) — correctly absent, since no endpoint requiring one exists until task 13.
- `services/crypto` has the same latent `server.port` gap this task fixed for
  `notification-service` (Kimi Phase 8 Finding #6) — not fixed here, out of this task's own scope to
  touch a sibling service's files.
- No real JWKS/signature validation is exercised by any test (`.with(jwt())` bypasses real token
  decoding) — matches `crypto-service`'s own identical, documented limitation.

## Reviewer notes

- Kimi's Phase 3 (design), Phase 8 (implementation), and Phase 11 (test) reviews raised 8, 7, and 8
  findings respectively — all 23 verified against actual source before disposition. Phase 8's
  Findings 1-5 (missing T03 test files) were the one case in this task requiring a nuanced
  disposition rather than a simple accept/reject: correct as stated, but describing an intentional,
  spec-sanctioned two-phase split (Phase 6 = code only, Phase 10 = tests) already established by this
  pipeline and identically precedented by `crypto-service`'s own T03 — deferred and tracked forward
  rather than rushed into Phase 9, then genuinely fulfilled at Phase 10.
- Two real, unplanned deviations surfaced during implementation, both disclosed rather than hidden:
  a second `T01SkeletonRegressionTest` conflict (Phase 6) and a test-fixture design flaw caught only
  by mutation-testing it (Phase 10/11's `WHERE`-clause-shaped precedent from T02 recurring in a new
  form — here, a first-draft `permitAll()` sweep would have been fine, but the underlying principle
  "verify a check actually catches what it claims to" was what caught Gap #3's own real value).

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T03.**
