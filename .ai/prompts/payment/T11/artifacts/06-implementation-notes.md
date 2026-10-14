# payment · T11 · Phase 6 — Implementation Notes

Implemented per `artifacts/05-implementation-plan.md`. No test files written this phase.

## Files created

- `Payment.java` — maps every `payments.payments` column; no mutator methods (every state/field
  change is an atomic conditional `UPDATE`, mirroring T08's own corrected lesson).
- `PaymentRepository.java` — `findByChainAndTxHash`, plus 3 atomic conditional `UPDATE` queries
  (`markConfirmingIfSeen`, `refreshConfirmationsIfGreaterAndCurrentlyConfirming`,
  `markFinalizedIfCurrentState`) — bind-parameter style throughout, mirroring
  `InvoiceRepository.updateStateIfCurrentState`'s own exact shape rather than inlined JPQL enum
  literals.
- `PaymentService.java` — `applySeen`/`applyConfirmed`/`applyFinalized`, implementing the
  transition/refresh/stale three-way split (Finding 3) and the Finding-1 OPEN-invoice guard.
- `PaymentSeenPayload.java`/`PaymentFinalizedPayload.java` (`payment/event`) — no formal contract
  exists yet (task 24's own scope), mirrors `InvoiceCreatedPayload`'s own conventions.
- `ChainEventConsumer.java` — 3 `@KafkaListener` methods, dedupe-first, `unregisterWatch` last
  (Decision 4).
- `ProcessedEvent.java`/`ProcessedEventRepository.java` — mirrors `notification-service`'s own
  `IdempotencyGuard`/`ProcessedEventRepository` exactly, minus the `event_type` column this
  service's own table doesn't have.
- 3 DTOs (`ChainTxSeen`/`ChainTxConfirmed`/`ChainTxFinalized`) — field names verified against the
  real schemas field-for-field.
- `V7__payment_app_payments_table_write_grant.sql`/`V8__payment_app_processed_events_grant.sql`.

## Files modified

- `InvoiceRepository.java`/`InvoiceService.java` — `findByWatchId`.
- `ArchitectureTest.java` — `FEATURE_MODULES` gains `"consumer"`.

## A real, confirmed ArchUnit violation found by running the existing T09 rule, not assumed away

The first compile-and-test pass of this task's own code **failed** `ArchitectureTest`'s own
`shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` — 8 real violations.
`PaymentService.applySeen`'s own original signature took the real `Invoice` entity as a
parameter, and `ChainEventConsumer.onChainTxSeen` called `.getWatchId()` directly on an `Invoice`
instance returned by `InvoiceService.findByWatchId`. **Receiving an entity instance and calling
its own methods from a different feature module violates L12 just as much as importing its
repository would, regardless of how the instance arrived** — a real design gap in the frozen
brief's own text (Finding 2 only addressed the repository-level coupling, not this one).

**Fixed by adding `InvoiceWatchView`** (`invoice` package): a plain public record exposing exactly
`id`/`invoiceUuid`/`state`/`payToAddress`/`watchId` — the fields `consumer`/`payment` actually
need — with a package-private `from(Invoice)` factory only `InvoiceService` itself can call.
`InvoiceService.findByWatchId` now returns `Optional<InvoiceWatchView>`, never `Optional<Invoice>`.
`InvoiceState` (an enum, not an `@Entity`) remains importable across the boundary, same as
`PaymentState`'s own already-established precedent (T10).

## Verification performed this phase

- `mvn -pl services/payment -am compile` / `test-compile` — clean.
- `mvn -pl services/payment -am test -Dtest='ArchitectureTest'` — failed (8 violations) before the
  `InvoiceWatchView` fix, 5/5 passing after.
- Full non-Docker-dependent suite: **196/196 passing, unchanged** (no new tests yet — Phase 10's
  own job).

## Net result

Production code complete per the frozen brief, plus one necessary, disclosed addition
(`InvoiceWatchView`) the frozen brief itself didn't anticipate — found by actually running the
existing ArchUnit test against real code, not assumed correct from the plan alone. Proceeding to
Phase 7.
