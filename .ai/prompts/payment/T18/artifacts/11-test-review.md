# payment · T18 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T18/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `ReceiptStoreTest.java`, `ReceiptStore.java`.

---

## Executive summary

- `ReceiptStoreTest` is now present with 12 methods and passes.
- Full payment suite: **386/386 passing** (374 baseline + 12 new).
- The manifest is complete and covers every acceptance criterion, the Phase 3 design-challenge findings, and the Phase 8 independent-review findings.
- Remaining gaps are small: no direct test that a PDF-rendering `IOException` wraps in `ReceiptStorageException`, no verification of `contentType` on the `PutObjectRequest`s, and no `copyObject` overwrite guard.

---

## Test manifest verification

### `ReceiptStoreTest` (12 methods)

| Manifest test | Present | Verifies |
|---|---|---|
| `shouldStoreReceiptInS3ObjectLockWorm` | ✅ | AC1/AC2 — two `putObject` calls, JSON first then PDF, correct bucket, `COMPLIANCE` mode |
| `shouldEmbedReceiptUuidInBothS3Keys` | ✅ | AC1/Finding 1 — prefix, JSON key, PDF key, bucket in `ReceiptStorageLocation` |
| `shouldNormalizePrefixWithoutTrailingSlash` | ✅ | Phase 9 `normalizedPrefix` fix |
| `shouldSetRetentionDateToSevenYearsFromTheInjectedClock` | ✅ | AC2 — retention date on both requests |
| `shouldWrapSdkExceptionFromJsonPutAsReceiptStorageException` | ✅ | AC4/Finding 2 — JSON failure wraps and stops before PDF |
| `shouldWrapSdkExceptionFromPdfPutAsReceiptStorageException` | ✅ | AC4 — PDF failure wraps after JSON succeeded |
| `shouldProduceTextExtractablePdfContainingReceiptUuidAndTxHash` | ✅ | AC7 — real PDFBox round-trip with `PDFTextStripper` |
| `shouldIncludeMatchingObjectMetadataOnBothPutRequests` | ✅ | Finding 2 — matching metadata on both objects |
| `shouldSerializeStoredJsonAsCanonicalFifteenFieldPayload` | ✅ | AC5/Finding 3 — exact canonical JSON bytes |
| `shouldNeverCallDeleteOrAnyOverwriteApi` | ✅ | AC3 — `deleteObject` never called |
| `shouldRejectNullPayload` | ✅ | Null-handling boundary |
| `shouldRejectNullSigned` | ✅ | Null-handling boundary |

---

## Gap 1 (Low) — No direct test for PDF-rendering `IOException` wrapping

- **Why it matters:** The Phase 7/8 fix unified PDF-rendering `IOException` and S3 `SdkException` under `ReceiptStorageException`. The current tests only exercise the S3 failure paths.
- **Suggested test:** Force a rendering failure by passing a `ReceiptPayload` whose `receiptUuid()` is `null`? That is blocked by the record's compact constructor. A cleaner approach is to extract `renderPdf` to a package-visible method or use a test-specific subclass, but that is intrusive. Alternatively, rely on the existing code path as acceptable since `PDDocument`/PDFBox failures are rare and the `catch` block is trivial.
- **Confidence:** Low.

---

## Gap 2 (Low) — `contentType` is not asserted

- **Why it matters:** AC1 says both JSON and PDF are written. The test verifies by key suffix, but a misconfigured `PutObjectRequest` could omit or mis-set `contentType` without failing any assertion.
- **Suggested test:** Add assertions in `shouldStoreReceiptInS3ObjectLockWorm` that the first request has `contentType("application/json")` and the second has `contentType("application/pdf")`.
- **Confidence:** Low.

---

## Gap 3 (Low) — `copyObject` overwrite path is not explicitly forbidden

- **Why it matters:** AC3 says "no delete/overwrite S3 API call exists anywhere in `ReceiptStore`'s own code." The test verifies `deleteObject` is never called, but `copyObject` (which can overwrite metadata or object content) is also an overwrite API.
- **Suggested test:** Add `verify(s3Client, never()).copyObject(any())` to `shouldNeverCallDeleteOrAnyOverwriteApi`, or rename the test and broaden the verification.
- **Confidence:** Low.

---

## Gap 4 (Low) — Empty-prefix edge case is not tested

- **Why it matters:** `normalizedPrefix("receipts")` is tested, but `normalizedPrefix("")` returns `"/"`, producing keys like `"/<receiptUuid>.json"`. This is probably unintended if an operator explicitly wants root-level keys.
- **Suggested test:** Decide whether an empty prefix should stay empty or become `"/"`, then add a test and possibly adjust `normalizedPrefix`. Given the local default is `receipts/`, this is a pathological config case.
- **Confidence:** Low.

---

## Positive confirmations

- The 12-method manifest fully satisfies the brief's required tests.
- The exact canonical JSON bytes are compared against an independently-built expected map, not just parsed.
- Mutation verification was performed for `normalizedPrefix` and the canonical JSON field mapping.
- The PDF test uses `Loader.loadPDF` and `PDFTextStripper`, proving the bytes are a real PDF containing the expected text.
- Both S3 failure paths verify the exception type, message, and cause.
- Strict Mockito stubbing is respected by keeping the happy-path stub in a per-test helper rather than a blanket `@BeforeEach`.
- The S3 failure tests prove the JSON-first ordering: a JSON failure stops before the PDF write; a PDF failure occurs after the JSON write.
- Full suite passes: **386/386**.

---

## Build context

- `ReceiptStoreTest`: **12/12 passing**
- Full `mvn -pl services/payment -am test`: **386/386 passing**

---

## Recommended follow-up order

1. Optionally add `contentType` assertions to `shouldStoreReceiptInS3ObjectLockWorm` (Gap 2).
2. Optionally broaden `shouldNeverCallDeleteOrAnyOverwriteApi` to also forbid `copyObject` (Gap 3).
3. Optionally decide on and test the empty-prefix behavior (Gap 4).
4. Optionally add a direct PDF-rendering failure test if a non-intrusive seam can be found (Gap 1).
