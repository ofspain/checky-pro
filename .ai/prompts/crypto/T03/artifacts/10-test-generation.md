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
