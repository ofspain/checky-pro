package com.themistra.payment.receipt;

import com.themistra.payment.common.CanonicalJson;
import com.themistra.payment.common.config.ReceiptProperties;
import com.themistra.payment.common.crypto.AttestationResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T18 (R23, L9). Plain JUnit + Mockito, no Spring context - mirrors {@code CryptoClientTest}'s/
 * {@code ReceiptServiceTest}'s own established pattern for a stateless collaborator wrapper;
 * here the mocked collaborator is {@link S3Client} itself (a plain SDK interface), not a local
 * HTTP server.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptStoreTest {

    private static final UUID RECEIPT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVOICE_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PAYMENT_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant FINALIZED_AT = Instant.parse("2026-07-13T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
    private static final Instant SIGNED_AT = Instant.parse("2026-10-15T00:00:01Z");

    @Mock
    private S3Client s3Client;

    private ReceiptStore store;
    private ReceiptProperties properties;
    private Clock clock;

    @BeforeEach
    void setUp() {
        properties = new ReceiptProperties("my-bucket", "receipts/", "us-east-1", "http://kms");
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        store = new ReceiptStore(s3Client, properties, clock);
    }

    /** Stubs the happy path - not every test needs it (the null-rejection tests throw before
     * any S3 call; the SdkException tests stub their own failure behavior instead), so this is
     * explicit per-test rather than a blanket {@code @BeforeEach} stub Mockito's strict
     * stubbing would otherwise flag as unnecessary on those tests. */
    private void stubSuccessfulPutObject() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }

    private static ReceiptPayload samplePayload() {
        return new ReceiptPayload(RECEIPT_UUID, INVOICE_UUID, PAYMENT_UUID, MERCHANT_ACCOUNT_UUID,
                "ETHEREUM", "0xabc123", "0xdef456", (short) 6, new BigDecimal("1000000"),
                "0x789", "0x012", FINALIZED_AT);
    }

    private static AttestationResult.Signed sampleSigned() {
        return new AttestationResult.Signed("deadbeef".repeat(20), "kms-key-1", SIGNED_AT);
    }

    /** Named test, `package.md` §8. */
    @Test
    void shouldStoreReceiptInS3ObjectLockWorm() {
        stubSuccessfulPutObject();
        store.store(samplePayload(), sampleSigned());

        ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(2)).putObject(requests.capture(), any(RequestBody.class));
        List<PutObjectRequest> all = requests.getAllValues();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).key()).endsWith(".json");
        assertThat(all.get(0).contentType()).isEqualTo("application/json");
        assertThat(all.get(1).key()).endsWith(".pdf");
        assertThat(all.get(1).contentType()).isEqualTo("application/pdf");
        for (PutObjectRequest request : all) {
            assertThat(request.bucket()).isEqualTo("my-bucket");
            assertThat(request.objectLockMode()).isEqualTo(ObjectLockMode.COMPLIANCE);
        }
    }

    @Test
    void shouldEmbedReceiptUuidInBothS3Keys() {
        stubSuccessfulPutObject();
        ReceiptStorageLocation location = store.store(samplePayload(), sampleSigned());

        assertThat(location.s3KeyPrefix()).isEqualTo("receipts/" + RECEIPT_UUID);
        assertThat(location.jsonKey()).isEqualTo("receipts/" + RECEIPT_UUID + ".json");
        assertThat(location.pdfKey()).isEqualTo("receipts/" + RECEIPT_UUID + ".pdf");
        assertThat(location.bucket()).isEqualTo("my-bucket");
    }

    /** Phase 9 (Kimi Phase 8 New Finding 2): a configured prefix missing its own trailing
     * delimiter must not produce a flat, unintended key. */
    @Test
    void shouldNormalizePrefixWithoutTrailingSlash() {
        ReceiptProperties noSlashProperties = new ReceiptProperties("my-bucket", "receipts", "us-east-1", "http://kms");
        ReceiptStore noSlashStore = new ReceiptStore(s3Client, noSlashProperties, clock);

        stubSuccessfulPutObject();
        ReceiptStorageLocation location = noSlashStore.store(samplePayload(), sampleSigned());

        assertThat(location.jsonKey()).isEqualTo("receipts/" + RECEIPT_UUID + ".json");
    }

    @Test
    void shouldSetRetentionDateToSevenYearsFromTheInjectedClock() {
        stubSuccessfulPutObject();
        store.store(samplePayload(), sampleSigned());

        ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(2)).putObject(requests.capture(), any(RequestBody.class));

        Instant expected = NOW.plus(Duration.ofDays(7 * 365L));
        for (PutObjectRequest request : requests.getAllValues()) {
            assertThat(request.objectLockRetainUntilDate()).isEqualTo(expected);
        }
    }

    @Test
    void shouldWrapSdkExceptionFromJsonPutAsReceiptStorageException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkException.create("boom", null));

        assertThatThrownBy(() -> store.store(samplePayload(), sampleSigned()))
                .isInstanceOf(ReceiptStorageException.class)
                .hasMessageContaining("JSON")
                .hasCauseInstanceOf(SdkException.class);

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void shouldWrapSdkExceptionFromPdfPutAsReceiptStorageException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build())
                .thenThrow(SdkException.create("boom", null));

        assertThatThrownBy(() -> store.store(samplePayload(), sampleSigned()))
                .isInstanceOf(ReceiptStorageException.class)
                .hasMessageContaining("PDF")
                .hasCauseInstanceOf(SdkException.class);

        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void shouldProduceTextExtractablePdfContainingReceiptUuidAndTxHash() throws Exception {
        ArgumentCaptor<RequestBody> bodies = ArgumentCaptor.forClass(RequestBody.class);
        stubSuccessfulPutObject();
        store.store(samplePayload(), sampleSigned());
        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), bodies.capture());

        byte[] pdfBytes = bodies.getAllValues().get(1).contentStreamProvider().newStream().readAllBytes();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains(RECEIPT_UUID.toString());
            assertThat(text).contains("0xabc123");
            assertThat(text).contains("deadbeef");
        }
    }

    @Test
    void shouldIncludeMatchingObjectMetadataOnBothPutRequests() {
        ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        stubSuccessfulPutObject();
        store.store(samplePayload(), sampleSigned());
        verify(s3Client, times(2)).putObject(requests.capture(), any(RequestBody.class));

        for (PutObjectRequest request : requests.getAllValues()) {
            assertThat(request.metadata())
                    .containsEntry("receipt-uuid", RECEIPT_UUID.toString())
                    .containsEntry("invoice-uuid", INVOICE_UUID.toString())
                    .containsEntry("payment-uuid", PAYMENT_UUID.toString());
        }
    }

    /** Phase 8, Kimi New Finding 3: an exact-bytes assertion, not just "the JSON parses." */
    @Test
    void shouldSerializeStoredJsonAsCanonicalFifteenFieldPayload() throws Exception {
        ArgumentCaptor<RequestBody> bodies = ArgumentCaptor.forClass(RequestBody.class);
        AttestationResult.Signed signed = sampleSigned();
        stubSuccessfulPutObject();
        store.store(samplePayload(), signed);
        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), bodies.capture());

        byte[] jsonBytes = bodies.getAllValues().get(0).contentStreamProvider().newStream().readAllBytes();
        String actualJson = new String(jsonBytes, StandardCharsets.UTF_8);

        java.util.Map<String, Object> expectedFields = new java.util.LinkedHashMap<>();
        expectedFields.put("receiptUuid", RECEIPT_UUID.toString());
        expectedFields.put("invoiceUuid", INVOICE_UUID.toString());
        expectedFields.put("paymentUuid", PAYMENT_UUID.toString());
        expectedFields.put("merchantAccountUuid", MERCHANT_ACCOUNT_UUID.toString());
        expectedFields.put("chain", "ETHEREUM");
        expectedFields.put("txHash", "0xabc123");
        expectedFields.put("tokenContractAddress", "0xdef456");
        expectedFields.put("tokenDecimals", (short) 6);
        expectedFields.put("amount", "1000000");
        expectedFields.put("payToAddress", "0x789");
        expectedFields.put("fromAddress", "0x012");
        expectedFields.put("finalizedAt", FINALIZED_AT.toString());
        expectedFields.put("signature", signed.signature());
        expectedFields.put("kmsKeyId", signed.kmsKeyId());
        expectedFields.put("signedAt", SIGNED_AT.toString());
        String expectedJson = CanonicalJson.serialize(expectedFields);

        assertThat(actualJson).isEqualTo(expectedJson);
    }

    /** AC3 - a direct, mechanical check rather than relying solely on code inspection. Also
     * forbids {@code copyObject} (Phase 11, Kimi Gap 3) - an S3 copy can overwrite an existing
     * object's content/metadata just as a delete can, so it falls under AC3's "no delete/
     * overwrite" text too, even though {@code ReceiptStore} never calls it today. */
    @Test
    void shouldNeverCallDeleteOrAnyOverwriteApi() {
        stubSuccessfulPutObject();
        store.store(samplePayload(), sampleSigned());

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(s3Client, never()).copyObject(any(software.amazon.awssdk.services.s3.model.CopyObjectRequest.class));
    }

    @Test
    void shouldRejectNullPayload() {
        assertThatThrownBy(() -> store.store(null, sampleSigned()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullSigned() {
        assertThatThrownBy(() -> store.store(samplePayload(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
