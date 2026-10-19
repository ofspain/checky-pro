# payment · T24 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi)

Approved by the human via `AskUserQuestion`, 2026-10-19 ("Approve as proposed"). Every gap was
checked against the existing tests before disposition.

## Disposition of Kimi's 7 gaps

| # | Gap | Disposition | Change / reason |
|---|---|---|---|
| 1 | CSV column test only checks a substring | **ACCEPTED** | See note 1. |
| 2 | `Content-Disposition` template not checked against the controller | **REJECTED** | Already tested against real behavior by `ExportControllerTest.shouldAddContentDispositionHeaderForCsvResponse` (`:130-140`). String-matching the contract's prose to the controller's code would test wording, not behavior. |
| 3 | Partition-key check only reads the description | **ACCEPTED** | See note 2. |
| 4 | No OpenAPI structural validity check | **ACCEPTED, dependency-free** | See note 3. |
| 5 | DTO schemas lack `additionalProperties: false` | **REJECTED** | Same as Phase 8 Kimi #7, already rejected at Phase 9, and the premise is still wrong. Properties already equal the record's components in both directions, so an added field already fails. `false` would also be inaccurate for the request, since Spring ignores unknown fields. |
| 6 | Outbound amounts lack a pattern or whole-unit check | **ACCEPTED, partial** | See note 4. |
| 7 | `format`-over-`Accept` precedence text not tested | **REJECTED** | The behavior is already tested by `ExportControllerTest.shouldPreferFormatParamOverConflictingAcceptHeader` (`:194`), plus the `format`/`Accept` selection tests at `:111-203`. A prose assertion adds nothing. |

Notes on the accepted gaps:

1. **Gap 1.** `csvDescriptionListsTheExportColumnsInRecordOrder` now parses the list after
   `columns in this order:` up to the period. It asserts `containsExactlyElementsOf` the
   `TransactionExportRow` components, so an extra, missing, or reordered column fails.
2. **Gap 3.** In `PaymentServiceTest`, `shouldEmitPaymentSeenOnFirstSeen` and
   `shouldPersistAddressPoisoningFlagWhenTrue` now capture the publish key and assert it equals
   `payload.paymentUuid().toString()`. Before, both accepted any key (`any(String.class)`).
   `invoice.created` and `receipt.issued` keys were already asserted exactly
   (`InvoiceServiceTest:96`, `ReceiptServiceTest:292`), so all four keys are now guarded against
   the code. This is the current behavior; if the G2 follow-up re-keys, these tests change on
   purpose. A second, identical finalized assertion (`:397`) was left as is, because one exact
   key check per event is enough.
3. **Gap 4.** New `paymentsYamlIsStructurallyValidOpenApi3`. It asserts:
   - `openapi` is `3.0.3`
   - `info.title` and `info.version` are present
   - every operation has non-empty `responses`
   - every security requirement names a defined scheme
   - every `$ref` is local and resolves by JSON pointer

   `swagger-parser` was rejected because it would break AC5's "exactly one new dependency".
4. **Gap 6.**
   - New `PaymentsOpenApiContractTest.everyAmountDescriptionStatesWholeBaseUnits`, covering the
     3 DTO schemas.
   - New `PaymentEventPayloadContractTest.nonPinnedAmountDescriptionsStateWholeBaseUnits`,
     covering `invoice-created` and `payment-finalized`. `receipt-issued` is excluded because
     its schema is pinned (G1).
   - No outbound `pattern`, per the Phase 9 decision.

## Files touched

- `services/payment/src/test/java/com/themistra/payment/common/PaymentsOpenApiContractTest.java`
  - 1 test changed, 2 tests added, 1 helper added (`collectRefs`)
  - both new tests added to the named umbrella test
- `services/payment/src/test/java/com/themistra/payment/events/PaymentEventPayloadContractTest.java`
  - 1 test added
- `services/payment/src/test/java/com/themistra/payment/payment/PaymentServiceTest.java`
  - 2 existing tests tightened
  - This file is outside T24's own files. It is in scope because Gap 3 was human-approved, and
    the change is test-only.

No production code or contract file was changed in this phase.

## Verification

- `mvn -o test` (`services/payment`): **490/490 green**. That is Phase 10's 487 plus 3 new tests.
- **Mutation check.** I made two temporary edits to `payments.yaml` and restored the file from a
  scratch copy afterwards:
  - Breaking the `TransactionExportRow` `$ref` made `paymentsYamlIsStructurallyValidOpenApi3`
    fail with `$ref #/components/schemas/TransactionExportRo resolves`.
  - Appending `, extra` to the CSV column list made
    `csvDescriptionListsTheExportColumnsInRecordOrder` fail.
  - The run reported 4 failures and 0 errors. The extra 2 failures are the named umbrella test
    and the response-schema test, both hit by the broken `$ref`.

## Open Questions

- **The G2 partition-key drift follow-up stays open.** Its own self F6 sub-item (the weak
  description-only guard) is now **closed** by Gap 3.
- **The `tokenDecimals` primitive-default note** is carried forward unchanged.
