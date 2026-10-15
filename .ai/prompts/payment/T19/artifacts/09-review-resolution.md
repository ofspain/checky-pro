# payment · T19 · Phase 9 — Review Resolution

**STATUS: resolved** — disposition approved via `AskUserQuestion`, applied below.

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

| # | Source | Comment | Decision | Reason |
|---|--------|---------|----------|--------|
| 1 | Self-review Finding 1 | `LedgerService.append` had no transaction boundary of its own | **ALREADY FIXED** (Phase 6, before Kimi's review ran) | `@Transactional` added; confirmed intact by Kimi's own independent re-inspection. |
| 2 | Self-review Finding 2 / Kimi Phase 8 Finding 3 | AC4's concurrency guarantee not proven under realistic (Spring-managed) load | **ACCEPTED, deferred to Phase 10 as a testing-strategy decision** | Both reviews converge on the same conclusion: the bare-`ExecutorService` harness that exhibited the unroot-caused anomaly is not representative of the real production concurrency path (Spring-managed Kafka listener threads). Phase 10 must make an explicit choice (argue correctness from the transaction-scoped lock's own documented semantics, vs. attempt a genuinely representative concurrent test) rather than silently skip proving AC4. |
| 3 | Kimi Phase 8 Finding 1 | Advisory lock acquired after the `Receipt` insert, not as the transaction's first statement, deviating from the frozen brief's own illustrative order | **REJECTED as a correctness concern, ACCEPTED as a documentation gap — now closed** | Verified directly against Postgres's own documented behavior: `pg_advisory_xact_lock` releases only at the *enclosing transaction's* commit or rollback, never early because some inner "critical section" finished — so the lock's exact acquisition point within one transaction has zero effect on its hold duration or serialization guarantee. Moving the call would relocate nothing. Strengthened `LedgerService`'s own Javadoc to state this explicitly, rather than code-move for no functional gain (which would also re-expose `ReceiptService` to advisory-lock mechanics this design deliberately encapsulates). |
| 4 | Kimi Phase 8 Finding 2 | No committed automated tests for any T19 code path yet | **NOTED, Phase 10's own job** | Correctly deferred per the standard rule — this task is not test-only. |
| 5 | Kimi Phase 8 Finding 4 | `issueIfEligible` is not safe to call twice for the same payment | **ACCEPTED, with corrected reasoning** | Verified `receipts.payment_id` is `UNIQUE` — a genuine second DB-committed Receipt for one payment cannot actually occur (the second insert would hit the constraint, caught, converted to `Failed`); R17's "exactly one receipt" holds at the system-of-record level regardless. The real, narrower risk a retry introduces is a *second signed S3 write* before ever reaching that constraint check — compounding the already-disclosed S3-orphan-window risk, not creating a second recorded receipt. Added a Javadoc warning to `persistSignedReceipt`, not a new repository guard (which would redundantly re-implement protection the `UNIQUE` constraint already provides). |
| 6 | Kimi Phase 8 Finding 5 | Advisory-lock key uses `hashtext(...)::bigint`, not a literal constant — framed as a cross-Postgres-version compatibility risk | **REJECTED** | This is the *exact* expression Kimi's own Phase 3 Finding 3 recommended verbatim ("a namespaced 64-bit constant such as `hashtext('themistra.payment.ledger.append')::bigint`"), now criticized by Kimi's own Phase 8 review as not being a fixed constant — a direct self-contradiction across review phases, the same pattern already logged in [[verify-kimi-findings-against-source]]. Advisory locks carry no persisted state that could go stale across a version upgrade (every caller always recomputes the expression fresh); there is no real cross-version compatibility risk to guard against. |
| 7 | Kimi Phase 8 Finding 6 | Physical `receipt-issued.v1.schema.json` contract file still missing | **NOTED, no action** | Already correctly, deliberately deferred to task 24 per Phase 4's own disposition of Finding 1 — confirmed consistent with the identical precedent for `invoice.created`/`payment.seen`/`payment.finalized`, none of which has a contract file yet either. |
| 8 | Kimi Phase 8 Finding 7 | `ReceiptStore.toCanonicalMap` is public and returns a mutable map | **ACCEPTED** | `toCanonicalMap` now returns an unmodifiable map; `ReceiptService.buildLedgerPayload` copies into a new mutable `LinkedHashMap` before adding the two S3 fields. Verified `ReceiptStore`'s own internal use of the returned map (`CanonicalJson.serialize`, `renderPdf`) never mutates it — safe to tighten. |
| 9 | Kimi Phase 8 Finding 8 | `receipts.s3_key` (`VARCHAR(256)`) has no length guard against a long configured prefix | **ACCEPTED** | Added `@Size(max = 219)` to `ReceiptProperties.prefix` — `256 - 36 (UUID) - 1 (normalizedPrefix's possible trailing "/") = 219`, the longest prefix that can never overflow the column regardless of which UUID is generated. |

No finding was rejected without a documented reason.

## Exact changes made

- **`ReceiptStore.toCanonicalMap`** — returns `Collections.unmodifiableMap(map)` instead of the
  raw `LinkedHashMap` (row 8).
- **`ReceiptService.buildLedgerPayload`** — copies the now-unmodifiable map into a new
  `LinkedHashMap` before adding `s3Bucket`/`s3KeyPrefix` (row 8).
- **`ReceiptService.persistSignedReceipt`** — Javadoc gains the "not safe to call twice" warning
  (row 5).
- **`LedgerService`** — Javadoc gains the Postgres-semantics explanation for why the lock's
  acquisition point within one transaction is functionally irrelevant (row 3).
- **`ReceiptProperties.prefix`** — gains `@Size(max = 219)` (row 9).

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`/`test-compile`: clean.
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **386/386 passing**, unchanged
  — no regression, no new test content this phase (Phase 10's own job, per rows 2/4).

## Carried-forward disclosed gaps (not fixed, by design)

- No committed test exists yet for any T19 path (row 4) — Phase 10.
- AC4's concurrency guarantee needs an explicit Phase 10 testing-strategy decision (row 2) —
  argue from the lock's own documented transaction-scoped semantics, or attempt a genuinely
  representative concurrent test using real Spring-managed threads, not a bare `ExecutorService`.
- The S3-orphan-window risk (already disclosed in `ReceiptService`'s own class Javadoc since
  Phase 6) is now explicitly cross-referenced from the "not safe to retry" warning (row 5) —
  still not solved in application code, by design (L9 bans a delete path).
