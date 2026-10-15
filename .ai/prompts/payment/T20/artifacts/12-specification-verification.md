# payment · T20 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`, `artifacts/11-test-review-resolution.md`

## Traceability matrix

| Requirement/Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R22** — recompute each entry hash from payload + `prevHash`, report any entry whose recomputed hash or `prevHash` doesn't match | Yes | `LedgerVerifier.verify`/`checkEntry` | `shouldDetectBrokenHashChainOnVerification` (named), `shouldDetectPrevHashCorruptionDistinctFromEntryHashCorruption`, `shouldDetectDirectEntryHashCorruptionWithUntouchedPayload` | No | No |
| **AC3 (genesis `prevHash` = 64 zeros, beyond R22's own two named failure modes)** | Yes | `checkEntry`'s own `else if` branch | `shouldDetectCorruptedGenesisPrevHash` | No | No |
| **AC4 (stop at the first break, head-to-tail)** | Yes | the `for` loop's own early `return` | `shouldReportEntryHashMismatchBeforePrevHashMismatchWhenBothFail`, `shouldDetectCorruptionOfTheHeadEntry` | No | No |
| **AC5 (empty/genesis-only ledger both `Intact()`)** | Yes | the loop body never executes for an empty list; the `isGenesis`/`priorEntry == null` branch handles the one-entry case | `shouldReportIntactForAnEmptyLedger`, `shouldReportIntactForAGenesisOnlyLedger` | No | No |
| **Positional (not `seq`-arithmetic) prior-entry lookup** | Yes | `entries.get(i + 1)`, never `current.seq() - 1` | `shouldNotFalselyReportBrokenAcrossASeqGap` (harmless gap), `shouldDetectBrokenChainWhenAMiddleEntryIsDeleted` (true tampering) — the same mechanism correctly distinguishes both | No | No |
| **L7 (continuity)** — read-only, no write path at all | Yes | `LedgerVerifier` has no repository write call anywhere | `verifyShouldNotMutateTheLedger` | No | No |
| **L12 (continuity)** | Yes | no cross-module entity import; stays inside `receipt.ledger` | `ArchitectureTest` (unchanged, no new module needed — `LedgerVerifier` lives in the already-registered `receipt` module) | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. `LedgerVerifier.verify()` is a complete, self-contained
read-only primitive that fully cashes in the hash-chain's own tamper-evidence claim — exactly
this task's own stated purpose.

**(2) Does it satisfy every acceptance criterion (frozen brief, Phase 4)?** All five (AC1-AC5)
are satisfied, now backed by 15 real Testcontainers tests covering every distinct failure mode
(entry-hash corruption at head/middle, `prevHash`-only corruption, genesis `prevHash`
corruption, a true deleted-row detection distinct from a harmless `seq` gap, malformed-row
resilience via the catch-all handler, and a direct read-only proof).

**(3) Does it violate any LOCKED decision?** No. L7 holds — confirmed by direct inspection that
`LedgerVerifier` contains no write statement at all, and by a dedicated test proving it. L12
holds — no cross-module entity import.

**(4) A genuine, confirmed technical finding from this task's own Phase 11 test-writing work**
(smaller in scope than T19's own severe bug, but a real correction nonetheless): Java's `+`
string-concatenation operator tolerates a `null` operand, so a corrupted `NULL` `prevHash` is
*always* caught by the ordinary AC1 hash-recomputation check before the catch-all exception
handler (added at Phase 9 for exactly this class of malformed-row scenario) ever gets a chance
to fire — the catch-all's own real, narrower purpose is a `NULL` *payload* or an unsupported
JSON value type, not a `NULL` `prevHash`. Found only by writing the test and watching the actual
assertion fail against a wrong initial assumption, not by code inspection alone.

**(5) Remaining risks (carried forward, not blocking this task's own PASS):**
- **No caller anywhere in this task breakdown** (confirmed at Phase 0, unchanged through Phase
  12) — `LedgerVerifier` is a complete, tested, unused primitive, exactly mirroring T15/T18's
  own established "built one task ahead of its consumer" shape. A future Dispute Service or ops
  tool is the intended real caller, outside this spec's own Phase 1 scope.
- **A full-database-owner adversary could in principle fabricate an entirely new, internally
  self-consistent replacement chain from scratch** — an inherent limitation of any hash-chain
  scheme, not a gap this task introduces or could close; `SECURITY-THREAT-MODEL.md`'s own
  "insider alters a historical verification" framing is about alteration of existing records,
  not full fabrication by an adversary who has already defeated every access control.

## Verdict

**PASS** — `LedgerVerifier` fully implements R22, correctly distinguishes harmless `seq` gaps
from true record deletion using the same positional mechanism, correctly handles malformed rows
via a typed result rather than an uncaught exception, and is backed by 15 tests proving every
distinct failure mode against a real Postgres, not a mock. `LedgerVerifierTest`: 15/15. Full
payment-service suite: **420/420 passing**.
