# payment · T11 · Phase 11 — Test Review Resolution

Consumes `artifacts/11-test-review.md` (Kimi 2.7). 6 of 7 findings fixed (all cheap, mechanical
test additions); 1 deferred, consistent with this task's own established Docker-unavailable
precedent.

## Gap 1 — repository atomic queries only exercised via mocks

**ACKNOWLEDGED, deferred — not written this phase.** A genuine, valuable suggestion (Medium
confidence), but writing a full Testcontainers integration test for all 5 `PaymentRepository`
methods is a substantial undertaking, and this task already carries the same disclosed,
Docker-unavailable limitation every prior task (T08/T09/T10) has for its own equivalent
repository-level proof. Logged as a carried-forward need, same as those.

## Gap 2 — no malformed-payload test for `chain.tx.finalized`

**ACCEPTED and FIXED.** Added `shouldLogAndDropAMalformedFinalizedPayloadWithoutPropagating`,
completing the set alongside the existing `seen`/`confirmed` versions.

## Gap 3 — no timestamp assertions on the created `Payment`

**ACCEPTED and FIXED.** Extended `shouldTransitionToSeenOnChainTxSeen` to assert `seenAt`/
`createdAt`/`updatedAt` all equal the fixed `Clock`'s own `NOW`.

## Gap 4 — consumer delegation tests used bare `any()` matchers

**ACCEPTED and FIXED.** `shouldDelegateChainTxFinalizedToPaymentService` now uses an
`ArgumentCaptor`, asserting the captured event's own `chain`/`txHash`/`amount`/`confirmations`
match the real JSON payload (the `confirmed` delegation test already used a field-asserting
`argThat`, confirmed on re-read — no change needed there).

## Gap 5 — no negative DTO deserialization tests

**ACCEPTED, part (b) only — part (a) deliberately not added, with rationale.** Added
`shouldRejectAnUnrecognizedPropertyNotInTheRealSchema`, confirmed empirically (Jackson's own
`FAIL_ON_UNKNOWN_PROPERTIES` defaults to `true` with zero extra configuration). Kimi's own part
(a) — a missing required field should throw — was **not** added: these are plain records with
no `@JsonProperty(required = true)` annotations, so a missing field currently deserializes to
`null`/a primitive default, not an exception. Adding that validation now would be new behavior
beyond this task's own frozen brief, not a test of behavior that already exists.

## Gap 6 — no test for `addressPoisoningFlag = true`

**ACCEPTED and FIXED.** Added `shouldPersistAddressPoisoningFlagWhenTrue`.

## Gap 7 — no outbox-publish assertion on the `SEEN`-only finalize path

**ACCEPTED and FIXED.** Extended `shouldTransitionToFinalizedFromSeenWhenNeverConfirmed` to
verify `outboxPublisher.publish("payment-finalized", ...)` is called.

## Verification performed this phase

- `mvn -pl services/payment -am test-compile` — clean.
- `mvn -pl services/payment -am test -Dtest='PaymentServiceTest,ChainEventConsumerTest,ChainEventDtoDeserializationTest'`
  — 33/33 (up from 30).
- Full non-Docker suite: **229/229 passing** (up from 226).

## Net result

6 real gaps fixed, 1 deferred with clear, precedent-consistent rationale. Proceeding to
Phase 12.
