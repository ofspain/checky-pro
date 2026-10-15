# payment · T20 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** same established practice as every prior task this session — executed
directly on the long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task
branch off `main`. `main` itself was never touched and stays deployable.

## Commit title

`payment T20: LedgerVerifier - hash-chain verification (R22)`

## Commit message

```
payment T20: LedgerVerifier - hash-chain verification (R22)

Implements LedgerVerifier.verify(): recomputes the hash chain T19's LedgerService built, head
(most recent entry) to tail (genesis), and reports the first entry that fails any of three
checks - its own stored entryHash doesn't match the recomputed SHA-256(canonical payload +
prevHash); its stored prevHash doesn't match the positionally prior entry's stored entryHash; or
(for the genesis entry specifically) its prevHash isn't the known 64-zero constant. This is the
read-only counterpart that actually cashes in the ledger's own tamper-evidence claim.

Kimi's own Phase 3 design challenge mostly asked to freeze signatures/strings my own Phase 2 TIB
left vague - the one substantive correction was to Kimi's own suggested test mechanism for
simulating a seq gap: deleting an already-chained entry does not simulate a harmless gap, it
breaks a real link and should (and does) correctly report Broken, not Intact(). The real,
harmless gap (a rolled-back transaction that reserved an identity value) is simulated by
advancing the identity sequence directly between two real appends - confirmed this and the
deletion case are both handled correctly by the same positional (not seq-arithmetic) prior-entry
lookup, a genuine, non-obvious design point: seq gaps from rollbacks are expected and harmless,
real deletions of committed rows are correctly detected, because the actual chain linkage is
carried entirely by prevHash/entryHash values, never by seq continuity - exactly how
LedgerService.append's own findTopByOrderBySeqDesc() already determines "the current head."

Self-review (Phase 7) flagged, but initially judged not worth fixing, a malformed-row edge case
(a null payload/prevHash, reachable only via the same database-owner access this task's own
threat model already assumes, would throw NullPointerException rather than return a typed
Broken). Kimi's own independent review (Phase 8) made a stronger case for fixing it - reversed
that initial judgment and wrapped per-entry verification in a try/catch converting any
unexpected failure to Broken, also extracting the three reason strings as named constants to
prevent future typo drift.

Writing the Phase 11 test suite surfaced one more genuine, confirmed technical fact, not assumed:
Java's own + string-concatenation operator tolerates a null operand (producing the literal text
"null"), so a corrupted null prevHash is always caught by the ordinary entry-hash-mismatch check
before the catch-all handler ever gets a chance to fire - that handler's own real purpose is
narrower (a null payload, or an unsupported JSON value type), not a null prevHash. A test's own
initial assumption about which reason would be reported was wrong; corrected after watching the
real assertion fail, not by inspection.

LedgerVerifierTest (new, real Testcontainers): 15/15, covering every distinct failure mode -
entry-hash corruption at the head and at a middle entry, prevHash-only corruption, corrupted
genesis prevHash, a true deleted-row detection distinct from a harmless seq gap, the
within-entry check-order guarantee, two malformed-row resilience cases, and a direct read-only
proof.

Full suite: 420/420 passing (405 pre-task baseline + 15 new).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Production code:**
- `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerVerificationResult.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerVerifier.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerEntryRepository.java` —
  new `findAllByOrderBySeqDesc()`.

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/receipt/ledger/LedgerVerifierTest.java`
  (new) — 15 tests.

No migration file — this task adds no entity, no schema change, no DB write path at all.

## Summary

T20 is the smallest task in the attestation/receipts/ledger group so far, but its own test-
writing work still produced two genuine, confirmed corrections — one to a Kimi-suggested test
mechanism (deleting a committed row doesn't simulate a harmless gap), and one to this task's own
initial assumption about which failure path a corrupted `prevHash` actually takes. Both were
caught only by writing the real test and checking the real, observed result against the
expectation, not by reasoning alone — the same "write the real test" discipline that caught
T19's own far more severe production bug, applied again at a smaller scale here.

## Testing performed

- `mvn -o -pl services/payment -am compile`, `test-compile`: clean throughout every phase.
- `mvn -o -pl services/payment -am test -Dtest=LedgerVerifierTest`: **15/15 passing**.
- Two throwaway Testcontainers scratch tests (Phase 6, Phase 9 — each written, run, deleted
  before its own commit) empirically confirmed the core algorithm and the null-payload fix
  before either was committed as a permanent test.
- Mutation-verification this task: the within-entry check order (swapped AC1/AC2, confirmed the
  corresponding test failed red, reverted).
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`): **420/420 passing** (405 pre-task baseline + 15 new).

## Specification references

- **Task:** T20 — Ledger verifier (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R22.
- **LOCKED decisions:** L7 (continuity — read-only, confirmed via a dedicated test), L12
  (continuity — no cross-module entity import, no new `ArchitectureTest` module needed).
- **Process decisions (not numbered requirements):** head-to-tail direction, "first break"
  semantics, the positional (not `seq`-arithmetic) prior-entry lookup, and the three frozen
  `Broken` reason strings — all resolved directly via Phase 2/4, no `AskUserQuestion` needed (no
  product-policy dimension).
