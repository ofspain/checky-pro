# payment · T10 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T10 — Verification record |
| **Consumes** | — (entry phase) |

## 1. Architecture summary

`payment-service` (Java 21, Spring Boot) now has: the full `invoice` package (T05-T08); an
outbound Kafka pipeline (T04); and, as of T09, a `payment` package containing only `PaymentState`
(enum) and `PaymentStateMachine` (pure logic, no persistence, no caller yet) plus a newly-authored
cross-module-entity-import ArchUnit rule. No `verification` package, no `Payment` entity, no
`ChainEventConsumer`, and no `VerificationRecord` exist yet. T10 is the first task to add any of
them — specifically `VerificationRecord` only, per its own narrow task text.

## 2. Existing code this task touches

- **New**: a `verification` package — does not exist. `design.md` §6's own package map lists
  `VerificationRecord.java` (entity), `VerificationRecordRepository.java`, and
  `VerificationRecordService.java` together; task 10's own text ("Add `VerificationRecord`
  (append-only) and record every transition, reversal, and ignored stale event with the trigger
  event key") names only the entity by class name but describes behavior ("record...") that
  implies a service-layer method too — read as in-scope for all three (unlike T09, where the
  task text named only one class and the package map's other two files were read as a *later*
  task's scope, here the task's own behavioral description — "record every X" — requires
  something to actually perform the recording, so `VerificationRecordRepository`/
  `VerificationRecordService` are read as in-scope). **To confirm explicitly in Phase 1/2, not
  silently assumed either way**, mirroring T09's own precedent of stating a scope reading rather
  than leaving it implicit.
