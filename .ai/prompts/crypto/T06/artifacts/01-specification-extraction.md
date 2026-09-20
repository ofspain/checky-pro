# crypto · T06 · Phase 1 — Specification Extraction

## Business Rules

- **R6.** WHEN evaluating Ethereum finality, THEN the system SHALL require the transaction's block to
  be at or below the beacon-chain `finalized` checkpoint, NOT a fixed confirmation count. **This is
  the one requirement independently testable by this task's own deliverable** — `getFinalityStatus`
  must report the real `finalized` block tag's number (verified in Phase 0: `web3j`'s
  `DefaultBlockParameterName.FINALIZED` gets this directly, no separate beacon-API client needed), and
  must never derive finality from a hardcoded confirmation-count arithmetic instead.

No other numbered requirement is independently testable by `EthereumAdapter` alone:
- R1 (2-of-3 quorum) — `EthereumAdapter` is *one* provider instance the quorum module (task 9) fans
  out across; it doesn't implement quorum logic itself.
- R4 (persist verbatim before quorum decision) — the observation module's job (task 8), not this
  adapter's; `EthereumAdapter` only returns `TxResult`, it doesn't persist anything.
- R9 (confirmation count on `chain.tx.confirmed`) — event emission is task 17's job;
  `EthereumAdapter` only needs to populate `TxResult.confirmations` correctly, which R9 itself doesn't
  test at this layer.
- R13 (token identity by contract address) — `getTokenInfo` must be keyed by the `contractAddress`
  parameter (already fixed by `ChainAdapter`'s own contract, T05), but the identity *rule itself* is
  R13/L7's concern, not independently re-tested by this task beyond "the adapter doesn't invent its
  own identity scheme."
- R15 (EIP-55 checksum validation) — design.md §6's package map gives this its own component,
  `token/AddressValidator.java` (task 12), separate from any `ChainAdapter` implementation. Whether
  `EthereumAdapter` should defensively validate addresses too, or trust the caller already did, is a
  genuine open question for Phase 2 (see Open Questions), not assumed either way here.

## Locked Decisions

Derived from `design.md` §4a:

- **L4.** `EthereumAdapter.getFinalityStatus` returns raw block data only (`txBlockNumber`,
  `currentBlockNumber`, `finalizedBlockNumber` — T05's already-fixed `FinalityStatus` shape) — it must
  not itself decide "is this transaction final," that's `EthereumFinalityPolicy`'s job (task 14).
- **L7.** `getTokenInfo` returns whatever the chain/provider reports for the given `contractAddress`
  — `EthereumAdapter` has no allowlist awareness (already fixed by `ChainAdapter`'s own contract, T05
  Phase 9).
- **L13.** No provider API key or endpoint credential is committed; config fails startup on
  missing/invalid values in non-local profiles (already the case for `ProviderProperties` since T03 —
  this task is its first real consumer, not the thing that establishes the rule).
- **L14.** `EthereumAdapter` must conform to exactly the same `ChainAdapter` contract a sidecar-backed
  implementation would — nothing in this task's own implementation may assume capabilities beyond
  what the interface itself exposes.
- **L15.** New file goes under `adapter/eth/`, matching design.md §6's file map exactly.

Also directly relevant, cited inline in the task statement itself:
- **O1 (design §4b, Q1 unresolved).** The specific 3 launch providers per chain and whether N is
  fixed at 3 (2-of-3) are unresolved — explicitly "Blocker for real deployment (not for fake-provider
  tests)" per `package.md` §11 Q1's own text. `EthereumAdapter` must be provider-*agnostic* (works
  against any correctly-configured Ethereum-JSON-RPC-compatible endpoint URL), never hardcoded to one
  vendor's quirks — this task is not blocked by Q1 remaining open, only real production deployment is.
- **O2 (design §4b, unresolved).** "Watcher transport & concurrency" — websocket-subscription vs.
  polling for `subscribeAddress`, and the polling-interval budget, is an explicitly open design
  question nothing in the spec has resolved yet. This task is the first one that must actually
  implement `subscribeAddress` for a real chain — Phase 2 will need to make this call for Ethereum
  specifically (see Open Questions).

## Files involved

**Existing — read/extend:**
- `adapter/ChainAdapter.java`, `adapter/model/*.java`, `adapter/ObservationSink.java` (T05) — the
  interface and types `EthereumAdapter` implements/returns; not modified.
- `common/config/ProviderProperties.java` (T03) — first real consumer; not modified (already
  shipped, tested, and sufficient — see Dependencies).
- `services/crypto/src/main/resources/application.properties` — already has `local`-profile Ethereum
  provider entries (fake URLs/keys) from T03; may need real wiring-relevant additions (TBD Phase 2).
