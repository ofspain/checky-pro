package com.themistra.payment.receipt.ledger;

import com.themistra.payment.common.CanonicalJson;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * T20 (R22). Recomputes the hash chain {@link LedgerService#append} built, head to tail, and
 * reports the first entry that fails verification - the read-only counterpart that actually
 * cashes in the ledger's own tamper-evidence claim (`ARCHITECTURE.md` §6.5/§6.7's own "how do we
 * know Themistra didn't alter this record" framing).
 *
 * <p><b>No caller anywhere in this task breakdown (Phase 0, confirmed by direct re-reading of
 * every remaining task).</b> Built one step ahead of its own consumer, the same "primitive
 * before its caller" shape T15→T17 (`CryptoClient`) and T18→T19 (`ReceiptStore`) already
 * established twice in this group - the intended caller is a future, out-of-scope-for-Phase-1
 * consumer (an ops tool, or a later Dispute Service per the platform's own multi-phase
 * roadmap).</p>
 *
 * <p><b>Head-to-tail direction and "first break" (Phase 2/4, resolved directly, no product-
 * policy dimension).</b> "Head" = the most recent entry (highest {@code seq}); verification
 * walks backward toward genesis, stopping at the first entry that fails any check - once one
 * link is known compromised, exhaustively flagging every entry on the far side of it adds noise,
 * not information.</p>
 *
 * <p><b>The "prior entry" for the {@code prevHash} check is positional, not {@code seq}-
 * arithmetic (Phase 2/4) - a genuine, non-obvious correctness point.</b> {@code seq} is a
 * Postgres {@code IDENTITY} column; a transaction that reserves an identity value and then
 * rolls back (e.g. {@code ReceiptService.persistSignedReceipt}'s own abort-on-zero-rows path,
 * T19 AC6) leaves that value permanently unused - Postgres sequences never roll back - so a gap
 * in {@code seq} is expected and harmless. The real chain linkage is carried entirely by the
 * {@code prevHash}/{@code entryHash} values themselves (exactly how {@link
 * LedgerService#append}'s own {@code findTopByOrderBySeqDesc()} call determines "the current
 * head" too - by value, never by {@code seq} arithmetic), so this class compares each entry
 * against whichever row is positionally next in the {@code seq}-ordered list, never
 * {@code current.seq() - 1}.</p>
 *
 * <p><b>Point-in-time snapshot, not a live barrier (Phase 3/4, Kimi Finding 6).</b> {@link
 * #verify()} reflects the committed ledger state at query-start time; it does not synchronize
 * with concurrent {@link LedgerService#append} calls (no transaction, no advisory lock) and
 * should be treated as a point-in-time audit, not a live stop-the-world check.</p>
 */
@Component
public class LedgerVerifier {

    private static final String GENESIS_PREV_HASH = "0".repeat(64);
    private static final String SHA_256 = "SHA-256";

    private final LedgerEntryRepository repository;

    public LedgerVerifier(LedgerEntryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public LedgerVerificationResult verify() {
        List<LedgerEntry> entries = repository.findAllByOrderBySeqDesc();

        for (int i = 0; i < entries.size(); i++) {
            LedgerEntry current = entries.get(i);

            String recomputedHash = sha256(CanonicalJson.serialize(current.getPayload()) + current.getPrevHash());
            if (!recomputedHash.equals(current.getEntryHash())) {
                return new LedgerVerificationResult.Broken(current.getSeq(),
                        "stored entryHash does not match recomputed hash");
            }

            boolean isGenesis = i == entries.size() - 1;
            if (!isGenesis) {
                LedgerEntry priorEntry = entries.get(i + 1);
                if (!current.getPrevHash().equals(priorEntry.getEntryHash())) {
                    return new LedgerVerificationResult.Broken(current.getSeq(),
                            "stored prevHash does not match prior entry's stored entryHash");
                }
            } else if (!current.getPrevHash().equals(GENESIS_PREV_HASH)) {
                return new LedgerVerificationResult.Broken(current.getSeq(),
                        "genesis prevHash is not 64 zeros");
            }
        }

        return new LedgerVerificationResult.Intact();
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