- **The real `verification_records` table already exists** (`V1__payments_baseline.sql`, T02,
  immutable): `id BIGINT IDENTITY`, `payment_id BIGINT NOT NULL REFERENCES payments(id) ON DELETE
  CASCADE`, `from_state VARCHAR(16)` (**nullable** — unlike `to_state`), `to_state VARCHAR(16) NOT
  NULL`, `trigger_event_key VARCHAR(200)` (nullable, "NULL for internal"), `confirmations INT`
  (nullable), `detail JSONB` (nullable), `occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()`. `payment_app`
  already has `INSERT+SELECT` on this table (T02's own original grant, confirmed by direct read of
  `V2__payment_app_role_and_grants.sql`/this session's own prior memory) — **no new migration
  needed this task**, unlike T06/T08's own grant-gap pattern.
- **`payment_id` has no JPA entity to reference** — no `Payment` class exists yet (confirmed T09's
  own explicit scope boundary: deferred to task 11). `VerificationRecord.payment_id` will almost
  certainly be a plain `Long` field (mirrors how `Invoice`/`OutboxEvent` already use plain
  `Long`/`UUID` fields rather than JPA relationship mappings for not-yet-modeled associations),
  not a `@ManyToOne` to a `Payment` entity that doesn't exist.
- **`PaymentState` (T09, `payment` package)** is the real type `from_state`/`to_state` should map
  to (`@Enumerated(STRING)`, mirroring `Invoice.state`'s own established convention) — this task
  will import `PaymentState` from the `payment` package, a cross-feature-package import of an
  *enum*, not an entity, so L12's own cross-module-entity-import rule (T09) does not forbid it
  (that rule is scoped to `@Entity`-annotated classes only, confirmed by direct read of
  `ArchitectureTest`'s own new rule).

## 3. Established patterns to follow

- **Append-only entities**: `OutboxEvent` (T04) is the nearest structural precedent — though
  `OutboxEvent` is mutated once (`published_at` set on relay), `verification_records`'s own schema
  has no analogous "update later" column, and `payment_app`'s own grant is `INSERT+SELECT` only
  (no `UPDATE`), confirming true append-only, closer in spirit to `ledger_entries` (L7) than to
  `OutboxEvent`. No existing JPA entity in this codebase is purely insert-only with literally no
  update path — this task is likely the first.
- **Enums on entities**: `Invoice.state` (`@Enumerated(EnumType.STRING)`) is the direct precedent
  for `from_state`/`to_state`.
- **Nullable-field entities**: `Invoice.reference` is the nearest precedent for a nullable
  `String`/value field with no `@Column(nullable = false)`.
- **JSONB columns**: **no existing precedent anywhere in this codebase** for a JPA-mapped `JSONB`
  column (`detail`). `receipts`/`ledger_entries` (both unbuilt) are the only other tables with a
  `JSONB` column (`ledger_entries.payload`) — neither has a built entity yet to check against.
  Phase 1/2 will need to research the correct Hibernate mapping approach (e.g.
  `@JdbcTypeCode(SqlTypes.JSON)` for Hibernate 6, which this codebase's Spring Boot 3.5.4/
  Hibernate version supports) — **flagged as a real unknown, not assumed**.
- **Services with a repository + no controller**: `InvoiceExpirySweepJob`'s own `InvoiceService`
  delegate methods are the nearest shape for a service class with no HTTP-facing caller yet.

## 4. Testing conventions

- Plain JUnit 5 with Mockito for a service class that has a repository dependency (mirrors
  `InvoiceServiceTest`'s own `@ExtendWith(MockitoExtension.class)` style) is the likely shape for
  `VerificationRecordServiceTest`, if a service class is in scope.
- Fixed `Clock`/`Instant` convention (`InvoiceServiceTest`'s own `NOW`/`Clock.fixed`) — likely
  relevant here too, since `occurred_at` needs a deterministic value for assertions, mirroring
  `Invoice.createdAt`'s own `Clock`-derived-`now` pattern rather than relying on a DB-side
  `DEFAULT now()` the application code should still set explicitly (same reasoning `Invoice`'s own
  Javadoc gives for its `@PrePersist` fallback being a last resort, not the primary path).
- No Testcontainers-backed repository test exists yet for any entity in this service other than
  the raw-JDBC `SchemaBaselineMigrationIntegrationTest` (T02) and T08's own
  `InvoiceRepositoryIntegrationTest` (a genuine Spring+JPA+Testcontainers test, the only one of
  its kind in this service so far, and **currently confirmed broken** — see
  [[payment-service-spec-progress]] memory, T08's own open follow-up, unrelated to this task).

## 5. Known gaps / unknowns

- **I do not know** the exact `VerificationRecordService` method signature(s) task 10 expects —
  one general `record(paymentId, fromState, toState, triggerEventKey, confirmations, detail)`
  method covering every case, or three distinct named methods (`recordTransition`,
  `recordReversal`, `recordIgnored`) mirroring the task's own three-part phrasing ("every
  transition, reversal, and ignored stale event"). A real design decision for Phase 2.
- **I do not know** how an "ignored" row should be shaped given `to_state` is `NOT NULL` — a
  stale/out-of-order event by definition causes no state change, so there is no new target state
  to record. Tentative reading (to confirm in Phase 1/2, not assumed): `to_state` is set to the
  payment's own *current, unchanged* state (`from_state == to_state`), with `detail` (JSONB)
  carrying the reason/context (e.g. the stale event's own reported state or confirmation count).
- **I do not know** the correct Hibernate JPA mapping for the `detail JSONB` column — no
  precedent exists in this codebase yet. Needs direct research in Phase 2 (e.g. confirm the
  `hibernate-core` version via the root POM and whether `@JdbcTypeCode(SqlTypes.JSON)` or a
  different Hibernate 6 mechanism is the right fit), not assumed.
- **I do not know** whether the named tests (`shouldWalkBackwardConfirmingToSeenOnReorg`,
  `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm`, `shouldIgnoreOutOfOrderOrStaleChainEvents`)
  test `VerificationRecordService`'s own recording methods directly (passing in the already-known
  `from`/`to` state pair a future caller would supply), or imply this task must also build some
  orchestration that actually calls `PaymentStateMachine`. **Tentative reading**: task 10's own
  text is scoped to recording only ("Add `VerificationRecord`... and record..."); task 12's own
  text ("Reorg handling. Wire `chain.tx.reorged` to the backward transitions... and record the
  reversal") is what actually wires a live event to a `PaymentStateMachine.transition(...)` call
  *and* a recording call together. R11/R12's own requirement text conflates "walk backward" with
  "record the reversal" in one sentence, which is why the named tests read behaviorally even
  though this task's own scope is the recording half only — the named tests exercise the
  recording method with the same state pairs the real reversal scenarios will eventually supply,
  without a live consumer or state machine call in the loop. **A real scope reading to confirm
  explicitly in Phase 1/3, not assumed.**
- `Payment`/`PaymentRepository`/`PaymentService` still do not exist (confirmed, carried forward
  from T09) — `VerificationRecord.paymentId` is a plain `Long`, not a JPA relationship.
