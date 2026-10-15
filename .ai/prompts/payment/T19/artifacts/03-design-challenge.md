# payment · T19 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/payment-service/agents.md`, `spec/payment-service/design.md`, the current codebase, and the T19 task statement.

---

## Finding 1 — `receipt.issued` event payload and contract schema are not frozen

- **Issue:** The TIB says only "create a new `ReceiptIssuedPayload` outbox event record" and that it should mirror `PaymentFinalizedPayload`'s shape. The event contract in `spec/payment-service/design.md` §4c requires `merchantAccountUuid`, `amount`, `tokenContractAddress`, `kmsKeyId`, `receiptUri`, and `occurredAt`; `PaymentFinalizedPayload` carries none of those except `amount`/`tokenContractAddress`. The schema file `contracts/events/payments/receipt-issued.v1.schema.json` does not exist in the repo.
- **Severity:** High — an emitted event that cannot validate against its contract violates `agents.md` "Event schemas live in `contracts/`" and R31.
- **Evidence:** `spec/payment-service/design.md` §4c schema (required fields and `additionalProperties: false`); glob of `contracts/events/payments/*.json` returns no files; `services/payment/src/main/java/com/themistra/payment/payment/event/PaymentFinalizedPayload.java` is the brief's stated mirror target.
- **Recommended brief amendment:** Pin the exact `ReceiptIssuedPayload` fields, how `receiptUri` is constructed (e.g. `s3://{bucket}/{prefix}.json`), the `aggregateType` (`"receipt"`), and `eventType` (`"receipt.issued"`). Either add authoring `contracts/events/payments/receipt-issued.v1.schema.json` to this task, or explicitly record that the event will be emitted without a contract for now and that task 24 must not forget to backfill it.

---

## Finding 2 — No abort path when `markAttestedIfFinalized` returns zero rows

- **Issue:** The TIB says the `Signed` branch will be restructured to insert the `Receipt`, append the `LedgerEntry`, transition the payment, and publish the outbox event inside one transaction. It does not say what happens if `PaymentRepository.markAttestedIfFinalized` returns 0 because the payment is no longer `FINALIZED` (concurrent reorg, `BLOCKED` handling, or a duplicate issuance attempt). Without an explicit abort, the code could still insert a `Receipt`/`LedgerEntry` and emit `receipt.issued` for a payment that never actually reached `ATTESTED`.
- **Severity:** High — breaks R24 and AC1.
- **Evidence:** TIB Outputs/State Changes; `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java` `markHeldIfFinalized` returns an `int`; `ReceiptService.issueIfEligible(...)` regenerates a fresh `receiptUuid` on every call, so a second caller is not inherently deduplicated.
- **Recommended brief amendment:** Add: "If `markAttestedIfFinalized` returns 0, the whole DB transaction must roll back and `ReceiptIssueResult.Failed` must be returned; no `Receipt`, `LedgerEntry`, or outbox row may be committed."

---

## Finding 3 — Advisory-lock key is unspecified and could collide across services

- **Issue:** The TIB proposes `pg_advisory_xact_lock(<fixed constant>)` but never names the constant or a naming scheme. Advisory locks are cluster-scoped, not schema-scoped. Two services sharing the same Postgres cluster could pick the same 64-bit value and serialize each other.
- **Severity:** Medium — correctness is preserved, but an unspecified constant is untestable and risks cross-service coupling.
- **Evidence:** TIB Locked Decisions (concurrency-control paragraph); `agents.md` "one logical schema per service" does not isolate advisory-lock IDs.
- **Recommended brief amendment:** Specify the lock ID, e.g. a namespaced 64-bit constant such as `hashtext('themistra.payment.ledger.append')::bigint`, expose it as a single Java constant (`LedgerLock.ID`), and require tests to assert the SQL uses exactly that value.

---

## Finding 4 — `PaymentService.markAttestedIfFinalized` omits `attested_at`

