# payment · T05 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T05 — Invoice domain |
| **Consumes** | `artifacts/11-test-review-resolution.md` |

## Verdict: **PASS**

## Traceability matrix

| AC | Requirement | Implementation | Test coverage |
|---|---|---|---|
| AC1 | `create(...)` rejects unrecognized `chain`, null/blank `tokenContractAddress` | `InvoiceService.validateChainAndTokenAddress` | `shouldRejectInvoiceWithNonAllowlistedTokenReferenceShape` (named, R3) — unrecognized chain, null chain, null/blank address |
| AC2 | `create(...)` rejects null/`<=0`/non-integral/>78-digit `amount` | `InvoiceService.validateAmount` | `shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat` (named, R4) + `shouldAcceptWholeNumberAmountWithTrailingZerosAndRejectByTrueDigitCountNotReducedPrecision` + `shouldAcceptAmountAtExactly78DigitsPrecisionBoundary` (Phase 11 Gap 2) |
| AC3 | ArchUnit forbids `float`/`double`/`Float`/`Double` on any field, method return, method parameter, or constructor parameter, including array forms | `ArchitectureTest.shouldRejectFloatingPointMoneyTypes` custom condition | `shouldRejectFloatingPointMoneyTypesAtArchUnit` (named, L6) + `...ActuallyFailsAgainstAGenuineViolation` (5-way fixture, Phase 11 Gaps 4/5) |
| AC4 | Valid `create(...)` persists `OPEN` with computed `expiresAt`, generated `invoiceUuid`, `Clock`-derived `createdAt`/`updatedAt` | `Invoice.create` + `InvoiceService.create` | `shouldCreateInvoiceWithOpenStateAndComputedExpiry` (Phase 3 finding 3), strengthened with `verify(repository).save(...)` (Phase 11 Gap 1) |
| AC5 | `create(...)` is `@Transactional` | `InvoiceService.create` | `createMethodIsAnnotatedTransactional` (Phase 11 Gap 3, source-text guard) |

## Required Tests (frozen brief)

- `shouldRejectInvoiceWithNonAllowlistedTokenReferenceShape` — present, passing.
- `shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat` — present, passing.
- `shouldRejectFloatingPointMoneyTypesAtArchUnit` — present, passing.
- `shouldCreateInvoiceWithOpenStateAndComputedExpiry` (Phase 3 finding 3) — present, passing.
- **All satisfied, and exceeded** — 4 additional tests beyond the frozen brief's own minimum
  (boundary, transaction, and ArchUnit-genuine-failure proofs) landed via the genuine Phase 11
  Kimi pass.

## Scope discipline

`git diff --stat e2ba7c46..HEAD` (T05's own full range) touches exactly the 5 production files
the frozen brief's own Files to Create list names (`Invoice`, `InvoiceState`,
`InvoiceRepository`, `InvoiceService`, `InvalidInvoiceRequestException`), nothing under
`db/migration/`, no other `common`/`events` file, nothing under `spec/`. Files NOT to Modify
constraint held.

## Locked decisions

- **L6** (money is `NUMERIC`/`BigDecimal` only) — held: `Invoice.amount` is `BigDecimal`
  throughout; the ArchUnit rule now enforces this build-time across fields, method returns,
  method parameters, constructor parameters, and array forms of all four.

## Open items carried forward (not blockers for this task)

- `Invoice`'s JPA mapping has not been empirically verified against a real Postgres (Docker
  unavailable this session) — carried from Phase 7 through Phase 11, same disposition each
  time. Task 6's own first DB-backed invoice test is the natural place this closes.
- `Invoice`'s `@PrePersist`/`@PreUpdate` `Instant.now()` fallback remains untested — disclosed,
  accepted, matches `OutboxEvent`'s own identical T04 precedent.
- `merchantAccountUuid`/`payToAddress` throw `NullPointerException`, not
  `InvalidInvoiceRequestException` — the frozen brief's own explicit, intentional distinction;
  noted for task 6 to reconsider once a DTO/bean-validation layer exists.

## Test suite status

95/95 non-Docker-dependent tests passing (`mvn -pl services/payment -am test
-Dtest='!SchemaBaselineMigrationIntegrationTest'`). `SchemaBaselineMigrationIntegrationTest`
(17 tests, T02, Testcontainers) not run this session — Docker unavailable — but unaffected by
any T05 change (no migration touched).

## Verdict rationale

All 5 acceptance criteria are implemented and test-covered, several beyond the frozen brief's
own minimum per the genuine Phase 11 Kimi review (constructor/array ArchUnit coverage, an exact-
boundary test, a `save`-invocation verification, and a `@Transactional` source-text guard). No
LOCKED decision violated. No file outside the frozen brief's own scope touched. The disclosed
carried-forward items above are accepted, named limitations of this task's own scope, not
defects. **PASS.** Proceeding to Phase 13.
