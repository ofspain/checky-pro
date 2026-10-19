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
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * T18 (R23, L9). Writes a signed receipt — both a canonical JSON object and a PDF rendering of
 * the same fields — to the S3 Object Lock bucket in compliance mode. Stateless beyond its
 * injected {@link S3Client}/{@link ReceiptProperties}/{@link Clock}, consistent with every other
 * service class in this codebase.
 *
 * <p><b>S3 itself refuses a second write to the same key (threat #5 follow-up FU-T28-1).</b>
 * Every put carries {@code If-None-Match: *}, a conditional write: S3 accepts it only when no
 * object exists at that key, and otherwise answers {@code 412 Precondition Failed}. On a
 * versioned Object Lock bucket an unconditional second put would not overwrite the locked
 * original, but it would add a new <i>current</i> version, so a reader fetching the key would see
 * different content. The conditional put closes that: the first write is the only write, as L9
 * says ("a second write to the same key is a bug, not an update"). A {@code receiptUuid} is still
 * generated fresh per {@code attest()} call, so the refusal should never fire through correct
 * code; when it does, {@link #store} reports it as a {@link ReceiptStorageException} saying the
 * object already exists.</p>
 *
 * <p><b>Reads exist only for the integrity check.</b> {@link #readJsonObject} and
 * {@link #listStoredReceiptPrefixes} let {@code ReceiptReconciler} compare the database against
 * the locked originals (FU-T28-3). This class never deletes, copies or re-configures an object;
 * {@code ArchitectureTest.shouldConfineS3SdkToReceiptStoreCreateAndReadOnly} enforces that.</p>
 *
 * <p>Unlike {@code ObservationSnapshotStore}'s (crypto-service) own "S3 is supplementary, never
 * throws" stance, this task's S3 write is the load-bearing operation R23's "SHALL store" text
 * exists to guarantee — any {@link SdkException} propagates wrapped as {@link
 * ReceiptStorageException}, never swallowed (AC4).</p>
 *
 * <p>No {@code @Component}/{@code @Service} annotation (Phase 7, self-review) — constructed
 * only via {@code ReceiptStoreConfig}'s own explicit {@code @Bean} factory method, mirroring
 * {@code ObservationSnapshotStore}'s own identical precedent (crypto-service) exactly. An
 * earlier draft of this class carried {@code @Component} as well, which silently registered
 * two {@code ReceiptStore} bean definitions under the same name — Spring's own component-scan-
 * vs-{@code @Configuration}-class override resolution happened to pick one without error since
 * both construct the bean identically, but relying on that undocumented resolution order was
 * never the intent.</p>
 */
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
    private static final int HTTP_PRECONDITION_FAILED = 412;

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

        String s3KeyPrefix = normalizedPrefix(properties.prefix()) + payload.receiptUuid();
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
     *
     * <p><b>Widened from {@code private} to {@code public static} (T19, Phase 3/4 Kimi Finding
     * 12).</b> {@code receipt.ledger.LedgerService} needs this exact same 15-field map (plus two
     * more fields it adds itself) for the ledger entry's own JSONB payload — reusing it here
     * avoids a second, independently-written canonicalizer (`design.md` O4's own explicit
     * "one shared canonicalizer" intent). {@code receipt.ledger} is a genuinely different Java
     * package from {@code receipt} (package-private visibility does not span sub-packages), so
     * nothing short of {@code public} would have let the caller in. {@code static} because the
     * method never touches instance state — confirmed by inspection, not merely convenient.</p>
     *
     * <p><b>Returns an unmodifiable map (Phase 8, Kimi Finding 7).</b> A public method returning
     * a mutable {@code LinkedHashMap} invited an easy mistake for any future caller assuming it
     * was already a safe, read-only canonical representation and mutating it directly before
     * serialization, silently changing the resulting hash. Callers that need to add fields (e.g.
     * {@code ReceiptService.buildLedgerPayload}) must copy into their own new map first.</p>
     */
    public static Map<String, Object> toCanonicalMap(ReceiptPayload payload, AttestationResult.Signed signed) {
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
        return java.util.Collections.unmodifiableMap(map);
    }

    private PutObjectRequest buildPutRequest(String key, Instant retainUntil, ReceiptPayload payload,
            String contentType) {
        return PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .ifNoneMatch("*")
                .objectLockMode(ObjectLockMode.COMPLIANCE)
                .objectLockRetainUntilDate(retainUntil)
                .metadata(Map.of(
                        "receipt-uuid", payload.receiptUuid().toString(),
                        "invoice-uuid", payload.invoiceUuid().toString(),
                        "payment-uuid", payload.paymentUuid().toString()))
                .build();
    }

    private <T> T putOrThrow(Supplier<T> s3Call, String artifactDescription) {
        try {
            return s3Call.get();
        } catch (S3Exception e) {
            if (e.statusCode() == HTTP_PRECONDITION_FAILED) {
                throw new ReceiptStorageException("Refused to store " + artifactDescription
                        + ": an object already exists at that key, and receipts are written exactly once (L9)", e);
            }
            throw new ReceiptStorageException("Failed to store " + artifactDescription, e);
        } catch (SdkException e) {
            throw new ReceiptStorageException("Failed to store " + artifactDescription, e);
        }
    }

    /**
     * Reads the canonical JSON object for one receipt, for the integrity check. Returns empty when
     * no object exists at {@code s3KeyPrefix + ".json"}; any other S3 failure is wrapped as
     * {@link ReceiptStorageException}.
     */
    public Optional<byte[]> readJsonObject(String bucket, String s3KeyPrefix) {
        Objects.requireNonNull(bucket, "bucket must not be null");
        Objects.requireNonNull(s3KeyPrefix, "s3KeyPrefix must not be null");
        try {
            return Optional.of(s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(s3KeyPrefix + ".json").build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw new ReceiptStorageException("Failed to read receipt object " + s3KeyPrefix + ".json", e);
        }
    }

    /**
     * Lists every stored receipt in the configured bucket and prefix, as the key prefix the
     * {@code receipts.s3_key} column holds (the {@code .json} key without its extension). Follows
     * continuation tokens, so it returns the full set, not only the first page.
     */
    public List<String> listStoredReceiptPrefixes() {
        List<String> prefixes = new ArrayList<>();
        String continuationToken = null;
        try {
            do {
                ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(properties.bucket())
                        .prefix(normalizedPrefix(properties.prefix()))
                        .continuationToken(continuationToken)
                        .build());
                for (S3Object object : page.contents()) {
                    if (object.key().endsWith(".json")) {
                        prefixes.add(object.key().substring(0, object.key().length() - ".json".length()));
                    }
                }
                continuationToken = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
            } while (continuationToken != null);
        } catch (SdkException e) {
            throw new ReceiptStorageException("Failed to list stored receipt objects", e);
        }
        return prefixes;
    }

    /**
     * One A4 page, 1-inch margins, Helvetica 12pt (Phase 3/4, Kimi Finding 4). {@code
     * receiptUuid} is printed first; every other field follows as a label/value line in the
     * same map order {@link #toCanonicalMap} built; {@code signature} is wrapped at {@value
     * #SIGNATURE_WRAP_WIDTH} characters/line so it never overflows the page width.
     *
     * <p>A failure here is wrapped as {@link ReceiptStorageException} (Phase 7, self-review),
     * the same type an S3 failure is wrapped as — not a separate {@code UncheckedIOException} —
     * so a caller has exactly one exception type to catch for "this receipt was not durably
     * stored," regardless of which step actually failed.</p>
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
            throw new ReceiptStorageException("Failed to render the PDF receipt object", e);
        }
    }

    /**
     * Guarantees the returned prefix ends with {@code /} (Phase 8, Kimi New Finding 2) — a
     * misconfigured {@code themistra.payment.receipt.prefix} without a trailing delimiter would
     * otherwise silently produce a flat key like {@code receipts11111111-...-.json} instead of
     * the intended {@code receipts/11111111-...-.json}. {@code ObservationSnapshotStore}'s own
     * sibling precedent (crypto-service) has this identical gap, unfixed there; normalized here
     * rather than left matching that precedent, since the fix is cheap and purely defensive.
     */
    private static String normalizedPrefix(String prefix) {
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private static java.util.List<String> wrapSignature(String signature) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < signature.length(); i += SIGNATURE_WRAP_WIDTH) {
            lines.add(signature.substring(i, Math.min(i + SIGNATURE_WRAP_WIDTH, signature.length())));
        }
        return lines;
    }
}
