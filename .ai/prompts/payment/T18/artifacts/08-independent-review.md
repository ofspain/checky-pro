# payment · T18 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T18/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `ReceiptStore.java`, `ReceiptStorageException.java`, `ReceiptStorageLocation.java`, `ReceiptStoreConfig.java`, `ReceiptProperties.java`, `application.properties`, `services/payment/pom.xml`, plus full-suite test run.

---

## Executive summary

- T18 implementation compiles and the full suite is **374/374 green**.
- The self-review's two findings were correctly fixed: `ReceiptStore` is no longer registered as both a `@Component` and a `@Bean`, and PDF-rendering `IOException`s now wrap in `ReceiptStorageException` just like S3 failures.
- The design-challenge recommendations were adopted: JSON-first write order, canonical 15-field JSON, PDFBox 3 A4/Helvetica rendering, 10s S3 timeout, `prefix`/`region` properties, and `ReceiptStorageLocation` carrying a common `s3KeyPrefix`.
- The single largest gap is **test coverage**: there are no tests for `ReceiptStore` at all. The brief's required tests (mocked `S3Client`, JSON/PDF artifact verification, retention-date check, exception wrapping, PDF text extraction) are not present.

---

## Confirming self-review Finding 1 (Medium) — duplicate `ReceiptStore` bean registration

- **Independent assessment:** Confirmed fixed. `ReceiptStore` no longer has `@Component`/`@Service`; it is constructed only by `ReceiptStoreConfig.receiptStore(...)`. The class Javadoc documents the reasoning and references the sibling `ObservationSnapshotStore` precedent.
- **Impact:** Eliminates reliance on undocumented Spring component-scan override resolution.
- **Confidence:** High.

---

## Confirming self-review Finding 2 (Low) — PDF rendering used a different exception type

- **Independent assessment:** Confirmed fixed. `renderPdf`'s `catch (IOException e)` now throws `new ReceiptStorageException("Failed to render the PDF receipt object", e)`. `ReceiptStorageException`'s Javadoc is widened accordingly.
- **Impact:** A future caller has exactly one exception type to catch for the whole `store()` contract.
- **Confidence:** High.

---

## Confirming self-review Finding 3 (Low) — long non-signature fields can overflow the PDF page

- **Independent assessment:** Confirmed and accepted. `renderPdf` only wraps the `signature` field; every other field is emitted as a single `showText` line. In practice all upstream values (UUIDs, bounded chain enum, hex hashes/addresses) are short enough for an A4 page with 1-inch margins and 12pt Helvetica.
- **Impact:** Low — currently unreachable with well-formed inputs; documented as an accepted trust-the-caller stance.
- **Confidence:** Low.

---

## New Finding 1 (High) — No tests for `ReceiptStore`

- **Issue:** Despite the brief's required-test list, there is no `ReceiptStoreTest`. A `grep` for `ReceiptStore`, `ReceiptStorageLocation`, `ReceiptStorageException`, `pdfbox`, or `PDFTextStripper` in `src/test/java` returns no matches.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/receipt/` contains only `ReceiptDigestTest` and `ReceiptServiceTest`.
  - Full suite count went from 372 to 374 because `ReceiptServiceTest` grew from 14 to 16 methods, not because any T18 tests were added.
- **Impact:** The core T18 behavior — S3 Object Lock parameters, key prefix/embed of `receiptUuid`, 7-year retention, `ReceiptStorageException` wrapping, PDF byte content, and the JSON-first ordering — is unverified. The full suite passing only proves the code compiles and does not break existing tests.
- **Recommendation:** Add the tests listed in the brief:
  - Mock `S3Client` and verify two `putObject` calls with correct bucket, keys (`<prefix><receiptUuid>.json`/`.pdf`), content types, Object Lock mode, and retention date.
  - Verify the retention date is ~7 years after the fixed `Clock` instant.
  - Verify an `SdkException` from either `putObject` propagates as `ReceiptStorageException` and that no further S3 calls are attempted after the first failure (for the JSON-first ordering).
  - Verify `renderPdf` produces non-empty bytes containing extractable text (use `PDFTextStripper`).
  - Verify `store(...)` returns a `ReceiptStorageLocation` with the expected bucket, prefix, jsonKey, and pdfKey.
- **Confidence:** High.

---

## New Finding 2 (Medium) — `properties.prefix()` is not validated to end with `/`

