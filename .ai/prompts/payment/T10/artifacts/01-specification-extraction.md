# payment · T10 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T10 — Verification record |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

- **R11** — a `chain.tx.reorged` event for a `CONFIRMING` payment walks it back to `SEEN` **and**
  records the reversal. This task's own scope is the recording half (the "walk back" half is
  task 12's — see Phase 0's own scope reading, carried forward).
- **R12** — a `chain.tx.reorged` event for a `SEEN` (not yet confirmed) payment walks it back to
  `WATCHING` **and** records the reversal. Same scope split as R11.
- **R14** — a stale/out-of-order `chain.tx.*` event (e.g. a `seen` after `finalized`, or a lower
  confirmation count than already recorded) is ignored without changing state **and** that it was
  ignored is recorded. This task's own scope is the recording half (the staleness *detection* is
  task 11's own `ChainEventConsumer` — `PaymentStateMachine.isPermitted(...)` is the mechanism
  task 11 will use to detect it, per T09's own design).

## Locked Decisions

- **L1** — the state graph `VerificationRecord.from_state`/`to_state` reference: `PaymentState`
  (T09), `@Enumerated(STRING)` to match the real `VARCHAR(16)` column (mirrors `Invoice.state`'s
  own established precedent).
- **L5** — idempotent, dedupe-on-key consumers; `trigger_event_key` is this task's own column
  carrying the deterministic `chain:txhash:eventtype` key a future consumer (task 11) will supply
  — this task does not itself perform dedupe (that's `processed_events`, task 11's own table).
- **L7** — the append-only ledger pattern (a different table, `ledger_entries`) is the nearest
  LOCKED-decision precedent for "no `UPDATE`/`DELETE`, service DB role has `INSERT`+`SELECT`
  only" — `verification_records` is built the same way (confirmed: `payment_app` already has
  exactly `INSERT`+`SELECT` on it since T02, no `UPDATE`/`DELETE` grant exists or is needed).
- **L11** — reorg is a first-class transition, not an error; this task's own recording must treat
  a reversal as a normal, expected row shape, not a special "error" case.
- **L12** — module boundaries; the new `verification` package must import no entity from any
  other feature package. It will import `PaymentState` (T09, an enum, not an entity — permitted,
  confirmed by direct read of the T09 ArchUnit rule's own scope: `@Entity`-annotated classes
  only).

## Files involved

