# notification · T17 · Phase 0 — Repository Understanding

## Task

`tasks.md` task 17: "End-to-end integration test. Testcontainers Postgres + Kafka + capturing
transport: produce `auth.email.requested(verify)` → exactly one verification email; redeliver the
same event → no second email (R8); produce `payments.receipt.issued` → both parties notified with
a receipt link; opt a recipient out of PAYMENT email → that channel is `SUPPRESSED`, in-app still
delivered."

## Blocker found — same root cause as T07, unchanged

This task's own literal text has two independent scenario groups:

**(A) The `auth.email.requested(verify)` redelivery scenario** — fully buildable today. Every piece
it needs already exists and is tested: `AuthEventConsumer` (T06), `IdempotencyGuard` (T04),
`PreferenceResolver` (T08), `TemplateRenderer` (T09), `DeliveryOrchestrator` (T11), `EmailChannel`
against the capturing fake (T12). No new blocker.

**(B) The `payments.receipt.issued` scenario** (two-party notification + PAYMENT-category opt-out)
— **blocked on the exact same unbuilt upstream producer T07 was skipped for, verified unchanged**:
- `services/payment/` still contains only a `README.md` — no `pom.xml`, not in the root
  `<modules>`, no Java source.
- `spec/payment-service/package.md`'s own header: still `Status: DRAFT`.
- `contracts/events/payments/` still does not exist anywhere in the repo (only
  `contracts/events/{auth,chain}/` do).
- No `PaymentEventConsumer` exists in `services/notification` — confirmed directly, `find` returns
  nothing. T08-T16 never needed one (preferences/templates/rendering/delivery are all generic;
  `TemplateRenderer`'s own 8 payment-derived seed templates are exercised only against
  hand-constructed test data today, per the T08/T09 record).

Scenario (B) cannot be built even partially the way T08/T09 worked around the same blocker, because
those tasks only needed to *render* a template given hand-built input data — this task needs a real
Kafka producer actually publishing `payments.receipt.issued` and a real consumer actually consuming
it, which does not exist and cannot be faked without writing the consumer this task was never
scoped to write (that's T07's own job, still skipped).

## Decision

Presented to the user as a genuine blocker with the same three-way choice T07's own blocker used,
adapted to this task already having a real, buildable half.

**User chose (1): scope T17 to scenario (A) only.** The real end-to-end integration test is built
for the `auth.email.requested(verify)` redelivery/idempotency path. Scenario (B)
(`payments.receipt.issued` two-party notification + PAYMENT-category opt-out) is deferred,
disclosed, identical in kind to T07's own already-accepted blocker — not silently dropped, not
worked around with a fake producer.

## Revisit condition (for scenario B, whichever option is chosen)

Unchanged from T07: revisit once `spec/payment-service` reaches `READY FOR IMPL` (or at minimum
once its own task 24 lands, giving `PaymentEventConsumer` real, authored contracts to consume) —
check `spec/payment-service/package.md`'s own header and `contracts/events/payments/` for that
signal.
