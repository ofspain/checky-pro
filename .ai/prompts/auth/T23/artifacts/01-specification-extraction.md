# auth · T23 · Phase 1 — Specification Extraction

## Business Rules

- **R30.** WHEN an authenticated user with the `MERCHANT` role and confirmed MFA calls `POST /api-keys` with a name, THEN the system SHALL create an API key with prefix `ck_live_`, store a SHA-256 hash of the full key, return the plaintext key exactly once, and record an `api_key.created` audit event.

R30 is the only requirement scoped to T23, and only its *data-shape* half applies here — the creation flow itself (role/MFA gating, hashing, audit) is T24's job. T23's contribution to R30 is making the row R30 describes representable and persistable: an entity matching the `api_keys` table exactly, plus the lookup-by-prefix method the task statement names directly (which isn't itself an R30 sentence, but is required by the task statement and by R31/R33's later needs — see Dependencies).

R31–R35 (exchange, `last_used_at` update, uniform 401, list, revoke) are out of scope for T23 — they belong to T24–T27 and are not implemented, referenced only where a repository method this task adds will later be needed by them (noted below, not built ahead of time).

## Locked Decisions

- **L7. API key format.** Public prefix is `ck_live_` followed by a random 24-character alphanumeric suffix (lookup handle). The full key is `ck_live_<suffix>.<secret>` with a 32-character secret. Only SHA-256 is stored; plaintext is returned exactly once. — Directly constrains the entity: `prefix` and `keyHash` must be modeled to hold exactly this shape's outputs (`prefix` = `ck_live_<suffix>`, up to `VARCHAR(16)` per the DDL — see Open Questions; `keyHash` = 64-char SHA-256 hex, matching `CHAR(64)`). L7 does not require T23 to generate or hash keys itself (that's T24); it constrains what the columns must be able to hold.
- **L12. Module boundaries.** No feature module may import an entity class from another feature module; ArchUnit-enforced (`ArchitectureTest.only_the_account_module_may_touch_the_Account_entity`). `ApiKey.accountId` must be a plain `Long` column, never a JPA relation to `Account`.

(L8/L9/L10/L11 — API-key JWT contract, token claims, MFA enforcement, public-endpoint discipline — govern the exchange/issuance flow (T24/T25), not entity/repository mapping; not applicable to T23.)

## Files involved

**Existing, to read/extend:**
- `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql:82-96` — the authoritative `api_keys` DDL; T23 maps to this exactly, adds no migration.
- `services/auth/src/main/java/com/themistra/auth/apikey/package-info.java` — already states the module's intent; not modified.
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollment.java`, `MfaEnrollmentRepository.java`, `RecoveryCodeRepository.java` — pattern references only (entity shape, repository shape, package-private convention), not touched.
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` — not modified, but the new entity/repository must satisfy it (repository package-private, no cross-module `Account` import) or the build fails.

