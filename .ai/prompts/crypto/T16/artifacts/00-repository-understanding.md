# crypto · T16 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` is a Spring Boot 3.5.4 / Java 21 module. Persistence is Postgres via Flyway-owned DDL
plus JPA entities validated against that schema. Outbox publishes to Kafka. Security is OAuth2
resource-server only. Virtual threads are globally enabled
(`spring.threads.virtual.enabled=true`, T01) with the watcher layer explicitly named as the intended
beneficiary in `application.properties`'s own comment — this task is that beneficiary.

This is by far the most architecturally central task remaining: it is the first piece of code that
actually *drives* the adapter layer (T06/T07), and the first real caller of the observation log (T08),
the quorum pipeline (T09), and provider health tracking (T10) — all four of which have been built and
fully tested since T08-T10 but have had **zero real caller** until now.

## 2. Existing code this task touches

**Already exists, fully built, currently uncalled by anything:**
- `adapter/ChainAdapter.java` — `subscribeAddress(address, sink)` (async push, one instance per
  provider) and `getTx(txHash)` (synchronous, per-provider on-demand pull) are BOTH already on the
  interface. `adapter/model/Subscription.java` — `cancel()` is idempotent, thread-safe, and does not
  guarantee suppression of an observation already in flight.
- `adapter/ObservationSink.java` — the callback `subscribeAddress` invokes with each `TxResult`; its own
  Javadoc names this exact task ("watcher layer, task 16") as the intended implementer/consumer.
  Deliberately carries no error/health channel — provider health signals are a separate, explicit call
  path (`ProviderHealthTracker`), not delivered through this sink.
- `observation/ObservationLog.java` — `record(chain, txHash, provider, factType, rawResponseJson)`.
  Requires a raw JSON string per call, one `FactType` at a time. Must be called **before** any quorum
  decision for the same fact (L3/R4) — already enforced by this class's own ordering, not something the
  caller needs to re-implement, but the caller must still call it first in sequence.
- `quorum/QuorumDecisionService.java` — `evaluate(chain, txHash, factType, List<ProviderAnswer<T>>
  answers)`. **Takes a fully-assembled list of per-provider answers for one fact, all at once** — it has
  no incremental/streaming API. This is the single most important existing-code constraint on this
  task's design: since `ObservationSink.onObservation` delivers one provider's `TxResult` at a time,
  asynchronously, independently per provider, something in this task's own scope must correlate/gather
  N providers' answers for the same `(chain, txHash, factType)` before this method can be called.
  Duplicate-provider answers are rejected; a second evaluation of an already-decided
  `(chain, txHash, factType)` throws `IllegalStateException` (re-evaluation is explicitly out of scope
  everywhere in this codebase so far).
- `observation/FactType.java` — `EXISTENCE`, `AMOUNT`, `TOKEN`, `CONFIRMATIONS`, `FINALITY`. `TxResult`
  (one flat record per provider per query) bundles data for potentially several of these fact types at
  once (`exists`, `amount`, `tokenContractAddress`, `confirmations`) — decomposing one `TxResult` into
  per-fact-type `ProviderAnswer<T>` values (with `T extends Comparable`) for `QuorumDecisionService` is
  this task's own job; no such decomposition exists yet anywhere in this codebase.
- `provider/ProviderHealthTracker.java` — `recordHealthy`/`recordUnhealthy(reason)`/`recordDisagreement`,
  all per `(chain, provider)`. Its own Javadoc explicitly states: "this task provides the tracking
  primitive; detecting either condition from adapter/watcher internals belongs to whichever future task
  first has a concrete signal to report" — naming this task as that future caller. No caller exists yet.
- `watch/WatchRepository.java`, `watch/ChainCursorRepository.java` (T15) — both currently package-private
  interfaces with only the narrow method set T15's own scope needed
  (`findByWatchId`/`existsByWatchId`/`markUnregisteredIfRegistered` on `Watch`; no query methods at all
  on `ChainCursor` beyond inherited `JpaRepository` ones). **Neither has a method to find all currently
  `REGISTERED` watches**, which this task will need to know what to watch.
