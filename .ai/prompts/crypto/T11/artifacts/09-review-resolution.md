# crypto · T11 · Phase 9 — Review Resolution

**Human Approval gate.** Approved 2026-09-05. Findings from Phase 7 (self-review) and Phase 8 (Kimi
independent review) are consolidated below — Kimi Issues 1, 8, 9, 10 independently confirmed
self-review Findings 1-4. No public API changed (the repository's method rename from
`findTopByOrderByVersionDesc`+`findByChainAndContractAddressAndVersion`-pair to
`findCurrentVersionEntry` is an internal, package-private interface change, not a public API), no
class renamed, no refactoring beyond the accepted fixes.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Self-review Finding 1 / Kimi Issue 1 — global (not per-chain) "current version" silently breaks lagging chains | **ACCEPTED** | `TokenAllowlistRepository.findCurrentVersionEntry(chain, contractAddress)` — a single JPQL query scoping "current version" to the given chain, replacing the two-call global-max approach entirely. |
| 2 | Kimi Issue 2 — required test files are missing | **REJECTED** | No change. This reflects a misunderstanding of this pipeline's own phase separation: Phase 6's own directive states "Do NOT write tests here (that is Phase 10)." Tests are correctly deferred, not missing. |
| 3 | Kimi Issue 3 — two-query `validate` has a read-committed race between version-read and keyed lookup | **ACCEPTED (same fix as #1)** | The single-query `findCurrentVersionEntry` closes this atomically alongside #1. |
| 4 | Kimi Issue 4 — seeder inserts entries one-by-one, so a new version can be partially visible | **ACCEPTED as a documented, disclosed risk; not code-fixed** | Wrapping the whole seeding loop in one transaction was considered and rejected: a single benign duplicate-key conflict would then roll back every other, non-conflicting entry, trading a narrow, transient, self-resolving startup race for a larger one. Documented in `TokenAllowlistSeeder`'s class Javadoc. |
| 5 | Kimi Issue 5 — `DataIntegrityViolationException` catch treats every constraint failure as benign | **ACCEPTED** | `TokenAllowlistSeeder.seedIfAbsent` now re-verifies the row actually exists (via the extracted `alreadyExists` helper) before logging the benign-race message; re-throws otherwise. |
| 6 | Kimi Issue 6 — duplicate/conflicting config entries with the same key are silently ignored | **ACCEPTED** | `TokenAllowlistProperties`'s compact constructor now rejects a duplicate `(chain, contractAddress, version)` tuple with `IllegalStateException`, mirroring `ProviderProperties`'s own precedent for semantic, cross-field config validation. |
| 7 | Kimi Issue 7 — empty-table path logs `WARN` on every `validate` call, risking log flooding | **ACCEPTED as a documented, intentional tradeoff; not code-fixed** | R14's own "surfaced loudly" requirement is exactly what this behavior satisfies; suppressing it would work against the requirement, not just against log noise. Documented in `TokenValidator`'s class Javadoc. |
| 8 | Self-review Finding 2 / Kimi Issue 8 — `{ETHEREUM, TRON}` hardcoded in a third, unlinked location | **ACCEPTED (documentation only)** | No code change. Added a Javadoc note on `TokenValidator.KNOWN_CHAINS` explaining why deriving it from `TokenAllowlistProperties` would be semantically wrong (config content ≠ supported chains) and why consolidating with `ProviderProperties`/`FinalityProperties` (T03, frozen) is out of this task's own scope. |
| 9 | Self-review Finding 3 / Kimi Issue 9 — `TokenAllowlistSeeder` has no defensive null-check on `entries` | **ACCEPTED** | Added `Objects.requireNonNull(properties.entries(), "entries")` at the top of `run`. |
| 10 | Self-review Finding 4 / Kimi Issue 10 — `decimals` range check looser than any realistic value | **ACCEPTED** | Tightened `TokenAllowlist.toShort`'s bound from `[0, Short.MAX_VALUE]` to `[0, 30]` (comfortably covers every known real-world token); added a matching `@Max(30)` to `TokenAllowlistProperties.Entry.decimals` for config-time failure. |
| 11 | Kimi Issue 11 — `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` mapping for `signature` unverified against `ddl-auto=validate` | **ACCEPTED — confirmed wrong, not merely unverified** | Decompiled Hibernate 6.6.22.Final's own `PostgreSQLDialect.columnType(int)` bytecode directly: `SqlTypes.LONGVARCHAR` (-1) is absent from its explicit type-code mapping and falls through to the generic base-`Dialect` default (not `"text"`); `SqlTypes.LONG32VARCHAR` (4001) is explicitly mapped to `"text"`. Switched `TokenAllowlist.signature`'s annotation accordingly. |
| 12 | Kimi Issue 12 — mixed versions across chains not rejected in config | **MOOT after #1's fix** | With per-chain version scoping, an Ethereum entry at version 2 and a Tron entry at version 1 are both correctly "current" for their own chains independently — the cross-chain interference this finding worried about no longer exists. No change needed beyond #1. |

## Summary

7 accepted with code changes (1/3 combined, 5, 6, 9, 10, 11), 2 accepted as documented/disclosed risks
with no code fix (4, 7), 1 accepted as documentation-only (8), 1 rejected as a misunderstanding of this
pipeline's own phase structure (2), 1 resolved as moot by another fix (12).

`mvn -pl services/crypto compile` succeeds cleanly after all changes, with zero new warnings.

Files changed in this phase: `TokenAllowlistRepository.java`, `TokenValidator.java`,
`TokenAllowlistSeeder.java`, `TokenAllowlist.java`, `TokenAllowlistProperties.java`. No file outside
`services/crypto/src/main/java/com/themistra/crypto/token/` and
`.../common/config/TokenAllowlistProperties.java` was touched. No public method signature changed on
any class consumed outside `token/`; no class renamed.
