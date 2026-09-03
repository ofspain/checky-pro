# crypto · T05 · Phase 0 — Repository Understanding

## 1. Architecture summary of this service (as it exists today)

Four tasks shipped so far, all under `services/crypto/src/main/java/com/themistra/crypto/`:

- **T01** — Maven skeleton, bare `CryptoServiceApplication` (`@SpringBootApplication
  @ConfigurationPropertiesScan @EnableScheduling`).
- **T02** — `chain` Postgres schema (Flyway `V1`/`V2`): 10 tables including `watches`,
  `observations`, `quorum_decisions`, `provider_health`, `chain_cursors`, `token_allowlist`,
  `screening_results`, `attestations`, `outbox`, `shedlock`; least-privilege `crypto_app` role.
- **T03** — `common/` config + resource-server foundation: 5 `@ConfigurationProperties` classes
  (`ProviderProperties`, `FinalityProperties`, `ScreeningProperties`, `KmsProperties`,
  `SnapshotProperties`), `PublicEndpoints`, `ResourceServerConfig` (JWT + `internal.crypto:write`
  scope on `/internal/v1/**`).
- **T04** — `events/` outbox mechanism: `EventTopics`, `OutboxPublisher`, `OutboxEvent`/
  `OutboxEventRepository`, `OutboxRelay`, `KafkaProducerConfig`; `V3` migration; `common/ClockConfig`
  (this service's first `Clock` bean).

**Only two packages exist under `com.themistra.crypto` today: `common/` (+ `common/config/`) and
`events/`. No `adapter/`, `provider/`, `quorum/`, `observation/`, `finality/`, `watch/`, `reorg/`,
`token/`, `screening/`, or `attest/` package exists anywhere — this task is the very first to touch
any of them.** `ProviderProperties` (T03) already has a generic, vendor-agnostic per-chain provider
list shape (`chains[].providers[].{name,url,timeoutSeconds,apiKeySecretName}`,
`quorumThreshold`), constrained via `@Pattern(regexp = "ETHEREUM|TRON")` on the chain field — but it
holds **configuration**, not a `Chain` enum or any adapter code; nothing in `common/config/` currently
references or produces a `Chain` type.

Persistence/security/events architecture is unchanged from T02/T03/T04's own summaries — Postgres
`chain` schema (Flyway-owned, `crypto_app` runtime role), OAuth2 resource server on
`/internal/v1/**`, transactional outbox via `OutboxPublisher`.

## 2. Existing code this task touches — what's already there vs. new

**Nothing existing is touched or extended by this task** — T05 is greenfield within a brand-new
`adapter/` package. The only *indirect* relationship to existing code:
- `ProviderProperties.ChainProviders.chain` (T03) and `FinalityProperties.enabledChains` (T03) are
  both plain `String` fields constrained to `"ETHEREUM"`/`"TRON"` via `@Pattern` — **not** typed to a
  `Chain` enum, specifically because `Chain` didn't exist yet when T03 was built (confirmed in T03's
  own Phase 0/6 notes). Whether this task's new `Chain` enum should retroactively be wired into those
  T03 classes is explicitly **not** this task's own statement ("Define `ChainAdapter`... and `Chain`.
  Build a scripted `FakeChainAdapter`") — flagged as a question for Phase 1, not assumed.
- `pom.xml` (T01) already declares `web3j:core:6.0.0` and `io.github.tronprotocol:trident:1.0.0` —
  the real chain-client libraries `EthereumAdapter`/`TronAdapter` (a **later** task, not T05) will
  use. T05 itself needs neither: `ChainAdapter` is a pure interface and `FakeChainAdapter` is a
  scripted test double, so no chain-client library call is expected in this task.

## 3. Established patterns to follow

- **VERBATIM interface, already given in the spec.** `design.md` §4c gives `ChainAdapter` as a
  VERBATIM artifact ("copy exactly, do not paraphrase"):
  ```java
  public interface ChainAdapter {
      Chain chain();                                   // ETHEREUM | TRON
      TxResult getTx(String txHash);                   // provider-scoped; quorum compares across adapters
      TokenInfo getTokenInfo(String contractAddress);  // identity by address only (L7)
      Subscription subscribeAddress(String address, ObservationSink sink); // watcher layer
      FinalityStatus getFinalityStatus(String txHash); // evaluated against the per-chain FinalityPolicy (L4)
  }
  ```
  This means the method signatures are fixed by the spec; only the supporting types
  (`TxResult`/`TokenInfo`/`FinalityStatus`/`Subscription`/`ObservationSink`/`Chain`) need actual
  design work, since the spec doesn't give their shapes verbatim anywhere in §4c.
