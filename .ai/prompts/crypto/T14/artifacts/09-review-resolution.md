# crypto · T14 · Phase 9 — Review Resolution

**Human Approval gate.** Approved 2026-09-07. Findings from Phase 7 (self-review) and Phase 8 (Kimi
independent review) are consolidated below, matching this pipeline's established convention (T13
Phase 9). No public API changed (interface method signatures unchanged) — Javadoc/documentation and
required-test additions only.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Self-review Finding 1 — `FinalityPolicy`'s interface methods carry no method-level Javadoc | **ACCEPTED** | Added method-level Javadoc to `isFinal` (`@param`/`@return`/`@throws`, condensing the class-level contracts) and a one-line comment to `chain()`, in `FinalityPolicy.java`. |
| 2 | Self-review Finding 2 — Q4 documentation references private adapter methods via `{@code}`, not a resolvable `{@link}`, risking silent drift | **REJECTED** | No action — both referenced methods are `private`, so a `{@link}` from another package could not resolve to them either; switching tags adds no real safety. Informational-only finding, correctly not hidden per this phase's own directive. |
| 3 | Kimi Issue 1 — `FinalityPolicy`'s class Javadoc overstated adapter guarantees (claimed both real adapters guard against every "inconsistent" `FinalityStatus`, but only `TronAdapter` additionally guards `txBlockNumber > currentBlockNumber`; `EthereumAdapter` does not) | **ACCEPTED** | Verified directly against `EthereumAdapter.java:145-166` vs. `TronAdapter.java:179-207` — confirmed true. Tightened the Trust-boundary Javadoc paragraph in `FinalityPolicy.java` to name the one invariant both adapters actually share (`finalizedBlockNumber <= currentBlockNumber`), explicitly note the Ethereum/Tron asymmetry, and explain why it cannot produce a wrong `isFinal` answer even so. |
| 4 | Kimi Issue 2 — no test locks in the documented "no wrong-chain self-check" contract | **ACCEPTED (noted for Phase 10)** | Additional required test: `doesNotSelfValidateChainWhenGivenAWrongChainStatus` (pass an Ethereum-shaped `FinalityStatus` to `TronFinalityPolicy`, or vice versa, and assert the raw comparison still applies). |
| 5 | Kimi Issue 3 — no test at the `txBlockNumber == 0` boundary (genesis block) | **ACCEPTED (noted for Phase 10), scoped down** | Additional required test: `txBlockNumber = 0`, `finalizedBlockNumber = 0` → final, for each policy. Kimi's own hedged "negative `finalizedBlockNumber`" variant is NOT added — block numbers are never negative in reality; contrived out-of-domain test data is inconsistent with this pipeline's own precedent (T13 Phase 9 Issue 8's rejection of unprecedented hardening). |
| 6 | Kimi Issue 4 — no test exercises `currentBlockNumber` far *behind* both other fields (only "far ahead" was planned) | **ACCEPTED (noted for Phase 10)** | Additional required test: scripted-heads variant with `currentBlockNumber` far behind `txBlockNumber`/`finalizedBlockNumber`, confirming the decision still depends only on `txBlockNumber <= finalizedBlockNumber`, for each policy. |
| 7 | Kimi Issue 5 — null-handling test should assert the `NullPointerException`'s exact message content | **REJECTED** | The frozen brief's contract requires only `NullPointerException`, not a specific message string; message text is not part of the documented API surface. No other test in this codebase asserts `Objects.requireNonNull` message content. Kimi itself rated this "Confidence: Low." Brittle, unprecedented hardening. |
| 8 | Kimi Issue 6 — add a reflection/source-scan test guarding against the two policy classes ever being collapsed into one | **REJECTED** | Same class of ask as Phase 3 Finding 4 (`isInstanceOf` test), already rejected there as unprecedented. No other class pair in this codebase has a meta-test protecting a design decision from a hypothetical future refactor — Javadoc (already in place, Phase 3 Finding 3) is this pipeline's established mechanism for recording that intent. Kimi itself rated this "Confidence: Low." |

## Summary

3 accepted with a documentation change (1, 3, folded together into `FinalityPolicy.java`), 3 accepted
as noted additional required tests for Phase 10 (4, 5 scoped down, 6), 3 rejected (2: no real safety
gain given `private` visibility; 7: out-of-contract message-string assertion; 8: unprecedented
process-guard test with an established documentation-only precedent).

`mvn -pl services/crypto compile` succeeds cleanly after the change, with zero new warnings. No public
method signature changed; no class renamed; no file outside `finality/FinalityPolicy.java` touched in
this phase.
