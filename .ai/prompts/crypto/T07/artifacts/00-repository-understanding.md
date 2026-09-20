# crypto · T07 · Phase 0 — Repository Understanding

## 1. Architecture summary of this service (as it exists today)

Six tasks shipped: T01 (skeleton), T02 (`chain` schema), T03 (`common/` config + resource server),
T04 (`events/` outbox), T05 (`adapter/` contract + fakes), T06 (`adapter/eth/EthereumAdapter` — the
first real `ChainAdapter`, backed by `web3j` against a single configured RPC provider).

**T07 is the second real `ChainAdapter` implementation and the first task to talk to Tron.** It has a
direct, already-built sibling to mirror: `EthereumAdapter`/`EthereumAdapterConfig` (T06) establish the
concrete shape — one adapter instance per configured provider entry, credential resolution via
`Environment`, `AutoCloseable` resource lifecycle, a virtual-thread-backed polling scheduler for
`subscribeAddress` — all of which are now precedent, not open questions, for whichever parts of them
carry over to Tron.

`services/crypto/pom.xml` (T01) already declares `io.github.tronprotocol:trident:1.0.0` — the only
Tron-capable dependency present; no separate TronGrid HTTP client library exists in the POM, so
"TronGrid / java-tron gRPC" (the task statement's own parenthetical) most likely resolves to trident's
gRPC client against a java-tron full node, not a TronGrid REST/HTTP client. `mvn dependency:tree`
confirms trident resolves a substantial transitive tree: `guava:33.0.0`, `grpc-netty`/`grpc-netty-
shaded`/`grpc-okhttp`/`grpc-protobuf`/`grpc-stub:1.81.0`, `protobuf-java(-util):3.25.8`,
`bouncycastle:bcprov-jdk18on:1.84`, `fastjson2:2.0.55`, `vertx-core:4.5.27` (with its own `netty-*`
chain). This is a much heavier dependency surface than `web3j:core` (T06) — worth a `-Dverbose`
conflict check once real trident classes are actually used in code (T06's own Jackson-annotations
lesson: a resolved-but-silently-wrong transitive version doesn't surface until the affected class is
actually constructed).

## 2. Existing code this task touches — what's already there vs. new

**Already exists (context, extend/consume, do not modify without cause):**
- `adapter/ChainAdapter.java` (T05) — the interface `TronAdapter` implements, identical to what
  `EthereumAdapter` implements. Its class Javadoc (T05 Phase 9) is the binding contract: an unchecked
  exception means the provider/transport couldn't answer; `getTx` for an unobserved tx returns
  `TxResult(exists=false, ...)`, never throws; `getTokenInfo` has no allowlist awareness;
  `getFinalityStatus` assumes the caller already confirmed existence.
- `adapter/model/{TxResult,TokenInfo,FinalityStatus,Subscription}.java`, `adapter/ObservationSink.java`
  (T05) — fixed shapes, not renegotiable. `FinalityStatus.finalizedBlockNumber` is a single
  non-nullable `long` — for Tron this is the **solidified block's number** (design.md §4a L4's
  verbatim finality table: "block is solidified (~19 confirmations toward the solidified block)"),
  distinct in meaning from Ethereum's beacon `finalized` checkpoint but stored in the same field —
  T05's own deliberate unification, already exercised once by T06, now exercised a second way by T07.
- `adapter/eth/EthereumAdapter.java` / `EthereumAdapterConfig.java` (T06) — **the direct precedent for
  this task's shape.** Concrete patterns already established there that Phase 1/2 will need to decide
  whether to mirror or diverge from for Tron: one adapter instance per configured provider entry;
  `apiKeySecretName` resolved via Spring `Environment`; `{apiKey}` URL-placeholder substitution for
  credential attachment (Tron's own credential-attachment mechanism is unknown — trident is a gRPC
  client, not an HTTP client with a request URL, so the URL-templating mechanism T06 built may not
  transfer directly); `AutoCloseable` + `@PreDestroy` resource lifecycle; virtual-thread-backed
  `ScheduledExecutorService` for polling; fixed-delay (never fixed-rate) scheduling; `confirmations =
  current - tx + 1`; `fromAddress`/`toAddress` sourced from the transfer event's data, not a raw
  transaction-level field (T06's ERC-20 `Transfer` log-topic precedent — Tron's TRC-20 equivalent
  needs its own confirmation of where the analogous data lives).
- `common/config/ProviderProperties.java` (T03) — already has a `local`-profile TRON entry in
  `application.properties` (`fake-tron-provider-a`, fake URL, `local-only-fake-provider-key`
  placeholder secret name), one provider only (vs. Ethereum's two), under the same
  `quorum-threshold=2` — meaning a real Tron deployment needs at least one more provider entry before
  quorum could ever be reached for Tron specifically; not this task's problem to fix (O1 provider-set
  sizing is still open per package.md §11 Q1), but worth carrying forward as a known gap.
