# crypto · T06 · Phase 0 — Repository Understanding

## 1. Architecture summary of this service (as it exists today)

Five tasks shipped: T01 (skeleton), T02 (`chain` schema), T03 (`common/` config + resource server),
T04 (`events/` outbox), T05 (`adapter/` contract + fakes — `ChainAdapter`, `Chain`, `TxResult`,
`TokenInfo`, `FinalityStatus`, `Subscription`, `ObservationSink`, and test-scope `FakeChainAdapter`).

**T06 is the first task to write code that talks to a real external system** (an Ethereum RPC
provider, via web3j) — every prior task was either pure config/security wiring (T03), internal
messaging (T04), or a pure-Java contract with no I/O (T05). This changes the testing posture
significantly: agents.md's "real RPC providers are never called in tests or CI" becomes directly
operative for the first time.

`services/crypto/pom.xml` (T01) already declares `org.web3j:core:6.0.0` — no other web3j module
(no `web3j-codegen`, no pre-generated ERC-20 contract wrapper). `org.web3j.protocol.Web3j` (the
library's main client type) is itself an **interface**, built via
`Web3j.build(new HttpService(url))` or similar — meaning a `Web3j` instance can be constructor-
injected and substituted with a test double, without needing web3j's own test-support module.

## 2. Existing code this task touches — what's already there vs. new

**Already exists (context, extend/consume, do not modify without cause):**
- `adapter/ChainAdapter.java` (T05) — the interface `EthereumAdapter` implements. Its class Javadoc
  (added at T05's Phase 9) already fixes the contract this task must honor: an unchecked exception
  means the provider/transport couldn't answer at all; `getTx` for an unobserved tx returns
  `TxResult(exists=false, ...)`, never throws; `getTokenInfo` has no allowlist awareness (returns raw
  provider-reported metadata only); `getFinalityStatus` assumes the caller already confirmed
  existence.
- `adapter/model/{TxResult,TokenInfo,FinalityStatus,Subscription}.java`, `adapter/ObservationSink.java`
  (T05) — `EthereumAdapter`'s methods return/consume these types; their shapes are fixed, not
  renegotiable by this task. Notably: `FinalityStatus.finalizedBlockNumber` is a single non-nullable
  `long` — for Ethereum this is the beacon-chain `finalized` checkpoint's block number (design.md
  §4a L4's own verbatim finality table: "block is at or below the beacon-chain `finalized`
  checkpoint. (NOT a block-count.)").
- `common/config/ProviderProperties.java` (T03) — **this task is the first real consumer.** Already
  shipped, already tested, generic shape: `chains[].providers[].{name, url, timeoutSeconds,
  apiKeySecretName}`, `quorumThreshold`. `application.properties` already has two `local`-profile
  ETHEREUM provider entries (fake URLs, `local-only-fake-provider-key` placeholders) plus one TRON
  entry, with `quorum-threshold=2` — consistent with T05's `ChainAdapter` Javadoc: "Each provider is
  one instance of this interface." This strongly implies `EthereumAdapter` is instantiated **once per
  configured Ethereum provider entry**, not once globally.
- `adapter/FakeChainAdapter.java` (T05, test scope) — the established scripted-fake pattern; not
  extended by this task, but the precedent for how `EthereumAdapter`'s own tests must avoid any real
  network call.

**New in this task (per design.md §6 file map, scoped to T06's statement):**
- `adapter/eth/EthereumAdapter.java` — implements `ChainAdapter` for real, via web3j.
- Whatever Spring wiring constructs the real `Web3j` client(s) from `ProviderProperties`' Ethereum
  entries (a `@Configuration`/`@Bean` class — not named in design.md §6's file map at all, same
  "functionally necessary but not spec-named" situation T04 hit with `OutboxRelay`).

## 3. Established patterns to follow

- **Config-to-adapter wiring has no precedent yet.** `ProviderProperties` has existed since T03 but
  nothing has consumed it. There is no established pattern in this codebase for turning a
  `ProviderProperties.ProviderEntry` into a running `Web3j` client — this task sets that precedent.
- **`apiKeySecretName`'s resolution mechanism is undefined** (see Known Gaps) — T03's own Javadoc
  only describes it as "an External Secrets reference, not the key itself," without specifying
  whether that means an environment-variable name, a Secrets Manager ARN, or something else.
- **Module boundaries (L15):** `EthereumAdapter` goes under `adapter/eth/`, matching design.md §6's
  file map exactly; any new config-wiring class most likely belongs under `common/` (shared plumbing)
  or `adapter/eth/` itself, per how narrowly it's scoped — a Phase 1/2 call, not decided here.
- **Testing precedent from other real-I/O-adjacent tasks:** T02/T04 used Testcontainers for real
  Postgres/schema behavior — but agents.md's explicit "real RPC providers are never called in tests
  or CI" rules out an equivalent "real Ethereum node" integration test entirely. The nearest
  precedent is T05's `FakeChainAdapter` (a scripted double) and T04's `OutboxRelayTest` (Mockito
  mocking the external `KafkaTemplate` dependency) — `EthereumAdapter`'s own tests will almost
  certainly need to mock `Web3j` (and whatever `Request`/`EthGetTransactionReceipt`-style response
  objects web3j returns) with Mockito, the same way `KafkaTemplate` was mocked in T04, rather than
  hitting any real or containerized Ethereum node.

## 4. Testing conventions

- agents.md: "Local dev runs against Docker Compose (Postgres + Kafka) and scripted fake provider
  adapters — real RPC providers are never called in tests or CI." This is a hard constraint on this
  task specifically, more than any prior one — `EthereumAdapter`'s own unit tests must mock web3j's
  `Web3j` interface (and the `Request<?, T>`/response-object chain its methods return) rather than
  connecting to anything real, local Docker Compose node or otherwise.
- No ArchUnit test exists yet anywhere in this service (per T05's own Phase 1 finding, task 25 owns
  that). Not this task's concern either.
- Fixed `Clock` convention (agents.md) — relevant if `EthereumAdapter` or its wiring does any
  time-based logic (e.g. timeout handling); not yet clear whether this task needs one.

## 5. Known gaps / unknowns

- **I do not know exactly how `apiKeySecretName` is resolved into a usable credential at runtime.**
  T03's Javadoc calls it "an External Secrets reference, not the key itself" but never specifies the
  resolution mechanism (environment variable lookup? a secrets-manager SDK call? something else?).
  Since `pom.xml` has no AWS Secrets Manager (or similar) SDK dependency, and the task statement's own
  wording ("Provider credentials via config") suggests a config-level, not an SDK-call-level,
  resolution — but this is inference, not a confirmed fact. Phase 1/2 must resolve this precisely.
- **I do not know the intended `subscribeAddress` transport for Ethereum** — design.md §4b-O2
  ("Watcher transport & concurrency") is explicitly an **unresolved OPEN decision**: "Propose
  per-provider websocket-subscription vs polling and the polling-interval budget... Recommend one;
  proceed if low-risk." This task is the first one that must actually implement `subscribeAddress`
  for a real chain, meaning T06 likely has to resolve O2 for Ethereum specifically (websocket-based
  `web3j.ethLogFlowable`/`transactionFlowable` vs. HTTP-polling), not just inherit an answer from
  elsewhere in the spec.
- **Verified, not a gap: web3j 6.0.0 supports querying the beacon-chain finalized checkpoint via the
  execution-layer JSON-RPC alone** — `DefaultBlockParameterName.FINALIZED` (confirmed present in
  web3j's `DefaultBlockParameterName` enum via web3j's own GitHub source and javadoc, and web3j 6.0.0
  is a recent release well past the block-tag's introduction). This means `eth_getBlockByNumber` with
  the `"finalized"` tag can supply `FinalityStatus.finalizedBlockNumber` directly — **no separate
  Beacon Chain API client is needed**, contrary to what might otherwise have been assumed. This
  resolves what would otherwise have been a major open architecture question for L4's Ethereum
  finality requirement.
- **I do not know the exact ERC-20 ABI-call mechanism `getTokenInfo` should use.** `pom.xml` has only
  `web3j:core` (which includes low-level ABI encode/decode utilities, `org.web3j.abi.FunctionEncoder`/
  `FunctionReturnDecoder`), not `web3j-codegen`'s generated contract wrapper classes — meaning
  `symbol()`/`decimals()` calls must be manually ABI-encoded unless this task adds a new dependency
  (out of its own stated scope unless justified in Phase 1/2).
- **I do not know** whether `EthereumAdapter` needs its own `@ConfigurationProperties`/`@Bean` wiring
  class or whether Spring wiring for it belongs somewhere else — no file for this is named in
  design.md §6's package map at all (only `EthereumAdapter.java` itself is listed under `eth/`).
- No named test exists in `package.md` §8 for this task (same situation as T05).

Do not design and do not extract requirements yet — that is Phase 1. This artifact stops here.
