package com.themistra.payment.receipt;

import com.themistra.payment.common.CanonicalJson;
import com.themistra.payment.common.config.ReceiptProperties;
import com.themistra.payment.common.crypto.AttestationResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectLockLegalHoldStatus;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * T18 (R23, L9). Writes a signed receipt — both a canonical JSON object and a PDF rendering of
 * the same fields — to the S3 Object Lock bucket in compliance mode. Stateless beyond its
 * injected {@link S3Client}/{@link ReceiptProperties}/{@link Clock}, consistent with every other
 * service class in this codebase.
 *
 * <p><b>No defensive pre-write existence check (Phase 2/3/4, Kimi Finding 9).</b> The no-
 * duplicate-write guarantee is structural, not SDK-enforced: a {@code receiptUuid} is generated
 * fresh exactly once per successful {@code attest()} call (T17's own {@code ReceiptService}),
 * so a second write to the same key cannot occur through correct application code — L9's own
 * text frames this as "a bug, not an update." Note the nuance Kimi's Finding 9 raised: on a
 * versioned Object Lock bucket, a second {@code putObject} to the same key creates a <i>new
 * version</i> rather than being rejected as an overwrite — Object Lock compliance mode prevents
 * deleting or overwriting an existing, already-retained object version, but does not prevent a
 * second version from being created. The guarantee this class relies on is therefore "the
 * caller never does this," not "the platform would reject it if the caller did."</p>
 *
 * <p>Unlike {@code ObservationSnapshotStore}'s (crypto-service) own "S3 is supplementary, never
 * throws" stance, this task's S3 write is the load-bearing operation R23's "SHALL store" text
 * exists to guarantee — any {@link SdkException} propagates wrapped as {@link
 * ReceiptStorageException}, never swallowed (AC4).</p>
 */
@Component
public class ReceiptStore {

    private static final Duration RETENTION_PERIOD = Duration.ofDays(7 * 365L);
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_PDF = "application/pdf";
    /** Declared as {@code int}, not {@code float}/{@code double} - ArchitectureTest's own
     * {@code shouldRejectFloatingPointMoneyTypesAtArchUnit} bans every floating-point field in
     * this package outright (L6), not only money-carrying ones; cast to {@code float} only at
     * the local-variable call sites PDFBox's own API requires. */
    private static final int MARGIN = 72;
    private static final int FONT_SIZE = 12;
    private static final int LEADING = 16;
    private static final int SIGNATURE_WRAP_WIDTH = 64;

    private final S3Client s3Client;
    private final ReceiptProperties properties;
    private final Clock clock;

    public ReceiptStore(S3Client s3Client, ReceiptProperties properties, Clock clock) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Writes the JSON object first, then the PDF (Phase 3/4, Kimi Finding 2 — the canonical,
     * machine-verifiable artifact is the one worth preserving if the second write fails; the
     * partial-write window itself is an accepted, documented operational risk, not solved here
     * — there is no multi-object atomic commit in S3, and both objects carry matching
     * {@code receipt-uuid}/{@code invoice-uuid}/{@code payment-uuid} metadata so an orphaned
     * half-pair can be identified and reconciled by ops).
     */
    public ReceiptStorageLocation store(ReceiptPayload payload, AttestationResult.Signed signed) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(signed, "signed must not be null");

        String s3KeyPrefix = properties.prefix() + payload.receiptUuid();
        String jsonKey = s3KeyPrefix + ".json";
        String pdfKey = s3KeyPrefix + ".pdf";
        Instant retainUntil = clock.instant().plus(RETENTION_PERIOD);

        Map<String, Object> fields = toCanonicalMap(payload, signed);
        byte[] jsonBytes = CanonicalJson.serialize(fields).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] pdfBytes = renderPdf(fields);

        putOrThrow(() -> s3Client.putObject(
                        buildPutRequest(jsonKey, retainUntil, payload, CONTENT_TYPE_JSON),
                        RequestBody.fromBytes(jsonBytes)),
                "the JSON receipt object");
        putOrThrow(() -> s3Client.putObject(
                        buildPutRequest(pdfKey, retainUntil, payload, CONTENT_TYPE_PDF),
                        RequestBody.fromBytes(pdfBytes)),
                "the PDF receipt object");

        return new ReceiptStorageLocation(properties.bucket(), s3KeyPrefix, jsonKey, pdfKey);
    }

    /**
     * The canonical 15-field map (`design.md` O4): the same 12 {@link ReceiptPayload} field
     * conversions {@code ReceiptDigest.toCanonicalMap} already uses ({@code UUID.toString()},
     * {@code BigDecimal.toPlainString()}, bare {@code tokenDecimals}, {@code Instant.toString()}
     * for {@code finalizedAt}) plus {@code signature}/{@code kmsKeyId}/{@code signedAt} (also
     * {@code Instant.toString()}) — duplicated here rather than shared with {@code
     * ReceiptDigest} since that method is {@code private} to a different class (Phase 4/5).
     */
    private Map<String, Object> toCanonicalMap(ReceiptPayload payload, AttestationResult.Signed signed) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("receiptUuid", payload.receiptUuid().toString());
        map.put("invoiceUuid", payload.invoiceUuid().toString());
        map.put("paymentUuid", payload.paymentUuid().toString());
        map.put("merchantAccountUuid", payload.merchantAccountUuid().toString());
        map.put("chain", payload.chain());
        map.put("txHash", payload.txHash());
        map.put("tokenContractAddress", payload.tokenContractAddress());
        map.put("tokenDecimals", payload.tokenDecimals());
        map.put("amount", payload.amount().toPlainString());
        map.put("payToAddress", payload.payToAddress());
        map.put("fromAddress", payload.fromAddress());
        map.put("finalizedAt", payload.finalizedAt().toString());
        map.put("signature", signed.signature());
        map.put("kmsKeyId", signed.kmsKeyId());
        map.put("signedAt", signed.signedAt().toString());
        return map;
    }

    private PutObjectRequest buildPutRequest(String key, Instant retainUntil, ReceiptPayload payload,
            String contentType) {
        return PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .objectLockMode(ObjectLockMode.COMPLIANCE)
                .objectLockRetainUntilDate(retainUntil)
                .objectLockLegalHoldStatus(ObjectLockLegalHoldStatus.OFF)
                .metadata(Map.of(
                        "receipt-uuid", payload.receiptUuid().toString(),
                        "invoice-uuid", payload.invoiceUuid().toString(),
                        "payment-uuid", payload.paymentUuid().toString()))
                .build();
    }

    private <T> T putOrThrow(Supplier<T> s3Call, String artifactDescription) {
        try {
            return s3Call.get();
        } catch (SdkException e) {
            throw new ReceiptStorageException("Failed to store " + artifactDescription, e);
        }
    }

    /**
     * One A4 page, 1-inch margins, Helvetica 12pt (Phase 3/4, Kimi Finding 4). {@code
     * receiptUuid} is printed first; every other field follows as a label/value line in the
     * same map order {@link #toCanonicalMap} built; {@code signature} is wrapped at {@value
     * #SIGNATURE_WRAP_WIDTH} characters/line so it never overflows the page width.
     */
    private byte[] renderPdf(Map<String, Object> fields) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float y = page.getMediaBox().getHeight() - MARGIN;

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(font, FONT_SIZE);
                content.beginText();
                content.newLineAtOffset(MARGIN, y);

                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    if ("signature".equals(entry.getKey())) {
                        content.showText("signature:");
                        content.newLineAtOffset(0, -LEADING);
                        for (String chunk : wrapSignature(String.valueOf(entry.getValue()))) {
                            content.showText("  " + chunk);
                            content.newLineAtOffset(0, -LEADING);
                        }
                    } else {
                        content.showText(entry.getKey() + ": " + entry.getValue());
                        content.newLineAtOffset(0, -LEADING);
                    }
                }

                content.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render receipt PDF", e);
        }
    }

    private static java.util.List<String> wrapSignature(String signature) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < signature.length(); i += SIGNATURE_WRAP_WIDTH) {
            lines.add(signature.substring(i, Math.min(i + SIGNATURE_WRAP_WIDTH, signature.length())));
        }
        return lines;
    }
}
