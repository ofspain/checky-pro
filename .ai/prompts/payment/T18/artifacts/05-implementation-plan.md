# payment · T18 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

No code in this artifact — planning only.

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStorageLocation.java`
2. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStorageException.java`
3. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java`
4. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStoreConfig.java`

## Files to modify

1. `services/payment/src/main/java/com/themistra/payment/common/config/ReceiptProperties.java`
2. `services/payment/pom.xml`
3. `services/payment/src/main/resources/application.properties`

## Public types/methods (signatures)

**`ReceiptProperties`** (amended, `prefix` added alongside the Phase 2 `region` addition):
```java
@ConfigurationProperties(prefix = "themistra.payment.receipt")
@Validated
public record ReceiptProperties(
        @NotBlank String bucket,
        @NotBlank String prefix,
        @NotBlank String region,
        @NotBlank String kmsPublicKeysUrl) {}
```

**`ReceiptStorageLocation`** (record, `receipt` package):
```java
public record ReceiptStorageLocation(String bucket, String s3KeyPrefix, String jsonKey, String pdfKey) {}
```

**`ReceiptStorageException`** (unchecked, `receipt` package, mirrors `CryptoServiceUnavailableException`'s shape):
```java
public class ReceiptStorageException extends RuntimeException {
    public ReceiptStorageException(String message, Throwable cause) { super(message, cause); }
}
```

**`ReceiptStoreConfig`** (new `@Configuration`, mirrors `ObservationSnapshotStoreConfig` exactly, widened timeout per Finding 5):
```java
@Configuration
public class ReceiptStoreConfig {
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public S3Client s3Client(ReceiptProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .build())
                .build();
    }

    @Bean
    public ReceiptStore receiptStore(S3Client s3Client, ReceiptProperties properties, Clock clock) {
        return new ReceiptStore(s3Client, properties, clock);
    }
}
```

**`ReceiptStore`** (new, stateless, `receipt` package):
```java
public ReceiptStore(S3Client s3Client, ReceiptProperties properties, Clock clock)

public ReceiptStorageLocation store(ReceiptPayload payload, AttestationResult.Signed signed)
```

## Private methods

- `ReceiptStore`: `private Map<String, Object> toCanonicalMap(ReceiptPayload payload, AttestationResult.Signed signed)`
  — the 15-field map (the same 12 conversions `ReceiptDigest.toCanonicalMap` already uses, duplicated
  here rather than reused since that method is `private` to a different class — same "no shared
  mutable helper across classes" shape already established — plus `signature`/`kmsKeyId`/`signedAt`).
- `ReceiptStore`: `private byte[] renderPdf(Map<String, Object> fields)` — PDFBox 3.0.3, one A4 page
  (`PDRectangle.A4`), 1-inch margins, Helvetica 12pt, `receiptUuid` printed first, then one
  label/value line per remaining field in the same map's own key order, `signature` wrapped at 64
  characters/line via `Pattern.compile(".{1,64}")` chunking (no external line-wrap library needed).
- `ReceiptStore`: `private PutObjectRequest buildPutRequest(String key, Instant retainUntil, UUID receiptUuid, UUID invoiceUuid, UUID paymentUuid, String contentType)`
  — shared builder for both the JSON and PDF `putObject` calls (same bucket, Object Lock mode/date,
  metadata map; only the key/content-type/bytes differ between the two calls).
- `ReceiptStore`: `private <T> T putOrThrow(Supplier<T> s3Call, String artifactDescription)` —
  wraps any `SdkException` as `ReceiptStorageException("Failed to store " + artifactDescription + ...", e)`;
  called once per `putObject` invocation (Finding 2's ordering: JSON call wrapped and executed
  first, then the PDF call, each independently wrapped so either failure is attributable to the
  artifact that actually failed).

## Entities used

None — `ReceiptStore` only ever sees `ReceiptPayload` (T16) and `AttestationResult.Signed` (T15),
never a `Payment`/`Invoice` entity (L12, continuity with T16/T17).

## Repositories used

None — this task has no DB interaction at all (confirmed, Phase 0/2).

## Services used

None injected beyond `S3Client`/`Clock`/`ReceiptProperties` — `ReceiptStore` has no caller yet
(Phase 2's own confirmed "built one task ahead of its consumer" shape).

## pom.xml / application.properties changes

```xml
<!-- Apache PDFBox - the single-page receipt PDF rendering (design.md O4, T18) -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
```
```properties
themistra.payment.receipt.prefix=${RECEIPT_BUCKET_PREFIX:receipts/}
themistra.payment.receipt.region=${RECEIPT_BUCKET_REGION:us-east-1}
```

## Unit tests required

All unit, mocked `S3Client` (Mockito — a plain SDK interface, no local HTTP server needed, unlike
`CryptoClientTest`'s WireMock-style precedent), no Spring context, injected fixed `Clock`:

**`ReceiptStoreTest`:**
1. `shouldStoreReceiptInS3ObjectLockWorm` (named) — both `putObject` calls happen (JSON then PDF,
   verified via `InOrder`), each with `objectLockMode(COMPLIANCE)` set and the correct bucket.
2. `shouldEmbedReceiptUuidInBothS3Keys` — `jsonKey`/`pdfKey` both derive from
   `properties.prefix() + receiptUuid` with the correct `.json`/`.pdf` suffix; returned
   `ReceiptStorageLocation.s3KeyPrefix()` has no file extension.
3. `shouldSetRetentionDateToSevenYearsFromTheInjectedClock` — `objectLockRetainUntilDate` equals
   `fixedClock.instant().plus(Duration.ofDays(7 * 365))` for both `putObject` calls.
4. `shouldWrapSdkExceptionFromJsonPutAsReceiptStorageException` — mocked `s3Client.putObject`
   throws a real `SdkException` on the first (JSON) call → `ReceiptStorageException` propagates,
   wrapping it; the PDF `putObject` is never called (JSON-first ordering, Finding 2).
5. `shouldWrapSdkExceptionFromPdfPutAsReceiptStorageException` — JSON call succeeds, PDF call
   throws → `ReceiptStorageException` propagates (the mirror case; documents, does not "fix," the
   partial-write window Finding 2 disclosed — the JSON object is left durably stored).
6. `shouldProduceTextExtractablePdfContainingReceiptUuidAndTxHash` — render the PDF bytes via
   PDFBox's own `Loader.loadPDF(...)` + `PDFTextStripper`, assert the extracted text contains the
   fixture's `receiptUuid` and `txHash` values.
7. `shouldIncludeMatchingObjectMetadataOnBothPutRequests` — both captured `PutObjectRequest`s
   carry the same `receipt-uuid`/`invoice-uuid`/`payment-uuid` metadata values (Finding 2).
8. `shouldSerializeStoredJsonAsCanonicalFifteenFieldPayload` — the captured JSON `RequestBody`
   bytes, parsed back, equal the 12 `ReceiptPayload` fields (in `ReceiptDigest`-identical string
   form) plus `signature`/`kmsKeyId`/`signedAt` — no extra/missing field (AC5).
9. `shouldNeverCallDeleteOrAnyOverwriteApi` — `verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class))`
   (AC3 — a direct, mechanical check rather than relying solely on code inspection).

## Execution order

1. `ReceiptProperties` (add `prefix`) — no dependency.
2. `ReceiptStorageLocation`/`ReceiptStorageException` — no dependency, can be done anytime.
3. `pom.xml` (PDFBox) — must land before `ReceiptStore` compiles.
4. `ReceiptStore` — depends on 1–3, plus the already-existing `ReceiptPayload`/`AttestationResult` (T15/T16).
5. `ReceiptStoreConfig` — depends on 4.
6. `application.properties` — must land before any test boots a Spring context (none of this
   task's own tests do, but the full-suite run at the end of every phase does).
7. All test files (deferred to Phase 10, per the standard rule — this task is not test-only).

## Traceability check

Every file above traces directly to the frozen brief's own "Files to Create"/"Files to Modify"
lists (Phase 4). No file is added that the brief does not authorize.