**New, expected by `design.md` §6 (T23's slice only):**
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java` — entity.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyRepository.java` — repository, package-private.

`design.md` §6 also lists `ApiKeyService.java`, `ApiKeyTokenIssuer.java`, `ApiKeyAuthenticationFilter.java`, `ApiKeyHasher.java`, `dto/`, `ApiKeyController.java` under `apikey/` — all out of scope for T23 (T24–T26).

## Dependencies

- `accounts` table / `account_id BIGINT REFERENCES accounts(id)` FK — `ApiKey.accountId` depends on this column existing (it does, V1) but not on the `Account` Java entity (forbidden by L12).
- Spring Data JPA (`JpaRepository<ApiKey, Long>`) — same as every other repository in this codebase.
- No config keys are needed for entity/repository mapping itself. `themistra.auth.api-key.prefix` and `themistra.auth.api-key.token-ttl-minutes` (`design.md` §4, lines 52-54) are consumed by T24/T25's service/issuer code, not by this task.
- `contracts/api/auth.yaml` is listed in this task's header as a governing contract, but the file does not exist yet — `contracts/api/` currently contains only a `.gitkeep`. This does not block T23 (entity/repository mapping needs no API contract), logged under Open Questions for completeness since the header names it.
- No dependency on `MfaEnrollment`/`RecoveryCode` or any other feature module's entities — `apikey` is a peer module, not a consumer of `mfa`.

## Acceptance Criteria

Mapped to R30 (the only requirement in scope), restricted to what an entity/repository task can actually satisfy:

- AC1 (R30, data shape). `ApiKey` maps every column of the `api_keys` table with correct nullability, types, and defaults — no column added, renamed, or dropped relative to the V1 DDL.
- AC2 (R30, data shape). `ApiKey.accountId` is a plain column, not a JPA relation to `Account` (L12).
- AC3 (task statement). A repository method exists to look up an `ApiKey` by its `prefix` column — the exact return shape (`Optional` vs `List`) is a Phase 2 design decision (see `00-repository-understanding.md` Known Gaps: `prefix` is indexed but not `UNIQUE` in the DDL).
- AC4 (implicit, ArchUnit). `ApiKeyRepository` is package-private; `ArchitectureTest` passes unmodified.

R31–R35's acceptance criteria are explicitly not this task's to satisfy.

## Tests required

`package.md` §8 has no test named for T23 directly. The four API-key-module named tests in §8 (`shouldCreateApiKeyAndShowPlaintextExactlyOnce`, `shouldExchangeValidApiKeyForMerchantJwt`, `shouldRejectRevokedOrUnknownApiKeyWithUniform401`, `shouldListAndRevokeOwnApiKeys`) all exercise service/controller-level behavior (creation, exchange, revoke) that does not exist until T24–T27 — none belong to T23, consistent with the Phase 0 finding.

**Note on §8/requirements.md numbering drift:** `package.md` §8 maps these four tests to R27–R30, but `requirements.md`'s own numbering has API-key requirements at R30–R35 (a 3-ID offset). Per this pipeline's established resolution (T16–T22), `requirements.md` is treated as authoritative — so `shouldCreateApiKeyAndShowPlaintextExactlyOnce` etc. actually correspond to R30–R33/R34, not R27–R30 as §8's own text says. Not T23's drift to fix; noted for whichever task's Phase 1 next touches those tests.

Boundary tests implied by this task's own scope (to be proposed formally in Phase 5, not built here):
- A `*PersistenceIntegrationTest`-style test (Testcontainers), matching `MfaPersistenceIntegrationTest`'s convention, that `saveAndFlush`s an `ApiKey` and asserts every column round-trips correctly, including the `scopes TEXT[]` array column and all four nullable timestamp columns (`last_used_at`, `expires_at`, `revoked_at`, and the always-set `created_at`).
- A test for whatever the prefix-lookup method resolves to (found / not-found cases at minimum).

## Open Questions

- Same three genuine blockers already raised in Phase 0, restated here because Phase 2 (design) cannot proceed without resolving them: (1) exact return shape/name for the lookup-by-prefix method, since `prefix` has no `UNIQUE` constraint in the DDL; (2) whether a UUID→internal-`accountId` resolver belongs in this repository (the `MfaEnrollmentRepository.findAccountIdByUuid` pattern) or is out of scope for T23 entirely; (3) how `scopes TEXT[]` should be mapped in Hibernate — no existing column in this schema uses a Postgres array type, so there's no in-repo precedent to follow.
- `contracts/api/auth.yaml`, named in this task's header as a governing contract, does not exist (`contracts/api/` is empty except `.gitkeep`). Not a blocker for T23 specifically, but flagged since the header cites it as authoritative and it isn't.
- `package.md` §11 Q3 ("max active API keys per merchant? additional scopes beyond `merchant.api`?") remains unresolved in the spec itself. Irrelevant to T23's entity/repository mapping either way — a cap or scope set doesn't change the column mapping — but will matter to T24.
