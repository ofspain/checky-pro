# crypto · T17 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Implement crypto-service seen/confirmed/finalized event emission (T17)
```

## Commit message

```
Implement crypto-service seen/confirmed/finalized event emission (T17)

Add TxLifecyclePublisher and wire it into Watcher's existing quorum
hooks so chain.tx.seen, chain.tx.confirmed, and chain.tx.finalized are
finally emitted from the pipeline T06-T16 built but never called
downstream from. Adds the previously-missing finality-polling path -
nothing called ChainAdapter.getFinalityStatus/FinalityPolicy before
this task - so FINALITY can itself be quorum-decided under the same
2-of-3 discipline as every other fact before chain.tx.finalized is
ever emitted.

A durable per-watch transaction snapshot (four new nullable
ChainCursor columns) replaces an initially-planned in-memory-only
cache, so the finalized event's amount/fromAddress/toAddress survive
independently of Watcher's own pruned-after-first-batch correlation
buffer.

Writing a realistic finality-poll test during Phase 10 surfaced a
critical defect neither self-review nor independent review had
caught: QuorumDecisionService.evaluate can only ever succeed once per
fact, but the finality poll was calling it unconditionally on every
tick - meaning the first, almost-never-final tick would permanently
decide AGREED false and make chain.tx.finalized unreachable in
practice. Fixed by checking the answers' majority locally before ever
persisting a decision, so evaluate() is only called once finality is
actually reached.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created:**
- `services/crypto/src/main/java/com/themistra/crypto/watch/TxLifecyclePublisher.java`
- `services/crypto/src/main/resources/db/migration/V8__crypto_chain_cursors_tx_snapshot.sql`
- `services/crypto/src/test/java/com/themistra/crypto/watch/TxLifecyclePublisherTest.java`

**Modified:**
- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java` — seen/confirmed publish hooks, finality-poll mechanism, fail-fast `FinalityPolicy` validation, `toRawJson` now embeds `txHash`.
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java` — threads `TxLifecyclePublisher`/`List<FinalityPolicy>`/`ObjectMapper` into `Watcher`'s constructor.
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java` — durable transaction snapshot (`recordSeenTransaction`) and `lastFinalizedBlock` advancement (`advanceFinalizedTo`).
- `services/crypto/src/main/java/com/themistra/crypto/common/config/WatcherProperties.java` — new `finalityPollIntervalMs` field.
- `services/crypto/src/main/resources/application.properties` — new `themistra.crypto.watcher.finality-poll-interval-ms` key.
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` — Flyway version-list assertion extended to include `V8`.
- `services/crypto/src/test/java/com/themistra/crypto/watch/ChainCursorTest.java`, `WatcherTest.java`, `WatcherRegistryTest.java`, `WatchModuleBoundaryTest.java` — new T17 scenarios plus constructor-signature ripple fixes.

13 files changed (3 created, 10 modified), +1623/-33 lines.

## Summary

Gives the quorum/watcher pipeline its first outward-facing signal: `Watcher` now publishes
`chain.tx.seen` when `EXISTENCE` first quorum-agrees `true`, `chain.tx.confirmed` (once, per the
frozen brief's resolution of R9's schema-incompatible "repeated" framing) when `CONFIRMATIONS` agrees,
and `chain.tx.finalized` once a new finality-poll loop's `FINALITY` quorum decision agrees `true`. Every
event's idempotency key is the LOCKED, deterministic `{chain}:{txHash}:{eventtype}` format (L5/R12),
preserved literally even where doing so means at most one event fires system-wide if two watches happen
to share an address — a disclosed consequence of respecting L5 exactly rather than silently widening it.

The design went through substantial correction across this task's own review gates: Phase 3's design
challenge identified that the idempotency-key format conflicts with the partition-key design for watches
sharing an address, and that the originally-proposed in-memory payload cache would not survive a
restart; Phase 8's independent review caught a real bug where a stale cursor snapshot could cause a
`finalized` event to cite the wrong transaction; and Phase 10's own test-writing surfaced the most
significant defect — the finality-poll's one-shot quorum evaluation was being attempted on every tick
rather than gated on a local majority check, which would have made `chain.tx.finalized` effectively
unreachable in production. All three were fixed with the user's explicit approval before this PR.

Two limitations remain deliberately disclosed, not fixed, as out of this task's proportionate scope: the
pending-finality tracking is in-memory only and does not survive a process restart between a
transaction being seen and reaching finality, and a watch that observes more than one distinct
transaction only tracks the first one's finality (a guard prevents this from producing a misleading
event, but does not make the second transaction trackable).

## Testing performed

- `mvn -pl services/crypto test-compile` — clean.
- `mvn -pl services/crypto test -Dtest=WatcherTest,WatcherRegistryTest,ChainCursorTest,ProviderSetTest,WatchModuleBoundaryTest,TxLifecyclePublisherTest` — 77/77 pass.
- `mvn -pl services/crypto -am test` (full module regression) — 585 tests, 6 failures, all pre-existing and unrelated to this task (a JSON-spacing assertion, three DB-permission-vs-Hibernate-exception-wrapping mismatches, one pre-existing `TokenAllowlistRepositoryIntegrationTest` gap, all disclosed since T16). Zero regressions.
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`: `artifacts/12-specification-verification.md` — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 17 ("Seen/confirmed/finalized emission").
- **Requirements:** R8 (first quorum-agreed sighting emits `chain.tx.seen`), R9 (confirmations gained emits `chain.tx.confirmed` with the count — resolved as one-shot, human-approved), R10 (finality emits `chain.tx.finalized`, never before), R12 (deterministic idempotency key on every emitted event).
- **LOCKED decisions:** L5 (`chain:txhash:eventtype` idempotency key format, preserved literally).
