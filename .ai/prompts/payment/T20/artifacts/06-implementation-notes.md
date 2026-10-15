# payment · T20 · Phase 6 — Implementation Notes

Consumes: `artifacts/05-implementation-plan.md`

## What changed

**New files:**
- `receipt/ledger/LedgerVerificationResult.java` — sealed interface (`Intact`/`Broken`).
- `receipt/ledger/LedgerVerifier.java` — `verify()`, head-to-tail walk, the three frozen reason
  strings, duplicated `sha256`/`GENESIS_PREV_HASH` private helpers mirroring `LedgerService`'s
  own identical precedent.

**Modified:**
- `receipt/ledger/LedgerEntryRepository.java` — new `findAllByOrderBySeqDesc()`.

No other production file touched — this task's own scope is entirely contained within
`receipt.ledger`, confirmed at Phase 0.

## Verification performed this phase

A throwaway Testcontainers scratch test (written, run, deleted before this commit — not part of
Phase 10's own committed deliverable) confirmed, against a real Postgres, all three of this
task's own central correctness claims empirically, not just by code inspection:
- An intact, 3-entry chain verifies `Intact()`.
- Directly corrupting a middle entry's `payload` via a raw admin connection (bypassing the
  application's own `@Immutable`/no-`UPDATE` design entirely) produces `Broken(seq=2, "stored
  entryHash does not match recomputed hash")` — the exact entry, the exact reason.
- A genuine `seq` gap (advancing the identity sequence directly between two real `append(...)`
  calls, per Phase 4's own corrected mechanism — `seq` jumped 1→3, skipping 2 entirely) still
  correctly verifies `Intact()` — confirming the positional (not `seq`-arithmetic) prior-entry
  lookup works exactly as designed.

## Mapping to the frozen brief / acceptance criteria

- **AC1** — `recomputedHash` compares `sha256(CanonicalJson.serialize(payload) + prevHash)`
  against the entry's own stored `entryHash`, checked first for every entry.
- **AC2** — for every non-genesis entry, `entries.get(i + 1)` (positional) is compared against
  `current.getPrevHash()`.
- **AC3** — the genesis entry's own `prevHash` is compared against the 64-zero constant.
- **AC4** — the loop returns immediately at the first failing entry, head-to-tail order.
- **AC5** — an empty `entries` list never enters the loop body at all, returning `Intact()`
  directly; a genesis-only list exercises the `isGenesis` branch on its one and only entry.

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`/`test-compile`: clean, no existing caller to break
  (confirmed, this task adds no new constructor dependency to any existing class).
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **405/405 passing**, unchanged
  — no regression, no new test content yet (`LedgerVerifierTest` is Phase 10's own job).
