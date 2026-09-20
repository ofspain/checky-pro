# auth · T23 · Phase 7 — Self Review

Self-review of the Phase 6 diff (`ApiKey.java`, `ApiKeyRepository.java`) against the frozen brief and `agents.md`. Findings only.

---

## 1. `getScopes()` returns the entity's live, mutable list — not a defensive copy

- **Issue.** `ApiKey.create(...)` defensively copies its `scopes` argument (`new ArrayList<>(scopes)`, `ApiKey.java:99`), but `getScopes()` (`ApiKey.java:128-130`) returns that same internal `List<String>` instance directly, with no `List.copyOf`/`Collections.unmodifiableList` wrapper. Any caller holding the returned reference can mutate it (`apiKey.getScopes().add("x")`) and, because it's the same object backing the entity's field, silently change what Hibernate's dirty-checking sees at the next flush — an unintended persisted `UPDATE` with no method call that looks like a mutation. This is exactly the class of bug `MfaEnrollment.getSecretEncrypted()` already guards against for its own mutable field (`byte[] secretEncrypted`), by returning `secretEncrypted.clone()` rather than the live array.
- **Severity.** Medium-High — this isn't just an encapsulation smell; it contradicts the class's own Javadoc claim ("Deliberately has no mutators for `lastUsedAt`, `revokedAt`, or `name`") by leaving an unguarded back door to mutate `scopes`, the one collection-typed field, through a getter that looks read-only.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java:99` (write-side defensive copy) vs. `:128-130` (read-side, no copy); compare `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollment.java:109-111` (`getSecretEncrypted()` returns `secretEncrypted.clone()`), which is this codebase's own established convention for exactly this situation.
- **Recommendation.** Change `getScopes()` to return `List.copyOf(scopes)` (or `Collections.unmodifiableList(scopes)`), matching the write-side's defensive-copy discipline and `MfaEnrollment`'s precedent.

## 2. The `scopes` array mapping's schema compatibility is unverified against a live database

- **Issue.** `@JdbcTypeCode(SqlTypes.ARRAY)` on `List<String> scopes` compiles cleanly and is the standard Hibernate 6 approach for a Postgres `TEXT[]` column, but compilation only proves Java-level type-checking — it says nothing about whether `spring.jpa.hibernate.ddl-auto=validate` (this service's configured mode) will accept the mapping against the real `text[]` column at application-context startup. No other entity in this schema maps an array column, so there's no working precedent to confirm this specific combination (no explicit `columnDefinition`, relying on Hibernate's default array-type inference) passes validation rather than failing it.
- **Severity.** Medium — if validation fails, the failure mode is a Spring context startup error, not a subtle data bug, so it will be loud and immediate rather than silent. But it is currently an open, unverified risk, not a confirmed-working mapping.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java:59-61`; `services/auth/src/main/resources/application.properties` (`ddl-auto=validate`, confirmed in Phase 3 finding #2's evidence); Phase 6 implementation notes already flagged this same gap under "Mapping to acceptance criteria" (AC5) rather than overclaiming it as proven.
- **Recommendation.** No code change proposed here — this is exactly what `ApiKeyPersistenceIntegrationTest` (Phase 10) exists to prove. If that test's Spring context fails to start with a schema-validation error against `scopes`, the fix is adding `columnDefinition = "text[]"` to the `@Column` annotation; if the context starts and the round-trip test passes, this finding is closed with no code change.

## 3. No other correctness, boundary, null-safety, thread-safety, transaction, module-boundary, idempotency, money-type, or secret-handling issues found

- `Objects.requireNonNull` guards every required factory argument (`accountId`, `prefix`, `keyHash`, `name`, `createdAt`); `scopes` correctly defaults rather than being rejected, per the frozen brief.
- `accountId` is a plain column, never a JPA relation to `Account` (L12) — confirmed both by inspection and by `ArchitectureTest` passing in Phase 6's verification run.
- `ApiKeyRepository` is package-private; confirmed by the same `ArchitectureTest` run (`repositories_are_never_public`).
- No money types involved. No idempotency concern — no `@Modifying`/write-behavior in this task. No thread-safety concern — a plain JPA entity, not shared across threads.
- Only a SHA-256 hash is stored (`keyHash`); no plaintext key material is ever held by this entity, consistent with L7.
- `id`, `keyUuid`, `accountId`, `prefix`, `keyHash`, `createdAt` are correctly `updatable = false`; `name`, `scopes`, `lastUsedAt`, `expiresAt`, `revokedAt` are correctly left mutable for later tasks, matching the frozen brief exactly.

No `agents.md` or LOCKED-decision violations found beyond the L7-vs-column-width conflict already identified and explicitly deferred to T24 in the frozen brief (not a new finding).
