# payment · T24 · Phase 9 — Review Resolution

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`.
Approved by the human via `AskUserQuestion`, 2026-10-19: "Approve as proposed", and on the
Accept parameter, "Keep, add a note".

Kimi's 7 findings overlap my 6 self-review findings almost one-to-one, so the two are resolved
together. Every cited file:line was re-checked against source before disposition.

## Resolution log

| Self | Kimi | Topic | Disposition | Exact change |
|---|---|---|---|---|
| F1 | #1 | `amount` described as "positive decimal string"; service accepts whole base units only (`InvoiceService.java:214`) | **ACCEPTED** | See notes 1–3 below. |
| F2 | #2 | AC6 test used invalid value `"10.5"` | **ACCEPTED** | `PaymentsOpenApiContractTest.createInvoiceRequestAcceptsADecimalStringAmount` now uses `"1000000"`. Javadoc says why. |
| F4 | #3 | `Problem` omits `violations` and `trace_id` | **ACCEPTED** | See note 4 below. |
| F3 | #4 | OpenAPI 3.0 ignores a header parameter named `Accept` | **ACCEPTED as "keep, add note"** (human choice) | See note 5 below. |
| F5 | #5 | `operation()` NPE on a missing route | **ACCEPTED** | See note 6 below. |
| — | #6 | Validation limits not verified against annotations | **ACCEPTED, partial** | See note 7 below. |
| — | #7 | Add `additionalProperties: false` to DTO schemas | **REJECTED** | See note 8 below. |
| F6 | — | Partition-key check only reads the description | **DEFERRED** | No change. It goes with the G2 partition-key follow-up (Open Questions). |

Notes on each change:

1. **F1 / Kimi #1, `CreateInvoiceRequest.amount` in `payments.yaml`.**
   - Added `pattern: '^[1-9][0-9]*$'`.
   - The description now reads "positive whole number of token base units, as a decimal string
     (never a JSON number). Fractional values are rejected."
   - The "JSON number is also accepted" note is removed. The server still binds numbers, but
     the contract states only the canonical L6 form.
2. **F1 / Kimi #1, other `amount` descriptions.** `InvoiceResponse.amount` and
   `TransactionExportRow.amount` now say "whole number of token base units". So do the `amount`
   descriptions in `invoice-created.v1.schema.json` and `payment-finalized.v1.schema.json`.
3. **F1 / Kimi #1, one deliberate omission.** `receipt-issued`'s amount description is
   unchanged, because it is `design.md` §4c's pinned text (G1). No outbound `pattern` was added;
   the human approved descriptions only for outbound fields.
4. **F4 / Kimi #3, `Problem` schema.** Added two optional properties:
   - `violations`: an array of `{field, message}`, both required strings. This matches
     `ApiExceptionHandler.Violation` (`:44`, used at `:55` and `:67`).
   - `trace_id`: a string, emitted at `:131`.

   Neither is added to `required`. No per-route error documentation was added (G3 scope kept).
5. **F3 / Kimi #4, `Accept` parameter.** The parameter stays, as frozen in Phase 4 (Finding 9).
   Its description now says it is documentation only under OpenAPI 3.0 §4.7.12.1, and that the
   rule is also stated in the operation description. The test is unchanged.
6. **F5 / Kimi #5, `operation()`.** It now asserts that the path exists and then that the method
   exists, with messages naming the route. To verify, I renamed `{invoiceUuid}` to
   `{invoiceId}`: the result was 5 failures and 0 errors, and each message reads
   `payments.yaml documents path /api/v1/invoices/{invoiceUuid}`. The file was restored from a
   scratch copy and confirmed unchanged.
7. **Kimi #6 (partial), new test `createInvoiceRequestLimitsMatchItsValidationAnnotations`.** It
   asserts the YAML's limits equal the annotation values read by reflection from the record's
   fields:
   - `tokenDecimals.minimum` equals `@Min.value`
   - `tokenDecimals.maximum` equals `@Max.value`
   - `reference.maxLength` equals `@Size.max`

   The test is added to the named umbrella test, with one private helper,
   `fieldAnnotation(...)`. The chain enum was already covered by
   `createInvoiceRequestChainEnumMatchesItsValidationPattern`. The rejected part was a generic
   annotation-to-constraint engine inside `assertTypeMatches`: it would be more machinery than
   three fields justify.
8. **Kimi #7, rejected.** The premise is factually wrong. `everyComponentSchemaMatchesItsRealRecordShape`
   already asserts the schema `properties` set **equals** the record's components in both
   directions. So an extra field added to a response record already fails the suite; this was
   confirmed by the Phase 6 mutation check and the 18-test class. On the request side, `false`
   would also be inaccurate: Spring Boot's default `FAIL_ON_UNKNOWN_PROPERTIES=false` means the
   server *accepts* unknown fields. On the response side, `false` makes strict generated clients
   break when a field is added later, an additive and otherwise non-breaking change. Neither
   sibling contract (`auth.yaml`, `notifications.yaml`) uses it on DTO schemas.

## Files touched in this phase

- `contracts/api/payments.yaml`
- `contracts/events/payments/invoice-created.v1.schema.json`
- `contracts/events/payments/payment-finalized.v1.schema.json`
- `services/payment/src/test/java/com/themistra/payment/common/PaymentsOpenApiContractTest.java`

All four are files this task created. No production code, no `spec/` file, and no renamed class
or public API.

## Verification

- `mvn -o test` (`services/payment`): **484/484 green**. That is Phase 6's 483 plus the one new
  limits test.
- F5 mutation check: passed (note 6).

## Open Questions

Carried forward unchanged. Both are owned by the human and out of T24 scope:

1. **The partition-key drift (G2).** Self F6, the weak description-only guard, is now attached
   to it.
2. **`tokenDecimals` is a primitive `short`.** An omitted value silently becomes `0`.

**Phase 9 sign-off:** human-approved, as recorded above. The next phase is 10 (test generation).
