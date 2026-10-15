# payment · T19 · Phase 4 — Freeze Task Brief

**STATUS: FROZEN** — approved as written via `AskUserQuestion`.

Consumes: `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md`

## Self-correction, found while dispositioning Kimi's Finding 12 (disclosed forward, not a silent edit to Phase 2)

Phase 2's own TIB put `LedgerEntry`/`LedgerEntryRepository`/`LedgerService` in a top-level
`com.themistra.payment.ledger` package and `ReceiptIssuedPayload` in `payment.event` (mirroring
`PaymentFinalizedPayload`'s own location). **Both are wrong** — re-reading `design.md` §6's own
pinned package-map diagram line by line (missed at Phase 0/1/2) shows `ledger/` and `event/` are
explicitly nested **under** `receipt/`:
```
receipt/
├── Receipt.java / ReceiptRepository.java
├── ReceiptService.java
├── ReceiptDigest.java
├── ReceiptStore.java
├── ledger/
│   ├── LedgerEntry.java / LedgerEntryRepository.java
│   ├── LedgerService.java
│   └── ... (LedgerVerifier/LedgerAnchor* — later tasks)
└── event/ReceiptIssuedPayload.java
```
Confirmed this is the real, already-followed convention, not just a diagram: `InvoiceCreatedPayload`
lives in `invoice.event`, `PaymentFinalizedPayload`/`PaymentSeenPayload` live in `payment.event` —
every existing event payload sits under its own feature module's `event` subpackage. The correct
packages for this task are therefore `com.themistra.payment.receipt.ledger.*` and
`com.themistra.payment.receipt.event.ReceiptIssuedPayload`. This also matters for Finding 12
below: `receipt.ledger` is a genuinely different Java package from `receipt` (package-private
visibility does not span sub-packages, the identical lesson T15's own Phase 8 review already
established for `CryptoClient`/`AttestRequest`) — so `ReceiptStore`'s canonicalizer must become
`public` regardless of this correction, not merely package-private.

## Disposition of Phase 3 (Kimi) findings

All 13 verified against real source before dispositioning (migration SQL, `design.md` §4c's own
pinned event schema and package map — both missed at Phase 0/1/2 until Kimi's review surfaced
them, `ReceiptStore.java`, `PaymentRepository.java`).

| # | Finding | Severity | Disposition | Reason |
|---|---|---|---|---|
| 1 | `receipt.issued` payload/contract unfrozen | High | **ACCEPTED** | Verified `design.md` lines 328-353 pin an exact 11-field JSON Schema (`invoiceUuid`/`paymentUuid`/`receiptUuid`/`merchantAccountUuid`/`chain`/`txHash`/`amount`/`tokenContractAddress`/`kmsKeyId`/`receiptUri`/`occurredAt`, `additionalProperties: false`) that my own Phase 0-2 completely missed. `ReceiptIssuedPayload`'s fields frozen to match exactly (below). The physical `.schema.json` file itself stays task 24's own job — confirmed `invoice.created`/`payment.seen`/`payment.finalized` all follow this identical "fields match the pinned schema, file authored later" precedent (none of the three has a `contracts/events/payments/*.json` file yet either). |
| 2 | No abort path on `markAttestedIfFinalized` returning 0 | High | **ACCEPTED** | Mirrors `handleBlocked`'s own split-result guard (T17) exactly: if the atomic update affects 0 rows, throw inside the transaction (rolling back the `Receipt`/`LedgerEntry`/outbox rows already staged in that same transaction), caught outside, converted to `ReceiptIssueResult.Failed`. |
| 3 | Advisory-lock key unnamed/unnamespaced | Medium | **ACCEPTED** | `SELECT pg_advisory_xact_lock(hashtext('themistra.payment.ledger.append')::bigint)` — a single Java constant (`LedgerService.LOCK_KEY_EXPRESSION` or similar), human-readable and namespaced by the full Kafka-topic-style convention this service already uses elsewhere (`EventTopics`), not a bare arbitrary number. |
| 4 | `attested_at` omitted | Medium | **ACCEPTED** | Verified `payments.attested_at TIMESTAMPTZ` exists (`V1`, already populated-by-nothing). `markAttestedIfFinalized` sets `state`, `attested_at`, `updated_at` in one `UPDATE`. |
| 5 | Ledger S3 self-description ambiguous | Medium | **ACCEPTED** | Ledger payload's `s3KeyPrefix` = the already-normalized value `ReceiptStorageLocation.s3KeyPrefix()` returns (post-`normalizedPrefix`, confirmed by direct read of `ReceiptStore.java:96`) — not re-derived. Documented: JSON object at `{s3KeyPrefix}.json`, PDF at `{s3KeyPrefix}.pdf`. |
| 6 | Orphaned S3 objects on DB failure | Medium | **ACCEPTED** | Extends the Phase 1 Open Question 2 disclosure already flagged forward — `ReceiptService`'s own class Javadoc gets a new paragraph naming this specific window (S3 write succeeds, DB transaction fails) as a sharper form of the existing "no retry" gap: here a *real KMS signature was already consumed*, not just an attempt that never ran. No cleanup mechanism built (L9 bans a delete path in application code); operational reconciliation only. |
| 7 | Insert order inside the transaction unspecified | Medium | **ACCEPTED** | Verified `ledger_entries.receipt_id REFERENCES receipts(id)` — `Receipt` must commit-order before `LedgerEntry` within the same transaction. Frozen order: (1) advisory lock, (2) insert `Receipt`, (3) read head + insert `LedgerEntry`, (4) `markAttestedIfFinalized` (abort on 0 rows), (5) `VerificationRecord`, (6) publish `receipt.issued`. |
| 8 | V9 migration re-grants `ledger_entries` | Low | **ACCEPTED** | Verified `V2` already grants `INSERT, SELECT` on `ledger_entries`. Renamed to `V9__payment_app_receipts_grant.sql`, grants only `receipts`, comments reference `V2` for the existing grant. |
| 9 | `LedgerService.append` signature deferred | Medium | **ACCEPTED** | Frozen now (below), not deferred to Phase 5. |
| 10 | Cross-module FK risk for `Receipt`/`LedgerEntry` | Medium | **ACCEPTED** | Made explicit in this frozen brief's own Constraints: `Receipt.paymentId`/`LedgerEntry.receiptId` are scalar `Long`, never `@ManyToOne`. |
| 11 | `LedgerEntry` PK is `seq`, not `id` | Low | **ACCEPTED** | `@Id @GeneratedValue(strategy = IDENTITY) @Column(name = "seq") private Long seq;` — stated explicitly now. |
| 12 | Reusing `ReceiptStore`'s canonicalizer needs a public seam | Medium | **ACCEPTED** | `ReceiptStore.toCanonicalMap` (currently `private`) becomes `public static` (no instance state needed — it's a pure function of its two parameters already). `receipt.ledger.LedgerService` calls `ReceiptStore.toCanonicalMap(payload, signed)` directly, then adds the two S3 fields itself, rather than duplicating the 15-field logic. **This is the one change to an already-closed T18 file this task makes** — a visibility widening only, no behavior change, confirmed by re-running T18's own `ReceiptStoreTest` suite unmodified after the change (Phase 6). |
| 13 | `VerificationRecord` trigger key/detail unspecified for `ATTESTED` | Low | **ACCEPTED** | `triggerEventKey` = the same `chain.tx.finalized` idempotency key `issueIfEligible` already receives as a parameter (unchanged — already threaded through); `detail` = `Map.of("receiptUuid", receiptUuid.toString())`. |

