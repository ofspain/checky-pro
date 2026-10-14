STATUS: FROZEN (approved 2026-10-14, Human Approval gate)

# payment · T05 · Frozen Task Brief

## Phase 3 disposition (6 findings ACCEPTED, 1 PARTIALLY ACCEPTED)

1. **`InvalidInvoiceRequestException` visibility.** ACCEPTED — made `public` (unlike crypto's own
   package-private `InvalidWatchRequestException`), since payment's future `InvoiceController`
   (task 6) will live in a different package and needs to catch it.
2. **JPA mapping details for `ddl-auto=validate`.** ACCEPTED — `state` is
   `@Enumerated(EnumType.STRING)`; `amount` is `@Column(precision = 78, scale = 0)`;
   `tokenDecimals` is `short` (maps to `SMALLINT`).
3. **No happy-path test named.** ACCEPTED — added
   `shouldCreateInvoiceWithOpenStateAndComputedExpiry` to Required Tests.
4. **`tokenDecimals`/`reference`/`amount`-precision not validated.** PARTIALLY ACCEPTED — an
   amount-precision check (reject if unscaled value exceeds 78 digits) is added to R4's own
   validation now, since it is directly tied to R4's wording ("not representable as an exact
   `NUMERIC`"). `tokenDecimals` range and `reference` max-length validation are explicitly
   **deferred to task 6**, where a real `CreateInvoiceRequest` DTO and bean validation exist —
   not this task's concern (no DTO exists yet).
5. **Transaction boundary not specified.** ACCEPTED — `create(...)` is `@Transactional`
   (default `REQUIRED`), forward-looking for task 6's outbox emit in the same transaction.
6. **ArchUnit L6 rule scope (parameters).** ACCEPTED — the rule also forbids
   `float`/`double`/`Float`/`Double` method **parameters**, not just fields/return types.
7. **`createdAt`/`updatedAt` lifecycle.** ACCEPTED — set via `@PrePersist`/`@PreUpdate` from the
   injected `Clock`, not relied on as DB defaults (agents.md's own injectable-`Clock`
   convention).

## Task

T05 — Invoice domain.

## Purpose

Give the service its first domain entity: `Invoice`, mapping the real, immutable `invoices`
table; `InvoiceState`, the 6-value enum matching the table's own CHECK constraint; and
`InvoiceService.create(...)`, which validates and persists a new invoice in `OPEN` state. This
is the foundation every later invoice-touching task (controller, expiry sweep, payment matching)
builds on.

## Scope

**In:**
- `Invoice` entity (all 13 real columns; `state` → `@Enumerated(STRING)`, `amount` →
  `@Column(precision=78, scale=0)`, `tokenDecimals` → `short`; `createdAt`/`updatedAt` set via
  `@PrePersist`/`@PreUpdate` from the injected `Clock`).
- `InvoiceState` enum (`OPEN, PAID, UNDERPAID, OVERPAID, EXPIRED, HELD`).
- `InvoiceRepository` (JPA, package-private, mirroring `OutboxEventRepository`'s own T04
  precedent).
- `InvoiceService.create(merchantAccountUuid, chain, tokenContractAddress, tokenDecimals,
  amount, payToAddress, reference)` — `@Transactional`; validates R3/R4 (including the amount-
  precision check, Phase 3 finding 4); computes `expiresAt` from
  `InvoiceProperties.defaultTtlMinutes()` via the injected `Clock`; persists in `OPEN` state.
- `InvalidInvoiceRequestException` (new, `invoice` package, **public**) — thrown on any R3/R4
  violation.
- A new `ArchitectureTest` (first in this service) — one rule, L6 only, covering fields, method
  return types, **and method parameters** (Phase 3 finding 6). Must use the plain-`ArchRule`-
  field + `@Test`-canary shape, never `@ArchTest`.