- **Issue:** The TIB says the new method should mirror `markHeldIfFinalized`'s shape minus `blockedReason`. `markHeldIfFinalized` only sets `state` and `updated_at`. The `payments` table has a dedicated `attested_at TIMESTAMPTZ` column (`V1__payments_baseline.sql`) that every other major transition populates (`seen_at`, `confirmed_at`, `finalized_at`, `reorged_at`). Leaving it null makes the audit trail incomplete and contradicts the data model.
- **Severity:** Medium — fails the spirit of R24/AC1 even though the state machine moves.
- **Evidence:** `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (payments table); `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java` `markHeldIfFinalized`.
- **Recommended brief amendment:** Require `markAttestedIfFinalized` to set `state = 'ATTESTED'`, `attested_at = now`, and `updated_at = now` atomically in the same UPDATE.

---

## Finding 5 — Ledger-entry S3 self-description is ambiguous

- **Issue:** The TIB proposes adding `s3Bucket` and `s3KeyPrefix` to the ledger payload so the entry is "self-describing." It does not say whether `s3KeyPrefix` is the normalized prefix (`receipts/`) or the raw configured value; `ReceiptStore.normalizedPrefix(...)` adds a trailing `/` if missing, and any difference changes the canonical hash. It also does not state the suffix convention (`.json`/`.pdf`) an auditor must append to locate the objects.
- **Severity:** Medium — makes AC2's hash deterministic but undocumented, and weakens the "self-describing" claim.
- **Evidence:** TIB Locked Decisions (ledger payload shape); `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` lines 96 and 224-226.
- **Recommended brief amendment:** Pin the ledger payload values: `s3Bucket` = `ReceiptProperties.bucket()`, `s3KeyPrefix` = the normalized prefix plus `receiptUuid` plus trailing `/`; document that the JSON object is at `{prefix}.json` and the PDF at `{prefix}.pdf`.

---

## Finding 6 — S3 objects may be orphaned if the DB transaction fails

- **Issue:** The TIB's Constraints state that `ReceiptStore.store(...)` and `attest()` run strictly outside the DB transaction. If S3 succeeds but the subsequent DB transaction rolls back (deadlock, connection failure, advisory-lock timeout, etc.), the signed JSON/PDF objects remain in WORM storage with no corresponding `Receipt`, `LedgerEntry`, or `ATTESTED` payment. There is no delete path in application code (`agents.md` L9), so the brief is introducing a disclosed-but-unstated operational orphan hazard.
- **Severity:** Medium — violates the "all durably" narrative without a cleanup story.
- **Evidence:** TIB Constraints; `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` writes two WORM objects; `agents.md` L9 "no overwrite or delete path exists in application code."
- **Recommended brief amendment:** Disclose the orphan-S3 window in the Constraints and in the `ReceiptService` class Javadoc; state that cleanup is out of scope and must be handled operationally.

---

## Finding 7 — Insert ordering inside the ledger transaction is unspecified

- **Issue:** The TIB says the `Receipt` insert, ledger append, `ATTESTED` transition, and outbox publish run in one transaction, but it does not state the order. `ledger_entries.receipt_id` is a foreign key to `receipts.id` with `ON DELETE RESTRICT` (`V1__payments_baseline.sql`). Inserting the `LedgerEntry` before the `Receipt` would violate the FK constraint. The payment transition should also logically happen only after the receipt and ledger evidence are durable.
- **Severity:** Medium — an implementer could produce a failing migration or an inconsistent commit order.
- **Evidence:** `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (`ledger_entries` DDL); TIB State Changes.
- **Recommended brief amendment:** Specify the order: (1) acquire advisory lock, (2) insert `Receipt`, (3) append `LedgerEntry`, (4) `markAttestedIfFinalized`, (5) publish `receipt.issued` outbox event.

---

## Finding 8 — V9 migration grants `ledger_entries` again

- **Issue:** The TIB names the migration `V9__payment_app_receipts_and_ledger_grants.sql`, implying both `receipts` and `ledger_entries` need grants. `ledger_entries` was already granted `INSERT, SELECT` in `V2__payment_app_role_and_grants.sql`. Re-granting is harmless but breaks the incremental, least-privilege convention used by every other migration.
- **Severity:** Low — correctness issue only if someone removes the duplicate and forgets `receipts`.
- **Evidence:** `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql` line 40; TIB Files to Create.
- **Recommended brief amendment:** Rename to `V9__payment_app_receipts_grant.sql` and grant only `INSERT, SELECT` on `payments.receipts`; add a comment referencing V2 for the existing `ledger_entries` grant.

---

## Finding 9 — `LedgerService.append` signature is deferred to Phase 5

- **Issue:** The task statement says "Implement `LedgerService.append(...)`", but the TIB says the exact signature is "a Phase 5 implementation-plan decision." Leaving the public API of the core service method unfrozen makes the acceptance criteria (AC2, AC3) and the integration-test contract unenforceable at Phase 6.
- **Severity:** Medium — the brief cannot be implemented as written without an interface decision.
- **Evidence:** TIB Inputs paragraph; task statement in Phase 3 header.
- **Recommended brief amendment:** Freeze a signature now, e.g. `LedgerEntry append(Long receiptId, Map<String,Object> payload)` or a typed `LedgerAppendRequest(receiptId, payload)`, and document the return value.

