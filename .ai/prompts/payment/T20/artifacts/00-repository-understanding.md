# payment · T20 · Phase 0 — Repository Understanding

Artifact: `.ai/prompts/payment/T20/artifacts/00-repository-understanding.md`

## 1. Architecture summary

`payment-service` (`services/payment`, package `com.themistra.payment`). T19 built the real
hash-chain append (`LedgerService.append`) and its backing entity (`LedgerEntry`). T20 is the
read-only counterpart: `LedgerVerifier` recomputes the chain head-to-tail and reports the first
break, proving (or disproving) the "insider alters a historical verification" defense
`ARCHITECTURE.md` §6.5/§6.7 names as this service's own core tamper-evidence claim.

## 2. Existing code this task touches

**Already exists, read/extend:**
- `receipt/ledger/LedgerEntry.java` (T19) — `seq`/`prevHash`/`entryHash`/`payload`/`createdAt`,
  now `@Immutable` (T19's own severe-bug fix, Phase 11) — confirmed this does **not** affect
  read-only queries, only Hibernate's own dirty-checking on writes, so `LedgerVerifier`'s own
  read-only use is unaffected.
- `receipt/ledger/LedgerEntryRepository.java` (T19, package-private) — currently has
  `findTopByOrderBySeqDesc()` and the advisory-lock native query; needs a new method to fetch
  every entry ordered for a full chain walk.
- `common/CanonicalJson.java` (T16) — `serialize(Map<String,Object>)`, the same canonicalizer
  `LedgerService.append` already uses to compute `entryHash`; `LedgerVerifier` must reuse this
  exact function to *recompute* each entry's hash, not a second, independently-written one
  (`design.md` O4's own explicit "one shared canonicalizer" intent, already established twice
  this group — T16/T18, T16/T19).

**Does not exist yet (net new, confirmed by direct search):**
- `receipt/ledger/LedgerVerifier.java` — this task's own sole deliverable.

**Confirmed NOT part of this task's own scope, by direct re-reading of `tasks.md` tasks 20-29:**
- **No controller, scheduled job, or any other caller wires `LedgerVerifier` into this service's
  own runtime anywhere in the full 29-task breakdown.** Re-checked every remaining task (21
  deferred/skipped, 22-26 export/contracts/hardening/e2e-test, 27-29 final-verification/threat-
  model/spec-bump) — none mentions `LedgerVerifier` again. This mirrors T18's own "`ReceiptStore`
  has no caller yet" shape exactly: a correct, confirmed, deliberate "build the primitive before
  its consumer" pattern already established twice in this group (T15→T17 for `CryptoClient`;
  T18→T19 for `ReceiptStore`). `ARCHITECTURE.md` §6.7/§7's own framing ("Dispute Service reads
  the hash-chain ledger... the defensible record is already being built") confirms the real
  intended caller is a **future, out-of-scope-for-Phase-1 consumer** (an ops tool or a later
  Dispute Service, per the platform's own multi-phase roadmap), not anything inside this task
  breakdown.

## 3. Established patterns to follow

- **Reuse, never reinvent, the canonical-JSON serializer** — `CanonicalJson.serialize(...)`
  (T16) is the one function both `ReceiptDigest`/`ReceiptStore` (the signing side) and
  `LedgerService` (the appending side) already use; `LedgerVerifier` (the checking side) must
  call the exact same function to recompute a hash that is directly comparable to what
  `LedgerService.append` originally stored — any independent reimplementation would risk a
  silent canonicalization drift that this design has twice already deliberately avoided.
- **Sealed result types for a method's own outcome**, not a boolean + nullable fields — mirrors
  `AttestationResult`/`ReceiptIssueResult`'s own established convention throughout this service.
- **No entity load, no merge** — `LedgerVerifier` only ever reads; no state-conditional update
  pattern applies here at all (this task is the first purely read-only primitive in the
  attestation/receipts/ledger group).

## 4. Testing conventions

- A real Testcontainers test is the only way to prove this task's own one named test
  (`shouldDetectBrokenHashChainOnVerification`) — the task's own text explicitly says "test with
  a deliberately corrupted row," which requires seeding real rows then directly corrupting one
  via a raw admin SQL `UPDATE` (bypassing `payment_app`'s own intentional no-`UPDATE` grant,
  T19's `@Immutable` fix, and the `ledger_entries` append-only design entirely — exactly the
  "insider alters a historical verification" threat-model scenario `LedgerVerifier` exists to
  detect, simulated the only way it's actually reachable: as the database owner, not as the
  application's own role). Mirrors `LedgerServiceTest`'s own already-established Testcontainers
  harness shape (T19) almost exactly, reusable directly.

## 5. Known gaps / unknowns

**A genuine implementation-shape ambiguity, not resolved by the spec text, flagged forward to
Phase 1/2**: R22's own text ("recompute each entry hash... report any entry whose...") describes
checking every entry, while the task's own text says "reports the first break" (singular). Also
ambiguous: "head-to-tail" — does "head" mean the most recent entry (walk backward toward
genesis) or the oldest/genesis entry (walk forward toward the most recent)? Neither the spec nor
`ARCHITECTURE.md` defines this literally. A reasonable, low-risk default exists (see Phase 1/2),
proposed directly rather than escalated — this is a pure implementation-shape choice with no
product-policy dimension, mirroring this session's own "propose directly when low-risk" pattern
(Q5/Q6/Q7 in this same spec).

**Known, not a gap — confirmed above, not assumed:** `LedgerVerifier` has no caller anywhere in
this task breakdown; `LedgerEntry`'s own `@Immutable` annotation (T19) does not affect read-only
queries.
