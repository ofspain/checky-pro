# crypto · T16 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Implement crypto-service watcher layer (T16)
```

## Commit message

```
Implement crypto-service watcher layer (T16)

Add Watcher, a per-watch multi-provider correlation buffer driven by
virtual-thread subscriptions, giving the existing adapter/observation-log/
quorum/provider-health pipeline (T06-T10) its first real caller. Extend
ObservationSink to carry provider identity and raw response JSON so a
transaction can be correlated across providers without depending on the
VERBATIM-frozen TxResult shape. Add WatcherRegistry for ShedLock-sharded
multi-replica assignment, so no watched address is driven by more than one
replica and a lost shard lease is picked up within one reconciliation
interval (O5, author-approved).

A fact is only ever quorum-evaluated once exactly 3 real per-provider
answers exist for it - never fewer, and never with a fabricated stand-in -
correcting an initially-assumed 2-of-3-partial-fanout scenario against
QuorumEvaluator's actual hard exactly-3 requirement, found by reading its
source during design.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ProviderSet.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/ShedLockConfig.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/WatcherProperties.java`
- `services/crypto/src/main/resources/db/migration/V7__crypto_app_watcher_grants.sql`
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherRegistryTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/adapter/ProviderSetTest.java`

**Modified:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ObservationSink.java` — signature extended to carry provider identity and raw JSON.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java`, `EthereumAdapterConfig.java` — `providerName()` accessor, raw-JSON capture, updated sink call site.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapter.java`, `TronAdapterConfig.java` — mirrors the Ethereum adapter changes.
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealthTracker.java` — `crypto.provider.disagreements` counter added to `recordDisagreement`.
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java`, `ChainCursorRepository.java`, `WatchRepository.java` — `advanceTo`, `findByWatchId`, `findByStatus`.
- `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` — Javadoc updated to reflect `WatcherRegistry`'s arrival.
- `services/crypto/src/main/resources/application.properties` — new `themistra.crypto.watcher.*` keys; provider fixtures expanded to exactly 3 per chain.
- `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapter.java`, `FakeChainAdapterTest.java`, `eth/EthereumAdapterConfigTest.java`, `eth/EthereumAdapterTest.java`, `tron/TronAdapterConfigTest.java`, `tron/TronAdapterTest.java` — ripple fixes for the `ObservationSink` signature change and the new `ObjectMapper` constructor parameter.
- `services/crypto/src/test/java/com/themistra/crypto/provider/ProviderHealthTrackerTest.java` — real `SimpleMeterRegistry` for the new counter.
- `services/crypto/src/test/java/com/themistra/crypto/watch/ChainBaselineMigrationIntegrationTest.java` — `V7` added to the expected Flyway version list; `shedlock` removed from `UNGRANTED_TABLES`.
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchRepositoryIntegrationTest.java` — `chain_cursors` grant test updated for the new `UPDATE` grant.
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchModuleBoundaryTest.java` — redesigned to an explicit per-package-prefix allowed-imports map covering `watch/`'s new files.
- `services/crypto/src/test/java/com/themistra/crypto/watch/ChainCursorTest.java` — `advanceTo` forward-only coverage.

31 files changed (9 created, 22 modified), +1879/-93 lines.

## Summary

Gives the fully-built-but-never-called T06-T10 pipeline its first real caller. `Watcher` subscribes to
every configured provider for a watch's `(chain, address)`, correlates their answers per transaction hash
in a process-local buffer, logs each provider's raw response verbatim before any quorum decision, and
evaluates each of `EXISTENCE`/`AMOUNT`/`TOKEN`/`CONFIRMATIONS` at most once, only once exactly 3 real
answers exist. A non-responding provider is marked lagging (not forced through with 2, never with a
fabricated third answer); a provider whose answer diverges from an agreed fact is marked disagreeing, at
most once per transaction. `WatcherRegistry` uses ShedLock's programmatic `LockProvider` API to
deterministically shard watched addresses across replicas (`Math.floorMod(watchId.hashCode(), shardCount)`),
renewing each held shard lock on every reconciliation tick and releasing it cleanly on `@PreDestroy`
shutdown so another replica can take over immediately rather than waiting out `lockAtMostFor`.

The originally-designed "subscribe for discovery, then `getTx` fan-out" approach was abandoned during
design (Phase 3/4) because `getTx`'s VERBATIM-frozen `TxResult` return type cannot carry raw response
JSON, which R4/L3's verbatim-log-first requirement needs; `ObservationSink` (confirmed not VERBATIM) was
extended instead, and the design shifted to a pure `subscribeAddress`-fed correlation buffer.

## Testing performed

- `mvn -pl services/crypto test-compile` — clean.
- `mvn -pl services/crypto test -Dtest=WatcherTest,ProviderSetTest,WatcherRegistryTest,ChainCursorTest` — 40/40 pass (19 + 4 + 8 + 5, after both the Phase 10 and Phase 11 test-generation rounds).
- `mvn -pl services/crypto -am test` (full module regression) — 538 tests, 6 failures, all pre-existing and unrelated to this task (disclosed in Phases 9-11: a JSON-spacing assertion, three DB-permission-vs-Hibernate-exception-wrapping mismatches, one pre-existing `TokenAllowlistRepositoryIntegrationTest` gap). Zero regressions.
- `WatcherRegistryTest` exercises real ShedLock shard exclusivity against a live Testcontainers Postgres instance (not a mocked `LockProvider`) for its core acquisition/renewal/shutdown scenarios, plus mocked-`LockProvider` variants for the lock-loss and multi-shard cases that don't need real DB timing.
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`: `artifacts/12-specification-verification.md` — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 16 ("Watcher layer").
- **Requirements:** R1 (2-of-3 quorum fan-out), R4 (verbatim-first observation log), R5 (provider health/degradation signaling).
- **LOCKED decisions:** L1 (2-of-3 quorum, not tunable), L2 (disagreement → `HELD`, untouched), L3 (log before decide), L14 (no `ChainAdapter`-implementation-specific code path), L15 (module boundaries).
- **Design decisions:** O2/Q5 (virtual-thread-driven watcher transport & concurrency), O5 (multi-replica watcher assignment via ShedLock-leased shards — author-approved).
- **`agents.md`:** per-chain watcher lag and provider-disagreement rate as paged metrics (`agents.md:54`).
