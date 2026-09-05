# crypto · T10 · Phase 9 — Review Resolution

**Human Approval gate.** Approved 2026-09-03. Findings from Phase 7 (self-review) and Phase 8 (Kimi
independent review) are consolidated below — Kimi Issues 1, 3, 4, 5, 6 independently confirmed
self-review Findings 1-5 (identical substance). No public API changed, no class renamed, no
refactoring beyond the accepted fixes.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Self-review Finding 1 / Kimi Issue 1 — concurrent first-time calls for the same `(chain, provider)` can race on INSERT, surfacing an uncaught `DataIntegrityViolationException` | **ACCEPTED as a documented, disclosed risk; not code-fixed** | A correct fix requires retrying in a *fresh* transaction (Postgres poisons the current one on a constraint violation — a same-transaction catch-and-retry would not actually work), which is a larger restructuring than this phase's narrow-fix remit. Documented explicitly in `ProviderHealthTracker`'s class Javadoc (`ProviderHealthTracker.java`, new paragraph after the existing disagreement-counter disclosure). |
| 2 | Kimi Issue 2 (new) — no optimistic locking (`@Version`); concurrent read-modify-write cycles can silently lose an update | **ACCEPTED as a documented, disclosed risk; not code-fixed** | The correct fix (an `@Version` column) requires a schema change beyond the frozen brief's grant-only `V4` migration — reopening the frozen brief's authorized files is a bigger decision than this phase should make unilaterally. Documented in the same new Javadoc paragraph as #1. |
| 3 | Self-review Finding 2 / Kimi Issue 3 — the in-memory disagreement counter is not transactional and doesn't roll back with a failed `@Transactional` method | **ACCEPTED (documentation only)** | Added a Javadoc paragraph to `ProviderHealthTracker` explicitly stating the counter increments before the transaction is guaranteed to succeed, self-corrects on the next call, but can transiently disagree with persisted state. |
| 4 | Self-review Finding 4 / Kimi Issue 4 — `ProviderHealth.create` has no null-guards of its own | **ACCEPTED** | Added `Objects.requireNonNull(chain, "chain")`, `Objects.requireNonNull(provider, "provider")`, `Objects.requireNonNull(now, "now")` at the top of `create` (`ProviderHealth.java`). |
| 5 | Self-review Finding 5 / Kimi Issue 5 — unescaped `:` separator in `aggregateId`/idempotency key could let two distinct `(chain, provider)` pairs collide | **ACCEPTED** | Added a private `requireNoColon(String, String)` helper in `ProviderDegradedPublisher`, called for both `chain` and `provider` at the top of `publish`, throwing `IllegalArgumentException` before any string concatenation happens. |
| 6 | Self-review Finding 3 / Kimi Issue 6 — `create`'s `now` parameter is always immediately overwritten by the tracker's very next mutator call | **ACCEPTED (documentation only)** | Added a Javadoc note to `ProviderHealth.create` stating the parameter is transient and explaining why (every current caller immediately follows with a mutator call). No API change (removing the parameter was considered and rejected — see #7). |
| 7 | Kimi Issue 7 (new) — `ProviderHealthTracker`'s three public methods return `void`, giving callers no visibility into whether a transition/event occurred | **REJECTED** | The suggested fix (returning a result object or the entity) changes public method signatures. This phase's own directive explicitly forbids that ("do not change public APIs"). No caller exists yet in this task's own scope to need this visibility; flagged as a legitimate future enhancement if/when a real caller does. |
| 8 | Kimi Issue 8 (new) — the `eventType` value's convention (matching the topic name) is not documented, so a future refactor could change one without the other | **ACCEPTED** | Added a Javadoc paragraph to `ProviderDegradedPublisher` explicitly stating `eventType` is deliberately pinned to `"chain.provider.degraded"`, matching the topic name and the one existing precedent (`OutboxPublisherTest`'s `"chain.tx.seen"` example), and that this value plus the `Payload` shape are what task 23 should formalize into a contract. |

## Summary

2 accepted with code changes (4, 5), 3 accepted as documentation-only with no code change (3, 6, 8), 2
accepted as documented/disclosed risks with no code fix — both requiring changes beyond this phase's
narrow-fix scope (1: a transaction-boundary restructuring; 2: a schema change reopening the frozen
brief) — and 1 rejected outright for requiring a forbidden public-API change (7).

`mvn -pl services/crypto compile` succeeds cleanly after all changes, with zero new warnings.

Files changed in this phase: `ProviderHealth.java`, `ProviderDegradedPublisher.java`,
`ProviderHealthTracker.java` (Javadoc only for the last one). No file outside
`services/crypto/src/main/java/com/themistra/crypto/provider/` was touched. No public method signature
changed; no class renamed.
