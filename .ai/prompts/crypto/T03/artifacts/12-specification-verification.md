# crypto · T03 · Phase 12 — Specification Verification

Principal-engineer sign-off pass over the final implementation + tests against `requirements.md`,
`design.md`, `tasks.md`, and the frozen brief (`artifacts/04-frozen-task-brief.md`), for T03 only.

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R27** — internal endpoints require a valid service-to-service JWT bearing `internal.crypto:write`; reject unauthenticated/under-scoped | Yes | `ResourceServerConfig.java:53-56` (`permitAll` → `hasAuthority` on `/internal/v1/**` → `authenticated()`); `application.properties:31-32` (JWKS/issuer source) | Yes — named test + 4 variants across `ResourceServerConfigIntegrationTest.java` (unauthenticated, under-scoped, correct-scope, non-Bearer scheme, extra-scope-allowed) | No | No — scope is at-least per amendment #12, confirmed by `shouldAllowInternalScopeAlongsideExtraScopes` |
| **AC1** — unauthenticated → 401 problem+json | Yes | `ResourceServerConfig.java:71-77` (entry point, `WWW-Authenticate` + problem body) | Yes — `...rejectsUnauthenticated` (×3, full `jsonPath` body assertion) | No | No |
| **AC2** — under-scoped → 403 problem+json | Yes | `ResourceServerConfig.java:80-85` | Yes — `...rejectsUnderScopedToken` (×3, full `jsonPath` body assertion) | No | No |
| **AC3** — correct scope → passes security layer | Yes | `ResourceServerConfig.java:54` | Yes — `...acceptsCorrectScope` (×3) + `shouldAllowInternalScopeAlongsideExtraScopes` | No | No |
| **AC4** — `PublicEndpoints` = exactly actuator health/info/prometheus + well-known, nothing else `permitAll` | Yes | `PublicEndpoints.java:12-17` | Yes — `patternsListExactlyTheFourDeclaredPaths`, `declaredPublicPathsAreNotBlockedBySecurity` (×6), `sensitiveActuatorPathsAreNotPublic` (×6, negative sweep) | No | No |
| **AC5** — 5 properties classes fail startup on missing/invalid config | Yes | `ProviderProperties.java`, `FinalityProperties.java`, `ScreeningProperties.java`, `KmsProperties.java`, `SnapshotProperties.java` (all `@Validated` + Jakarta constraints; 3 have compact-constructor cross-field checks) | Yes — dedicated fail-fast test per class (`ProviderPropertiesTest` ×5, `FinalityPropertiesTest` ×3, `ScreeningPropertiesTest` ×7, `KmsPropertiesTest` ×2, `SnapshotPropertiesTest` ×3) | No | No |
| **AC6** — `local` boots with no real credentials | Yes | `application.properties:44-79` (all placeholder/disabled values, screening `enabled=false`) | Reasoned + indirectly covered — every `@WebMvcTest`/`ApplicationContextRunner` test in the suite runs under `local`'s actual property file successfully; `ScreeningPropertiesTest.bindsTheLocalProfileShape_disabledWithNoBaseUrl` directly proves the local shape binds | No direct `@SpringBootTest` full-boot proof (see Remaining Risks) | Documented, not hidden |
| **AC7 (L4)** — `FinalityProperties` has no confirmation-count/threshold field | Yes | `FinalityProperties.java:21-23` (single component: `enabledChains`) | Yes — `hasNoConfirmationOrThresholdShapedField` (reflection) | No | No |
| **AC8 (L11)** — `KmsProperties` exposes exactly one key-identifying field | Yes | `KmsProperties.java:16-18` (single component: `keyId`) | Yes — `exposesExactlyOneKeyIdentifyingField` (reflection) | No | No |
| **L12** — screening config stays vendor-agnostic | Yes | `ScreeningProperties.java:20-27` (generic fields only) | Yes — bind-success/fail-fast tests use no vendor-specific shape | No | No |
| **L13** — no committed secrets; fail-fast in non-local profiles | Yes | `application.properties` — all placeholder values obviously named (`local-only-fake-*`); every properties class validated | Yes — fail-fast tests per class | No | No (automated placeholder-*rejection* was explicitly rejected at Phase 4/9 — a deliberate scope decision, not a gap) |
| **L15** — module boundaries, shared plumbing only in `common` | Yes | All 7 new files under `common/` or `common/config/` | N/A (structural) | No | No |
| **Task statement's own text** ("Wire service-to-service JWT validation requiring `internal.crypto:write`… `PublicEndpoints` allows only actuator + well-known") | Yes | As above | Yes | No | No |

