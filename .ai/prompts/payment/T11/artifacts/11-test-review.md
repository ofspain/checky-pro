# payment · T11 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T11/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `PaymentServiceTest.java`, `ChainEventConsumerTest.java`, `ChainEventDtoDeserializationTest.java`.

---

## Executive summary

- `PaymentServiceTest` — **17/17 passing**. Strong coverage of the three-way confirmed split, finalize-from-either-state, existing-payment-row guard, non-OPEN invoice guard, and AC9.
- `ChainEventConsumerTest` — **10/10 passing**. Covers dedupe on all three topics, `unregisterWatch` ordering, unknown-watch drop, and malformed-payload handling for `seen`/`confirmed`.
- `ChainEventDtoDeserializationTest` — **3/3 passing**. Verifies all three DTOs deserialize, including `BigDecimal` amount from a decimal string with full precision.
- Full non-Docker suite — **226/226 passing**.
- Remaining gaps are mostly integration-level (real repository queries, real Kafka, real Postgres round-trip), which are carried forward due to Docker unavailability.

---

## Gap 1 — `PaymentRepository` atomic queries are mocked, not exercised against a real database

- **Why it matters:** The correctness of `existsByInvoiceIdAndStateIn`, `findCurrentState`, `markConfirmingIfSeen`, `refreshConfirmationsIfGreaterAndCurrentlyConfirming`, and `markFinalizedIfCurrentState` rests on Spring Data JPQL/native query derivation. A typo in a parameter name or a subtle JPQL issue would only surface when the repository runs against Hibernate/Postgres.
- **Suggested test:** Add an integration test (Testcontainers `@DataJpaTest` or full `@SpringBootTest`) that seeds a real `Invoice` and `Payment` row and exercises each repository method, asserting both the returned row-count and the actual persisted state.
- **Confidence:** Medium.

---

## Gap 2 — `ChainEventConsumer.onChainTxFinalized` malformed-payload path is not tested

- **Why it matters:** `ChainEventConsumerTest` proves malformed payloads on `seen` and `confirmed` are caught and dropped. A parallel test for `finalized` would complete the set and guard against a future change that accidentally allows the exception to propagate from the third listener.
- **Suggested test:** Add `shouldLogAndDropAMalformedFinalizedPayloadWithoutPropagating` mirroring the existing two malformed-payload tests.
- **Confidence:** Low.

---

## Gap 3 — `PaymentServiceTest` does not assert timestamp fields on the created `Payment`

- **Why it matters:** `Payment.createSeen` sets `seenAt`, `createdAt`, and `updatedAt` from the `Clock`. A regression that stopped setting one of these (or set it from `Instant.now()` instead of the fixed clock) would not be caught by the current tests.
- **Suggested test:** Extend `shouldTransitionToSeenOnChainTxSeen` to assert `saved.getSeenAt()`, `saved.getCreatedAt()`, and `saved.getUpdatedAt()` all equal `NOW`.
- **Confidence:** Low.

---

## Gap 4 — `ChainEventConsumerTest` does not verify the exact deserialized event is passed to `PaymentService`

- **Why it matters:** The delegation tests use `any()` matchers. A bug that passed the wrong event object (e.g., always a new empty event, or a seen event to `applyConfirmed`) would not be caught.
- **Suggested test:** Use `ArgumentCaptor<ChainTxConfirmed>`/`ChainTxFinalized` in `shouldDelegateChainTxConfirmedToPaymentService` and `shouldDelegateChainTxFinalizedToPaymentService` and assert the captured object's fields match the JSON payload.
- **Confidence:** Low.

---

## Gap 5 — DTO deserialization tests do not exercise missing/unknown fields

- **Why it matters:** The contract schemas specify `additionalProperties: false` and a strict `required` list. A producer that omits a required field or adds an extra field should fail deserialization, but the current tests only verify the happy path.
- **Suggested test:** Add a negative test per DTO: (a) JSON missing a required field should throw, and (b) JSON with an unknown property should throw when `ObjectMapper.FAIL_ON_UNKNOWN_PROPERTIES` is enabled (the Spring Boot default).
- **Confidence:** Low/Medium.

---

## Gap 6 — No test verifies `applyFinalized` with `addressPoisoningFlag = true`

- **Why it matters:** R16 requires persisting and surfacing the address-poisoning flag. The current test `shouldPersistRealObservedAmountAndAddressesOnFinalize` asserts the flag is `false`. A regression that hardcoded the flag to `false` would not be caught.
- **Suggested test:** Add `shouldPersistAddressPoisoningFlagWhenTrue` with a finalized event carrying `addressPoisoningFlag=true` and assert both the repository update argument and the published payload carry `true`.
- **Confidence:** Low.

---

## Gap 7 — No test exercises the outbox publish for `payment.finalized` from the `SEEN` path

- **Why it matters:** `shouldTransitionToFinalizedFromSeenWhenNeverConfirmed` verifies the verification record, but it does not verify that the outbox event is published. A regression that accidentally skipped outbox publication in the SEEN-only path would be missed.
- **Suggested test:** Extend `shouldTransitionToFinalizedFromSeenWhenNeverConfirmed` to verify `outboxPublisher.publish("payment-finalized", ...)` is called.
- **Confidence:** Low.

---

## Positive confirmations

- `PaymentServiceTest` uses a real `PaymentStateMachine`, not a mock, so the tests genuinely validate that the service and the state machine agree.
- The existing-payment-row guard is directly tested with a mocked `existsByInvoiceIdAndStateIn` returning `true`.
- The full non-terminal state set passed to `existsByInvoiceIdAndStateIn` is asserted explicitly.
- `ChainEventConsumerTest` uses `InOrder` to prove `unregisterWatch` runs strictly after `applySeen`, not merely that both are called.
- Malformed payloads on `seen` and `confirmed` are proven not to propagate.
- `ChainEventDtoDeserializationTest` uses inline JSON that mirrors the real contract schemas field-for-field and cites them in comments.
- `shouldPersistRealObservedAmountAndAddressesOnFinalize` verifies the exact amount/address values passed to the atomic update and the published payload.
- The stale-finalized path asserts no outbox event is published.

---

## Recommended follow-up order

1. Add the missing `finalized` malformed-payload test (Gap 2).
2. Add timestamp assertions to the seen-creation test (Gap 3).
3. Use argument captors in consumer delegation tests (Gap 4).
4. Add a positive address-poisoning-flag test (Gap 6) and the SEEN→FINALIZED outbox assertion (Gap 7).
5. Add negative DTO deserialization tests for missing/unknown fields (Gap 5).
6. Run repository-level integration tests once Docker is available (Gap 1).
7. Fix the unrelated T08 `InvoiceRepositoryIntegrationTest` transaction issue so the full build is green.
