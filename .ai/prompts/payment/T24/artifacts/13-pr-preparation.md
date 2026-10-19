# payment · T24 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

This artifact is documentation only; there is no code in it.

**Branching:** following the practice of every prior task in this pipeline, the work ran on the
long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task branch off
`main`. `main` was never touched and stays deployable.

**Trailer:** the template names "Claude Opus 4.8 (1M context)". The trailer below names the
model that actually did the work, following T23's precedent.

## Commit title

`payment T24: payments.yaml + payments event schemas with contract tests (R31)`

## Commit message

```
payment T24: payments.yaml + payments event schemas with contract tests (R31)

Author contracts/api/payments.yaml (the 4 merchant routes, MERCHANT-scoped bearerAuth, the
export's JSON and CSV variants with from/to/format params, and a Problem schema whose type
enum is the 11 ProblemTypes URIs) and the four contracts/events/payments/*.v1.schema.json
(invoice-created, payment-seen, payment-finalized, receipt-issued). Every amount is a decimal
string; the create-invoice request also pins ^[1-9][0-9]*$ because the service only accepts
whole base units. receipt-issued follows design.md section 4c's pinned schema. Nullable fields
(reference, fromAddress, toAddress) are typed nullable and left out of required.

Add PaymentsOpenApiContractTest (named test shouldConformToPaymentsOpenApiContract) and
PaymentEventPayloadContractTest. Both are plain Jackson plus reflection, mirroring the sibling
contract tests. They check route completeness in both directions, that schema properties
equal record components, the exact required and nullable sets, a Java-type to type/format
mapping that fails on unknown types, MERCHANT scoping, Problem-enum vs ProblemTypes parity,
CSV column order, a dependency-free OpenAPI structural check, and each event's documented
partition key. Drift guards were mutation-checked.

Each schema states the partition key the code actually uses: invoice.created is keyed by
invoiceUuid, payment.* by paymentUuid, and receipt.issued by receiptUuid. design.md says
invoiceUuid for all; that drift is logged as a follow-up, not fixed here. No production code
changed. Adds test-scope jackson-dataformat-yaml and flips the T01 tripwire that expected it to
land at this task.

Full suite: 490/490 passing (461 baseline + 29).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed

**Contracts (new):**
- `contracts/api/payments.yaml`
- `contracts/events/payments/invoice-created.v1.schema.json`
- `contracts/events/payments/payment-seen.v1.schema.json`
- `contracts/events/payments/payment-finalized.v1.schema.json`
- `contracts/events/payments/receipt-issued.v1.schema.json`

**Build:**
- `services/payment/pom.xml`: adds `jackson-dataformat-yaml` (test scope, BOM-managed). Also
  corrects the stale T08 comment about it.

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/common/PaymentsOpenApiContractTest.java`
  (new, 23 tests)
- `services/payment/src/test/java/com/themistra/payment/events/PaymentEventPayloadContractTest.java`
  (new, 6 tests)
- `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`: one
  tripwire assertion flipped to expect the dependency (Phase 6, deviation 0).
- `services/payment/src/test/java/com/themistra/payment/payment/PaymentServiceTest.java`: two
  publish-key assertions tightened from `any(String.class)` to the exact `paymentUuid` (Phase
  11, Gap 3).

**Production code:** none.

**Pipeline artifacts:** `.ai/prompts/payment/T24/artifacts/00`–`13`.

## Summary

T24 closes R31 for payment-service. It is the last sibling-parity gap: auth, crypto and
notification already had tested contracts.

**Human decisions that shaped it:**
- **Phase 4:**
  - Accepted most of Kimi's Phase 3 findings.
  - Disclosed the real partition keys rather than copying `design.md`'s wording, and logged the
    drift (G2).
  - Honoured T23's forward commitment with a minimal `Problem` schema (G3).
- **Phase 9:**
  - Corrected the `amount` contract to whole base units, with a request pattern.
  - Documented the `violations` and `trace_id` problem members.
  - Kept the `Accept` header parameter, with a note that tooling ignores it.
  - Rejected `additionalProperties: false` on DTO schemas. The equality check already catches
    added fields, and Spring accepts unknown request fields.
- **Phase 11:** tightened the real partition-key assertions for both payment events, and added
  an OpenAPI structural check and exact CSV column parsing.

**Open follow-ups (human-owned, out of T24's scope):**
1. **G2 partition-key drift:** decide whether `design.md` changes to match the code, or the code
   is re-keyed to `invoiceUuid`.
2. **`agents.md:39`, "models are generated from `contracts/`":** this service's DTOs are
   hand-written plus contract-tested, like its siblings. This is a platform-level standing-rule
   gap.
3. **`CreateInvoiceRequest.tokenDecimals`:** it is a primitive `short`, so an omitted value
   becomes `0` instead of a 400.

## Testing performed

- **Full suite:** `mvn -o test` in `services/payment`, with Testcontainers Postgres and Kafka,
  passes **490/490**. The pre-task baseline was 461. The new tests break down as:
  - 23 in `PaymentsOpenApiContractTest`
  - 6 in `PaymentEventPayloadContractTest`
  - existing-test tightenings only (`T01SkeletonRegressionTest`, `PaymentServiceTest`)
- **Mutation checks:** temporary edits, each reverted and confirmed restored.

  | Phase | Edit | Effect |
  |---|---|---|
  | 6 | `amount` → `type: number` | fails |
  | 6 | path rename | fails |
  | 9 | path rename | 5 failures, 0 crashes (clear messages) |
  | 11 | broken `$ref` | fails |
  | 11 | extra CSV column | fails |

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 24 (`:42`).
- **Requirement:** R31 (`requirements.md:56`).
- **Named test:** `shouldConformToPaymentsOpenApiContract` (`package.md:108`).
- **LOCKED decisions honoured:** L6 (decimal-string money), L8 (the four outbox events), L10
  (MERCHANT-only), L12 (module boundaries; continuity).
- **Design:** `design.md` §4c, the pinned `receipt-issued` schema (followed; G1).
- **Standing rules:** `agents.md:26` (money), `:38` (versioned schemas), `:57-58` (contract
  tests against `contracts/`).
