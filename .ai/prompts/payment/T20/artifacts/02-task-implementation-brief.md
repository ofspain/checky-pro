# payment · T20 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Implement `LedgerVerifier` that recomputes the hash chain head-to-tail and reports the first
break (R22). Test with a deliberately corrupted row.

## Purpose

This is the read-only counterpart to T19's `LedgerService.append` — the mechanism that actually
cashes in the hash-chain's own tamper-evidence claim. Without it, the chain is append-only and
internally linked, but nothing in this codebase can actually *prove* that to anyone; with it, a
single method call recomputes every link and reports exactly where (if anywhere) it breaks.

## Scope

**In:** `LedgerVerifier`'s own verification method; the new `LedgerEntryRepository` read method
it needs.

**Out:** Any controller, scheduled job, CLI, or other caller — confirmed at Phase 0, this task's
own deliverable has no wiring into this service's runtime anywhere in the remaining task
breakdown. The intended caller is a future, out-of-scope consumer.

## Business Rules

- **R22.**

## Locked Decisions

- **L7/L12** (continuity) — see Phase 1.
- **New, resolved directly (low-risk implementation choice, no `AskUserQuestion` needed):**
  **"Head-to-tail" = most recent entry (highest `seq`) first, walking backward toward genesis
  (lowest `seq`).** Chosen over the reverse direction for the operationally natural framing: if
  verification finds a break, everything from the current head back to (but not including) that
  broken entry is confirmed intact — a direct answer to "how far back can we trust the record
  right now," read from the present backward, matching how an actual audit or dispute
  investigation would be framed.
- **New, resolved directly: "report the first break" means stop scanning at the first entry
  that fails either check, in head-to-tail order — not an exhaustive list.** Once one link is
  known compromised, exhaustively flagging every entry on the far side of it as "also broken"
  adds noise, not information: the break itself is the actionable fact.
- **New, resolved directly — a genuine, non-obvious design point found while planning the
  algorithm, not assumed: the "prior entry" for the `prevHash` check is *positional* (whichever
  row is next in the `seq`-ordered list), not derived from `seq` arithmetic (`current.seq - 1`).**
  Confirmed against `LedgerService.append`'s own real behavior: it determines the chain's own
  head via `findTopByOrderBySeqDesc()` — the row with the highest `seq`, full stop, regardless of
  numeric gaps. Gaps in `seq` are expected and harmless: `seq` is a Postgres `IDENTITY` column,
  and a transaction that reserves an identity value then rolls back (e.g.,
  `ReceiptService.persistSignedReceipt`'s own abort-on-zero-rows path, T19 AC6) leaves that value
  permanently unused — Postgres sequences never roll back. The real chain linkage is carried
  entirely by the `prevHash`/`entryHash` values themselves, never by `seq` continuity.
- **New, resolved directly — the genesis entry's own `prevHash` is checked against the known
  64-zero constant, not merely skipped.** R22's own text names two failure modes (hash
  mismatch, `prevHash` mismatch against "the prior entry") but is silent on the one entry with
  no prior entry at all; leaving it unchecked would mean a corrupted genesis `prevHash` (e.g.,
  tampered to anything other than 64 zeros) goes entirely undetected by this method.

## Dependencies

- `LedgerEntryRepository` (new method, below).
- `CanonicalJson.serialize(Map<String,Object>)` (T16) — reused exactly as `LedgerService.append`
  already uses it, never a second canonicalizer.
- A private `sha256`/genesis-constant helper duplicated inside `LedgerVerifier`, mirroring
  `LedgerService`'s own already-established "duplicated, not shared" precedent for this exact
  pair of trivial private helpers (confirmed: `ReceiptDigest`/`LedgerService` each already carry
  their own copy, not a shared utility).

## Inputs

None beyond the ledger's own current state — `LedgerVerifier`'s own verification method takes no
parameters; it always verifies the whole chain from the current head.

## Outputs

A sealed `LedgerVerificationResult`: `Intact()` (the whole chain checks out, including the empty
and genesis-only cases, AC4) or `Broken(Long seq, String reason)` (the first entry, in
head-to-tail order, that fails either check, naming which check failed).

## State Changes

None — this task is purely read-only.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerVerifier.java`.
- `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerVerificationResult.java`.

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerEntryRepository.java`
  — add a method returning every entry ordered head-first.

## Files NOT to Modify

`LedgerEntry`/`LedgerService` (T19, used as-is — no behavior change, this task's own class is a
new, independent reader); anything under `spec/`.

## Acceptance Criteria

- **AC1.** For every entry, `LedgerVerifier` recomputes
  `SHA-256(CanonicalJson.serialize(payload) + prevHash)` and compares it to the entry's own
  stored `entryHash`.
- **AC2.** For every non-genesis entry, `LedgerVerifier` also checks the entry's own stored
  `prevHash` equals the *positionally* prior entry's own stored `entryHash` (not a `seq`-
  arithmetic lookup).
- **AC3.** The genesis entry's own `prevHash` is checked against the known 64-zero constant.
- **AC4.** Verification stops and reports at the first entry (head-to-tail order) that fails
  AC1, AC2, or AC3.
- **AC5.** An empty ledger and a single-entry (genesis-only, otherwise uncorrupted) ledger both
  report `Intact()`.

## Required Tests

The one named test from `package.md` §8 is pre-mapped:
- `shouldDetectBrokenHashChainOnVerification` (R22) — seed several real entries via
  `LedgerService.append` against a real Postgres, directly corrupt one row via a raw admin
  connection (the only reachable way to simulate this — mirrors `SECURITY-THREAT-MODEL.md`'s own
  "insider alters a historical verification" threat exactly), assert `LedgerVerifier` reports the
  break at the correct entry with the correct reason.

Boundary tests implied, not separately named:
- An intact, multi-entry chain verifies `Intact()` (the "no false positive" counterpart).
- Both AC5 edge cases (empty ledger, genesis-only ledger).
- A distinct `prevHash`-only corruption (entry's own hash is still internally self-consistent,
  but its `prevHash` doesn't match the real prior entry) vs an `entryHash`-only corruption
  (payload or stored hash tampered directly) — R22 names both failure modes separately.
- A corrupted genesis `prevHash` specifically (AC3's own distinct check).
- A gap in `seq` (simulating a rolled-back transaction) does not itself cause a false `Broken`
  report — the positional, not arithmetic, prior-entry lookup (above) handles this correctly by
  construction, worth a dedicated test proving it.

## Constraints

- **Transaction:** read-only; no transaction boundary concern at all (no writes).
- **Thread-safety:** stateless beyond its injected `LedgerEntryRepository`, consistent with
  every other service class in this codebase.
- **Module boundaries:** no cross-module entity import (L12, continuity).
- **Scale:** this task fetches the full ledger into memory for a single verification call — a
  reasonable Phase 1/MVP choice (no pagination/streaming), matching this codebase's own
  established "no premature optimization" stance; worth revisiting only if a future caller's own
  real ledger size makes it a genuine concern, not pre-emptively solved here.

## Open Questions

None remaining — both Phase 1 questions are resolved directly above.
