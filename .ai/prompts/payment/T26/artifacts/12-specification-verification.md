# payment · T26 · Phase 12 — Specification Verification

Consumes: all prior T26 artifacts, 00–11 including `11-test-review-resolution.md`.

**Spec sources:**
- `tasks.md:47` (task 26)
- `requirements.md`: R1, R2, R6–R12, R17–R19, R21, R23–R25, R30; R13 as continuity (Phase 11
  Gap 6)
- `design.md` §4a (L1–L3, L5, L7–L12, L14) and §4c (`:181-189`, the Crypto API)
- `package.md:74`, `:138`
- `agents.md:57-58`

**Final state:**
- Commit `d80e5baa`.
- Full suite **496/496 green**.
- The test file is `services/payment/src/test/java/com/themistra/payment/PaymentEndToEndIntegrationTest.java`.
  It is called "E2E" below.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **Task 26**: Testcontainers Postgres and Kafka | Yes | E2E `:184` (`PostgreSQLContainer`), `:188` (`KafkaContainer`, `apache/kafka:3.8.0`) | all 3 | No | No |
| **Task 26**: "WireMock Crypto Service" | Yes | E2E `:199`; `pom.xml:104` (`wiremock-standalone` 3.9.1, test scope) | all 3 | No | No |
| **Task 26**: receipt in S3 | Yes | E2E `:192` (LocalStack 3.8), `:269` (`@Primary S3Client`); `pom.xml:105` | AC1 | No | The S3 stand-in is the spec-implied LocalStack, approved at Phase 4 (D2). |
| **R1**: authenticated create, watch registered | Yes | Real `InvoiceController` via MockMvc with a Bearer token | AC1 `:358` | No | Only JWKS validation is stubbed (`@MockBean JwtDecoder`; D4, F6). |
| **R2**: invoice plus `invoice.created` in one transaction | Yes | outbox row, `published_at`, Kafka record | AC1 | No | No |
| **R6**: watch-ack | Yes | invoice `watch_id` = acked W1; watch request body matches §4c | AC1 | No | There is no `CREATED`/`WATCHING` row before `SEEN`, by an earlier design decision (Phase 1 OQ5). |
| **R7 / R7a / R8**: seen, unregister, `payment.seen` | Yes | `SEEN`; `DELETE /watches/W1` with Bearer; seen record values | AC1, AC2, AC3 | No | No |
| **R9**: `CONFIRMING` | Yes | state | AC1, AC2 | No | No |
| **R10**: `FINALIZED` plus `payment.finalized` | Yes | `payment.finalized` record values; `published_at` | AC1 | No | `SEEN→FINALIZED` is not run end-to-end. Its unit coverage (F5) was rejected for E2E by the human at Phase 4. |
| **R11**: `CONFIRMING→SEEN`, recorded | Yes | state plus a `verification_records` row; no watch side effects | AC2 `:534` | No | No |
| **R12**: `SEEN→WATCHING`, recorded | Yes | state plus record; replacement watch W2; W2 not deleted | AC3 `:560` | No | No |
| **R13**: dedupe (continuity) | Yes | finalized replay plus sentinel; no second attest, receipt, ledger entry or S3 pair | AC1 | No | No |
| **R17**: exactly one receipt | Yes | 1 `receipts` row, 1 `/attest`, 1 ledger entry, 2 S3 objects, including after the replay | AC1 | No | No |
| **R18 / L2 / L11**: no receipt before `FINALIZED` | Yes | 0 attest, 0 receipts, 0 ledger, no S3 object carrying `payment-uuid` | AC2, AC3 | No | No |
| **R19 / L3**: signature only via `/attest` | Yes | 1 attest call with Bearer, `chain` and `txHash`; digest equals `receipts.digest_sha256`; digest recomputed from the stored S3 JSON | AC1 | No | No |
| **R21 / L7**: ledger chained to head | Yes | the only `prev_hash` is genesis; `LedgerVerifier.verify()` returns `Intact` | AC1 | No | No |
| **R23 / L9**: JSON and PDF, Object Lock COMPLIANCE, no overwrite | Yes | content types; PDFBox parse; metadata; per-version COMPLIANCE retention; versioned DELETE returns `403` | AC1 | No | No |
| **R24**: `ATTESTED` after sign, store and append | Yes | final state `ATTESTED` | AC1 | No | Same-transaction ordering is unit-proven in T19. Here it is composed evidence. |
| **R25 / R30 / L8**: `receipt.issued` on `payments.receipt.issued` via the outbox | Yes | record keyed `receiptUuid`; uuids, `receiptUri`, `kmsKeyId`, string amount; `published_at` | AC1 | No | Kafka checks are at-least-once (identical payloads; Phase 9 F7). |
| **L10**: zero-trust edge, outbound auth | Yes | inbound: filter chain and `@PreAuthorize`; outbound: Bearer on every Crypto call | AC1, AC3 | No | No |
| **L12**: module boundaries | Yes | no entity imports; admin JDBC; only `LedgerVerifier` and `CanonicalJson` used | — | No | No |
| **L14 / R7a**: one transaction per invoice | Yes | W1 unregistered on first `SEEN` | AC1, AC2, AC3 | No | No |
| **AC4**: no production change | Yes | diff: test class plus `pom.xml` test scope | — | No | No |
| **AC5**: suite green | Yes | 496/496 | — | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every step in task 26's chain is driven through the real
application and asserted against real infrastructure. Both reorg variants are covered. That is
two methods, because reorg dedupe makes a second reorg of one transaction unreachable (D5).

**(2) Does it satisfy every acceptance criterion?** Yes, AC1–AC5.
- Each assertion group was seen to fail under a targeted temporary edit, across Phases 6, 9
  and 11.
- Every negative assertion is non-vacuous: the triggering event is first proven consumed, using
  `processed_events` or a sentinel.

**(3) Does it violate any LOCKED decision?** No.
- No production code changed, and no `spec/` or `contracts/` file was touched.
- The test honours L6 (string amounts), L12 (JDBC only) and L13 (dummy credentials only).

**(4) Remaining risks.**
- **This is not a cross-service proof.** Crypto Service is a stand-in, so the known
  crypto-service gap is invisible here. That gap: `unregisterWatch` stops tracking an
  already-matched transaction, so `confirmed`/`finalized`/`reorged` may never arrive in
  production. It is logged as a separate, human-owned issue.
- **JWKS signature validation is stubbed.** `ResourceServerConfigTest` covers it on its own.
- **`SEEN→FINALIZED` runs only at unit level.** That was a human decision (F5).
- **Shutdown log noise.** At the end of the class, the scheduled `OutboxRelay` keeps polling
  after the Postgres container stops. It affects no result.
- **Runtime.** The class takes about 27 s with 4 containers. Docker is a hard prerequisite,
  as it is for the rest of the suite.

**PASS:** task 26's forward and reorg flows are proven end-to-end against real Postgres, Kafka,
S3 (Object Lock) and a WireMock Crypto Service (496/496 green), with no LOCKED-decision
violation and no production change.
