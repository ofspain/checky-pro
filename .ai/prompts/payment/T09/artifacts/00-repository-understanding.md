# payment · T09 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T09 — Payment state machine |
| **Consumes** | — (entry phase) |

## 1. Architecture summary

`payment-service` (Java 21, Spring Boot) currently has: a Flyway-migrated `payments` schema (9
tables, `V1`-`V6`, T02/T06/T08); a resource-server security layer validating JWTs against
auth-service's JWKS (T03); five validated `@ConfigurationProperties` records (T03); an outbound
Kafka pipeline (`OutboxPublisher`/`OutboxRelay`/`EventTopics`/`KafkaProducerConfig`, T04); a full
`invoice` package — `Invoice` entity, `InvoiceState` enum, `InvoiceRepository`, `InvoiceService`
(create/read/expire), `InvoiceController` (`POST`/`GET /api/v1/invoices`), `CryptoClient` in
`common` (register/unregister watch), and `InvoiceExpirySweepJob` (ShedLock) — T05-T08. No
`payment` package, no `Payment` entity, no consumer infrastructure, and no verification-record
infrastructure exist yet. T09 is the first task to touch any of them.

## 2. Existing code this task touches

- **New**: a `payment` package — does not exist. Per `tasks.md` task 9's own text ("Implement
  `PaymentStateMachine` as pure logic... Unit-test every permitted transition and assert illegal
  transitions throw"), this task's own scope is narrowly `PaymentState` (enum) and
  `PaymentStateMachine` (pure logic) only. `design.md` §6's own package map lists `Payment.java`
  (entity), `PaymentRepository.java`, and `PaymentService.java` in the same `payment/` package,
  but task 9's own text never names any of them — those are almost certainly task 11's own scope
  (`ChainEventConsumer`, which needs somewhere to persist), not this task's. **Flagged for Phase 1
  scope confirmation, not assumed** — building an entity/repository/persistence-facing service
  this task doesn't ask for would be scope creep; the reverse (task 9 secretly needing one) would
  be a real gap. Narrow reading for now: this task produces a standalone, Spring-free Java class
  plus its own enum, nothing else.
- **A real tension between `L1`'s own prose and `design.md` §4c's own VERBATIM enum comment block,
  found by direct reading, not assumed away:** `L1` ("LOCKED decision", `design.md` §4a) says
  permitted transitions are *exactly* `CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED →
  ATTESTED`, "with the only permitted reversals being `CONFIRMING → SEEN` and `SEEN → WATCHING`...
  No other transition may be added." Read hyper-literally, this would forbid two transitions the
  verbatim `PaymentState` enum comment (§4c) explicitly lists: `SEEN → FINALIZED` ("chain.tx.finalized
  arriving without a prior confirmed") and `any → HELD` ("attest `BLOCKED`, or post-attest reorg").
  Both of these are independently corroborated elsewhere in the same spec, not just that one
  comment block: the "Consumed topics" section a few lines below it explicitly diagrams
  `chain.tx.finalized -> */SEEN/CONFIRMING -> FINALIZED`; `L11` explicitly requires a post-attest
  reorg to move the payment to `HELD`; `R20` explicitly requires a `BLOCKED` attest outcome to move
  the payment/invoice to `HELD`; and the `PaymentState` enum itself has a `HELD` value with no
  other spec text saying it's unreachable. L1's own prose reads as an abbreviated/narrative summary
  that named the *reversals* explicitly but didn't separately enumerate the *skip-ahead* and
  *escape-hatch* transitions already specified elsewhere in the same section — not a genuine
  conflicting design intent. **Resolved reading, to confirm explicitly in Phase 1/3, not silently
  assumed:** the full 9-edge transition set from the §4c verbatim block is authoritative; L1's "no
  other transition may be added" is read as "no transition beyond the full set §4c actually
  specifies," not as contradicting §4c itself.
- **Existing, to reuse as-is**: `InvoiceState` (`invoice` package) as the sibling enum's own
  established style (plain `enum`, no methods, mirrors the real DB `CHECK` constraint) — the
  precedent `PaymentState` should mirror. No existing `payments.payments` table column currently
  maps to any Java entity (task 11's own scope per `V1__payments_baseline.sql`'s own
  `chk_payment_state CHECK (state IN ('CREATED','WATCHING','SEEN','CONFIRMING','FINALIZED',
  'ATTESTED','HELD'))` — 7 values, matching the §4c enum exactly, confirmed by direct read).

## 3. Established patterns to follow

- **Pure-logic, Spring-free classes**: no precedent in `payment-service` itself yet. The closest
  cross-service precedent is `auth-service`'s own `LockoutStateMachine` (`authn` package) — final
  class, no Spring annotations, constructor-validated invariants via `Objects.requireNonNull`/
  `IllegalArgumentException`, Javadoc disclosing every non-obvious design choice, nested
  records/enums for its own inputs/outputs. Its own shape (a continuous decision-evaluation
  function) doesn't map onto T09's much simpler "fixed permitted-edge-set, illegal edge throws"
  requirement, so it's a *style* precedent (immutability, Javadoc discipline, no persistence
  awareness, package-boundary hygiene per L12), not a *structural* one to copy literally. No
  existing `IllegalStateTransitionException`-shaped class exists anywhere in the codebase today —
  this task likely introduces the first one.