- **Package map (`design.md` §6)** places these under `adapter/`:
  ```
  adapter/
  ├── ChainAdapter.java                     (interface — §4c)
  ├── Chain.java                            (enum: ETHEREUM, TRON)
  ├── eth/EthereumAdapter.java              (web3j)               [NOT this task]
  ├── tron/TronAdapter.java                 (TronGrid / java-tron gRPC) [NOT this task]
  ├── model/{TxResult,TokenInfo,FinalityStatus,Subscription}.java
  └── ObservationSink.java
  ```
  `EthereumAdapter`/`TronAdapter` are explicitly named as later tasks in `tasks.md` (task 6, task 7),
  not this one — T05's own statement covers only the interface, the enum, and the fake.
- **No prior "fake adapter" or scripted-test-double pattern exists anywhere in this codebase yet.**
  Auth-service has no chain-adapter equivalent to mirror (it's a different domain entirely — identity/
  tokens, not blockchain data). This is genuinely new pattern territory, unlike T02/T03/T04 which each
  had a direct auth-service precedent to structurally mirror.
- **Module boundaries (L15, agents.md):** package-by-feature under `com.themistra.crypto`; shared
  plumbing only in `common`; ArchUnit enforces no cross-module entity import. `adapter/` is its own
  feature module — `model/` and `eth/`/`tron/` are sub-packages of it, not separate top-level modules.
- **Config precedent (T03):** if this task needs any new config (unclear yet — see Known Gaps), the
  established convention is validated `@ConfigurationProperties` for multi-field/cross-validated
  config, or a standard Spring property for anything the framework already provides a slot for.

## 4. Testing conventions

- agents.md: "Unit (plain JUnit, fixed `Clock`, scripted fake `ChainAdapter`s) → ArchUnit + contract
  → integration (Testcontainers: Postgres + Kafka, fake providers)." **This task's own deliverable —
  `FakeChainAdapter` — is explicitly the thing every later unit test in this service is described as
  depending on.** Getting its scripting shape right (able to "agree, disagree, lag, and reorg", per
  the task statement) is foundational for T06 onward, not just for this task's own tests.
- No ArchUnit test exists yet anywhere in `services/crypto` (confirmed: `archunit-junit5` is a
  `pom.xml` test dependency since T01, per the crypto-service memory notes, but never yet exercised).
  Whether T05 is the task that first adds an ArchUnit rule (e.g. for `adapter/` module boundaries) is
  unclear from the task statement alone — flagged in Known Gaps.
- T02/T03/T04 all used either plain JUnit+Mockito (no Spring context) for pure logic, or
  Testcontainers-Postgres for real-DB behavior. `FakeChainAdapter` and `ChainAdapter`/`Chain`/the
  model types are pure Java with no persistence or Spring dependency at all — this task's own tests
  (once designed in Phase 2+) are very likely plain-JUnit, no Spring context, no Testcontainers,
  matching agents.md's explicit "Unit (plain JUnit...)" framing for this exact layer.

## 5. Known gaps / unknowns

- **I do not know** the exact shape of `TxResult`, `TokenInfo`, `FinalityStatus`, or `Subscription`.
  None of these appear as VERBATIM artifacts in `design.md` §4c — only `ChainAdapter`'s own method
  signatures reference them by name. Their fields must be inferred from how later tasks use them (e.g.
  task 8 "Observation log first," task 9 "Quorum evaluator," task 11 "Token allowlist + validator,"
  task 14 "Finality policies") — Phase 1/2 design work, not derivable from this phase's read-only pass.
- **I do not know** exactly what "agree, disagree, lag, and reorg" scripting means as a concrete API
  on `FakeChainAdapter` — the task statement names these four behaviors but no verbatim shape is
  given. This is squarely Phase 2 (TIB) design work.
- **I do not know** whether `Chain` (this task's new enum) is expected to retroactively replace the
  plain `String` chain fields in T03's `ProviderProperties`/`FinalityProperties`. Retrofitting T03's
  already-shipped, already-tested config classes is arguably out of scope for a task whose statement
  only says "Define... `Chain`," but leaving two parallel representations of the same concept
  (a `String` constrained by regex vs. a real enum) is a design tension worth flagging, not silently
  resolving either way.
- **I do not know** whether `ObservationSink` (referenced in `ChainAdapter.subscribeAddress`) needs
  any real shape in this task or can be a minimal marker/functional interface — its real consumer
  (the watcher layer, task 16) doesn't exist yet, mirroring the exact "named in an interface signature
  but not fleshed out until a much later task" situation T04 hit with `events/event/*Payload.java`.
- **I do not know** which requirement IDs apply — the task header explicitly defers this to Phase 1
  ("none cited inline — derive them in `requirements.md`"). A first read suggests R1 (multi-provider
  fetch), R6/R7 (finality evaluation), R13 (token identity by contract address), and R15/R16 (address
  validation) all *touch* the shapes this task defines, but none of them are testable by this task's
  own deliverable (an interface, an enum, and a fake) in isolation — Phase 1's own job to resolve.
- No contract file (`contracts/events/chain/*`, `contracts/api/crypto-internal.yaml`) defines any of
  these types either — both are still unauthored (task 23).

Do not design and do not extract requirements yet — that is Phase 1. This artifact stops here.
