# payment · T24 · Phase 12 — Specification Verification

Consumes: all prior T24 artifacts (00–11, including `11-test-review-resolution.md`).

Spec sources:
- `requirements.md:56` (R31)
- `tasks.md:42` (task 24)
- `package.md:108` (named test)
- `design.md` §4c (pinned `receipt-issued` schema, partition-key note)
- `agents.md:26, 38-39, 57-58`

Final state: commit `fd139e70`, full suite **490/490 green**.

## Traceability matrix

| Requirement / AC | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R31 (a):** responses conform to `payments.yaml` | Yes | `contracts/api/payments.yaml` (4 paths at `:17`, `:51`, `:75`; 4 component schemas at `:150`, `:177`, `:215`, `:252`) | `PaymentsOpenApiContractTest.shouldConformToPaymentsOpenApiContract` (`:380`), which runs 21 sub-checks | No | No |
| **R31 (b):** every published event conforms to its schema | Yes | `contracts/events/payments/{invoice-created,payment-seen,payment-finalized,receipt-issued}.v1.schema.json` | `PaymentEventPayloadContractTest` `:51`, `:64`, `:75`, `:89` | No | No |
| **Task 24:** author `payments.yaml` | Yes | as above | as above | No | No |
| **Task 24:** four event schemas | Yes | 4 files; topics match `EventTopics.java:13-16` | `everySchemaHasTheFrozenIdAndTitle` (`:100`) | No | No |
| **Task 24:** "amounts as decimal strings" (L6, `agents.md:26`) | Yes | every `amount` is `type: string`; the request also has `pattern` (`payments.yaml:165`) | `assertTypeMatches` (both classes); `createInvoiceRequestAmountPatternAdmitsOnlyPositiveWholeBaseUnits`; `everyAmountDescriptionStatesWholeBaseUnits`; `nonPinnedAmountDescriptionsStateWholeBaseUnits` (`:120`) | No | No |
| **Task 24:** contract tests mirror `UserLifecycleEventPayloadContractTest` | Yes, extended | plain Jackson, no Spring, no Testcontainers; also checks both directions, type/format and nullability | both classes | No | Stricter than the sibling (by design, Phase 4) |
| **Named test** `shouldConformToPaymentsOpenApiContract` (`package.md:108`) | Yes | `PaymentsOpenApiContractTest.java:380` | itself | No | No |
| **AC1:** 4 routes plus component shapes | Yes | `payments.yaml` | the completeness, shape, path-param, export, and request/response tests | No | No |
| **AC2:** event schemas field-for-field, `additionalProperties: false`, exact `required` | Yes | 4 schemas | 4 `*MatchesItsSchema` tests | No | `receipt-issued` keeps the pinned 6-field `required` (G1, human-approved) |
| **AC3:** money is `type: string` | Yes | all 5 files | see "amounts" row | No | No |
| **AC4:** fails on drift (self-guards) | Yes | — | both completeness directions; `expected*Cover*` guards; mutation-verified in Phases 6, 9 and 11 | No | No |
| **AC5:** exactly one new dependency; suite green | Yes | `services/payment/pom.xml` (`jackson-dataformat-yaml`, test scope) | `T01SkeletonRegressionTest` (tripwire flipped); 490/490 | No | Flipping the T01 tripwire touched a file outside the brief (Phase 6, deviation 0, T08 precedent) |
| **AC6:** string `amount` deserializes | Yes | — | `createInvoiceRequestAcceptsADecimalStringAmount` | No | No |
| **AC7:** Java type ↔ type/format; enums | Yes | `format: uuid` / `date-time`; `chain` and `state` enums | `assertTypeMatches` ×2; `invoiceStateSchemaEnum…`; `createInvoiceRequestChainEnum…`; `…LimitsMatchItsValidationAnnotations` | No | No |
| **AC8:** `bearerAuth: [MERCHANT]` everywhere (L10) | Yes | `payments.yaml:22, 40, 59, 86` | `everyOperationRequiresBearerAuthWithMerchantScope` | No | No |
| **AC9:** `Problem.type` enum = `ProblemTypes` (G3) | Yes | `payments.yaml:252+` | `problemTypeEnumMatchesProblemTypesCatalogue`; `problemExtensionMembersMatchWhatTheHandlersEmit` | No | No |
| **AC10:** real partition key documented (G2) | Yes | each schema's `description` | description assertion in each `*MatchesItsSchema`; real keys asserted by `InvoiceServiceTest:96`, `PaymentServiceTest` (seen, finalized; tightened in Phase 11), `ReceiptServiceTest:292` | No | The descriptions state the code's keys, not `design.md`'s "invoiceUuid for all" (G2, human-approved; logged as a follow-up) |
| **OpenAPI structural validity** | Yes | — | `paymentsYamlIsStructurallyValidOpenApi3` | No | No validator library (AC5) |
| **`agents.md:38`:** schemas versioned, backward-compatible only | Yes | `.v1.` filenames; `$id` and `title` carry schema_version 1 | `everySchemaHasTheFrozenIdAndTitle` | No | No |
| **`agents.md:57-58`:** contract tests validate payloads against `contracts/` | Yes | both classes read the real files from `../../contracts/` | — | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes.
- **Contracts:** `payments.yaml` and the four event schemas exist and describe the service as
  built, verified by direct source reads in every phase.
