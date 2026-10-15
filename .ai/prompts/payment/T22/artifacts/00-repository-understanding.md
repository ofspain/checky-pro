# payment · T22 · Phase 0 — Repository Understanding

Consumes: — (entry phase)

## 1. Architecture summary

`payment-service` (Spring Boot 3.5.4, Java 21) owns one Postgres schema, `payments`, migrated
via Flyway. Package-by-feature under `com.themistra.payment`; `ArchitectureTest` enforces (via
ArchUnit) that no feature module imports another feature module's `@Entity` class, and maintains
its own explicit `FEATURE_MODULES` allow-list (`invoice`, `payment`, `events`, `verification`,
`consumer`, `receipt` — `export` is not in this list yet; see §2). Security is OAuth2
resource-server, JWT `sub` claim = `merchantAccountUuid`, `@PreAuthorize("hasRole('MERCHANT')")`
on every merchant-facing endpoint. State changes other services care about are published through
a transactional outbox (`libs/java/outbox`), never a direct producer call. Money is always
`BigDecimal`/`NUMERIC`, never `float`/`double`.

## 2. Existing code this task touches

**Already exists (read-only for this task):**
- `invoice.Invoice` — `merchantAccountUuid`, `chain`, `tokenContractAddress`, `tokenDecimals`
  (`short`), `amount`, `payToAddress`, `reference`, `state`, `expiresAt`, `createdAt`,
  `updatedAt`. Table `payments.invoices`.
- `payment.Payment` — `invoiceId` (FK), `chain`, `txHash`, `fromAddress`, `toAddress`,
  `tokenContractAddress`, `amount`, `state` (`PaymentState`), `finalizedAt`, `attestedAt`,
  `createdAt`, `updatedAt`. Table `payments.payments`. `PaymentState.ATTESTED` is the terminal
  success state T19 added (transition happens inside `LedgerService.append`'s own transaction via
  `PaymentRepository.markAttestedIfFinalized`).
- `receipt.Receipt` — `paymentId` (FK, unique — one receipt per payment), `digestSha256`,
  `signature`, `kmsKeyId`, `s3Bucket`, `s3Key`, `signedAt`, `createdAt`. Table
  `payments.receipts`. `ReceiptService`'s own private `receiptUri(location)` helper builds
  `"s3://" + bucket + "/" + s3KeyPrefix + ".json"` — the same shape this task's own `receiptUri`
  export column needs, built from `s3Bucket`/`s3Key` directly off the row.
- Precedent for a **native, cross-module reporting query owned by a module that owns neither
  table**: `InvoiceRepository.findExpiredCandidatesWithoutInFlightPayment` (T08/T12) is a native
  query against `payments.payments` issued from the `invoice` module, which owns neither that
  table nor the `Payment` entity — it never imports the `Payment` entity class, just reads raw
  columns. This is the direct precedent for this task's own three-table join (see Phase 2).
- Precedent for a **cross-module read via a service-level projection, not an entity import**:
  `ReceiptService` consumes `InvoiceService.findReceiptPrimitives` →  `InvoiceReceiptView` and
  `PaymentService.findReceiptPrimitives` → `PaymentReceiptView` (both plain projections/records,
  never the `Invoice`/`Payment` entities themselves). This is the established alternative shape;
  Phase 2 will reason about which of these two precedents to extend for this task's own read, since
  it genuinely joins three tables, not one.
- `InvoiceController` — `merchantAccountUuidFrom(jwt)` helper (missing/blank/malformed `sub` →
  400, not a 500) is the exact precedent for this task's own merchant-scoping; this task's
  endpoint has no path-variable resource lookup at all (unlike `GET /invoices/{invoiceUuid}`), so
  there is no 404-vs-403 ambiguity to resolve — every row is scoped to the caller's own
  `merchantAccountUuid` by construction, R28's "extends here" framing (per `design.md` O5) is
  satisfied trivially, not by a lookup-and-compare.

