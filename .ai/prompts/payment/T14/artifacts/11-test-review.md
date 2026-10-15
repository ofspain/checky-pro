# payment · T14 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T14/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java`.

---

## Executive summary

- All five named tests are present and pass: **5/5** in the integration class, **297/297** in the full payment suite.
- The test class now correctly addresses every Phase 8 independent-review finding that was approved for Phase 9 resolution: split R14 scenarios, `sendAndWait`, positive `processed_events` awaits before negative assertions, and invoice-state assertion in AC3.
- The external-Kafka-broker dependency is now explicitly documented as the approved design (Phase 4/9), so it is treated as a known environmental constraint rather than a test gap.
- Remaining gaps are minor and mostly about surfacing additional columns/audit rows that the current ACs do not require.

---

## Test manifest verification

| Manifest test | Present | Verifies |
|---|---|---|
| `shouldDedupeDuplicateChainEventsByEventKey` | ✅ | AC1/R13 — duplicate `chain.tx.seen` leaves exactly one `Payment` row and one `processed_events` row |
| `shouldIgnoreFinalizedForAnUnknownPayment` | ✅ | AC2/R14 scenario (a) — finalized before seen creates no payment row |
| `shouldIgnoreReorgedForAnAlreadyFinalizedPayment` | ✅ | AC2/R14 scenario (b) — reorged after FINALIZED leaves state unchanged |
| `shouldIgnoreOutOfOrderOrStaleChainEvents` | ✅ | AC2/R14 scenario (c) — confirmed after FINALIZED leaves state unchanged |
| `shouldHandleTheFullForwardSequenceAndReachFinalized` | ✅ | AC3/O6 — full forward sequence reaches FINALIZED with correct payment fields and invoice PAID |

---

## Gap 1 (Low) — AC3 does not assert `addressPoisoningFlag`

- **Why it matters:** `ChainTxFinalized` carries `addressPoisoningFlag`, and `PaymentService.applyFinalized` persists it in the same atomic update. The current AC3 only checks `amount`, `from_address`, and `to_address`. A regression that dropped the flag column update would not be caught by this integration test.
- **Suggested test:** Add `assertThat(resultSet.getBoolean("address_poisoning_flag")).isFalse();` to `shouldHandleTheFullForwardSequenceAndReachFinalized`.
- **Confidence:** Low.

---

## Gap 2 (Low) — No assertion on `verification_records` for ignored events

- **Why it matters:** R14's staleness handling is supposed to record that the event was ignored (via `VerificationRecordService.recordIgnored`). The current R14 tests only assert the absence/persistence of payment state. A regression that silently dropped the audit row would not be caught.
- **Suggested test:** In each R14 scenario, after awaiting the `processed_events` row, assert that `verification_records` contains a row for the same `idempotencyKey` with `to_state`/`reason` indicating the ignored outcome. This requires joining or querying by `idempotency_key`.
- **Confidence:** Low.

---

## Gap 3 (Low) — AC1 does not assert the duplicate produced no second `processed_events` row

- **Why it matters:** The dedupe mechanism is exactly that the second message returns 0 from `insertIfNew`. The test asserts `countPaymentsForTxHash == 1`, but because `event_key` is the primary key, a duplicate `processed_events` row is impossible anyway. The real concern is whether the second delivery was consumed at all.
- **Suggested test:** The current assertion is sufficient for the AC; no change required. If stronger confidence is desired, assert that the `processed_at` timestamp did not change after the redelivery, but this is overkill.
- **Confidence:** Low.

---

## Gap 4 (Low) — No test for out-of-order `finalized` before `confirmed`

- **Why it matters:** O6 is about per-payment ordering. The manifest argues, correctly, that Kafka's cross-topic ordering guarantee is the broker's property. However, the consumer itself can still be tested for robustness against a `finalized` arriving before `confirmed` (both are on separate topics; a genuine race is possible). The current AC3 only tests the forward sequence.
- **Suggested test:** Add a separate test that seeds an invoice, sends `seen`, then sends `finalized` immediately (before the consumer has necessarily processed `confirmed`), and asserts the payment ends in `FINALIZED` and the invoice in `PAID`. This is the strongest consumer-side ordering proof available without testing broker internals.
- **Confidence:** Low.

---

## Gap 5 (Low) — External Kafka broker is a runtime dependency

- **Why it matters:** This is not a test-code gap per se, but a test-environment one. The class Javadoc now documents the dependency on `services/auth/compose.local.yaml` Kafka, and the Phase 9 resolution explicitly approved this over a Testcontainers Kafka container. CI must therefore ensure the broker is running before this test executes.
- **Suggested action:** Add a fail-fast `@BeforeAll` check that `localhost:9094` is reachable and throws a clear `IllegalStateException` if not, so a missing broker fails immediately with a meaningful message rather than a 40-second `Awaitility` timeout.
- **Confidence:** Low.

---

## Positive confirmations

- All five named tests from the manifest exist and are independent (`@Test` methods).
- The R14 method was correctly split; the named method now covers the most interesting cross-topic race, and the other two have descriptive names.
- Scenario (a) no longer seeds an unnecessary invoice.
- `sendAndWait` surfaces producer errors immediately.
- Every negative assertion first awaits the `processed_events` row, eliminating vacuous-pass risk.
- AC3 verifies both payment fields (`amount`, `from_address`, `to_address`) and the invoice state (`PAID`).
- Cleanup truncates `verification_records`, `processed_events`, `outbox`, `payments`, and `invoices`.
- `auto-offset-reset=earliest`, the dedicated group id, `WebEnvironment.NONE`, and `@EntityScan` scoping are all in place.
- `BigDecimal` amount is emitted as a quoted JSON string, matching the schema.
- No `Thread.sleep`; `Awaitility` is used throughout.
- The full suite passes: **297/297**.

---

## Build context

- `ChainEventConsumerIdempotencyAndOrderingIntegrationTest`: **5/5 passing**.
- Full `mvn -pl services/payment -am test`: **297/297 passing**.
- Kafka broker was running from `services/auth/compose.local.yaml` for this run.

---

## Recommended follow-up order

1. Add the `addressPoisoningFlag` assertion to AC3 (Gap 1).
2. Optionally assert `verification_records` ignored rows in the R14 tests (Gap 2).
3. Consider adding a true out-of-order `finalized`-before-`confirmed` consumer test (Gap 4).
4. Add a fail-fast broker-reachability check in `@BeforeAll` (Gap 5).