- **Tests:** the named test exists and runs 21 sub-checks, and the event contract test covers all
  four events.
- **Suite:** green at 490/490.
- **Production code:** none changed, as frozen.

**(2) Does it satisfy every acceptance criterion?** Yes, AC1–AC10, each with at least one
dedicated test. The drift guards were mutation-checked, not just observed passing.

**(3) Does it violate any LOCKED decision?** No.
- **L6:** money is a string everywhere.
- **L8:** exactly the four outbox events are documented.
- **L10:** every route is MERCHANT-scoped.
- **L12:** the tests import no entity across a module boundary.

Two departures from `design.md`'s text were human-approved at Phase 4 and are faithful to the
code:
- **G1:** this one *follows* the spec's pinned schema.
- **G2:** the partition-key wording reflects what the code does, and the drift is logged.

**(4) Remaining risks.**
- **G2 partition-key drift.** This is a pre-existing spec-vs-code mismatch, not introduced by
  T24.
  - `design.md` says every payments event is keyed by `invoiceUuid`. The code keys
    `payment.*` by `paymentUuid` and `receipt.issued` by `receiptUuid`.
  - Consumers that need per-invoice ordering across event types won't get it.
  - The contracts now state the real keys, and the tests pin them, so the eventual decision is
    deliberate. The owner is the human.
- **`agents.md:39`, "models are generated from `contracts/`, never hand-written".**
  - This service's DTOs and payload records were hand-written in tasks T04–T22, before any
    contract existed. T24 was barred from changing production code, and sibling services
    (auth, crypto, notification) follow the same hand-written-plus-contract-test pattern.
  - The contract tests make the hand-written models provably conform. That satisfies R31's
    conformance clause but not the generation rule's letter.
  - This is a platform-level standing-rule gap, not a T24 defect. I'm flagging it for the human
    rather than deviating silently.
- **`CreateInvoiceRequest.tokenDecimals` is a primitive `short`.** An omitted value
  deserializes to `0` instead of being rejected. The contract marks it required, so conforming
  clients are unaffected, but the server is laxer than its contract. Fixing it would mean
  changing the DTO, which is out of T24's scope.
- **Lower-severity notes:**
  - The OpenAPI 3.0 `Accept` header parameter is ignored by tooling. It is kept, with a note
    (human choice).
  - The structural check is hand-rolled, not a full validator.

**PASS:** every R31/task-24 obligation and AC1–AC10 is implemented and tested (490/490 green),
with no LOCKED-decision violation; the three remaining risks are pre-existing or out of scope
and are logged.
