<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T38 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

## Decision packet

All 8 Phase 3 (Kimi) findings verified against source before disposition.

| # | Finding | Disposition |
|---|---|---|
| 1 | AC1 evidence lacks citations; local-only placeholders need explicit boundary | Accepted. Verified the additional `pom.xml:196-199` Flyway-plugin `checky-local-only` password citation is accurate (a Maven-plugin-only config, per its own adjacent comment "never fires during package/verify/CI or the production Docker build"). Folded the explicit evidence list + placeholder-vs-production-secret boundary into AC1 below. |
| 2 | Admin-route ArchUnit rule is name-based (`Admin*`), not path-based | Accepted. Verified `admin_controller_handlers_require_preauthorize` at `ArchitectureTest.java:270-272` uses `.haveSimpleNameStartingWith("Admin")` — a real, if minor, limitation (a future admin controller not following the naming convention would need its own enforcement). Noted as an accepted residual, not fixed (out of this task's read-only scope). |
| 3 | AC3 should scope to production code, not T37's test-only cross-module imports | Accepted. Clarified AC3 applies to production/runtime artifacts; `AuditTrailIntegrationTest`/`RoleAssignmentIntegrationTest`'s `AccountService` imports (T37) are intra-service test helpers, excluded from `ArchitectureTest` analysis, and out of AC3's scope. |
| 4 | AC4 evidence should explicitly name all four grep patterns checked | Accepted. Restated evidence explicitly in the brief below (Phase 0 already checked all four; Phase 2's TIB just under-cited it). |
| 5 | AC5 should explicitly confirm no programmatic circular-reference setting | Accepted. The original grep already covered `src/` (inclusive of `src/main/java`) — restated explicitly for clarity, no new check needed. |
| 6 | Gitleaks CI gate confirmation should live inside AC1, not as a separate open question | Accepted. Folded into AC1's own text below. |
| 7 | No explicit comment-scan for embedded secrets | Accepted, and performed: `grep -rniE` for password/secret/API-key-shaped literal assignments across all Java source — zero matches beyond the already-known, already-named local-only placeholders. Folded into AC1 evidence. |
| 8 | Docker image build should be checked for copied secrets | **Rejected — factual error.** T38's own verbatim task statement (quoted at the top of Kimi's own artifact) has no Docker-build clause at all; this finding misattributes T37's task statement ("Docker image must build from repo root") to T38. No action. |

## Frozen brief (Phase 2 TIB, as amended)

### Task

Verify five reference-project defect classes (plaintext credentials, unauthenticated admin routes,
shared model artifact, `Long.getLong` config misread, `allow-circular-references`) are absent from
`services/auth`.

### Purpose / Scope / Business Rules / Locked Decisions / Dependencies / Inputs / Outputs / State
Changes / Files to Create/Modify/NOT to Modify

Unchanged from Phase 2 — this task remains read-only verification, zero code changes.

### Acceptance Criteria (amended with explicit evidence per Kimi Findings 1/2/3/4/5/6/7)

- **AC1 — Plaintext credentials absent. Met.** Evidence: (a) every credential-shaped domain field is
  hashed/encrypted (`Account.passwordHash` BCrypt, `RefreshTokenFamily.currentTokenHash` SHA-256,
  `ApiKey.keyHash` SHA-256, TOTP seeds AES-256-GCM envelope); (b) `application.properties` and the
  Flyway Maven plugin's local-only config (`pom.xml:196-199`) contain only `${ENV_VAR:local-only-placeholder}`/
  literal `-local-only`-suffixed fallbacks, accepted local-dev defaults per D-010, not committed
  production credentials; (c) a targeted comment/source scan (`grep -rniE` for
  password/secret/API-key-shaped literal assignments across all Java source) found zero embedded
  credentials. **Open, non-blocking**: whether the repo-wide gitleaks CI gate (the defense against a
  *future* committed secret) is actually configured is outside this task's scope to confirm.
- **AC2 — Unauthenticated admin routes absent. Met.** No `/admin/**` path in `PublicEndpoints.java`;
  `ArchitectureTest.shouldEnforcePublicEndpointAllowlist` prevents `.permitAll()` outside the
  allowlist; `admin_controller_handlers_require_preauthorize` covers all five existing `Admin*`
  controllers. **Accepted residual**: that rule is name-based (`haveSimpleNameStartingWith("Admin")`),
  not path-based — a future admin controller not following the naming convention would need its own
  enforcement. Not fixed here (read-only task scope).
- **AC3 — Shared model artifact absent. Met for production/runtime code.** No cross-service
  entity-sharing dependency in `services/auth/pom.xml` or the root parent. Scope note: T37's
  `AccountService` imports into `audit`/`authz` test files are intra-service test helpers, excluded
  from `ArchitectureTest` analysis, and outside AC3's scope (which governs cross-*service* sharing,
  not intra-service test-code imports).
- **AC4 — `Long.getLong` config misread absent. Met.** Zero matches for `Long.getLong`,
  `Integer.getInteger`, `System.getProperty`, and `System.getenv` across
  `src/main/java/com/themistra/auth`; all config surfaces use `@ConfigurationProperties`/`@Validated`
  records that fail boot on a missing/invalid value in non-local profiles.
- **AC5 — `allow-circular-references=true` absent. Met.** Verified by grep across `pom.xml`,
  `application.properties`, and the full `src/` tree (inclusive of `src/main/java`) for
  `allow-circular-references`, `allowCircularReferences`, `setAllowCircularReferences` — zero matches.

### Required Tests / Constraints

Unchanged — none new, read-only task.

### Open Questions

No blockers. AC1's gitleaks-CI-gate note is explicitly non-blocking, carried for awareness.

---

**Phase 4 complete — task brief frozen and approved.** Proceed to Phase 5 (Implementation Plan).
