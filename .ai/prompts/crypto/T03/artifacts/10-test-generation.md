# crypto · T03 · Phase 10 — Test Generation

Test-only phase. No production code changed — `mvn -pl services/crypto -am compile` output is
identical to Phase 9's. 8 test files added, 51 new tests, all mapped below to the frozen brief's
acceptance criteria / requirement IDs. Full suite (new tests + T01's `T01SkeletonRegressionTest`):
**57/57 passing**. `ChainBaselineMigrationIntegrationTest` (T02, Testcontainers) was not run — Docker
is unavailable in this environment, and it's unrelated to T03's scope.

## Files created

- `services/crypto/src/test/java/com/themistra/crypto/common/InternalTestController.java` — test-only
  stand-in mirroring the real internal API (`design.md` §4c) exactly: `POST /internal/v1/watches`,
  `DELETE /internal/v1/watches/{watchId}`, `POST /internal/v1/attest`.
- `.../common/ResourceServerConfigIntegrationTest.java` — 11 tests.
- `.../common/PublicEndpointsTest.java` — 13 tests.
- `.../common/config/ProviderPropertiesTest.java` — 8 tests.
- `.../common/config/FinalityPropertiesTest.java` — 5 tests.
- `.../common/config/ScreeningPropertiesTest.java` — 6 tests.
- `.../common/config/KmsPropertiesTest.java` — 4 tests.
- `.../common/config/SnapshotPropertiesTest.java` — 4 tests.

## Test manifest

| Test | AC / Requirement | Notes |
|---|---|---|
| `ResourceServerConfigIntegrationTest.shouldRequireInternalScopeForWatchAndAttestEndpoints_rejectsUnauthenticated` (×3, parameterized over all 3 internal paths) | **Named test** (package.md §8) → R27, AC1 | Asserts 401 + `WWW-Authenticate: Bearer` + `application/problem+json` body |
| `...shouldRequireInternalScopeForWatchAndAttestEndpoints_rejectsUnderScopedToken` (×3) | Named test → R27, AC2 | Authenticated but wrong scope → 403 + problem+json |
| `...shouldRequireInternalScopeForWatchAndAttestEndpoints_acceptsCorrectScope` (×3) | Named test → R27, AC3 | Correct scope → 2xx |
| `...shouldRequireInternalScopeForWatchAndAttestEndpoints_missingTokenEntirely` | R27, AC1 | Bare request, no `.with(jwt())` at all — distinct from "wrong scope" |
| `...shouldAllowInternalScopeAlongsideExtraScopes` | R27, AC3, amendment #12 (at-least semantics) | Token with `internal.crypto:write` + an unrelated extra scope still succeeds |
| `PublicEndpointsTest.patternsListExactlyTheFourDeclaredPaths` | AC4 | Direct array assertion |
| `...declaredPublicPathsAreNotBlockedBySecurity` (×6) | AC4 | Health/liveness/readiness/info/prometheus/well-known — security layer doesn't 401/403 them (may 404, no real handler in this slice — see Known Limitations) |
| `...sensitiveActuatorPathsAreNotPublic` (×6) | AC4 (negative sweep) | `/actuator/env`, `/beans`, `/configprops`, `/loggers`, `/heapdump`, `/threaddump` → 401, proving the allowlist isn't a blanket `/actuator/**` |
| `ProviderPropertiesTest.bindsValidTwoChainConfiguration` | AC5 (bind-success) | |
| `...failsWhenChainsListMissing` | AC5 (fail-fast) | |
| `...failsWhenQuorumThresholdMissing` | AC5 | |
| `...failsWhenProviderTimeoutIsNonPositive` | AC5 | |
| `...failsWhenApiKeySecretNameBlank` | AC5 | |
| `...failsWhenChainNotInLaunchScope` | Phase 9 Finding 2/6 (chain `@Pattern`) | `"SOLANA"` rejected |
| `...failsWhenQuorumThresholdExceedsConfiguredProviderCount` | Phase 9 Finding 7 (quorum-vs-provider-count) | |
| `...succeedsWhenQuorumThresholdEqualsProviderCount` | Phase 9 Finding 7 (boundary — equal is valid, not just less-than) | |
| `FinalityPropertiesTest.bindsValidEnabledChains` | AC5 | |
| `...failsWhenEnabledChainsMissing` | AC5 | |
| `...failsWhenEnabledChainsEmpty` | AC5 (blank element) | |
| `...failsWhenChainNotInLaunchScope` | Phase 9 Finding 2/6 | `"SOLANA"` rejected |
| `...hasNoConfirmationOrThresholdShapedField` | **AC7**, L4 | Reflection over `getRecordComponents()` |
| `ScreeningPropertiesTest.bindsTheLocalProfileShape_disabledWithNoBaseUrl` | AC6 (local shape) | |
| `...bindsWhenEnabledWithBaseUrlAndApiKey` | AC5 (bind-success) | |
| `...failsWhenEnabledTrueWithoutBaseUrl` | AC5 (fail-fast) | |
| `...failsWhenEnabledTrueWithoutApiKeySecretName` | AC5 | |
| `...failsWhenBaseUrlSetButNotEnabled` | Phase 9 Finding 3 (reverse-direction guard) | |
| `...failsWhenBaseUrlSetAndEnabledOmittedEntirely` | Phase 9 Finding 3 (omitted, not just `false`) | |
| `KmsPropertiesTest.bindsValidKeyId` | AC5 | |
| `...failsWhenKeyIdMissing` | AC5 | |
| `...failsWhenKeyIdBlank` | AC5 | |
| `...exposesExactlyOneKeyIdentifyingField` | **AC8**, L11 | Reflection — no ARN+region redundancy |
| `SnapshotPropertiesTest.bindsValidConfiguration` | AC5 | |
| `...failsWhenBucketMissing` / `...failsWhenPrefixMissing` / `...failsWhenRegionMissing` | AC5 | One per field |

**AC1–AC8: all covered.** R27: covered by the named test + its 4 variants. L4/L11: covered by the
two reflection tests. The 6 Phase 9 fixes each have at least one dedicated positive and/or negative
test.

## Negative-proof (mutation testing), not just green tests

Per this codebase's established convention (T02's own negative-proof precedent), the most
safety-critical test — the scope check itself — was verified to actually catch a regression, not
just pass trivially:

1. Mutated `ResourceServerConfig.INTERNAL_SCOPE_AUTHORITY` from `"SCOPE_internal.crypto:write"` to
   `"SCOPE_wrong.scope"`.
2. Re-ran `ResourceServerConfigIntegrationTest` alone: **4 tests failed** (`acceptsCorrectScope` ×3,
   `shouldAllowInternalScopeAlongsideExtraScopes`) — exactly the tests that assert the correct-scope
   path succeeds, each now getting 403 instead of 2xx.
3. Reverted via `diff` against a pre-mutation backup (confirmed byte-identical), re-ran the full new
   suite: 57/57 green again. `git status` confirms no stray changes were left behind.

## Known limitations (flagged, not hidden)

- **No real end-to-end JWT decoding/signature/issuer validation is exercised.**
  `SecurityMockMvcRequestPostProcessors.jwt()` injects a pre-built `Authentication` directly into the
  security context, bypassing the actual `NimbusJwtDecoder`/JWKS-fetch/issuer-check path entirely —
  this is the standard, documented way to unit-test `authorizeHttpRequests` rules in isolation, but it
  does not prove the `jwk-set-uri`/`issuer-uri` properties actually work against a real auth-service
  token. That would require either a running auth-service instance or a self-signed test JWKS server;
  neither was in scope for this task's test suite. The properties' *presence and correctness* were
  verified by direct inspection in Phase 9's resolution log, not by an automated test here.
- **No full-context (`@SpringBootTest`) boot smoke test was added.** Docker/Postgres is unavailable in
  this environment (confirmed via `docker ps`), and `spring-boot-starter-data-jpa` on the classpath
  means a full context load needs a reachable datasource. The `@WebMvcTest`/`ApplicationContextRunner`
  slices used throughout this phase deliberately avoid that dependency, at the cost of not proving the
  *entire* `local` profile boots end-to-end with the real `application.properties` file as one unit —
  each piece (security wiring, each properties class) is proven independently instead.
- `PublicEndpointsTest`'s positive cases can legitimately 404 (no real actuator/well-known handler
  exists in a `@WebMvcTest(controllers = InternalTestController.class)` slice) — the assertion is
  scoped exclusively to "security didn't block with 401/403," documented inline in the test class.

## Verification

