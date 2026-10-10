# 7. Tasks — ordered execution plan

Coarse-grained; each task is a PR-sized unit, not decomposed into fine sub-units unless a future
pass asks for that. Every task cites the requirement(s)/design section(s) it implements. Tasks are
ordered by dependency, not necessarily by priority — §10 of `package.md` states which of these can
genuinely ship independently of crypto-service's own task 30.

## Phase 0 — Skeleton

1. **Module skeleton.** Register `services/intelligence` in the root `<modules>`; package
   structure `com.themistra.intelligence.{evidence,collection,extraction,integrity,correlation,common}`
   per `design.md` §6. `application.properties` (local/dev/staging/prod profiles), Dockerfile
   (multi-stage, distroless JRE 21), actuator health/info only as the public surface (R26).
2. **Security baseline.** OAuth2 resource server against Auth Service JWKS; `PublicEndpoints`
   allowlist test (actuator only, R26); RFC 9457 problem+json error handling mirroring every other
   service's `ProblemTypes`/`@RestControllerAdvice` convention.
3. **Flyway baseline migration.** `V1__intelligence_baseline.sql` exactly as specified in
   `design.md` §4c — all 8 tables (`evidence_items`, `collected_facts`, `extraction_results`,
   `integrity_findings`, `evidence_graphs`, `correlation_findings`, `processed_event_keys`,
   `outbox`). No application code depends on this yet; this task is schema-only plus a migration
   test confirming it applies cleanly against a fresh Testcontainers Postgres.
4. **ArchUnit boundaries.** `api → application → domain` per module; no cross-module entity
   imports (L10); the vision-API key read by exactly one class (L12, §4a). Named test:
   `shouldPreventCrossModuleEntityImports`.

## Phase 1 — Evidence upload & storage (R1-R5) — no crypto-service dependency

5. **S3 WORM client wrapper.** A thin client over S3 with Object Lock compliance mode (L5); no
   delete/overwrite operation exposed at all (R5) — the class has no method that could perform
   one, not just a guard that rejects it.
6. **Evidence upload endpoint.** Accept file + transaction hash + chain; validate size/content-type
   (R3) before any S3 call; compute SHA-256 digest; persist `EvidenceItem`, upload to S3, ack
   (R1). Named tests: `shouldStoreEvidenceFileInS3AndPersistEvidenceItemBeforeAck`,
   `shouldRejectOversizedOrDisallowedContentTypeWithoutStoring`.
7. **Dedup by digest.** Identical `<account, txHash, digest>` returns the existing item, no new
   S3 write (R2). Named test: `shouldReturnExistingItemOnByteIdenticalReupload`.
8. **Evidence read endpoints.** Metadata-only retrieval — no response DTO carries a byte-array/
   stream field (R4); ArchUnit/reflection test enforces this across every DTO in the `evidence`
   module, not just the one endpoint reviewed by hand. Access scoped to the uploading account only
   (R24). Named tests: `shouldNeverExposeRawFileBytesViaAnyApiResponse`,
   `shouldScopeEveryReadAndWriteToCallersOwnAccount`.
9. **Audit wiring for upload.** Outbox-pattern audit event on every accepted upload (R25, partial —
   extended in later phases for polling/findings events).

## Phase 2 — Automatic evidence collection (R6-R10) — **blocked on `spec/crypto-service` task 30**

> Do not start this phase's integration tests against a real crypto-service call until task 30
> (R29/L16) is actually deployed. Unit tests use a capturing fake `TransactionLookupClient`
> (agents.md) and are not blocked.

10. **`TransactionLookupClient` interface + fake.** Wraps crypto-service's
    `GET /internal/v1/transactions/{chain}/{txHash}` exactly as specified in `design.md` §4c; a
    capturing fake for unit tests now, a real `RestClient`/`WebClient`-backed implementation wired
    once task 30 ships. Auth-service client registration with `internal.crypto:write` scope
    (`package.md` §10, O1).
11. **Bare-tx-hash submission endpoint.** No file attached → persist `EvidenceItem` of kind
    `AUTO_COLLECTED`, begin polling (R6). Named test: `shouldBeginPollingOnTxHashOnlySubmissionWithNoFile`.
12. **Polling scheduler.** Backoff schedule and timeout per O6 (owner sign-off required before this
    task is considered done, `package.md` §11 Q4); stops at a terminal outcome and retains every
    fact already collected (R10). Named test: `shouldStopPollingAtTerminalOutcomeAndRetainCollectedFacts`.
13. **Collected-facts recording & reorg handling.** Append-only per milestone, never overwritten
    (R7); at most one row per `<evidence item, milestone>` (R9); reorg detection marks prior facts
    `INVALIDATED`, never deleted (R8). Named tests:
    `shouldAppendNewMilestoneFactWithoutOverwritingAnEarlierOne`,
    `shouldMarkFactsInvalidatedOnReorgDetectedByLaterPoll`,
    `shouldRecordAtMostOneRowPerEvidenceItemAndMilestone`.
