# payment · T18 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Implement `ReceiptStore`: writes the signed receipt as both JSON and a PDF rendering (Apache
PDFBox) to the S3 Object Lock bucket in compliance mode, with a key embedding `receiptUuid`, no
overwrite path.

## Purpose

This is the second of L2's own three durable steps ("the signature is obtained, the receipt is
stored in S3, and the ledger entry is appended, all durably") — the first real, tamper-evident
storage of a signed receipt, giving Themistra's own "how do we know we didn't alter this
record?" claim its physical backing.

## Scope

**In:** `ReceiptStore`'s own store method; the new `S3Client` bean/config; the `region` field
addition to `ReceiptProperties`; the Apache PDFBox dependency and PDF-rendering logic.

**Out:** Persisting a `Receipt` row (T19's own job, once the S3 key is known — out of scope per
Phase 0's confirmed finding), the `ATTESTED` transition, the `receipt.issued` event, the
hash-chain ledger.

## Business Rules

- **R23.**

## Locked Decisions

- **L9**, resolved fully now:
  - **Retention (decided directly by the user via `AskUserQuestion`): 7 years**, set as a
    per-object `objectLockRetainUntilDate` computed from the write instant
    (`clock.instant().plus(Duration.ofDays(7 * 365))` — a fixed day-count, not a calendar-aware
    "7 years," since neither AWS's own API nor this codebase's established `Clock`-injection
    pattern works in calendar terms; the ~0.24-day leap-year drift over 7 years is immaterial
    for a compliance-hold floor).
  - **No overwrite enforcement (proposed, for Phase 3 to stress-test): rely on Object Lock +
    a deterministic, never-reused key — no defensive pre-write existence check.** L9's own text
    ("a second write to the same key is a bug, not an update") frames this as a caller-
    correctness invariant, not a condition `ReceiptStore` itself must defensively detect; adding
    a `headObject` pre-check would be an extra round-trip for a scenario that structurally
    cannot occur (each `receiptUuid` is generated fresh exactly once per successful `attest()`
    call, T17), and even if it somehow did occur, Object Lock compliance mode would itself
    reject any attempt to overwrite an already-retained object at the platform level — an
    application-level check would be redundant with, not additive to, that guarantee.
- **L13** (context): no credential set in code; mirrors `ObservationSnapshotStoreConfig`'s own
  precedent exactly.

## Dependencies

- `S3Client`/`PutObjectRequest` (AWS SDK v2, already a `pom.xml` dependency).
- Apache PDFBox (new dependency).
- `ReceiptPayload`, `ReceiptIssueResult.Signed` — the caller-provided inputs.
- `Clock` (injected, for the retention-date computation — matches this codebase's own
  established convention everywhere a class needs "now").

## Inputs

`ReceiptStore`'s own store method takes a `ReceiptPayload` (the 12 O4 fields) plus the signed
data (`signature`/`kmsKeyId`/`signedAt` from `AttestationResult.Signed`) — never a `Payment`/
`Invoice` entity, consistent with every cross-module boundary this task's own predecessors
(T16/T17) already established.

## Outputs

- **Reliability decision (resolves Phase 1 AC4 — the explicit contrast with
  `ObservationSnapshotStore`'s own "never throws" precedent): `ReceiptStore` throws an
  unchecked exception on an S3 failure, never swallows it.** A new
  `ReceiptStorageException` (unchecked), mirroring `CryptoServiceUnavailableException`'s own
  shape — wraps the real `SdkException`. Deliberately NOT following `ObservationSnapshotStore`'s
  own "supplementary, swallow failures" stance: that class's own Javadoc explicitly frames S3 as
  non-blocking for its own use case, but R23's own "SHALL store" text makes this task's S3 write
  the load-bearing guarantee itself — a caller (T19, not built yet) needs to know definitively
  whether the receipt was actually, durably stored before proceeding to the ledger append and
  the `ATTESTED` transition.
- **Return value**: on success, the method returns enough information for a future caller to
  populate the `receipts` table's own `s3_bucket`/`s3_key` columns (T19's job) — a small record,
  e.g. `ReceiptStorageLocation(String bucket, String jsonKey, String pdfKey)` (exact shape an
  implementation-phase choice).

## State Changes

None in this service's own database — this task writes only to S3.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java`.
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStorageLocation.java`.
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStorageException.java`.
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStoreConfig.java` (the
  `S3Client` bean, mirrors `ObservationSnapshotStoreConfig`'s own naming/shape convention).

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/common/config/ReceiptProperties.java`
  — add `@NotBlank String region`.
- `services/payment/pom.xml` — add Apache PDFBox.

## Files NOT to Modify

`ReceiptPayload`/`ReceiptDigest`/`ReceiptIssueResult` (T16/T17, used as-is), `ReceiptService`
(no wiring change in this task — `ReceiptStore` has no caller yet, same "built one task ahead
of its consumer" shape T15/T16 already established), anything under `spec/`.

## Acceptance Criteria

- **AC1.** Both a JSON object and a PDF rendering are written to S3, each keyed with
  `receiptUuid`.
- **AC2.** The `PutObjectRequest` sets `objectLockMode(ObjectLockMode.COMPLIANCE)` and
  `objectLockRetainUntilDate` 7 years from the write instant.
- **AC3.** No delete/overwrite S3 API call exists anywhere in `ReceiptStore`'s own code.
- **AC4.** An S3 `SdkException` propagates as `ReceiptStorageException`, never swallowed.

## Required Tests

Unit, mocked `S3Client`, no Spring context (mirrors `CryptoClientTest`'s own established
local-dependency-mocking shape, adapted — here the collaborator being mocked is `S3Client`
itself, via Mockito, not a real local HTTP server, since the AWS SDK's own client is a plain
Java interface):
1. `shouldStoreReceiptInS3ObjectLockWorm` (named) — both JSON and PDF `putObject` calls happen,
   each with the correct bucket/key/Object Lock parameters.
2. A test proving the key embeds `receiptUuid`.
3. A test proving the retention date is ~7 years from the injected `Clock`'s own fixed instant.
4. A test proving an `SdkException` from `s3Client.putObject` propagates as
   `ReceiptStorageException`, for both the JSON and PDF write calls.
5. A test proving the PDF byte content is non-empty and contains recognizable rendered text
   (exact assertion mechanism — e.g. extracting text via PDFBox's own reader — an
   implementation-phase choice).

## Constraints

- **Transaction:** none — this task has no DB interaction at all.
- **Thread-safety:** `ReceiptStore` is stateless beyond its injected `S3Client`/`Clock`/
  `ReceiptProperties`, consistent with every other service class in this codebase.
- **Module boundaries:** no `Invoice`/`Payment` entity import (L12, continuity).
- **Security:** no AWS credential in code (L13); no secret/PII beyond what the receipt itself
  already legitimately carries is logged.
- **Null handling:** trusts the caller for well-formed `ReceiptPayload`/`AttestationResult.
  Signed` inputs, mirroring every prior task's own established stance.

## Open Questions

No blockers. Both Phase 1 questions are resolved directly above (retention: 7 years, decided
by the user; no-overwrite enforcement: rely on Object Lock + a deterministic key, proposed for
Phase 3 to stress-test).
