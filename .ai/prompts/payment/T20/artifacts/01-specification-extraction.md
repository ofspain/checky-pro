# payment · T20 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

- **R22.** WHEN the ledger is verified, THEN the system SHALL recompute each entry hash from its
  payload plus `prev_hash` and SHALL report any entry whose recomputed hash does not match its
  stored hash or whose `prev_hash` does not match the prior entry.

## Locked Decisions

- **L7** (continuity). Append-only hash-chain ledger; `entryHash` = SHA-256(canonical payload ‖
  `prevHash`); genesis `prevHash` = 64 zeros. `LedgerVerifier` recomputes against this exact
  formula — no new hashing rule.
- **L12** (continuity). `LedgerVerifier` stays inside `receipt.ledger`; no cross-module entity
  import.

## Files involved

**Existing, to extend:**
- `receipt/ledger/LedgerEntryRepository.java` (T19, package-private) — needs a new method to
  fetch every entry for a full chain walk (exact shape a Phase 2 decision).

**Existing, read-only:**
- `receipt/ledger/LedgerEntry.java` (T19) — `seq`/`prevHash`/`entryHash`/`payload`.
- `common/CanonicalJson.java` (T16) — reused verbatim to recompute each entry's hash input.

**New files the spec expects:**
- `receipt/ledger/LedgerVerifier.java` — this task's own sole deliverable.

**Not touched, confirmed out of scope (Phase 0):** no controller, scheduled job, or any other
caller — `LedgerVerifier` is a standalone, callable utility with no wiring in this task
breakdown, the intended caller being a future, later-phase consumer (a Dispute Service or an ops
tool, per `ARCHITECTURE.md`'s own roadmap framing) outside this spec's own scope.

## Dependencies

- `LedgerEntryRepository` (new read method).
- `CanonicalJson.serialize(Map<String,Object>)` (T16) — reused, not reimplemented.
- `java.security.MessageDigest` (SHA-256) — mirrors `LedgerService`'s/`ReceiptDigest`'s own
  identical private helper shape.

## Acceptance Criteria

- **AC1 (R22).** For every entry in the chain, `LedgerVerifier` recomputes
  `SHA-256(CanonicalJson.serialize(payload) + prevHash)` and compares it to the entry's own
  stored `entryHash`.
- **AC2 (R22).** For every entry except the genesis one, `LedgerVerifier` also checks the
  entry's own stored `prevHash` equals the immediately-prior entry's own stored `entryHash`.
- **AC3 (the task's own "reports the first break," resolved at Phase 2 — see Open Questions).**
  The verification stops and reports at the first entry that fails either check in AC1/AC2,
  rather than exhaustively listing every failure.
- **AC4.** An empty ledger (no entries at all) and a single-entry (genesis-only) ledger both
  verify as intact — no false positive on the smallest possible real states.

## Tests required

The one named test from `package.md` §8 is pre-mapped:
- `shouldDetectBrokenHashChainOnVerification` (R22) — the task's own text explicitly says "test
  with a deliberately corrupted row": seed several real entries via `LedgerService.append`
  against a real Postgres, directly corrupt one row's `payload`/`entryHash`/`prevHash` via a raw
  admin connection (bypassing the application's own no-`UPDATE` path entirely — the only way
  this scenario is actually reachable, matching `SECURITY-THREAT-MODEL.md`'s own "insider alters
  a historical verification" threat exactly), then assert `LedgerVerifier` reports the break at
  the correct entry.

Boundary tests implied, not separately named:
- An intact chain (several real, uncorrupted entries) verifies clean — the "no false positive"
  counterpart to the named test.
- The two AC4 edge cases (empty ledger, genesis-only ledger).
- A `prevHash`-only corruption (the stored `entryHash` is internally self-consistent with its
  own `prevHash`, but that `prevHash` doesn't match the real prior entry's `entryHash`) as a
  distinct case from an `entryHash`-only corruption (payload or hash changed, breaking the
  self-consistency check directly) — R22's own text names both failure modes separately.

## Open Questions

Both carried forward from Phase 0, confirmed not resolved by `package.md`/`design.md`/
`ARCHITECTURE.md`:

1. **"Head-to-tail" direction.** Does verification walk from the most recent entry backward
   toward genesis, or from genesis forward toward the most recent entry? A low-risk
   implementation choice (both directions are equally capable of finding a break, assuming a
   full scan to the end regardless of direction) — resolved directly at Phase 2, not escalated.
2. **"Report the first break" vs R22's "report any entry."** Resolved directly at Phase 2 as
   "stop at the first break encountered in scan order" — exhaustively reporting every
   downstream-of-a-break entry as "also broken" would be noise once one link in the chain is
   known compromised, and the task's own text explicitly says "first."

Neither blocks starting Phase 2.
