# payment · T05 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T05 — Invoice domain |
| **Consumes** | `artifacts/05-implementation-plan.md` |

## Files created

- `invoice/InvoiceState.java` — 6-value enum, matches `chk_invoice_state` verbatim.
- `invoice/InvalidInvoiceRequestException.java` — public (Phase 3 finding 1).
- `invoice/Invoice.java` — all 13 non-id columns mapped: `state` → `@Enumerated(STRING)`,
  `amount` → `@Column(precision=78, scale=0)`, `tokenDecimals` → `short`. `createdAt`/
  `updatedAt` are set explicitly by `create(...)` from the `Instant` passed in (sourced from
  `InvoiceService`'s injected `Clock`); `@PrePersist`/`@PreUpdate` are a last-resort
  `Instant.now()` fallback only, mirroring `OutboxEvent`'s own established T04 pattern — a JPA
  entity cannot itself hold an injected `Clock`.
- `invoice/InvoiceRepository.java` — package-private, no custom query methods (none needed by
  `create(...)`).
- `invoice/InvoiceService.java` — `create(...)`, `@Transactional` (Phase 3 finding 5);
  `validateChainAndTokenAddress` (R3) and `validateAmount` (R4, including the 78-digit-
  precision check, Phase 3 finding 4) as private static helpers.

## Not written this phase (deliberately deferred)

- `ArchitectureTest.java` and `InvoiceServiceTest.java` — both are test files; Phase 10's job,
  not Phase 6's, per the established discipline (and the T03 mistake this session already
  disclosed and corrected for).

## Verification performed this phase

- `mvn -pl services/payment -am compile` — clean.
- `mvn -pl services/payment -am test -Dtest='!SchemaBaselineMigrationIntegrationTest'` —
  **85/85 passing, unchanged** from before this task (no existing test touches `Invoice`; this
  confirms no accidental regression from adding the new package).

## Traceability to frozen brief

- AC1 (R3) / AC2 (R4) — implemented in `InvoiceService`'s two private validators; not yet unit-
  tested (Phase 10).
- AC3 (L6) — `Invoice.amount` is `BigDecimal` everywhere; the ArchUnit rule itself is Phase 10's
  job (test file).
- AC4 — `create(...)` persists `OPEN` with `expiresAt`/`createdAt`/`updatedAt` all derived from
  the single injected `Clock` instant.
- AC5 — `create(...)` is `@Transactional`.

No code deviates from the frozen brief. No LOCKED decision or Files-NOT-to-Modify constraint
was touched.
