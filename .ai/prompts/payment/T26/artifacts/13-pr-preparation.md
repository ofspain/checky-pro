# payment · T26 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

This artifact contains no code; it is documentation only.

**Branching:** as with every prior task in this pipeline, the work ran on the long-running
`spec/service-specs-and-ai-framework` branch, not a fresh per-task branch off `main`. `main` was
never touched and stays deployable.

**Trailer:** the template names "Claude Opus 4.8 (1M context)". The trailer below names the
model that actually did the work, following the T23–T25 precedent.

## Commit title

`payment T26: end-to-end integration test (forward flow to receipt.issued; reorg without receipt)`

## Commit message

```
payment T26: end-to-end integration test (forward flow to receipt.issued; reorg without receipt)

Add PaymentEndToEndIntegrationTest. It boots the real application against
Testcontainers Postgres and Kafka (apache/kafka:3.8.0), LocalStack 3.8 S3 with
an Object Lock bucket, and WireMock as the Crypto Service and auth token
endpoint.

The forward flow starts with an authenticated POST /api/v1/invoices through
the real security chain, then watch-ack, seen, confirmed, finalized, /attest
SIGNED, receipt in S3, ledger appended, ATTESTED, and receipt.issued. It
asserts:
- the outbound watch and attest contracts, each carrying the
  service-to-service token
- the signed digest equals the canonical digest recomputed from the stored
  receipt
- both S3 objects are COMPLIANCE-locked, and a versioned delete is refused
- the real LedgerVerifier reports the chain intact
- all four outbox events reach Kafka with the right keys and values, and are
  marked published
- a redelivered finalized event issues no second receipt

Two reorg flows (CONFIRMING to SEEN; SEEN to WATCHING with a replacement
watch) prove the backward walk is recorded and that no attest, receipt,
ledger entry or S3 object exists.

Test-only: no production code changed. Adds test-scope wiremock-standalone
3.9.1 and testcontainers localstack.

Full suite: 496/496 passing (493 baseline + 3).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed

- **Tests:**
  `services/payment/src/test/java/com/themistra/payment/PaymentEndToEndIntegrationTest.java`
  (new, 3 tests).
- **Build:** `services/payment/pom.xml`. Adds two test-scope dependencies:
  `org.wiremock:wiremock-standalone:3.9.1` and `org.testcontainers:localstack` (BOM-managed).
- **Production code:** none.
- **Pipeline artifacts:** `.ai/prompts/payment/T26/artifacts/00`–`13`, including
  `11-test-review-resolution.md`.

## Summary

T26 is the payment service's final-verification task. Every step from T05–T19 had been tested in
isolation. This is the first proof that they compose through the real wiring: HTTP security, the
Kafka listeners, the transaction boundaries, the outbox relay, S3 Object Lock, the hash-chain
ledger, and the Crypto Service HTTP calls.

**Human decisions that shaped it:**

- **Phase 2/4 decisions:**
  - Use WireMock, as the spec names, plus LocalStack and Testcontainers Kafka.
  - Use MockMvc through the real filter chain.
  - Write two reorg methods.
  - Do not run `SEEN→FINALIZED` end-to-end.
- **Phase 4 (Kimi Phase 3):**
  - **WORM:** COMPLIANCE retention, and a versioned delete is refused.
  - **Ledger:** checked by `LedgerVerifier`.
  - **Kafka:** the `payment.*` events are verified there.
  - **Digest:** independently recomputed.
  - **S3 objects:** distinct content types.
- **Phase 9 (Kimi Phase 8):**
  - **Watch request:** body checked, and bearer tokens checked on outbound calls.
  - **Events:** `invoice.created` checked on Kafka; payload values and the S3 metadata the reorg
    checks rely on are checked too.
  - **Kafka checks:** relaxed to at-least-once, with identical payloads.
- **Phase 11 (Kimi Phase 11):**
  - **Calls and events:** bearer token checked on deletes; full event values; outbox
    `published_at`.
  - **Replay:** a finalized replay is sent behind a sentinel event.
  - **Reorg back to `SEEN`:** no watch side effects.
  - **Watch contract and PDF:** the PDF is parsed with PDFBox; the watch's `expiresAt` is
    checked.

Kimi's field names were wrong at Phase 3 (the `payment.finalized` key) and at Phase 8 (the watch
and `receipt.issued` fields). Each was corrected against source before the human decided.

**Open follow-ups (human-owned, out of T26 scope):**

1. **Cross-service caveat.** This test fakes Crypto Service. The previously logged crypto-service
   gap is untouched: its `unregisterWatch` stops tracking an already-matched transaction, so in
   production `confirmed`/`finalized`/`reorged` events may never arrive. It needs a real
   two-service test, or a crypto-service fix.
2. **Earlier follow-ups, still open:**
   - G2: the partition-key drift.
   - `agents.md:39`: model generation.
   - The `tokenDecimals` primitive.
   - FU-1: the layering rule.
   - FU-2: the `@PreAuthorize` sweep.

## Testing performed

- **Full suite:** `mvn -o test` in `services/payment` passes **496/496**. Docker is required.
  The pre-task baseline was 493, and `PaymentEndToEndIntegrationTest` adds 3. That class runs in
  about 27 s.
- **LocalStack probe (Phase 2):** a throwaway LocalStack 3.8 container honoured COMPLIANCE
  retention and refused a versioned DELETE with `403`.
- **Mutation checks.** Each edit was temporary and restored, and the restore was confirmed with
  `cmp`.

  | Phase | Edit | Effect |
  |---|---|---|
  | 6 | Digest recomputation | AC1 fails |
  | 6 | Replacement watch | AC3 fails |
  | 6 | No-receipt guard applied to a receipted payment | Fails |
  | 9 | Bearer token | AC1 and AC3 fail |
  | 9 | Metadata key, plus "W2 not deleted" | AC1 and AC3 fail |
  | 11 | `published_at` inverted | AC1 fails |

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 26 (`:47`).
- **Requirements:**
  - verified: R1, R2, R6, R7, R7a, R8–R12, R17–R19, R21, R23–R25, R30
  - continuity: R13
- **LOCKED decisions honoured:**
  - verified: L1, L2, L3, L5, L7, L8, L9, L10, L11, L12, L14
  - honoured: L6, L13
- **Design:** `design.md` §4c, the Crypto Service API (`:181-189`).
- **Standing rules:**
  - `agents.md:57-58` (the integration layer)
  - `package.md:74` and `:138` (WireMock for the Crypto Service)
