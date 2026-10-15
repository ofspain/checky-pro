# payment · T20 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

No production code changed this phase.

## Test manifest (`LedgerVerifierTest`, new class — 9 methods, real Testcontainers)

| Test | Verifies |
|---|---|
| `shouldReportIntactForAnEmptyLedger` | AC5 |
| `shouldReportIntactForAGenesisOnlyLedger` | AC5 |
| `shouldReportIntactForAnUncorruptedMultiEntryChain` | the "no false positive" counterpart to the named test |
| `shouldDetectBrokenHashChainOnVerification` (named) | AC1/AC4 — a real corrupted middle entry reports `Broken` at the exact right `seq` with `REASON_ENTRY_HASH_MISMATCH` |
| `shouldDetectPrevHashCorruptionDistinctFromEntryHashCorruption` | AC2 — a `prevHash`-only corruption (self-consistent `entryHash`) reports the distinct `REASON_PREV_HASH_MISMATCH` |
| `shouldDetectCorruptedGenesisPrevHash` | AC3 |
| `shouldNotFalselyReportBrokenAcrossASeqGap` | the Phase 4 Finding 7 corrected mechanism — advances the identity sequence directly between two real appends, asserts `Intact()`; mutation-relevant to the positional-vs-arithmetic design point |
| `shouldReportEntryHashMismatchBeforePrevHashMismatchWhenBothFail` | Kimi Phase 8 Finding 4 — the within-entry check order (AC1 before AC2); mutation-verified this phase (see below) |
| `shouldReportBrokenNotThrowWhenPayloadIsNull` | Kimi Phase 8/9 Finding 1 — the null-payload fix returns a typed `Broken`, never throws |

## Mutation verification performed this phase

- **Within-entry check order**: temporarily swapped `checkEntry`'s own AC1/AC2 order (checked
  `prevHash` linkage before hash self-consistency) — `shouldReportEntryHashMismatchBeforePrevHashMismatchWhenBothFail`
  failed red, as expected (the test's own corrupted entry fails both checks, and the wrong
  order would report `REASON_PREV_HASH_MISMATCH` instead of the expected
  `REASON_ENTRY_HASH_MISMATCH`). Reverted; confirmed `git diff` empty, full suite green again.

## Full suite

`mvn -o -pl services/payment -am test` (Docker up — `services/auth/compose.local.yaml`):
**414/414 passing** (405 pre-task baseline + 9 new).
