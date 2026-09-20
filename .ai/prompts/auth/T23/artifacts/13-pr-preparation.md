# auth · T23 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

## Commit title

```
feat(auth): map ApiKey entity/repository, add lookup-by-prefix (T23)
```

## Commit message

```
feat(auth): map ApiKey entity/repository, add lookup-by-prefix (T23)

Add ApiKey, mapping every column of the existing api_keys table (V1)
exactly as it stands, plus a package-private ApiKeyRepository with a
lookup-by-prefix method. This is the foundation later API-key tasks
(T24 create, T25 exchange, T26 CRUD) build on; no service, controller,
or key-generation behavior is added here.

The one genuinely novel piece is mapping api_keys.scopes (Postgres
TEXT[]) with Hibernate 6's @JdbcTypeCode(SqlTypes.ARRAY) - no other
entity in this schema maps an array column. Proven against a real
Testcontainers Postgres instance via a native pg_typeof(scopes)
assertion, not just compiled.

Two adversarial reviews (design-challenge, independent code review)
plus a test review ran against this task; all accepted findings were
applied and are each covered by a dedicated test proving the fix
works: getScopes() now returns a defensive List.copyOf(...) instead
of a mutable live reference, create(...) rejects null elements within
a non-null scopes argument, and findByPrefix returns List (not
Optional) since prefix has no DB-level UNIQUE constraint.

One real, previously-unknown spec/schema conflict was found and
explicitly deferred rather than silently ignored or unilaterally
"fixed": L7 defines the public API-key prefix as ck_live_ + a
24-character suffix (32 characters total), but the existing prefix
column is VARCHAR(16). This task maps the column as it actually is
and generates no keys, so the conflict does not block it - but it
will block whichever task (T24) next generates a compliant key. Both
resolution options (shrink L7's suffix, or widen the column via a new
migration) are recorded in the frozen brief for T24 to pick up.

Full suite: 498 tests, same 6 pre-existing/unrelated failures as
before this task (Kafka delivery timing, audit-FK ordering, Mockito
strict-stubbing) - unchanged baseline.

Refs: spec/auth-service/tasks.md T23, requirement R30, L7 (deferred), L12

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

(Note: as in prior tasks this session, the phase template's boilerplate trailer names "Claude Opus 4.8" — generic scaffolding text, not a record of which model did the work. This entire task ran on Claude Sonnet 5, so the trailer above reflects that.)

## Files changed

**Production**
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java` — new. Entity mapping every `api_keys` column; static `create(...)` factory; defensive `getScopes()`.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyRepository.java` — new. Package-private, `List<ApiKey> findByPrefix(String prefix)`.

**Tests**
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyPersistenceIntegrationTest.java` — new. 12 tests: column round-trip (including the `scopes` array's DB-reported type), nullable-field handling, `findByPrefix`'s found/not-found/multi-match/case-sensitive behavior, null-argument and null-scope-element rejection, defensive-copy behavior on both read and write, and DB-level `UNIQUE(key_hash)` enforcement.

No migration, no other module, no `spec/` file touched.

## Summary

T23 gives the `apikey` module (previously an empty package scaffold) a working, fully-tested persistence layer: `ApiKey` and `ApiKeyRepository`. Every column of the pre-existing `api_keys` table is mapped correctly, including a Postgres array column no other entity in this service had mapped before — proven against a real database rather than assumed. Two rounds of adversarial review (design and code) plus a dedicated test review all ran against this task; every accepted finding is now both fixed and covered by a test that would fail if the fix were reverted. One real spec/schema inconsistency (L7 vs. the immutable `V1` column width) was discovered along the way and handled correctly: recorded, not hidden, and explicitly handed to the next task with both resolution paths preserved rather than resolved unilaterally outside this task's scope.

## Testing performed

- `mvn -pl services/auth -am compile` / `test-compile` — clean throughout.
- `mvn -pl services/auth test -Dtest=ArchitectureTest` — re-run after both the Phase 6 implementation and the Phase 9 fixes; passes clean both times, confirming `accountId` never becomes an `Account` relation and `ApiKeyRepository` stays package-private.
- `mvn -pl services/auth test -Dtest=ApiKeyPersistenceIntegrationTest` — 12/12 pass against real Testcontainers Postgres + Kafka.
- Full suite: 498 tests, 492 passing, the same 6 pre-existing/unrelated failures as the established baseline before this task (486 tests, same 6 failures) — confirmed unchanged.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 23 — "ApiKey entity/repository."
- **Requirements:** R30 (data-shape half only — the row R30 describes is now representable and persistable; creation/gating/audit behavior remains T24's).
- **LOCKED decisions:** L7 (API key format) — column-shape conflict identified and deferred to T24 with an explicit owner and two recorded resolution options, not violated. L12 (module boundaries) — respected, CI-verified via `ArchitectureTest`.
