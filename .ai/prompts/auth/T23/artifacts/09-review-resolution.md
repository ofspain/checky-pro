> **STATUS: RESOLVED.** Human sign-off given 2026-08-10 ("go ahead") on the resolution set below, presented alongside the Phase 8 findings. `mvn -pl services/auth -am compile` and `mvn -pl services/auth test -Dtest=ArchitectureTest` both pass after every change.

# auth · T23 · Phase 9 — Review Resolution

4 findings from Phase 8 (`08-independent-review.md`), 2 of which independently re-derived Phase 7 self-review findings.

---

**1. `getScopes()` exposes the entity's live, mutable `scopes` list.**
**Disposition: ACCEPTED.**
**Change:** `getScopes()` now returns `List.copyOf(scopes)` instead of the internal list reference, matching `MfaEnrollment.getSecretEncrypted()`'s established defensive-copy convention. A caller mutating the returned list can no longer affect the entity's persisted state. (`ApiKey.java`)

**2. The `scopes` Postgres-array mapping compiles but is unverified against a live database.**
**Disposition: ACCEPTED IN SUBSTANCE, no code change.** This is exactly what `ApiKeyPersistenceIntegrationTest` (Phase 10) exists to prove — deferring to it rather than guessing at a fix for a problem that may not exist. If Phase 10's Spring context fails to start with a schema-validation error, `columnDefinition = "text[]"` is the fix at that point, not now.

**3. No runtime guard against null or blank elements in `scopes`.**
**Disposition: ACCEPTED, narrowed.** `create(...)` now rejects `null` elements within a non-null `scopes` argument (`Objects.requireNonNull` per element, before copying) — the `scopes` column is `NOT NULL`, so a null element is a structural violation regardless of business meaning. Blank/empty-string elements are deliberately NOT rejected: whether `""` is a meaningful scope value is a business-semantics decision that belongs to whichever task defines the actual scope vocabulary (T24), not to this entity-mapping task. The class Javadoc on `create(...)` now states this distinction explicitly. (`ApiKey.java`)

**4. `findByPrefix` is a raw lookup with no lifecycle filtering.**
**Disposition: ACCEPTED — documentation only.** Added a Javadoc note on `ApiKeyRepository.findByPrefix` stating explicitly that it applies no `revokedAt`/`expiresAt` filtering and that callers must check those fields themselves. No logic change — the method's contract was already correct per the frozen brief; only the documentation was incomplete. (`ApiKeyRepository.java`)

No findings rejected.

---

## Files changed this phase
- `apikey/ApiKey.java` — `getScopes()` returns a defensive `List.copyOf(...)`; `create(...)` rejects null elements within a non-null `scopes` argument; Javadoc updated to state both the null-element rejection and the deliberate non-validation of blank/empty-string elements.
- `apikey/ApiKeyRepository.java` — `findByPrefix`'s Javadoc gained a note that it performs no lifecycle filtering.

No production code outside `apikey/` touched. No test file touched (none exists yet — Phase 10). No public API signature changed, no class renamed, no refactoring beyond the four accepted fixes.
