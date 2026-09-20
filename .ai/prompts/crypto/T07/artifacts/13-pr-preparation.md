# crypto · T07 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to prepare T07 for merge, per that gate.

**Note on this repo's actual git history:** as with T01–T06, this session's phase-boundary work has
already been captured across several small commits on the current branch
(`spec/service-specs-and-ai-framework`, off `main`). The material below is prepared as the **logical
PR description for the whole of T07**, not as a claim that one new commit contains all of it. The only
files still uncommitted as of this phase are the Phase 11 test-review resolution (production fix + 13
new tests, across the 3 files listed below) and the Phase 12 verification artifact.
**No commit or push has been made** — repo-wide instructions require an explicit go-ahead before
committing.

## Commit title

```
crypto-service: Tron adapter (T07)
```

## Commit message

```
crypto-service: Tron adapter (T07)

Add TronAdapter, the second real ChainAdapter implementation
(getTx/getTokenInfo/subscribeAddress/getFinalityStatus), backed by
trident against a single configured Tron gRPC endpoint, and
TronAdapterConfig, which wires one adapter per configured
ProviderProperties Tron entry - against exactly the same interface
EthereumAdapter (T06) implements, per the task statement's own
wording.

- Credentials attach via trident's own first-class
  ApiWrapperBuilder.withApiKey(...), skipped entirely when the
  resolved value is null/blank so the local-profile fixture (whose
  apiKeySecretName resolves to nothing locally) keeps working -
  cleaner than T06's URL-templating workaround, which Tron's gRPC
  target has no place for anyway.
- subscribeAddress polls block-by-block via
  getTransactionInfoByBlockNum (trident exposes no
  eth_getLogs-equivalent range query), filtered by recipient topic
  only - no contract-address filter, mirroring T06's detection
  philosophy exactly. Cursor starts at the current block, not
  genesis; fixed-delay scheduling on a virtual-thread-backed
  scheduler; catch-up per poll tick is capped so a subscription that
  fell far behind doesn't scan unboundedly in one tick.
- getFinalityStatus uses getNowBlock(NodeType.SOLIDITY_NODE) (R7) -
  trident's supported replacement for the deprecated
  getNowBlockSolidity() convenience method, discovered via
  javac -Xlint:deprecation during implementation, not assumed.
- fromAddress/toAddress are sourced from the TRC-20 Transfer log's
  topics for token transfers, and from Contract.TransferContract's
  own fields for native TRX transfers - two distinct address
  encodings (Tron-native 21-byte vs. EVM-style 20-byte), handled by
  two separate, explicitly-documented helpers so they can't be
  conflated.

Went through the full 14-phase spec-driven pipeline. Phase 3/8
adversarial review (Kimi) surfaced 22 accepted findings combined,
most concretely a bytecode-confirmed defect where the local-profile
Tron fixture's URL (http://localhost:9903/..., inherited from T03)
is not a valid gRPC target - grpc-java's ManagedChannelBuilder.forTarget
expects a bare host:port, and unlike T06's lazy HttpService, trident's
channel construction is eager, so this would have broken at Spring
context startup, not just at first RPC call. Also added a pollOnce
exception boundary so one transient RPC failure can't silently and
permanently kill a subscription's polling (a pre-existing exposure
EthereumAdapter's own pollOnce still has - deliberately left
unfixed here as out of this task's scope, disclosed rather than
silently inconsistent), and a getFinalityStatus guard against a
transaction's own block being reported ahead of the chain head that
same provider just returned, mirroring the finalized-vs-current guard
already there.

Phase 6 implementation relied on direct bytecode inspection of
trident-1.0.0.jar throughout (no sources jar is available) rather
than assuming API shape from web3j's precedent - this caught a
materially different not-found signaling mechanism (IllegalException
with a message prefix, not a default-instance return), a deprecated
method, and a wrong address-decoding utility class in the original
plan, all corrected before they shipped as bugs.

Phase 12 traceability matrix: PASS - all 11 acceptance criteria and
R7/L4/L7/L13/L15 implemented and tested (45 new tests). Two risks
carried forward, disclosed rather than silently resolved: amendment
#12 (whether one ProviderEntry.url can serve both trident's
full-node and solidity-node gRPC endpoints) remains unverified
against a real provider, and EthereumAdapter's own matching pollOnce
exposure remains unfixed, now inconsistent with TronAdapter's.

Task: spec/crypto-service/tasks.md #7
Requirements: R7
Locked decisions: L4, L7, L13, L15

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed (complete T07 file set)

**Main:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapter.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/adapter/tron/TronAdapterConfig.java` (new)
- `services/crypto/src/main/resources/application.properties` (modified — added
  `themistra.crypto.adapter.tron.poll-interval-ms`; fixed the pre-existing TRON provider entry's
  `url` from an HTTP-shaped value to a valid `host:port` gRPC target, Phase 9)

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/adapter/tron/TronAdapterTest.java` (new — 37
  tests)
- `services/crypto/src/test/java/com/themistra/crypto/adapter/tron/TronAdapterConfigTest.java`
  (new — 8 tests)

**Pipeline artifacts:** `.ai/prompts/crypto/T07/artifacts/00-*.md` through `12-*.md` (13 files).

**Not part of T07** — pre-existing/unrelated, untouched by this task: `adapter/ChainAdapter.java`,
`adapter/Chain.java`, `adapter/model/*.java`, `adapter/FakeChainAdapter.java`, `adapter/eth/*` (T05/T06,
frozen, consumed only); `common/config/ProviderProperties.java` (T03, frozen, consumed only);
`services/crypto/pom.xml` (no new dependency — `trident:1.0.0` already present since T01).

**Still uncommitted as of this phase** (the Phase 11 resolution + Phase 12 artifact):
`TronAdapter.java` (modified — Gap 13 fix), `TronAdapterTest.java`, `TronAdapterConfigTest.java` (both
modified — 13 new tests), `.ai/prompts/crypto/T07/artifacts/12-specification-verification.md` (new).
Everything else listed above (main files through Phase 10, and the Phase 9 `application.properties`
URL fix) was already auto-committed earlier in this session.

## Summary

T07 gives crypto-service its second real `ChainAdapter` — `TronAdapter`, backed by `trident` — plus
the Spring wiring (`TronAdapterConfig`) that turns `ProviderProperties`' Tron entries into live
provider instances, against exactly the same interface `EthereumAdapter` (T06) implements. With two
real chain adapters now shipped, the quorum evaluator (task 9) has its first genuine multi-provider,
multi-chain surface to fan out across.

## Testing performed

- `mvn -pl services/crypto -am compile` / `test-compile` — clean throughout, zero warnings after the
  Phase 6 deprecation fix (`getNowBlock(SOLIDITY_NODE)` replacing the deprecated
  `getNowBlockSolidity()`).
- `mvn -pl services/crypto test -Dtest=TronAdapterTest,TronAdapterConfigTest` — **45/45** passing
  (37 + 8).
- `mvn -pl services/crypto -am test` (full module) — **208 tests, 0 failures**; the only errors are
  the 3 pre-existing Docker-unavailable `Testcontainers` integration tests, unchanged from before this
  task and unrelated to it.
- Two separate mutation-based negative-proofs performed and reverted cleanly (`diff`-confirmed against
  pre-mutation backups): disabling the `pollOnce` exception boundary broke the two tests written to
  catch it; disabling the Phase 11 `txBlockNumber > currentBlockNumber` guard broke the test written
  to catch that.
- Extensive `javap`/bytecode verification of `trident-1.0.0.jar` throughout Phases 5–11 (no sources
  jar available) — not-found signaling, a deprecated method, the correct Base58Check utility class,
  `withTimeout`'s millisecond unit, `getContract`'s never-throws/never-null behavior, and
  `decimals()`'s `Uint8` (not `Uint256`) output type were all confirmed this way rather than assumed;
  one Phase 11 test (`decimals()` "overflow") was corrected mid-phase after bytecode tracing showed
  its original premise didn't hold, rather than shipping a test that could never fail as intended.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 7 — "Tron adapter."
- **Requirements:** R7 (`getFinalityStatus` uses the real solidified-block query, not a fixed
  confirmation count).
- **Locked decisions:** L4, L7, L13 (derived in Phase 1 from `design.md` §4a — none were cited inline
  in the task header), L14 (not directly applicable — this is a direct adapter, not a sidecar;
  recorded as such in Phase 12), L15.
- **Named test:** none pre-mapped in `package.md` §8; this task's Required Tests were derived from its
  own acceptance criteria (Phase 5).
- **Standing rules:** `spec/crypto-service/agents.md` — followed throughout; never modified.

---

**This artifact is preparation only.** No `git commit`, `git push`, or PR was created. If you'd like me
to commit the pending Phase 11/12 delta now (the 4 files listed above), say so and I will — repo-wide
instructions require that explicit go-ahead first.
