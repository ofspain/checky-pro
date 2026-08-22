<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T38 · Phase 10 — Test Generation

No new tests — zero-code-change, read-only verification task. Every acceptance criterion is already
enforced or evidenced by pre-existing mechanisms, not new coverage this task adds.

## AC → existing enforcement mechanism

| AC | Defect class | Enforcement/evidence mechanism | Owning task |
|---|---|---|---|
| AC1 | Plaintext credentials | Hash/encrypt-at-rest on every credential field (`Account`, `RefreshTokenFamily`, `ApiKey`, `MfaSeedEncryption`); `@ConfigurationProperties` local-only defaults refused in non-local profiles | T04-T25 (original implementation) |
| AC2 | Unauthenticated admin routes | `ArchitectureTest.shouldEnforcePublicEndpointAllowlist`, `admin_controller_handlers_require_preauthorize` | T32, earlier admin work |
| AC3 | Shared model artifact | Absence by construction — no dependency ever added | N/A (never introduced) |
| AC4 | `Long.getLong` misread | Uniform `@ConfigurationProperties`/`@Validated` convention across every config surface | T04+ (established from the start) |
| AC5 | `allow-circular-references` | Absence by construction — property never set | N/A (never introduced) |

T38's own contribution is the verification record itself (Phases 0/1/4/6/9), not new test authorship.

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

All five defect classes confirmed absent, independently re-verified at Phase 8, no code change made
or needed.

---

**Phase 10 complete — test manifest written (no new tests, verification-only task).** Proceed to
Phase 11 (Kimi Test Review) on approval.
