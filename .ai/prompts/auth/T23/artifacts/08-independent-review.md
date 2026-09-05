# auth · T23 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T23 — ApiKey entity/repository |
| **Consumes** | `artifacts/07-self-review.md` + Phase 6 implementation |
| **Produces** | `artifacts/08-independent-review.md` |

Independent adversarial review of the T23 implementation. Findings only.

**Review limitation:** `mvn` is not available in this environment, so I could not re-run compilation or `ArchitectureTest`. Findings below are based on static analysis of `apikey/ApiKey.java` and `apikey/ApiKeyRepository.java`.

---

## 1. `getScopes()` exposes the entity's live, mutable `scopes` list

- **Issue.** `ApiKey.create(...)` defensively copies the incoming `scopes` argument into a new `ArrayList`, but `getScopes()` returns that same internal list directly. A caller can mutate the returned list, and because it is the object Hibernate dirty-checks, the change can be persisted implicitly on the next flush without any entity method call that looks like a state change. This contradicts the class's own Javadoc claim that it "deliberately has no mutators" and breaks the defensive-copy discipline the factory establishes.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java:99` (write-side copy) vs. `:128-130` (read-side live reference). `MfaEnrollment.getSecretEncrypted()` (`services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollment.java:109-111`) is the codebase's established precedent for guarding a mutable getter.
- **Recommendation.** Change `getScopes()` to return `List.copyOf(scopes)`. This creates an unmodifiable defensive copy, matching the factory's write-side discipline. Avoid `Collections.unmodifiableList(scopes)` because it still reflects future mutations to the entity's internal list.
- **Confidence.** High.

---

## 2. The `scopes` Postgres-array mapping compiles but is unverified against a live database

- **Issue.** `@JdbcTypeCode(SqlTypes.ARRAY)` on `List<String> scopes` is the correct Hibernate 6 mapping for a `text[]` column, and the implementation notes confirm it compiles. However, `spring.jpa.hibernate.ddl-auto=validate` means the real proof is whether the Spring context starts against the actual `text[]` column and whether values round-trip. That verification is intentionally deferred to `ApiKeyPersistenceIntegrationTest` in Phase 10.
- **Evidence.** `ApiKey.java:59-61`; `services/auth/src/main/resources/application.properties` line 30 (`spring.jpa.hibernate.ddl-auto=validate`); `06-implementation-notes.md` §32 (AC5 noted as "not yet proven against a live DB row").
- **Recommendation.** No code change now. Ensure Phase 10's persistence test includes: (a) a non-empty `scopes` list that round-trips, (b) an assertion on the DB-reported column type (native query `SELECT pg_typeof(scopes) FROM api_keys WHERE id = :id` = `text[]`), and (c) an empty-list default case. If validation fails, add `columnDefinition = "text[]"` to the `@Column` annotation.
- **Confidence.** High.

---

## 3. No runtime guard against null or blank elements in `scopes`

- **Issue.** The factory accepts any `List<String>` and copies it without validating element contents. Postgres `text[]` can store null elements, but an API-key scope of `null` or `""` is almost certainly a bug in the caller (T24). Because `getScopes()` currently returns the live list, such a value could propagate silently.
- **Evidence.** `ApiKey.java:85-102` copies `scopes` without element validation.
- **Recommendation.** Either reject null/blank scope elements in `create(...)` with `Objects.requireNonNull`/`isBlank` checks, or document that validation is T24's responsibility. Given the defensive posture elsewhere in the factory, adding a non-null/non-blank guard in T23 is low cost and prevents bad data from reaching the DB.
- **Confidence.** Medium.

---

## 4. `findByPrefix` is a raw lookup with no lifecycle filtering

- **Issue.** `ApiKeyRepository.findByPrefix(String prefix)` returns every row with the given prefix, regardless of `revokedAt`, `expiresAt`, or the owning account. This is exactly what T23's scope authorizes, but T25's exchange logic must not use this method blindly — it will need to filter for active, non-expired keys itself.
- **Evidence.** `ApiKeyRepository.java:11-21`.
- **Recommendation.** No T23 code change. Add a brief note (or Javadoc on the repository method) that callers are responsible for checking `revokedAt`/`expiresAt` and, if needed, selecting the correct key when multiple rows share a prefix. This prevents T25 from treating `findByPrefix` as a safe "find the active key" method.
- **Confidence.** Medium.

---

## 5. The L7-vs-DDL prefix-width conflict is correctly deferred but remains unresolved

- **Issue.** `ApiKey` maps `prefix` as `VARCHAR(16)` per the DDL, while L7 defines a 32-character public prefix. This is not a T23 defect — the frozen brief explicitly defers resolution to T24 — but it is still a live blocker for any task that generates keys.
- **Evidence.** `ApiKey.java:45-46`; `04-frozen-task-brief.md` §7-9 (disposition #1).
- **Recommendation.** No T23 action. Ensure T24's Phase 1/2 explicitly revisits this conflict and picks one of the two recorded options (amend L7 or add a `V7` migration widening `prefix`). Do not let T24 silently assume the current column fits L7.
- **Confidence.** High.

---

## 6. `ApiKey` entity is `public`, but the repository is package-private

- **Issue/Non-issue.** The entity class is declared `public`, while the repository is package-private. Other modules could technically import `ApiKey`, but they cannot query it (repository not visible) and the `apikey` module's service will expose DTOs, not entities. `ArchitectureTest` does not restrict entity visibility. This is consistent with `MfaEnrollment` and `RecoveryCode` (both `public`). No finding — confirmed acceptable.
- **Evidence.** `ApiKey.java:33`; `MfaEnrollment.java:24`; `RecoveryCode.java:28`.
- **Confidence.** High.

---

## 7. Immutable/mutable column split matches the frozen brief and codebase convention

- **Issue/Non-issue.** `id`, `keyUuid`, `accountId`, `prefix`, `keyHash`, and `createdAt` are `updatable = false`; `name`, `scopes`, `lastUsedAt`, `expiresAt`, and `revokedAt` are mutable. This matches `MfaEnrollment`'s pattern and the frozen brief. No finding — confirmed correct.
- **Evidence.** `ApiKey.java:39-73`.
- **Confidence.** High.

---

## 8. `keyHash` mapping mirrors the established `CHAR(64)` precedent

- **Issue/Non-issue.** `@JdbcTypeCode(SqlTypes.CHAR)` + `columnDefinition = "char(64)"` + `unique = true` matches `RecoveryCode.codeHash` and the DDL. No finding — confirmed correct.
- **Evidence.** `ApiKey.java:50-52`; `RecoveryCode.java:39-41`; `V1__auth_baseline_schema.sql` line 87.
- **Confidence.** High.

---

## 9. No `agents.md` or LOCKED-decision violations observed

- L12 is respected: `accountId` is a plain `Long` column with no `@ManyToOne` to `Account`; `apikey/` imports nothing from `com.themistra.auth.account`.
- `repositories_are_never_public` is respected: `ApiKeyRepository` has no `public` modifier.
- No secrets or credential material are stored; only the SHA-256 `keyHash` field exists.
- `spec/` files were not modified.

---

## Open Questions

None. The only open item is the L7 prefix-width conflict, which is explicitly deferred to T24 and recorded in the frozen brief.

(End of artifact)
