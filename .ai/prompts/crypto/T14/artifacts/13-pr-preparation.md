# crypto · T14 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS** (`artifacts/12-specification-verification.md`). Proceeding to prepare T14
for merge. Branches off `main`; `main` remains deployable throughout — no commit in this task touches
anything outside `services/crypto/` (plus this task's own `.ai/prompts/crypto/T14/` artifacts).

## Commit title

```
crypto: add per-chain finality policy objects (T14)
```

## Commit message

```
crypto: add per-chain finality policy objects (T14)

Implement FinalityPolicy, EthereumFinalityPolicy, and
TronFinalityPolicy (L4, R6/R7): each chain's own boolean "is this
transaction final" decision, as a pure, stateless @Component pair
behind a shared interface. Ethereum is final at or below the beacon
`finalized` checkpoint; Tron is final once its block is solidified -
both reduce to the identical txBlockNumber <= finalizedBlockNumber
comparison once FinalityStatus already carries the right
finalized/solidified block number, which EthereumAdapter/TronAdapter
(T06/T07) already fetch per-chain. This task is the boolean-decision
layer only; obtaining that number was already shipped ahead of
schedule during the adapter work.

Both implementations are deliberately identical in body - L4 requires
"adding a chain adds a policy object," never a shared branch or
confirmation-count constant, so the duplication is intentional and
documented in both classes' Javadoc, not an invitation to merge them.
Tron's own "~19 confirmations" is descriptive of what a solidified
block represents; it is never a literal threshold in this code - the
actual solidified block number is supplied pre-computed via
FinalityStatus.

Confirms Q4 (Tron confirmation-count basis) as a documentation
deliverable: TronFinalityPolicy's own Javadoc records that
chain.tx.confirmed's future confirmation count is already fixed by
TronAdapter.computeConfirmations (T07) as block depth from the current
head, identical in kind to EthereumAdapter's own basis - not
confirmations toward the solidified block specifically, and a
distinct concept from this task's own finality check.

Review cycle caught one real documentation-accuracy defect via direct
source comparison: EthereumAdapter.getFinalityStatus does not guard
against a transaction block ahead of the chain's own current head,
while TronAdapter.getFinalityStatus does - the original Javadoc's
"both adapters throw before constructing an inconsistent
FinalityStatus" claim overstated the shared guarantee. Fixed by
naming the actual shared invariant and the chain-specific asymmetry
explicitly (confirmed by inspection that the asymmetry cannot itself
produce a wrong finality decision).

FinalityStatus carries no chain discriminator, so neither policy
self-validates that the status it receives actually came from its own
chain's adapter - an accepted, documented risk (not this task's to
fix; FinalityStatus is a frozen, multi-consumer type). A new test
locks this "no self-check" contract in executable form so a future
refactor cannot silently add or remove it unnoticed.

No persistence, no RPC call, no event emission - the first task since
T12/T13 with zero Docker dependency in its own tests. A new
FinalityModuleBoundaryTest (mirroring T10/T11's own source-scan
precedent) confirms finality/ imports nothing from observation,
provider, quorum, token, or events, and only the two types it
legitimately needs from adapter.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Main:**
- `services/crypto/src/main/java/com/themistra/crypto/finality/FinalityPolicy.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/finality/EthereumFinalityPolicy.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/finality/TronFinalityPolicy.java` — new

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/finality/EthereumFinalityPolicyTest.java` — new (10 tests)
- `services/crypto/src/test/java/com/themistra/crypto/finality/TronFinalityPolicyTest.java` — new (9 tests)
- `services/crypto/src/test/java/com/themistra/crypto/finality/FinalityModuleBoundaryTest.java` — new (1 test)

**Pipeline artifacts:**
- `.ai/prompts/crypto/T14/artifacts/00-repository-understanding.md` through `13-pr-preparation.md` — all 14 phase artifacts

## Summary

T14 adds the per-chain finality decision R6/R7/L4 require: two intentionally near-identical
`FinalityPolicy` implementations, each self-identifying its chain, operating purely on the
already-shipped `FinalityStatus` value both real adapters compute. Its own review cycle found and fixed
a genuine documentation-accuracy gap (an overstated adapter guarantee, confirmed via direct source
comparison rather than assumed) and added an executable regression guard for an already-accepted,
documented design risk (no chain self-validation). It is the third task in this service's build-out
with zero Docker dependency in its own tests (after T12/T13).

## Testing performed

- `mvn -pl services/crypto compile` — BUILD SUCCESS, no new warnings.
- `mvn -pl services/crypto test -Dtest=EthereumFinalityPolicyTest,TronFinalityPolicyTest,FinalityModuleBoundaryTest`
  — 20/20 passing.
- `mvn -pl services/crypto -am test` (full module suite) — 419 tests, 411 passing, 8 errors, all
  `IllegalState: … Docker environment …` (the same pre-existing set carried unchanged from T13's own
  baseline — this task introduces no new persistence layer) — zero genuine failures, zero regressions.

## Specification references

- **Task:** T14 — Finality policies (`spec/crypto-service/tasks.md` #14).
- **Requirements:** R6, R7 (`spec/crypto-service/requirements.md`).
- **Locked decisions:** L4 (`spec/crypto-service/design.md`) — finality is a per-chain policy object,
  not a global constant.
- **Named tests:** `shouldRequireBeaconFinalizedCheckpointForEthereumFinality`,
  `shouldRequireSolidifiedBlockForTronFinality` (`package.md` §8).
- **Contracts:** none of `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`,
  `contracts/events/chain/tx-finalized.v1.schema.json` are touched by this task — none of those files
  exist anywhere in this repository yet, and `FinalityPolicy` is a pure predicate with no HTTP endpoint
  and no event emission.
