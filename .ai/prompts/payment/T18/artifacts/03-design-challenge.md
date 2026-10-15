# payment · T18 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T18/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `spec/payment-service/design.md` §L2/§L9/§L13/§O4/§6, `spec/payment-service/agents.md` (L12/L13), `ReceiptProperties.java`, `application.properties`, `services/crypto/.../ObservationSnapshotStoreConfig.java`, `services/crypto/.../ObservationSnapshotStore.java`, `services/crypto/.../SnapshotProperties.java`, `ReceiptPayload.java`, `ReceiptIssueResult.java`, `AttestationResult.java`.

---

## Executive summary

The brief is directionally sound: a stateless `ReceiptStore` in `receipt/`, an S3 Object Lock WORM write with compliance mode, both JSON and PDF artifacts, and an unchecked `ReceiptStorageException`. The three load-bearing gaps are (1) the existing `receipts.s3_key` schema column is singular but the task produces two S3 objects, (2) two separate `putObject` calls create an unrecoverable partial-write window because Object Lock prevents deletion, and (3) the exact content/format of the stored JSON and PDF is unspecified. Smaller gaps cover timeouts, key prefixes, and the local `region` default.

---

## Finding 1 (High) — The schema's single `receipts.s3_key` column cannot represent both JSON and PDF objects

- **Issue:** The brief's proposed `ReceiptStorageLocation` returns a bucket plus separate `jsonKey` and `pdfKey`. However, `V1__payments_baseline.sql`'s `receipts` table has only one `s3_key VARCHAR(256) NOT NULL` column. T19 (out of scope here) will need to persist *something* in that column, but the design does not say which of the two keys it is or how the other is recovered.
- **Impact:** Either T19 will store only one key and lose track of the other artifact, or the design must silently assume a derivable relationship between the two keys that is not documented.
- **Evidence:**
  - `spec/payment-service/design.md` §4c `receipts` table: `s3_bucket`, `s3_key` (singular).
  - `spec/payment-service/design.md` §O4 explicitly requires both a JSON object and a PDF rendering.
  - The brief says "Files NOT to Modify: `spec/`", so adding a second column is ruled out under the current plan.
- **Recommendation:** Treat the single `s3_key` column as a **common prefix** rather than a single object key. Example:
  - prefix stored in DB: `receipts/<receiptUuid>`
  - JSON object key: `receipts/<receiptUuid>.json`
  - PDF object key: `receipts/<receiptUuid>.pdf`
  
  Return `ReceiptStorageLocation(bucket, s3KeyPrefix, jsonKey, pdfKey)` so T19 can store the prefix in `receipts.s3_key` and both full keys are available to any caller that needs them.
- **Confidence:** High.

---

## Finding 2 (Medium) — Two sequential `putObject` calls create an unrecoverable partial-write window

- **Issue:** `ReceiptStore` must write both JSON and PDF. If the first `putObject` succeeds and the second fails, the first object is already durably stored under Object Lock compliance mode and cannot be deleted or overwritten by application code.
- **Impact:** A transient S3 failure during the second write leaves a half-stored receipt (e.g., JSON without PDF, or PDF without JSON). Because the caller throws `ReceiptStorageException`, the ledger append and `ATTESTED` transition do not happen, but the orphaned S3 object remains and may confuse future audits.
- **Evidence:**
  - `spec/payment-service/design.md` §L9: "no overwrite or delete path exists in application code."
  - AWS Object Lock compliance mode actively prevents deletion or overwrite of a protected object version.
- **Recommendation:**
  1. Document the partial-write window as an accepted, low-probability operational risk (there is no multi-object atomic commit in S3).
  2. Write the **JSON first** (the canonical, machine-verifiable artifact) and the PDF second (the derivative human-readable rendering). If the PDF fails, the more important artifact is still present.
  3. Add matching S3 object metadata (`receipt-uuid`, `invoice-uuid`, `payment-uuid`) to both objects so an orphaned half-pair can be identified and reconciled by ops.
- **Confidence:** Medium.

---

