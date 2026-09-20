<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T38 · Phase 6 — Implementation Notes

Zero-code-change task. This phase is the formal, re-verified (not stale-cited) execution of the
Phase 5 plan's five checks — every command re-run fresh at this phase, not carried forward
unverified from Phase 0.

## What was verified

### AC1 — Plaintext credentials absent

- `Account.passwordHash` (BCrypt), `RefreshTokenFamily.currentTokenHash` (SHA-256),
  `ApiKey.keyHash` (SHA-256), `MfaSeedEncryption` (AES-256-GCM envelope, L14) — all confirmed
  present and credential-shaped fields stored hashed/encrypted, re-checked directly.
- `application.properties` — 27 `${ENV_VAR:...}`-style externalized values, no bare literal secret.
- `pom.xml:198` — Flyway Maven plugin's `<password>checky-local-only</password>`, confirmed a
  local-only placeholder (plugin only runs via manual `flyway:migrate`, never `package`/`verify`/CI
  per its own adjacent comment), not a production credential.
- Comment/source scan for embedded credential-shaped literals across all Java source: **0 matches**.
- **Met.**

### AC2 — Unauthenticated admin routes absent

- `PublicEndpoints.java`: zero `/admin` matches anywhere in the file.
- `ArchitectureTest.shouldEnforcePublicEndpointAllowlist` (line 283) and
  `admin_controller_handlers_require_preauthorize` (line 270) both present and confirmed still
  covering the current controller set.
- **Met**, with the Phase 4-accepted residual (name-based, not path-based, enforcement) still noted,
  not fixed (out of this task's read-only scope).

### AC3 — Shared model artifact absent

- Zero matches for `commons-netra`/`shared-domain`/`shared-model` in either `pom.xml`.
- **Met for production/runtime code**, per the Phase 4-clarified scope boundary excluding T37's
  intra-service test-only cross-module imports.

### AC4 — `Long.getLong` config misread absent

- Zero matches for `Long.getLong`, `Integer.getInteger`, `System.getProperty`, `System.getenv`
  across `src/main/java/com/themistra/auth`.
- **Met.**

### AC5 — `allow-circular-references=true` absent

- Zero matches across `src/`, `pom.xml` for `allow-circular-references`/`allowCircularReferences`/
  `setAllowCircularReferences`.
- **Met.**

## Deviations forced by reality

None. Every check re-ran identically to the Phase 0/4 findings — no code changed between those
phases and this one, so no discrepancy was possible or found.

## Verification performed

All five checks above, executed fresh at this phase (not stale-cited from Phase 0), with exact
grep commands and file:line evidence recorded per AC.

---

**Phase 6 complete — implementation written (zero code change, verification-only).** Proceed to
Phase 7 (Self Review) on approval.
