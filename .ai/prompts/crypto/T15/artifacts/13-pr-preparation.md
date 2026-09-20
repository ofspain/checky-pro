# crypto · T15 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS** (`artifacts/12-specification-verification.md`). Proceeding to prepare T15
for merge. Branches off `main`; `main` remains deployable throughout.

## Commit title

```
crypto: add watch registration API (T15)
```

## Commit message

```
crypto: add watch registration API (T15)

Implement WatchService + WatchController for POST/DELETE
/internal/v1/watches (R18/R19), persisting Watch and a placeholder
ChainCursor per registration. This is the service's first HTTP API
surface - every prior task (T01-T14) built internal/adapter/persistence
logic only. Security required no new work: ResourceServerConfig (T03)
already mapped /internal/v1/** to the internal.crypto:write scope in
anticipation of exactly this endpoint.

ChainCursor.lastBlock is seeded with a documented -1 sentinel, never a
real chain position - ChainAdapter (a VERBATIM, frozen interface) has
no current-block method independent of an existing transaction hash,
and -1 is chosen over 0 specifically because block 0 is a real genesis
block on both launch chains. Task 16's watcher is expected to overwrite
it on first run. DELETE distinguishes a genuinely unknown watchId (404)
from an already-unregistered one (204, idempotent no-op via a single
atomic conditional UPDATE - race-safe by construction, since at most
one concurrent DELETE can match `WHERE status = 'REGISTERED'`).

Design review (Phase 3) caught a real scope mistake in the initial
brief: address validation (L8, a LOCKED decision - "invalid addresses
are rejected at the boundary") was originally excluded on the reasoning
that R18's own text doesn't mention validation. LOCKED decisions
constrain every applicable task regardless of whether the task's own
requirement text repeats them, and this endpoint is literally the
boundary L8 means. Fixed by wiring AddressValidator (T12, previously
uncalled by any code in this service) into WatchService.register for
both address and tokenContractAddress.

Independent review (Phase 8) found this service had zero controller-
advice infrastructure before this task, so framework-level errors
(bean-validation failures, malformed JSON, a malformed path UUID,
unexpected exceptions) would not have produced the RFC 9457
application/problem+json agents.md requires. Added
common/ApiExceptionHandler.java (this service's first global error
handler, @Order(LOWEST_PRECEDENCE) so WatchExceptionHandler's
domain-specific mappings still win), mirroring services/auth's own
ApiExceptionHandler shape. Verified end-to-end through real Spring MVC
dispatch, not assumed - a claim from the same review (that ddl-auto
=validate would reject a stricter JPA nullable=false than the DDL's own
physically-nullable column) was checked directly against a real
Postgres container before being accepted, and found to be safe.

expectedAmount is validated as a positive, scale-0 integer decimal
string capped at 78 digits, matching expected_amount NUMERIC(78, 0)'s
own precision - an initial unbounded regex would have let an oversized
value pass validation and fail at the database as a 500 instead of a
400 (caught independently by both self-review and Kimi's review).

No event emission, no real watcher subscription, and no
TokenValidator/allowlist checking are in this task's scope - all
explicitly deferred to later tasks per design.md's own file map.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Main:**
- `services/crypto/src/main/resources/db/migration/V6__crypto_app_watches_grant.sql` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchStatus.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/Watch.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchRepository.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursorRepository.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchNotFoundException.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/InvalidWatchRequestException.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchExceptionHandler.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/dto/RegisterWatchRequest.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/dto/RegisterWatchResponse.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchService.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchController.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/common/ApiExceptionHandler.java` — new

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchTest.java` — new (3 tests)
- `services/crypto/src/test/java/com/themistra/crypto/watch/ChainCursorTest.java` — new (3 tests)
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchServiceTest.java` — new (29 tests)
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchControllerTest.java` — new (11 tests)
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchRepositoryIntegrationTest.java` — new (5 tests)
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchModuleBoundaryTest.java` — new (1 test)
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` — modified (`watches`/`chain_cursors` removed from `UNGRANTED_TABLES`; Flyway version list extended to `"6"`)

**Pipeline artifacts:**
- `.ai/prompts/crypto/T15/artifacts/00-repository-understanding.md` through `13-pr-preparation.md` — all 14 phase artifacts

## Summary

T15 adds the service's first HTTP API surface: the watch registration endpoint R18/R19 requires,
persisting `Watch` and a placeholder `ChainCursor` per registration. Its own review cycle caught and
fixed a genuine LOCKED-decision scoping mistake (L8 address validation) in the original brief, and
closed a real infrastructure gap (no RFC 9457 handling existed anywhere in this service before this
task) with a new, reusable `common/ApiExceptionHandler`. Two claims were verified empirically against
real Postgres/Spring MVC before being trusted, rather than assumed correct: a Hibernate
`ddl-auto=validate` nullability-mismatch question, and the full HTTP-layer error-handling chain.

## Testing performed

- `mvn -pl services/crypto compile` — BUILD SUCCESS, no new warnings.
- `mvn -pl services/crypto test -Dtest="Watch*"` (49 tests) plus `-Dtest=ChainCursorTest` (3 tests) — 52/52 passing.
- `mvn -pl services/crypto -am test` (full module suite) — 505 tests, 499 passing, 6 failures, all
  pre-existing and unrelated (`ObservationRepositoryIntegrationTest`,
  `ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`,
  `TokenAllowlistRepositoryIntegrationTest` — T08-T11's own work, disclosed but not fixed here per this
  task's own scope) — zero regressions from this task's changes.

## Specification references

- **Task:** T15 — Watch registration API (`spec/crypto-service/tasks.md` #15).
- **Requirements:** R18, R19 (`spec/crypto-service/requirements.md`).
- **Locked decisions:** L6 (`design.md`) — reorg is a first-class transition, `ChainCursor` supports the
  future walk-back; L8 — address validation is mandatory, rejected at the boundary; L15 — module
  boundaries, enforced by `WatchModuleBoundaryTest`.
- **Named tests:** `shouldRegisterWatchAndReturnWatchId`, `shouldUnregisterWatchOnDelete` (`package.md` §8).
- **Contracts:** none of `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`,
  `contracts/events/chain/tx-finalized.v1.schema.json` exist anywhere in this repository yet; this
  task's request/response shapes come from `design.md` §4c's VERBATIM text instead.
