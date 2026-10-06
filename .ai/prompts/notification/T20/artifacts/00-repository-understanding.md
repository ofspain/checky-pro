# notification · T20 · Phase 0 — Repository Understanding

## Task

`tasks.md` task 20: "Bump spec status. Once §11 questions (esp. Q1, Q2, Q3, Q4, Q7) are closed and
tests pass, change this spec from `DRAFT` to `READY FOR IMPL` and version to `0.2`."

## Gate check: the condition is not met

**Tests pass:** yes. The full suite is 373 tests, 0 failures, 0 errors (T19 Phase 11).

**Open questions:** checked directly in `spec/notification-service/package.md`.

- **Only Q8 is marked `Resolved` in the spec text.** Q1–Q7 are still phrased as open questions.
- **Q1 (recipient resolution):** implemented as a contact projection fed by auth events (T05).
  The spec text does not record it as closed.
- **Q2 (email transport):** implemented as SES behind `EmailChannel` (T12). Not recorded as closed.
- **Q3 (in-app transport):** implemented as SSE (T13). Not recorded as closed.
- **Q4 (link base URL):** implemented as configuration
  (`themistra.notification.link.base-url=${AUTH_EMAIL_LINK_BASE_URL:}`, `application.properties:71`).
  Not recorded as closed.
- **Q7 (event to recipient to channel matrix):** **not closed in practice, not only on paper.** Its
  payment half (`payments.invoice.created`, `payment.seen`, `payment.finalized`,
  `receipt.issued`, recipients merchant and payer) depends on a `PaymentEventConsumer`. That
  consumer does not exist: `find` returns none, and task 7 was skipped (`services/payment` still has
  only a README; `spec/payment-service` is still `DRAFT`). T17 deferred the payment scenario for the
  same reason. The matrix cannot be confirmed without the producer.

## Why this matters

Bumping to `READY FOR IMPL` asserts the spec is implementable as written. Q7's payment half is
unvalidated, and the implementation has no payment path to validate it against. A status bump now
would say the payment matrix is confirmed when it has never been exercised.

## Decision needed

This is a judgment about the spec's own status, so it belongs to you, not to me.

1. **Bump now and record the gap.** Mark Q1–Q4 closed (matching the code), close Q7's auth-side
   matrix, state explicitly that the payment matrix is unvalidated, and set `READY FOR IMPL`/`0.2`.
2. **Hold T20 until payment-service unblocks Q7.** This matches the T07 and T17 precedent: the
   payment half stays blocked, and the status stays `DRAFT`.
3. **Bump with a narrower honest status**, for example a partial or interim header, rather than
   `READY FOR IMPL`, since the spec's own condition is not met.

No spec file has been modified. The `spec/` directory is read-only for every task except this
one, and this one's own gate is not yet satisfied.
