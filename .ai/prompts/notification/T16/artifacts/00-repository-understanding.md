# notification · T16 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/notification` is a Spring Boot 3 / Java 21 Kafka-consuming, package-by-feature monolith
(`com.themistra.notification`), non-custodial, consume-only (L2). Persistence is Postgres (schema
`notifications`); security is OAuth2 resource-server (`ResourceServerConfig`, L8) validating JWTs
minted by `auth-service`. Seven top-level feature packages exist today: `channel`, `consumer`,
`delivery`, `inapp`, `preference`, `template`, plus `common` (shared plumbing, deliberately not a
"feature module" — `agents.md`'s own L11 wording: "Shared plumbing lives in common"). This task
introduces the first ArchUnit tests anywhere in this service, turning three already-stated standing
rules (L2, L8, L11) from prose into a CI-enforced check — the same conversion `services/auth`
(T32/T35) and `services/crypto` (T20/T25/T27) already made for their own, analogous rules.

## 2. Existing code this task touches

**The real `@Entity` classes (L11's own subject) — 7 total, across 5 packages, none in `channel` or
`common`:**
- `consumer/ProcessedEvent.java`
- `delivery/DeliveryLog.java`, `delivery/DeliveryRetry.java`
- `inapp/InappNotification.java`
- `preference/ChannelPreference.java`, `preference/ContactProjection.java`
- `template/Template.java`

**The real `@RestController` classes (L8's own surface) — exactly 2:**
- `inapp/InappStreamController.java`, `inapp/InappReadController.java`.
- `common/ApiExceptionHandler.java` is `@RestControllerAdvice`, **not** `@RestController` — a plain
  textual grep for `@RestController` matches it as a substring false positive; ArchUnit's own
  `areAnnotatedWith(RestController.class)` operates on the real annotation type via bytecode and
  correctly excludes it (already confirmed and explicitly documented this exact distinction in
  `services/auth`'s own `ArchitectureTest` Javadoc).

**L8's real, single enforcement point:** `common/ResourceServerConfig.java` —
`auth.requestMatchers(PublicEndpoints.PATTERNS).permitAll()` is the *only* `permitAll()` call
anywhere in this service's main code (confirmed by direct inspection of the one security config
class); `PublicEndpoints.PATTERNS` (`common/PublicEndpoints.java`) is the already-existing,
already-correct allowlist source (`/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`).

**L2's real, current state — nothing to find, which is itself the finding:** confirmed directly by
grep across `src/main/java/` — no class anywhere in this service imports `RestTemplate`,
`WebClient`, `HttpClient`, `FeignClient`, or any `com.themistra.auth../com.themistra.crypto../com.themistra.payment..`
package. The delivery path (`delivery/DeliveryOrchestrator.java` and everything it calls) makes
exactly one kind of outbound network call today — `EmailChannel` → `SesEmailTransport` → the AWS
SES v2 SDK (an external, third-party API call, already resolved/accepted at O2/Q2, not a
"synchronous cross-service call" in L2's own sense of calling *another service in this platform*).

**Already-established, directly relevant precedent — not new code this task writes, but the exact
pattern it must follow:**
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` — the literal pattern the
  task statement says to mirror for `shouldPreventCrossModuleEntityImports`, plus
  `shouldEnforcePublicEndpointAllowlist` (L8's own direct analogue: "no class outside
  `SecurityChainsConfig` may call `permitAll()`").
- `services/crypto/src/test/java/com/themistra/crypto/common/CrossModuleEntityArchitectureTest.java`
  — an even closer mirror (crypto's own L15 is worded almost identically to this service's own L11),
  including its own documented `ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES` allowlist pattern for any
  pre-existing, accepted exception (auth and crypto each carry 1-2; this service's own T13 violation
  was already found and *fixed*, not allowlisted — no pre-existing exception is expected here, to be
  confirmed, not assumed, once the rule actually runs against this codebase).
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java` — the
  closest existing precedent for an L2-shaped "only one place may do X" rule, though its own X (a
  specific AWS SDK call) is narrower than "no synchronous cross-service HTTP call" at all.
- `services/crypto/src/test/java/archtestfixtures/` — a dedicated, top-level (not nested under
  `com.themistra.crypto`) test-source package holding deliberately-violating fixture classes
  (`RogueWatchEntityReferencer`, `RogueUnmappedEntity`, `RogueAttestReferencer`) used only to prove a
  rule can actually fail, never swept into the real, scanned package. No such package exists yet in
  `services/notification`.

## 3. Established patterns to follow

- **`@ArchTest` fields do not execute under this monorepo's own Maven Surefire configuration —
  confirmed independently, via real negative-proof runs, in both `services/auth` (T32/T35) and
  `services/crypto` (T20/T25/T27).** A deliberately-introduced violation did not fail `mvn test`
  through the `@ArchTest` field alone in either service. Every real enforcement in both existing
  precedents comes from a plain, ordinary `@Test` method ("a canary") that manually calls
  `someArchRule.check(analyzedClasses)` directly — JUnit Jupiter's own engine, proven to execute
  under Surefire. **This is the single most important fact this task must not miss**: writing only
  `@ArchTest` fields (the seemingly-idiomatic ArchUnit style) would compile, look correct, and
  silently enforce nothing under a real `mvn test` run.
- **Dependency-based, not access-based, entity-boundary checking** —
  `JavaClass.getDirectDependenciesToSelf()`, not `getAccessesToSelf()`. Both existing precedents
  document the same reason: a declared-but-unused field of the entity's own type is already a real
  cross-module coupling even if nothing ever calls a method on it, which an access-based check would
  miss entirely.
- **A fail-fast, not silently-skipped, response to a class outside every known feature module** —
  both existing precedents explicitly make an unmapped `@Entity`/`@RestController` fail the rule
  loudly (with a message explaining why), rather than silently not enforcing anything for it.
- **Allowlist any genuinely pre-existing, accepted exception by fully-qualified name, not by
  loosening the rule itself** — and add a dedicated, separate test proving each allowlist entry
  still corresponds to a real dependency in the current code, so a future refactor that removes the
  need for an exception doesn't leave silent, stale dead configuration behind.
- **A real negative-proof test, not only a canary that passes on already-clean code** — both
  precedents treat "the rule currently finds nothing" and "the rule is actually capable of finding
  something" as two separate things to prove, via a dedicated, deliberately-violating fixture class
  kept physically outside the real scanned package.

## 4. Testing conventions

- Plain JUnit, no Spring context, no Docker, no fixed `Clock` needed — ArchUnit's own
  `ClassFileImporter`/`JavaClasses` analysis is a pure, static, compile-output-based check.
- `archunit-junit5` (version `1.3.0`) has been present in `pom.xml` since T01, pre-scaffolded,
  completely unused in any real test until this task — confirmed by `T01SkeletonRegressionTest`'s
  own cross-service version-alignment check, which already compares this service's own
  `archunit-junit5` version against `services/auth`/`services/crypto`'s identical version, but no
  actual ArchUnit *test* exists anywhere in this service yet.

## 5. Known gaps / unknowns

- **I do not know** the exact scope Phase 2/3 will choose for the L2 rule ("Assert the delivery path
  makes no synchronous cross-service HTTP call") — whether it should be scoped narrowly to
  `com.themistra.notification.delivery..` (matching the task statement's own literal wording) or
  broadened to the whole service (matching L2's own service-wide framing in `design.md`). Both are
  defensible; this is a genuine Phase 2 design decision, not resolved here.
- **I do not know** whether any pre-existing, accepted cross-module entity coupling actually exists
  in this codebase today — unlike auth/crypto (each carrying 1-2), this service's own one known past
  violation (T13, `InAppChannel` importing `InappNotification` directly) was already found and
  *fixed* via `InappNotificationAppender`, not allowlisted. I expect zero exceptions will be needed,
  but this must be confirmed by actually running the rule against this codebase (Phase 6), not
  assumed clean from memory of a fix applied several tasks ago.
- **I do not know** the exact Maven-coordinate version compatibility nuance (if any) between this
  monorepo's own pinned `archunit-junit5:1.3.0` and the specific DSL methods
  (`getDirectDependenciesToSelf`, `resideOutsideOfPackage`, `callMethod`) both existing precedents
  use — both precedents already compile and run against this exact version, so this is very likely a
  non-issue, but worth a direct `javap`-style confirmation before writing new code, not assumed.
