# payment · T18 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

No production code changed this phase.

## Test manifest (`ReceiptStoreTest`, new class — 12 methods)

| Test | Verifies |
|---|---|
| `shouldStoreReceiptInS3ObjectLockWorm` (named) | AC1/AC2 — both `putObject` calls happen, JSON then PDF (by key suffix order), correct bucket, `objectLockMode(COMPLIANCE)` |
| `shouldEmbedReceiptUuidInBothS3Keys` | AC1/Finding 1 — `ReceiptStorageLocation`'s `bucket`/`s3KeyPrefix`/`jsonKey`/`pdfKey` all derive correctly from `receiptUuid` |
| `shouldNormalizePrefixWithoutTrailingSlash` | Phase 9's own `normalizedPrefix` fix (Kimi Phase 8 New Finding 2) — mutation-verified this phase (see below) |
| `shouldSetRetentionDateToSevenYearsFromTheInjectedClock` | AC2 — `objectLockRetainUntilDate` equals the fixed `Clock` instant plus exactly `7 * 365` days on both requests |
| `shouldWrapSdkExceptionFromJsonPutAsReceiptStorageException` | AC4, Finding 2 ordering — a JSON-write failure wraps correctly and the PDF `putObject` is never attempted |
| `shouldWrapSdkExceptionFromPdfPutAsReceiptStorageException` | AC4 — the mirror case; the JSON write already succeeded by the time the PDF write fails |
| `shouldProduceTextExtractablePdfContainingReceiptUuidAndTxHash` | AC7 — a genuine PDFBox round-trip (`Loader.loadPDF` + `PDFTextStripper`), not a byte-length check |
| `shouldIncludeMatchingObjectMetadataOnBothPutRequests` | Finding 2 — both objects carry identical `receipt-uuid`/`invoice-uuid`/`payment-uuid` metadata |
| `shouldSerializeStoredJsonAsCanonicalFifteenFieldPayload` | AC5, Kimi Phase 8 New Finding 3 — an exact-bytes comparison against an independently-built expected canonical JSON, not just "the JSON parses"; mutation-verified this phase (see below) |
| `shouldNeverCallDeleteOrAnyOverwriteApi` | AC3 — a direct `verify(..., never())` on `deleteObject`, not reliance on code inspection alone |
| `shouldRejectNullPayload` / `shouldRejectNullSigned` | Null-handling boundary (constructor-style `Objects.requireNonNull` guards) |

## Mutation verification performed this phase

- **`normalizedPrefix`**: temporarily changed it to return the prefix unmodified (reverting the
  Phase 9 fix) — `shouldNormalizePrefixWithoutTrailingSlash` failed red, as expected. Reverted;
  confirmed `git diff` on `ReceiptStore.java` was empty before proceeding.
- **Canonical JSON field mapping**: temporarily appended an extra character to the `amount`
  field's value inside `toCanonicalMap` — `shouldSerializeStoredJsonAsCanonicalFifteenFieldPayload`
  failed red, as expected (no other existing test in this class would have caught a field-value
  regression; the full suite's other 373 tests are unrelated to this class). Reverted; confirmed
  `git diff` was empty again, then reran the full suite to confirm a clean baseline.

## Why Mockito's strict stubbing needed `stubSuccessfulPutObject()` as an explicit per-test call

The happy-path `when(s3Client.putObject(...)).thenReturn(...)` stub cannot live in `@BeforeEach`
in this class: the two null-rejection tests throw before any S3 call is made, and the two
`SdkException` tests each stub their own failure behavior instead — Mockito's strict-stubbing
`MockitoExtension` correctly flags a blanket `@BeforeEach` stub as unused in those four tests.
Extracted into a small private helper called explicitly by the eight tests that actually need
the happy path, rather than loosening strictness for the whole class.

## Full suite

`mvn -o -pl services/payment -am test` (Docker up — `services/auth/compose.local.yaml`):
**386/386 passing** (374 pre-task baseline + 12 new `ReceiptStoreTest` methods).
