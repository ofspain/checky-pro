# crypto · T05 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to prepare T05 for merge, per that gate.

**Note on this repo's actual git history:** as with T03/T04, this session's phase-boundary work has
already been captured across several small commits on the current branch
(`spec/service-specs-and-ai-framework`, off `main`) — the same working pattern established for
T01–T04. The material below is prepared as the **logical PR description for the whole of T05**, not
as a claim that one new commit contains all of it. The only files still uncommitted as of this phase
are the Phase 11 test-review follow-up (3 new/modified test files) and the Phase 12 verification
artifact — listed separately below. **No commit or push has been made** — repo-wide instructions
require an explicit go-ahead before committing.

## Commit title

```
crypto-service: adapter interface + fakes (T05)
```

## Commit message

```
crypto-service: adapter interface + fakes (T05)

Add ChainAdapter (copied verbatim from design.md §4c) and Chain, the
contract every chain integration implements from here forward - real
(EthereumAdapter/TronAdapter, tasks 6/7) or scripted
(FakeChainAdapter, this task). Also design the four supporting types
design.md names but never shapes (TxResult, TokenInfo, FinalityStatus,
Subscription) and ObservationSink, grounded in concrete downstream
uses rather than guessed at:

- TxResult carries the existence/amount/token/confirmations facts the
  observation log's own fact_type check constraint already fixes
  (V1__chain_baseline.sql); FinalityStatus carries raw block data
  only, no precomputed decision (L4) - and unifies Ethereum's
  beacon-finalized checkpoint and Tron's solidified block into one
  field, since design.md's own finality table phrases both as block-
  number thresholds.
- TokenInfo's identity is contractAddress only (L7); its Javadoc
  documents that record equality is NOT the identity comparison
  (equals/hashCode include symbol/decimals) rather than overriding
  the record's generated equality, which would itself be an
  anti-pattern - a decision made explicitly at the design-challenge
  gate and reaffirmed against a second review pass asking to revisit it.
- ChainAdapter's own class Javadoc states the contract three separate
  review-pass findings converged on from different angles: an
  unchecked exception means the provider/transport couldn't answer at
  all; a transaction not yet observed is a normal, successful
  TxResult(exists=false, ...); getTokenInfo has no allowlist awareness
  (UNKNOWN_TOKEN classification is TokenValidator's job, task 11); and
  getFinalityStatus assumes the caller already confirmed existence.
- FakeChainAdapter (test scope, per the task statement's own words):
  agree/disagree are emergent from scripting multiple instances with
  matching/mismatching data, never a special mode; simulateReorg is
  the one method that both re-scripts a transaction and pushes the
  new state to every subscription whose watched address matches -
  proven not to be a blanket broadcast, and proven to reach every
  matching subscriber, not just the first.

No numbered requirement is independently satisfied by this task's own
deliverable - it ships shape/plumbing for later tasks (6, 7, 9, 11,
14, 16) to implement business logic against, mirroring T01's own
skeleton-task precedent. First task in this series with zero Docker
dependency anywhere: every acceptance criterion was both written and
actually executed in this environment, not just reasoned through.

Went through the full 14-phase spec-driven pipeline: Phase 3/8
adversarial review (Kimi) surfaced 15 accepted findings across design
and test coverage, several resolved with a cheaper or more consistent
fix than literally proposed (e.g. three separate "what happens on a
missing/failed query" findings collapsed into one unified adapter
contract instead of three inconsistent sentinel mechanisms); Phase 4
and 9 human-approval gates recorded acceptance/rejection with reasons
for each, including one explicit re-affirmation of an already-decided
design choice. Phase 12 traceability matrix: PASS.

Task: spec/crypto-service/tasks.md #5
Requirements: none (shape/plumbing task, no R-ID independently satisfied)
Locked decisions: L4, L7, L14, L15

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed (complete T05 file set)

**Main:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/Chain.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ObservationSink.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TxResult.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TokenInfo.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/FinalityStatus.java` (new)
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/Subscription.java` (new)

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapter.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapterTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/adapter/ChainAdapterShapeTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/adapter/ChainTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/adapter/model/TokenInfoTest.java` (new)
- `services/crypto/src/test/java/com/themistra/crypto/adapter/model/FinalityStatusShapeTest.java` (new)

**Pipeline artifacts:** `.ai/prompts/crypto/T05/artifacts/00-*.md` through `11-*.md` (12 files).

**Not part of T05** — pre-existing/unrelated, untouched by this task: everything under `common/`,
`events/` (T01–T04); `pom.xml`, `README.md` (T01).

**Still uncommitted as of this phase** (the Phase 11 follow-up + Phase 12 artifact):
`ChainAdapterShapeTest.java`, `FakeChainAdapterTest.java` (both modified),
`ChainTest.java`, `model/TokenInfoTest.java`, `model/FinalityStatusShapeTest.java` (all new),
`.ai/prompts/crypto/T05/artifacts/10-test-generation.md` (modified — addendum),
`.ai/prompts/crypto/T05/artifacts/12-specification-verification.md` (new).

## Summary

T05 gives crypto-service the one contract every chain integration implements — `ChainAdapter` — plus
`Chain` and a scripted `FakeChainAdapter` test double, exactly as the task statement names. It also
designs the four supporting types the spec references but never shapes, grounding each choice in a
concrete downstream consumer rather than guessing. It's the first task in "Adapters, providers,
quorum"; `EthereumAdapter`/`TronAdapter` (T06/T07) are the first real implementations.

## Testing performed

- `mvn -pl services/crypto -am compile` / `test-compile` — clean throughout.
- `mvn -pl services/crypto test -Dtest=...` — **25/25 tests passing**, all six test classes, zero
  Docker dependency anywhere — the first task in this series where every acceptance criterion was
  both written and actually executed, not partially deferred to an unavailable environment.
- Three separate mutation-based negative-proofs performed and reverted cleanly (`diff`-confirmed
  against pre-mutation backups): a blanket-broadcast mutation to the reorg push logic broke exactly
  the selectivity test; a wrong expected return type in the shape test's own data broke exactly that
  test (proving the assertion isn't vacuously true); an early-`break` mutation in the multi-subscriber
  push loop broke exactly the multi-subscriber test.
- One additional safety net discovered (not a test, an observation): mutating `ChainAdapter`'s own
  method signature broke `mvn test-compile` immediately, before any test ran, because
  `FakeChainAdapter`'s `@Override` no longer matched — any interface-shape regression is caught by
  the build itself, with `ChainAdapterShapeTest` as an explicit, self-documenting second guard.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 5 — "Adapter interface + fakes."
- **Requirements:** none — no `R`-numbered requirement is independently testable by this task's own
  deliverable (Phase 1 finding, confirmed through to Phase 12).
- **Locked decisions:** L4, L7, L14, L15 (derived in Phase 1 from `design.md` §4a — none were cited
  inline in the task header).
- **Named test:** none pre-mapped in `package.md` §8; this task's own tests are self-referential,
  mirroring T01's precedent.
- **Standing rules:** `spec/crypto-service/agents.md` — followed throughout; never modified.

---

**This artifact is preparation only.** No `git commit`, `git push`, or PR was created. If you'd like
me to commit the pending Phase 11/12 delta now (the 7 files listed above), say so and I will —
repo-wide instructions require that explicit go-ahead first.
