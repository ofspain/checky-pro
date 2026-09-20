# crypto · T07 · Phase 1 — Specification Extraction

## Business Rules

- **R7.** WHEN evaluating Tron finality, THEN the system SHALL require the transaction's block to be
  solidified (~19 confirmations) per the Tron finality policy object. **This is the one requirement
  independently testable by this task's own deliverable** — `getFinalityStatus` must report the real
  solidified-block number, never a hardcoded confirmation-count arithmetic substituted for it. Unlike
  T06's R6 (where `web3j`'s `DefaultBlockParameterName.FINALIZED` gave a direct named-tag query),
  whether trident exposes an equivalent direct "solidified block" query or requires computing one from
  raw block data is unconfirmed (Phase 0 gap) — Phase 2 must resolve this by inspecting the library,
  not by assuming parity with Ethereum's mechanism.

No other numbered requirement is independently testable by `TronAdapter` alone — identical reasoning
to T06's own Phase 1 finding, restated for Tron:
- R1 (2-of-3 quorum) — `TronAdapter` is *one* provider instance the quorum module (task 9) fans out
  across; it doesn't implement quorum logic itself.
- R4 (persist verbatim before quorum decision) — the observation module's job (task 8), not this
  adapter's.
- R9 (confirmation count on `chain.tx.confirmed`) — event emission is task 17's job; `TronAdapter`
  only needs to populate `TxResult.confirmations` correctly. **Q4 (package.md §11) is directly
  relevant here**: the confirmation-count *basis* for Tron (solidified-block depth vs. some other
  count) is an open question the author must confirm so the Payment Service displays it consistently
  — not a blocker for building/testing this task, but a real semantic question Phase 2 must make an
  explicit, documented choice about rather than silently assuming Ethereum's arithmetic transfers
  unchanged.
- R13 (token identity by contract address) — `getTokenInfo` must be keyed by the `contractAddress`
  parameter (already fixed by `ChainAdapter`'s contract, T05); the identity rule itself is R13/L7's
  concern, not independently re-tested here beyond "the adapter doesn't invent its own identity
  scheme."
- R16 (Base58Check address validation) — design.md §6's package map gives this its own component
  (`token/AddressValidator.java`-equivalent, task 12), separate from any `ChainAdapter` implementation.
  T06's own precedent for the same question (R15/EIP-55) left `EthereumAdapter` without defensive
  validation, trusting the caller. Whether `TronAdapter` should do the same is a genuine Phase 2
  question (see Open Questions), not assumed either way here.

## Locked Decisions

Derived from `design.md` §4a:

- **L4.** `TronAdapter.getFinalityStatus` returns raw block data only (`txBlockNumber`,
  `currentBlockNumber`, `finalizedBlockNumber` — T05's fixed `FinalityStatus` shape, unchanged) — it
  must not itself decide "is this transaction final," that's `TronFinalityPolicy`'s job (task 14).
  Per the verbatim finality table (design.md §4c): Tron's `finalizedBlockNumber` field carries the
  **solidified block's number**, not a beacon-chain checkpoint — same field, chain-specific meaning,
  exactly as T05 designed it to.
- **L7.** `getTokenInfo` returns whatever the chain/provider reports for the given `contractAddress` —
  `TronAdapter` has no allowlist awareness, mirroring `EthereumAdapter`'s T06 precedent exactly.
- **L8.** Address validation (Base58Check for Tron) is mandatory *somewhere* in the system, but per T06's
  precedent for the EVM half of this same decision, likely not inside the adapter itself — see Open
  Questions.
- **L13.** No provider API key or endpoint credential is committed; config fails startup on
  missing/invalid values in non-local profiles. `ProviderProperties` (T03) already enforces this
  generically; this task is a consumer, not the rule's origin.
- **L14.** `TronAdapter` must conform to exactly the same `ChainAdapter` contract a sidecar-backed
  implementation would — nothing in this task's own implementation may assume capabilities beyond what
  the interface itself exposes.
- **L15.** New file goes under `adapter/tron/`, matching design.md §6's file map exactly.

Also directly relevant, cited inline in the task statement itself:
- **O1 (design §4b, Q1 unresolved).** The specific 3 launch providers per chain (Tron's own set is
  literally written as "TronGrid + ? + ?" in package.md §11 Q1) are unresolved — "Blocker for real
  deployment (not for fake-provider tests)." `TronAdapter` must be provider-agnostic wherever trident's
  own API allows it (works against any correctly-configured Tron full-node/gRPC endpoint), never
  hardcoded to TronGrid's specific quirks — this task is not blocked by Q1 remaining open.
- **O2 (design §4b, unresolved for Tron specifically).** T06's frozen brief explicitly scoped its own
  HTTP-polling resolution of O2 to **"Ethereum, this task only"** — meaning O2 remains genuinely open
  for Tron. Phase 2 must make its own transport call for `TronAdapter`, informed by whatever
  subscription/polling trident's gRPC client actually supports, not by copying T06's HTTP-polling
  choice on the assumption it transfers.

## Files involved

**Existing — read/extend:**
- `adapter/ChainAdapter.java`, `adapter/model/*.java`, `adapter/ObservationSink.java` (T05) — the
  interface and types `TronAdapter` implements/returns; not modified.
- `adapter/eth/EthereumAdapter.java`, `adapter/eth/EthereumAdapterConfig.java` (T06) — not modified,
  but the direct sibling precedent Phase 2/3 will compare every design choice against.
- `common/config/ProviderProperties.java` (T03) — already has one `local`-profile TRON provider entry
  (`fake-tron-provider-a`); not modified.
- `services/crypto/src/main/resources/application.properties` — already has the TRON provider entry
  and `themistra.crypto.finality.enabled-chains` includes `TRON`; may need a Tron-specific
  wiring-relevant addition (e.g. a poll-interval property, if O2 resolves to polling again) — TBD
  Phase 2.
- `services/crypto/pom.xml` — `io.github.tronprotocol:trident:1.0.0` already present (T01); no new
  dependency expected unless Phase 2 finds trident genuinely insufficient for a required call.

**New — expected by design.md §6:**
- `adapter/tron/TronAdapter.java` — named explicitly in the task statement and design §6
  (`tron/TronAdapter.java (TronGrid / java-tron gRPC)`).
- **Not named in design.md §6's file map, but likely functionally necessary** (mirrors T06's own
  `EthereumAdapterConfig` situation): a Spring wiring class constructing real trident client
  instance(s) from `ProviderProperties`' TRON entries — exact shape/name is Phase 2 design work, and
  the credential-attachment mechanism cannot simply reuse T06's URL-templating (a gRPC channel has no
  request URL to substitute into — see Open Questions).

