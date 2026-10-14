# payment · T05 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch, mirroring every prior task's own identical
convention. This artifact is the consolidated summary of that already-committed history, not a
pending commit — `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T05: invoice domain (Invoice, InvoiceState, InvoiceService.create)`

## Commit message

```
payment-service T05: invoice domain (Invoice, InvoiceState, InvoiceService.create)

Adds the service's first domain entity: Invoice, mapping the real, immutable
invoices table (state as @Enumerated(STRING), amount as @Column(precision=78,
scale=0), tokenDecimals as short - Phase 3 finding 2); InvoiceState, the
6-value enum matching chk_invoice_state verbatim; InvoiceRepository
(package-private, no custom queries needed); and InvoiceService.create(...)
(@Transactional, forward-looking for task 6's outbox emit in the same
transaction - Phase 3 finding 5), which validates R3 (chain in
{ETHEREUM,TRON}, non-blank tokenContractAddress) and R4 (amount null,
non-positive, non-integral, or exceeding the schema's own 78-digit NUMERIC
capacity) via InvalidInvoiceRequestException (public, unlike crypto-service's
own package-private equivalent - Phase 3 finding 1, since the future
InvoiceController lives in a different package).

This service's first ArchitectureTest also lands here: one L6 rule (no
float/double/Float/Double field, method return type, method parameter,
constructor parameter, or array form, anywhere under com.themistra.payment),
using the plain-ArchRule-field + @Test-canary shape (never @ArchTest, which
this repository's Surefire silently reports as 0 tests for the whole class -
confirmed by services/notification's own prior empirical reproduction).
Proven to actually fail via RogueFloatingPointMoneyUser, a 5-way-violating
test fixture.

Kimi 2.7's independent code review (Phase 8) found 2 required-but-deferred
tests (correctly routed to Phase 10, same disposition as every prior task)
and 2 disclosed, accepted observations (an unreachable Clock-bypass fallback
matching OutboxEvent's own precedent; an NPE-vs-business-exception
distinction that is the frozen brief's own intentional design). Kimi's test
review (Phase 11) then found 7 real gaps in the Phase 10 suite; 5 fixed - a
save-invocation verification, an exact-78-digit boundary test, a
@Transactional source-text guard, and two ArchUnit rule gaps (constructors
and array types) closed with matching fixture extensions. The remaining 2
(DB-mapping empirical verification, blocked on Docker being unavailable this
session; the Invoice lifecycle fallback, untested for the same reason
OutboxEvent's own analogous fallback is untested) were declined, consistent
with established precedent.

Spec: spec/payment-service/tasks.md task 5. Requirements: R3, R4. Locked
decisions: L6 (money is NUMERIC/BigDecimal only, now also ArchUnit-enforced
across fields/returns/parameters/constructors/arrays).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceState.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvalidInvoiceRequestException.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`
- `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RogueFloatingPointMoneyUser.java`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceTest.java`

**Process artifacts (not application code)**
- `.ai/prompts/payment/T05/artifacts/00-*.md` through `12-*.md` — the full 14-phase pipeline
  record for this task (this file is `13-*.md`).

## Summary

`payment-service` now has its first domain entity and its first ArchUnit-enforced standing rule.
No controller, DTO, or watch registration exists yet — those are task 6's scope. The invoice
created by `InvoiceService.create` is not yet reachable by any caller outside a test; task 6's
`InvoiceController` is the first real caller.

## Testing performed

- `mvn -pl services/payment -am compile` — clean throughout.
- 10 new tests added this task (2 `ArchitectureTest`, 8 `InvoiceServiceTest`).
- **95/95 non-Docker-dependent tests passing** (`mvn -pl services/payment -am test
  -Dtest='!SchemaBaselineMigrationIntegrationTest'`), up from 85 before this task.
  `SchemaBaselineMigrationIntegrationTest` (17 tests, T02, unaffected by this task) was not run
  this session — Docker unavailable — but last confirmed green before T04.
- Mutation tests across Phases 10 and 11 on: a real `double` field added to `InvoiceService`
  (ArchUnit canary), the amount-scale check, the `expiresAt` computation (`ofHours` vs
  `ofMinutes`), bypassing `repository.save`, an off-by-one in the 78-digit boundary, and removing
  `@Transactional` — all six caught their introduced defect, all six reverted clean.

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 5 — "Invoice domain."
- **Requirement IDs:** R3, R4.
- **LOCKED decisions:** L6 (money is `NUMERIC`/`BigDecimal` only).

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T05.**
