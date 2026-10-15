# payment · T20 · Phase 4 — Freeze Task Brief

**STATUS: FROZEN** — approved as written via `AskUserQuestion`.

Consumes: `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md`

## Disposition of Phase 3 (Kimi) findings

All 7 verified against real source/mechanics before dispositioning.

| # | Finding | Severity | Disposition | Reason |
|---|---|---|---|---|
| 1 | Repository read method unnamed | Medium | **ACCEPTED** | Frozen as `List<LedgerEntry> findAllByOrderBySeqDesc()` — a plain Spring Data derived query, mirroring `findTopByOrderBySeqDesc`'s own existing naming convention in the same file. |
| 2 | `verify()` signature not frozen | Medium | **ACCEPTED** | `public LedgerVerificationResult verify()`, no parameters. |
| 3 | `Broken` reason strings unspecified | Medium | **ACCEPTED** | Three exact strings frozen below. |
| 4 | Within-entry check order unspecified | Low | **ACCEPTED** | AC1 (hash self-consistency) → AC2 (linkage, non-genesis) → AC3 (genesis `prevHash`). The hash self-consistency check is the most fundamental ("does this entry's own claimed content match its own claimed hash at all") and is checked first regardless of position in the chain. |
| 5 | `LedgerVerifier`'s own Spring-bean status unspecified | Low | **ACCEPTED** | Public `@Component`, constructor-injected `LedgerEntryRepository` — mirrors `LedgerService` exactly, even with no current caller, so a future consumer (or Phase 10's own test) can inject it normally. |
| 6 | No statement about concurrent-append consistency | Low | **ACCEPTED** | A point-in-time-snapshot disclosure added to `LedgerVerifier`'s own class Javadoc. |
| 7 | No guidance for simulating a `seq` gap in the boundary test | Low | **ACCEPTED, with a correction to the suggested mechanism** | Kimi's own suggested mechanism ("delete a middle entry... verify the remaining chain still reports `Intact()`") is technically wrong, verified by tracing the mechanics directly: deleting an *already-successfully-chained* entry does **not** produce a harmless gap — the entry originally appended after it still carries a `prevHash` pointing to the now-deleted row's `entryHash`, which no longer matches whatever is now positionally prior; this correctly (and should) report `Broken`, not `Intact()`. The real, harmless gap this boundary test needs to simulate is a **never-committed reserved identity value** (a rolled-back transaction) — every row that *does* exist is still correctly chained to its own true predecessor. The correct mechanism: call `SELECT nextval(pg_get_serial_sequence('payments.ledger_entries', 'seq'))` directly between two real `LedgerService.append(...)` calls, advancing the sequence without ever inserting a row for that value — no row exists for the skipped `seq`, but every row that does exist remains correctly linked. |

No finding was rejected.

## Amendments folded into this frozen brief (supersede the Phase 2 TIB where they conflict)

**`LedgerEntryRepository`** (Finding 1):
```java
List<LedgerEntry> findAllByOrderBySeqDesc();
```

**`LedgerVerificationResult`** (sealed, Finding 3's exact reason strings):
```java
public sealed interface LedgerVerificationResult {
    record Intact() implements LedgerVerificationResult {}
    record Broken(Long seq, String reason) implements LedgerVerificationResult {}
}
```
Exact `reason` values, used verbatim wherever constructed:
- `"stored entryHash does not match recomputed hash"` (AC1 failure)
- `"stored prevHash does not match prior entry's stored entryHash"` (AC2 failure)
- `"genesis prevHash is not 64 zeros"` (AC3 failure)

**`LedgerVerifier`** (Finding 2/4/5/6):
```java
@Component
public class LedgerVerifier {
    public LedgerVerifier(LedgerEntryRepository repository) { ... }

    public LedgerVerificationResult verify() {
        List<LedgerEntry> entries = repository.findAllByOrderBySeqDesc();  // head first
        for (int i = 0; i < entries.size(); i++) {
            LedgerEntry current = entries.get(i);
            // AC1 first, always
            if (!recomputedHashMatches(current)) {
                return new LedgerVerificationResult.Broken(current.getSeq(),
                        "stored entryHash does not match recomputed hash");
            }
            boolean isGenesis = (i == entries.size() - 1);
            if (!isGenesis) {
                // AC2
                LedgerEntry prior = entries.get(i + 1);  // positional, not seq-arithmetic
                if (!current.getPrevHash().equals(prior.getEntryHash())) {
                    return new LedgerVerificationResult.Broken(current.getSeq(),
                            "stored prevHash does not match prior entry's stored entryHash");
                }
            } else {
                // AC3
                if (!current.getPrevHash().equals(GENESIS_PREV_HASH)) {
                    return new LedgerVerificationResult.Broken(current.getSeq(),
                            "genesis prevHash is not 64 zeros");
                }
            }
        }
        return new LedgerVerificationResult.Intact();
    }
}
```
(Illustrative — exact private-helper shape confirmed at Phase 5.) Class Javadoc states: "reflects
the committed ledger state at query-start time; does not synchronize with concurrent appends,
and should be treated as a point-in-time audit, not a live stop-the-world check" (Finding 6).

## Final scope, files, and acceptance criteria (carried from Phase 2 TIB, as amended above)

**Scope:** unchanged. **Files to Create:** `receipt/ledger/LedgerVerifier.java`,
`receipt/ledger/LedgerVerificationResult.java`. **Files to Modify:**
`receipt/ledger/LedgerEntryRepository.java` (new `findAllByOrderBySeqDesc`).

**Acceptance Criteria** (AC1-AC5 from Phase 2, unchanged in substance, now testable thanks to
the frozen signatures/strings above).

**Required Tests, boundary test correction (Finding 7):** the `seq`-gap boundary test uses
`SELECT nextval(pg_get_serial_sequence('payments.ledger_entries', 'seq'))` between two real
`append(...)` calls to advance the sequence without inserting a row — not row deletion.

## Open Questions

None remaining.
