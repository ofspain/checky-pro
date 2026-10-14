# payment · T07 · Phase 9 — Review Resolution

Human Approval gate. Decision made by the human (approved 2026-10-14); this artifact records it.

## Finding 1 — Read endpoints have zero automated tests

**REJECTED as a Phase 9 action — same disposition as every prior task's own identical
finding.** The required test files do not exist yet by design; Phase 10 writes them.

## Finding 2 — Full-view `InvoiceResponse` mapper not exercised in controller tests

**REJECTED, same reason as Finding 1.** Routes into Phase 10's own GET-endpoint tests.

## Finding 3 — `list` returns a bare JSON array

**ACCEPTED as written; no action required.** Already an explicit, disclosed scope decision
(Phase 3 finding 6 / frozen brief's own "Out: pagination") — a forward-compatibility note, not
a defect.

## Finding 4 — Role-enforcement test only covers `POST`

**REJECTED, same reason as Finding 1.** `shouldRejectNonMerchantCallerOnReadEndpoints` is
already named in the frozen brief's own Required Tests; Phase 10 writes it.

## Finding 5 — No test proves the identical 404 shape for cross-merchant vs nonexistent UUID

**REJECTED, same reason as Finding 1.** `shouldReturn404ForNonOwningMerchant`/
`shouldReturn404ForNonexistentInvoiceUuid` are already named; Phase 10 writes them.

## Finding 6 — Positive confirmations

**ACCEPTED as written — no action required.** Notably confirms, from a real Docker-enabled
run, that `SchemaBaselineMigrationIntegrationTest` now passes with V3 (18 tests) — empirically
validating T06 Phase 11's own migration-test fix, which could not be re-run in that session.

## Net result

**Nothing was accepted that required a code or test change in this phase.** All substantive
findings route into Phase 10. No file was modified in this phase. Proceeding to Phase 10.
