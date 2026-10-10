# 3. Requirements — acceptance criteria (EARS)

Each requirement is independently testable and maps to a named test in [`package.md`](package.md) §8.
External vision/forensics calls are never made in tests — a capturing fake client stands in (agents.md).

## Evidence upload & storage

- R1. WHEN an authenticated account uploads an evidence file for a transaction hash, THEN the system SHALL store the raw file in S3 with Object Lock (WORM) and persist an `EvidenceItem` row before returning an acknowledgement.
- R2. WHEN an evidence file is accepted, THEN the system SHALL compute and store its SHA-256 digest, and IF an identical digest already exists for the same account and transaction hash, THEN the system SHALL return the existing item rather than storing a duplicate.
- R3. IF an uploaded file exceeds the configured maximum size or is not one of the allowed content types, THEN the system SHALL reject it with a validation error and SHALL NOT store anything.
- R4. The system SHALL NOT make an evidence file's raw byte stream available through any API response — only derived, non-sensitive metadata (filename, content type, size, `uploadedAt`, analysis results) is ever returned.
- R5. The system SHALL NOT delete or overwrite a stored evidence file once accepted.

## Automatic evidence collection (from a transaction hash alone)

**Design note (2026-10-10):** this section originally assumed Crypto Service's existing
`registerWatch` could serve a user-submitted, already-occurred transaction hash. Verified directly
against `WatchService.java` during this spec's own authoring: `registerWatch` only watches an
address prospectively for a *future* payment, with no tx-hash input and no backfill/lookback. The
real mechanism is a new, small, read-only lookup endpoint on Crypto Service (`spec/crypto-service`
R29/L16/task 30 — **not yet built**), reusing its existing quorum logic, that this service polls.

- R6. WHEN an authenticated account submits a transaction hash and chain with no file attached, THEN the system SHALL persist an `EvidenceItem` of kind `AUTO_COLLECTED` and SHALL begin polling Crypto Service's on-demand transaction-lookup endpoint for that hash (design.md §4c).
- R7. WHEN a poll of that endpoint returns an `AGREED` outcome at a new confirmation milestone (seen / confirmed / finalized), THEN the system SHALL append the returned facts to that evidence item's own collected-facts record and SHALL NOT overwrite a fact already recorded for a different milestone.
- R8. IF a later poll shows a transaction hash's previously `AGREED` facts no longer hold (a reorg), THEN the system SHALL mark every fact collected before that point as `INVALIDATED` rather than deleting it.
- R9. The system SHALL record at most one collected-facts row per `<evidence item, milestone>` pair — a repeated poll returning the identical terminal outcome SHALL leave the collected-facts record unchanged.
- R10. WHEN polling for a transaction hash reaches a terminal outcome (finalized, or the configured timeout, design.md O6) THEN the system SHALL stop polling and SHALL retain every fact already collected.

## AI evidence interpretation

- R11. WHEN an image or PDF evidence item is stored, THEN the system SHALL submit it to the configured external vision model for structured-field extraction (merchant, wallet address, token, amount, date, transaction reference, chain, status) and SHALL persist the result as an `ExtractionResult` linked to that evidence item.
- R12. WHEN an `ExtractionResult` is persisted, THEN the system SHALL record the model/vendor identifier and version that produced it, and a per-field confidence value when the vendor provides one.
- R13. IF the external vision call fails or times out, THEN the system SHALL record the evidence item's extraction status as `FAILED` with the failure reason, SHALL NOT retry inline/synchronously, and the evidence item's own stored file and metadata SHALL remain retrievable regardless.
- R14. The system SHALL NOT expose an extracted field in any API response without its confidence value and source model identifier alongside it — no caller can mistake an extraction for a verified fact from the response shape alone.

## Evidence integrity analysis

- R15. WHEN an image evidence item is stored, THEN the system SHALL compute its EXIF/metadata fields (camera/device model, software tag, GPS if present, original-capture timestamp) in-house, without any external call.
- R16. WHEN an image evidence item is stored, THEN the system SHALL compute a perceptual hash in-house and compare it against every other evidence item previously uploaded for the same transaction hash (by any account).
- R17. IF two evidence items for the same transaction hash have a perceptual-hash distance below the configured near-duplicate threshold, THEN the system SHALL record a `DUPLICATE_SUSPECTED` integrity finding on both, each referencing the other.
- R18. IF an image's EXIF metadata is entirely absent where the content type would normally carry it, THEN the system SHALL record a `METADATA_STRIPPED` integrity finding — not an assertion that the image was edited.
- R19. The system SHALL NOT claim to detect pixel-level photo manipulation beyond the checks named in R15–R18. A true editing-detection/error-level-analysis capability is explicitly out of this task's scope (§2); adding one later is itself a new, reviewed decision for a follow-up task.

## Multi-evidence correlation

- R20. WHEN two or more evidence items (uploaded or auto-collected) exist for the same transaction hash, THEN the system SHALL create or update a single `EvidenceGraph` record for that hash linking all of them.
- R21. IF an extracted field (R11) disagrees with an on-chain fact collected for the same transaction hash (R7), THEN the system SHALL record the disagreement as a correlation finding on the `EvidenceGraph` and SHALL NOT silently prefer either value.
- R22. WHEN an API response surfaces both an on-chain fact and a conflicting extracted field for the same transaction hash, THEN the system SHALL mark the on-chain fact as authoritative.
- R23. WHEN an account requests the evidence graph for a transaction hash they have uploaded evidence for or auto-collected facts for, THEN the system SHALL return every evidence item, every extraction/integrity finding, and every correlation finding recorded for that hash.

## Access control & audit

- R24. WHEN an authenticated account calls any evidence-service endpoint, THEN the system SHALL scope every read and write to that account's own uploads and collected facts only.
- R25. WHEN an evidence upload, a poll of crypto-service's lookup endpoint, or an integrity/correlation finding is recorded, THEN the system SHALL record an audit event carrying the account, the transaction hash, and the event type.
- R26. IF a caller presents no valid JWT, THEN every non-actuator endpoint SHALL reject with 401; there is no public, unauthenticated endpoint on this service.
