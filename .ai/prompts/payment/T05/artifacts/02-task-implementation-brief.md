# payment · T05 · Phase 2 — Task Implementation Brief

## Task

T05 — Invoice domain.

## Purpose

Give the service its first domain entity: `Invoice`, mapping the real, immutable `invoices`
table; `InvoiceState`, the 6-value enum matching the table's own CHECK constraint; and
`InvoiceService.create(...)`, which validates and persists a new invoice in `OPEN` state. This
is the foundation every later invoice-touching task (controller, expiry sweep, payment
matching) builds on.

## Scope

**In:**
- `Invoice` entity (all 13 real columns).
- `InvoiceState` enum (`OPEN, PAID, UNDERPAID, OVERPAID, EXPIRED, HELD`).
- `InvoiceRepository` (JPA, package-private, mirroring `OutboxEventRepository`'s own T04
  precedent).
- `InvoiceService.create(merchantAccountUuid, chain, tokenContractAddress, tokenDecimals,
  amount, payToAddress, reference)` — validates R3/R4, computes `expiresAt` from
  `InvoiceProperties.defaultTtlMinutes()` via the injected `Clock`, persists in `OPEN` state.
- `InvalidInvoiceRequestException` (new, `invoice` package) — thrown on any R3/R4 violation.
  Mirrors `crypto-service`'s own `InvalidWatchRequestException` naming/shape exactly.
- A new `ArchitectureTest` (first in this service) — one rule, L6 only (no `float`/`double`/
  `Float`/`Double` on any field/return type under `com.themistra.payment`). **Must use the
  plain-`ArchRule`-field + `@Test`-canary shape**, never `@ArchTest` (silently reports 0 tests
  under this repo's Surefire — confirmed, `notification-service`'s own T16 precedent).

**Out:**
- `InvoiceController`, any DTO, watch registration, outbox emit (R1, R2 — task 6).
- `InvoiceExpirySweepJob`, `expire(...)` (task 8).
- `mark paid/under/over` methods and the R15 amount-comparison logic itself (a later consumer
  task — no caller of this logic exists until a payment is matched against an invoice).
- Any migration granting `payment_app` on `invoices` — **not needed this task.** `R3`/`R4`/`L6`'s
  own named tests (`package.md` §8) are unit-level (no Testcontainers test is named for this
  task's own requirement IDs); the first real DB-backed invoice test is R1's own
  `shouldCreateInvoiceAndRegisterWatch`, task 6's scope. Deferred there, not assumed needed now.
- `shouldPreventCrossModuleEntityImports` (L1/L12) — a different named test, not in this task's
  scoped LOCKED decisions (`L6` only). Out of scope.

## Business Rules

- **R3** — reject `create(...)` if `chain` is not exactly `"ETHEREUM"` or `"TRON"`, or if
  `tokenContractAddress` is null/blank. (No address-format/checksum validation — no precedent
  exists anywhere in the codebase; `crypto-service`'s own `AddressValidator` is explicitly
  future-scoped, unbuilt.)
- **R4** — reject `create(...)` if `amount` is null, `<= 0`, or has a non-zero scale (i.e.
  `amount.stripTrailingZeros().scale() > 0`). The scale check is this task's own real
  consequence of combining R4's text ("not representable as an exact `NUMERIC`") with the real
  schema's `amount NUMERIC(78,0)` column — base units are always whole numbers; a fractional
  `BigDecimal` is not representable in that column without silent truncation.

## Locked Decisions

- **L6** — `BigDecimal`/`NUMERIC` only; enforced both by `Invoice.amount`'s type (compile-time)
  and the new ArchUnit rule (build-time, catches any future field anywhere in the service).

## Dependencies

- `InvoiceProperties` (T03, `common.config`) — `defaultTtlMinutes()`.
- `Clock` (T04, `ClockConfig`) — `createdAt`/`expiresAt` computation, never `Instant.now()`
  directly.

## Inputs

- `V1__payments_baseline.sql` — the real `invoices` table (field names/types/constraints,
  verbatim).
- `crypto-service`'s own `WatchExceptionHandler`/`InvalidWatchRequestException` — naming/shape
  template for `InvalidInvoiceRequestException` (not its HTTP mapping — no controller exists).
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
  `amount`, and for a non-zero-scale `amount`.
- **AC3 (L6)** — `Invoice.amount` is `BigDecimal`; the new ArchUnit rule fails the build on any
  `float`/`double`/`Float`/`Double` field or method return type anywhere under
  `com.themistra.payment`.
- **AC4** — a valid `create(...)` call persists an `Invoice` in `OPEN` state with `expiresAt =
  now + InvoiceProperties.defaultTtlMinutes()` and a freshly generated `invoiceUuid`.

## Required Tests

- `shouldRejectInvoiceWithNonAllowlistedTokenReferenceShape` (R3).
- `shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat` (R4) — "Float" here reads as "non-
  integral `BigDecimal` scale," the only way a floating-point-shaped value could reach this
  method given `amount`'s declared type is already `BigDecimal` (no primitive `float`/`double`
  overload exists to reject).
- `shouldRejectFloatingPointMoneyTypesAtArchUnit` (L6) — ArchUnit, canary-`@Test` shape.

## Constraints

- **Transaction:** `create(...)` has no cross-service call in this task's scope (watch
  registration is task 6) — no transactional boundary concern beyond the single `save`.
- **Thread-safety:** `InvoiceService` holds no mutable instance state.
- **Module boundaries:** `invoice` package owns `Invoice`; no other package imports it (not yet
  enforced by ArchUnit this task — that's `shouldPreventCrossModuleEntityImports`, L1/L12, out
  of scope).
- **Null handling:** `create(...)` throws `InvalidInvoiceRequestException` (not
  `NullPointerException`) for the two spec-named invalid inputs (chain, tokenContractAddress,
  amount); other parameters (`merchantAccountUuid`, `payToAddress`) are defensively null-guarded
  per this service's own established `Objects.requireNonNull` convention but are not named by
  any test.

## Open Questions

No blockers. All three questions carried from Phase 1 are resolved above by direct evidence
(package.md §8's own test list, the real schema, and existing cross-service precedent) — flagged
for Phase 3 challenge if any resolution is wrong, not treated as settled beyond challenge.
