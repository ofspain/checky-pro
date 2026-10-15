# payment · T14 · Phase 9 — Review Resolution

**STATUS: resolved directly** — one finding required a factual correction (with citations) and
was rejected on that basis, not a product/architectural decision; every other finding was
self-evident and within already-established precedent. No fresh human gate was needed beyond
this resolution log.

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

| # | Source | Comment | Decision | Reason |
|---|--------|---------|----------|--------|
| 1 | Kimi New Finding 1 (High) | Test should use a Testcontainers Kafka container; relying on the external broker "directly contradicts the Phase 4 design-challenge approval" | **REJECTED — factually incorrect about the historical record** | Verified directly against `artifacts/04-frozen-task-brief.md`: Phase 4 **explicitly, deliberately** chose the real local broker over a Testcontainers Kafka image (its own reworded Decision 1, "neither this nor that precedent uses one") and **explicitly rejected** Kimi's own earlier Phase 3 Finding 7 (`AdminClient` topic pre-creation) for the identical reason. Kimi's own Phase 8 claim contradicts Kimi's own Phase 3 review once Phase 4 corrected it. The real, indirect cause: this test class's own Javadoc contained a self-contradictory sentence ("does not use a Testcontainers Kafka image either... with a real Kafka Testcontainer swapped in here instead") — a genuine drafting error, now fixed, that plausibly misled this review. No `KafkaContainer` added. |
| 2 | Kimi New Finding 2 (Medium) | `kafkaTemplate.send(...)` is fire-and-forget | **ACCEPTED** | Real, cheap improvement — surfaces a producer-side failure immediately rather than only via a later, less-specific `Awaitility` timeout. A `sendAndWait` helper added, used everywhere. |
| 3 | Kimi New Finding 3 (Medium) | Negative assertions don't first confirm the stimulus event was actually consumed | **ACCEPTED** | Real strengthening — without it, a lost/never-delivered message would make the negative assertion pass vacuously. Each of the three R14 scenarios now awaits `processedEventRepository.existsById(...)` first, then asserts the state invariant. **Not applied to R13's own dedupe test**: its own redelivery check has no analogous "first confirm consumption" step available, since the `processed_events` row already existed *before* the second send (from the first, successful delivery) — a lost second message and a correctly-deduped second message are structurally indistinguishable at the DB level, so there's nothing extra to await there. |
| 4 | Kimi New Finding 4 (Medium) | AC3 doesn't verify the invoice-state transition | **ACCEPTED** | Real gap — `InvoiceService.markOutcomeIfOpenOrExpired` (T13) is as much a part of `onChainTxFinalized`'s own real wiring as the payment row's own fields. Added `fetchInvoiceState(...)` and an assertion that the seeded invoice reaches `PAID`. |
| 5 | Kimi New Finding 5 (Low) | Pre-create Kafka topics via `AdminClient` | **REJECTED (re-raised, already dispositioned)** | Identical to Phase 3's own Finding 7, already rejected at Phase 4 for the same reason (the real broker's own compose config already confirmed to have auto-create enabled; the established precedent doesn't do this either). No new information in the re-raise. |
| 6 | Kimi New Finding 6 (Low) | Clarify that `spring.task.scheduling.enabled=false` is a no-op here | **ACCEPTED, documentation only** | Already disclosed in Phase 6's own implementation notes; the class-level Javadoc strengthened further while fixing Finding 1's own drafting error in the same paragraph. |
| 7 | Kimi confirming self-review Finding 1 | Unused `seedInvoice()` in the unknown-payment scenario | **ACCEPTED** | Already my own finding, independently confirmed. Removed; the scenario now uses a bare `UUID.randomUUID()` for `watchId`/`invoiceUuid` in the event JSON, needing no seeded row at all. |
| 8 | Kimi confirming self-review Finding 2, with a concrete naming suggestion | Split the three bundled R14 scenarios into independent methods | **ACCEPTED, exact naming adopted** | Real diagnostic-granularity improvement. Split into `shouldIgnoreFinalizedForAnUnknownPayment`, `shouldIgnoreReorgedForAnAlreadyFinalizedPayment`, and `shouldIgnoreOutOfOrderOrStaleChainEvents` (keeping the exact name `package.md` §8 maps to R14, per Kimi's own suggestion — assigned to the cross-topic-race scenario, the most interesting of the three). |

## Exact changes made

- **Class-level Javadoc** — fixed the self-contradictory sentence that plausibly caused Finding
  1's own misreading; clarified Finding 3's own corrected mechanism and Finding 6's own
  documentation note in the same paragraph.
- **New `sendAndWait(String topic, String key, String json)`** — wraps every
  `kafkaTemplate.send(...)` call, waiting on the real producer future (5s timeout) instead of
  fire-and-forget.
- **New `fetchInvoiceState(UUID invoiceUuid)`** — raw JDBC, mirrors `fetchPaymentState`'s own
  shape.
- **New `FinalizedPayment(UUID watchId, UUID invoiceUuid)` record** — `finalizeARealPayment`'s
  own return type, widened from a bare `UUID watchId` so callers can also reach the invoice for
  the new AC3 assertion.
- **`shouldIgnoreOutOfOrderOrStaleChainEvents`** split into three independent `@Test` methods
  (see row 8); each now awaits real consumption before asserting the negative outcome (row 3);
  the unknown-payment scenario no longer seeds an unused invoice (row 7).
- **`shouldHandleTheFullForwardSequenceAndReachFinalized`** gains the invoice-state assertion
  (row 4).

## Verification

`mvn -o compile`, `mvn -o test-compile`: clean. `mvn -o test -Dtest=ChainEventConsumerIdempotencyAndOrderingIntegrationTest`:
**5/5 passing, confirmed stable across 3 consecutive runs** (9 total across Phases 6/7/9 for the
pre-split version, now 3 more for the restructured one). `mvn -o test` (full suite): **297/297
passing** (292 baseline + 5 new, up from 3 before the split).
