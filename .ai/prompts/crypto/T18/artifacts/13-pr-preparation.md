# crypto · T18 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Implement crypto-service reorg detector (T18)
```

## Commit message

```
Implement crypto-service reorg detector (T18)

Add ReorgDetector and wire it into Watcher's existing finality-poll
tick to defend against package.md's own threat #3: a transaction
already treated as seen/confirmed/finalized turning out to have been
reorged off the canonical chain.

The originally-designed detection mechanism (waiting for a provider's
subscribeAddress push to re-deliver an already-seen txHash with a
contradicting answer) was verified, by reading EthereumAdapter and
TronAdapter directly, to be structurally unreachable - both adapters
scan strictly forward and never re-report a transaction they already
found. Replaced with a synchronous ChainAdapter.getTx pull, re-checked
every finality-poll tick against whatever transaction the watch's own
ChainCursor currently holds, independent of finality-poll state - so a
reorg is caught whether it's discovered after seen, after confirmed,
or after finalized.

On a fresh 2-of-3 majority of exists=false, the affected ChainCursor
is walked back to a full reset (no forward-derived state survives, L6)
and chain.tx.reorged is emitted through the same outbox pattern the
three existing chain.tx.* events already use. The event is published
before the cursor is invalidated, so a persistence failure after a
successful publish self-heals on the very next tick rather than
silently losing the event.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created:**
- `services/crypto/src/main/java/com/themistra/crypto/reorg/ReorgDetector.java`
- `services/crypto/src/test/java/com/themistra/crypto/reorg/ReorgDetectorTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/reorg/ReorgModuleBoundaryTest.java`

**Modified:**
- `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java` — new `checkForReorg()` step at the start of every `pollFinality()` tick; per-call exception guards around both halves of that method; new `toRawJson(TxResult)` helper.
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java` — new `invalidate(Instant now)` mutator.
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java` — threads `ReorgDetector` into `Watcher`'s constructor.
- `services/crypto/src/test/java/com/themistra/crypto/watch/ChainCursorTest.java`, `WatcherTest.java`, `WatcherRegistryTest.java`, `WatchModuleBoundaryTest.java` — new T18 scenarios plus constructor-signature ripple fixes.

10 files changed (3 created, 7 modified), +979/-18 lines. No new migration — no new tables, columns, or grants (an additive schema change was considered and rejected as out of scope for the disclosed cross-thread-race mitigation; the cursor's existing nullable columns already cover this task's own needs).

## Summary

Gives the platform its defense against threat #3 from `package.md`'s own threat model. `Watcher` now
re-verifies, on every scheduled finality-poll tick, that the transaction it has already recorded as seen
still exists on-chain — a synchronous pull via `ChainAdapter.getTx`, independent of each adapter's own
forward-only scanning position and independent of whether finality has already been reached. A fresh
2-of-3 majority of `exists=false` triggers `ReorgDetector.reorg`, which emits `chain.tx.reorged` with the
same deterministic idempotency-key format every other `chain.tx.*` event already uses, and
`ChainCursor.invalidate` resets every forward-derived field to its pre-observation state.

The design went through a substantial correction at Phase 3: the original push-based detection mechanism
was verified, by reading `EthereumAdapter`/`TronAdapter` source directly rather than assuming their
contract, to be structurally incapable of ever firing — both adapters only scan forward and never
re-report an already-found transaction. The pull-based redesign this forced also resolved a
race-condition finding for free (both concerns now run sequentially on the same thread) and naturally
extended coverage to reorgs discovered after finalization, which package.md's own threat framing treats
as the highest-severity case.

Two limitations remain deliberately disclosed, not fixed, as out of this task's proportionate scope: a
narrow cross-thread race on `ChainCursor` between this task's own reorg-check and T17's `handleSeenIfAgreed`
(mitigated by a fresh re-check immediately before acting, not fully closed — would need a `@Version`
optimistic-locking schema change), and no recovery path if a reorged transaction is later re-included on
the canonical chain (the deterministic idempotency key and the one-decision-ever quorum constraint both
permit only one occurrence of each lifecycle event, ever).

## Testing performed

- `mvn -pl services/crypto test-compile` — clean.
- `mvn -pl services/crypto test -Dtest=WatcherTest,WatcherRegistryTest,ChainCursorTest,ProviderSetTest,WatchModuleBoundaryTest,TxLifecyclePublisherTest,ReorgModuleBoundaryTest,ReorgDetectorTest` — 111/111 pass.
- `mvn -pl services/crypto -am test` (full module regression) — 619 tests, 4 failures, all pre-existing and unrelated to this task (a JSON-spacing assertion, three DB-permission-vs-Hibernate-exception-wrapping mismatches, all disclosed since T17). Zero regressions.
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`: `artifacts/12-specification-verification.md` — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 18 ("Reorg detector").
- **Requirements:** R11 (a previously observed transaction invalidated by a reorg walks the cursor backward and emits `chain.tx.reorged`).
- **LOCKED decisions:** L6 (reorg is a first-class transition; no forward-derived state survives it). L5/R12 (service-wide, unmodified — deterministic idempotency key format).