**New for this task (per `design.md`'s own package map, line 421-423):**
- `export/TransactionExportService.java`
- `export/ExportController.java` — `GET /api/v1/exports/transactions`

Neither is a new `@Entity`, so `ArchitectureTest.FEATURE_MODULES` does **not** need `export`
added — that list only governs classes that own an `@Entity`; a plain DTO/record and a
service/controller pair trip no rule there. (Confirmed by reading the rule's own mechanics, not
assumed — mirrors this task's own Phase 0 prediction.)

## 3. Established patterns to follow

- **JPA vs. native query**: `agents.md` states directly — "JPA for simple find/save; a stored
  proc / native query only for complex or reporting reads." This task's own read (merchant-scoped,
  date-ranged, joining three tables, no entity mutation) is exactly a "reporting read." I looked
  for a `stored-proc-dao` Skill that `agents.md`'s own "Reusable procedures" section names
  (`spec/payment-service/agents.md` line 89, identically in `auth-service`/`crypto-service`'s own
  `agents.md`) — **no such Skill is registered in this environment** (`Skill` tool returns
  "Unknown skill: stored-proc-dao"; no file anywhere in the repo or in `~/.claude/skills/` or any
  installed plugin matches that name either). This reference appears stale or aspirational across
  all three services' specs, not something introduced by this task. Logged as a known gap (§5);
  proceeding by extending the two real, already-shipped precedents above directly rather than
  waiting on a Skill that does not exist.
- **Config**: flat `application.properties`, validated `@ConfigurationProperties`, fail-fast on
  missing values outside `local`.
- **Clock**: no `java.util.Date`; injectable `Clock` everywhere a "now" is needed (this task's
  date-range filter takes caller-supplied bounds, not "now", so this is lower-stakes here than in
  prior tasks, but any default/validation logic still must not call `Instant.now()` directly).
- **Error handling**: the shared `ApiExceptionHandler` template; a malformed/missing JWT `sub`
  maps to a typed 400 exception, mirrored from `InvoiceController`.
- **No cross-schema queries, ever** (`agents.md`) — not relevant to this task's own risk surface:
  `invoices`, `payments`, `receipts` are all three inside this same service's own single
  `payments` schema; the three-table join this task needs is cross-*module* at the Java-package
  level, never cross-*schema* at the Postgres level.

## 4. Testing conventions

- Unit tests for pure logic; Testcontainers (class-scoped, real Postgres via
  `services/auth/compose.local.yaml`'s own image) for anything touching the database — this
  task's own read almost certainly needs a real Testcontainers test given it is a multi-table SQL
  join, not something a mock could meaningfully verify.
- `ArchitectureTest` (ArchUnit) must stay green; §2 above confirms no change needed there for this
  task specifically.
- Fixed/injectable `Clock` in any test touching time.
- Named test from `package.md` §8: `shouldExportTaxReadyHistoryForMerchantAndDateRange` → R27.

## 5. Known gaps / unknowns

- **`stored-proc-dao` Skill does not exist** in this environment despite being named directly in
  `agents.md` across all three Themistra services reviewed so far. I do not know whether it was
  meant to be authored as a real Skill and never was, or whether the reference is simply stale.
  Not blocking — proceeding directly from the two real in-repo precedents identified in §2/§3.
- **O5's own "matching the rest of this platform's export endpoints" framing (`design.md` line
  91-92) appears to assume a platform export convention that does not actually exist yet** — I
  searched every service's controllers for an existing `/export` endpoint or any CSV/format-
  negotiation code and found none anywhere in this codebase. This task is, as far as I can tell,
  the **first** export endpoint in the platform, not one that is extending an existing one. I do
  not know whether this was already known at the time O5 was resolved (2026-10-11) or is a new
  finding. Logged for Phase 2; the content-negotiation shape will have to be invented here, not
  mirrored from a real sibling.
- I do not know the exact precision/format Postgres will serialize a joined `NUMERIC(78,0)`
  `amount` column as inside a native query's own result-row mapping (vs. the already-solved JPA
  `BigDecimal` mapping on the entities themselves) — to be confirmed empirically in Phase 6, not
  assumed in Phase 2's design.
