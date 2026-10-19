package com.themistra.payment.receipt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.payment.common.CanonicalJson;
import com.themistra.payment.receipt.ReceiptReconciliationResult.Discrepancy;
import com.themistra.payment.receipt.ledger.LedgerService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Threat #5 follow-up FU-T28-3: compares the database against the locked S3 originals, so a
 * rewrite of {@code receipts} or {@code ledger_entries} by someone with database-owner access is
 * caught even though the hash chain alone can't catch it. Such an insider can recompute every
 * {@code entry_hash} so {@code LedgerVerifier} still reports {@code Intact}. They can't change
 * the S3 objects: those are COMPLIANCE-locked and written once. This is the interim measure until
 * the on-chain anchor (R26, deferred) exists.
 *
 * <p>For every {@code receipts} row it checks that:</p>
 * <ul>
 *   <li>the JSON object exists at {@code s3_key + ".json"} and parses;</li>
 *   <li>its {@code receiptUuid}, {@code signature}, {@code kmsKeyId} and {@code signedAt} equal
 *       the row's;</li>
 *   <li>its canonical digest (the object minus the three signature fields, through
 *       {@link CanonicalJson}, then SHA-256) equals {@code receipts.digest_sha256}, the value that
 *       was signed;</li>
 *   <li>a ledger entry exists for the receipt, its payload minus {@code s3Bucket}/{@code
 *       s3KeyPrefix} serialises identically to the S3 object, and those two fields point at the
 *       row's bucket and key.</li>
 * </ul>
 * <p>It also lists the bucket and reports any stored receipt object with no {@code receipts} row,
 * which is what deleting a receipt and its ledger entry from the database would leave behind.</p>
 *
 * <p><b>Limits.</b> It compares against the S3 originals; it does not verify the KMS signature
 * cryptographically. It reads every receipt object on each run, which is fine at Phase 1 volumes.
 * One receipt that can't be checked is reported as a discrepancy, never an aborted run.</p>
 */
