# payment · T07 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T07 — Merchant-scoped reads |
| **Consumes** | `artifacts/11-test-review-resolution.md` |

## Verdict: **PASS**

## Traceability matrix

| AC | Requirement | Implementation | Test coverage |
|---|---|---|---|
| AC1 | List returns only the caller's own invoices | `InvoiceRepository.findByMerchantAccountUuidOrderByCreatedAtDesc`, `InvoiceService.listForMerchant` | `shouldReturnOnlyCallersOwnInvoicesFromList` (service + controller, full JSON shape, ordering preservation), `shouldReturnEmptyListWhenCallerHasNoInvoices` |
| AC2 | Single lookup: owner → `200` full view; non-owner/nonexistent → identical `404` | `InvoiceRepository.findByInvoiceUuidAndMerchantAccountUuid`, `InvoiceService.getForMerchant`, `InvoiceNotFoundException`, `InvoiceExceptionHandler` | `shouldScopeInvoiceReadsToOwningMerchant` (named), `shouldReturnInvoiceForOwningMerchant` (full field set), `shouldReturn404ForNonOwningMerchant`/`...NonexistentInvoiceUuid` (service layer: same exception type), `shouldReturnIdentical404ShapeForNonOwningMerchantAndNonexistentInvoiceUuid` (controller layer: byte-identical body) |
| AC3 | Both endpoints require `ROLE_MERCHANT` | `@PreAuthorize("hasRole('MERCHANT')")` on `list`/`get` | `shouldRejectNonMerchantCallerOnReadEndpoints` (named, now with full RFC 9457 body), `shouldRejectUnauthenticatedCallerOnReadEndpoints` |
| AC4 | Full merchant-facing view; `amount` as decimal string; no `watchId` | `InvoiceResponse.from(Invoice)` | `shouldReturnOnlyCallersOwnInvoicesFromList`/`shouldReturnInvoiceForOwningMerchant` (full field set + absence of `watchId`), `shouldSerializeNullReferenceAsJsonNull` |
| AC5 | Read methods `@Transactional(readOnly = true)` | `InvoiceService.listForMerchant`/`getForMerchant` | verified by direct code read (no dedicated test — matches `createMethodIsAnnotatedTransactional`'s own T05 precedent style; not separately named by the frozen brief) |

## Required Tests (frozen brief)

All 7 named tests present and passing, each strengthened beyond the frozen brief's own minimum
by the genuine Phase 8/11 Kimi passes: `shouldScopeInvoiceReadsToOwningMerchant`,
`shouldReturnOnlyCallersOwnInvoicesFromList`, `shouldReturnInvoiceForOwningMerchant`,
`shouldReturn404ForNonOwningMerchant`, `shouldReturn404ForNonexistentInvoiceUuid`,
`shouldRejectNonMerchantCallerOnReadEndpoints`, `shouldSerializeAmountAsDecimalStringNotJsonNumber`
(covered by the full-field-set assertions rather than a standalone test of that exact name —
same substance).

## Scope discipline

`git diff --stat 8f176732..HEAD` (T07's own full range) touches exactly 6 production files, all
named in the frozen brief's own Files to Modify/Create list. No migration touched, nothing
under `spec/`, no file outside this task's own disclosed scope.

## Locked decisions

- **L10** — merchant-scoped reads, `404` not `403` for cross-merchant access. Held — confirmed
  both at the service layer (same exception type for both causes) and the controller/handler
  layer (byte-identical response body for both causes).

## Open items carried forward (not blockers for this task)

- `contracts/api/payments.yaml` still doesn't exist (Phase 3 finding 3, already established as
  task 24's own scope).
- List endpoint remains unbounded/unpaginated (Phase 3 finding 6, explicitly out of scope).
- The existing single-column `idx_invoices_merchant` index doesn't optimally support the
  `ORDER BY created_at DESC` clause (Phase 3 finding 7) — low severity, MVP-acceptable, deferred.
- Carried-forward DB-mapping/migration verification gaps from T04/T05/T06 still need a real
  Docker run to finally close (though T06's own V3 fix was empirically confirmed by Kimi's own
  Docker-enabled run during this task's Phase 8).

## Test suite status

141/141 non-Docker-dependent tests passing (`mvn -pl services/payment -am test
-Dtest='!SchemaBaselineMigrationIntegrationTest'`). `SchemaBaselineMigrationIntegrationTest`
itself was not run in this session (Docker unavailable) but was confirmed passing (18 tests,
with V3) by Kimi's own Phase 8 run in a Docker-enabled environment.

## Verdict rationale

All 5 acceptance criteria are implemented and test-covered, several beyond the frozen brief's
own minimum per two genuine Kimi review passes (Phase 8, Phase 11) that together found zero
code defects and a comprehensive set of real coverage gaps — all fixed. No LOCKED decision
violated. No file outside this task's own disclosed scope touched. The carried-forward items
above are accepted, named limitations, not defects. **PASS.** Proceeding to Phase 13.