No finding was rejected.

## Amendments folded into this frozen brief (supersede the Phase 2 TIB where they conflict)

**Corrected packages (self-correction above):** `com.themistra.payment.receipt.ledger.LedgerEntry`/
`LedgerEntryRepository`/`LedgerService`; `com.themistra.payment.receipt.event.ReceiptIssuedPayload`.

**`ReceiptIssuedPayload`** (Finding 1, exact fields from `design.md`'s own pinned schema):
```java
package com.themistra.payment.receipt.event;

public record ReceiptIssuedPayload(
        UUID invoiceUuid, UUID paymentUuid, UUID receiptUuid, UUID merchantAccountUuid,
        String chain, String txHash, String amount, String tokenContractAddress,
        String kmsKeyId, String receiptUri, Instant occurredAt) {}
```
`amount` = `payment.amount().toPlainString()` (L6, decimal string). `receiptUri` =
`"s3://" + location.bucket() + "/" + location.s3KeyPrefix() + ".json"` (the canonical JSON
object — the machine-verifiable artifact, matching T18's own "JSON first" framing — not the
PDF). `occurredAt` = the same `now` used for `attested_at`.

**`LedgerService`** (Finding 9, frozen signature):
```java
package com.themistra.payment.receipt.ledger;

@Component
public class LedgerService {
    public LedgerEntry append(Long receiptId, Map<String, Object> payload)
}
```
Internally: acquires the advisory lock (Finding 3), reads the current head's `entry_hash` (or 64
zeros if no row exists — `LedgerEntryRepository.findTopByOrderBySeqDesc()`, mapped to
`Optional<LedgerEntry>`), computes `entry_hash = SHA-256(CanonicalJson.serialize(payload) +
prevHash)`, saves and returns the new `LedgerEntry`. Caller (`ReceiptService`) builds `payload`
by calling `ReceiptStore.toCanonicalMap(payload, signed)` (now `public static`, Finding 12) and
adding `s3Bucket`/`s3KeyPrefix` (Finding 5).

