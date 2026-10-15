# payment · T18 · Phase 9 — Review Resolution

**STATUS: resolved** — disposition approved via `AskUserQuestion`, applied below.

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

| # | Source | Comment | Decision | Reason |
|---|--------|---------|----------|--------|
| 1 | Self-review Findings 1/2, confirmed by Kimi Phase 8 | Duplicate `ReceiptStore` bean registration; PDF-rendering exception type inconsistency | **ALREADY FIXED** (Phase 7, before Kimi's review ran) | Both confirmed fixed by Kimi's independent re-inspection — no further action. |
| 2 | Self-review Finding 3, confirmed/accepted by Kimi Phase 8 | Only `signature` is defensively wrapped against PDF page overflow | **ACCEPTED, no action** | Both self-review and Kimi judged this unreachable with well-formed inputs; Kimi's own confidence was Low. |
| 3 | Kimi Phase 8 New Finding 1 (High) | No `ReceiptStoreTest` at all | **NOTED, Phase 10's own job** | Correctly deferred per the standard rule — this task is not test-only. |
| 4 | Kimi Phase 8 New Finding 2 (Medium) | `properties.prefix()` is not validated/normalized to end with `/` | **ACCEPTED** | Verified `ObservationSnapshotStore` (crypto-service) has the identical unvalidated gap, so this isn't a regression introduced by T18 — but the fix is cheap and purely defensive, so applied anyway rather than left matching an imperfect precedent. Added a private `normalizedPrefix(String)` appending `/` only when missing. |
| 5 | Kimi Phase 8 New Finding 3 (Low) | No test verifies the exact canonical JSON bytes stored | **NOTED, Phase 10's own job** | New test content, deferred per the standard rule. |
| 6 | Kimi Phase 8 New Finding 4 (Low) | No end-to-end integration test exercises `ChainEventConsumer → ReceiptService → ReceiptStore → S3` | **ACCEPTED, deferred to a future task** | Kimi's own text: "acceptable for Phase 8... should be closed in a future phase" — `ReceiptStore` has no caller yet (confirmed scope, Phase 0/2); a real end-to-end test belongs once T19 wires it into the live flow, mirroring the identical reasoning T17's own Phase 11 Gap 5 already used for the same class of deferred integration test. |
| 7 | Kimi Phase 8 New Finding 5 (Low) | `.objectLockLegalHoldStatus(OFF)` is set explicitly but not required by any AC | **ACCEPTED** | Kimi's own text: "could be omitted with no behavioral change." Removed — keeps the request to exactly what AC2 requires (`objectLockMode`, `objectLockRetainUntilDate`). |

No finding was rejected.

## Exact changes made

- **`ReceiptStore.store(...)`** — now calls a new private `normalizedPrefix(properties.prefix())`
  instead of using `properties.prefix()` directly, guaranteeing the computed `s3KeyPrefix` always
  has exactly one `/` between the configured prefix and `receiptUuid`, regardless of whether the
  operator-configured value already ends with one (row 4).
- **`ReceiptStore.buildPutRequest(...)`** — removed the explicit
  `.objectLockLegalHoldStatus(ObjectLockLegalHoldStatus.OFF)` call and its now-unused import
  (row 7).

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`/`test-compile`: clean.
- A throwaway `javac`/`java` check (not committed) confirmed `normalizedPrefix("receipts")` and
  `normalizedPrefix("receipts/")` both produce `"receipts/"` — the fix handles both the
  already-correct local default and a hypothetically misconfigured value identically.
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **374/374 passing**, unchanged
  — no regression, no new test content added this phase (Phase 10's own job, per rows 3/5).

## Carried-forward disclosed gaps (not fixed, by design)

- No `ReceiptStoreTest` yet (row 3) — Phase 10.
- No exact-JSON-bytes assertion yet (row 5) — Phase 10.
- No LocalStack/end-to-end integration test (row 6) — deferred to a future task, once a real
  caller of `ReceiptStore` exists.
