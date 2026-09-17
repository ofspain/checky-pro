# crypto · T24 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` (`services/crypto`, package root `com.themistra.crypto`) is a Spring Boot 3.5.4 / Java 21
service, package-by-feature, one Postgres schema (`chain`), Flyway migrations, Kafka via a
transactional outbox, ArchUnit-enforced module boundaries. Relevant modules for this task:

- **`adapter`** — `ChainAdapter` is the one interface every chain integration implements: real
  (`EthereumAdapter`, `TronAdapter`), a sidecar-backed translation shim (L14, none built yet), or in
  tests `FakeChainAdapter`. `ProviderSet` groups `ChainAdapter` instances per `Chain` into
  `NamedAdapter(providerName, adapter)` pairs — the one place a concrete adapter's own identity is
  captured; every other caller (`Watcher`) sees only the abstract `ChainAdapter` view plus the
  `providerName` string.
- **`quorum`** — `QuorumEvaluator` is pure 2-of-3 arbitration logic over exactly 3
  `Comparable`-answers; `QuorumDecisionService` persists the outcome and invokes `HeldFactAlerter` on
  `HELD`. Neither has any concept of provider identity beyond an opaque label (`ProviderAnswer<T>` pairs
  a provider name with a value) — no weighting, no "trusted source" notion anywhere in this path.
- **`watch`** — `Watcher` (one instance per active `Watch`) subscribes to every configured
  `ProviderSet.NamedAdapter` for the watch's chain, correlates each provider's independently-timed
  `onObservation` callback by `txHash`, and once exactly 3 real answers exist for a fact, evaluates
  quorum for it. Also drives `ProviderHealthTracker` (healthy/lagging/disagreement), the observation log
  (`ObservationLog.record`, verbatim, before any quorum evaluation), `ChainCursor` persistence, reorg
  detection (`ReorgDetector`), and `TxLifecyclePublisher` (the `chain.tx.*` outbox events).
- **`attest`** — `KmsSigner` is the sole class permitted to touch the AWS KMS SDK (ArchUnit-enforced,
  `KmsSignerArchitectureTest`), invoked only from `AttestationService`/`AttestController`. No other
  module, including `adapter`/`watch`/`quorum`, has any dependency on `attest` today.

## 2. Existing code this task touches

Everything this task needs already exists and is unmodified by any part of T24's own scope:

- `ChainAdapter` (interface) — already explicitly documents in its own Javadoc that a real adapter, a
  sidecar-backed shim, and `FakeChainAdapter` are all equally valid implementations.
- `ProviderSet`/`ProviderSet.NamedAdapter` — its own Javadoc states the L14/AC7 constraint verbatim: "no
  code path may distinguish one `ChainAdapter` implementation from another."
- `Watcher` — every method operates solely on `ChainAdapter`/`NamedAdapter.providerName()`; no
  `instanceof`, no adapter-type switch, anywhere in the class (confirmed by direct reading of the full
  ~720-line file, not by grep alone).
- `QuorumEvaluator`/`QuorumDecisionService`/`ProviderAnswer` — operate on `Comparable` values and opaque
  provider-name strings only.
- `FakeChainAdapter` (test-only, `adapter` test package) — the existing scripted fake, already used by
  `WatcherTest`'s 62 tests, constructed as `new FakeChainAdapter(Chain, String providerName)`.
- **Nothing new exists yet for this task.** T24 is purely additive: a new fake "sidecar" observation
  source (whether a new class or a parameterized use of the existing `FakeChainAdapter` is a Phase 2/5
  decision, not decided here) plus the one named test.
- Prior art: T16's own Phase 12 (`​.ai/prompts/crypto/T16/artifacts/12-specification-verification.md`)
  already verified AC7/L14 by direct source inspection (`Watcher`/`WatcherRegistry` grepped for
  `EthereumAdapter`/`TronAdapter`/`instanceof` — zero code hits) and explicitly deferred the "dedicated
  negative test" to this task, noting "an absence is not independently testable beyond source
  inspection" at the time.

## 3. Established patterns to follow