## Frozen-brief file-list compliance

`git status --porcelain services/crypto` (excluding `target/`) shows changes only in: the 5
properties classes, `PublicEndpoints.java`, `ResourceServerConfig.java`,
`CryptoServiceApplication.java`, `application.properties`, and 8 test files under
`common`/`common/config` — every one of these is on the frozen brief's Files to
Create/Modify list. `git status --porcelain spec/` is empty — no specification file was touched at
any point in this task. Files-NOT-to-Modify (T01/T02 migrations, tests, `pom.xml`) are untouched.

## Answers

**(1) Is the task fully complete?** Yes, for T03's own scope. All three task-statement clauses
(validated `@ConfigurationProperties` for the 5 named areas; service-to-service JWT + scope
enforcement on internal endpoints; the narrowed `PublicEndpoints` allowlist) are implemented and
tested. Work explicitly deferred to later tasks (real `WatchController`/`AttestController`,
`ApiExceptionHandler` for business errors, contract files) was correctly identified as out of scope
in Phase 2 and never attempted here.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC8 all have passing tests
(67/67 green, `mvn -pl services/crypto -am compile`/`test-compile` clean). AC6 is satisfied by
direct property inspection plus every test in the suite successfully running under the real `local`
profile's property file, though not by one dedicated `@SpringBootTest` full-boot assertion (Docker
unavailable in this environment — see Remaining Risks, not a correctness gap).

**(3) Does it violate any LOCKED decision?** No. L4, L11, L12, L13, and L15 were each identified as
in-scope in Phase 1 and are respected: finality config holds no confirmation count, KMS config holds
no signing capability, screening stays vendor-agnostic, no secret is committed, and all new code
lives under `common`. `agents.md`'s Security/Configuration rules are followed; the one literal-text
mismatch (`/internal/v1/**` vs. agents.md's `/internal/v1/*`) was surfaced, justified (a real
sub-resource path requires it), and accepted at the Phase 4 gate rather than silently deviated from.

**(4) Remaining risks?**
- No real end-to-end JWT signature/issuer validation was exercised against a live auth-service or
  JWKS server (documented limitation, Phase 10/11) — `ScopeClaimConversionTest` proves the claim-shape
  assumption directly against Spring's real converter, but the network-facing decoder path itself
  relies on Spring Boot's own well-tested autoconfiguration rather than a test in this suite.
- No full `@SpringBootTest` proves the committed `application.properties` boots end-to-end as one
  unit; `ApplicationPropertiesSecurityConfigTest` closes the most likely failure mode (a
  deleted/typo'd key) without needing Docker, but a genuine full-context proof is still deferred until
  Docker is available or a later task's own Testcontainers infrastructure (e.g. T26's end-to-end test)
  covers it incidentally.
- The named test `shouldRequireInternalScopeForWatchAndAttestEndpoints` is proven against a
  path/method-mirroring test-only controller, not the real `WatchController`/`AttestController` (which
  don't exist until T15/T21) — flagged since Phase 2 as a scoping decision, not a gap; those tasks
  must re-verify the same behavior against the real endpoints when built.
- The platform-level gap noted at Phase 4 (auth-service's `client_credentials` tokens carry no
  resource-audience claim, so `internal.crypto:write` scope possession is the sole authorization
  boundary between internal callers) remains open at the platform level — explicitly out of this
  task's power to fix, deferred to the auth-service/platform spec owner.

## Verdict

**PASS** — every requirement, LOCKED decision, and acceptance criterion in T03's scope is implemented,
tested, and traceable to the frozen brief; remaining risks are documented limitations of the current
environment (no Docker) and deliberate task-boundary decisions, not defects.
