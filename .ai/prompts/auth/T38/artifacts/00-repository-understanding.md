<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T38 · Phase 0 — Repository Understanding

Task statement: verify plaintext credentials, unauthenticated admin routes, shared model artifact,
`Long.getLong` config misread, and `allow-circular-references` classes of error are absent.

---

## 1. Architecture summary

These five defect classes trace directly to `services/auth/docs/architecture/gap-analysis.md`
("Phase 3 — Gap Analysis: Reference (`authrex`) vs Target Architecture", dated 2026-07-13) — the
document comparing this service against a prior reference implementation whose known defects this
codebase was explicitly designed to not repeat. Each of the five maps to a named row in that
document's §2 ("Violations Register") or §3 ("Defect → Test-Case Mapping"):

| Task-statement defect class | Gap-analysis source | Reference-project problem being guarded against |
|---|---|---|
| Plaintext credentials | §2: "Plaintext refresh tokens", "Committed DB password + RDS credential in comment" | Reference stored refresh tokens as plaintext UUIDs and had a committed credential in a code comment |
| Unauthenticated admin routes | §2: "Unauthenticated `/api/roles/**` whitelist ('testing only')" | Reference shipped a public role-administration allowlist meant only for testing |
| Shared model artifact | §2: "Shared domain-model artifact" (matrix item 19: entities imported from `com.netra:commons-netra`) | Cross-service entity sharing creates lock-step deployments and schema coupling |
| `Long.getLong` config misread | §3: "Config-binding test: `@ConfigurationProperties` records + startup validation" | Reference read config via raw `Long.getLong`-style parsing, which silently defaults on a misread instead of failing boot |
| `allow-circular-references=true` | §2: "Hides dependency cycles that later block modularization" | A Spring Boot escape hatch that papers over circular bean dependencies rather than surfacing them |

## 2. Existing code this task touches

This is a pure verification task — no new code is expected unless a violation is actually found. A
direct source-level check was performed as part of this phase's own grounding (not deferred to a
later phase, since "verify X is absent" for this task IS the repository-understanding work):

1. **`allow-circular-references`** — `grep -rn "allow-circular-references\|allowCircularReferences\|setAllowCircularReferences" src/ pom.xml` → **zero matches**. Absent.
2. **`Long.getLong`/raw config reads** — `grep -rn "Long\.getLong\|Integer\.getInteger\|System\.getProperty\|System\.getenv" src/main/java/com/themistra/auth` → **zero matches**. Every config value in this service goes through validated `@ConfigurationProperties` records (confirmed pattern, e.g. `ApiKeyProperties`, `MfaProperties`, `RateLimitProperties`, `VerificationTokenProperties`, `AuthClientsProperties`), which fail boot on a missing/invalid value in non-local profiles rather than silently defaulting. Absent.
3. **Unauthenticated admin routes** — `PublicEndpoints.java` (`common` package) is the exhaustive allowlist; contains no `/admin/**` entry anywhere, and its own docstring names this exact historical defect explicitly: "the reference project shipped a 'testing only' whitelist exposing role administration; this constant is that lesson, enforced." `ArchitectureTest.shouldEnforcePublicEndpointAllowlist` (added T32) CI-enforces no `.permitAll()` call exists outside this list. Absent.
4. **Shared model artifact** — full `pom.xml` dependency inventory (both `services/auth/pom.xml` and the root parent) checked: every dependency is a standard Spring Boot starter, an infra client (Postgres, Kafka, Flyway, ShedLock, Bucket4j, AWS KMS), or a test tool. No `commons-netra`-style shared entity/domain-model artifact. Cross-service sharing in this monorepo goes only through `contracts/` (OpenAPI/JSON Schema, build-time codegen, no runtime coupling) — the pattern the gap-analysis itself prescribes as the replacement. Absent.
5. **Plaintext credentials** — checked all four credential-shaped values in this service:
   - Passwords: `Account.passwordHash` (BCrypt, strength 12, `SecurityBeansConfig`).
   - Refresh tokens: `RefreshTokenFamily.currentTokenHash` (SHA-256, never the raw token).
   - API keys: `ApiKey.keyHash` (SHA-256, via `ApiKeyHasher`).
   - TOTP seeds: AES-256-GCM envelope-encrypted (`MfaSeedEncryption`, KMS-backed, L14).
   - `application.properties` itself: opens with "No secrets in this file, ever (D-010)"; every sensitive value is `${ENV_VAR:local-only-placeholder}` (e.g. `${DB_PASSWORD:checky-local-only}`) — environment-injected in real deployments, an explicitly-named local-dev-only fallback otherwise, not a committed production credential.
   All absent.

**Provisional finding**: all five defect classes are absent, verified directly against source, not
assumed. Phase 1 will formally extract this into the spec-package structure; later phases (Design
Challenge, Independent Review) exist to adversarially re-check this conclusion, not to re-derive it
from scratch.

## 3. Established patterns to follow

- **Validated `@ConfigurationProperties`** is this codebase's uniform answer to defect class #4 —
  every config surface in the service already follows it; no new pattern needed.
- **The public-endpoint allowlist + ArchUnit enforcement** (T32) is the uniform answer to defect
  class #2.
- **`contracts/`-only cross-service sharing** is the uniform answer to defect class #3.
- **Hash/encrypt-at-rest for every credential-shaped value** is the uniform answer to defect class #1.

## 4. Testing conventions

Per `agents.md`: unit → ArchUnit/contract → integration → image build → gitleaks/dependency scan.
Defect class #1's "committed secrets" half is additionally covered by a repo-wide gitleaks CI gate
(gap-analysis §2, §3: "gitleaks gate in CI (repo-wide, not just auth)") — outside `services/auth`'s
own test suite, not re-verified by this task (out of this module's scope to re-audit CI config).

## 5. Known gaps / unknowns

- **I do not know whether the repo-wide gitleaks CI gate itself is actually configured and running**
  — the gap-analysis document asserts it should exist "repo-wide, not just auth," but confirming CI
  configuration is outside `services/auth`'s own source tree, and this task's own scope (per its
  header) is this service's spec package. Flagged rather than assumed either way.
- No other gap identified. Every one of the five named defect classes was checked directly against
  source in this phase and found absent, with a specific file/mechanism as evidence for each.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