## Finding 3 (Medium) — The exact format of the stored JSON artifact is undefined

- **Issue:** The brief says "writes the signed receipt as both JSON" but does not specify whether the stored JSON is the original 12-field canonical payload, the 12 fields plus `signature`/`kmsKeyId`/`signedAt`, pretty-printed or canonical, or with what key ordering.
- **Impact:** A future verifier (merchant portal, audit, tax export) that downloads the S3 JSON may not be able to prove it matches the digest that was attested, or may accidentally rely on a non-canonical representation.
- **Evidence:**
  - `spec/payment-service/design.md` §O4: the digest covers exactly the 12 O4 fields; signature fields are added "once attested."
  - `ReceiptPayload` and `CanonicalJson` already provide a deterministic canonicalizer.
- **Recommendation:** Store a **canonical JSON object** containing all 12 O4 fields plus `signature`, `kmsKeyId`, and `signedAt`, with keys sorted lexicographically, no insignificant whitespace, and monetary values as decimal strings. Reuse `CanonicalJson.serialize(...)` after building the 15-field map. This makes the artifact self-describing and verifiable: a verifier can remove the three signature fields and re-canonicalize to obtain the exact digest that was submitted to `/attest`.
- **Confidence:** Medium.

---

## Finding 4 (Medium) — PDF rendering details are unspecified

- **Issue:** The brief requires a PDF rendering using Apache PDFBox but gives no guidance on page size, font, layout, text extraction, or how to display a long base64 signature.
- **Impact:** Different implementers could produce PDFs that are not human-readable, not text-extractable, or that overflow the page.
- **Recommendation:**
  - Use Apache PDFBox 3 (`org.apache.pdfbox:pdfbox:3.0.3`).
  - A4 page, 1-inch margins, Helvetica 12pt.
  - Title "Themistira Receipt" followed by label/value pairs for the same 15 fields in the stored JSON.
  - The base64 `signature` should be broken into 64-character lines so it does not overflow the page.
  - Include `receiptUuid` prominently at the top; include `signedAt` in RFC3339 form.
  - Tests should use `PDFTextStripper` to assert the page contains expected text (e.g., the `receiptUuid` and `txHash`).
- **Confidence:** Medium.

---

## Finding 5 (Low) — No S3 API timeout is specified

- **Issue:** The brief does not set a timeout on the `S3Client`. The codebase precedent (`ObservationSnapshotStoreConfig`) uses a fixed 5-second API call timeout.
- **Impact:** A hung S3 call could block the caller for the SDK's default duration (which can be much longer) before `ReceiptStorageException` is thrown.
- **Recommendation:** Add `ClientOverrideConfiguration` in `ReceiptStoreConfig` with a fixed or configurable API call timeout. For a load-bearing blocking call that also generates a PDF, 10 seconds is more appropriate than 5.
- **Confidence:** Low.

---

## Finding 6 (Low) — `ReceiptProperties` lacks a key prefix

- **Issue:** The brief only adds `region` to `ReceiptProperties`. The S3 key will embed `receiptUuid` but no path prefix, so every receipt object lands in the bucket root.
- **Impact:** Bucket root clutter, harder lifecycle policies, and inconsistent organization with the sibling `ObservationSnapshotStore` which uses `properties.prefix()`.
- **Recommendation:** Add `@NotBlank String prefix` to `ReceiptProperties` with a local default of `receipts/`, and build keys as `prefix + receiptUuid`. This keeps the bucket root clean and matches the existing S3-store precedent.
- **Confidence:** Low.

---

## Finding 7 (Low) — `application.properties` needs a local `region` default

- **Issue:** Adding `@NotBlank String region` to `ReceiptProperties` will fail startup validation in the `local` profile unless a value is present.
- **Impact:** Local dev and any test that boots the Spring context will fail to start.
- **Evidence:** `application.properties` already has local-only fake defaults for `themistra.payment.receipt.bucket` and `themistra.payment.receipt.kms-public-keys-url`.
- **Recommendation:** Add `themistra.payment.receipt.region=${RECEIPT_BUCKET_REGION:us-east-1}` to `application.properties`.
- **Confidence:** Low.

