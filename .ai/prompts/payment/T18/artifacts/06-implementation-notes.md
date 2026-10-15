# payment · T18 · Phase 6 — Implementation Notes

Consumes: `artifacts/05-implementation-plan.md`

## What changed

**New files:**
- `receipt/ReceiptStorageLocation.java` — record, `(bucket, s3KeyPrefix, jsonKey, pdfKey)`
  (Phase 3 Finding 1).
- `receipt/ReceiptStorageException.java` — unchecked, mirrors `CryptoServiceUnavailableException`'s
  `(String message, Throwable cause)` shape.
- `receipt/ReceiptStoreConfig.java` — the `S3Client`/`ReceiptStore` beans, mirrors
  `ObservationSnapshotStoreConfig` exactly, 10s `apiCallTimeout` (Phase 3 Finding 5, widened from
  that precedent's own 5s).
- `receipt/ReceiptStore.java` — the task's own primary deliverable:
  `store(ReceiptPayload payload, AttestationResult.Signed signed)` → `ReceiptStorageLocation`.
  Writes the canonical 15-field JSON first, then the PDF (Phase 3 Finding 2); wraps any
  `SdkException` from either call as `ReceiptStorageException`, keyed separately per artifact so
  a failure is attributable to the one that actually failed.

**Modified:**
- `common/config/ReceiptProperties.java` — added `prefix` (Phase 3 Finding 6) alongside the
  Phase 2 `region` addition. Both `@NotBlank`.
- `services/payment/pom.xml` — added `org.apache.pdfbox:pdfbox:3.0.3`.
- `services/payment/src/main/resources/application.properties` — two new local-only fake
  defaults: `themistra.payment.receipt.prefix=${RECEIPT_BUCKET_PREFIX:receipts/}`,
  `themistra.payment.receipt.region=${RECEIPT_BUCKET_REGION:us-east-1}`.

**Mechanically fixed (not new test content, kept the module buildable):**
- `test/.../common/config/ConfigPropertiesBindingTest.java` — the four existing
  `ReceiptConfig`-based tests (`receiptPropertiesBindFromValidConfiguration`,
  `receiptPropertiesFailWhenBucketIsBlank`, `receiptPropertiesFailWhenKmsPublicKeysUrlIsBlank`,
  `receiptPropertiesFailWhenBucketIsMissingEntirely`, `receiptPropertiesFailWhenKmsPublicKeysUrlIsMissingEntirely`)
  now also supply `prefix`/`region` values, since both are newly `@NotBlank` and would otherwise
  fail each of those tests for an unrelated reason (a missing new required field, not the field
  each test actually means to exercise). The valid-binding test also gained two new assertions
  (`props.prefix()`/`props.region()`) since it already asserts every other bound field
  individually (Phase 11-era precedent in that same file). No new *failure-path* test for
  `prefix`/`region` added here — that's Phase 10's own job, per the standing rule that new test
  content belongs there, not Phase 6.

## Deviations forced by reality

**`ReceiptStore`'s PDF-layout constants (`MARGIN`/`FONT_SIZE`/`LEADING`) are declared `int`, not
`float`, contrary to the Phase 5 plan's implicit assumption.** `ArchitectureTest
.shouldRejectFloatingPointMoneyTypesAtArchUnit` bans every `float`/`double`/`Float`/`Double`
field, method return type, or method/constructor parameter anywhere in `com.themistra.payment..`
— confirmed by running the full suite and reading the rule's own definition directly
(`ArchitectureTest.java` line ~60): it is a blanket ban across the whole package (L6), not a
semantic "is this actually money" check, so it caught these three PDF-layout constants even
though they have nothing to do with money. Fixed by declaring them `int` and relying on Java's
automatic `int`-to-`float` widening at each PDFBox API call site (`setFont`, `newLineAtOffset`,
etc.) — those calls are to an external library's own methods, not fields/methods declared on a
class in this package, so they're outside the rule's reach; the local `float y` variable inside
`renderPdf` is likewise untouched by the rule, which only inspects fields and method/constructor
signatures, never local variables. No other deviation from the Phase 5 plan.

## Mapping to the frozen brief / acceptance criteria

- **AC1 (Finding 1)** — both `putObject` calls happen, keyed `s3KeyPrefix + ".json"` /
  `s3KeyPrefix + ".pdf"`; `store()` returns `ReceiptStorageLocation` with all four fields.
- **AC2** — both `PutObjectRequest`s set `objectLockMode(COMPLIANCE)` and
  `objectLockRetainUntilDate = clock.instant().plus(Duration.ofDays(7 * 365))`.
- **AC3** — `ReceiptStore` contains no `deleteObject`/overwrite call anywhere in its code.
- **AC4** — `putOrThrow` wraps any `SdkException` as `ReceiptStorageException`, never caught and
  swallowed elsewhere in the class.
- **AC5 (Finding 3)** — `toCanonicalMap` builds the 12 `ReceiptDigest`-identical conversions plus
  `signature`/`kmsKeyId`/`signedAt`, serialized via the existing `CanonicalJson.serialize(...)`.
- **AC6 (Finding 2)** — JSON `putObject` executes before the PDF `putObject` (sequential method
  calls, no concurrency); both carry identical `receipt-uuid`/`invoice-uuid`/`payment-uuid`
  object metadata.
- **AC7 (Finding 4)** — `renderPdf` produces a single A4 page of Helvetica 12pt label/value text,
  `signature` wrapped at 64 characters/line; text-extractability confirmed empirically this
  phase (see below), not merely asserted.

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`: clean.
- `mvn -o -pl services/payment -am test-compile`: clean (no signature changes to any existing
  caller — `ReceiptStore` has no caller yet, per the frozen brief's own confirmed scope).
- A throwaway JUnit smoke test (written, run, then deleted before this commit — not part of
  Phase 10's own deliverable) drove `ReceiptStore.store(...)` against a mocked `S3Client` and
  confirmed, empirically rather than by inspection alone:
  - the JSON `putObject` is called before the PDF `putObject`;
  - the returned `ReceiptStorageLocation` keys are exactly `receipts/<uuid>.json`/`.pdf`;
  - `objectLockRetainUntilDate` on both requests equals the fixed-clock instant plus exactly
    `7 * 365` days (`2026-10-15T00:00:00Z` → `2033-10-13T00:00:00Z`, confirmed by direct
    comparison, not just a tolerance check);
  - the captured JSON body round-trips to the expected 15 canonical fields, correctly sorted;
  - the captured PDF bytes, re-loaded via PDFBox's own `Loader.loadPDF(...)` +
    `PDFTextStripper`, yield real extractable text containing the fixture's `receiptUuid`,
    `txHash`, and wrapped `signature` chunks (AC7, a genuine round-trip, not a byte-length
    check);
  - a mocked `SdkException` thrown from the first (JSON) `putObject` call propagates as
    `ReceiptStorageException`, with the real `SdkException` as its cause, and `deleteObject` is
    never called (AC3/AC4, mutation-style verification — not merely inspecting the code for the
    absence of a delete call).
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`): initially 2 failures, both diagnosed and fixed this phase:
  1. `ConfigPropertiesBindingTest` — the four existing `ReceiptConfig` tests failed to bind
     because `prefix`/`region` are now required; fixed mechanically (above).
  2. `ArchitectureTest.shouldRejectFloatingPointMoneyTypesAtArchUnit` — the three `float` PDF
     layout constants; fixed by declaring them `int` (above, a real design deviation, not a test
     change).
  After both fixes: **374/374 passing**, unchanged from the T17 baseline — no regression, no new
  test content added yet (`ReceiptStoreTest` is Phase 10's own job).
