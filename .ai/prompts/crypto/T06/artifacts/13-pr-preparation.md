# crypto · T06 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to prepare T06 for merge, per that gate.

**Note on this repo's actual git history:** as with T01–T05, this session's phase-boundary work has
already been captured across several small commits on the current branch
(`spec/service-specs-and-ai-framework`, off `main`). The material below is prepared as the **logical
PR description for the whole of T06**, not as a claim that one new commit contains all of it. The only
files still uncommitted as of this phase are the Phase 11 test-review resolution (production fix +
doc note + 9 new tests, across the 4 files listed below) and the Phase 12 verification artifact.
**No commit or push has been made** — repo-wide instructions require an explicit go-ahead before
committing.

## Commit title

```
crypto-service: Ethereum adapter (T06)
```

## Commit message

```
crypto-service: Ethereum adapter (T06)

Add EthereumAdapter, the first real ChainAdapter implementation
(getTx/getTokenInfo/subscribeAddress/getFinalityStatus), backed by
web3j against a single configured provider endpoint, and
EthereumAdapterConfig, which wires one adapter per configured
ProviderProperties Ethereum entry.

- Credentials are resolved via Spring's Environment and substituted
  into a {apiKey} placeholder in the provider URL - the mechanism
  real URL-embedded-key providers (Alchemy/Infura/QuickNode-style)
  need; fails fast at wiring time if the placeholder is present but
  the value doesn't resolve, stays silent when no placeholder is
  present at all (covers the local profile's own fixture URLs).
- subscribeAddress polls eth_getLogs for the standard ERC-20
  Transfer(address,address,uint256) topic, filtered by recipient
  only - no contract-address filter, so a non-allowlisted contract's
  fake Transfer is reported and rejected downstream by TokenValidator
  (task 11), not silently dropped here. Cursor starts at LATEST, not
  genesis; fixed-delay scheduling on a virtual-thread-backed
  scheduler, matching OutboxRelay's own precedent.
- confirmations = currentBlock - txBlock + 1, so a transaction in the
  current latest block reads as 1 confirmation, not 0.
- fromAddress/toAddress are sourced from the Transfer log's topics,
  not the raw transaction's own from field - correct for a DEX-
  router or smart-contract-wallet-initiated transfer, and the field
  R17's address-poisoning attribution actually needs.
- getFinalityStatus uses the FINALIZED block tag (R6), never a
  confirmation-count approximation; throws for a not-found or
  unmined transaction (L4).

Went through the full 14-phase spec-driven pipeline. Phase 3/8
adversarial review (Kimi) surfaced 10 accepted findings, most
notably a real correctness bug where subscribeAddress's own matched-
log detection could disagree with a separate getTx(txHash)
re-lookup for a multi-Transfer transaction - fixed by building each
observation directly from the specific log a poll matched, never
round-tripping through getTx again. Also added AutoCloseable/
@PreDestroy resource lifecycle for the Web3j client and scheduler,
which nothing had been shutting down.

Phase 10 testing surfaced a real, production-affecting defect
unrelated to this task's own logic: web3j:core 6.0.0's Jackson-3
jackson-databind dependency requires jackson-annotations 2.21+, but
Spring Boot 3.5.4's imported jackson-bom silently downgrades it to
2.19.2, which is missing a class web3j's own annotation introspector
needs - EthereumAdapterConfig would have crashed with
NoClassDefFoundError on its first real HttpService construction, in
production, not just in tests. Fixed with a single
dependencyManagement version pin in services/crypto/pom.xml (no new
dependency), root-caused via the resolved dependency tree and each
candidate jar's own contents, not guessed at.

Phase 11 (Kimi test review) surfaced 12 gaps; 9 were closed with new
tests (cursor advancement, no-new-blocks early return, mined-tx-
with-null-receipt fallback, receipt IOException propagation,
scheduler delay/period wiring, multiple-observations-per-poll,
@PreDestroy actually shutting down every adapter's real scheduler,
no-adapters-when-only-TRON-configured), one closed with a small
production robustness fix (getFinalityStatus now guards against a
provider reporting FINALIZED ahead of its own LATEST block, mirroring
the existing confirmations negative-check pattern), one closed with
a documentation-only Javadoc note (API-key URL substitution isn't
percent-encoded - deliberately left that way pending the still-
unresolved real provider choice, package.md Q1), and two acknowledged
with no action (close() idempotency isn't meaningfully testable with
mocks; async delivery via a real thread is a JDK ScheduledExecutorService
guarantee, not this class's own code, and already implicit in every
existing subscribeAddress test's design). Phase 12 traceability
matrix: PASS - all 11 acceptance criteria and R6/L4/L7/L13/L14/L15
implemented and tested, with the pom.xml pin disclosed as a
justified, narrow exception to the frozen brief's file list.

Task: spec/crypto-service/tasks.md #6
Requirements: R6
Locked decisions: L4, L7, L13, L14, L15

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed (complete T06 file set)

**Main:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfig.java` (new)
- `services/crypto/src/main/resources/application.properties` (modified — added
  `themistra.crypto.adapter.ethereum.poll-interval-ms`)