## Dependencies

- `io.github.tronprotocol:trident:1.0.0` (already present) — exact client class/method shapes for
  transaction lookup, TRC-20 token metadata, address watching, and solidified-block query are
  **unconfirmed** (Phase 0 gap); Phase 5/6 must inspect the actual library before writing code against
  it, the same `javap`/direct-inspection discipline T06 used for `web3j` rather than trusting memory.
  Its own transitive tree (`grpc-*:1.81.0`, `netty-*:4.1.123.Final`, `protobuf-java:3.25.8`,
  `guava:33.0.0-jre`, `vertx-core:4.5.27`, `bouncycastle:1.84`) is substantially heavier than
  `web3j:core`'s — worth a `-Dverbose` dependency-tree check once real trident classes are constructed,
  given T06's own Jackson-annotations lesson that a silently-wrong transitive version doesn't surface
  until the affected code path actually runs.
- `ProviderProperties.ProviderEntry` (`name`, `url`, `timeoutSeconds`, `apiKeySecretName`) — the config
  surface this task's Tron provider instances are built from. **`url`'s shape for a gRPC target is
  unconfirmed** — whether the existing string field cleanly carries a `host:port` gRPC target the way
  it carried an HTTP URL for T06, or needs a different convention, is a genuine Phase 2 question.
