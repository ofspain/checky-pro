# payment · T19 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the actual test files against the frozen brief's acceptance criteria and the three named tests.

---

## Gap 1 — `ReceiptService` mock tests pass a null receipt id into `ledgerService.append`

- **Why it matters:** The real flow depends on `receiptRepository.save(...)` returning a persisted `Receipt` with a generated, non-null `id`, which `ReceiptService` then passes to `ledgerService.append(receipt.getId(), ...)`. In `ReceiptServiceTest`, the stubbed `save` returns a `Receipt.create(...)` instance whose `id` is null, and the test only verifies `ledgerService.append(any(), any())`. A regression that dropped `receipt.getId()` and passed `null` or a wrong value would still pass.
- **Suggested test:** Stub `receiptRepository.save` to return a `Receipt` with a known id set via reflection, capture the first argument to `ledgerService.append(...)`, and assert it equals that id.

---

## Gap 2 — `shouldEmitReceiptIssuedAfterAttestation` does not assert all 11 frozen payload fields

- **Why it matters:** AC7 (frozen brief) requires `ReceiptIssuedPayload` to match `design.md`'s pinned 11-field schema exactly. The test only asserts `chain`, `txHash`, `amount`, `kmsKeyId`, and `receiptUri`. Regressions in `invoiceUuid`, `paymentUuid`, `receiptUuid`, `merchantAccountUuid`, `tokenContractAddress`, or `occurredAt` would not be caught.
- **Suggested test:** Add exact assertions for every field, comparing to the input `PaymentReceiptView`/`InvoiceReceiptView` values and the generated `receiptUuid`.

---

## Gap 3 — `shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended` uses loose `any()` matchers for the ledger payload and receipt fields

- **Why it matters:** The test verifies that collaborators are called but not that `ReceiptService` builds the correct `Receipt` entity or ledger payload. A regression that omitted `s3Bucket`/`s3KeyPrefix` from the ledger payload, stored the wrong S3 key in `Receipt.s3Key`, or used the wrong `digestSha256` would pass silently.
- **Suggested test:** Capture the `Receipt` argument to `receiptRepository.save(...)` and assert all fields (`paymentId`, `digestSha256`, `signature`, `kmsKeyId`, `s3Bucket`, `s3Key`, `signedAt`). Capture the `Map<String,Object>` passed to `ledgerService.append(...)` and assert it contains the canonical receipt fields plus `s3Bucket` and `s3KeyPrefix`.

---

## Gap 4 — No real-DB test proves `markAttestedIfFinalized` sets `attested_at` and the correct state

- **Why it matters:** `PaymentServiceTest` mocks `PaymentRepository`, so it only proves the service calls the expected method. It does not prove the JPQL actually updates `state` to `ATTESTED` or sets `attested_at`. A typo in the JPQL (e.g., updating `updatedAt` but omitting `attestedAt`) would pass the mock test.
- **Suggested test:** Add a Testcontainers-backed test that inserts a `FINALIZED` payment, calls `PaymentService.markAttestedIfFinalized(...)`, reloads the row, and asserts `state = 'ATTESTED'` and `attested_at` is non-null.

---

## Gap 5 — `LedgerServiceTest` only independently verifies the genesis entry hash

- **Why it matters:** `shouldComputeEntryHashAsShaTwoFiftySixOfCanonicalPayloadConcatenatedWithPrevHash` computes the expected hash only for the genesis case. A bug that ignored the real head and reused the genesis `prevHash` for every subsequent entry could still pass `shouldAppendLedgerEntryWithPrevHashChainingToHead` because that test asserts chaining, not that the hash input used the predecessor's hash.
- **Suggested test:** After appending one entry, append a second with a known payload and assert `second.entryHash == sha256(CanonicalJson.serialize(secondPayload) + first.entryHash)`.

---

## Gap 6 — The abort-on-zero test cannot prove the DB transaction rolls back staged rows

- **Why it matters:** `shouldAbortAndReturnFailedWhenMarkAttestedIfFinalizedReturnsZeroRows` mocks `PlatformTransactionManager`, so `TransactionTemplate` does not create a real transaction. The test proves `Failed` is returned and no outbox event is published, but it does not prove the staged `Receipt` and `LedgerEntry` rows are not committed.
- **Suggested test:** Add a Testcontainers-backed test for `ReceiptService.issueIfEligible` with a stubbed/fake `ReceiptStore` that forces `markAttestedIfFinalized` to return 0, then assert no `Receipt`, `LedgerEntry`, or `outbox` rows exist for the payment.

---

## Gap 7 — No test verifies the real 17-field ledger payload shape

- **Why it matters:** `LedgerServiceTest` appends minimal payloads (`Map.of("k", "v1")`). The production payload is the 15-field `ReceiptStore.toCanonicalMap(...)` plus `s3Bucket`/`s3KeyPrefix`. A regression that omitted `signature`, `kmsKeyId`, `signedAt`, or the S3 fields from the payload would change the `entryHash` without failing any current test.
- **Suggested test:** In `LedgerServiceTest`, build a payload matching the real 17-field shape, append it, and assert the stored JSONB contains all expected keys. Optionally recompute the expected `entryHash` from that full payload.

---

## Gap 8 — No test pins the advisory-lock SQL expression

- **Why it matters:** A future edit to `LedgerEntryRepository.acquireAppendLock` that changed the lock key (typo, different function, or a literal constant) would silently break AC4 by serializing on a different lock than expected.
- **Suggested test:** Add a test that inspects the `@Query` annotation value (or issues the query via JDBC and checks `pg_locks`) and asserts it matches the namespaced expression `pg_advisory_xact_lock(hashtext('themistra.payment.ledger.append')::bigint)`.

---

## Gap 9 — No test verifies `ReceiptIssuedPayload` serialization is a string date-time

- **Why it matters:** The event schema expects `occurredAt` as a `date-time` string. If the `ObjectMapper` were ever misconfigured to serialize `Instant` as a numeric timestamp, the emitted event would fail contract validation.
- **Suggested test:** Serialize a `ReceiptIssuedPayload` with the application's `ObjectMapper` and assert `occurredAt` is a quoted ISO-8601 string.

---

## Gap 10 — `LedgerServiceTest` does not exercise the `Receipt` JPA entity or `ReceiptRepository.save`

- **Why it matters:** The test seeds `receipts` directly via JDBC with dummy values, so a bug in the `Receipt` entity mapping (wrong column name, missing `@JdbcTypeCode(SqlTypes.CHAR)` on `digestSha256`, etc.) would not be caught by this task's tests.
- **Suggested test:** Add a `ReceiptRepositoryIntegrationTest` that saves and reloads a `Receipt` via JPA as `payment_app`, asserting all fields round-trip.

---

## Summary

The Phase 10 tests cover the collaborator call graph and the sequential hash-chain property well, but they rely heavily on mocks for `ReceiptService`, leave several important fields unasserted, and do not exercise the real transaction rollback or the full ledger payload shape. The highest-value additions before considering T19 complete are Gap 1 (real receipt id wiring), Gap 3 (correct payload/entity construction), Gap 4 (real `attested_at` update), and Gap 6 (real rollback proof).
