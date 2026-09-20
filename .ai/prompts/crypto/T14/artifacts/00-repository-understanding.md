# crypto · T14 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` is a Spring Boot 3.5.4 / Java 21 module (`services/crypto`) inside the `checky-pro`
parent. Persistence is Postgres via Flyway-owned DDL (`db/migration/`) plus JPA entities validated
against that schema (`ddl-auto=validate`, never mutating). Outbox pattern (`events/OutboxEvent` +
`OutboxRelay`) publishes to Kafka with idempotency keys `chain:txhash:eventtype` (L5). Security is
OAuth2 resource-server only (validates auth-service-issued JWTs; never issues its own). The service is
the sole component with chain-write/`kms:Sign` access (agents.md).

Runtime package layout relevant here:
- `adapter/` — `ChainAdapter` interface (one instance per provider) plus real implementations
  `EthereumAdapter` (web3j, T06) and `TronAdapter` (trident, T07), each also having a corresponding
  `*Config` class wiring it from `ProviderProperties`.
- `adapter/model/` — value objects returned by `ChainAdapter`: `TxResult`, `FinalityStatus`,
  `TokenInfo`, `Subscription`.
- `observation/`, `quorum/`, `provider/`, `token/` — later pipeline stages (verbatim log, quorum
  evaluation, provider health, token/address validation) built in T08-T13; none of them currently
  reference finality.

## 2. Existing code this task touches

**Already exists, task 14 must NOT redo:**
- `adapter/model/FinalityStatus.java` (built ahead of schedule during T06/T07) — a plain data record
  `(txBlockNumber, currentBlockNumber, finalizedBlockNumber)`. Its own Javadoc states the key
  observation this task inherits: for either launch chain, "finality reduces to the same check:
  `txBlockNumber <= finalizedBlockNumber`" — each `FinalityPolicy` implementation's *input* is already
  chain-agnostic; only *how the adapter obtained* `finalizedBlockNumber` differed per chain.
- `ChainAdapter.getFinalityStatus(String txHash)` — already declared on the interface and already
  **fully implemented** in both `EthereumAdapter.getFinalityStatus` (uses
  `DefaultBlockParameterName.FINALIZED`, the beacon-chain finalized checkpoint, R6) and
  `TronAdapter.getFinalityStatus` (uses `fetchSolidifiedBlockNumber()` via `NodeType.SOLIDITY_NODE`,
  the solidified block, R7). Both already guard against a provider reporting an internally
  inconsistent snapshot (finalized/solidified block ahead of current head, or tx block ahead of
  current head) by throwing `IllegalStateException`. **This means the "how do I even learn the
  finalized/solidified block number per chain" half of R6/R7 is already shipped** — it happened inside
  T06/T07's own adapter work, ahead of this task.
- `FakeChainAdapter.scriptFinalityStatus(txHash, status)` (test fixture, already exists) — precisely
  the "scripted chain heads" mechanism the task statement names; no new test double is needed.
- `common/config/FinalityProperties.java` — `enabled-chains` list only (`ETHEREUM`, `TRON`), already
  wired into `application.properties`. Its own Javadoc is explicit that "no confirmation-count or
  threshold field belongs here... each chain's actual policy... is hardcoded in its `FinalityPolicy`
  implementation (design.md task 14)" — i.e. this properties class already anticipates this task and
  deliberately leaves the policy classes themselves for it to add.

**Does NOT yet exist — this task's actual remaining scope:**
- No `FinalityPolicy` interface or type anywhere in `services/crypto/src` (confirmed via grep — zero
  matches outside spec/config-comment references).
- No `EthereumFinalityPolicy` / `TronFinalityPolicy` classes.
- Nothing currently *calls* `getFinalityStatus` and turns its result into a boolean "is this final"
  decision, or dispatches to the correct per-chain policy by `Chain`. That decision layer is the actual
  gap this task fills — not the "obtain the finalized block number per chain" logic, which already
  shipped.

## 3. Established patterns to follow

- **Pure, stateless, no-persistence value predicates behind a shared interface, dispatched by `Chain`**
  — the precedent set by `ChainAdapter.chain()` itself, and by T11/T12/T13's `token/` package
  (`TokenValidator`, `AddressValidator`, `AddressPoisoningDetector`): no injected dependency, no
  `@Component` state beyond constants, operates purely on its method arguments.
- **`Chain` enum as the discriminator** for per-chain dispatch (`ETHEREUM`, `TRON` only — launch scope,
  design.md §2). Any registry/dispatch this task adds (if any) should key on `Chain`, mirroring
  `ChainAdapter.chain()`.
