# crypto · T18 · Phase 0 — Repository Understanding

## 1. Architecture summary

Unchanged from T17's own Phase 0 summary: Spring Boot 3.5.4 / Java 21, package-by-feature under
`com.themistra.crypto`, Postgres `chain` schema (Flyway-migrated, currently at `V8`), transactional
outbox (`events.OutboxEvent` → `events.OutboxRelay` → Kafka), least-privilege `crypto_app` DB role. T17
just gave the watcher/quorum pipeline its first real event emissions (`chain.tx.seen`/`confirmed`/
`finalized`, via the new `watch.TxLifecyclePublisher`). T18 adds the fourth and final `chain.tx.*` event,
`chain.tx.reorged`, and is explicitly named by design.md's own package map as living in a **new,
separate top-level package**, `reorg/` — not folded into `watch/` the way T17's publisher was.

## 2. Existing code this task touches

**Already exists:**
- `events.EventTopics` — `"tx-reorged" → "chain.tx.reorged"` already mapped (T04, provisioned ahead of
  need, never called by any task until this one).
- `watch.TxLifecyclePublisher` (T17) — the direct, complete structural precedent: one component,
  aggregate-type/idempotency-key/payload-per-event-type, wrapping `events.OutboxPublisher`, catching
  `DataIntegrityViolationException` as benign. This task's own publishing method almost certainly
  mirrors it, whether added to the same class or a new one in `reorg/`.
- `watch.ChainCursor` — `lastBlock` (forward-only, `advanceTo`), `lastFinalizedBlock` (forward-only,
  `advanceFinalizedTo`, T17), and the T17-added transaction snapshot (`txHash`, `amount`, `fromAddress`,
  `toAddress`, write-once via `recordSeenTransaction`). L6 requires this task to walk `lastBlock`
  **backward** on reorg — every cursor mutator built so far (T16, T17) is deliberately forward-only or
  write-once; this task is the first to need the opposite direction.
- `watch.Watcher` — owns the only existing subscription-based observation pipeline
  (`ObservationSink.onObservation`), the only existing per-`txHash` correlation buffer (pruned
  immediately once every configured provider has answered, T16 Phase 8 Finding 2), and the only
  existing "already evaluated" guards (`evaluatedFacts`, and `QuorumDecisionService`'s own DB-level
  one-decision-ever constraint per `(chain, txHash, factType)`).
- `adapter.ChainAdapter` (VERBATIM, frozen) — `getTx`, `getFinalityStatus`, `subscribeAddress`. **No
  method or return type anywhere in this interface, `TxResult`, or `FinalityStatus` represents a reorg,
  a block hash, or "this block was replaced."** A reorg has no direct signal to query for; it can only
  be *inferred* from a later observation that contradicts an earlier one for the same `(chain, txHash)`.
- `quorum.QuorumDecision`/`QuorumDecisionService` — as established repeatedly in T17's own Phase 0/1/7,
  a persisted `QuorumDecision` stores only the outcome and counts, never the actual agreed value, and
  `QuorumDecisionService.evaluate` can only ever succeed once per `(chain, txHash, factType)` — any
  second attempt throws. This is the same one-shot constraint T17's own finality-polling bug (Phase 10)
  ran into; this task must reckon with it from the start rather than discover it mid-implementation.
- `FakeChainAdapter.simulateReorg(txHash, newResult)` (test fixture) — already named "reorg" from
  T06/T16's own test-infrastructure work, but its actual behavior is generic: re-script a `txHash`'s
  answer and push the new value to every matching live subscription. It is already this codebase's
  general "deliver an observation" mechanism (T16/T17 both reuse it for entirely non-reorg scenarios).
  This strongly suggests the intended reorg-detection signal *is* exactly this: a subsequent
  `onObservation` delivery for an already-resolved `txHash` carrying a value that contradicts what was
  previously agreed.
- T16 Phase 9's own disclosed, unfixed residual limitation is directly relevant: "a correlation created
  from a late/duplicate observation for an already-fully-resolved transaction... is not itself evicted
  by a TTL... not addressed by a second eviction mechanism in this task's own scope" — `Watcher`
  currently just starts a brand-new `TxCorrelation` for such a re-delivery and proceeds through the same
  exactly-once machinery, which would immediately hit `QuorumDecisionService`'s duplicate-decision
  exception (already caught and swallowed as benign, T16). T18 is very plausibly the task meant to give
  this exact pathway real meaning instead of just swallowing it.

