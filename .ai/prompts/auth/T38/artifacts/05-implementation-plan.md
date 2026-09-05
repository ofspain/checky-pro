<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T38 · Phase 5 — Implementation Plan

Zero-code-change task. No files to create or modify — the frozen brief's AC1-AC5 are all already
Met, each with direct source evidence gathered at Phases 0/1/4. This phase's plan is the verification
sequence itself, formalized for Phase 6 to execute and document as the deliverable.

## Files to create

None.

## Files to modify

None.

## Public / private methods

None — no code.

## Entities / Repositories / Services used

None — read-only source inspection, no runtime component exercised.

## Tests required

None new.

## Execution order (the verification sequence Phase 6 documents as its deliverable)

1. **AC1 (plaintext credentials)** — confirm each credential-shaped domain field's storage mechanism
   (`Account.passwordHash`, `RefreshTokenFamily.currentTokenHash`, `ApiKey.keyHash`,
   `MfaSeedEncryption`); confirm `application.properties` + the Flyway Maven plugin config contain
   only local-only placeholders; re-run the Phase 4 comment/source scan for embedded credential-shaped
   literals.
2. **AC2 (unauthenticated admin routes)** — confirm `PublicEndpoints.java` has no `/admin/**` entry;
   confirm `ArchitectureTest.shouldEnforcePublicEndpointAllowlist` and
   `admin_controller_handlers_require_preauthorize` both exist and cover the current controller set.
3. **AC3 (shared model artifact)** — confirm `services/auth/pom.xml` and the root parent `pom.xml`
   carry no cross-service entity-sharing dependency.
4. **AC4 (`Long.getLong` misread)** — re-run the full-tree grep for `Long.getLong`,
   `Integer.getInteger`, `System.getProperty`, `System.getenv`.
5. **AC5 (`allow-circular-references`)** — re-run the full-tree grep for
   `allow-circular-references`/`allowCircularReferences`/`setAllowCircularReferences`.
6. Assemble the five results into the Phase 6 implementation-notes artifact, citing exact
   file:line evidence for each, matching the frozen brief's amended AC text.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