---

## Finding 8 (Low) — `ReceiptStorageException` placement

- **Issue:** The brief places `ReceiptStorageException` in `receipt/`. If a caller in another module (e.g., a future ledger orchestrator in `receipt/` anyway) catches it, this is fine. If it ever needs to be caught from `common` or another feature module, the dependency direction is still `feature → receipt`, which is allowed by L12 (only entity imports are banned).
- **Impact:** None currently; just a placement note.
- **Recommendation:** Keep it in `receipt/` unless a cross-module caller appears. No change needed now.
- **Confidence:** Low.

---

## Finding 9 (Low) — No delete/overwrite API path is the right call, but versioning nuance should be documented

- **Issue:** The brief correctly states there is no application delete/overwrite path. However, on a versioned Object Lock bucket, a second `putObject` to the same key creates a **new version** rather than overwriting the old one. Object Lock does not prevent new versions.
- **Impact:** A caller bug that reuses a `receiptUuid` would silently create multiple versions of the same logical receipt, all retained for 7 years. This is not an overwrite, but it is still undesirable.
- **Recommendation:** Document that the no-overwrite guarantee is structural (`receiptUuid` generated once per successful `attest()`) rather than enforced by the SDK call, and that Object Lock + versioning means duplicate writes become additional retained versions.
- **Confidence:** Low.

---

## Positive confirmations

- `ReceiptStore` belongs in `receipt/`, has no `Payment`/`Invoice` entity imports, and follows the cross-module boundary established by T16/T17.
- Using AWS SDK v2 `S3Client` and `PutObjectRequest` matches the existing `ObservationSnapshotStore` precedent and the already-declared `software.amazon.awssdk:s3` dependency.
- Throwing `ReceiptStorageException` on any S3 failure is the right contrast with the sibling store's swallow-failures stance, because R23's "SHALL store" makes this write load-bearing.
- Storing both JSON and PDF aligns with `design.md` §O4.
- Object Lock compliance mode + 7-year retention satisfies L9 and the user-confirmed retention decision.
- A stateless `ReceiptStore` with injected `S3Client`, `Clock`, and `ReceiptProperties` is thread-safe and consistent with every other service class in the codebase.
- The brief correctly defers the `Receipt` row, `ATTESTED` transition, ledger append, and `receipt.issued` event to T19.

---

## Disposition summary

| Finding | Severity | Disposition |
|---|---|---|
| 1. Singular `s3_key` vs two objects | High | Store a common prefix in `receipts.s3_key`; return prefix + full `jsonKey`/`pdfKey` |
| 2. Partial-write window | Medium | Document; write JSON first; add linking metadata to both objects |
| 3. Stored JSON format | Medium | Canonical 15-field JSON (12 O4 + signature/kmsKeyId/signedAt) |
| 4. PDF rendering details | Medium | A4, Helvetica, label/value pairs, wrapped signature, text-extractable |
| 5. S3 timeout | Low | Add `ClientOverrideConfiguration` timeout in `ReceiptStoreConfig` |
| 6. Key prefix | Low | Add `prefix` to `ReceiptProperties` with local default |
| 7. Local `region` default | Low | Add property to `application.properties` |
| 8. Exception placement | Low | Keep in `receipt/` |
| 9. Versioning nuance | Low | Document structural no-duplicate guarantee |

---

## Recommended next step

Update the brief to:
1. Define `ReceiptStorageLocation` as `(bucket, s3KeyPrefix, jsonKey, pdfKey)` and clarify that `receipts.s3_key` stores the prefix.
2. Specify JSON-first write order and the partial-write operational risk.
3. Mandate canonical 15-field JSON for the stored artifact.
4. Add the PDF layout convention and PDFBox version.
5. Add `prefix` to `ReceiptProperties` and `region` to `application.properties`.

Then freeze and proceed to Phase 5 implementation. Findings 1 and 2 are load-bearing; the rest are clarity/robustness improvements.
