# 7. Tasks — ordered execution plan

Execute in order. Each task leaves the module buildable and the test suite green. Front-load schema, then consumers/state machine, then attestation/receipts/ledger, then exports, then contracts and hardening.

## Foundation

1. **Service skeleton & POM.** Add `services/payment` to the root `<modules>`. Create `pom.xml` mirroring `services/auth` (web, validation, resource-server, data-jpa, flyway, postgres, spring-kafka, actuator, prometheus, testcontainers, archunit, awaitility). Add the S3 SDK dependency for the receipt store only (KMS SDK is **not** added — L3).
2. **Schema V1.** Add `V1__payments_baseline.sql` (design §4c) and run `mvn -pl services/payment flyway:migrate` against local Docker Compose Postgres. Grant the service DB role INSERT+SELECT-only on `ledger_entries` and `verification_records`.
3. **Config & resource server.** Add validated `@ConfigurationProperties` for Crypto base URL, invoice, receipt/S3, ledger, and expiry keys (design §4c). Wire JWT resource-server validation against the Auth JWKS and `PublicEndpoints` (health/info/prometheus only). Startup fails on missing config in non-local profiles.
4. **Outbox & EventTopics.** Add `OutboxPublisher`, the `outbox` mapping, and `EventTopics` (design §4c). Unit-test aggregate→topic routing (R30).

## Invoices

5. **Invoice domain.** Add `Invoice`, `InvoiceState`, repository, and `InvoiceService.create(...)` with amount/token validation (R3, R4) and base-unit `NUMERIC` handling (L6). Resolve Q1 amount-tolerance before finalizing comparison logic.
6. **Invoice API + watch registration.** Add `InvoiceController` `POST /api/v1/invoices`, register the watch via `CryptoClient` (design §4c), and emit `invoice.created` through the outbox in the same transaction (R1, R2). Move tracking to `CREATED`, then `WATCHING` on watch-ack (R6).
7. **Merchant-scoped reads.** Add `GET /api/v1/invoices` and `GET /api/v1/invoices/{invoiceUuid}` filtered to the caller's `sub`, returning `404` cross-merchant (R28).
8. **Invoice expiry sweep.** Add `InvoiceExpirySweepJob` (ShedLock) that transitions `OPEN → EXPIRED` and unregisters the watch (R5). Resolve Q3 for post-expiry finalization behaviour.

## State machine & consumers

9. **Payment state machine.** Implement `PaymentStateMachine` as pure logic enforcing exactly the transitions in design §4c/L1, including the two reorg reversals. Unit-test every permitted transition and assert illegal transitions throw (not silently no-op).
10. **Verification record.** Add `VerificationRecord` (append-only) and record every transition, reversal, and *ignored stale event* with the trigger event key (R11, R12, R14).
11. **Idempotent consumers.** Add `ChainEventConsumer` with `processed_events` dedupe on `chain:txhash:eventtype` inside the applying transaction (R13, L5). Handle `chain.tx.seen` → `SEEN` + emit `payment.seen` (R7, R8); `chain.tx.confirmed` → `CONFIRMING` + record confirmations (R9); `chain.tx.finalized` → `FINALIZED` + emit `payment.finalized` (R10).
12. **Reorg handling.** Wire `chain.tx.reorged` to the backward transitions (R11, R12) and record the reversal; assert no receipt exists to revoke (L11).
13. **Amount & poisoning integrity.** On match, compute under/overpayment against invoice amount in base units and set `amount_discrepancy` (R15, Q1); persist and surface the address-poisoning flag from the observation (R16).
14. **Idempotency & ordering tests.** Testcontainers: duplicate delivery is a no-op (R13); stale/out-of-order events are ignored (R14); per-payment ordering is preserved (O6).

## Attestation, receipts, and ledger

15. **Crypto client.** Add `CryptoClient` for `POST /attest` and `POST/DELETE /watches` (design §4c) with timeouts and error mapping. Use a service-to-service JWT (`internal.crypto:write`). Never import a KMS or chain client (L3 — add the ArchUnit ban here).
16. **Receipt digest (Q5).** Implement the canonical signed-JSON receipt shape and `ReceiptDigest` (SHA-256 over canonical JSON). Pin the shape in design §4c once Q5 is answered.
17. **Receipt issuance — FINALIZED only.** Implement `ReceiptService`: guard that the payment is `FINALIZED` (L2, R17, R18), call `/attest` (R19), and honour a `BLOCKED` outcome by moving to `HELD` and emitting nothing (R20).
18. **S3 WORM store.** Implement `ReceiptStore` writing the signed JSON (and PDF per Q5) to the Object Lock bucket in compliance mode, no overwrite path (R23, L9).
19. **Hash-chain ledger.** Implement `LedgerService.append(...)`: `prev_hash` = current head `entry_hash`, `entry_hash` = SHA-256(canonical payload ‖ prev_hash), genesis prev_hash = 64 zeros (R21, L7). Then transition the payment to `ATTESTED` and emit `receipt.issued` in the same transaction as the ledger append (R24, R25).
20. **Ledger verifier.** Implement `LedgerVerifier` that recomputes head-to-tail and reports the first break (R22). Test with a deliberately corrupted row.
21. **Daily anchor job (Q4).** Implement `LedgerAnchorJob` (ShedLock) recording the head hash and anchor tx reference (R26). Route the on-chain write per Q4/O3 — **blocked until Q4 is answered.**

## Exports, contracts, hardening

22. **Tax export (Q6).** Implement `TransactionExportService` + `ExportController` `GET /api/v1/exports/transactions` over a date range, merchant-scoped (R27).
23. **RFC 9457 errors.** Add `ApiExceptionHandler` with a stable `type` catalogue; no stack traces or internal detail on 4xx (R29).
24. **Contracts.** Author `contracts/api/payments.yaml` and the four `contracts/events/payments/*.v1.schema.json` (amounts as decimal strings). Add contract tests mirroring the auth `UserLifecycleEventPayloadContractTest` pattern (R31).
25. **ArchUnit/module boundaries.** Enforce no cross-module entity imports (L12), no floating-point money types (L6), and the KMS/chain-client import ban (L3). Assert the public-endpoint allowlist (L10).

## Final verification

26. **End-to-end integration test.** Testcontainers Postgres + Kafka + WireMock Crypto Service: create invoice → watch-ack → `chain.tx.seen` → `chain.tx.confirmed` → `chain.tx.finalized` → `/attest` (SIGNED) → receipt in S3 → ledger appended → `ATTESTED` → `receipt.issued`. Then a separate flow: reorg between seen and finalized walks the state backward with no receipt.
27. **Run full suite.** `mvn -pl services/payment verify` must pass; Docker image builds from repo root.
28. **Threat-model check.** Verify against `SECURITY-THREAT-MODEL.md`: insider alters a historical verification (#5) is defeated by the append-only ledger + S3 WORM; confirm no code path can UPDATE/DELETE `ledger_entries` or overwrite a receipt object.
29. **Bump spec status.** Once §11 questions (esp. Q1, Q3, Q4, Q5, Q7) are closed and tests pass, change this spec from `DRAFT` to `READY FOR IMPL` and version to `0.2`.
