# notification · T07 · Phase 0 — Repository Understanding

## Task

`tasks.md` task 7: "Payment event consumer. Implement `PaymentEventConsumer` for
`payments.invoice.created`, `payment.seen`, `payment.finalized`, `receipt.issued` → templates,
resolving recipients per the §4c matrix (R3, R4, R5; confirm Q7)."

## Blocker found — task deferred, not started

**`services/payment` (the sole publisher of all 4 events this task needs to consume) does not
exist as buildable code.** Verified directly:
- `services/payment/` contains only a `README.md` — no `pom.xml`, not listed in the root
  `<modules>`, no Java source anywhere.
- `spec/payment-service/package.md`'s own header: `Status: DRAFT`, `Implementer: TBD` — the spec
  itself hasn't reached `READY FOR IMPL`.
- `spec/payment-service/tasks.md` task 24 ("Contracts") is where
  `contracts/events/payments/{invoice-created,payment-seen,payment-finalized,receipt-issued}.v1.schema.json`
  would be authored — task 24 of 29, near the very end of payment-service's own pipeline. None of
  those files exist in `contracts/events/` today (only `contracts/events/{auth,chain}/` do).
- Of the 4 events, only `payments.receipt.issued`'s exact field shape is pinned, in
  `spec/payment-service/design.md` §4c (a full JSON Schema, `invoiceUuid`/`paymentUuid`/
  `receiptUuid`/`chain`/`txHash`/`amount`/etc.). The other 3
  (`payments.invoice.created`, `payments.payment.seen`, `payments.payment.finalized`) are
  explicitly **not** pinned — design.md's own text: "follow the same envelope shape; author them
  under `contracts/events/payments/`" — deferred to whoever implements payment-service's own task
  24, not decided anywhere yet.

This is a materially different situation from T05's missing-`email`-field or T06's
missing-`eventType`-field blockers (both were small gaps on an existing, real, fully-tested
producer). Here, the entire upstream producer is unbuilt — a 29-task service starting from zero.

## Decision

Presented to the user as a genuine blocker with three options: (1) skip T07 and continue the
notification-service pipeline with T08+ (none of which depend on payment-service's events), (2)
author the 3 missing contract files now as a disclosed prerequisite, mirroring `receipt-issued`'s
already-pinned envelope shape, (3) pause notification-service entirely and bootstrap
payment-service's own 29-task pipeline first.

**User chose (1): skip T07, continue with T08.** Verified T08 (Preference resolver) through T12
(Email channel) — the immediately following tasks — operate on preferences/templates/delivery
generically and can be built and tested entirely against the auth events T06 already wired up
(`verify_email`/`password_reset`/`user.registered`); none require `payments.*` events.

## Revisit condition

T07 stays open, unstarted. Revisit once `spec/payment-service` reaches `READY FOR IMPL` (or at
minimum once its own task 24 lands, giving this task real, authored contracts to consume) — check
`spec/payment-service/package.md`'s own header and `contracts/events/payments/` for that signal
before attempting T07 again.
