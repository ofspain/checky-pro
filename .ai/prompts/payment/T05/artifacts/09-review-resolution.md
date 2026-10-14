# payment · T05 · Phase 9 — Review Resolution

Human Approval gate. Decision made by the human (approved 2026-10-14); this artifact records it.

## Finding 1 — "Required `InvoiceServiceTest` is missing"

**REJECTED as a Phase 9 action — same disposition as every prior task's own identical finding**
(`payment` T02 Phase 9 finding 1, T04 Phase 9 finding 1). The observation is factually correct;
it is the deliberate consequence of Phase 6's own test-exclusion rule. Phase 10 writes it.

## Finding 2 — "Required `ArchitectureTest` is missing"

**REJECTED, same reason as Finding 1.** Phase 10 writes it.

## Finding 3 — "`Invoice`'s `@PrePersist`/`@PreUpdate` fallback uses `Instant.now()`"

**ACCEPTED as a correct observation; NO code change made.** Confirmed this is unreachable
through `InvoiceService.create` (the only current caller). Matches `OutboxEvent`'s own
identical, already-accepted T04 limitation — a JPA entity cannot hold an injected `Clock`. Not
diverging from that precedent for `Invoice` alone.

## Finding 4 — "`merchantAccountUuid`/`payToAddress` throw `NullPointerException`, not the typed business exception"

**ACCEPTED as written; no action required.** The frozen brief explicitly distinguishes
spec-named validations (R3/R4, throw `InvalidInvoiceRequestException`) from defensive null
guards on parameters no requirement names — this is the brief's own intentional design, not a
defect against it. Noted for task 6: when `InvoiceController`/the DTO layer is built, consider
whether bean validation on the request DTO makes this distinction moot before it ever reaches
`InvoiceService`.

## Finding 5 — `Invoice` entity mapping matches the schema

**ACCEPTED as written — positive confirmation, no action required.**

## Finding 6 — Amount validation covers all four R4 cases correctly

**ACCEPTED as written — positive confirmation, no action required.**

## Finding 7 — `InvalidInvoiceRequestException` public, `create(...)` `@Transactional`

**ACCEPTED as written — positive confirmation, no action required.**

## Net result

**Nothing was accepted that required a code or test change in this phase.** Findings 1 and 2
route into Phase 10. No file was modified in this phase. Proceeding to Phase 10, where
`InvoiceServiceTest.java` and `ArchitectureTest.java` are written.
