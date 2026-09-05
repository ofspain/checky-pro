# crypto · T10 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS** (`artifacts/12-specification-verification.md`). Proceeding to prepare T10
for merge. Branches off `main`; `main` remains deployable throughout — no commit in this task touches
anything outside `services/crypto/` (plus this task's own `.ai/prompts/crypto/T10/` artifacts).

## Commit title

```
crypto: add provider health tracking and degraded-event emission (T10)
```

## Commit message

```
crypto: add provider health tracking and degraded-event emission (T10)

Implement ProviderHealth (this service's first update-in-place entity -
one row per (chain, provider), unlike every append-only entity built so
far) and ProviderHealthTracker, which records direct unhealthy/lagging
signals and consecutive-disagreement counts, transitioning a provider to
unhealthy and emitting chain.provider.degraded exactly once per episode
via ProviderDegradedPublisher/OutboxPublisher.

Closes a real gap in T02's own migration set: crypto_app had no grant at
all on provider_health (V2 only covered the three append-only tables).
V4 adds INSERT/SELECT/UPDATE, no DELETE - a health row is transitioned,
never removed.

The idempotency key embeds both the transition instant and a random UUID
("{chain}:{provider}:degraded:{instant}:{uuid}"), since - unlike a
one-time chain.tx.* transition - a provider can degrade, recover, and
degrade again, and a fixed key would collide with itself on the second
episode. The disagreement counter is an explicitly disclosed, process-
local, non-persisted signal: it resets on restart and isn't coordinated
across replicas, an accepted tradeoff for an operational, not
correctness-critical, signal (T09's quorum evaluation remains the actual
source of truth for AGREED/HELD).

Kimi design/independent/test review findings (Phases 3, 8, 11) were
triaged and folded in - most notably a corrected @ConfigurationProperties
prefix (themistra.crypto.*, matching the established convention), null
guards, a colon guard preventing aggregate-key ambiguity, and a missing
module-boundary test the frozen brief had already required. Two
concurrency races (a first-write INSERT race; a lost-update race with no
@Version column) are deliberately left disclosed and unfixed - both
would require changes beyond this task's own narrow scope (a
transaction-boundary restructuring; a schema change reopening the frozen
brief) - documented extensively in ProviderHealthTracker's own Javadoc
rather than silently accepted.

Investigating the payload-serialization test review finding surfaced a
real, previously-latent Jackson gotcha: a bare `new ObjectMapper()` (the
existing OutboxPublisherTest pattern) throws on java.time.Instant, and
even with modules registered, serializes it as epoch seconds rather than
ISO-8601 unless WRITE_DATES_AS_TIMESTAMPS is explicitly disabled -
production is unaffected (Spring Boot's autoconfigured ObjectMapper
already disables it), but this is the first task to put a java.time
value through OutboxPublisher's payload path, so the gap was previously
untested. Locked down with an explicit test.

Testing gated on Docker (ProviderHealthRepositoryIntegrationTest) has
not executed in this environment - a pre-existing limitation already
affecting T02/T04/T08/T09's own integration tests, disclosed throughout
this task's artifacts, not a defect in this change.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Main:**
- `services/crypto/src/main/resources/db/migration/V4__crypto_app_provider_health_grant.sql` — new
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderHealthProperties.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealth.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealthRepository.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/provider/DegradationReason.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderDegradedPublisher.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealthTracker.java` — new
- `services/crypto/src/main/resources/application.properties` — modified (adds
  `themistra.crypto.provider-health.disagreement-threshold=3`)

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/common/config/ProviderHealthPropertiesTest.java` — new
- `services/crypto/src/test/java/com/themistra/crypto/provider/ProviderHealthTest.java` — new, extended at Phase 11 (+1)
- `services/crypto/src/test/java/com/themistra/crypto/provider/ProviderDegradedPublisherTest.java` — new, extended at Phase 11 (+1, +1 enhanced)
- `services/crypto/src/test/java/com/themistra/crypto/provider/ProviderHealthTrackerTest.java` — new, extended at Phase 11 (+4)
- `services/crypto/src/test/java/com/themistra/crypto/provider/ProviderHealthRepositoryIntegrationTest.java` — new, extended at Phase 11 (+1)
- `services/crypto/src/test/java/com/themistra/crypto/provider/ProviderModuleBoundaryTest.java` — new (Phase 11)

**Pipeline artifacts:**
- `.ai/prompts/crypto/T10/artifacts/00-repository-understanding.md` through `13-pr-preparation.md` — all 14 phase artifacts

## Summary

T10 adds the operational counterpart to T09's quorum arbitration: per-provider health tracking and a
`chain.provider.degraded` event, so ops can see which provider is degrading without that provider's
disagreements ever influencing quorum's own AGREED/HELD determination. It closes a genuine gap in T02's
migration set (a missing grant), introduces this service's first update-in-place entity, and is the
first task to actually exercise `OutboxPublisher`'s payload-serialization path with a `java.time` value
— an investigation that surfaced and locked down a real, previously-latent Jackson configuration
gotcha. Two concurrency races are deliberately disclosed rather than fixed, each requiring a larger
change (transaction restructuring; a schema-changing `@Version` column) than this task's own scope
allows without reopening its frozen brief.

## Testing performed

- `mvn -pl services/crypto test-compile` — BUILD SUCCESS, no new warnings.
- `mvn -pl services/crypto test -Dtest=ProviderHealthPropertiesTest,ProviderHealthTest,ProviderDegradedPublisherTest,ProviderHealthTrackerTest,ProviderModuleBoundaryTest` — 32/32 passing.
- `mvn -pl services/crypto -am test` (full module suite) — 311 tests, 304 passing, 7 errors, all
  `IllegalState: … Docker environment …` (6 pre-existing from T02/T04/T08/T09's own Testcontainers
  integration tests, 1 new from this task's own `ProviderHealthRepositoryIntegrationTest`) — zero
  genuine failures.
- Docker unavailable throughout this session — `ProviderHealthRepositoryIntegrationTest` compiles
  cleanly and mirrors the already-established, previously-proven-once-Docker-is-available pattern
  (extended to prove `UPDATE` succeeds — the first such proof in this service's test history, the
  inverse of every prior append-only entity's own integration test), but has not itself executed
  against a real Postgres in this environment.

## Specification references

- **Task:** T10 — Provider health + degraded (`spec/crypto-service/tasks.md` #10).
- **Requirement:** R5 (`spec/crypto-service/requirements.md:11`).
- **Locked decisions:** L5 (`spec/crypto-service/design.md:9`) — deterministic idempotency key on every
  event; L15 (`design.md:19`) — module boundaries, no cross-feature-module entity import.
- **Named test:** `shouldEmitProviderDegradedWhenAProviderIsUnhealthy` (`package.md` §8).
- **Contracts:** none of `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`,
  `contracts/events/chain/tx-finalized.v1.schema.json` are touched by this task —
  `contracts/events/chain/` does not exist anywhere in this repository yet; authoring the five event
  schemas (including `provider-degraded.v1.schema.json`) is task 23's explicit, separately-scheduled
  scope (confirmed directly against `tasks.md`), not this one's. This task's `ProviderDegradedPublisher.Payload`
  is the concrete implementation task 23 will formalize.