**New (this task's own scope — entity + repository + service, per Phase 0's own scope reading):**
- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecord.java`
- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordRepository.java`
- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordService.java`
- Test files mirroring both, under `src/test/java/com/themistra/payment/verification/`.

**Existing, to read/extend:**
- `PaymentState` (`payment` package, T09) — the enum `from_state`/`to_state` map to.
- `V1__payments_baseline.sql` — the real, immutable `verification_records` table shape (7 columns
  besides the surrogate `id`); `V2__payment_app_role_and_grants.sql` — the existing
  `INSERT`+`SELECT` grant (no new migration needed).
- `ArchitectureTest` (T09's own new rule) — the new `verification` package needs adding to its
  `FEATURE_MODULES` list (mirrors the `events`-module gap T09 itself found and fixed) — **flagged
  so Phase 2/5 don't repeat that same miss.**

**Not involved (confirmed by Phase 0, carried forward):**
- No `Payment` entity/repository/service — task 11's own scope. `VerificationRecord.paymentId`
  is a plain `Long`.
- No `ChainEventConsumer`, no `processed_events` — task 11.
- No actual `PaymentStateMachine.transition(...)` call site — task 12 (for reversals) and task 11
  (for forward transitions/staleness detection) wire the real orchestration; this task only
  builds what records the outcome.

## Dependencies

- `PaymentState` (T09) — the only cross-package production dependency.
- `hibernate-core` (Spring Boot 3.5.4's own managed version — Hibernate 6.x), specifically its
  native `@JdbcTypeCode(SqlTypes.JSON)` mechanism (`org.hibernate.annotations`/
  `org.hibernate.type.SqlTypes`) for the `detail JSONB` column — confirmed available without any
  additional dependency (Hibernate 6 ships this natively; the older third-party
  `hibernate-types` library some Hibernate-5-era codebases need is not required here). No
  precedent for this exists elsewhere in this codebase yet — this task is the first to map a
  JSONB column.
- `Clock` (already available service-wide via `ClockConfig`, T04) — `occurred_at` should be set
  explicitly from an injected `Clock`, mirroring `Invoice.createdAt`'s own established pattern,
  not left to the DB's own `DEFAULT now()`.

## Acceptance Criteria

- **AC1 (R11, named)** — recording a `CONFIRMING → SEEN` reversal with a trigger event key
  persists a row with `from_state=CONFIRMING`, `to_state=SEEN`, `trigger_event_key` set.
- **AC2 (R12, named)** — recording a `SEEN → WATCHING` reversal persists the equivalent row.
- **AC3 (R14, named)** — recording an ignored stale event persists a row with the *trigger event
  key* set and some durable indication it was a no-op, not a real transition. **Open question
  (Phase 0, carried forward): exact row shape** — tentatively `from_state == to_state` (the
  payment's own real, unchanged current state) plus a `detail` payload explaining why. To confirm
  in Phase 2/3.
- **AC4 (general, task's own "every transition" framing, not individually named)** — a forward
  transition (not a reversal, not an ignored event) can also be recorded through the same general
  mechanism, with `from_state != to_state` and no implied specialness.
- **AC5 (L7-style append-only)** — no code path updates or deletes an existing
  `VerificationRecord` row; the repository exposes no `update`/`delete` beyond what
  `JpaRepository` provides by default (and nothing in this task's own code ever calls `delete*`).
- **AC6 (L12)** — the new `verification` package imports no entity from `invoice`/`payment`/
  `events`; `ArchitectureTest`'s own `FEATURE_MODULES` list is updated to include it (so the
  fail-fast "unmapped entity" path doesn't trip against `VerificationRecord` itself, the same
  category of gap T09 found for `events`).
- **AC7 (defensive, established convention)** — null-argument handling via
  `Objects.requireNonNull`, mirroring every other service class in this codebase.

## Tests required

- `shouldWalkBackwardConfirmingToSeenOnReorg` (named, R11) — recording method, not a live
  consumer/state-machine call (Phase 0's own scope reading).
- `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` (named, R12).
- `shouldIgnoreOutOfOrderOrStaleChainEvents` (named, R14).
- A forward-transition recording test (AC4, not individually named — "every transition" implies
  coverage beyond just the two named reversal shapes).
- An append-only guard test (AC5) — likely a structural/source-text or reflection check that the
  repository interface declares no custom delete/update method (mirrors `OutboxEvent`'s own
  similar append-semantics discipline, though that one does have an update path for
  `published_at` — this one should have none at all, a stronger guarantee).
- `ArchitectureTest`'s own existing canaries re-run with `verification` added to
  `FEATURE_MODULES` — confirm this doesn't regress (AC6).
- Null-argument tests (AC7).

## Open Questions

- **Scope boundary (carried forward from Phase 0, not yet confirmed):** does this task build
  `VerificationRecordRepository`/`VerificationRecordService`, or only the entity? Tentative
  reading: all three, since the task's own text describes recording *behavior*, not just a table
  mapping. To confirm in Phase 2/3.
- **The "ignored" row shape** (carried forward from Phase 0): `to_state` is `NOT NULL`, so an
  ignored event's row cannot leave it unset. Tentative: `from_state == to_state` (the real,
  unchanged state) plus a `detail` JSONB payload with the reason. To confirm in Phase 2, invite a
  genuine Phase 3 Kimi challenge (this is a real design choice with consequences for how task 11
  later calls this service, similar in spirit to T09's own `HELD`-edge question, though lower
  stakes since nothing else depends on parsing these rows automatically yet).
- **`detail` JSONB mapping mechanism**: resolved during this phase, not left open —
  `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6, no extra dependency), confirmed via the root POM's
  own Spring Boot 3.5.4 parent. Phase 2 to pin the exact Java type (`Map<String, Object>` vs. a
  typed record) for the mapped field.
