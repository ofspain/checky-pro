# notification · T07 · Phase 7 — Self-Review

Consumes: `artifacts/06-implementation-notes.md` (commit `f484251f`).

This is a review of the diff against `04-frozen-task-brief.md` and `spec/notification-service/agents.md`. It records findings only; fixes belong to Phase 9.

## Findings

| # | Issue | Severity | Evidence | Recommendation |
|---|---|---|---|---|
| S1 | **Cross-topic catch-up race silently drops notifications.** Each topic has its own listener container, so after downtime or lag the four backlogs are consumed concurrently. `payment.seen` can run before its `invoice.created`, and `payment.finalized` before its `payment.seen`. The merchant then can't be resolved, and the event is dedup-recorded and dropped (AC6). The race is plausible in normal catch-up, not only in rare edge cases. The IT sends each event only after the previous dispatch, so it doesn't exercise the race. | Medium (already disclosed as FU-T07-4 in the brief) | `consumer/PaymentEventConsumer.java`: `onPaymentSeen` (merchant lookup after `recordPayment`) and `onPaymentFinalized` (two-hop lookup). `PaymentEventConsumerIntegrationTest` uses sequential `awaitDispatches`. | Keep it in scope as a disclosed limitation, per the frozen brief. Phase 9 could make the drop observable, for example with a distinct log reason or a counter, so FU-T07-4's real frequency can be measured. Do not add retry here (out of scope). |
| S2 | **`receipt.issued` can freeze `token_decimals` as null.** When `receipt.issued` is processed before `invoice.created` (same race as S1), it inserts an `invoice_routes` row with null decimals. The later `invoice.created` insert is then a no-op because of `ON CONFLICT DO NOTHING`, so the decimals are never recorded and any later amount for that invoice renders as "base units". | Low | `PaymentEventConsumer.onReceiptIssued`: `recordInvoice(…, null)`. `routing/InvoiceRouteRepository.java:16` (`DO NOTHING`). | Either fill only a null `token_decimals` (`ON CONFLICT … DO UPDATE SET token_decimals = EXCLUDED.token_decimals WHERE invoice_routes.token_decimals IS NULL`, which needs an UPDATE grant on that column), or accept it and disclose it. In practice `payment.finalized` (the only later amount user) is processed before `receipt.issued`, so the impact is small. Recommend: disclose, no change. |
| S3 | **The dedup key is built before the null check.** A payload missing its aggregate UUID is recorded under `invoice.created:null` (or `payment.seen:null`, and so on). Every later malformed event of that type then dedupes against that one row and is silently skipped, with no "not dispatched" log line after the first. | Low | `PaymentEventConsumer`: each listener builds `eventKey` before the `== null` check, for example `onInvoiceCreated`. | Check for a null aggregate UUID before deduping (log and return without recording), so each malformed event is logged. The producer schemas require these fields, so this only guards against contract drift. |
| S4 | **The routing entities aren't `@Immutable`.** They are getter-only and the role has no UPDATE grant, so nothing can mutate them today. Still, the platform precedent for insert-once rows (see the Hibernate JSON dirty-checking note) is `@Immutable`, which makes the intent explicit and stops a future setter from causing a failing UPDATE. | Low | `routing/InvoiceRoute.java:17`, `routing/PaymentRoute.java`. | Add `@org.hibernate.annotations.Immutable` to both. |
| S5 | **The `receipt.issued` EMAIL subject says "Your receipt is ready".** The brief asks for merchant-facing wording. The merchant *issued* the receipt for the payer, so "your" is ambiguous. | Low | `V14__seed_payment_templates_v2.sql`, the `receipt.issued` EMAIL row. | Reword it to "Receipt issued", before V14 is ever applied outside test. It is not merged to main yet, so editing V14 is still allowed under L9's own logic. |
| S6 | **The invoice link depends on configuration and a frontend route.** If `themistra.notification.link.base-url` (`AUTH_EMAIL_LINK_BASE_URL`) is unset, `{{invoiceLink}}` renders as a relative `/invoices/<uuid>`, and the frontend route itself is unconfirmed (Q4). This is the existing auth-email convention, not new. | Info | `application.properties:71`; the `TemplateRenderer` link computation. | No change. Note it in the PR alongside FU-T07-2. |
| S7 | **Amounts have no token symbol.** For example "An invoice for 1.5 on ethereum". This is accepted under F7, since no event carries a symbol. With unknown decimals, the message reads "1500000 base units". | Info | V14 bodies; `AmountFormatter`. | No change. A possible follow-up: show a token symbol once a token registry exists. |

## Checklist

| Area | Assessment | Notes |
|---|---|---|
| Correctness | OK | All AC1–AC9 are covered by tests; seven mutations are killed. |
| Boundary conditions | OK | `tokenDecimals` 0–255 fits `Short` (schema `:13`). Large values stay exact, and zero renders as `0`. |
| Null-safety | OK, apart from S3 | Optional fields are boxed, and `putIfPresent` is used. |
| Thread-safety | OK | Listeners are stateless. A concurrent insert of the same route resolves through `ON CONFLICT DO NOTHING`; the second transaction waits, then no-ops. |
| Transactions | OK | Listeners are `@Transactional`; `PaymentRouting`'s `REQUIRED` methods join the same transaction, so dedupe, routing and dispatch commit or roll back together (L1). |
| Malformed JSON | OK | `readValue` throws, and the default container error handler retries then skips. This is the same disposition as `AuthEventConsumer` (`malformedMessageDoesNotPermanentlyPoisonTheListener`). |
| Module boundaries | OK | `consumer` imports only `PaymentRouting`, never a routing entity (L11). `routing` is in `FEATURE_MODULES`. |
| Idempotency | OK | `<eventType>:<aggregateUuid>` is deduped before any side effect (AC5, killed M1). |
| Money types | OK | No `double`/`float` (killed M5). |
| Secrets and PII | OK | Logs carry the topic, the key and UUIDs only (F9). `receiptUri`, `kmsKeyId` and addresses never reach `eventData` (killed M3). |
| Readability and complexity | OK | Four short listeners with one shared helper each. |

## Open Questions

None blocking. S1 and S2 restate the disclosed FU-T07-4 limitation, so Kimi Phase 8 can challenge whether it is acceptable for Phase 1.
