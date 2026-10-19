# payment · T29 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`. This artifact contains no code; it is documentation only.

## Task

Verify `package.md` §9's 14 checklist items against current evidence and address each one with a citation. Bump the header Version `0.1→0.2` and Status `DRAFT→READY FOR IMPL`, now that both gates hold:
- **T27:** `verify` passes, 532/532.
- **T28:** threat-model row #5 is closed.

## Purpose

This closes the last task in `spec/payment-service/tasks.md`. It formally declares the spec implemented and verified, following the crypto T29 precedent (`b303d682`).

## Scope

**In:**
- **Header.** `package.md` header, `:8` Version and `:9` Status.
- **§9.** `package.md` §9: tick `[ ]→[x]` with an italic typed citation where the evidence fully supports it. Where it doesn't, keep the item visibly partial with the reason.
- **Guard.** One regression guard test in `T01SkeletonRegressionTest`.

**Out:**
- **Payment source.** Any change to `services/payment` main source.
- **Missing receipt read.** Building the missing R28 receipt read endpoint. It becomes a disclosed follow-up, not an in-scope fix.
- **Other spec files.** `requirements.md`, `design.md`, `tasks.md`, `agents.md`.
- **§8 and §11.** §8's named-test text (the drift is disclosed in §9 item 1, not renamed). §11 is already fully closed, 2026-10-11.
- **Other services.** Crypto-service's unregister defect. Sibling Dockerfiles (FU-T27-1).

## Business Rules

None is targeted. Governed by `tasks.md` task 29. R7a, R13, R17/R18, R21–R23, R26, R28, R30 and R31 are re-verified as §9 evidence only.

## Locked Decisions

None constrains the edit. L2, L6, L7, L9, L11, L12 and L14 are cited as evidence through §9.

## Dependencies

- **`verify` result:** 532/532, at `b8a654e8`.
- **`SECURITY-THREAT-MODEL.md:14`:** row #5.
- **Precedent:** `spec/crypto-service/package.md` §9 citation style, and the crypto `T01SkeletonRegressionTest` header guard.

## Inputs

- the Phase 1 evidence table
- the current `package.md:8`, `:9` and `:114`–`:127`

## Outputs

- an edited `spec/payment-service/package.md`
- one new test method in the payment `T01SkeletonRegressionTest`

## State Changes

None. Documentation and test only.

## Files to Create

None.

## Files to Modify

- **`spec/payment-service/package.md`:** subject to Phase 4 OQ1.
- **`services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`:** add the header guard.

## Files NOT to Modify

- everything under `services/payment/src/main`
- the other `spec/payment-service/*.md` files
- `contracts/`
- `SECURITY-THREAT-MODEL.md`
- every other service
- the user's untracked `prompt` file

## Acceptance Criteria

| AC | Criterion |
|---|---|
| AC1 | Both gates hold, cited in the artifact: `verify` passes, T28 is closed. |
| AC2 | All 14 §9 items are addressed. Items 2–12 are ticked with typed citations; item 12 also discloses G2 partition-key drift. Item 1 is partial: 32/35 verbatim, 2 cosmetic drifts named, R26 deferred, **R28 receipt half unbuilt**. Item 13 is partial: payment half proven, the second-match half is cross-service and unproven against real crypto-service, known crypto unregister defect. Item 14 is kept, noting interim reconciliation. |
| AC3 | Version `0.2`, Status `READY FOR IMPL`. |
| AC4 | The guard test fails when either header value regresses. Mutation-proven. |

## Required Tests

- **`T01SkeletonRegressionTest`** gets a payment spec header guard: Version `0.2`, Status `READY FOR IMPL`. The method name will be fixed at Phase 5.
- **Full `verify`** stays green: 532 plus the new guard.

## Constraints

- **Honesty over tidiness.** No item is ticked without evidence. Partials stay visible, as in crypto item 13.
- **Citations.** Every cited test name is checked with `grep "void <name>("` before commit.
- **No secrets** (L13). There are no module-boundary concerns, since this is test plus documentation.

## Open Questions

These are decisions for the Phase 4 human gate, not implementation blockers:
- **OQ1:** may this task edit `spec/payment-service/package.md`, as crypto T29 did, despite the general "never modify `spec/`" guardrail?
- **OQ2:** bump to `READY FOR IMPL` with items 1 and 13 honestly partial (crypto precedent), or hold the bump until the R28 receipt read exists?
- **OQ3:** replace the stale explanatory note in the Status cell, or keep it as history?
