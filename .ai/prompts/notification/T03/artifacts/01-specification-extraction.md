# notification · T03 · Phase 1 — Specification Extraction

## Business Rules

No individual R-numbered requirement is behaviorally implemented by this task — like T02, this task
lays down foundation (config + security wiring) that later requirements' own implementations depend
on. Relationship to requirements:
- **R1/R2/R5** (verification/reset/receipt links) depend on the `link.base-url` config this task
  introduces (Q4) — the value itself stays a placeholder/env-driven default here; the tasks that
  render these messages (later) are what actually consume it.
- **R16/R17** (in-app stream authenticated/scoped, read API authenticated/scoped) depend on this
  task's `ResourceServerConfig` as their security foundation — the endpoints themselves
  (`InappStreamController`/`InappReadController`) are task 13's own scope, not built here.
- **R15** (no secrets/tokens in messages or logs) is indirectly relevant: config values are read via
  validated properties, never logged, and no real secret value is ever committed to
  `application.properties` (only a `local-only` placeholder or a secret *name* for out-of-band
  injection, mirroring `crypto-service`'s own `KmsProperties`/`ScreeningProperties` pattern).

## Locked Decisions

- **L8** (header-scoped). Zero trust on the in-app surface: JWT resource-server validation against
  the Auth JWKS, results scoped to the caller's `sub` (not enforceable by `ResourceServerConfig`
  alone — the filter chain authenticates the request; `sub`-scoping is each endpoint's own query
  concern, task 13's scope). `PublicEndpoints` allows only actuator health/info/prometheus — no
  other endpoint may be public.
- **L10** (widened from the header's `L8`-only scope — the task statement's own literal words,
  "Startup fails on missing config in non-local profiles," are L10's exact text, not L8's). Secrets
  discipline: no email-transport credential, DB password, or key committed; validated
  `@ConfigurationProperties` fail startup on missing/invalid config in non-local profiles.
- **L4** (tangential). No secrets or tokens in messages or logs — relevant only in that config
  values must never be logged in a way that would leak a secret; this task introduces no logging of
  its own config values and no test asserts this beyond what L10's validation already implies.

## Files involved

**New, this task's own deliverable (per `design.md` §6 `common/` and `common/config/*`):**
- `services/notification/src/main/java/com/themistra/notification/common/config/EmailProperties.java`
  — prefix `themistra.notification.email` (`from`, `transport`).
- `.../common/config/LinkProperties.java` — prefix `themistra.notification.link` (`base-url`).
- `.../common/config/RetryProperties.java` — prefix `themistra.notification.retry`
  (`max-attempts`, `initial-backoff-seconds`, `max-backoff-seconds`).
- `.../common/config/InappProperties.java` — prefix `themistra.notification.inapp` (`transport`).
- `.../common/ResourceServerConfig.java` — JWT resource-server filter chain (L8).
- `.../common/PublicEndpoints.java` — actuator-only allowlist.
- `services/notification/src/main/resources/application.properties` — does not exist anywhere in
  this module yet; this task creates it.

**Existing, to extend (narrow, justified changes only):**
- `NotificationServiceApplication.java` (T01) — add `@ConfigurationPropertiesScan`. T01's own class
  Javadoc explicitly anticipates this: "Add each annotation in the task that actually introduces the
  thing it enables" — this is that task for this annotation, mirroring `CryptoServiceApplication`'s
  own T03 precedent exactly.
- `T01SkeletonRegressionTest.java` (T01) — **must** change one assertion. This file currently asserts
  `.doesNotContain("@ConfigurationPropertiesScan")` (line 198) as a T01-era fact. That fact stops
  being true the moment this task adds the annotation; leaving the assertion as `doesNotContain`
  would make this task's own correct implementation fail T01's own test. This is not a scope
  violation of the kind T02 deliberately avoided (T02's own "don't touch T01's test" rule was about
  *not preempting* a later task's own deliverable; here, T03 *is* that later task, and T01's own
  Javadoc/test were written expecting exactly this update). The narrowest correct fix is flipping
  that one line's `doesNotContain` to `contains` — not touching `@EnableScheduling`/
  `@EnableSchedulerLock` (still correctly absent; scheduling is task 14's own scope).

**Read-only precedent to mirror:**
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ScreeningProperties.java` (and
  its 5 siblings) — the record + `@Validated` + compact-constructor-validation shape.
- `services/crypto/src/main/java/com/themistra/crypto/common/{ResourceServerConfig,PublicEndpoints}.java`
  — the filter-chain/allowlist shape for a service that only validates JWTs minted by `auth-service`.
- `services/crypto/src/main/resources/application.properties`'s own "Resource server (T03, R27)" and
  per-config-group sections — the property-file idiom (`${ENV_VAR:local-default}`, inline
  justification comments, `management.endpoints.web.exposure.include`).

**Not touched:** any file under `spec/`; `services/auth`, `services/crypto` source (precedent only);
`NotificationBaselineMigrationIntegrationTest.java`'s stale "no `application.properties` exists"
Javadoc line is flagged (Phase 0) but is T02's own file and not in this task's `Files to Create`/
`Files to Modify` list — whether it needs a follow-up edit is Phase 2's own call, not decided here.

## Dependencies

`spring-boot-starter-oauth2-resource-server` + `spring-security-oauth2-resource-server` (already in
`pom.xml`, T01), `spring-boot-starter-validation` (already present, needed for `jakarta.validation`
annotations on the properties records), `spring-boot-starter-actuator` (already present). No new
Maven dependency — T03 is config/wiring only. Config keys depend on nothing runtime-side yet (no
`EmailChannel`/`InAppChannel` exists to consume them — those are later tasks).

## Acceptance Criteria

1. **AC1.** Four validated `@ConfigurationProperties` records exist (email, link, retry, in-app),
   each `@Validated`, bound to the exact prefixes/keys `design.md` §4c's "New configuration keys"
   block lists.
2. **AC2.** `application.properties` exists and contains every key from that same block, using
   `${ENV_VAR:default}` syntax for anything environment-specific, matching the sibling services'
   established idiom.
3. **AC3.** `ResourceServerConfig` exists: one `SecurityFilterChain` bean, stateless, CSRF disabled,
   `PublicEndpoints.PATTERNS` permitted, everything else authenticated, JWT validated against
   `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`/`issuer-uri` (both set).
4. **AC4.** `PublicEndpoints.PATTERNS` contains only the actuator health/info/prometheus paths — no
   other pattern.
5. **AC5.** Startup fails (a real exception, not a silent default) when a required config value is
   missing/invalid outside the `local` profile — mechanism to be resolved in Phase 2 (Phase 0 flagged
   this as genuinely unresolved in the current codebase; no sibling service has a per-profile
   properties file yet).
6. **AC6.** `NotificationServiceApplication` carries `@ConfigurationPropertiesScan`;
   `T01SkeletonRegressionTest`'s corresponding assertion is updated to match.

## Tests required

`package.md` §8's 19 named tests all require feature code this task doesn't add (consumer,
preference resolution, template rendering, in-app controllers) — none apply directly, mirroring
T02's own Phase 1 finding. Boundary tests implied by this task's own ACs:
- Each `*Properties` record's own validation logic (valid construction succeeds; invalid/missing
  required fields throw, mirroring `ScreeningProperties`'s own conditional-requirement tests).
- A `PublicEndpoints` sweep test proving no `permitAll()` exists outside the declared allowlist
  (mirrors `services/crypto`'s own `PublicEndpointsTest`).
- A resource-server integration test proving an unauthenticated request to a non-public path is
  rejected and a validly-signed JWT is accepted (mirrors `services/crypto`'s own
  `ResourceServerConfigIntegrationTest` — not yet read in full; Phase 2 should confirm its exact
  shape before committing to duplicating it).
- `T01SkeletonRegressionTest`'s updated assertion, plus a new/extended check (this task's own file,
  not T01's) proving `application.properties` now exists with the expected keys — mirrors
  `services/crypto`'s own `ApplicationPropertiesSecurityConfigTest`, not yet read in detail.

## Open Questions

1. **AC5's real mechanism is unresolved** (Phase 0 finding, restated here as a blocker for Phase 2,
   not this phase). Neither sibling service has an `application-{profile}.properties` file; whether
   "non-local profiles fail on missing config" means (a) real values must arrive via env var with no
   `:default` fallback so the property is genuinely absent outside `local` and Spring's own
   placeholder resolution fails, or (b) a profile-specific properties file needs to be introduced by
   this task, is not decided anywhere in the spec or the existing codebase. Phase 2 must propose one;
   this is the single most consequential decision this task makes.
2. **Q4 (email link base URL)** is a genuine blocker only for the tasks that *render* links (later);
   this task's own `link.base-url` key can be added as a generically-named, env-driven placeholder
   (`${AUTH_EMAIL_LINK_BASE_URL:}` per `design.md` §4c's own VERBATIM default) without resolving Q4
   itself — not a blocker for T03.
3. **Q3 (in-app transport: SSE vs WebSocket)** — `design.md` §4c's own VERBATIM config default
   (`themistra.notification.inapp.transport=sse`) already encodes the spec's own recommendation; not
   a blocker for this task, since the property is a string value, not a live SSE/WebSocket wiring
   decision (that's task 13's own scope).