**New, per the task statement and design.md's package map:**
- `reorg/ReorgDetector.java` — named explicitly in design.md §6's own package map: "cursor walk-back +
  chain.tx.reorged — L6, R11."
- Whatever event-payload type formalizes `chain.tx.reorged` (a `TxReorgedPayload`-shaped record,
  matching `TxLifecyclePublisher`'s existing three payload records' style).
- A new `ChainCursor` mutator to walk `lastBlock` (and possibly `lastFinalizedBlock`) **backward** —
  every existing mutator is forward-only or write-once; none of them fit a reorg's own requirement.

## 3. Established patterns to follow

- **Outbox emission:** one component wrapping `OutboxPublisher`, idempotency key
  `{chain}:{txHash}:{eventtype}` (L5, extended to this event type by R12's own general rule, even
  though L5/R12 were only in T17's own scoped LOCKED-decision list — L5 itself is a service-wide rule,
  not task-scoped), `watchId` as `aggregateId`/partition key (design.md §4c).
- **Persistence:** JPA entities, Flyway migrations (`V9` would be next if any schema change is needed),
  `ddl-auto=validate`, least-privilege `crypto_app` grants.
- **Testing:** plain JUnit, fixed/mutable `Clock`, scripted `FakeChainAdapter` — `simulateReorg` already
  exists and is presumably the test mechanism this task's own named test
  (`shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg`) uses directly, per its own name.
- **Money/decimal handling, module boundaries, no-placeholder-code, verbatim-log-before-decide** — all
  established, unmodified rules from `agents.md` and prior tasks, apply identically here.

## 4. Testing conventions

Same as T16/T17: unit tests (plain JUnit, fixed/mutable `Clock`, `FakeChainAdapter`) for the detection
and cursor-walk-back logic; no new Testcontainers pattern anticipated beyond what a new migration (if
any) would need to verify via the existing `ChainBaselineMigrationIntegrationTest`/grant-integration-test
style. The task statement's own wording — "Test a scripted reorg after `seen` and after `confirmed`" —
directly implies at least two named scenarios: a reorg discovered after `chain.tx.seen` has already been
emitted, and one discovered after `chain.tx.confirmed` has already been emitted (per package.md's own
"threat #3: reorg after confirmed" framing) — a reorg discovered before `seen` has no distinct life-cycle
consequence to test)

## 5. Known gaps / unknowns

- **No reorg signal exists anywhere in `ChainAdapter`/`TxResult`/`FinalityStatus`.** Detection must be
  inferred entirely from a later observation contradicting an earlier one. Exactly what constitutes
  "invalidated" (R11's own word) — `exists` flipping to `false`, `blockNumber` changing, `confirmations`
  resetting to a lower value than previously agreed, or some combination — is not specified anywhere in
  the spec package and is squarely Phase 1/3's job to resolve, not guessed at here.
- **Where does `ReorgDetector` plug into the existing pipeline?** `Watcher` is the only current consumer
  of `ObservationSink` callbacks. Whether `ReorgDetector` is a second consumer of the same subscriptions,
  a component `Watcher` itself calls when it notices a late/contradicting observation, or something else
  entirely is undetermined. I do not know which.
- **What does "walk the cursor backward" concretely mean for `ChainCursor.lastBlock`/
  `lastFinalizedBlock`?** To what block number — the reorg's own new chain-tip, the last block still
  presumed valid, or something else? `ChainAdapter` has no method returning "the block this reorg
  reverted to." Not resolved here.
- **Interaction with `CONFIRMATIONS`'s and `FINALITY`'s one-shot quorum decisions.** Both are already
  permanently decided (via `QuorumDecisionService`'s DB constraint) the moment they first agree; a reorg
  discovered afterward cannot cause either to be *re-decided* under the current, frozen `quorum_decisions`
  schema. Whether `chain.tx.reorged` is emitted as an independent, additional signal (not itself a new
  quorum decision) is the most likely resolution, mirroring how `chain.provider.degraded` (T10) is
  emitted without ever being a `QuorumDecision` — but this is an inference to verify in Phase 1/3, not
  assumed here.
- **Relationship to T17's own disclosed "late observation for an already-resolved tx" limitation.** As
  noted above, this looks like the natural home for that exact scenario, but I do not know whether the
  spec intends T18 to modify `Watcher`'s own pruned-correlation/duplicate-decision-swallowing behavior,
  or to build an entirely separate detection path that doesn't touch `Watcher` at all.