@Component
public class ReceiptReconciler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final List<String> SIGNATURE_FIELDS = List.of("signature", "kmsKeyId", "signedAt");
    private static final List<String> LEDGER_LOCATION_FIELDS = List.of("s3Bucket", "s3KeyPrefix");

    static final String REASON_OBJECT_MISSING = "S3 receipt object is missing";
    static final String REASON_OBJECT_UNPARSEABLE = "S3 receipt object is not valid JSON";
    static final String REASON_RECEIPT_UUID_MISMATCH = "S3 receiptUuid differs from the receipts row";
    static final String REASON_SIGNATURE_MISMATCH = "S3 signature, kmsKeyId or signedAt differs from the receipts row";
    static final String REASON_DIGEST_MISMATCH = "receipts.digest_sha256 differs from the digest of the S3 original";
    static final String REASON_LEDGER_MISSING = "receipt has no ledger entry";
    static final String REASON_LEDGER_PAYLOAD_MISMATCH = "ledger payload differs from the S3 original";
    static final String REASON_LEDGER_LOCATION_MISMATCH = "ledger s3Bucket/s3KeyPrefix differ from the receipts row";
    static final String REASON_ORPHAN_OBJECT = "S3 receipt object has no receipts row";

    private final ReceiptRepository receiptRepository;
    private final ReceiptStore receiptStore;
    private final LedgerService ledgerService;

    public ReceiptReconciler(ReceiptRepository receiptRepository, ReceiptStore receiptStore,
            LedgerService ledgerService) {
        this.receiptRepository = Objects.requireNonNull(receiptRepository, "receiptRepository must not be null");
        this.receiptStore = Objects.requireNonNull(receiptStore, "receiptStore must not be null");
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService must not be null");
    }

    public ReceiptReconciliationResult reconcile() {
        List<Receipt> receipts = receiptRepository.findAll(Sort.by("id"));
        List<Discrepancy> discrepancies = new ArrayList<>();
        Set<String> knownPrefixes = new HashSet<>();

        for (Receipt receipt : receipts) {
            knownPrefixes.add(receipt.getS3Key());
            try {
                checkReceipt(receipt).ifPresent(reason ->
                        discrepancies.add(new Discrepancy(receipt.getReceiptUuid(), receipt.getS3Key(), reason)));
            } catch (RuntimeException e) {
                discrepancies.add(new Discrepancy(receipt.getReceiptUuid(), receipt.getS3Key(),
                        "receipt could not be checked: " + e.getMessage()));
            }
        }

        for (String storedPrefix : receiptStore.listStoredReceiptPrefixes()) {
            if (!knownPrefixes.contains(storedPrefix)) {
                discrepancies.add(new Discrepancy(null, storedPrefix, REASON_ORPHAN_OBJECT));
            }
        }

        return new ReceiptReconciliationResult(receipts.size(), discrepancies);
    }

    /** @return the first failed check's reason, or empty when the receipt is consistent. */
    private Optional<String> checkReceipt(Receipt receipt) {
        Optional<byte[]> stored = receiptStore.readJsonObject(receipt.getS3Bucket(), receipt.getS3Key());
        if (stored.isEmpty()) {
            return Optional.of(REASON_OBJECT_MISSING);
        }
        Map<String, Object> s3Fields;
        try {
            s3Fields = OBJECT_MAPPER.readValue(stored.get(), MAP_TYPE);
        } catch (IOException e) {
            return Optional.of(REASON_OBJECT_UNPARSEABLE);
        }

        if (!receipt.getReceiptUuid().toString().equals(s3Fields.get("receiptUuid"))) {
            return Optional.of(REASON_RECEIPT_UUID_MISMATCH);
        }
        if (!receipt.getSignature().equals(s3Fields.get("signature"))
                || !receipt.getKmsKeyId().equals(s3Fields.get("kmsKeyId"))
                || !sameInstant(receipt.getSignedAt(), s3Fields.get("signedAt"))) {
            return Optional.of(REASON_SIGNATURE_MISMATCH);
        }

        Map<String, Object> signedFields = new LinkedHashMap<>(s3Fields);
        SIGNATURE_FIELDS.forEach(signedFields::remove);
        if (!sha256(CanonicalJson.serialize(signedFields)).equals(receipt.getDigestSha256())) {
            return Optional.of(REASON_DIGEST_MISMATCH);
        }

        Optional<Map<String, Object>> ledgerPayload = ledgerService.findPayloadByReceiptId(receipt.getId());
        if (ledgerPayload.isEmpty()) {
            return Optional.of(REASON_LEDGER_MISSING);
        }
        Map<String, Object> ledgerFields = new LinkedHashMap<>(ledgerPayload.get());
        Object ledgerBucket = ledgerFields.get("s3Bucket");
        Object ledgerKeyPrefix = ledgerFields.get("s3KeyPrefix");
        LEDGER_LOCATION_FIELDS.forEach(ledgerFields::remove);
        if (!CanonicalJson.serialize(ledgerFields).equals(CanonicalJson.serialize(s3Fields))) {
            return Optional.of(REASON_LEDGER_PAYLOAD_MISMATCH);
        }
        if (!receipt.getS3Bucket().equals(ledgerBucket) || !receipt.getS3Key().equals(ledgerKeyPrefix)) {
            return Optional.of(REASON_LEDGER_LOCATION_MISMATCH);
        }
        return Optional.empty();
    }

    private static boolean sameInstant(Instant expected, Object stored) {
        if (!(stored instanceof String text)) {
            return false;
        }
        try {
            // receipts.signed_at is TIMESTAMPTZ (microsecond precision); the S3 JSON keeps the
            // value as signed, which may carry nanoseconds.
            return Instant.parse(text).truncatedTo(ChronoUnit.MICROS).equals(expected.truncatedTo(ChronoUnit.MICROS));
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
