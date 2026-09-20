# crypto · T17 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` is a Spring Boot 3.5.4 / Java 21 module (`services/crypto`) under the `checky-pro`
parent. Package-by-feature under `com.themistra.crypto`: `adapter` (chain integrations + `ChainAdapter`
VERBATIM interface), `observation` (verbatim provider-response log), `quorum` (2-of-3 comparison +
persisted decisions), `provider` (health tracking + degradation), `finality` (per-chain finality
policies, T14), `watch` (watch registration + `Watcher`/`WatcherRegistry`, T15/T16), `events` (outbox +
Kafka relay), `common` (shared config/security/error-handling). Persistence is Postgres (`chain` schema,
Flyway-migrated, currently at `V7`), JPA/Hibernate entities with `ddl-auto=validate`. Events go through
a transactional outbox (`events.OutboxEvent` → `events.OutboxRelay` → Kafka) — domain code never
produces to Kafka directly. Security: internal endpoints are an OAuth2 resource server validating a
service-to-service JWT against the Auth JWKS; `crypto_app` is a least-privilege DB role with
per-table, per-verb grants added incrementally by migration.

## 2. Existing code this task touches

**Already exists, to be called/extended, not created from scratch:**
- `events.EventTopics` — `"tx-seen"→"chain.tx.seen"`, `"tx-confirmed"→"chain.tx.confirmed"`,
  `"tx-finalized"→"chain.tx.finalized"` are already mapped (provisioned ahead of need, alongside
  `tx-reorged` for task 18 and `provider` for T10) — T17 is the first task to actually emit through
  any of the first three.
- `events.OutboxPublisher` — the sole sanctioned emission path (`publish(aggregateType, aggregateId,
  eventType, idempotencyKey, payload)`), already used by `provider.ProviderDegradedPublisher` (T10) as
  the direct precedent this task should follow: a dedicated `XPublisher` component per event family,
  called from wherever the domain transition is detected.
- `quorum.QuorumDecisionService.evaluate(...)` — already called once per fact by `watch.Watcher`
  (T16), for `EXISTENCE`/`AMOUNT`/`TOKEN`/`CONFIRMATIONS`. **Important gap:** the persisted
  `quorum.QuorumDecision` entity carries only `chain`/`txHash`/`factType`/`outcome`/`agreeingCount`/
  `providerCount`/`decidedAt` — it does **not** store the actual agreed value (no amount, no
  confirmation count, no boolean). The only place the real value is available is the caller's own
  `List<ProviderAnswer<T>>` at the `Watcher.evaluateFact` call site. Any event payload needing a real
  value (`amount`, `confirmations`) cannot be built by re-querying `QuorumDecision` alone.
- `finality.FinalityPolicy` (+ `EthereumFinalityPolicy`/`TronFinalityPolicy`, T14) — pure, stateless
  `isFinal(FinalityStatus)`. **Never yet called by any production code.** No dispatcher exists that
  picks the right policy for a chain (its own Javadoc: "for a future dispatcher... none exists yet").
- `ChainAdapter.getFinalityStatus(String txHash)` — VERBATIM interface method, implemented by
  `EthereumAdapter`/`TronAdapter`, but **never yet called by any production code**. `Watcher` (T16)
  only calls `subscribeAddress`. Obtaining finality status requires a new poll/call path this task
  must add.