**Out:**
- `InvoiceController`, any DTO, watch registration, outbox emit (R1, R2 — task 6).
- `tokenDecimals` range validation, `reference` max-length validation (Phase 3 finding 4,
  deferred to task 6's DTO-level bean validation).
- `InvoiceExpirySweepJob`, `expire(...)` (task 8).
- `mark paid/under/over` methods and the R15 amount-comparison logic itself (a later consumer
  task).
- Any migration granting `payment_app` on `invoices` — not needed this task (no Testcontainers
  test is named for R3/R4/L6; task 6 owns the first real DB-backed invoice test).
- `shouldPreventCrossModuleEntityImports` (L1/L12) — out of scope (`L6` only, this task).

## Business Rules

- **R3** — reject `create(...)` if `chain` is not exactly `"ETHEREUM"` or `"TRON"`, or if
  `tokenContractAddress` is null/blank.
- **R4** — reject `create(...)` if `amount` is null, `<= 0`, has a non-zero scale, or has an
  unscaled value exceeding 78 digits (Phase 3 finding 4).

## Locked Decisions

- **L6** — `BigDecimal`/`NUMERIC` only; enforced by `Invoice.amount`'s type and the widened
  ArchUnit rule (fields, returns, and parameters).

## Dependencies

- `InvoiceProperties` (T03) — `defaultTtlMinutes()`.
- `Clock` (T04, `ClockConfig`) — `createdAt`/`updatedAt`/`expiresAt`, never `Instant.now()`
  directly.

## Inputs

- `V1__payments_baseline.sql` — the real `invoices` table, verbatim.
- `crypto-service`'s own `InvalidWatchRequestException`/`WatchExceptionHandler` — naming/shape
  template (visibility corrected per finding 1).
- `notification-service`'s own `ArchitectureTest.java` (T16) — the only confirmed-working
  ArchUnit-without-`@ArchTest` shape in this codebase.

## Outputs

- `services/payment/src/main/java/com/themistra/payment/invoice/{Invoice,InvoiceState,
  InvoiceRepository,InvoiceService,InvalidInvoiceRequestException}.java` (new).
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` (new).

## State Changes

None exercised yet — `create(...)` is the only method; it writes `OPEN` and nothing transitions
out of it within this task's own scope.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceState.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvalidInvoiceRequestException.java`
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceTest.java`

## Files NOT to Modify

- `V1__payments_baseline.sql`, `V2__payment_app_role_and_grants.sql` (immutable).
- Anything under `common`/`common.config`/`events` (prior tasks' own scope).
- Anything under `spec/`.

## Acceptance Criteria

- **AC1 (R3)** — `create(...)` throws `InvalidInvoiceRequestException` for `chain` ∉
  `{ETHEREUM, TRON}`, and for null/blank `tokenContractAddress`.
- **AC2 (R4)** — `create(...)` throws `InvalidInvoiceRequestException` for null/zero/negative
  `amount`, a non-zero-scale `amount`, and an amount exceeding 78 significant digits.
- **AC3 (L6)** — `Invoice.amount` is `BigDecimal`; the ArchUnit rule fails the build on any
  `float`/`double`/`Float`/`Double` field, method return type, **or method parameter** anywhere
  under `com.themistra.payment`.
- **AC4** — a valid `create(...)` call persists an `Invoice` in `OPEN` state with `expiresAt =
  now + InvoiceProperties.defaultTtlMinutes()`, a freshly generated `invoiceUuid`, and
  `createdAt`/`updatedAt` set from the injected `Clock`.
- **AC5 (Phase 3 finding 5)** — `InvoiceService.create` is `@Transactional`.

## Required Tests

- `shouldRejectInvoiceWithNonAllowlistedTokenReferenceShape` (R3).
- `shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat` (R4) — includes the scale and
  78-digit-precision cases.
- `shouldRejectFloatingPointMoneyTypesAtArchUnit` (L6) — ArchUnit, canary-`@Test` shape, fields +
  returns + parameters.
- `shouldCreateInvoiceWithOpenStateAndComputedExpiry` (AC4, Phase 3 finding 3).

## Constraints

- **Transaction:** `create(...)` is `@Transactional` (Phase 3 finding 5).
- **Thread-safety:** `InvoiceService` holds no mutable instance state.
- **Module boundaries:** `invoice` package owns `Invoice`; cross-module-import enforcement
  (L1/L12) is out of scope for this task.
- **Null handling:** `create(...)` throws `InvalidInvoiceRequestException` for the spec-named
  invalid inputs (chain, tokenContractAddress, amount); `merchantAccountUuid`/`payToAddress` are
  defensively null-guarded per this service's established `Objects.requireNonNull` convention.

## Open Questions

None. All resolved via the Phase 3 disposition above.
