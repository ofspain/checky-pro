# payment · T18 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`, `artifacts/11-test-review-resolution.md`

## Traceability matrix

| Requirement/Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R23** — a signed receipt (JSON + PDF) is stored in the S3 Object Lock bucket in compliance mode, never overwritten | Yes | `ReceiptStore.java:92` (`store`), `:145` (`buildPutRequest` — `objectLockMode(COMPLIANCE)`) | `shouldStoreReceiptInS3ObjectLockWorm` (named), `shouldSetRetentionDateToSevenYearsFromTheInjectedClock` | No | No |
| **L9** — the object key embeds `receiptUuid`; no overwrite/delete path in application code; a second write to the same key is a bug, not an update | Yes | `ReceiptStore.java:96-98` (key construction), no `deleteObject`/`copyObject` call anywhere in the file | `shouldEmbedReceiptUuidInBothS3Keys`, `shouldNeverCallDeleteOrAnyOverwriteApi` (broadened Phase 11 to also forbid `copyObject`) | No | No |
| **L13** (context) — no AWS credential set in code | Yes | `ReceiptStoreConfig.java` — `S3Client.builder()` with no credentials provider, mirrors `ObservationSnapshotStoreConfig` | N/A (a config-wiring fact, not independently testable at the unit level) | No | No |
| **New, found during design (Phase 3 Finding 1)** — `ReceiptStorageLocation` carries a common prefix plus both full keys, since `receipts.s3_key` is a single column | Yes | `ReceiptStorageLocation.java` (4-field record), `ReceiptStore.java:114` | `shouldEmbedReceiptUuidInBothS3Keys` | No | No |
| **New, found during design (Phase 3 Finding 2)** — JSON written before PDF; matching metadata on both objects for ops reconciliation | Yes | `ReceiptStore.java:105-112` (sequential calls), `:153-156` (shared metadata) | `shouldWrapSdkExceptionFromJsonPutAsReceiptStorageException`/`...FromPdfPutAsReceiptStorageException` (prove the ordering), `shouldIncludeMatchingObjectMetadataOnBothPutRequests` | No | No |
| **New, found during design (Phase 3 Finding 3)** — stored JSON is the canonical 15-field payload (12 O4 fields + signature/kmsKeyId/signedAt) | Yes | `ReceiptStore.java:125-143` (`toCanonicalMap`) | `shouldSerializeStoredJsonAsCanonicalFifteenFieldPayload` (exact-bytes comparison, Kimi Phase 8 New Finding 3) | No | No |
| **New, found during design (Phase 3 Finding 4)** — PDF is a single A4, Helvetica 12pt page, text-extractable, signature wrapped | Yes | `ReceiptStore.java:179-214` (`renderPdf`) | `shouldProduceTextExtractablePdfContainingReceiptUuidAndTxHash` (real PDFBox round-trip) | No | No |
| **New, found during review (Phase 7)** — any failure (S3 or PDF-render) is observable to the caller as one exception type, never swallowed (AC4) | Yes | `ReceiptStore.java:160-166` (`putOrThrow`), `:211-213` (`renderPdf`'s own catch) | `shouldWrapSdkExceptionFromJsonPutAsReceiptStorageException`/`...FromPdfPutAsReceiptStorageException` | Partial — no direct test for the PDF-render `IOException` path (Phase 11 Gap 1, accepted-not-fixed) | No |
| **New, found during review (Phase 8/9)** — a misconfigured prefix without a trailing delimiter still produces the intended key shape | Yes | `ReceiptStore.java:224-226` (`normalizedPrefix`) | `shouldNormalizePrefixWithoutTrailingSlash` (mutation-verified) | No | No |
| **New, found during review (Phase 7/8)** — `ReceiptStore` is constructed exactly once, via `ReceiptStoreConfig`'s own `@Bean` method, matching `ObservationSnapshotStore`'s own precedent | Yes | `ReceiptStore.java` (no `@Component`), `ReceiptStoreConfig.java` | Confirmed empirically via a temporary probe against the real `ChainEventConsumerIdempotencyAndOrderingIntegrationTest` context (reverted, not committed) | N/A | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. `ReceiptStore.store(ReceiptPayload, AttestationResult
.Signed)` is the complete, self-contained S3 WORM write this task's own scope calls for —
confirmed to have no caller yet (the correct, deliberate scope boundary: T19's own job, same
"built one task ahead of its consumer" shape T15/T16 already established).

**(2) Does it satisfy every acceptance criterion (frozen brief, Phase 4)?** All seven (AC1–AC7)
are satisfied: AC1 (both objects written, keyed under a common prefix), AC2 (Object Lock
compliance mode + 7-year retention), AC3 (no delete/overwrite call — broadened at Phase 11 to
also cover `copyObject`), AC4 (any failure propagates as `ReceiptStorageException`, unified at
Phase 7 across both the S3 and PDF-render failure paths), AC5 (canonical 15-field JSON, exact
bytes verified at Phase 10), AC6 (JSON-first ordering with matching metadata), AC7 (PDF is
genuinely text-extractable, not merely non-empty).

**(3) Does it violate any LOCKED decision?** No. L9 holds — no delete/overwrite API call exists
anywhere in the file (confirmed by direct inspection and by a dedicated test); the key embeds
`receiptUuid`. L13 holds — no credential set in code. L12/continuity holds — `ReceiptStore`
imports neither `Payment` nor `Invoice`, only `ReceiptPayload`/`AttestationResult.Signed`.

**(4) Remaining risks (carried forward, not blocking this task's own PASS):**
- **No end-to-end integration test exercises `ReceiptStore` against a real or LocalStack-backed
  S3** (Kimi Phase 8 New Finding 4, Phase 9) — deferred to a future task once T19 wires a real
  caller into the live flow, mirroring T17's own Phase 11 Gap 5 reasoning for the identical class
  of deferred test.
- **No direct unit test forces the PDF-rendering `IOException` path** (Phase 11 Gap 1) — judged
  disproportionate effort for a Low-confidence gap without an intrusive test seam; the S3 failure
  path (the far more likely real-world failure mode) is fully covered.
- **The partial-write window between the JSON and PDF writes is an accepted, documented
  operational risk** (Phase 3 Finding 2) — not solvable within a single-object S3 API, mitigated
  by matching object metadata for ops-side reconciliation, not by application-level rollback.
- **No Object Lock retention-duration regulatory citation** — 7 years was a direct product
  decision (`AskUserQuestion`), not sourced from a specific named jurisdiction's statute; carried
  as the team's own chosen compliance floor, not independently legally verified here.

## Verdict

**PASS** — `ReceiptStore` fully implements R23/L9: both the canonical JSON and the PDF rendering
are durably written to the Object Lock bucket in compliance mode with a 7-year per-object
retention, no overwrite/delete path exists anywhere in the code, and any failure — S3 or
PDF-render — is uniformly observable to a future caller via `ReceiptStorageException`. Two real
process-quality catches this task is worth naming: a genuine duplicate Spring bean registration
(self-caught at Phase 7, before Kimi's independent review ran) and a real schema mismatch
(`receipts.s3_key` is singular; this task writes two objects — caught by Kimi's own Phase 3
design challenge before any code was written, reshaping `ReceiptStorageLocation`'s own shape).
`ReceiptStoreTest`: 12/12 passing. Full payment-service suite: 386/386 passing.
