# 3. Requirements — acceptance criteria (EARS)

Each requirement is independently testable and maps to a named test in [`package.md`](package.md) §8.

## Invoice creation & lifecycle

- R1. WHEN a merchant with a valid JWT bearing the `MERCHANT` role calls `POST /api/v1/invoices` with amount, token contract address, chain, and pay-to address, THEN the system SHALL create an invoice in state `OPEN`, register a watch with the Crypto Service, and return the invoice with its `invoiceUuid` and pay-to address.
- R2. WHEN an invoice is created, THEN the system SHALL write the invoice row and an `invoice.created` outbox event in the same database transaction.
- R3. IF the token contract address or chain in a `POST /api/v1/invoices` request is malformed or not a recognised `<chain, contractAddress>` shape, THEN the system SHALL reject the request with a `400` RFC 9457 problem and SHALL NOT register a watch.
- R4. IF the invoice amount is zero, negative, or not representable as an exact `NUMERIC`, THEN the system SHALL reject the request with a `400` and SHALL NOT create the invoice.
- R5. WHEN an invoice's expiry time elapses with no finalized payment, THEN the scheduled expiry job SHALL transition the invoice to `EXPIRED` and unregister its watch with the Crypto Service.
- R28. WHEN a caller requests `GET /api/v1/invoices`, `GET /api/v1/invoices/{invoiceUuid}`, or a receipt, THEN the system SHALL return only resources owned by the caller's merchant account and SHALL return `404` (not `403`) for resources owned by another merchant.

## Verification state machine

- R6. WHEN the Crypto Service acknowledges the watch registration for an invoice, THEN the associated payment tracking SHALL move from `CREATED` to `WATCHING`.
- R7. WHEN a `chain.tx.seen` event matching a watched address arrives, THEN the system SHALL record a payment in state `SEEN` linked to the invoice.
- R8. WHEN a payment first enters `SEEN`, THEN the system SHALL emit a `payment.seen` outbox event in the same transaction.
- R9. WHEN a `chain.tx.confirmed` event for a `SEEN` payment arrives, THEN the system SHALL transition the payment to `CONFIRMING` and record the reported confirmation count.
- R10. WHEN a `chain.tx.finalized` event for a `CONFIRMING` (or `SEEN`) payment arrives, THEN the system SHALL transition the payment to `FINALIZED` and emit a `payment.finalized` outbox event in the same transaction.
- R11. IF a `chain.tx.reorged` event arrives for a payment in `CONFIRMING`, THEN the system SHALL walk the payment backward to `SEEN` and record the reversal in the verification record.
- R12. IF a `chain.tx.reorged` event arrives for a payment in `SEEN` (not yet confirmed), THEN the system SHALL walk the payment backward to `WATCHING` and record the reversal.
- R14. IF a consumed `chain.tx.*` event is stale relative to the payment's current state (e.g. a `seen` after `finalized`, or a lower confirmation count than already recorded), THEN the system SHALL ignore it without changing state and SHALL record that it was ignored.

## Idempotency & event handling

- R13. WHEN the same `chain.tx.*` event (identified by its deterministic key `chain:txhash:eventtype`) is delivered more than once, THEN the system SHALL apply it at most once and SHALL treat every subsequent delivery as a no-op.
- R30. WHEN an `invoice.created`, `payment.seen`, `payment.finalized`, or `receipt.issued` event is emitted, THEN `EventTopics` SHALL route it to the `payments.invoice.created`, `payments.payment.seen`, `payments.payment.finalized`, and `payments.receipt.issued` Kafka topics respectively.

## Amount, token, and address integrity

- R15. IF a matched payment's transferred amount is less than the invoice amount (underpayment) or greater than it (overpayment), THEN the system SHALL record the discrepancy on the payment and SHALL NOT mark the invoice `PAID` on an underpayment.
- R16. WHERE a consumed `chain.tx.*` event carries an address-poisoning flag from the Crypto Service observation, THEN the system SHALL persist that flag on the payment and surface it in the payment and receipt views.

## Attestation & receipts

- R17. WHEN a payment is in `FINALIZED` and satisfies its invoice, THEN the system SHALL request attestation and issue exactly one receipt.
- R18. IF a payment is in any state other than `FINALIZED`, THEN the system SHALL NOT issue a receipt under any code path.
- R19. WHEN issuing a receipt, THEN the system SHALL compute the canonical receipt digest and obtain its signature by calling the Crypto Service `POST /attest` endpoint; the system SHALL NOT perform signing itself.
- R20. IF the Crypto Service attest response is `BLOCKED` (e.g. an OFAC/compliance hit), THEN the system SHALL NOT issue a receipt, SHALL move the payment/invoice to `HELD`, and SHALL emit no `receipt.issued` event.
- R23. WHEN a receipt is issued, THEN the system SHALL store the signed receipt object (JSON, and PDF per Q5) in the S3 Object Lock bucket in compliance mode, and SHALL NOT overwrite an existing receipt object.
- R24. WHEN the receipt has been signed, stored in S3, and appended to the ledger, THEN the payment SHALL transition to `ATTESTED`.
- R25. WHEN a payment reaches `ATTESTED`, THEN the system SHALL emit a `receipt.issued` outbox event in the same transaction as the ledger append.

## Tamper-evident hash-chain ledger

- R21. WHEN a receipt is issued, THEN the system SHALL append one ledger entry whose `prev_hash` is the SHA-256 entry hash of the current chain head, and SHALL advance the head to the new entry.
- R22. WHEN the ledger is verified, THEN the system SHALL recompute each entry hash from its payload plus `prev_hash` and SHALL report any entry whose recomputed hash does not match its stored hash or whose `prev_hash` does not match the prior entry.
- R26. WHEN the scheduled daily anchor job runs, THEN the system SHALL record the current ledger head hash as an on-chain anchor (mechanism per Q4) and store the anchor transaction reference against the head.

## History, exports, and errors

- R27. WHEN a merchant calls `GET /api/v1/exports/transactions` with a date range, THEN the system SHALL return their finalized/attested payment history in a tax-ready export (format per Q6), including token, amount, chain, tx hash, finality timestamp, and receipt reference.
- R29. WHEN the service returns a `4xx`, THEN the response SHALL be an RFC 9457 `application/problem+json` body with a stable `type`, no stack traces, and no internal details.
- R31. WHERE `contracts/api/payments.yaml` is authored, THEN the service responses and generated client models SHALL conform to it, and every published event SHALL conform to its schema under `contracts/events/payments/`.