- `services/crypto/pom.xml` (modified — `jackson-annotations:2.21` `dependencyManagement` pin; see
  Phase 12's File-list compliance note for the justification)

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/adapter/eth/EthereumAdapterTest.java` (new — 25
  tests)
- `services/crypto/src/test/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfigTest.java`
  (new — 7 tests)

**Pipeline artifacts:** `.ai/prompts/crypto/T06/artifacts/00-*.md` through `12-*.md` (13 files).

**Not part of T06** — pre-existing/unrelated, untouched by this task: `adapter/ChainAdapter.java`,
`adapter/Chain.java`, `adapter/model/*.java`, `adapter/FakeChainAdapter.java` (T05, frozen, consumed
only); `common/config/ProviderProperties.java` (T03, frozen, consumed only); everything under
`common/`, `events/` outside this task's own additions.

**Still uncommitted as of this phase** (the Phase 11 resolution + Phase 12 artifact):
`EthereumAdapter.java`, `EthereumAdapterConfig.java` (both modified — Gap 10 fix + Gap 9 doc note),
`EthereumAdapterTest.java`, `EthereumAdapterConfigTest.java` (both modified — 9 new tests),
`.ai/prompts/crypto/T06/artifacts/12-specification-verification.md` (new). Everything else listed
above (main files through Phase 10) was already auto-committed earlier in this session.

## Summary

T06 gives crypto-service its first real, external-system-touching `ChainAdapter` — `EthereumAdapter`,
backed by `web3j` — plus the Spring wiring (`EthereumAdapterConfig`) that turns `ProviderProperties`
(T03, shipped but unused until now) into live provider instances. It is also the first task to expose
a genuine gap in the module's own dependency graph (the Jackson 3/`jackson-annotations` conflict),
found and fixed because this task's own tests deliberately exercise real `HttpService` construction
rather than mocking it away entirely.

## Testing performed

- `mvn -pl services/crypto -am compile` / `test-compile` — clean throughout.
- `mvn -pl services/crypto test -Dtest=EthereumAdapterTest,EthereumAdapterConfigTest` — **32/32**
  passing (25 + 7).
- `mvn -pl services/crypto -am test` (full module) — **163 tests, 0 failures**; the only errors are the
  3 pre-existing Docker-unavailable `Testcontainers` integration tests, unchanged from before this
  task and unrelated to it (same known limitation carried forward from T03/T04).
- Two separate mutation-based negative-proofs performed and reverted cleanly (`diff`-confirmed against
  pre-mutation backups): routing `pollOnce`'s observation-building back through `getTx(txHash)` (the
  exact bug Phase 9 Finding 1 fixed) broke the test written to catch it; disabling the Phase 11
  `FINALIZED > LATEST` guard broke the test written to catch that.
- A genuine dependency-resolution defect (see Commit message) was found, root-caused via
  `mvn dependency:tree -Dverbose` and direct jar-content inspection (not guessed at), and fixed with a
  verified, minimal `pom.xml` change.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 6 — "Ethereum adapter."
- **Requirements:** R6 (`getFinalityStatus` uses the real `finalized` checkpoint, not a fixed
  confirmation count).
- **Locked decisions:** L4, L7, L13, L14 (not directly applicable — this is a direct adapter, not a
  sidecar; recorded as such in Phase 12), L15 (derived in Phase 1 from `design.md` §4a — none were
  cited inline in the task header).
- **Named test:** none pre-mapped in `package.md` §8; this task's Required Tests were derived from its
  own acceptance criteria (Phase 5).
- **Standing rules:** `spec/crypto-service/agents.md` — followed throughout; never modified.

---

**This artifact is preparation only.** No `git commit`, `git push`, or PR was created. If you'd like me
to commit the pending Phase 11/12 delta now (the 5 files listed above), say so and I will — repo-wide
instructions require that explicit go-ahead first.