---

## Finding 10 — Cross-module entity-import risk for `Receipt` and `LedgerEntry`

- **Issue:** `agents.md` L12 forbids cross-module entity imports. The TIB creates `Receipt` in the `receipt` module and `LedgerEntry` in the `ledger` module, both of which need foreign-key values (`payment_id`, `receipt_id`) to tables in other modules. If implemented as JPA `@ManyToOne` references, they would violate L12.
- **Severity:** Medium — ArchUnit will catch it, but the brief should prevent the attempt.
- **Evidence:** `agents.md` L12; `spec/payment-service/design.md` §6 package map places `ledger/` inside `receipt/`; TIB Module-boundaries constraint.
- **Recommended brief amendment:** Add: "`Receipt.paymentId` and `LedgerEntry.receiptId` must be scalar `Long` values, not JPA entity references; no `@ManyToOne` to `Payment` or `Receipt`."

---

## Finding 11 — `LedgerEntry` primary key is `seq`, not `id`

- **Issue:** The TIB does not specify the primary-key column name for `LedgerEntry`. `V1__payments_baseline.sql` defines `ledger_entries.seq BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`, not `id`. An implementer following the usual JPA convention would map `id`, creating a schema mismatch.
- **Severity:** Low — easy to catch in integration tests, but cheaper to state now.
- **Evidence:** `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (`ledger_entries` DDL).
- **Recommended brief amendment:** State that `LedgerEntry` uses `@Id @GeneratedValue(strategy = IDENTITY) @Column(name = "seq") Long seq`.

---

## Finding 12 — Reusing `ReceiptStore`'s canonical map requires exposing a private method

- **Issue:** The TIB says the ledger payload is "the same canonical map `ReceiptStore` already builds" plus two fields. That map is built by the private `ReceiptStore.toCanonicalMap(...)` method. `LedgerService` lives in the `receipt.ledger` subpackage, so it cannot access package-private members of `receipt.ReceiptStore`. The brief tells the implementer not to write a second canonicalizer, but it does not say how to reuse the first one without duplication or illegal cross-package access.
- **Severity:** Medium — creates a code-structure conflict the brief pretends is already solved.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` lines 125-143 (private `toCanonicalMap`); `spec/payment-service/design.md` §6 package map (`receipt/ledger/`).
- **Recommended brief amendment:** Add one option: make `ReceiptStore.toCanonicalMap` public (or add a public `toCanonicalMap(ReceiptPayload, AttestationResult.Signed)` factory), or extract the map-building logic into a shared package-private builder inside `com.themistra.payment.receipt`.

---

## Finding 13 — `VerificationRecord` for the `ATTESTED` transition lacks trigger-key/detail rules

- **Issue:** The TIB says `VerificationRecordService.record(...)` is reused for the `ATTESTED` transition's audit trail, but it does not say what `triggerEventKey` or `detail` map to pass. `ReceiptService.issueIfEligible` already receives the originating `chain.tx.finalized` idempotency key; other transitions record that key. Without guidance, the implementer may pass `null` and lose traceability.
- **Severity:** Low — does not break functionality, but weakens R24/AC1 auditability.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java` `issueIfEligible` signature; `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordService.java` `record(...)`.
- **Recommended brief amendment:** Specify that the ATTESTED `VerificationRecord` uses the original `chain.tx.finalized` idempotency key as `triggerEventKey` and includes `receiptUuid` in `detail`.

---

## Summary of recommended brief amendments to fold in Phase 4

1. Freeze `ReceiptIssuedPayload` fields, `receiptUri`, `aggregateType`, and `eventType`; decide whether the schema file belongs to T19 or T24.
2. Require abort-on-zero for `markAttestedIfFinalized`.
3. Name and namespace the advisory-lock constant.
4. Require `attested_at` to be set on the `ATTESTED` transition.
5. Pin `s3KeyPrefix` normalization and the `.json`/`.pdf` suffix convention in the ledger payload.
6. Disclose the S3-orphan window.
7. Specify the insert order inside the ledger transaction.
8. Rename V9 migration to `V9__payment_app_receipts_grant.sql`.
9. Freeze `LedgerService.append(...)` signature.
10. Mandate scalar foreign-key values for `Receipt` and `LedgerEntry`.
11. Document `LedgerEntry.seq` as the primary key.
12. Decide how `LedgerService` obtains the canonical map without duplicating `ReceiptStore`.
13. Specify the `VerificationRecord` trigger key and detail for `ATTESTED`.