- `watch.ChainCursor.lastFinalizedBlock` — a `Long` column, always `null` today. Its own class Javadoc
  (written at T15) states "Task 16's watcher is expected to overwrite both this field and
  `lastFinalizedBlock` with real values the first time it processes this watch" — **T16 did not do
  this** (explicitly out of T16's own approved scope: "Quorum-evaluating FINALITY... deferred"). This
  field is real, migrated, and currently unused/always-null.
- `watch.Watch` — already carries `invoiceUuid`, `chain`, `address`, `tokenContractAddress`,
  `expectedAmount`, `watchId`. The `tx-finalized` event schema (design.md §4c, VERBATIM) requires
  `invoiceUuid`, `fromAddress`, `toAddress`, `tokenContractAddress`, `amount` — `fromAddress`/
  `toAddress` are not on `Watch` itself; they exist on `adapter.model.TxResult` (per-observation, not
  persisted anywhere queryable after the fact).
- `provider.AddressPoisoningDetector` (T13) — the `tx-finalized` schema has an `addressPoisoningFlag`
  boolean field; this task will need to source that value from the existing detector.
- No `TxLifecycle`/`SEEN`/`CONFIRMED`/`FINALIZED`-state entity exists anywhere in the schema or code.
  `watch.WatchStatus` (`REGISTERED`/`UNREGISTERED`/`EXPIRED`) is the *watch's* registration lifecycle,
  unrelated to a transaction's seen/confirmed/finalized progression. Whether R9 ("a `SEEN` transaction
  gains confirmations") requires new persisted state, or can be derived from existing rows
  (`quorum_decisions` rows for `EXISTENCE`/`CONFIRMATIONS` already exist once decided), is a Phase 1/3
  design question, not resolved by anything already in the codebase.

**New, per the task statement:**
- Whatever publisher component(s) T17 introduces (likely `TxSeenPublisher`/`TxConfirmedPublisher`/
  `TxFinalizedPublisher` or one combined publisher, mirroring `ProviderDegradedPublisher`'s shape) —
  a Phase 1/3 design decision.
- Wiring inside (or alongside) `Watcher` to detect the seen/confirmed/finalized transitions and a new
  finality-polling mechanism (`getFinalityStatus` + `FinalityPolicy` dispatch) — currently nothing
  calls either.

## 3. Established patterns to follow

- **Outbox emission:** one `XPublisher` component per event family, wrapping `OutboxPublisher`, called
  from the domain code that detects the transition (`ProviderDegradedPublisher` is the direct,
  complete precedent — aggregate type, idempotency key construction, payload record shape, Javadoc
  style documenting every non-obvious decision).
- **Idempotency key (L5):** `chain:txhash:eventtype` — R12 fixes this as a *deterministic*, not
  randomized, key for `chain.tx.*` events (unlike `ProviderDegradedPublisher`'s randomized key, which
  is deliberately different because a provider can degrade/recover/degrade repeatedly; a `chain.tx.*`
  transition — seen, a given confirmation count, finalized — is a one-time-ever event per the schema's
  own `idempotencyKey` description: "Idempotency key = chain:txHash:finalized").
- **Partition key:** `watchId` as `aggregateId` for all `chain.tx.*` events (design.md §4c, already
  documented in `OutboxPublisher`'s own Javadoc).
- **Persistence:** JPA entities, Flyway migrations (`V8` would be next), `ddl-auto=validate`,
  least-privilege `crypto_app` grants added per migration matching exactly what the new code needs
  (pattern from `V7__crypto_app_watcher_grants.sql`).
- **Amounts:** never a JSON number — token base units as a decimal string (design.md §4c, restated in
  the `tx-finalized` schema itself).
- **Contracts:** `contracts/events/chain/` does not exist yet in this repository (only `contracts/events/auth/*`
  exist) — same disclosed gap T16 already recorded. `agents.md` states "Models are generated from
  `contracts/` — never hand-written," but the established precedent (`ProviderDegradedPublisher`'s
  `Payload` record, explicitly annotated as "a concrete implementation for task 23 (Contracts) to
  later formalize... not itself a contract file") defers actual schema authoring + codegen to task 23.
  Phase 1/3 should confirm whether T17 follows the same deferral or must author `tx-seen.v1.schema.json`
  /`tx-confirmed.v1.schema.json`/`tx-finalized.v1.schema.json` itself, given `tx-finalized.v1.schema.json`
  is explicitly named in this task's own header contracts list (unlike T16, which had no named contract).
- **Error handling:** fail-fast with a named exception for a genuine programming/config error
  (`EventTopics.forAggregateType`'s unmapped-aggregate-type `IllegalStateException`); trust the caller
  at documented trust boundaries (`ProviderDegradedPublisher`'s disclosed null-safety trust in its
  caller) rather than defensive-coding everywhere.

## 4. Testing conventions

Unit tests: plain JUnit, fixed/injectable `Clock`, scripted `FakeChainAdapter` (test fixture, already
extended in T16 with a `providerName` field and `simulateReorg` as the general observation-delivery
mechanism). `OutboxPublisherTest` and `ProviderDegradedPublisherTest` are the direct precedents for
testing a new `XPublisher` — mocked `OutboxEventRepository`/`OutboxPublisher`, asserting the exact
`aggregateType`/`aggregateId`/`eventType`/`idempotencyKey`/payload passed through. Module-boundary
tests are plain source-scans (`WatchModuleBoundaryTest`, `ProviderModuleBoundaryTest`) checking `import`
lines, not ArchUnit despite `agents.md`'s mention of ArchUnit — establishe practice in this codebase
diverges from that line of `agents.md` (already true before this task; not this task's problem to
reconcile). Integration tests: Testcontainers Postgres (`WatcherRegistryTest`, T16, is the most recent
example — real ShedLock-backed exclusivity, not mocked), migrated through the current Flyway head.
No Kafka Testcontainers usage exists yet anywhere in this module despite `agents.md` naming it — outbox
row creation is what's tested; actual Kafka delivery is `OutboxRelay`'s own concern, apparently never
integration-tested against a real broker so far in this codebase.

## 5. Known gaps / unknowns

- **How does the watcher layer learn a fact is newly "seen" vs. already known?** No mechanism exists
  today to distinguish "this is the first time `EXISTENCE` was AGREED for this tx" from "this AGREED
  decision already existed." `QuorumDecisionService.evaluate` itself throws if a decision already
  exists for `(chain, txHash, factType)` — so in principle, a *successful* `evaluate()` call for
  `EXISTENCE` already implies "first time," but this is an inference from existing behavior, not
  something Phase 0 is meant to design. I do not know whether T17 is expected to hang new logic off
  `Watcher.evaluateFact`'s existing exactly-once-per-fact guarantee, or introduce separate state.
- **How is "confirmations gained" (R9) supposed to repeat?** T16's frozen brief explicitly says
  `CONFIRMATIONS` is evaluated only once, ever, per `(chain, txHash)` (`uq_quorum_tx_fact` constraint) —
  "Repeated/incremental CONFIRMATIONS quorum re-evaluation as a tx gains more confirmations... is task
  17's problem, not resolved here." I do not know how R9's "gains confirmations" repeated-emission
  requirement is meant to work against a quorum-decision schema that only allows one `CONFIRMATIONS`
  decision ever. This is squarely what Phase 1/3 must resolve — flagged, not guessed at here.
- **Finality polling mechanism is entirely unbuilt.** No caller of `getFinalityStatus`/`FinalityPolicy`
  exists; no dispatcher selects a policy by chain; nothing schedules a finality check. Whether this
  reuses `Watcher`'s existing per-watch lifecycle/scheduler or needs a new one is undetermined.
- **`fromAddress`/`toAddress` for the `tx-finalized` payload** are per-observation (`TxResult`) fields,
  not persisted anywhere queryable after the triggering observation is processed (`observation.Observation`
  stores the raw JSON verbatim, not structured columns, per T08's own design). I do not know whether
  the payload is expected to be built at the moment of the triggering observation (values in hand) or
  reconstructed later (would require parsing the verbatim JSON log) — Phase 1/3 question.
- **`addressPoisoningFlag`'s exact source at emission time** — `AddressPoisoningDetector` (T13) exists,
  but I did not find any existing call site wiring its output into a per-transaction flag available at
  the moment `chain.tx.finalized` would be emitted. Not investigated further here (Phase 1 concern).
- Whether T17 must author `contracts/events/chain/*.json` itself or continue deferring to task 23, per
  the "Established patterns" note above.
