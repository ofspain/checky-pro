package com.themistra.payment.receipt.ledger;

import com.themistra.payment.common.CanonicalJson;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * T19 (R21, L7). Appends one entry to the tamper-evident hash chain: {@code prevHash} = the
 * current head's {@code entryHash} (or 64 zeros if no entry exists yet, the genesis case),
 * {@code entryHash} = SHA-256(canonical payload concatenated with {@code prevHash}) - L7's own
 * literal "canonical payload || prev_hash" notation, a string concatenation, not a nested JSON
 * field.
 *
 * <p>{@link #append} acquires {@link LedgerEntryRepository#acquireAppendLock()}'s own advisory
 * lock as its very first statement - encapsulated here rather than left to the caller (a
 * deviation from the frozen brief's own illustrative Phase 5 sketch, which showed the lock
 * acquired directly inside {@code ReceiptService}'s transaction block): {@code ReceiptService}
 * needs the same transaction to insert the {@code Receipt} row first (Finding 7's own
 * FK-ordering requirement - {@code ledger_entries.receipt_id} references {@code receipts.id}),
 * but the lock's only real purpose is to protect the head-read-then-insert sequence this method
 * performs, so acquiring it here keeps {@code ReceiptService} unaware of advisory locks
 * entirely. {@code pg_advisory_xact_lock} is transaction-scoped - it still protects this
 * method's own critical section no matter which statement in the enclosing transaction it was
 * acquired on, and is automatically released at that transaction's commit or rollback either
 * way.</p>
 *
 * <p>{@code @Transactional} on {@link #append} is itself load-bearing (found during real
 * Testcontainers verification, not assumed): without it, each of the three repository calls
 * inside this method runs in its own separate, independently-committed micro-transaction (Spring
 * Data's own per-query-method transaction wrapping) - the advisory lock would be acquired and
 * immediately released again before the head is even read, providing zero protection. The
 * default {@code REQUIRED} propagation also means a caller that already has a transaction open
 * (e.g. {@code ReceiptService}'s own {@code TransactionTemplate} block) has this method simply
 * join it, extending the lock's protection across whatever else that caller does in the same
 * transaction too.</p>
 *
 * <p><b>Why the lock's exact acquisition point within the transaction doesn't matter (Phase 8,
 * Kimi Finding 1 - re-examined against real Postgres semantics, not taken at face value).</b>
 * Kimi's Phase 8 review flagged that the frozen brief's own illustrative order named the lock as
 * the transaction's first statement, while this class acquires it only once {@code append} is
 * reached (after {@code ReceiptService} has already inserted the {@code Receipt} row). This
 * makes no functional difference: {@code pg_advisory_xact_lock} is held until the *enclosing
 * transaction's* commit or rollback, full stop - never released early because some "critical
 * section" inside that transaction finished. Acquiring it before or after the {@code Receipt}
 * insert yields an identical hold duration and an identical serialization guarantee for the
 * head-read-then-insert sequence this method performs; moving the call earlier would not close
 * any real window, only relocate it. Kept here rather than moved, since moving it would also
 * re-expose {@code ReceiptService} to advisory-lock mechanics this design deliberately keeps
 * encapsulated inside this class.</p>
 */
@Component
public class LedgerService {

    private static final String GENESIS_PREV_HASH = "0".repeat(64);
    private static final String SHA_256 = "SHA-256";

    private final LedgerEntryRepository repository;
    private final Clock clock;

    public LedgerService(LedgerEntryRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public LedgerEntry append(Long receiptId, Map<String, Object> payload) {
        Objects.requireNonNull(receiptId, "receiptId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        repository.acquireAppendLock();
        String prevHash = repository.findTopByOrderBySeqDesc()
                .map(LedgerEntry::getEntryHash)
                .orElse(GENESIS_PREV_HASH);
        String canonicalJson = CanonicalJson.serialize(payload);
        String entryHash = sha256(canonicalJson + prevHash);
        return repository.save(LedgerEntry.create(receiptId, prevHash, entryHash, payload, clock.instant()));
    }

    /** Read-only lookup for the integrity check (FU-T28-3): the ledger payload recorded for one
     * receipt, as a defensive copy, or empty when the receipt has no ledger entry. */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findPayloadByReceiptId(Long receiptId) {
        Objects.requireNonNull(receiptId, "receiptId must not be null");
        return repository.findByReceiptId(receiptId).map(LedgerEntry::getPayload);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