- `apiKeySecretName` resolution mechanism — **already resolved by T06's precedent**: Spring
  `Environment.getProperty(...)`, an environment-variable lookup. Not open for this task; only the
  *attachment* mechanism (how the resolved value reaches trident's client) is open.
- No Spring Data/persistence dependency — `TronAdapter` itself does not touch the database (the
  observation module, task 8, consumes its `TxResult` output later).

## Acceptance Criteria

Derived from the task statement's own clause ("against the same interface") plus R7:

- **AC1 (R7, L4).** `getFinalityStatus` reports the real solidified-block number, never a
  confirmation-count-based approximation substituted for it.
- **AC2 (ChainAdapter contract, T05).** `getTx` for a transaction the provider hasn't observed returns
  `TxResult(exists=false, ...)`, never throws; a genuine provider/transport failure throws unchecked —
  identical contract to `EthereumAdapter`'s (T06), since both implement the same frozen interface.
- **AC3 (L7).** `getTokenInfo` is keyed by the `contractAddress` parameter alone; the returned
  `TokenInfo.symbol` is sourced from the chain, never invented or defaulted.
- **AC4 (task statement, "against the same interface").** `TronAdapter` implements `ChainAdapter`
  with no capability beyond what the interface exposes — `chain()` returns `Chain.TRON`.
- **AC5 (task statement, "Provider credentials via config", mirroring T06's AC4).** Tron provider
  endpoint/credential come from `ProviderProperties`, not a hardcoded value anywhere in `TronAdapter`.
- **AC6 (agents.md, testability, mirroring T06's AC5).** `TronAdapter`'s own unit tests never make a
  real network call — achieved by constructor-injecting whatever trident client type Phase 2 identifies
  as the injectable/mockable seam, the same pattern `Web3j` gave T06.
- **AC7 (O1, mirroring T06's AC6).** `TronAdapter` works against any correctly-configured Tron
  full-node/gRPC endpoint — no vendor-specific (TronGrid-only) assumption hardcoded into the adapter
  itself, wherever trident's own API allows that generality.

## Tests required

No named test exists in `package.md` §8 for this task (same situation as T05/T06). Self-referential,
mirroring T06's own precedent:
- A test proving `getFinalityStatus` uses the real solidified-block query (however trident exposes
  it), not a confirmation-count computation (AC1/R7) — with a mocked trident client.
- A test proving `getTx` returns `exists=false` (not a throw) for a transaction the mocked client
  reports as not found (AC2).
- A test proving `getTokenInfo` correctly decodes a mocked TRC-20 `symbol()`/`decimals()`-equivalent
  response (AC3).
- A test proving `chain()` returns `Chain.TRON` (AC4).
- A test proving provider URL/credentials come from config, not a hardcoded value (AC5) — likely a
  wiring-level test once Phase 2 settles the config-to-trident-client construction shape.

## Open Questions

**Not genuine external blockers for building/testing this task** (Q1 itself says so explicitly for
provider vendor names) — but several real scoping decisions Phase 2 must make by engineering judgment,
since nothing elsewhere in the spec answers them for Tron specifically:

1. **trident's actual client API shape (Phase 0 gap, carried forward).** Direct library inspection is
   required before Phase 2 can finalize a design — this is the single highest-leverage unknown, since
   it determines the answer to nearly every other open question below.
2. **`subscribeAddress` transport for Tron (O2, genuinely unresolved — T06 explicitly scoped its own
   resolution to Ethereum only).** Depends entirely on what trident's gRPC client supports:
   subscription-style streaming vs. polling, and if polling, the interval budget.
3. **Credential-attachment mechanism for a gRPC-based provider.** T06's `{apiKey}` URL-templating
   assumes an HTTP request URL to substitute into; a gRPC channel's natural credential-attachment
   points (call credentials, channel-level metadata, or a raw API key parameter some Tron providers
   expect in-band) are structurally different. Whether `ProviderEntry.url`/`apiKeySecretName` even
   cleanly maps onto a gRPC target is itself part of this question.
4. **Solidified-block query mechanism (ties to R7 above).** Whether trident exposes a direct query
   analogous to `DefaultBlockParameterName.FINALIZED`, or whether solidified-block status must be
   computed from a raw block-height/confirmation-count difference — materially affects both
   `TronAdapter`'s own implementation and, per R7, must never degrade into a hardcoded confirmation
   count masquerading as "solidified."
5. **Confirmation-count basis for Tron (Q4, package.md §11 — explicitly still open).** Whether
   `TxResult.confirmations` for Tron should mirror T06's `current - tx + 1` convention directly, or
   needs its own basis given Tron's different block-time/finality model — the author's own confirmation
   is requested by Q4, not yet given.
6. **Whether `TronAdapter` should defensively re-validate addresses (Base58Check) itself**, mirroring
   the same question T06 left open for EIP-55 and ultimately deferred (trusts the caller). Phase 2
   must decide explicitly for Tron and document the choice, rather than silently inheriting T06's
   answer without re-examining whether it still holds.
