# payment · T24 · Phase 0 — Repository Understanding

Consumes: — (entry phase)

## 1. Architecture summary

Unchanged from prior grounding. This task touches none of this service's persistence/business
logic — it authors documentation artifacts (`contracts/`) and adds contract-conformance tests
that read those artifacts and this service's already-built classes via reflection/Jackson, no
Spring context needed for either new test class.

## 2. Existing code this task touches

**Already exists — every HTTP endpoint and published event this task must document:**
- `invoice.InvoiceController` — `POST /api/v1/invoices` (R1/R2), `GET /api/v1/invoices` (R28),
  `GET /api/v1/invoices/{invoiceUuid}` (R28).
- `export.ExportController` — `GET /api/v1/exports/transactions` (R27).
- Four outbox event payload records, all already built, all already using decimal-string
  amounts (L6) — confirmed field-for-field by direct read, not assumed:
  - `invoice.event.InvoiceCreatedPayload` (`invoiceUuid, merchantAccountUuid, chain,
    tokenContractAddress, tokenDecimals (short), amount, payToAddress, reference, expiresAt`).
  - `payment.event.PaymentSeenPayload` (`paymentUuid, invoiceUuid, chain, txHash,
    tokenContractAddress, confirmations (int), occurredAt`) — deliberately no `amount`
    (`chain.tx.seen` itself carries none).
  - `payment.event.PaymentFinalizedPayload` (`paymentUuid, chain, txHash, fromAddress, toAddress,
    tokenContractAddress, amount, confirmations (int), addressPoisoningFlag (boolean),
    occurredAt`) — deliberately no `invoiceUuid` (see its own Javadoc).
  - `receipt.event.ReceiptIssuedPayload` (`invoiceUuid, paymentUuid, receiptUuid,
    merchantAccountUuid, chain, txHash, amount, tokenContractAddress, kmsKeyId, receiptUri,
    occurredAt`) — already frozen field-for-field against `design.md`'s own pinned schema at
    T19 (its own Javadoc says so directly).
- `design.md` already pins the **full** JSON Schema for `payments.receipt.issued` verbatim
  (lines 328-348) and states the other three "follow the same envelope shape... author them
  under `contracts/events/payments/`" — a template, not a second full pinned schema each.

**New for this task (none exist yet, confirmed by `ls`/`find`):**
- `contracts/api/payments.yaml`.
- `contracts/events/payments/{invoice-created,payment-seen,payment-finalized,receipt-issued}.v1.schema.json`
  (the directory `contracts/events/payments/` itself doesn't exist yet either).
- Two new test classes (see §4).

## 3. Established patterns to follow

**A direct, already-built, three-service-deep precedent exists for exactly this task's shape —
mirror it, don't invent:**
- **Event-payload contract test**: `auth/account/event/UserLifecycleEventPayloadContractTest`
  (the task's own named pattern reference) — plain Jackson, no JSON-Schema-validation library,
  asserts every `required` field is present and every serialized field is declared
  (`additionalProperties: false` enforced structurally, not via a validator library).
- **OpenAPI contract test**: `auth/common/AuthOpenApiContractTest`,
  `crypto/common/CryptoInternalOpenApiContractTest`,
  `notification/common/NotificationOpenApiContractTest` — all three follow one identical shape:
  reflection over `@RequestMapping` to enumerate real controller routes, a plain
  `YAMLMapper`/Jackson read of the contract file, bidirectional route-completeness checks,
  per-component schema-shape matching against a real instance, a security-scheme check, and a
  self-guarding `expectedResponseSchemasCoverEveryControllerRoute` test preventing silent drift.
  `notification`'s own version (smallest, 2 routes) is the most direct size-analog for this
  task's own 4 routes.
- **Scoped to success responses only** — all three existing OpenAPI contracts explicitly say so
  (mirrors "R47" precedent language in each): error shapes are `ApiExceptionHandler`'s own
  cross-cutting concern, not endpoint-specific, and out of scope for the contract file itself.
  `ProblemTypes` (T23) is the real stable-type backing for any 4xx `type` field a future
  consumer might want documented — not this task's own job to wire into the OpenAPI file.

**A dependency was explicitly pre-planned for this exact task, confirmed by a standing comment
in the POM itself, not inferred**: `services/payment/pom.xml` (lines 88-90) reads verbatim:
*"jackson-dataformat-yaml still deliberately NOT added - lands at task 24 (contracts/api/
payments.yaml parsing for the OpenAPI contract test)."* This is the one new dependency this task
needs to add.

**`contracts/README.md`'s own "generated in CI" claim does not match reality anywhere in this
platform** — confirmed by direct check: `libs/ts/api-client/` contains only a `.gitkeep`, and
`.github/workflows/ci.yml` has zero `openapi`/`codegen` references. Every sibling service's own
contract files (`auth.yaml`, `crypto-internal.yaml`, `notifications.yaml`, all four `events/`
directories) already exist and are already verified only by hand-written contract tests exactly
like the ones this task must add — never by actual codegen. This is a platform-wide, pre-existing
discrepancy between the README's own aspirational framing and current reality, not something
this task introduces, breaks, or is expected to fix.

## 4. Testing conventions

- `PaymentsOpenApiContractTest` (new, mirrors `NotificationOpenApiContractTest` exactly) and four
  payload-specific `*ContractTest` classes (new, mirror `UserLifecycleEventPayloadContractTest`),
  or one combined class covering all four payloads — exact shape decided at Phase 2.
- No Spring context, no Testcontainers needed for either kind of contract test — pure reflection
  + Jackson/YAMLMapper, confirmed by every existing sibling example.
- Named test: `shouldConformToPaymentsOpenApiContract` (`package.md` §8) → R31.

## 5. Known gaps / unknowns

- I do not know yet whether a single combined event-contract test class (covering all four
  payloads) or four separate classes (mirroring `auth`'s own one-class-per-payload-type
  granularity — confirmed by `find`: `UserLifecycleEventPayloadContractTest` and
  `EmailRequestedEventPayloadContractTest` are two separate files there, even though `auth` has
  a third schema file, `security-audit.v1.schema.json`, with no corresponding contract test at
  all — a pre-existing, undisclosed gap in `auth-service`'s own coverage, not a convention to
  follow) is the right shape here — to be decided directly at Phase 2 (a low-risk, pure
  structural choice).
- I do not know the exact `operationId`/response-schema-naming conventions to use for the four
  new `payments.yaml` paths beyond what the three sibling files already establish generally —
  to be resolved directly at Phase 2, no genuine ambiguity found requiring `AskUserQuestion`.