14. **Audit wiring for polling.** Extends task 9's audit event to polling-derived facts (R25).

## Phase 3 — AI evidence interpretation (R11-R14) — no crypto-service dependency

15. **`VisionExtractionClient` implementation.** Real external vision-model call behind the
    interface already drafted in `design.md` §4c; API key read only by this class (L12); async,
    never inline with the upload response (agents.md's "no synchronous external call" rule).
16. **Extraction pipeline.** Triggered after an image/PDF evidence item is stored; persists
    `ExtractionResult` with model/vendor identifier, version, and per-field confidence (R11, R12).
    Named tests: `shouldSubmitImageOrPdfToVisionModelAndPersistExtractionResult`,
    `shouldRecordModelIdentifierVersionAndConfidencePerField`.
17. **Failure handling.** Vision call failure/timeout → `FAILED` status with reason, no inline
    retry, evidence item's own file/metadata stays retrievable regardless (R13). Named test:
    `shouldRecordFailedExtractionStatusWithoutInlineRetryOrBlockingEvidenceAccess`.
18. **Response-shape enforcement.** No extracted field ever serializes without confidence + model
    identifier alongside it (R14) — same reflection/ArchUnit-style enforcement as task 8's byte-
    stream check. Named test: `shouldNeverExposeAnExtractedFieldWithoutConfidenceAndModelIdentifier`.

## Phase 4 — Evidence integrity analysis (R15-R19) — no crypto-service dependency

19. **In-house EXIF extraction.** Camera/device model, software tag, GPS if present, capture
    timestamp — in-house library (O5 sign-off), no external call (R15). Named test:
    `shouldExtractExifMetadataInHouseWithoutExternalCall`.
20. **Perceptual hash + near-duplicate detection.** Computed in-house (O2 algorithm/library
    sign-off), compared against every other evidence item for the same tx hash across accounts
    (R16); below-threshold (O3 cutoff sign-off) distance records `DUPLICATE_SUSPECTED` on both
    items (R17). Named tests: `shouldComputePerceptualHashAndCompareAgainstPriorEvidenceForSameHash`,
    `shouldRecordDuplicateSuspectedFindingBelowThreshold`.
21. **Metadata-stripped finding.** Wholly-absent EXIF where the content type would normally carry
    it → `METADATA_STRIPPED` finding, explicitly not an edit assertion (R18). Named test:
    `shouldRecordMetadataStrippedFindingWhenExifWhollyAbsent`.
22. **Scope-boundary test for R19.** A standing test (not a feature) asserting no integrity
    finding type beyond `DUPLICATE_SUSPECTED`/`METADATA_STRIPPED` exists in the codebase — guards
    against a future PR silently adding an editing-detection claim without a new, reviewed spec
    decision. Named test: `shouldNeverClaimEditingDetectionBeyondTheNamedChecks`.

## Phase 5 — Multi-evidence correlation (R20-R23) — depends on Phases 1-4's own data existing

23. **`EvidenceGraph` creation/update.** One record per transaction hash, linking every evidence
    item (uploaded or auto-collected) for that hash (R20). Named test:
    `shouldCreateOrUpdateOneEvidenceGraphPerTransactionHash`.
24. **Disagreement recording.** Extracted field vs. on-chain fact mismatch → correlation finding,
    neither value silently preferred at write time (R21). Named test:
    `shouldRecordDisagreementFindingWithoutPreferringEitherValue`.
25. **On-chain-wins response shaping.** Any API response surfacing both values marks the on-chain
    fact authoritative (R22, L6). Named test: `shouldMarkOnChainFactAuthoritativeWhenFieldsConflict`.
26. **Evidence graph read endpoint.** Full graph (items + findings) for a hash the caller has
    evidence/facts for, scoped to their own account (R23, R24). Named test:
    `shouldReturnFullEvidenceGraphForAccountsOwnTransactionHash`.

## Phase 6 — Publishing, docs, contract & final hardening

27. **Outbox-published event.** `intelligence.evidence.correlation_updated` per `design.md` §4c,
    `contracts/events/intelligence/correlation-updated.v1.schema.json` (package.md §11 Q3 — confirm
    before building if not already signed off).
28. **Full audit coverage.** R25's remaining event types (findings) wired; one named test exercises
    all three triggers (upload, poll, finding). Named test: `shouldRecordAuditEventForUploadPollingAndFindings`.
29. **Auth/401 sweep.** A single parameterized test enumerating every non-actuator route and
    asserting 401 without a JWT (R26). Named test: `shouldReject401ForEveryNonActuatorEndpointWithoutAValidJwt`.
30. **ADR + spec status bump.** Record the registerWatch→new-endpoint correction as an ADR in
    `services/intelligence/docs/architecture/` (mirroring auth-service's `auth-decisions.md`
    convention); bump this spec's own `package.md` Status to `READY FOR IMPL` once every task
    above (other than the crypto-service-task-30-blocked parts of Phase 2) is built and verified
    per `package.md` §9.