- `services/crypto/pom.xml` — `org.web3j:core:6.0.0` already present (T01); no new dependency
  expected unless Phase 2 finds ERC-20 ABI encoding genuinely requires more than `core` provides.

**New — expected by design.md §6:**
- `adapter/eth/EthereumAdapter.java` — named explicitly in the task statement and design §6.
- **Not named in design.md §6's file map, but likely functionally necessary** (mirrors T04's own
  `OutboxRelay`/`KafkaProducerConfig` situation): a Spring wiring class constructing real `Web3j`
  client instance(s) from `ProviderProperties`' Ethereum entries — exact shape/name is Phase 2 design
  work.

## Dependencies

- `org.web3j:core:6.0.0` (already present) — `Web3j` (interface, injectable/mockable),
  `DefaultBlockParameterName.FINALIZED` (confirmed available, Phase 0), `HttpService`, and low-level
  ABI utilities (`FunctionEncoder`/`FunctionReturnDecoder`) for `getTokenInfo`'s ERC-20 calls (no
  codegen module is on the classpath — manual encoding, unless Phase 2 justifies adding one).
- `ProviderProperties.ProviderEntry` (`name`, `url`, `timeoutSeconds`, `apiKeySecretName`) — the
  config surface this task's Ethereum provider instances are built from.
- **Unresolved dependency: how `apiKeySecretName` resolves to an actual credential value.** No
  secrets-manager SDK exists in `pom.xml`; Phase 0 could not confirm the mechanism from source alone.
- No Spring Data/persistence dependency — `EthereumAdapter` itself does not touch the database (that's
  the observation module, task 8, consuming its `TxResult` output later).

## Acceptance Criteria

Derived from the task statement's own clauses plus R6:

- **AC1 (R6, L4).** `getFinalityStatus` reports the real `finalized`-tagged block number
  (`DefaultBlockParameterName.FINALIZED`), never a confirmation-count-based approximation.
- **AC2 (ChainAdapter contract, T05).** `getTx` for a transaction the provider hasn't observed
  returns `TxResult(exists=false, ...)`, never throws; a genuine provider/transport failure throws
  unchecked.
- **AC3 (L7).** `getTokenInfo` is keyed by the `contractAddress` parameter alone; the returned
  `TokenInfo.symbol` is sourced from the chain, never invented or defaulted.
- **AC4 (task statement, "Provider credentials via config").** Ethereum provider endpoint URL and
  credential come from `ProviderProperties`, not a hardcoded value anywhere in `EthereumAdapter`.
- **AC5 (agents.md, testability).** `EthereumAdapter`'s own unit tests never make a real network call
  — achieved by constructor-injecting `Web3j` (an interface) so tests substitute a mock.
- **AC6 (O1).** `EthereumAdapter` works against any correctly-configured Ethereum-JSON-RPC-compatible
  endpoint — no vendor-specific assumption hardcoded into the adapter itself.

## Tests required

No named test exists in `package.md` §8 for this task (same situation as T05). Self-referential,
mirroring T05's own precedent:
- A test proving `getFinalityStatus` uses the `finalized` block tag, not a confirmation-count
  computation (AC1/R6) — with a mocked `Web3j`.
- A test proving `getTx` returns `exists=false` (not a throw) for a transaction `Web3j` reports as
  not found (AC2).
- A test proving `getTokenInfo` correctly decodes a mocked ERC-20 `symbol()`/`decimals()` response
  (AC3).
- A test proving provider URL/credentials come from config, not a hardcoded value (AC4) — likely a
  wiring-level test once Phase 2 settles the config-to-`Web3j` construction shape.

## Open Questions

**Not genuine external blockers for building/testing this task** (Q1 itself says so explicitly for
provider vendor names) — but two real scoping decisions Phase 2 must make by engineering judgment,
since nothing elsewhere in the spec answers them for Ethereum specifically:

1. **`subscribeAddress` transport for Ethereum (O2).** Websocket-based (`web3j.ethLogFlowable`/
   `transactionFlowable`, needs a `WebSocketService`, not all provider tiers support it) vs.
   HTTP-polling (works with any HTTP-only endpoint, needs a polling-interval budget). Phase 2 must
   pick one for this task's own scope; the config already provides plain `url` values with no
   protocol hint either way.
2. **`apiKeySecretName` resolution mechanism.** Environment-variable lookup (matching the K8s/External
   Secrets Operator pattern agents.md describes elsewhere) vs. something else. Affects exactly how
   `EthereumAdapter`'s wiring turns a `ProviderEntry` into an authenticated `Web3j`/`HttpService`.
3. **Whether `EthereumAdapter` should defensively re-validate addresses (EIP-55) itself**, given
   `AddressValidator` (task 12) is a separate, not-yet-built component — Phase 2 must decide whether
   this task assumes valid input or adds its own boundary check, and document the choice either way.