- **Issue:** `ReceiptStore.store(...)` builds keys as `properties.prefix() + payload.receiptUuid() + ".json"`. If an operator configures `prefix=receipts` (without trailing slash), the resulting key is `receipts<uuid>.json` rather than `receipts/<uuid>.json`.
- **Impact:** Misconfigured prefix silently produces flat keys like `receipts11111111-...-.json` instead of a directory-like prefix. S3 still accepts it, but lifecycle/organization expectations are broken.
- **Recommendation:** Either (a) normalize the prefix in `ReceiptStore` by adding a `/` if absent, or (b) validate in `ReceiptProperties`/`ReceiptStoreConfig` that `prefix` ends with `/` (or is empty). Normalizing is friendlier and matches the spirit of the local default `receipts/`.
- **Confidence:** Low.

---

## New Finding 3 (Low) — No test verifies the exact JSON bytes stored in S3

- **Issue:** The implementation builds a 15-field canonical map and serializes it with `CanonicalJson`. There is no test that captures the `RequestBody` bytes and asserts they equal the expected canonical JSON string.
- **Impact:** A regression in field mapping (e.g., swapping `amount`/`tokenDecimals` or omitting a signature field) would not be caught.
- **Recommendation:** In the mocked `S3Client` test, capture the JSON `RequestBody` bytes, deserialize or compare against a known canonical string, and assert all 15 fields are present.
- **Confidence:** Low.

---

## New Finding 4 (Low) — The integration test does not exercise `ReceiptStore` end-to-end

- **Issue:** `ChainEventConsumerIdempotencyAndOrderingIntegrationTest` drives payments to `FINALIZED`/`PAID` but `CryptoClient` is mocked and not stubbed for `attest(...)`, so `ReceiptService` returns `Failed` and `ReceiptStore` is never called. No integration test writes a real receipt to S3.
- **Impact:** The wiring between `ChainEventConsumer → ReceiptService → ReceiptStore → S3` is only proven at compile time and by code inspection.
- **Recommendation:** This is acceptable for Phase 8 if adding a real S3 fixture is out of scope, but the test gap should be closed in a future phase (e.g., a LocalStack-based integration test) before T19 wires the store into the main flow.
- **Confidence:** Low.

---

## New Finding 5 (Low) — `ObjectLockLegalHoldStatus.OFF` is set explicitly

- **Issue:** `buildPutRequest` sets `.objectLockLegalHoldStatus(ObjectLockLegalHoldStatus.OFF)`. The brief/AC only requires `objectLockMode` and `objectLockRetainUntilDate`.
- **Impact:** This is harmless and explicit, but it adds an extra SDK field that is not required by the spec. It could be omitted with no behavioral change.
- **Recommendation:** Optional: remove the explicit `OFF` setting to keep the request minimal, or document why it is present. Not a defect.
- **Confidence:** Low.

---

## Positive confirmations

- `ReceiptStore` lives in `receipt/`, imports no `Payment`/`Invoice` entities, and takes `ReceiptPayload`/`AttestationResult.Signed` as inputs (L12).
- `ReceiptStorageLocation` correctly exposes `s3KeyPrefix`, `jsonKey`, and `pdfKey`, solving the singular `receipts.s3_key` schema problem from Phase 3 Finding 1.
- JSON is written before PDF, with a documented partial-write operational risk.
- Both S3 objects carry identical `receipt-uuid`/`invoice-uuid`/`payment-uuid` metadata for ops reconciliation.
- The stored JSON is a canonical 15-field object reusing `CanonicalJson.serialize(...)` from T16.
- `ReceiptStoreConfig` constructs the `S3Client` with a 10s timeout and no hard-coded credentials (L13), mirroring the sibling `ObservationSnapshotStoreConfig` precedent.
- `ReceiptProperties` now includes `prefix` and `region`, both `@NotBlank`, with local fake defaults in `application.properties`.
- `ReceiptStorageException` is unchecked and wraps both `SdkException` and PDF-rendering `IOException`.
- No `deleteObject` or overwrite S3 API call exists in `ReceiptStore` (AC3).
- Apache PDFBox 3.0.3 is pinned in `pom.xml`.
- Full suite passes: **374/374**.

---

## Build context

- `mvn -pl services/payment -am test`: **374/374 passing**.
- No `ReceiptStoreTest` exists yet.

---

## Recommended follow-up order

1. **Phase 10:** add `ReceiptStoreTest` covering the brief's required tests (Finding 1).
2. **Phase 9 (optional):** normalize or validate `properties.prefix()` trailing slash (Finding 2).
3. **Phase 10 (optional):** capture and assert exact canonical JSON bytes in `ReceiptStoreTest` (Finding 3).
4. **Future phase:** add a LocalStack-based integration test that exercises the full `ReceiptService → ReceiptStore → S3` path (Finding 4).