**`PaymentRepository.markAttestedIfFinalized`** (Findings 2/4, mirrors `markHeldIfFinalized`'s
shape exactly):
```java
@Modifying
@Query("UPDATE Payment p SET p.state = :newState, p.attestedAt = :now, p.updatedAt = :now "
       + "WHERE p.id = :id AND p.state = :expectedCurrentState")
int markAttestedIfFinalized(@Param("id") Long id,
        @Param("expectedCurrentState") PaymentState expectedCurrentState,
        @Param("newState") PaymentState newState, @Param("now") Instant now);
```
`PaymentService.markAttestedIfFinalized(Long paymentId, String triggerEventKey, UUID
receiptUuid, Instant now)` → `boolean`, records the `VerificationRecord` (Finding 13) on success.

**`ReceiptService.issueIfEligible`'s restructured `Signed` branch** (Finding 7's frozen order):
```java
if (result instanceof AttestationResult.Signed signed) {
    ReceiptStorageLocation location = receiptStore.store(payload, signed);   // outside any transaction
    try {
        return transactionTemplate.execute(status -> {
            // (1) advisory lock — first statement
            jdbcLock.acquire();   // exact mechanism a Phase 5 implementation choice (native query vs JdbcTemplate)
            // (2) insert Receipt
            Receipt receipt = receiptRepository.save(Receipt.create(receiptUuid, payment.id(),
                    digest, signed.signature(), signed.kmsKeyId(), location.bucket(),
                    location.s3KeyPrefix(), signed.signedAt(), clock.instant()));
            // (3) read head + insert LedgerEntry
            Map<String, Object> ledgerPayload = buildLedgerPayload(payload, signed, location);
            ledgerService.append(receipt.getId(), ledgerPayload);
            // (4) ATTESTED transition, abort on 0 rows (Finding 2)
            Instant now = clock.instant();
            boolean attested = paymentService.markAttestedIfFinalized(payment.id(),
                    triggerEventKey, receiptUuid, now);
            if (!attested) {
                throw new IllegalStateException("Payment " + payment.id()
                        + " was no longer FINALIZED when the ATTESTED transition ran");
            }
            // (6) publish receipt.issued, same transaction (R25)
            outboxPublisher.publish("receipt", receiptUuid.toString(), "receipt.issued",
                    new ReceiptIssuedPayload(invoice.invoiceUuid(), payment.paymentUuid(), receiptUuid,
                            invoice.merchantAccountUuid(), payment.chain(), payment.txHash(),
                            payment.amount().toPlainString(), payment.tokenContractAddress(),
                            signed.kmsKeyId(), receiptUri(location), now));
            return new ReceiptIssueResult.Signed(receiptUuid, signed);
        });
    } catch (RuntimeException e) {
        log.error("Failed to persist receipt/ledger/ATTESTED transition for payment {}", payment.id(), e);
        return new ReceiptIssueResult.Failed("persist-failed: " + e.getMessage());
    }
}
```
(Illustrative — exact field/method names confirmed against real signatures at Phase 5.)

**`ReceiptService`'s class Javadoc** gains the Finding 6 disclosure (the sharper, post-`attest()`
crash window — a consumed KMS signature, not just a missed attempt) alongside the already-
existing "no retry" disclosure from T17.

## Final scope, files, and acceptance criteria (carried from Phase 2 TIB, as amended above)

**Scope:** unchanged in substance, corrected packages (above).

**Files to Create:** `receipt/Receipt.java`, `receipt/ReceiptRepository.java`,
`receipt/ledger/LedgerEntry.java`, `receipt/ledger/LedgerEntryRepository.java`,
`receipt/ledger/LedgerService.java`, `receipt/event/ReceiptIssuedPayload.java`,
`V9__payment_app_receipts_grant.sql` (renamed, Finding 8).

**Files to Modify:** `receipt/ReceiptService.java` (restructured `Signed` branch + Javadoc),
`receipt/ReceiptStore.java` (Finding 12 — `toCanonicalMap` widened to `public static`, no
behavior change), `payment/PaymentRepository.java`/`PaymentService.java` (new
`markAttestedIfFinalized`).

**Acceptance Criteria** (AC1-AC6 from Phase 2, amended):
- AC1-AC5: unchanged in substance, sharpened by the findings above (exact insert order, exact
  lock key, `attested_at` populated).
- **AC6 (amended, Finding 2):** a `markAttestedIfFinalized` call returning 0 rows aborts the
  entire transaction (no `Receipt`/`LedgerEntry`/outbox row survives) and returns
  `ReceiptIssueResult.Failed`.
- **AC7 (new, Finding 1):** `ReceiptIssuedPayload`'s 11 fields match `design.md`'s own pinned
  schema exactly, field-for-field, even though the physical contract file isn't authored until
  task 24.

## Open Questions

None remaining.