- **Enums**: `InvoiceState` is the only sibling precedent in this service — a bare `enum`, no
  behavior, Javadoc pointing at the real `CHECK` constraint it mirrors.
- **Module boundaries (L12)**: `ArchitectureTest` (T05) already enforces no cross-module entity
  imports and no floating-point money fields for the `invoice` package; a `payment` package will
  need the same ArchUnit coverage extended to it, though `shouldPreventCrossModuleEntityImports`
  (the one test `package.md` §8 names for this task, mapped to "L1/L12") suggests this task itself
  may only need the structural "no entity import across feature packages" rule exercised against
  `PaymentStateMachine` (which should import no entity at all, being pure logic) rather than a new
  ArchUnit rule being authored from scratch — **Phase 1 should confirm whether this means
  extending the existing `ArchitectureTest`, or whether `PaymentStateMachine`'s own complete lack
  of entity imports is what the test is meant to prove once it exists.**

## 4. Testing conventions

- Plain JUnit 5, no Spring context, for anything Spring-free (mirrors `InvoiceServiceTest`'s own
  `@ExtendWith(MockitoExtension.class)` style where mocking is needed; a true pure-logic class
  like `LockoutStateMachine`'s own test has no Mockito at all, just direct construction and
  assertion — likely the closer match for `PaymentStateMachineTest`).
  Fixed `Clock`/`Instant` convention used throughout this service (`InvoiceServiceTest`'s own
  `NOW`/`Clock.fixed`) — likely inapplicable here since nothing in L1's own transition table
  depends on time.
- `assertThatThrownBy(...).isInstanceOf(...)` (AssertJ) is this codebase's established style for
  "must throw" assertions (`InvoiceServiceTest`'s own null-argument tests).
- ArchUnit (`ArchitectureTest`, T05) for module-boundary/money-type rules — plain `ArchRule` field
  + `@Test` canary shape, never `@ArchTest` (see [[surefire-archtest-field-bug]] memory — any class
  with an `@ArchTest` field silently reports 0 tests under this repo's Surefire 3.5.3).

## 5. Known gaps / unknowns

- **I do not know** whether `PaymentStateMachine` should expose a single `transition(currentState,
  event)` -style method keyed on the *consumed event type* (`chain.tx.seen`/`confirmed`/
  `finalized`/`reorged`, plus the watch-ack and the attest outcome), or a lower-level
  `transition(currentState, targetState)` method keyed purely on the state pair, with the caller
  (task 11's `ChainEventConsumer`) responsible for mapping an event type to the target state. The
  task text says "pure logic enforcing exactly the transitions," which could support either
  reading. This is a real design decision for Phase 2, not something to assume here.
- **I do not know** whether R14's own "stale event → ignore silently, record that it was ignored,
  do not change state" requirement is meant to be handled *inside* `PaymentStateMachine` itself
  (e.g. a transition attempt that happens to already be a no-op returns the unchanged state rather
  than throwing) or entirely *outside* it, by a future caller (task 11) that decides an event is
  stale before ever calling the state machine. Task 9's own text ("assert illegal transitions
  throw, not silently no-op") reads as being about this class's own contract for a transition that
  isn't a legal graph edge at all — a different concern from R14's "legal event, but stale/
  out-of-order relative to current state" scenario. Tentative reading, to confirm in Phase 1/2: the
  state machine enforces the fixed edge set and throws on any pair not in it; staleness-detection
  (R14) is a higher-level, caller-side concern for task 11, out of this task's own scope. Not
  assumed without flagging.
- **I do not know** the exact exception type/name to introduce — no existing precedent anywhere in
  this codebase for an "illegal state transition" exception. Phase 2 will need to name one.
- `PaymentRepository.java`/`PaymentService.java`/`Payment.java` (entity) are listed in `design.md`
  §6's own package map but, per point 2 above, are read as out of this task's own scope — to be
  confirmed explicitly in Phase 1, not silently built or silently skipped.
