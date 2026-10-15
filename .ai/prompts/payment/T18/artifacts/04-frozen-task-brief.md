# payment · T18 · Phase 4 — Freeze Task Brief

**STATUS: FROZEN** — approved as written via `AskUserQuestion`.

Consumes: `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md`

## Disposition of Phase 3 (Kimi) findings

All 9 findings verified against real source before dispositioning (migration SQL, `design.md`
§O4, `ObservationSnapshotStoreConfig`/`SnapshotProperties`, `CanonicalJson`, `ReceiptDigest`).
None were factually wrong — a first for this pipeline's Phase 3 reviews this session.

| # | Finding | Severity | Disposition | Reason |
|---|---|---|---|---|
| 1 | Singular `receipts.s3_key` can't represent two S3 objects | High | **ACCEPTED** | Verified directly: `V1__payments_baseline.sql` line 79 has exactly one `s3_key VARCHAR(256) NOT NULL` column, no `s3_key_pdf` or similar. `ReceiptStorageLocation` redefined as `(bucket, s3KeyPrefix, jsonKey, pdfKey)`; T19 (unbuilt, out of scope) will store the prefix in `receipts.s3_key`. |
| 2 | Two sequential `putObject` calls create an unrecoverable partial-write window | Medium | **ACCEPTED** | Real and inherent to S3 (no multi-object transaction). Write JSON first (the canonical, machine-verifiable artifact), PDF second; tag both objects with S3 metadata (`receipt-uuid`, `invoice-uuid`, `payment-uuid`) for ops reconciliation; document as an accepted operational risk, not solved by retries (no caller to retry into yet — same disclosed-gap shape as T17's own unretried `attest()` failure). |
| 3 | Stored JSON format unspecified | Medium | **ACCEPTED** | Verified `design.md` §O4 itself already specifies this exactly: "render the same signed fields (plus signature/kmsKeyId/signedAt once attested)" — the brief just didn't spell it out. Stored JSON = the same 12 O4 fields `ReceiptDigest.toCanonicalMap` converts (`UUID.toString()`, `BigDecimal.toPlainString()`, `Instant.toString()`, bare `tokenDecimals`) plus `signature`/`kmsKeyId`/`signedAt` (also `Instant.toString()`), serialized via the same public `CanonicalJson.serialize(Map<String,Object>)` T16 already built — not a new canonicalizer. |
| 4 | PDF rendering details unspecified | Medium | **ACCEPTED, as implementation-level guidance, not spec-mandated** | Verified `design.md` §O4 only says "single-page layout using Apache PDFBox" — no page size/font/layout given anywhere in the spec package, confirmed by grep. Kimi's proposal (PDFBox 3.0.3, A4, Helvetica 12pt, label/value pairs for the same 15 fields, signature wrapped at 64 chars/line, `receiptUuid` prominent, `PDFTextStripper` for test assertions) adopted as the concrete choice — an implementation-phase decision within discretion, not a correction to a spec gap. |
| 5 | No S3 API timeout specified | Low | **ACCEPTED** | Verified `ObservationSnapshotStoreConfig` uses a fixed 5s `apiCallTimeout`. `ReceiptStoreConfig` gets the same `ClientOverrideConfiguration` mechanism, widened to 10s (a load-bearing call that also does local PDF generation work before/after the network call). |
| 6 | `ReceiptProperties` lacks a key prefix | Low | **ACCEPTED** | Verified `SnapshotProperties` (the sibling store's config) has `bucket`/`prefix`/`region`. Add `@NotBlank String prefix` to `ReceiptProperties`, local default `receipts/` in `application.properties`. |
| 7 | `application.properties` needs a local `region` default | Low | **ACCEPTED** | Mirrors the existing local-only fake defaults already present for `bucket`/`kmsPublicKeysUrl`. |
| 8 | `ReceiptStorageException` placement | Low | **ACCEPTED (no change)** | Stays in `receipt/`; no cross-module caller exists yet. |
| 9 | Versioning nuance (Object Lock ≠ prevents new versions) | Low | **ACCEPTED** | Correct AWS behavior — document in `ReceiptStore`'s own class Javadoc that the no-duplicate guarantee is structural (`receiptUuid` generated fresh exactly once per successful `attest()`, T17), not SDK-enforced. |

No finding was rejected.

## Amendments folded into this frozen brief (supersede the Phase 2 TIB where they conflict)

**`ReceiptProperties`** (Finding 6/7):
```java
public record ReceiptProperties(
        @NotBlank String bucket,
        @NotBlank String prefix,
        @NotBlank String region,
        @NotBlank String kmsPublicKeysUrl) {}
```
`application.properties` adds:
```
themistra.payment.receipt.prefix=${RECEIPT_BUCKET_PREFIX:receipts/}
themistra.payment.receipt.region=${RECEIPT_BUCKET_REGION:us-east-1}
```

**`ReceiptStoreConfig`** (mirrors `ObservationSnapshotStoreConfig` exactly, Finding 5 widened timeout):
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

**`ReceiptStorageLocation`** (Finding 1, redefined from the Phase 2 TIB's 3-field sketch):
```java
public record ReceiptStorageLocation(String bucket, String s3KeyPrefix, String jsonKey, String pdfKey) {}
```
Keys: `s3KeyPrefix = properties.prefix() + receiptUuid` (e.g. `receipts/<uuid>`), `jsonKey = s3KeyPrefix + ".json"`, `pdfKey = s3KeyPrefix + ".pdf"`. T19 (unbuilt) stores `s3KeyPrefix` in `receipts.s3_key`; both full keys stay available in-memory to whatever caller needs them in the same call chain.

**`ReceiptStore.store(ReceiptPayload payload, AttestationResult.Signed signed)` → `ReceiptStorageLocation`** (Finding 2/3, explicit ordering and format):
1. Build the 15-field canonical map: the same 12 conversions `ReceiptDigest.toCanonicalMap` already uses (`UUID.toString()`, `BigDecimal.toPlainString()`, bare `tokenDecimals`, `Instant.toString()` for `finalizedAt`) plus `signature` (as-is), `kmsKeyId` (as-is), `signedAt` (`Instant.toString()`). Serialize via `CanonicalJson.serialize(...)`.
2. Render the PDF: PDFBox 3.0.3, one A4 page, 1-inch margins, Helvetica 12pt, `receiptUuid` prominent at the top, label/value pairs for all 15 fields, `signature` wrapped at 64 characters/line so it never overflows the page.
3. `putObject` the JSON first, to `jsonKey`, with `objectLockMode(COMPLIANCE)` and `objectLockRetainUntilDate = clock.instant().plus(Duration.ofDays(7 * 365))`, plus metadata `receipt-uuid`/`invoice-uuid`/`payment-uuid` (Finding 2).
4. `putObject` the PDF second, to `pdfKey`, same Object Lock parameters and metadata.
5. Any `SdkException` from either call propagates wrapped as `ReceiptStorageException` (unchecked, mirrors `CryptoServiceUnavailableException`'s shape: `(String message, Throwable cause)`) — never swallowed (AC4, the deliberate contrast with `ObservationSnapshotStore`).
6. Return `ReceiptStorageLocation(properties.bucket(), s3KeyPrefix, jsonKey, pdfKey)`.

No `headObject` pre-write existence check (Phase 2's own proposal, confirmed by Kimi's Finding 9: the no-duplicate guarantee is structural — `receiptUuid` fresh per successful `attest()` call — document this reasoning directly in `ReceiptStore`'s class Javadoc, including the Finding 9 nuance that Object Lock does not prevent a second object *version*, only deletion/overwrite of an existing one).

**`ReceiptStorageException`** (Finding 8, no change in placement):
```java
public class ReceiptStorageException extends RuntimeException {
    public ReceiptStorageException(String message, Throwable cause) { super(message, cause); }
}
```

## Final scope, files, and acceptance criteria (carried from Phase 2 TIB, as amended above)

**Scope:** unchanged (in: `ReceiptStore`, `S3Client` bean, `region`/`prefix` config, PDFBox dependency; out: `Receipt` row persistence, `ATTESTED` transition, ledger — all T19).

**Files to Create:** `receipt/ReceiptStore.java`, `receipt/ReceiptStorageLocation.java` (redefined, 4 fields), `receipt/ReceiptStorageException.java`, `receipt/ReceiptStoreConfig.java`.

**Files to Modify:** `common/config/ReceiptProperties.java` (add `prefix` **and** `region`, amended from Phase 2's `region`-only sketch), `services/payment/pom.xml` (Apache PDFBox 3.0.3), `application.properties` (two new local-fake defaults).

**Files NOT to Modify:** unchanged from Phase 2 — `ReceiptPayload`/`ReceiptDigest`/`ReceiptIssueResult`/`ReceiptService` (no wiring change, no caller yet), anything under `spec/`.

**Acceptance Criteria** (AC1–AC4 from Phase 2, amended/added):
- **AC1 (amended, Finding 1).** Both a JSON object and a PDF rendering are written to S3, each keyed under a common `receiptUuid`-derived prefix (`jsonKey`/`pdfKey`), returned as `ReceiptStorageLocation`.
- **AC2 (unchanged).** Both `PutObjectRequest`s set `objectLockMode(COMPLIANCE)` and `objectLockRetainUntilDate` 7 years from the injected `Clock`'s instant.
- **AC3 (unchanged).** No delete/overwrite S3 API call exists anywhere in `ReceiptStore`'s code.
- **AC4 (unchanged).** An `SdkException` from either `putObject` call propagates as `ReceiptStorageException`, never swallowed.
- **AC5 (new, Finding 3).** The stored JSON is the canonical 15-field serialization (12 O4 fields + `signature`/`kmsKeyId`/`signedAt`), produced via `CanonicalJson.serialize(...)`, not an ad hoc Jackson default.
- **AC6 (new, Finding 2).** JSON is written before the PDF; both `putObject` calls carry matching `receipt-uuid`/`invoice-uuid`/`payment-uuid` object metadata.
- **AC7 (new, Finding 4).** The PDF is text-extractable (PDFBox `PDFTextStripper` can recover `receiptUuid` and other rendered fields as plain text).

## Open Questions

None remaining.
