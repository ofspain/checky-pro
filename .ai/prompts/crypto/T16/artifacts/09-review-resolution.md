# crypto · T16 · Phase 9 — Review Resolution

**Human Approval gate.** Approved 2026-09-07, in two rounds: the initial 10-finding packet, and a
follow-up correction to Finding 7's own scope discovered while implementing it (below). Findings from
Phase 7 (self-review) and Phase 8 (Kimi independent review) are consolidated below — Kimi Findings 1, 2,
3 independently confirmed self-review Findings 1, 2, 3 respectively.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Self-review Finding 1 / Kimi Finding 1 — `Watcher.stop()` leaks its sweep task and Micrometer gauge | **ACCEPTED, redesigned** | `Watcher` now owns a private, single-thread virtual-thread scheduler (created in its own constructor) instead of receiving a shared one from `WatcherRegistry` — `stop()` simply calls `sweepScheduler.shutdownNow()`, and removes the gauge via `meterRegistry.remove(...)`. This redesign also resolves Finding 9 (below) as a side effect. |
| 2 | Self-review Finding 2 / Kimi Finding 2 — `correlations`/`evaluatedFacts` never pruned | **ACCEPTED**, with Kimi's more precise trigger | Both are pruned once `correlation.answers().size() == adapters.size()` (every configured provider has answered), regardless of whether every fact reached a quorum decision — no further answer can ever arrive for that transaction once that's true. **Disclosed residual limitation**: a correlation created from a late/duplicate observation for an already-fully-resolved transaction (rare — none of the other providers re-report an already-processed tx) is not itself evicted by a TTL; it lingers, bounded in practice, not addressed by a second eviction mechanism in this task's own scope. |
| 3 | Self-review Finding 3 / Kimi Finding 3 — 3-way-split majority miscount | **ACCEPTED** | `recordDisagreementsIfAny` now requires `majorityCount >= 2` before flagging anyone; a genuine 3-way `HELD` split flags no one (relies on `HeldFactAlerter`, already invoked for every `HELD` outcome, as the sole signal for that case). |
| 4 | Kimi Finding 4 — no catch-all in `Watcher.handleObservation`; verified `EthereumAdapter.pollOnce` has no guard of its own | **ACCEPTED — verified directly before implementing** | Confirmed by reading `EthereumAdapter.pollOnce` directly: no try/catch, unlike `TronAdapter`'s already-guarded `pollOnceUnguarded`. Added a broad `catch (RuntimeException e)` around `handleObservation`'s body, logging and returning rather than propagating — the watcher-level fix Kimi itself preferred, since it also protects any future adapter implementation that might lack its own guard. |
| 5 | Kimi Finding 5 — gauge tag collision for two watches sharing `(chain, address)` | **ACCEPTED** | Added `watchId` as a third gauge tag. |
| 6 | Kimi Finding 6 — no graceful shutdown for `WatcherRegistry`; claimed non-daemon threads could hang the JVM | **ACCEPTED, factual correction** | Verified directly (a small standalone program) that `Thread.ofVirtual().factory()`-backed threads are daemon by default — the JVM would *not* hang for the stated reason. The recommendation (add a shutdown path) was kept anyway, for the real reasons that remain: a clean stop avoids watchers writing during a closing connection pool, and releasing shard locks explicitly on a planned shutdown lets other replicas take over immediately rather than waiting out `lockAtMostFor`. Added `WatcherRegistry.shutdown()` (`@PreDestroy`): stops every running `Watcher`, then unlocks every held shard lock. |
| 7 | Kimi Finding 7 — silent, permanent non-function if a chain's configured provider count isn't exactly 3 | **ACCEPTED, relocated during implementation** | Initially implemented in `ProviderProperties`' own compact constructor (T03's general-purpose config class) — this broke many *unrelated* existing tests in `ProviderPropertiesTest`/`TronAdapterConfigTest`/`EthereumAdapterConfigTest` that legitimately use 1-2 providers to test credential resolution, timeout wiring, and adapter counting, none of which care about quorum arithmetic. Recognized this as a sign the validation was in the wrong place mid-implementation, reverted it, and instead added the exactly-3 check to `ProviderSet`'s own constructor — the actual, sole consumer that needs it. All the incidental test-ripple this caused was reverted along with it. The local-profile `application.properties` fixture was still updated to 3 providers per chain (ETHEREUM was 2, TRON was 1) — `ProviderSet` is a real, eagerly-constructed bean in the actual application context, so it still needed to satisfy its own new invariant regardless of where the check lives. |
| 8 | Kimi Finding 8 — `recordDisagreement` counted per disagreeing *fact*, not per disagreeing *transaction* | **ACCEPTED** | `TxCorrelation` now tracks a `disagreementFlagged` set; `recordDisagreementsIfAny` calls `recordDisagreement` at most once per provider per transaction, regardless of how many of that transaction's facts diverged. |
| 9 | Kimi Finding 9 — shared single-thread sweep scheduler as a cross-watcher bottleneck | **ACCEPTED, resolved by Finding 1's redesign** | Each `Watcher` now has its own scheduler (see #1) — there is no shared scheduler left to bottleneck. |
| 10 | Kimi Finding 10 — `sweepStaleCorrelations` repeatedly calls `recordUnhealthy` for an already-flagged lagging provider | **ACCEPTED** | `TxCorrelation` tracks a `laggingFlagged` set; a given provider is flagged lagging on a given correlation at most once. |

## Summary

All 10 findings accepted; 3 were independently confirmed by both self-review and Kimi (1, 2, 3). Two
required verification before being trusted rather than accepted on the stated premise: Finding 4
(confirmed true by reading `EthereumAdapter.pollOnce` directly) and Finding 6 (the daemon-thread claim
was factually wrong, verified by direct execution, though the underlying recommendation was kept for
other real reasons). Finding 7's fix was relocated mid-implementation from `ProviderProperties` to
`ProviderSet` after the original placement's test ripple revealed it was scoped to the wrong class —
correctness of the underlying fix was never in question, only where it belongs.

`mvn -pl services/crypto compile` and `test-compile` succeed cleanly. `mvn -pl services/crypto -am test`
(full module regression): 505 tests, 6 failures — the same pre-existing, disclosed set unrelated to this
task, zero regressions.

Files changed in this phase: `watch/Watcher.java` (substantial rewrite), `watch/WatcherRegistry.java`
(scheduler removed, `@PreDestroy` added), `adapter/ProviderSet.java` (exactly-3 validation added),
`application.properties` (3 providers per chain). No public method signature changed in a way that
breaks any external contract; `Watcher`'s own (package-private) constructor signature changed, but it
has exactly one caller (`WatcherRegistry`), already updated.
