# crypto · T11 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS** (`artifacts/12-specification-verification.md`). Proceeding to prepare T11
for merge. Branches off `main`; `main` remains deployable throughout — no commit in this task touches
anything outside `services/crypto/` (plus this task's own `.ai/prompts/crypto/T11/` artifacts).

## Commit title

```
crypto: add token allowlist and contract-address-only validator (T11)
```

## Commit message

```
crypto: add token allowlist and contract-address-only validator (T11)

Implement TokenValidator, identifying a token strictly by <chain,
contractAddress> and never a symbol (R13, L7) - a non-allowlisted
contract classifies as UNKNOWN_TOKEN (an empty Optional, staying
quorum-agnostic since no caller yet needs QuorumOutcome) and logs a
structured WARN line, the "surfaced loudly" R14 requires.

Seeding is config-driven (TokenAllowlistProperties + a startup
TokenAllowlistSeeder), not a Flyway DML migration - a DML seed migration
was the original design but violates agents.md's "Flyway, DDL-only
migrations" rule, caught during this task's own Phase 3 design review
before implementation. V5 grants crypto_app INSERT/SELECT (no
UPDATE/DELETE - an allowlist entry is inserted once per (chain,
contractAddress, version) and never revised); the same rereading of the
grants migrations that caught this gap also confirmed crypto_app had no
grant at all on token_allowlist, the same gap pattern T10 found and
fixed for provider_health.

"Current version" is scoped per chain, not globally, resolved via one
atomic query (TokenAllowlistRepository.findCurrentVersionEntry) - the
original design used a single table-wide maximum, which this task's own
self-review and independent review both independently caught as a real
bug: a version bump on one chain would silently make every other,
unchanged chain's tokens report UNKNOWN_TOKEN the moment its own version
fell behind the new global max. Fixed and proven end-to-end against a
real Postgres before this task's own verification.

Also caught during independent review: TokenAllowlist.signature's
@JdbcTypeCode(SqlTypes.LONGVARCHAR) mapping does not actually map to
PostgreSQL's TEXT type in Hibernate 6.6.22 (confirmed by decompiling
PostgreSQLDialect.columnType's own bytecode) - only SqlTypes.LONG32VARCHAR
does. Fixed before the mapping was ever exercised against a real schema.

Seed data (placeholder, not real mainnet contract addresses - this
codebase has no way to verify a real address's correctness from memory,
and no signing key/algorithm exists anywhere in this spec for a real
signature) covers Ethereum and Tron USDT/USDC at version 1.

Testing gated on Docker (TokenAllowlistRepositoryIntegrationTest) has
not executed in this environment - a pre-existing limitation already
affecting this service's other integration tests, disclosed throughout
this task's artifacts, not a defect in this change.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Main:**
- `services/crypto/src/main/resources/db/migration/V5__crypto_app_token_allowlist_grant.sql` — new
- `services/crypto/src/main/java/com/themistra/crypto/common/config/TokenAllowlistProperties.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlist.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlistRepository.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/token/TokenValidator.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlistSeeder.java` — new
- `services/crypto/src/main/resources/application.properties` — modified (adds four
  `themistra.crypto.token-allowlist.entries[...]` blocks)
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` —
  modified (Flyway-version-list assertion extended to `V5`; `provider_health`/`token_allowlist` removed
  from `UNGRANTED_TABLES`)

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/common/config/TokenAllowlistPropertiesTest.java` — new
- `services/crypto/src/test/java/com/themistra/crypto/token/TokenAllowlistTest.java` — new
- `services/crypto/src/test/java/com/themistra/crypto/token/TokenValidatorTest.java` — new
- `services/crypto/src/test/java/com/themistra/crypto/token/TokenAllowlistSeederTest.java` — new
- `services/crypto/src/test/java/com/themistra/crypto/token/TokenModuleBoundaryTest.java` — new
- `services/crypto/src/test/java/com/themistra/crypto/token/TokenAllowlistRepositoryIntegrationTest.java` — new

**Pipeline artifacts:**
- `.ai/prompts/crypto/T11/artifacts/00-repository-understanding.md` through `13-pr-preparation.md` — all 14 phase artifacts

## Summary

T11 adds the token-identity boundary R13/L7 require: a signed, versioned canonical-token allowlist and
a validator that resolves identity strictly by `<chain, contractAddress>`, never a symbol. It is the
first task in this service to seed reference data via application code rather than schema migration
(a deliberate correction of this task's own initial design, made before implementation, to respect
agents.md's DDL-only migration rule), and its own review cycle caught and fixed two independently-
significant bugs — a global-vs-per-chain versioning flaw and a wrong Hibernate type mapping — before
either reached the verification phase.

## Testing performed

- `mvn -pl services/crypto test-compile` — BUILD SUCCESS, no new warnings.
- `mvn -pl services/crypto test -Dtest=TokenAllowlistPropertiesTest,TokenAllowlistTest,TokenValidatorTest,TokenAllowlistSeederTest,TokenModuleBoundaryTest` — 33/33 passing.
- `mvn -pl services/crypto -am test` (full module suite) — 345 tests, 337 passing, 8 errors, all
  `IllegalState: … Docker environment …` (7 pre-existing from T02/T04/T08/T09/T10's own Testcontainers
  integration tests, 1 new from this task's own `TokenAllowlistRepositoryIntegrationTest`) — zero
  genuine failures.
- Docker unavailable throughout this session — `TokenAllowlistRepositoryIntegrationTest` compiles
  cleanly and is the first integration test in this service to exercise a full seeding-at-startup path
  (not just a narrow entity/repository slice) against the real `application.properties` configuration,
  but has not itself executed against a real Postgres in this environment.

## Specification references

- **Task:** T11 — Token allowlist + validator (`spec/crypto-service/tasks.md` #11).
- **Requirements:** R13, R14 (`spec/crypto-service/requirements.md:25-26`).
- **Locked decisions:** L7 (`spec/crypto-service/design.md:11`) — token identity is contract address
  only, against a signed, versioned canonical allowlist; a symbol is never used to decide identity.
- **Named tests:** `shouldIdentifyTokenByContractAddressNotSymbol`,
  `shouldSurfaceUnknownTokenForNonAllowlistedContract` (`package.md` §8).
- **Contracts:** none of `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`,
  `contracts/events/chain/tx-finalized.v1.schema.json` are touched by this task — `TokenValidator` is a
  pure lookup service with no HTTP endpoint and no event emission.
