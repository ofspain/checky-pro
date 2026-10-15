# payment · T18 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi)

The Phase 11 artifact above is Kimi's own real review output. This companion note records the
disposition — the pipeline's test-review phase doesn't have a separate numbered resolution
artifact in this task's own prompt set, so it's recorded here alongside it, mirroring how every
other Kimi phase in this task was dispositioned against real source before acting.

| Gap | Severity | Disposition | Reason |
|---|---|---|---|
| 1 — no direct test for PDF-rendering `IOException` wrapping | Low | **ACCEPTED, not fixed** | Kimi's own writeup already identifies the difficulty: `ReceiptPayload`'s compact constructor blocks every null-field path that might otherwise be used to force a renderer failure, and introducing a test-only seam (extracting `renderPdf` to a package-visible method, or subclassing) would be intrusive for a Low-confidence gap. Matches this session's own established precedent (T16/T17) of rejecting low-value intrusive tests. |
| 2 — `contentType` not asserted | Low | **ACCEPTED, fixed** | Added `contentType("application/json")`/`contentType("application/pdf")` assertions to `shouldStoreReceiptInS3ObjectLockWorm`. Mutation-verified: temporarily changed `CONTENT_TYPE_JSON` to `"text/plain"`, confirmed the test failed red, reverted, confirmed a clean diff. |
| 3 — `copyObject` overwrite path not forbidden | Low | **ACCEPTED, fixed** | Broadened `shouldNeverCallDeleteOrAnyOverwriteApi` with `verify(s3Client, never()).copyObject(...)` — `copyObject` can overwrite an existing object's content/metadata just as `deleteObject` removes one, so it falls under AC3's "no delete/overwrite" text even though `ReceiptStore` never calls it today. |
| 4 — empty-prefix edge case untested | Low | **REJECTED, no action** | Verified `ReceiptProperties.prefix()` is `@NotBlank` (`ReceiptProperties.java:22`) — Spring's bean validation already prevents a blank prefix from ever binding in any real environment (local, staging, prod alike). The scenario Kimi describes is only reachable by hand-constructing `ReceiptProperties` directly in a test, bypassing that validation — testing it would exercise a state the real application can never reach, not a real gap. |

No finding was rejected without a documented reason.

## Exact changes made

- **`ReceiptStoreTest.shouldStoreReceiptInS3ObjectLockWorm`** — two new assertions
  (`contentType()` on each captured request).
- **`ReceiptStoreTest.shouldNeverCallDeleteOrAnyOverwriteApi`** — one new `verify(..., never())`
  line for `copyObject`.

No production code changed this phase.

## Verification performed this phase

- Mutation-verified the new `contentType` assertion: temporarily changed
  `ReceiptStore.CONTENT_TYPE_JSON` to `"text/plain"`, confirmed
  `shouldStoreReceiptInS3ObjectLockWorm` failed red, reverted, confirmed `git diff` was empty.
- `mvn -o -pl services/payment -am test -Dtest=ReceiptStoreTest`: **12/12 passing** (same method
  count — both fixes strengthened existing test bodies, not new methods).
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **386/386 passing**, unchanged.
