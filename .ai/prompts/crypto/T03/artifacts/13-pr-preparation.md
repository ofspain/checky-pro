# crypto · T03 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to prepare T03 for merge, per that gate.

**Note on this repo's actual git history:** this session's phase-boundary work has already been
captured across several small commits on the current branch (`spec/service-specs-and-ai-framework`,
off `main`) — `8a2cf7b`, `c978d0d`, `05c21c9`, `dd28732`, `78c61a5` and others cover T03's Phase
0–11 artifacts and implementation. This mirrors the same working pattern already established for T01
and T02 on this branch — small, frequent commits rather than one squashed task commit — so the
material below is prepared as the **logical PR description for the whole of T03** (what the phase
template asks for), not as a claim that a single new commit will contain all of it. The only files
still uncommitted as of this phase are the Phase 11 test-review follow-up (widened `ScreeningProperties`
guard + 8 test files) and the Phase 12 verification artifact — listed separately below. **No commit
or push has been made** — repo-wide instructions require an explicit go-ahead before committing, so
this artifact stops at preparation.

## Commit title

```
crypto-service: config & resource server (T03)
```

## Commit message

```
crypto-service: config & resource server (T03)

Add validated @ConfigurationProperties for providers, finality, screening,
KMS, and S3 snapshot config, and wire OAuth2 resource-server JWT validation
requiring the internal.crypto:write scope on /internal/v1/** (R27).
PublicEndpoints narrows the public allowlist to actuator health/info/
prometheus plus the (not-yet-built) verification-keys well-known path.

- Five @ConfigurationProperties records under common/config, each @Validated
  with Jakarta Bean Validation; three add compact-constructor cross-field
  checks (quorum-threshold vs. provider count; screening's enabled flag vs.
  base-url/api-key-secret-name in both directions) so a forgotten flag or an
  impossible quorum target fails startup instead of degrading silently.
- FinalityProperties deliberately holds only an enabled-chains list — no
  confirmation-count field — per L4 (finality is a per-chain policy object,
  not configurable). KmsProperties exposes exactly one key-identifying field
  per L11.
- ResourceServerConfig validates against auth-service's JWKS + issuer (no
  local key material, unlike auth's own issuer role) and returns RFC 9457
  problem+json on 401/403, matching agents.md's error-format rule.
- Chain identifiers are constrained to the launch scope (ETHEREUM/TRON,
  design.md §2) via @Pattern, closing a typo-tolerant gap the design's own
  vendor-open fields (providers, screening) correctly don't have.
- 67 tests: fail-fast/bind-success coverage per properties class, the named
  test shouldRequireInternalScopeForWatchAndAttestEndpoints (exercised
  against a test-only controller mirroring the real internal API, since
  WatchController/AttestController don't exist until T15/T21), a
  PublicEndpoints exhaustiveness sweep, and a direct unit test of Spring's
  scope-claim-to-authority conversion against the token-claims.md Path 2
  shape.

Went through the full 14-phase spec-driven pipeline: Phase 3/8/11 adversarial
review (Kimi) surfaced 12 accepted findings across design, implementation,
and test coverage (audience-validation scoping, actuator exposure config,
JWT issuer validation, chain-value constraints, quorum/provider-count
cross-check, RFC 9457 on the security layer, WWW-Authenticate header, and
several test-coverage gaps); Phase 4 and 9 human-approval gates recorded
acceptance/rejection with reasons for each. Phase 12 traceability matrix:
PASS.

Task: spec/crypto-service/tasks.md #3
Requirements: R27
Locked decisions: L4, L11, L12, L13, L15

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed (complete T03 file set)

**Main:**
- `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` (modified — `@ConfigurationPropertiesScan`)
- `services/crypto/src/main/java/com/themistra/crypto/common/PublicEndpoints.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/common/config/FinalityProperties.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ScreeningProperties.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/common/config/KmsProperties.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/common/config/SnapshotProperties.java` (new)
- `services/crypto/src/main/resources/application.properties` (modified — JWKS/issuer/actuator/`themistra.crypto.*` config)

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/common/InternalTestController.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/common/ResourceServerConfigIntegrationTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/common/PublicEndpointsTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/common/ScopeClaimConversionTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/common/ApplicationPropertiesSecurityConfigTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/common/config/ProviderPropertiesTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/common/config/FinalityPropertiesTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/common/config/ScreeningPropertiesTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/common/config/KmsPropertiesTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/common/config/SnapshotPropertiesTest.java` (new)

