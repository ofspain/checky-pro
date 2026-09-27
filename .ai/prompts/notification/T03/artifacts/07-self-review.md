# notification · T03 · Phase 7 — Self-Review

## Files reviewed

- `common/config/{Email,Link,Retry,Inapp}Properties.java`
- `common/config/LinkPropertiesStartupValidation.java`
- `common/{PublicEndpoints,ResourceServerConfig}.java`
- `application.properties`
- `NotificationServiceApplication.java`, `T01SkeletonRegressionTest.java` (both changes)

## Findings

### Finding 1 — No direct proof that `EmailProperties`/`RetryProperties`/`InappProperties` actually bind from the real `application.properties` keys

**Severity:** Medium

**Evidence:** `EmailProperties.java:13`, `RetryProperties.java:13`, `InappProperties.java:13` each
declare their own `@ConfigurationProperties(prefix = "...")`. Phase 6's own verification only booted
the app twice and confirmed `LinkPropertiesStartupValidation`'s own pass/fail behavior — which
indirectly proves `LinkProperties` binds correctly (its value had to resolve to blank for the
`dev`-profile failure to fire, and the local boot's own success is consistent with, but doesn't
independently confirm, a correctly-bound blank value). Neither boot test exercised `EmailProperties`,
`RetryProperties`, or `InappProperties` in any way that would surface a key-name typo (e.g., a
misspelled `themistra.notification.emial.from` in `application.properties` would still compile,
would still pass every planned Phase 10 unit test of the record's own constructor logic in isolation
— those tests construct records directly with `new EmailProperties(...)`, never via real property
binding — and would silently leave `from`/`transport` bound to Spring's own defaults or fail with an
unrelated-looking `@NotBlank` violation, not a "wrong key name" signal).

**Recommendation:** Phase 10 should include at least one real-binding test per record (a
`@SpringBootTest`-level or `ConfigurationPropertiesBindingPostProcessor`-driven context load that
injects the real bean and asserts its actual values match `application.properties`'s own literals),
not only isolated constructor-unit tests — mirroring how `LinkPropertiesStartupValidationTest` is
already planned to exercise real Spring wiring end-to-end.

### Finding 2 — `LinkPropertiesStartupValidation`'s profile-gated `@Component` pattern has no existing sibling precedent

**Severity:** Low

**Evidence:** `common/config/LinkPropertiesStartupValidation.java` (whole file). Every other
conditional-validation need in this codebase so far (`services/crypto`'s own `ScreeningProperties`)
is expressed as a compact-constructor check gated on a boolean field (`enabled`), not a separate
`@Profile`-gated `@Component`. This is a deliberate, reasoned choice (Kimi Phase 3 Finding #1 exists
precisely because no existing pattern fit `LinkProperties.baseUrl`'s own "blank is valid in exactly
one profile" shape), not an oversight — but it is a genuinely new architectural shape for this
codebase, verified nowhere else.

**Recommendation:** No change needed now. If a later notification-service task needs the same
"required outside `local`" treatment for a different config value, prefer reusing this same
`@Profile("!local")`-gated-component shape rather than inventing a third pattern, to keep the
codebase's own conditional-validation vocabulary small.

## Verification performed

- `mvn -pl services/notification -am verify` — 22 tests, 0 failures, unchanged from Phase 6's own
  final record.
- Re-confirmed (from Phase 6) the two real app-boot proofs are still valid: `local` profile boots
  clean; `dev` profile with no link URL fails with the intended exception. Additionally booted with
  an intentionally invalid `RetryProperties` config
  (`max-backoff-seconds=10 < initial-backoff-seconds=30`) to check error-message clarity for Finding
  1's own concern about silent misbinding: Spring Boot's own failure analyzer produced a clear
  `Description`/`Reason`/`Action` report naming the exact prefix and the compact constructor's own
  message verbatim — confirms this specific cross-field path fails loudly and legibly, not silently;
  this does not resolve Finding 1 (which is about a *key-name* typo, not a *cross-field value*
  error), so Finding 1 stands as-is.
