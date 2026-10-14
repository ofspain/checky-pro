# payment · T11 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T11 — Idempotent consumers |
| **Consumes** | — (entry phase) |

## 1. Architecture summary

`payment-service` now has the full `invoice` package (T05-T08); an outbound Kafka pipeline
(T04); a `payment` package with `PaymentState`/`PaymentStateMachine` (T09, pure logic, no
persistence, no caller yet); and a `verification` package with `VerificationRecord`/
`VerificationRecordRepository`/`VerificationRecordService` (T10, also no real caller yet). No
`Payment` entity, no inbound Kafka consumer, and no `processed_events` mapping exist anywhere in
this service. T11 is the first task to add any of them, and the first real caller of both T09's
and T10's own work.

## 2. Existing code this task touches

- **New**: `Payment` entity, `PaymentRepository`, `PaymentService` (`design.md` §6's own
  `payment/` package map — task 11's own text doesn't name these classes explicitly, but "Handle
  `chain.tx.seen` → `SEEN`..." is meaningless without somewhere to persist a `Payment` row, so
  these are read as in-scope, mirroring T10's own identical scope-reading precedent). A
  `ChainEventConsumer` (`consumer` package, new) with `@KafkaListener` methods for
  `chain.tx.seen`/`chain.tx.confirmed`/`chain.tx.finalized` (task 12's own scope: `chain.tx.reorged`).
  `ProcessedEvent`/`ProcessedEventRepository` (`consumer` package) mapping the real,
  already-migrated `processed_events` table.
- **The real `payments.payments` table already exists** (`V1__payments_baseline.sql`, T02,
  immutable): `payment_uuid UUID NOT NULL UNIQUE`, `invoice_id BIGINT NOT NULL REFERENCES
  invoices(id)`, `chain VARCHAR(32) NOT NULL`, `tx_hash VARCHAR(128) NOT NULL`, `from_address
  VARCHAR(128)` (nullable), `to_address VARCHAR(128) NOT NULL`, `token_contract_address
  VARCHAR(128) NOT NULL`, `amount NUMERIC(78,0) NOT NULL`, `confirmations INT NOT NULL DEFAULT
  0`, `state VARCHAR(16) NOT NULL DEFAULT 'CREATED'`, `amount_discrepancy VARCHAR(16)` (nullable),
  `address_poisoning_flag BOOLEAN NOT NULL DEFAULT FALSE`, `seen_at`/`confirmed_at`/
  `finalized_at`/`reorged_at`/`attested_at TIMESTAMPTZ` (all nullable), `created_at`/`updated_at`.
  `UNIQUE (chain, tx_hash)`.
- **The real `processed_events` table already exists** (same migration, immutable): exactly
  `event_key VARCHAR(200) PRIMARY KEY` (the comment says `chain:txhash:eventtype`) and
  `processed_at TIMESTAMPTZ NOT NULL DEFAULT now()` — **no `event_type` column**, unlike
  `notification-service`'s own `processed_events` table (confirmed by direct comparison — a real
  structural difference between the two services' tables of the same name, not an oversight to
  "fix").
- **`payment_app`'s current grants, confirmed by direct read**: `payments.payments` has
  `SELECT`-only (`V5__payment_app_payments_table_read_grant.sql`, T08, for the expiry sweep's own
  in-flight-payment check) — **no `INSERT`/`UPDATE` yet**, and this task needs both (creating and
  then repeatedly updating a `Payment` row). `processed_events` has **no grant at all**
  (`SchemaBaselineMigrationIntegrationTest`'s own `UNGRANTED_TABLES` confirms it). **Two new
  migrations are needed this task**, not zero — flagged now so Phase 2/5 don't under-plan this
  the way T09's own Phase 2 under-planned the `ArchitectureTest` rule.
- **Contracts already exist and are authoritative** (not to be invented): `contracts/events/chain/
  tx-seen.v1.schema.json`, `tx-confirmed.v1.schema.json`, `tx-finalized.v1.schema.json` (this
  task's own 3 consumed events; `tx-reorged.v1.schema.json` is task 12's). Read directly, not
  assumed — **a genuinely significant finding, detailed in §5 below**: `tx-seen`/`tx-confirmed`
  carry no `amount`, no `fromAddress`/`toAddress`, no `addressPoisoningFlag` — only `tx-finalized`
  does. All four schemas require `watchId` (not `invoiceUuid`, which is present but *not* in any
  schema's own `required` list).

## 3. Established patterns to follow

- **The first real `@KafkaListener` in this codebase is `notification-service`'s own
  `AuthEventConsumer`** (consumes raw `String`, deserializes via Spring Boot's autoconfigured
  `ObjectMapper`, `@Transactional` per listener method, dedupes before doing anything else,
  returns early — not an exception — for an event it recognizes but doesn't act on). The exact
  shape to mirror for `ChainEventConsumer`.
- **Idempotent dedupe**: `notification-service`'s own `IdempotencyGuard`/`ProcessedEventRepository`
  is a precise, battle-tested precedent with a documented failure history worth re-reading in
  full before Phase 2 designs anything: a JPA `existsById`-then-`save` has a real TOCTOU race
  under concurrent duplicate delivery; catching a constraint-violation around a plain `save`
  still throws `UnexpectedRollbackException` (Hibernate marks the transaction rollback-only the
  instant the flush fails, regardless of whether the exception is caught);
  `Propagation.NESTED`/savepoints are not supported by `JpaTransactionManager`/
  `HibernateJpaDialect`. The actual working fix: a **native** `INSERT ... ON CONFLICT (event_key)
  DO NOTHING` query (never constructs/saves a JPA entity for the write path),
  `@Modifying(clearAutomatically = true, flushAutomatically = true)`, returning the row count (1
  = genuinely new, 0 = duplicate) as the only signal. **payment-service's own `ProcessedEvent`
  has no `event_type` column** (per §2 above), so the mirrored query takes one fewer bind
  parameter than notification's own version — not a literal copy-paste.
- **Entities**: `Invoice`/`OutboxEvent`/`VerificationRecord` are this service's own established
  shape — package-private repository, protected no-arg constructor, static factory method,
  getters only, `@PrePersist` `Instant.now()` fallback for a timestamp the real caller is expected
  to set explicitly via an injected `Clock`.
- **`CryptoClient.unregisterWatch(UUID watchId)`** (T08) already exists and is exactly what R7a
  needs to call on first `SEEN`.
- **`EventTopics`** (T04) already maps `"payment-seen" → "payments.payment.seen"` and
  `"payment-finalized" → "payments.payment.finalized"` — both topics this task needs already
  exist; no `EventTopics` change expected.
- **`InvoiceRepository`** has no `findByWatchId` method yet — needed this task to resolve an
  incoming event's `watchId` back to the owning `Invoice` (see §5 — `watchId`, not `invoiceUuid`,
  is the one field every consumed event schema actually guarantees).

## 4. Testing conventions

- Plain JUnit + Mockito for `ChainEventConsumer`/`PaymentService` unit tests (mirrors
  `InvoiceServiceTest`'s own style). `AuthEventConsumerIntegrationTest`/
  `AuthEventConsumerTransactionRollbackIntegrationTest`/`IdempotencyGuardIntegrationTest`
  (`notification-service`) are the nearest Testcontainers+Kafka precedent for a genuine
  end-to-end consumer proof, though this task's own named tests (§8) read as unit-level, not
  necessarily requiring a live Kafka broker — to confirm in Phase 1/2.
- Fixed `Clock` throughout, mirroring every other service class in this codebase.
- `ArchitectureTest.FEATURE_MODULES` will need a `consumer` entry (the same category of gap T09
  found for `events` and T10 proactively flagged for itself) — called out here so Phase 2/5
  don't have to discover it empirically a third time.

## 5. Known gaps / unknowns — three genuinely significant findings, not assumed away

- **Finding A (significant): `R6` (`shouldTransitionCreatedToWatchingOnWatchAck`) appears to be
  an orphaned requirement no task in this 29-task breakdown actually owns.** Task 11's own header
  scopes `R7, R8, R9, R10, R13` — **`R6` is conspicuously absent**, even though it's a real,
  separately-named test in `package.md` §8. Watch-registration acknowledgment (T06) is a
  *synchronous* REST response (`CryptoClient.registerWatch` returns `{watchId, status:
  REGISTERED}` in the same HTTP call `InvoiceController` makes) — there is no async
  "watch-acknowledged" Kafka event for any consumer to ever receive, and T06's own frozen brief
  explicitly built no `Payment` row at invoice-creation time (confirmed: `Payment` didn't exist
  until T10's own package map first listed it, and even then task 11 is the first to build it).
  **This means `R6` cannot be fulfilled by any consumer this task builds, and was never fulfilled
  by T06 either.** Tentative reading, to confirm explicitly in Phase 1/3: `R6` is a real,
  pre-existing specification gap (no task number currently implements it), not something task 11
  should silently absorb into its own scope just because it's thematically adjacent. **Flag for
  the user, do not silently fix by retroactively modifying T06's own closed `InvoiceController`.**
- **Finding B (significant): a `Payment` row must apparently be born already in `SEEN` state,
  never actually persisted in `CREATED`/`WATCHING`.** Following from Finding A: since no earlier
  task ever creates a `Payment` row, `ChainEventConsumer`'s own handling of the *first*
  `chain.tx.seen` for a given invoice must be what creates the row at all (R7: "record a payment
  in `SEEN`"). This is consistent with T09's own established "an edge can exist in the fixed
  graph with no real caller yet" pattern (e.g. `FINALIZED → ATTESTED`, task 17/19's own scope) —
  `CREATED → WATCHING` and `WATCHING → SEEN` would both be edges `PaymentStateMachine` defines
  but no running code ever actually exercises as a real stored-row transition; the row is simply
  constructed with its initial field value already `SEEN`, mirroring `Invoice.create`'s own
  convention of setting an initial state directly rather than calling a transition method for it.
  **A real design call for Phase 2, to invite genuine Phase 3 Kimi challenge and very likely a
  real Phase 4 `AskUserQuestion`** (comparable in weight to T09's own `HELD`-edge decision) —
  not assumed silently.
- **Finding C (significant): `payments.payments.amount`/`to_address` are `NOT NULL`, but neither
  `chain.tx.seen` nor `chain.tx.confirmed` carries an amount or address at all** (confirmed by
  direct schema comparison, §2 above) — only `chain.tx.finalized` does. `to_address` is resolvable
  without the event at all (`Invoice.payToAddress`, already known from invoice creation). `amount`
  has no real value available until finalization — the row must be created with *some* `NOT NULL`
  placeholder (`0` is the natural candidate, later overwritten once `chain.tx.finalized` reports
  the real observed amount) or the schema's own `NOT NULL` constraint cannot be satisfied at
  `chain.tx.seen` time at all. **A real design call for Phase 2/3/4**, not assumed — needs
  explicit reasoning about whether `0` could ever collide with a legitimate real value in a way
  that matters (tentatively: no, since `amount_discrepancy`/the real comparison logic is task 13's
  own scope and reads the *post-finalization* amount, never the placeholder).
- **I do not know** whether event correlation should key off `watchId` (guaranteed present in
  every schema's own `required` list, and already stored on `Invoice.watchId` since T06) or
  `invoiceUuid` (present in every schema's own `properties` but absent from every `required`
  list). Tentative, strong reading: `watchId` — the only field every event schema actually
  guarantees, and the one this service already has a stable FK-equivalent column for. To confirm
  explicitly in Phase 1/2, not assumed by habit.
- **I do not know** the exact `@KafkaListener` topology task 11's own 3 event types should use —
  `design.md`'s own O6 leaves this to the implementer ("Recommend one; proceed if low-risk").
  Given `ChainEventConsumer` is named singular in both the task text and the package map, one
  class with 3 (not 4 — `chain.tx.reorged` is task 12's) listener methods is the strong default
  reading.
