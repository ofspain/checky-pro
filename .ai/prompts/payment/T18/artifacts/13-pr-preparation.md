# payment · T18 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** same established practice as every prior task this session — executed
directly on the long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task
branch off `main`. `main` itself was never touched and stays deployable. The attribution trailer
below uses this session's own current identity (Claude Sonnet 5), not the generic placeholder
text baked into this prompt template's own header.

## Commit title

`payment T18: ReceiptStore - S3 Object Lock WORM write (R23, L9)`

## Commit message

```
payment T18: ReceiptStore - S3 Object Lock WORM write (R23, L9)

Adds ReceiptStore.store(ReceiptPayload, AttestationResult.Signed) - the second of L2's three
durable steps ("the signature is obtained, the receipt is stored in S3, and the ledger entry is
appended"). Writes the canonical 15-field receipt (the 12 O4 fields plus signature/kmsKeyId/
signedAt, reusing T16's CanonicalJson.serialize) and a PDFBox-rendered single-page PDF of the
same fields to the S3 Object Lock bucket in compliance mode, with a 7-year per-object retention
decided directly by the user via AskUserQuestion before design started.

Kimi's own Phase 3 design challenge caught a real schema mismatch before any code was written:
receipts.s3_key is a single column, but this task produces two S3 objects. ReceiptStorageLocation
is shaped (bucket, s3KeyPrefix, jsonKey, pdfKey) so a future caller (T19, unbuilt) can store the
prefix in that column while keeping both full keys available in the same call chain. JSON is
written before the PDF; both objects carry matching receipt-uuid/invoice-uuid/payment-uuid
metadata so a partial write - an accepted, documented operational risk inherent to any
two-object S3 write with no multi-object transaction - can be identified and reconciled by ops.

No defensive pre-write existence check: the no-duplicate-write guarantee is structural
(receiptUuid is generated fresh exactly once per successful attest() call, T17), not
SDK-enforced - L9's own text frames a second write as a caller bug, not a condition to detect.
Any failure, whether a real S3 SdkException or a PDF-rendering IOException, propagates uniformly
wrapped as a new unchecked ReceiptStorageException - the deliberate opposite of crypto-service's
own ObservationSnapshotStore, whose S3 write is supplementary and never throws; this task's
write is the load-bearing guarantee R23's "SHALL store" text exists to make durable.

Self-review (Phase 7) found and fixed two issues before Kimi's independent review ran: ReceiptStore
was registered as a Spring bean twice (both @Component and an explicit @Bean factory method,
relying on an undocumented override-resolution order rather than a deliberate single construction
path - fixed by removing @Component, matching ObservationSnapshotStore's own precedent exactly),
and PDF-rendering failures used a different exception type than S3 failures (unified). Kimi's
Phase 8 independent review added a real, cheap fix (properties.prefix() is now normalized to
guarantee a trailing delimiter, closing a misconfiguration trap the sibling ObservationSnapshotStore
has unfixed) and confirmed the rest. ArchitectureTest's own blanket float/double ban across the
whole package (L6) - not a money-specific check - caught the PDF layout constants during Phase 6;
fixed by declaring them int and relying on Java's automatic widening at PDFBox's own API call sites.

ReceiptStoreTest: 12/12 passing, including a genuine PDFBox round-trip (render, then re-extract
text via PDFTextStripper) and an exact-bytes comparison of the stored canonical JSON against an
independently-built expectation - not merely "the JSON parses." Full suite: 386/386 passing (374
pre-task baseline + 12 new).

Disclosed, not solved here: no end-to-end integration test exercises ReceiptStore against a real
or LocalStack-backed S3 - deferred to a future task once T19 wires a real caller into the live
flow, the identical class of deferral T17's own Phase 11 Gap 5 already used.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Production code:**
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStorageLocation.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStorageException.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStoreConfig.java` (new).
- `services/payment/src/main/java/com/themistra/payment/common/config/ReceiptProperties.java` —
  added `prefix` and `region`, both `@NotBlank`.
- `services/payment/pom.xml` — added `org.apache.pdfbox:pdfbox:3.0.3`.
- `services/payment/src/main/resources/application.properties` — two new local-only fake
  defaults (`themistra.payment.receipt.prefix`/`.region`).

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/receipt/ReceiptStoreTest.java` (new) —
  12 tests.
- `services/payment/src/test/java/com/themistra/payment/common/config/ConfigPropertiesBindingTest.java`
  — the four existing `ReceiptConfig`-based tests updated to supply the two new required fields
  (mechanical fix, not new test content); the valid-binding test gained two new field assertions.

No migration file — this task adds no entity, no DB interaction at all (confirmed scope,
Phase 0/2: the `Receipt` row/`receipts` table insert is T19's own job, once `ReceiptStore`'s
return value gives it a real S3 key to persist).

## Summary

T18 is the first task in this service to actually write anything to S3, and the first to need
Apache PDFBox anywhere in the codebase. The hardest finding — surfaced by Kimi's own Phase 3
design challenge before any implementation code existed — was that the `receipts` table's single
`s3_key` column cannot represent the two objects this task produces; reshaping
`ReceiptStorageLocation` to carry a prefix plus both full keys resolved it cleanly, without
touching `spec/` or requiring a new migration in this task. The self-review phase caught a subtle
Spring wiring bug (a bean registered twice) that happened to resolve harmlessly only by luck of
an undocumented framework resolution order — a reminder that "the tests pass" and "the wiring is
unambiguous by design" are not the same claim.

## Testing performed

- `mvn -o -pl services/payment -am compile`, `mvn -o -pl services/payment -am test-compile`:
  clean throughout every phase (after two real build breaks diagnosed and fixed at Phase 6: the
  `ConfigPropertiesBindingTest` config-binding tests needing the two new required fields, and
  `ArchitectureTest`'s blanket float/double ban catching the PDF layout constants).
- `mvn -o -pl services/payment -am test -Dtest=ReceiptStoreTest`: **12/12 passing**.
- A throwaway JUnit smoke test (Phase 6, written/run/deleted before that commit) and a temporary
  Spring-context probe (Phase 7, written/run/reverted via `git checkout` before that commit) each
  verified real behavior empirically rather than by inspection alone — neither is part of the
  committed test suite.
- Three separate mutation-testing passes this task (Phase 10: `normalizedPrefix`, the canonical
  JSON field mapping; Phase 11: `CONTENT_TYPE_JSON`) — each real defect introduced via `Edit`,
  confirmed red, reverted via `Edit`, confirmed a clean `git diff` and green suite.
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`): **386/386 passing** (374 pre-task baseline + 12 new).

## Specification references

- **Task:** T18 — S3 WORM store (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R23.
- **LOCKED decisions:** L9 (resolved fully this task — 7-year retention decided directly by the
  user; no-overwrite enforcement relies on Object Lock + a deterministic key, stress-tested and
  confirmed by Kimi's own Phase 3 Finding 9); L13 (context, continuity — no credential in code);
  L12 (continuity — no cross-module entity import); L6 (continuity — no float/double anywhere in
  the package, confirmed the hard way via `ArchitectureTest` during implementation).
- **Product decision (not a numbered requirement, decided directly by the user via
  `AskUserQuestion`):** Object Lock compliance-mode retention is 7 years, computed as a fixed
  365-day-per-year duration from the write instant.
