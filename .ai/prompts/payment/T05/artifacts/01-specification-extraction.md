# payment · T05 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T05 — Invoice domain |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

- **R3** — malformed or unrecognized `<chain, contractAddress>` shape → reject with `400` RFC
  9457, no watch registered.
- **R4** — amount zero, negative, or not an exact `NUMERIC` → reject with `400`, no invoice
  created.

**Explicitly out of scope for T05** (not in the header's "Scoped requirement IDs," confirmed by
`tasks.md`'s own task 5/6/9/10 split): R1, R2 (controller + outbox emit — task 6), R15 (payment-
vs-invoice amount comparison — a later consumer task, not `InvoiceService.create`). Task 5's own
trailing sentence ("Comparison logic is exact-match only... `amount == invoice.amount` →
`PAID`...") is read here as **context for why `InvoiceState` has `PAID`/`UNDERPAID`/`OVERPAID`
values**, not as a method this task must implement — no caller of that comparison exists until
whichever later task applies a payment to an invoice. Flagged for Phase 3 challenge if wrong.

## Locked Decisions

- **L6** — money is `NUMERIC`/`BigDecimal` only; no `float`/`double`/`Double`/`Float` on any
  money-carrying field or DTO; token identified by `<chain, contractAddress>`, never symbol;
  ArchUnit forbids floating-point money types.

## Files involved

**New (this task's own scope, per `design.md` §6's package map and `tasks.md` task 5's text):**
- `invoice/Invoice.java` — entity.
- `invoice/InvoiceState.java` — enum (`OPEN, PAID, UNDERPAID, OVERPAID, EXPIRED, HELD`, matching
  the real `chk_invoice_state` CHECK constraint verbatim).
- `invoice/InvoiceRepository.java`.
- `invoice/InvoiceService.java` — `create(...)` only this task; `expire`/`mark paid/under/over`
  are `design.md`'s own full eventual scope for this class but belong to later tasks (8, and
  whichever task applies R15).
- A new ArchUnit test (money-type enforcement, L6) — no existing `ArchitectureTest` class exists
  in `payment-service` to extend; this task creates the first one.

**Existing, read-only / reused as-is:**
- `V1__payments_baseline.sql` — the real `invoices` table shape (immutable, T02).
- `ClockConfig` (T04) — if `InvoiceService.create` computes `expiresAt` itself.
- `InvoiceProperties` (T03, `common.config`) — already has
  `default-ttl-minutes`/`amount-tolerance-base-units`.

**Not to modify:** `V1__payments_baseline.sql`, `V2__payment_app_role_and_grants.sql`, anything
under `spec/`, any other `common`/`events` file.

## Dependencies

- `InvoiceProperties` (T03) — `default-ttl-minutes` for `expiresAt` computation.
- `Clock` (T04, `ClockConfig`) — for `createdAt`/`expiresAt`, per `agents.md`'s own "injectable
  Clock" convention.
- **New migration likely required**: `payment_app` has no grant on `invoices` yet (T02 scoped
  grants to `ledger_entries`/`verification_records` only). `InvoiceRepository` cannot function
  against the real role without one. Carried to Open Questions below — not assumed.
- No dependency on `OutboxPublisher` (T04) — R2's outbox emit is task 6's scope, not this one's.
- No dependency on `CryptoClient` — watch registration is task 6's scope.

## Acceptance Criteria

- **AC1 (R4)** — `InvoiceService.create` rejects amount `<= 0`; the repository/entity layer only
  ever handles `BigDecimal`, never `float`/`double`.
- **AC2 (R3)** — `InvoiceService.create` rejects an unrecognized `chain` value and a malformed/
  blank `tokenContractAddress`.
- **AC3 (L6)** — a new ArchUnit rule fails the build if any field/method return type on a money-
  carrying class is `float`/`double`/`Float`/`Double`.
- **AC4** — `Invoice`/`InvoiceState`/`InvoiceRepository` map/enumerate exactly the real schema
  (6-state enum matching `chk_invoice_state`, `NUMERIC(78,0)` ↔ `BigDecimal`).

## Tests required

- `shouldRejectInvoiceWithNonAllowlistedTokenReferenceShape` (R3) — named, `package.md` §8.
- `shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat` (R4) — named, `package.md` §8.
- `shouldRejectFloatingPointMoneyTypesAtArchUnit` (L6) — named, `package.md` §8. **Must use the
  plain-`ArchRule`-field-plus-`@Test`-canary shape** ([[surefire-archtest-field-bug]]) — the
  `@ArchTest`-field shape silently reports 0 tests under this repo's Surefire.
- Implied boundary tests (not named, but required by R4's own text): zero amount, negative
  amount; implied by R3: blank `chain`, blank `tokenContractAddress`, an unrecognized chain value.
- Implied by AC4: `InvoiceState.valueOf` round-trips all 6 values; entity field types match the
  schema exactly (same discipline as `OutboxEvent`'s own T04 verification).

## Open Questions

1. **Does `InvoiceRepository` need a new migration (`V3__...sql`) granting `payment_app` on
   `invoices` before this task's own tests can run against a real database?** Not stated in
   task 5's text; T02's own grant scope doesn't cover it. Genuine blocker if a Testcontainers-
   backed repository test is in scope — not a blocker if this task's tests are unit-only
   (plain JUnit, `InvoiceService` tested with a mocked `InvoiceRepository`). Flagged for Phase 3.
2. **What exception type does `InvoiceService.create` throw on an R3/R4 violation, and is its
   `@RestControllerAdvice` mapping to `400` built in this task or deferred to task 6 (the
   controller)?** Reading here: **T05 defines and throws a typed exception
   (e.g. `InvalidInvoiceRequestException`); the HTTP-level `400` RFC 9457 mapping is task 6's
   scope**, since no controller exists yet to attach a `@RestControllerAdvice` to, mirroring how
   `crypto-service`'s own `WatchExceptionHandler` was built alongside its controller, not its
   service. Flagged for Phase 3 challenge if wrong.
3. **Is R3's "not a recognised `<chain, contractAddress>` shape" local format validation only
   (chain ∈ `{ETHEREUM, TRON}` + a non-blank/shape check on the address), or a real cross-service
   allowlist lookup?** No contract endpoint exists for the latter (confirmed in Phase 0). Reading
   here: **local format validation only**, mirroring `crypto-service`'s own `@Pattern(regexp =
   "ETHEREUM|TRON")` convention for `chain`, and a `@NotBlank` (no address-format regex — no
   precedent for one exists anywhere yet; `crypto-service`'s own `AddressValidator` is explicitly
   future-scoped, not built). Flagged for Phase 3 challenge if wrong.