**Pipeline artifacts:** `.ai/prompts/crypto/T03/artifacts/00-*.md` through `12-*.md` (13 files).

**Not part of T03** — pre-existing/unrelated, untouched by this task: `services/crypto/pom.xml`,
`services/crypto/README.md`, `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`,
`ChainBaselineMigrationIntegrationTest.java`, `T01SkeletonRegressionTest.java` (all T01/T02). The
stray root-level `prompt` file (an unrelated auth-service audit prompt from earlier in this session)
is untracked and explicitly excluded from this task's changes.

**Still uncommitted as of this phase** (the Phase 11 follow-up + Phase 12 artifact):
`ScreeningProperties.java`, `ResourceServerConfigIntegrationTest.java`,
`config/FinalityPropertiesTest.java`, `config/ProviderPropertiesTest.java`,
`config/ScreeningPropertiesTest.java` (all modified), `ApplicationPropertiesSecurityConfigTest.java`,
`ScopeClaimConversionTest.java` (new), `.ai/prompts/crypto/T03/artifacts/10-test-generation.md`
(modified — addendum), `.ai/prompts/crypto/T03/artifacts/12-specification-verification.md` (new).

## Summary

T03 adds crypto-service's configuration and security foundation: five validated
`@ConfigurationProperties` classes (providers, finality, screening, KMS, S3 snapshot) and a single
`SecurityFilterChain` enforcing `internal.crypto:write` on `/internal/v1/**` while narrowing the
public surface to actuator health/info/prometheus and the future verification-keys well-known path.
It is the second foundation task after T02's schema baseline and unblocks every subsequent task that
needs config or an internal endpoint (T04 outbox onward).

## Testing performed

- `mvn -pl services/crypto -am compile` / `test-compile` — clean throughout.
- `mvn -pl services/crypto test -Dtest=...` — **67/67 tests passing** across all 10 new test
  classes plus T01's pre-existing `T01SkeletonRegressionTest` (6). T02's
  `ChainBaselineMigrationIntegrationTest` (Testcontainers) was not run in this environment — Docker
  is unavailable here and it's unrelated to T03; nothing in this task touches schema/migration code.
- Two mutation-based negative-proofs performed and reverted cleanly (confirmed via `diff` against
  pre-mutation backups): mutating the required scope constant broke exactly the 4 tests asserting
  correct-scope acceptance; removing the widened screening reverse-guard broke exactly
  `failsWhenApiKeySecretNameSetButNotEnabled` — with the wrong exception type, concretely
  demonstrating why several tests assert specific exception types rather than bare `hasFailed()`.
- No full-context `@SpringBootTest` boot smoke test — documented limitation (Docker unavailable);
  `ApplicationPropertiesSecurityConfigTest` closes the most likely regression (a deleted/typo'd
  property key) without needing one.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 3 — "Config & resource server."
- **Requirements:** R27.
- **Locked decisions:** L4, L11, L12, L13, L15 (derived in Phase 1 from `design.md` §4a — none were
  cited inline in the task header).
- **Named test:** `shouldRequireInternalScopeForWatchAndAttestEndpoints` (`package.md` §8).
- **Standing rules:** `spec/crypto-service/agents.md` (Configuration, Security, Error-handling
  sections) — followed throughout; never modified.

---

**This artifact is preparation only.** No `git commit`, `git push`, or PR was created. If you'd like
me to commit the pending Phase 11/12 delta now (the 9 files listed above), say so and I will —
repo-wide instructions require that explicit go-ahead first.
