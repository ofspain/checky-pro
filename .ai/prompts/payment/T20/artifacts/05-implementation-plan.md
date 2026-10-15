# payment · T20 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

No code in this artifact — planning only.

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerVerificationResult.java`
2. `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerVerifier.java`

## Files to modify

1. `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerEntryRepository.java`

## Public types/methods (signatures)

**`LedgerVerificationResult`** (sealed, `receipt.ledger` package — frozen, Phase 4):
```java
public sealed interface LedgerVerificationResult {
    record Intact() implements LedgerVerificationResult {}
    record Broken(Long seq, String reason) implements LedgerVerificationResult {}
}
```

**`LedgerEntryRepository`** (one new method):
```java
List<LedgerEntry> findAllByOrderBySeqDesc();
```

**`LedgerVerifier`** (new `@Component`, frozen signature):
```java
@Component
public class LedgerVerifier {
    public LedgerVerifier(LedgerEntryRepository repository) { ... }
    public LedgerVerificationResult verify() { ... }
}
```

## Private methods

- `LedgerVerifier`: a private `recomputedEntryHash(LedgerEntry entry)` → `String` — mirrors
  `LedgerService`'s own `sha256`/`GENESIS_PREV_HASH` private-helper duplication precedent
  (confirmed established twice already: `ReceiptDigest`/`LedgerService`), not a shared utility.
- `LedgerVerifier`: a private static `GENESIS_PREV_HASH` constant (`"0".repeat(64)`), duplicated
  from `LedgerService`'s own identical constant for the same reason.

## Entities used

`LedgerEntry` (T19, read-only). No `Payment`/`Invoice`/`Receipt` entity touched — confirmed this
task's own scope is entirely contained within `receipt.ledger`.

## Repositories used

`LedgerEntryRepository` (new read method), via `LedgerVerifier` directly — there is no service
layer above it yet (no caller exists, confirmed Phase 0/2), so `LedgerVerifier` is itself the
"service" for this task's own purposes, consistent with `LedgerService`'s own equivalent
standalone shape at T19.

## Unit/integration tests required

A real Testcontainers test (mirrors `LedgerServiceTest`'s own established harness almost
exactly, reusable directly) — the hash-chain verification property cannot be meaningfully proven
against a mocked repository, and the task's own text explicitly requires testing against "a
deliberately corrupted row":

**`LedgerVerifierTest`:**
1. `shouldDetectBrokenHashChainOnVerification` (named) — seed 3 real entries via
   `LedgerService.append`, corrupt the middle entry's `payload` via a raw admin `UPDATE`
   (bypassing the application's own `@Immutable`/no-`UPDATE` design entirely — the only
   reachable way to simulate this, matching `SECURITY-THREAT-MODEL.md`'s own "insider alters a
   historical verification" threat), assert `verify()` returns `Broken(middleSeq, "stored
   entryHash does not match recomputed hash")`.
2. `shouldReportIntactForAnUncorruptedMultiEntryChain` — 3 real, uncorrupted entries, assert
   `Intact()`.
3. `shouldReportIntactForAnEmptyLedger` (AC5).
4. `shouldReportIntactForAGenesisOnlyLedger` (AC5).
5. `shouldDetectPrevHashCorruptionDistinctFromEntryHashCorruption` — corrupt only an entry's own
   stored `prevHash` (leaving its `entryHash`/`payload` self-consistent with each other, but no
   longer matching the real prior entry), assert `Broken(seq, "stored prevHash does not match
   prior entry's stored entryHash")` — distinct reason from test 1.
6. `shouldDetectCorruptedGenesisPrevHash` — corrupt the genesis entry's own `prevHash` to
   something other than 64 zeros (while keeping its `entryHash` self-consistent with that
   corrupted value, so AC1 passes and AC3 is the one that actually fires), assert `Broken(seq,
   "genesis prevHash is not 64 zeros")`.
7. `shouldNotFalselyReportBrokenAcrossASeqGap` (Phase 4 Finding 7's own corrected mechanism) —
   advance the identity sequence directly (`SELECT nextval(pg_get_serial_sequence(...))`)
   between two real `append(...)` calls, assert `verify()` still returns `Intact()`.

## Execution order

1. `LedgerVerificationResult` — no dependency.
2. `LedgerEntryRepository.findAllByOrderBySeqDesc` — no dependency on 1.
3. `LedgerVerifier` — depends on 1 and 2, plus the already-existing `CanonicalJson` (T16).
4. All test files (deferred to Phase 10, per the standard rule — this task is not test-only).

## Traceability check

Every file above traces directly to the frozen brief's own "Files to Create"/"Files to Modify"
lists (Phase 4). No file is added that the brief does not authorize.