```
mvn -pl services/crypto -am test-compile   → BUILD SUCCESS
mvn -pl services/crypto test -Dtest='ProviderPropertiesTest,FinalityPropertiesTest,
  ScreeningPropertiesTest,KmsPropertiesTest,SnapshotPropertiesTest,PublicEndpointsTest,
  ResourceServerConfigIntegrationTest,T01SkeletonRegressionTest'
  → Tests run: 57, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

## Addendum — Phase 11 (Kimi test review) follow-up

Kimi's Phase 11 review (`artifacts/11-test-review.md`) raised 15 gaps. There is no dedicated
resolution phase for test findings (unlike Phase 9 for code), so — consistent with this pipeline's
own judgment-call precedent elsewhere — the clearly valuable, low-risk, in-scope ones were applied
directly before Phase 12; the rest were rejected/deferred with reasons, mirroring the Phase 9
resolution-log format:

| Gap | Decision | Change |
|---|---|---|
| 1 (`anyRequest().authenticated()` untested) | REJECTED | Duplicate of Phase 9 Kimi Finding 2, already rejected (matches auth precedent) |
| 2 (generic `hasFailed()` masks wrong-cause failures) | **ACCEPTED** | Strengthened the 4 custom compact-constructor checks (quorum-vs-provider-count, both screening enabled-mismatch directions) to assert `rootCause().isInstanceOf(IllegalStateException.class).hasMessageContaining(...)` instead of bare `hasFailed()` |
| 3 (problem+json only substring-checked) | **ACCEPTED** | 401/403 tests now assert full `type`/`title`/`status`/`detail` via `jsonPath()` |
| 4 (actuator positive tests pass even if exposure config is removed) | **ACCEPTED (lightweight)** | New `ApplicationPropertiesSecurityConfigTest` reads the real committed `application.properties` and asserts the exact `management.*` values, instead of the heavier `@SpringBootTest`+Docker route Kimi suggested |
| 5 (`PublicEndpoints.PATTERNS` wiring-identity not proven via reflection) | REJECTED | Already behaviorally proven (single source of truth, no parallel hardcoded list exists to diverge from); reflecting Spring Security's internal matcher list for a stability guarantee it doesn't offer is excessive coupling |
| 6 (`missingTokenEntirely` redundant) | **ACCEPTED** | Replaced with `shouldRejectNonBearerAuthorizationScheme` (a `Basic` header — genuine new edge case) |
| 7 (real `scope`→authority conversion untested) | **ACCEPTED** | New `ScopeClaimConversionTest` exercises Spring's actual default `JwtGrantedAuthoritiesConverter` against a JSON-array `scope` claim shaped per `contracts/api/token-claims.md` Path 2 |
| 8 (`issuer-uri` not exercised) | **ACCEPTED (lightweight)** | Folded into `ApplicationPropertiesSecurityConfigTest` — asserts the key is present and non-blank; proving Spring Boot's internal `JwtIssuerValidator` wiring itself was judged not worth reimplementing Boot's own autoconfiguration in a test |
| 9 (screening guard only covers `baseUrl`, not `apiKeySecretName` alone) | **ACCEPTED — production fix** | Widened `ScreeningProperties`'s reverse-direction compact-constructor check to also guard `apiKeySecretName`; added `failsWhenApiKeySecretNameSetButNotEnabled` |
| 10 (invalid screening timeout/retry untested) | **ACCEPTED** | Added 3 negative tests (`connect-timeout-seconds=0`, `read-timeout-seconds=-1`, `retry-max-attempts=-1`) |
| 11 (placeholder-value rejection untested) | REJECTED | Re-raise of the already-rejected Phase 4 amendment #14 ask |
| 12 (chain case-sensitivity untested) | **ACCEPTED** | Added `failsWhenChainIsLowercase` to both `ProviderPropertiesTest` and `FinalityPropertiesTest` |
| 13 (duplicate chain entries unconstrained) | REJECTED (deferred) | New validation beyond what Phase 9 scoped; duplicates are redundant, not unsafe — no explicit AC requires it |
| 14 (finality/provider chain cross-consistency) | REJECTED | Duplicate of Phase 9 Kimi Finding 8, already rejected/deferred |
| 15 (no full-context `local` boot smoke test) | ACKNOWLEDGED, no action | Already an honest disclosed limitation above; Docker still unavailable in this environment |

**Result: 67/67 tests passing** (was 57; +10 from gaps 9, 10, 12 (×2), plus 2 new test classes for
gaps 4/7/8, with gap 6 a net-zero swap and gaps 2/3 strengthening existing tests in place).
Negative-proof performed on both new mechanisms: temporarily removing the widened screening guard
(gap 9's fix) caused `failsWhenApiKeySecretNameSetButNotEnabled` to fail with the wrong exception
type — concretely confirming gap 2's own point, since a bare `hasFailed()` assertion would have
missed that regression entirely (the context still failed, just for the wrong reason). Reverted
cleanly (`diff` against pre-mutation backup — byte-identical); full suite green again afterward.
