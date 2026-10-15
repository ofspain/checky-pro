# payment · T18 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

- **R23.** WHEN a receipt is issued, THEN the system SHALL store the signed receipt object
  (JSON, and PDF per Q5) in the S3 Object Lock bucket in compliance mode, and SHALL NOT
  overwrite an existing receipt object.

## Locked Decisions

- **L9.** Signed receipts are written once to the S3 Object Lock bucket in compliance mode;
  there is no overwrite or delete path in application code. The object key embeds the
  `receiptUuid`; a second write to the same key is a bug, not an update.
- **L13** (context): secrets discipline — no AWS credential is ever set in application code;
  the SDK's own default credential chain applies (confirmed as the established pattern via
  `crypto-service`'s own `ObservationSnapshotStoreConfig`).

## Files involved

**Existing, to extend:**
- `common/config/ReceiptProperties.java` — needs a new `region` field (confirmed missing at
  Phase 0; `S3Client` construction requires one and no fallback/default exists anywhere else).
- `services/payment/pom.xml` — needs Apache PDFBox added (confirmed absent at Phase 0; the AWS
  S3 SDK dependency already exists, added at T01 for exactly this task).

**Existing, read-only (the exact data this task's own method receives):**
- `receipt/ReceiptPayload.java`/`ReceiptDigest.java` (T16) — the 12-field O4 shape.
- `receipt/ReceiptIssueResult.Signed` (T17) — `receiptUuid`, `AttestationResult.Signed`
  (`signature`/`kmsKeyId`/`signedAt`).

**New files the spec expects:**
- `receipt/ReceiptStore.java` — this task's own primary deliverable.
- An `S3Client` Spring bean/config class (none exists yet in this service).

**Not touched, confirmed out of scope (Phase 0):** `receipt/Receipt.java`/`ReceiptRepository.java`
— no DB persistence of the S3 key happens in this task.

## Dependencies

- AWS SDK v2 `S3Client`/`PutObjectRequest` (already a `pom.xml` dependency since T01, unused
  until now) — specifically its Object Lock fields (`objectLockMode`,
  `objectLockRetainUntilDate`, per the AWS SDK's own `PutObjectRequest.Builder`).
- Apache PDFBox (net new dependency).
- `ReceiptPayload`/`ReceiptIssueResult.Signed` as the method's own input shape.

## Acceptance Criteria

- **AC1 (R23/L9).** `ReceiptStore` writes both a JSON object (the signed receipt — O4's 12
  fields plus `signature`/`kmsKeyId`/`signedAt`) and a PDF rendering of the same fields to the
  configured S3 bucket, with a key that embeds `receiptUuid`.
- **AC2 (L9).** The S3 request sets Object Lock compliance-mode retention — exact duration is a
  Phase 2 design decision (not specified anywhere in the spec package, confirmed at Phase 0).
- **AC3 (R23/L9, "no overwrite path").** The application code contains no S3 delete/overwrite
  call for an existing receipt object — exact enforcement mechanism (rely on Object Lock + a
  deterministic, never-reused key, vs. an explicit pre-write existence check) is a Phase 2
  design decision.
- **AC4 (reliability, distinct from `crypto-service`'s own precedent).** Unlike
  `ObservationSnapshotStore`'s own "S3 is supplementary, never throws" stance, this task's own
  S3 write is the load-bearing operation R23 exists to guarantee — a failure must be
  observable to the caller (propagated, not silently swallowed into an `Optional.empty()`-style
  return).

## Tests required

The one named test from `package.md` §8 is pre-mapped:
- `shouldStoreReceiptInS3ObjectLockWorm` (R23/L9) — the primary test, covering both the JSON and
  PDF writes and the Object Lock parameters.

Boundary tests implied, not separately named:
- A test proving the S3 key embeds `receiptUuid` (not a random/unrelated value).
- A test proving a real S3 failure propagates rather than being swallowed (AC4, the explicit
  contrast with `crypto-service`'s own precedent).
- A test proving the Object Lock mode/retention fields are actually set on the request (AC2) —
  exact mechanism (unit-level request inspection vs. a LocalStack integration test) is a Phase
  1/2 testing-strategy question, carried from Phase 0.

## Open Questions

Both carried forward from Phase 0, confirmed not pre-answered anywhere in `package.md` §8/§11:

1. **No Object Lock retention duration is specified anywhere in the spec package.** This is
   arguably a compliance/legal policy question (real-world WORM retention periods are often
   externally mandated, e.g. by financial recordkeeping regulations), not a pure engineering
   default — flagging as a candidate for a genuine `AskUserQuestion` at Phase 2, not a silent
   assumption, mirroring T17's own R17-scope precedent for resolving a real policy ambiguity
   before starting detailed design.
2. **Whether `ReceiptStore` needs a defensive pre-write existence check or relies on Object
   Lock + a deterministic key alone.** An implementation-shape decision within this task's own
   discretion — proposed at Phase 2, stress-tested at Phase 3, not escalated as a blocker.

Neither blocks starting Phase 2, but Question 1's own answer should be resolved directly with
the user before finalizing the TIB, given its real compliance-policy character.