- `common/config/ProviderProperties.java` — per-chain provider list (`chains[].providers[]`), each with
  `name`/`url`/`timeoutSeconds`/`apiKeySecretName`, plus a global `quorumThreshold`
  (`@Min(1)`, cross-checked against each chain's own provider count at binding time). No `ProviderSet`
  class exists yet (`design.md` §6 names one, `provider/ProviderSet.java`, "N adapters per chain — O1" —
  not yet built by any prior task).
- `ProviderDegradedPublisher`, `ProviderHealthRepository` — already exist (T10), composed internally by
  `ProviderHealthTracker`, not called directly by anything else.
- `net.javacrumbs.shedlock:shedlock-spring` / `shedlock-provider-jdbc-template` (already a dependency,
  `pom.xml:91-92`) and the `chain.shedlock` table (already in `V1__chain_baseline.sql:126-131`) — both
  provisioned since T01/T02, unused by any code so far. `package.md:132` states directly: "scheduled/
  leased work is ShedLock-guarded" for watcher assignment (O5) — a strong signal for what `WatcherRegistry`
  is meant to use, though not yet confirmed as a locked decision (O5 is an OPEN decision the implementer
  proposes and the human/author must approve, per the task statement's own "author approval required
  before finalizing").

**Does NOT yet exist — this task's actual scope:**
- No `watch/Watcher.java`, no `watch/WatcherRegistry.java` anywhere in `services/crypto/src/main/java`.
- No correlation/aggregation mechanism for multi-provider answers to the same fact.
- No `ProviderSet` abstraction (design.md names it but no prior task built it).
- No method on `WatchRepository` to list active/`REGISTERED` watches.
- No cursor-advancement logic anywhere — `ChainCursor` (T15) is currently a write-once placeholder row
  (`lastBlock = -1` sentinel); nothing in this codebase moves it forward yet.

## 3. Established patterns to follow

- **Coordinator classes that compose several single-purpose collaborators, named in Javadoc as
  "functionally necessary, not spec-named"** — `ObservationLog` (T08), `QuorumDecisionService` (T09),
  `ProviderHealthTracker` (T10) are all this shape. `Watcher` will very likely be the largest such
  coordinator yet, sitting on top of all three.
- **`@Transactional` only when a coordinator's own write actually spans more than one collaborator's
  already-individually-transactional `save`** (`ProviderHealthTracker`'s own Javadoc explains this
  precisely, contrasting itself with `ObservationLog`/`QuorumDecisionService`, which are deliberately
  NOT `@Transactional`).
- **Fixed, injectable `Clock`** for every timestamp, throughout every task so far.
- **Scripted fake `ChainAdapter`s only in tests, real adapters never called in tests/CI** (agents.md,
  already exercised via `FakeChainAdapter`, T06-era).
- **Config-driven, validated `@ConfigurationProperties`**, fail-fast at binding time
  (`ProviderProperties`'s own constructor cross-check is the most elaborate example so far).
- **ShedLock for scheduled/leased work** — provisioned but unused; `package.md` itself names this as the
  intended mechanism for O5.
- **Money as `BigDecimal`**, never floating point, throughout.

## 4. Testing conventions

- Unit tests: plain JUnit, fixed `Clock`, scripted `FakeChainAdapter` — established since T06.
- Integration tests: Testcontainers (Postgres [+ Kafka once outbox-publishing is involved]) — established
  since T02.
- No ArchUnit convention exists yet for a NEW rule this task might need (e.g. "only `attest` may call
  `KmsSigner`" is task 20's own future rule) — the only enforcement mechanism used so far for module
  boundaries is the plain source-scan `*ModuleBoundaryTest` pattern (T10, T11, T14, T15).
- **Concurrency/scheduling testing has no precedent yet in this codebase.** Every prior task's tests are
  either pure-function unit tests or single-threaded integration tests against Testcontainers. This task
  introduces the service's first genuinely concurrent, long-running, multi-thread component — there is
  no established pattern here to follow, and Phase 2/5 will need to design one (e.g. `Awaitility`,
  already a test dependency per `pom.xml`, confirmed present since earlier tasks, but not yet used for
  this kind of scenario).

## 5. Known gaps / unknowns

- **How this task correlates asynchronous, independently-timed per-provider observations into the
  synchronous, all-at-once `List<ProviderAnswer<T>>` shape `QuorumDecisionService.evaluate` requires is
  not specified anywhere in the spec.** Two plausible designs exist in the interface shapes already
  provided: (a) use `subscribeAddress` purely for *discovery* (learning a candidate `txHash` exists via
  whichever provider notices first), then synchronously call `getTx(txHash)` against *every* configured
  provider for that chain to gather a complete, quorum-ready answer set in one step, avoiding any
  stateful correlation buffer; or (b) maintain a stateful, timeout-bounded correlation buffer keyed by
  `(chain, txHash)` that accumulates async `onObservation` callbacks from all subscribed providers until
  either all have answered or a timeout elapses. I do not know which (if either) the spec intends —
  genuine Phase 1/2 design territory, not resolved here.
- **Whether `ChainCursor.lastBlock`/`lastFinalizedBlock` advancement is in this task's own scope is not
  stated.** T15 left `ChainCursor` as a write-once `-1` placeholder; T18 (`ReorgDetector`) "walks the
  cursor backward," which presupposes something walks it *forward* first — nothing else in the task list
  claims that responsibility. I do not know for certain whether that's this task's job.
- **Whether `WatcherRegistry`'s O5 multi-replica assignment is ShedLock-per-watch, ShedLock-per-shard, or
  something else is not decided.** `package.md`'s "scheduled/leased work is ShedLock-guarded" is
  directional, not a locked decision — and the task statement itself flags O5 as needing explicit author
  approval before finalizing, distinct from every other OPEN decision resolved so far in this pipeline
  (which have only needed the normal Phase 4 gate).
- **No `ProviderSet` class exists** (`design.md` §6 names `provider/ProviderSet.java`, "N adapters per
  chain — O1," as a file no prior task built) — I do not know whether this task is expected to build it
  as a prerequisite, or whether `Watcher`/`WatcherRegistry` are expected to read `ProviderProperties` and
  construct/hold adapter instances directly without that intermediate abstraction.
- **Confirmed, not an open question: `EthereumAdapterConfig` already exposes `@Bean List<EthereumAdapter>
  ethereumAdapters(...)`** (one adapter per configured Ethereum provider entry), and `TronAdapterConfig`
  almost certainly mirrors this shape for Tron (not yet directly re-read, but the same pattern is
  overwhelmingly likely given T07's own stated mirroring of T06). These are two separate
  `List<EthereumAdapter>`/`List<TronAdapter>` **collection beans**, not individually-registered
  `ChainAdapter` beans — Spring's `List<ChainAdapter>` multi-bean-injection convenience would NOT
  automatically flatten and aggregate their contents, since each is one bean of type `List<X>`, not N
  beans of type `X`. Whatever this task builds must inject both typed lists separately and combine them
  itself (e.g. via `Stream.concat` or a per-chain grouping) — a concrete, confirmed constraint for
  Phase 2's design, not a gap.
