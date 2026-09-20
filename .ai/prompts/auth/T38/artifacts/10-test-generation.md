<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T38 · Phase 10 — Test Generation

Originally scoped as zero-code-change/no-new-tests. **Revised at Phase 11** (Kimi gap-closure, human
approved): three permanent regression guards added for the defect classes that had no durable
guard — see the gap-closure table below. AC1 and AC2 already had durable, pre-existing guards and
are unchanged.

## AC → enforcement mechanism (post-Phase-11)

| AC | Defect class | Enforcement/evidence mechanism | Owning task |
|---|---|---|---|
| AC1 | Plaintext credentials | Hash/encrypt-at-rest on every credential field (`Account`, `RefreshTokenFamily`, `ApiKey`, `MfaSeedEncryption`); `@ConfigurationProperties` local-only defaults refused in non-local profiles | T04-T25 (original implementation) |
| AC2 | Unauthenticated admin routes | `ArchitectureTest.shouldEnforcePublicEndpointAllowlist`, `admin_controller_handlers_require_preauthorize` | T32, earlier admin work |
| AC3 | Shared model artifact | **New**: `GapAnalysisDefectRegressionTest.noSharedModelArtifactDependencyIsIntroduced` | T38 |
| AC4 | `Long.getLong` misread / `@Value` default | Uniform `@ConfigurationProperties`/`@Validated` convention (pre-existing); **new**: `GapAnalysisDefectRegressionTest.noValueAnnotationEverCarriesADefaultFallback` closes the `@Value`-specific gap | T04+ / T38 |
| AC5 | `allow-circular-references` | **New**: `GapAnalysisDefectRegressionTest.allowCircularReferencesIsNeverEnabled` | T38 |

T38's own contribution is now both the verification record (Phases 0/1/4/6/9) and three permanent
regression tests (Phase 11) for the defect classes that previously had no durable guard.

## Verification performed (final, post-Phase-9)

- AC1: credential-field storage confirmed hashed/encrypted; `application.properties` + Flyway plugin
  config confirmed local-only placeholders only; comment/source scan (0 matches) + independent
  re-run (2 known-benign false positives, no real match); `src/test/resources` confirmed absent;
  `.github/workflows/ci.yml` scanned for completeness (0 matches beyond proper secrets references).
- AC2: `PublicEndpoints.java` confirmed no `/admin/**` entry; both ArchUnit rules confirmed present
  and covering the current controller set; naming-convention residual explicitly preserved, not
  fixed (out of scope).
- AC3: full dependency inventory (Phase 0) + targeted grep (Phase 6/8) both confirm no cross-service
  entity-sharing dependency in either `pom.xml`.
- AC4: zero matches for `Long.getLong`/`Integer.getInteger`/`System.getProperty`/`System.getenv`;
  two `@Value` usages confirmed to have no default fallback (fail-boot behavior preserved).
- AC5: zero matches for `allow-circular-references`/`allowCircularReferences`/
  `setAllowCircularReferences` across `src/`, `pom.xml`.

All five defect classes confirmed absent, independently re-verified at Phase 8.

## Kimi Phase 11 test review — gaps closed

All 7 findings verified against source before disposition.

| Gap | Disposition |
|---|---|
| Gap 1 — no automated guard against AC3/AC5 reintroduction | **Accepted, human-approved scope expansion.** Added `GapAnalysisDefectRegressionTest` with `noSharedModelArtifactDependencyIsIntroduced` and `allowCircularReferencesIsNeverEnabled`. Unlike prior sessions' rejected "future-proofing" suggestions (T36/T37), this guards against specific, already-happened-once-in-history reference-project defects, not a hypothetical scenario. |
| Gap 2 — no test asserts `PublicEndpoints` excludes `/admin/**` | **Rejected as redundant with a stronger existing mechanism.** `ArchitectureTest.shouldEnforcePublicEndpointAllowlist` already CI-enforces no `.permitAll()` exists outside the allowlist at all — a broader, already-existing guard that subsumes "the allowlist itself contains no `/admin` path" (if `/admin` were ever added to the allowlist, that alone doesn't violate anything; the actual risk is an admin *handler* being `permitAll()`'d, which the existing rule already catches). Kimi's suggested test would duplicate coverage without adding a distinct failure mode. |
| Gap 3 — gitleaks CI gate status not explicitly confirmed | **Investigated and resolved as a real, documented finding.** `.github/workflows/ci.yml` read in full: it is an explicit placeholder ("Path-filtered CI — placeholder until service skeletons land"), running only `mvn -B -pl services/auth verify` for the `auth` job — no gitleaks/secret-scanning step exists anywhere in it yet. Logged as a genuine, out-of-scope-for-T38 (repo-wide CI infra, not `services/auth` code) follow-up, not left as an unresolved open question. |
| Gap 4 — evidence spread across multiple artifacts | Accepted — will be consolidated into the Phase 12 verification matrix and Phase 13 PR summary. |
| Gap 5 — exact grep commands not recorded | Accepted — recorded in Phase 6/9's own artifacts already; will carry through to Phase 12. |
| Gap 6 — Docker build not re-verified in Phase 10 | **Rejected — repeated factual error**, the third instance of the same mistake (Phase 3 Finding 8, Phase 8 Finding 4, now this). T38's task statement has no Docker-build clause; that belongs to T37. No action. |
| Gap 7 — no test verifies `@Value` fail-on-missing behavior | **Accepted.** Added `GapAnalysisDefectRegressionTest.noValueAnnotationEverCarriesADefaultFallback` — scans all production Java source for `@Value("${prop:default}")` shape, not just the two currently-known usages, so it also catches a *future* `@Value` gaining a default. |

**New file**: `services/auth/src/test/java/com/themistra/auth/GapAnalysisDefectRegressionTest.java`
(3 tests). Plain JUnit, not ArchUnit — each check is text/content scanning ("this string must never
appear"), not structural bytecode analysis, and plain `@Test` methods reliably execute under this
project's Surefire setup, unlike `@ArchTest` rules without their own canary (the still-open issue
from T32).

**Negative-proof, all three, each introduced then reverted, confirmed via `git diff` empty
afterward:**
1. `noSharedModelArtifactDependencyIsIntroduced` — appended `<!-- commons-netra -->` to
   `services/auth/pom.xml` → correctly failed; reverted, re-confirmed passing.
2. `allowCircularReferencesIsNeverEnabled` — appended `spring.main.allow-circular-references=true`
   to `application.properties` → correctly failed; reverted, re-confirmed passing.
3. `noValueAnnotationEverCarriesADefaultFallback` — changed `KafkaProducerConfig`'s `@Value` to
   `"${spring.kafka.bootstrap-servers:localhost:9092}"` → correctly failed, correctly identified the
   exact offending file; reverted, re-confirmed passing.

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest=GapAnalysisDefectRegressionTest` — all 3 pass.
- Three negative-proof runs above, each confirmed to fail for the right reason and reverted; `git
  diff` confirmed empty on all three touched files afterward.

---

**Phase 10 complete — test manifest written and updated post-Phase-11.** Proceed to Phase 12
(Specification Verification) on approval.
