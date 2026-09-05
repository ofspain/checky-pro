# auth · T23 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R30** (data-shape half only — creation/gating/audit belongs to T24) | Yes | `apikey/ApiKey.java:33-73` (entity, all 11 `api_keys` columns mapped); `:88-108` (`create(...)` factory) | `ApiKeyPersistenceIntegrationTest.apiKeyRoundTripsEveryColumnIncludingTheScopesArray` (`:53-74`), `.apiKeyPersistsWithNullableFieldsAbsent` (`:77-90`) | No | None — T23 never claimed R30's creation/gating/audit behavior; only the row shape R30 describes. |
| **Task statement, part 2** ("Add lookup-by-prefix method") | Yes | `apikey/ApiKeyRepository.java:11,22` (`List<ApiKey> findByPrefix(String prefix)`) | `findByPrefixReturnsMatchingKeys`, `findByPrefixReturnsEmptyListForUnknownPrefix`, `findByPrefixReturnsAllKeysSharingAPrefix`, `findByPrefixIsCaseSensitive` | No | None |
| **L7** (API key format) — column-shape constraint only | Partial | `ApiKey.java:45-46` (`prefix`, `VARCHAR(16)` as-is), `:50-52` (`keyHash`, `CHAR(64)`) | N/A (no key generation in this task) | No, for T23's scope | **Known, deferred deviation**: L7's public prefix (`ck_live_` + 24-char suffix = 32 chars) does not fit the existing `prefix VARCHAR(16)` column. Explicitly identified in Phase 3, dispositioned in the frozen brief (`04-frozen-task-brief.md`, disposition #1) as **deferred to T24 with an explicit owner and two recorded resolution options** — not a silent gap. T23 maps the column as it actually exists, per the task statement's own framing ("map the existing table"), and generates no keys, so this does not block T23 itself. |
| **L12** (module boundaries — `accountId` plain column, no `Account` import) | Yes | `ApiKey.java:42-43` (`accountId: Long`, no `@ManyToOne`) | `ArchitectureTest.only_the_account_module_may_touch_the_Account_entity` (run explicitly in Phase 6 and re-confirmed passing after Phase 9's edits) | No | None |
| **Repository visibility convention** (`ArchitectureTest.repositories_are_never_public`, implicit AC) | Yes | `ApiKeyRepository.java:11` (no `public` modifier) | Same `ArchitectureTest` run | No | None |

## Beyond the matrix: what this task actually delivered

- A working `ApiKey` entity and `ApiKeyRepository`, exercised by 12 passing Testcontainers tests (7 from Phase 10, 5 more closing every Phase 11 coverage gap Kimi identified — multi-match `findByPrefix`, the null-`scopes` default, the factory's defensive copy, DB-level `UNIQUE` enforcement on `key_hash`, and `findByPrefix`'s case-sensitivity).
- The one genuinely novel technical risk this task carried — mapping a Postgres `TEXT[]` column with Hibernate 6's `@JdbcTypeCode(SqlTypes.ARRAY)`, something no other entity in this schema had done — is now proven against a real database, not just compiled: `apiKeyRoundTripsEveryColumnIncludingTheScopesArray` asserts `pg_typeof(scopes)::text = 'text[]'` directly.
- Two independent adversarial reviews (Phase 3 design-challenge, Phase 8 code review, Phase 11 test review) ran against this task; every accepted finding was applied and is now itself covered by a test proving the fix works, not just that it compiles (e.g., `getScopesReturnsADefensiveCopy`, `createRejectsNullScopeElements`).
- One real, previously-unknown spec/schema conflict was surfaced (L7 vs. `VARCHAR(16)`) and correctly handled: not silently ignored, not unilaterally "fixed" by widening a table this task had no authorization to touch, but explicitly recorded with an owner (T24) and two preserved resolution paths.

## Answers

**1. Is the task fully complete?**
Yes. Both parts of the task statement — mapping the existing table, and adding a lookup-by-prefix method — are implemented and verified against a real database.

**2. Does it satisfy every acceptance criterion?**
Yes. AC1–AC5 from the frozen brief (`04-frozen-task-brief.md`) are all met: column mapping, L12 module-boundary compliance, `findByPrefix`'s existence and `List` semantics, repository package-privacy, and the `scopes` array mapping — the last of these now proven at the database level, not merely asserted.

**3. Does it violate any LOCKED decision?**
No violation. L12 is respected and CI-verified. L7 has a real, known tension with the pre-existing (immutable, V1) schema — but T23 does not violate L7; it maps the column that exists without altering it, and the tension is explicitly owned by T24, not swept under this task's completion.

**4. Remaining risks**
- L7-vs-`VARCHAR(16)` remains genuinely unresolved and will block T24 the moment it tries to generate a compliant key — this is a real, live risk for the next task, not for this one.
- `findByPrefix` performs no lifecycle filtering (documented in its own Javadoc, per Phase 9's resolution) — T25's exchange flow must not treat any returned row as automatically active; this is now explicit rather than assumed.
- The six pre-existing, already-documented test failures elsewhere in the suite (Kafka delivery timing, audit-FK ordering ×3, Mockito strict-stubbing ×2) remain unfixed and unrelated to this task — confirmed unchanged before and after (486→498 total tests, same 6 errors both times).
- `contracts/api/auth.yaml`, named in this task's own header as a governing contract, still does not exist (`contracts/api/` is empty except `.gitkeep`) — flagged again since it was flagged in Phase 1 and remains true; irrelevant to entity/repository mapping specifically, but a real gap for whichever task next needs it.

## Verdict

**PASS** — both parts of the task statement are implemented, tested against a real Postgres instance (including the one genuinely novel mapping risk this task carried), no LOCKED decision is violated, and the one real spec/schema conflict discovered along the way was surfaced and explicitly deferred with an owner rather than hidden or improperly resolved outside this task's authority.
