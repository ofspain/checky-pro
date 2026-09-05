# auth · T23 · Phase 10 — Test Generation

`ApiKeyPersistenceIntegrationTest` created (Testcontainers, Postgres + Kafka). No production code changed this phase. No named `package.md` §8 test maps to T23 (confirmed in Phase 1/2).

**Verification performed:** `mvn -pl services/auth -am test -Dtest=ApiKeyPersistenceIntegrationTest` — 7/7 pass. Full suite (`mvn -pl services/auth -am test`) — 493 tests, same 6 pre-existing/unrelated errors as the established baseline (Kafka delivery-timing flake, audit-FK ordering ×3, Mockito strict-stubbing ×2), unchanged by this task.

One bug was found and fixed during this phase, in the test fixture itself, not production code: an initial draft of `getScopesReturnsADefensiveCopy` used the prefix literal `"ck_live_defensive"` (17 characters), which exceeds `VARCHAR(16)` and failed with `value too long for type character varying(16)` — an accidental live demonstration of the exact L7-vs-column-width conflict already flagged and deferred to T24 in the frozen brief. Fixed by shortening the literal to `"ck_live_defcopy0"` (16 characters); no scope or design implication, purely a test-data-length fix.

## Test manifest

| Test | Maps to | What it proves |
|---|---|---|
| `apiKeyRoundTripsEveryColumnIncludingTheScopesArray` | AC1, AC5 | Every `api_keys` column, including `scopes`, round-trips through a real save → `entityManager.clear()` → reload cycle (not just an in-memory `save()` return value). Additionally asserts `pg_typeof(scopes)::text = 'text[]'` via a native query — direct proof the `@JdbcTypeCode(SqlTypes.ARRAY)` mapping (frozen brief, Phase 3 finding #3) is correct against the real column, closing the one open risk Phase 7/8/9 explicitly deferred to this phase. |
| `apiKeyPersistsWithNullableFieldsAbsent` | AC1 | `lastUsedAt`, `expiresAt`, `revokedAt` persist and reload as null when not set; `scopes` persists and reloads as an empty (not null) list. |
| `findByPrefixReturnsMatchingKeys` | AC3 | The task statement's named requirement — `findByPrefix` finds a saved key by its exact prefix. |
| `findByPrefixReturnsEmptyListForUnknownPrefix` | AC3, frozen brief (Phase 3 finding #4) | Confirms `List`, not `Optional`, semantics: an unmatched prefix returns an empty list, never `null` and never an exception. |
| `createRejectsNullRequiredArguments` | Frozen brief constraints (null handling) | Each of `accountId`, `prefix`, `keyHash`, `name`, `createdAt` throws `NullPointerException` when null, via `Objects.requireNonNull` in `create(...)`. |
| `createRejectsNullScopeElements` | Phase 9 resolution (finding #3) | A `scopes` list containing a `null` element is rejected at creation, proving the Phase 9 fix (`scopes.forEach(Objects::requireNonNull)`) actually works, not just compiles. |
| `getScopesReturnsADefensiveCopy` | Phase 7/8 finding #1, Phase 9 resolution | `getScopes()` returns an unmodifiable copy — attempting `.add(...)` on the returned list throws `UnsupportedOperationException`, proving the `List.copyOf(...)` fix actually closes the mutability leak, not just that it compiles. |

Every acceptance criterion in the frozen brief (AC1–AC5) and every Phase 9-resolved finding that had test-observable behavior now has a corresponding, passing, Testcontainers-verified test. No boundary or state transition from the frozen brief's scope is left unproven.
