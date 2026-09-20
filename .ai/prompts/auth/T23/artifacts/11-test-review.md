# auth · T23 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T23 — ApiKey entity/repository |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Review of `ApiKeyPersistenceIntegrationTest` against the frozen brief's acceptance criteria. Gaps only.

---

## Confirmed coverage (no gaps)

- **AC1 (column mapping)** — `apiKeyRoundTripsEveryColumnIncludingTheScopesArray` and `apiKeyPersistsWithNullableFieldsAbsent` together assert every mapped column round-trips.
- **AC3 (lookup-by-prefix)** — `findByPrefixReturnsMatchingKeys` and `findByPrefixReturnsEmptyListForUnknownPrefix` cover the found and not-found branches.
- **AC5 (scopes array mapping)** — `apiKeyRoundTripsEveryColumnIncludingTheScopesArray` explicitly asserts `pg_typeof(scopes)::text = 'text[]'` via native query.
- **Null-safety constraints** — `createRejectsNullRequiredArguments` covers every `Objects.requireNonNull` in the factory.
- **Phase 7/8 mutability finding** — `getScopesReturnsADefensiveCopy` proves the returned list is unmodifiable.
- **Phase 9 null-scope-element finding** — `createRejectsNullScopeElements` proves null elements are rejected.
- **AC2 (L12, plain `accountId`) and AC4 (package-private repository)** — covered by `ArchitectureTest`, not this integration-test file; no gap in overall suite coverage.

---

## Gaps

### 1. `findByPrefix` is not tested with multiple matching rows

- **Gap.** The repository returns `List<ApiKey>` precisely because `prefix` has no `UNIQUE` constraint and duplicates are possible. The current tests cover exactly-one match and zero matches, but not the >1 match case that the `List` return type exists to handle.
- **Why it matters.** A future regression that silently limited the query (e.g., an accidental `LIMIT 1` or a misunderstood `Optional` refactor) would pass the current suite even though it broke the documented `List` semantics.
- **Suggested test.** `findByPrefixReturnsAllKeysSharingAPrefix`: insert two `ApiKey` rows (different `keyUuid`/`keyHash`) with the same prefix for the same or different accounts, call `findByPrefix`, and assert the returned list has size 2 and contains both distinct key hashes/uuids.

---

### 2. Factory's null-scopes default is not tested

- **Gap.** `ApiKey.create(...)` explicitly converts a null `scopes` argument to an empty `ArrayList`, but every test either passes `List.of(...)` or `List.of()` — never `null`.
- **Why it matters.** The frozen brief documents the null-to-empty default as intentional behavior; without a test, a refactor could change `scopes != null ? new ArrayList<>(scopes) : new ArrayList<>()` to `Objects.requireNonNull(scopes)` and the suite would still pass, breaking the contract.
- **Suggested test.** `createDefaultsNullScopesToEmptyList`: call `ApiKey.create(..., null, ...)` and assert `getScopes()` returns an empty list. Optionally persist and reload to assert the DB stores `{}` (already implied by `apiKeyPersistsWithNullableFieldsAbsent`, but the factory default itself is not).

---

### 3. Factory's defensive copy of the input scopes list is not tested

- **Gap.** `ApiKey.create(...)` copies the incoming `scopes` into a new `ArrayList`, but `apiKeyRoundTripsEveryColumnIncludingTheScopesArray` passes an immutable `List.of(...)`, so the copy is never exercised.
- **Why it matters.** A refactor that removed the `new ArrayList<>(scopes)` wrapper would still pass every current test, yet would let a caller mutate its own list after creation and corrupt the entity before persistence.
- **Suggested test.** `createDefensivelyCopiesMutableScopesList`: create a mutable `ArrayList<String>`, pass it to `ApiKey.create(...)`, then mutate the original list; assert `apiKey.getScopes()` is unchanged. Persist, clear, reload, and assert the DB still holds the original scopes.

---

### 4. DB uniqueness constraints on `key_uuid` and `key_hash` are not exercised

- **Gap.** The DDL declares both `key_uuid` and `key_hash` as `NOT NULL UNIQUE`, but no test attempts to insert a duplicate of either and assert a `DataIntegrityViolationException`.
- **Why it matters.** Uniqueness is part of the mapped schema; a missing `@Column(unique = true)` annotation (or a future regression that removes it) would not be caught by the current tests, even though schema validation would likely catch it at startup. Testing the runtime DB enforcement makes the contract explicit and protects against test setups that bypass validation.
- **Suggested test.** `duplicateKeyHashIsRejectedByTheDatabase`: save one `ApiKey`, then attempt to save a second `ApiKey` with the same `keyHash` (different `keyUuid` via the factory) and assert `DataIntegrityViolationException`. For `key_uuid`, a duplicate is harder to construct because the factory always generates a fresh UUID; skip it or use a native `INSERT` to force the collision, since the factory intentionally prevents caller-supplied uuids.

---

### 5. `findByPrefix` case-sensitivity boundary is not tested

- **Gap.** `findByPrefixReturnsMatchingKeys` uses the exact same string for save and lookup; there is no test for a different-cased prefix.
- **Why it matters.** `prefix` is `VARCHAR`, not `citext`, so lookup is case-sensitive. A caller who lowercases or uppercases the prefix would unexpectedly get an empty list. This is a behavior contract, not just an implementation detail.
- **Suggested test.** `findByPrefixIsCaseSensitive`: save a key with prefix `ck_live_ABCD1234`, then call `findByPrefix("ck_live_abcd1234")` and assert an empty list; the exact-case lookup should still return the key.

---

## Open Questions

None. The L7-vs-column-width conflict remains deferred to T24 as recorded in the frozen brief; it is not a test-coverage gap for T23.

(End of artifact)