- `adapter/FakeChainAdapter.java` (T05, test scope) — the scripted-fake precedent; `EthereumAdapterTest`
  (T06) is the more directly relevant precedent for how `TronAdapter`'s own tests must mock its client
  library entirely, never touching a real or containerized Tron node.

**New in this task (per design.md §6 file map, scoped to T07's statement):**
- `adapter/tron/TronAdapter.java` — implements `ChainAdapter` for real, via trident (design.md §6
  names this exact path: `tron/TronAdapter.java (TronGrid / java-tron gRPC)`).
- Whatever Spring wiring constructs the real trident client(s) from `ProviderProperties`' TRON
  entries — same "functionally necessary, not spec-named" situation `EthereumAdapterConfig` was in for
  T06 (design.md §6 names only `TronAdapter.java` under `tron/`).

## 3. Established patterns to follow

- **T06 is now the load-bearing precedent, not a blank slate.** Unlike T06 (which had no
  config-to-adapter wiring precedent at all), T07 has a working sibling to compare against at every
  design decision: same `ChainAdapter` interface, same `ProviderProperties` shape, same
  `FinalityStatus`/`TxResult` types. Where Tron's actual chain semantics or trident's actual API shape
  force a genuine divergence from `EthereumAdapter`'s choices, that divergence needs its own explicit
  justification in Phase 2/3 — mirroring T06's own amendment-by-amendment reasoning, not silently
  copying a pattern that doesn't fit.
- **Credential attachment mechanism is unresolved for Tron specifically.** T06 resolved this for
  Ethereum via URL-templating because `HttpService` takes a URL string. trident is a gRPC client
  (`ManagedChannel`-based, typically) — grpc-java credential attachment (call credentials,
  channel-level auth) is a structurally different mechanism than substituting into a URL string. This
  is a genuine Phase 1/2 question, not something T06's precedent already answers.
- **Module boundaries (L15):** `TronAdapter` goes under `adapter/tron/`, matching design.md §6's file
  map exactly, mirroring `adapter/eth/`'s own precedent.

## 4. Testing conventions

- agents.md: "real RPC providers are never called in tests or CI" — applies identically to trident/
  Tron as it did to web3j/Ethereum in T06. `TronAdapter`'s own unit tests must mock trident's client
  types entirely (Mockito), the same way `EthereumAdapterTest` mocked `Web3j`/`Request<?,T>`. The exact
  mocking shape (how granular trident's own client/request/response types are) is unknown until Phase
  5/6 inspects the actual library, the same `javap`-verification discipline T06 used throughout rather
  than trusting memory of the library's API.
- No ArchUnit test exists yet anywhere in this service (task 25's concern, not this task's).
- Fixed `Clock` convention (agents.md) — same open question T06 carried: not yet clear whether this
  task needs one (depends on whether any time-based logic, e.g. timeout handling, lands in this task).

## 5. Known gaps / unknowns

- **I do not know trident's actual client API shape** — its own package/class structure, how a gRPC
  channel/credentials are constructed, what the equivalent of "get transaction by hash," "get TRC-20
  token info," "watch an address," and "get the solidified block number" look like as trident method
  calls. This needs direct library inspection (Phase 5/6), not assumption from web3j's shape.
- **I do not know whether trident exposes a direct "solidified block" query**, or whether Tron
  finality (R7, design.md's "~19 confirmations toward the solidified block") has to be computed from a
  raw block-height difference instead of a named tag the way `DefaultBlockParameterName.FINALIZED`
  gave T06 a direct query for Ethereum. This materially affects whether `TronFinalityPolicy`-adjacent
  logic belongs in this task's adapter at all, or purely in the later `finality/TronFinalityPolicy.java`
  (task 14, design.md §6) — a Phase 1/2 scoping question.
- **I do not know Tron's transfer-event-log equivalent to Ethereum's ERC-20 `Transfer` topic** — TRC-20
  tokens do emit a `Transfer` event, but whether trident surfaces raw event logs the way web3j's
  `eth_getLogs` does, or requires a different query shape (e.g. TronGrid's own log/event-query REST
  endpoints, which trident may or may not wrap), is unconfirmed.
- **I do not know the correct credential-attachment mechanism for a gRPC-based Tron provider** — see
  §3 above. `ProviderEntry.url`'s `{apiKey}` templating (T06) assumes an HTTP URL; a gRPC channel's
  natural credential-attachment points (call metadata, channel credentials) are different in kind, not
  just detail.
- **R16 (Base58Check Tron address validation, L8)** is a scoped requirement/decision for *some* task in
  this package, but T06's own precedent explicitly put EIP-55 (EVM) address validation **out of
  scope** for `EthereumAdapter` itself ("trusts the caller"). Whether R16/L8 land inside `TronAdapter`
  or in a separate validation layer this task doesn't own is a Phase 1 scoping question, not decided
  here — but the T06 precedent leans toward "not this task's job."
- No named test exists in `package.md` §8 for this task specifically (same situation T05/T06 were in —
  §8's named tests map to R1/R2/etc., none of which are Tron-adapter-specific by name).

Do not design and do not extract requirements yet — that is Phase 1. This artifact stops here.
