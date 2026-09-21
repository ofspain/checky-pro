# crypto · T28 · Phase 1 — Specification Extraction

## Business Rules

- **R1.** `shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree` — a fact is true only when ≥2 of 3
  independent providers agree (Threat #1).
- **R4.** `shouldLogEveryProviderResponseVerbatimToObservationLog` — every provider response is persisted
  verbatim to the observation log before the quorum decision (Threat #5, crypto-service's own scope only
  — see Open Questions).
- **R6.** `shouldRequireBeaconFinalizedCheckpointForEthereumFinality` — Ethereum finality requires a
  beacon `finalized` checkpoint (Threat #3).
- **R7.** `shouldRequireSolidifiedBlockForTronFinality` — Tron finality requires a solidified block
  (Threat #3).
- **R11.** `shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg` — a reorg walks the cursor backward and
  emits `chain.tx.reorged` (Threat #3).
- **R13.** `shouldIdentifyTokenByContractAddressNotSymbol` — tokens are identified by
  `<chain, contractAddress>`, never by symbol (Threat #2).
- **R14.** `shouldSurfaceUnknownTokenForNonAllowlistedContract` — a non-allowlisted contract yields
  `UNKNOWN_TOKEN`, never a symbol guess (Threat #2).
- **R17.** `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` — prefix/suffix address similarity to a
  known counterparty is flagged (Threat #6).
- **R22.** `shouldOnlyAllowAttestPathToInvokeKmsSign` — `kms:Sign` is reachable only from the attest path
  (Threat #4, and the task statement's own standalone "no non-attest path can reach `kms:Sign`" check).

## Locked Decisions

- **L1.** 2-of-3 quorum, no single-provider truth — governs Threat #1 and the task statement's own
  standalone "no single-provider fact is ever emitted" check; directly quoted by `package.md` §9's own
  verification-checklist first bullet.
- **L3.** Observation log is verbatim and written first (Postgres + S3 snapshot) — governs Threat #5,
  crypto-service's own scope (see Open Questions for the boundary with the hash-chain/anchor portion).
- **L4.** Finality is a per-chain policy object — governs Threat #3.
- **L6.** Reorg is a first-class transition — governs Threat #3.
- **L7.** Token identity is contract address only — governs Threat #2.
- **L9.** Address-poisoning flagging — governs Threat #6.
- **L11.** KMS-only signing, single path, enforced by ArchUnit + IAM — governs Threat #4 and the task
  statement's own standalone `kms:Sign` check; directly quoted by `package.md` §9's own
  verification-checklist second bullet.

L2, L5, L8, L10, L12–L15 are not directly targeted by any threat-model row or by the task's own two
standalone checks; not in scope for this task's own verification pass.

## Files involved

**Read-only (existing, to verify against):**
- `SECURITY-THREAT-MODEL.md` (repo root — not under `spec/`) — the six rows this task verifies.
- `spec/crypto-service/package.md` §8 (named tests), §9 (verification checklist).
- The full existing test suite (697 tests as of T27) — specifically
  `QuorumEvaluatorTest`/`QuorumDecisionServiceTest`/`WatcherTest` (Threat #1/L1),
  `TokenValidatorTest`/`AddressValidatorTest` (Threat #2/L7),
  `EthereumFinalityPolicyTest`/`TronFinalityPolicyTest`/`ReorgDetectorTest`/`WatcherTest` (Threat #3),
  `KmsSignerArchitectureTest` (Threat #4/L11),
  `ObservationLogTest`/`ObservationSnapshotStoreTest`/`ObservationSnapshotStoreLocalStackIntegrationTest`
  (Threat #5/L3), `AddressPoisoningDetectorTest` (Threat #6/L9).

**Possible write target (open question, not decided here):**
- `SECURITY-THREAT-MODEL.md` itself — whether "closure" includes updating each row's `Status` column
  (currently `tracked`) is not yet determined; see Open Questions.

**Not touched:** any `spec/crypto-service/*` file (guardrail); any main/test source outside what's
needed to identify or, if a genuine test gap is found, close one.

## Dependencies

No new classes, services, or config keys. This task depends entirely on existing, already-implemented
and already-tested code identified in Phase 0: `QuorumEvaluator`, `KmsSigner`/`KmsSignerArchitectureTest`,
`FinalityPolicy` implementations, `ReorgDetector`, `TokenAllowlist`/`AddressValidator`,
`AddressPoisoningDetector`, `ObservationLog`/`ObservationSnapshotStore`.

## Acceptance Criteria

1. **AC1.** Each of `SECURITY-THREAT-MODEL.md` rows #1–#6 is checked against the real test suite; for
   each, either a named, passing test is identified and cited (file + test method), or a genuine gap is
   found and reported — never silently assumed covered.
2. **AC2 (task statement, standalone).** Confirmed: no non-attest path can reach `kms:Sign` — cites
   `KmsSignerArchitectureTest`'s own two ArchUnit rules and their real, passing, re-verified execution
   (last confirmed at T27 Phase 11: 697 tests, 0 failures).
3. **AC3 (task statement, standalone).** Confirmed: no single-provider fact is ever emitted — cites the
   quorum-layer tests (L1) and, if found insufficient, identifies what a stronger system-level assertion
   would need to look like (Phase 0's own flagged unknown).

## Tests required

None authored fresh, unless AC1's row-by-row check finds a genuine gap (Phase 0 flagged one candidate:
whether a system-level "no single-provider fact" assertion exists beyond per-unit coverage — to be
resolved by Phase 2's design work, not assumed here).

## Open Questions

1. **Does "closure" include updating `SECURITY-THREAT-MODEL.md`'s own `Status` column** (from `tracked`
   to something like `closed`/`verified`) for rows #1–#6, or is this task purely a verification report
   with no file changes to the threat-model document itself? The task statement says "verify... confirm..."
   (read-only language) but the task's own name ("closure") suggests rows should visibly change state.
   Not a blocker for Phase 2 to design around, but the Phase 2 TIB must pick one interpretation
   explicitly rather than leave it ambiguous.
2. **Threat #5's mitigation text ("Hash-chain ledger + S3 Object Lock + on-chain anchor") spans two
   services** (Phase 0 finding): the hash-chain ledger and on-chain anchor belong to the Payment
   Service (`ARCHITECTURE.md` §6.5, data-ownership table), not yet built. `package.md`'s own threat-model
   row #5 "Implementing task" column lists only crypto-service's T08/T09. Phase 2 must scope AC1's
   row-#5 check to crypto-service's own contribution (L3 verbatim persistence + S3 WORM snapshot) and
   state explicitly that the hash-chain/anchor portion is out of this service's/task's reach, not
   silently treat the row as either fully closed or fully blocked.
3. **`package.md` §11 Q6** (Payment Service's anchor-write endpoint, a blocker for Payment R26) is
   cross-service and not cited by any of the six threat-model rows directly. Not treated as in scope for
   this task unless Phase 2 finds a reason SECURITY-THREAT-MODEL.md itself expects it addressed — no
   such reference was found in this document.