- **No confirmation-count/threshold hardcoded as a *global* constant** — L4/agents.md are explicit that
  finality is per-chain-policy-object, never a shared numeric constant. `TronFinalityPolicy` must not
  hardcode "~19" anywhere; R7's "~19 confirmations" is descriptive of what a solidified block typically
  represents, not a literal threshold to re-derive — the actual solidified block number is already
  fetched by `TronAdapter` from the node itself.
- **Fail loudly on impossible state, never silently coerce** — both adapters already throw
  `IllegalStateException` for an internally inconsistent `FinalityStatus`-producing snapshot; a
  `FinalityPolicy` consuming an already-validated `FinalityStatus` should not need to re-guard the same
  invariants, but Phase 2 should decide explicitly whether defense-in-depth here is warranted or
  redundant, per this pipeline's own precedent of not adding unprecedented hardening (T13 Phase 9
  Issue 8's rejection).
- **Named tests come from `package.md` §8 verbatim** — `shouldRequireBeaconFinalizedCheckpointForEthereumFinality`,
  `shouldRequireSolidifiedBlockForTronFinality`.

## 4. Testing conventions

- Pure-predicate classes in this codebase (T09 `QuorumEvaluator`, T11 `TokenValidator`, T12
  `AddressValidator`, T13 `AddressPoisoningDetector`) get plain JUnit 5 unit tests with hand-constructed
  fixtures — no Spring context, no Testcontainers, no Docker dependency. `FinalityPolicy` is very likely
  the same shape (pure function of a `FinalityStatus`), so the same convention should apply.
- "Unit-test each against scripted chain heads" (task statement) maps directly onto constructing
  `FinalityStatus` records by hand at the relevant boundary (`txBlockNumber == finalizedBlockNumber`,
  `txBlockNumber < finalizedBlockNumber`, `txBlockNumber > finalizedBlockNumber`) — `FakeChainAdapter`
  is available but may not even be needed if the policy takes a `FinalityStatus` directly rather than a
  `ChainAdapter` (a Phase 2 design decision).
- Fixed `Clock` convention (used elsewhere in this service for time-dependent logic) is very unlikely to
  apply here — nothing about finality-by-block-number is wall-clock-dependent.
- ArchUnit is used elsewhere in this codebase only for the `kms:Sign`-reachable-only-from-`attest`
  boundary rule (agents.md) and this service's own `*ModuleBoundaryTest` source-scan convention
  (T10/T11/T13) for the `token/`/`provider/` packages specifically — no indication either applies to a
  new `finality/`-shaped package, but Phase 2 should confirm there is no existing ArchUnit rule this
  task must satisfy.

## 5. Known gaps / unknowns

- **Where does `FinalityPolicy` live?** No `finality/` package exists yet. Phase 2 must decide the
  package (a new `finality/` package is the most direct read of design.md's own "task 14" framing, but
  this is not yet confirmed against `design.md`'s own file-layout section, which Phase 1 will read).
- **Exact method signature is undetermined** — I do not know yet whether `FinalityPolicy` should take a
  `FinalityStatus` directly (my read of the evidence favors this — it is the natural, decoupled,
  easily-scripted shape) or a `(String txHash, ChainAdapter adapter)` pair that calls
  `getFinalityStatus` itself. This is a Phase 2 design decision, not yet resolved.
- **Q4 (Tron confirmation-count basis) may already be answered by existing, shipped code** —
  `TronAdapter.computeConfirmations` (T07) already computes `confirmations` as block depth from the
  current head (`currentBlock - txBlock + 1`), identical in basis to `EthereumAdapter.computeConfirmations`
  — NOT confirmations-toward-the-solidified-block. This looks like it only needs Phase 2 to confirm and
  document this already-implemented basis (per the task statement's own "Confirm... (Q4)" wording,
  distinct from "implement"), rather than write any new code for it. I do not know whether Phase 1/2
  should also check whether `design.md`/`package.md` say anything further on Q4 beyond what's quoted in
  Phase 1's own scoped-requirement extraction — that is Phase 1's job, not this one.
- **Whether a chain-to-policy dispatcher/registry belongs in this task's scope** — the task statement
  only names the two policy classes plus the shared interface; nothing yet consumes a `FinalityPolicy`
  by chain (the plausible future caller is the same not-yet-built watcher/attest layer that will also
  eventually call `AddressPoisoningDetector`, per T13's own precedent of shipping algorithms ahead of
  their integration point). I do not know whether Phase 2 should scope in a small
  `Map<Chain, FinalityPolicy>`-style lookup or leave that entirely to a future task — flagged for Phase 2,
  not resolved here.
