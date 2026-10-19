package com.themistra.payment.receipt;

import com.themistra.payment.common.CanonicalJson;
import com.themistra.payment.common.crypto.AttestationResult;
import com.themistra.payment.receipt.ReceiptReconciliationResult.Discrepancy;
import com.themistra.payment.receipt.ledger.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Threat #5 follow-up FU-T28-3. Each test starts from one fully consistent receipt (row, locked S3
 * original and ledger entry all agree) and changes exactly one thing, the way a database-owner
 * rewrite would. {@code PaymentEndToEndIntegrationTest} proves the same check through the real
 * Postgres, JSONB and LocalStack wiring.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptReconcilerTest {

    private static final String BUCKET = "my-bucket";
    private static final UUID RECEIPT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PREFIX = "receipts/" + RECEIPT_UUID;
    private static final long RECEIPT_ID = 7L;
    private static final Instant SIGNED_AT = Instant.parse("2026-10-15T00:00:01.123456789Z");

    @Mock
    private ReceiptRepository receiptRepository;
    @Mock
    private ReceiptStore receiptStore;
    @Mock
    private LedgerService ledgerService;

    private ReceiptReconciler reconciler;
    private Map<String, Object> storedFields;
    private Map<String, Object> ledgerPayload;
    private String digest;

    @BeforeEach
    void setUp() {
        reconciler = new ReceiptReconciler(receiptRepository, receiptStore, ledgerService);
        ReceiptPayload payload = new ReceiptPayload(RECEIPT_UUID,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "ETHEREUM", "0xabc123", "0xdef456", (short) 6, new BigDecimal("1000000"),
                "0x789", "0x012", Instant.parse("2026-07-13T01:00:00Z"));
        AttestationResult.Signed signed = new AttestationResult.Signed("c2ln", "kms-key-1", SIGNED_AT);
        storedFields = new LinkedHashMap<>(ReceiptStore.toCanonicalMap(payload, signed));
        Map<String, Object> signedFields = new LinkedHashMap<>(storedFields);
        List.of("signature", "kmsKeyId", "signedAt").forEach(signedFields::remove);
        digest = sha256(CanonicalJson.serialize(signedFields));
        ledgerPayload = new LinkedHashMap<>(storedFields);
        // As JSONB hands it back: tokenDecimals comes back as an Integer, not the Short written.
        ledgerPayload.put("tokenDecimals", 6);
        ledgerPayload.put("s3Bucket", BUCKET);
        ledgerPayload.put("s3KeyPrefix", PREFIX);
    }

    private static Receipt receipt(String digest, String signature, Instant signedAt, String s3Key) {
        // receipts.signed_at is TIMESTAMPTZ, so the row carries microseconds only.
        Receipt receipt = Receipt.create(RECEIPT_UUID, 3L, digest, signature, "kms-key-1", BUCKET, s3Key,
                signedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS), Instant.now());
        ReflectionTestUtils.setField(receipt, "id", RECEIPT_ID);
        return receipt;
    }

    private Receipt consistentReceipt() {
        return receipt(digest, "c2ln", SIGNED_AT, PREFIX);
    }

    private void givenRows(Receipt... receipts) {
        when(receiptRepository.findAll(any(Sort.class))).thenReturn(List.of(receipts));
    }

    private void givenStoredObject(Map<String, Object> fields) {
        when(receiptStore.readJsonObject(BUCKET, PREFIX)).thenReturn(Optional.of(
                CanonicalJson.serialize(fields).getBytes(StandardCharsets.UTF_8)));
    }

    private void givenListed(String... prefixes) {
        when(receiptStore.listStoredReceiptPrefixes()).thenReturn(List.of(prefixes));
    }

    private void givenLedgerPayload(Map<String, Object> payload) {
        when(ledgerService.findPayloadByReceiptId(RECEIPT_ID)).thenReturn(Optional.ofNullable(payload));
    }

    private ReceiptReconciliationResult reconcileExpectingOne(String reason) {
        ReceiptReconciliationResult result = reconciler.reconcile();
        assertThat(result.consistent()).isFalse();
        assertThat(result.discrepancies()).singleElement().satisfies(d -> {
            assertThat(d.reason()).isEqualTo(reason);
            assertThat(d.receiptUuid()).isEqualTo(RECEIPT_UUID);
            assertThat(d.s3KeyPrefix()).isEqualTo(PREFIX);
        });
        return result;
    }

    @Test
    void shouldReportAConsistentReceiptAsConsistent() {
        givenRows(consistentReceipt());
        givenStoredObject(storedFields);
        givenLedgerPayload(ledgerPayload);
        givenListed(PREFIX);

        ReceiptReconciliationResult result = reconciler.reconcile();

        assertThat(result.consistent()).isTrue();
        assertThat(result.receiptsChecked()).isEqualTo(1);
    }

    /** The core case: an owner rewrites the signed digest, which the hash chain can't see. */
    @Test
    void shouldDetectARewrittenDigest() {
        givenRows(receipt("f".repeat(64), "c2ln", SIGNED_AT, PREFIX));
        givenStoredObject(storedFields);
        givenListed(PREFIX);

        reconcileExpectingOne(ReceiptReconciler.REASON_DIGEST_MISMATCH);
    }

    @Test
    void shouldDetectAMissingS3Object() {
        givenRows(consistentReceipt());
        when(receiptStore.readJsonObject(BUCKET, PREFIX)).thenReturn(Optional.empty());
        givenListed();

        reconcileExpectingOne(ReceiptReconciler.REASON_OBJECT_MISSING);
    }

    @Test
    void shouldDetectAnUnparseableS3Object() {
        givenRows(consistentReceipt());
        when(receiptStore.readJsonObject(BUCKET, PREFIX))
                .thenReturn(Optional.of("not json".getBytes(StandardCharsets.UTF_8)));
        givenListed(PREFIX);

        reconcileExpectingOne(ReceiptReconciler.REASON_OBJECT_UNPARSEABLE);
    }

    @Test
    void shouldDetectAReceiptUuidMismatch() {
        givenRows(consistentReceipt());
        storedFields.put("receiptUuid", UUID.randomUUID().toString());
        givenStoredObject(storedFields);
        givenListed(PREFIX);

        reconcileExpectingOne(ReceiptReconciler.REASON_RECEIPT_UUID_MISMATCH);
    }

    @Test
    void shouldDetectARewrittenSignature() {
        givenRows(receipt(digest, "Zm9yZ2Vk", SIGNED_AT, PREFIX));
        givenStoredObject(storedFields);
        givenListed(PREFIX);

        reconcileExpectingOne(ReceiptReconciler.REASON_SIGNATURE_MISMATCH);
    }

    @Test
    void shouldDetectARewrittenSignedAt() {
        givenRows(receipt(digest, "c2ln", SIGNED_AT.plusSeconds(1), PREFIX));
        givenStoredObject(storedFields);
        givenListed(PREFIX);

        reconcileExpectingOne(ReceiptReconciler.REASON_SIGNATURE_MISMATCH);
    }

    @Test
    void shouldDetectAMissingLedgerEntry() {
        givenRows(consistentReceipt());
        givenStoredObject(storedFields);
        givenLedgerPayload(null);
        givenListed(PREFIX);

        reconcileExpectingOne(ReceiptReconciler.REASON_LEDGER_MISSING);
    }

    /** An owner who rewrites a ledger payload and recomputes every entry_hash keeps
     * LedgerVerifier at Intact; the S3 original still catches it. */
    @Test
    void shouldDetectARewrittenLedgerPayload() {
        givenRows(consistentReceipt());
        givenStoredObject(storedFields);
        ledgerPayload.put("amount", "1");
        givenLedgerPayload(ledgerPayload);
        givenListed(PREFIX);

        reconcileExpectingOne(ReceiptReconciler.REASON_LEDGER_PAYLOAD_MISMATCH);
    }

    @Test
    void shouldDetectARewrittenLedgerLocation() {
        givenRows(consistentReceipt());
        givenStoredObject(storedFields);
        ledgerPayload.put("s3KeyPrefix", "receipts/elsewhere");
        givenLedgerPayload(ledgerPayload);
        givenListed(PREFIX);

        reconcileExpectingOne(ReceiptReconciler.REASON_LEDGER_LOCATION_MISMATCH);
    }

    /** Deleting a receipt row and its ledger entry leaves the locked original behind. */
    @Test
    void shouldDetectAnS3ObjectWithNoReceiptsRow() {
        givenRows();
        givenListed(PREFIX);

        ReceiptReconciliationResult result = reconciler.reconcile();

        assertThat(result.receiptsChecked()).isZero();
        assertThat(result.discrepancies())
                .containsExactly(new Discrepancy(null, PREFIX, ReceiptReconciler.REASON_ORPHAN_OBJECT));
    }

    @Test
    void shouldReportAReceiptThatCannotBeCheckedWithoutAbortingTheRun() {
        Receipt failing = consistentReceipt();
        givenRows(failing);
        when(receiptStore.readJsonObject(BUCKET, PREFIX))
                .thenThrow(new ReceiptStorageException("S3 down", new RuntimeException()));
        givenListed(PREFIX);

        ReceiptReconciliationResult result = reconciler.reconcile();

        assertThat(result.discrepancies()).singleElement()
                .satisfies(d -> assertThat(d.reason()).isEqualTo("receipt could not be checked: S3 down"));
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