- **Fake providers only, never real RPC, in tests/CI** (`agents.md`). `FakeChainAdapter` is the
  established vehicle — scripted per-`txHash` answers via `scriptTx`/`scriptTokenInfo`/
  `scriptFinalityStatus`, pushed to a live subscription only via `simulateReorg` (the codebase's only
  push mechanism into an `ObservationSink`, per `WatcherTest`'s own class Javadoc).
- **`WatcherTest`'s own 3-provider harness** is the direct template: three named adapters
  (`provider-a`/`-b`/`-c` today) wired into a real `Watcher` via `List<ProviderSet.NamedAdapter>`,
  everything else (`ObservationLog`, `QuorumDecisionService`, `ProviderHealthTracker`,
  `ChainCursorRepository`, `TxLifecyclePublisher`, `ReorgDetector`) mocked, a `MutableClock`, and a
  `deliver(FakeChainAdapter, TxResult)` helper that calls `simulateReorg` under the hood. A sidecar test
  most naturally extends or mirrors this exact harness with one of the three named adapters relabeled to
  a sidecar-style provider name (design.md's own DDL comment gives the convention: `sidecar:<chain>`,
  e.g. `sidecar:solana`).
- **ArchUnit as the "no signing/state access" enforcement mechanism.** `KmsSignerArchitectureTest`
  already forbids any class outside `attest` from touching the KMS SDK at all — this rule is
  package-wide, so it already covers a hypothetical sidecar-backed `ChainAdapter` implementation without
  any change. `ProviderModuleBoundaryTest`/`WatchModuleBoundaryTest`/etc. are the per-module ArchUnit
  precedent if a dedicated `adapter`-scoped rule turns out to be warranted (open question below).
- **Fixed `Clock`** (`MutableClock`, test-only) throughout, never wall-clock time in tests.
- **Named-test convention**: `package.md` §8 names `shouldTreatSidecarOutputAsJustAnotherProviderAnswer`
  as this task's one required named test — mirrors every prior task's single-named-test pattern.

## 4. Testing conventions

Unit (plain JUnit + Mockito, fixed `Clock`, scripted `FakeChainAdapter`) → ArchUnit + contract →
integration (Testcontainers Postgres + Kafka, still fake providers, never real RPC). `WatcherTest` is
pure unit-level (no Spring context, no Testcontainers) — a sidecar test at this same level (extending or
sitting alongside `WatcherTest`) is the pattern already established for exercising the full
observation→quorum→health→event pipeline without infrastructure. `ArchUnit` rules in this codebase run as
plain JUnit tests with an explicit assertion inside the test method, not via `@ArchTest` fields (this
repo's Surefire config does not execute bare `@ArchTest` fields — established since T20).

## 5. Known gaps / unknowns

- **Whether the new fake sidecar source should be a new class or a relabeled `FakeChainAdapter`
  instance** is not yet decided — `FakeChainAdapter`'s existing `providerName` constructor parameter
  already supports naming an instance `sidecar:tron` with zero code change, but the task name
  ("sidecar-as-provider **test**") and R25/L14's wording don't mandate a new production or even new
  test-fixture class. This is a Phase 2/5 design decision, not resolved here.
- **Whether a new, `adapter`-scoped ArchUnit rule is needed, or the existing `KmsSignerArchitectureTest`
  rule (already package-wide) is sufficient proof of "no signing access."** I do not know which the
  design phase will prefer — both are technically sufficient; the frozen brief should decide.
- **"No business state access"** — I do not know of any existing test that would fail if a
  `ChainAdapter` implementation tried to write directly to `Watch`/`ChainCursor`/`QuorumDecision`
  repositories, since no adapter is ever given a repository reference by any wiring path (`ProviderSet`'s
  constructor takes only `List<EthereumAdapter>`/`List<TronAdapter>`, never a repository). This appears
  to already be true by construction (no adapter type has a repository dependency, confirmed by reading
  `EthereumAdapter`/`TronAdapter`/`ChainAdapter`), but I have not exhaustively verified every adapter
  constructor signature — that level of detail belongs to Phase 1/2.
- `docs/service-languages.pdf` (cited by L14) was not read — out of this phase's scoped file list
  (package.md/requirements.md/design.md/tasks.md/agents.md only).
