# payment · T11 · Phase 10 — Test Generation

Created `PaymentServiceTest` (17 tests), `ChainEventConsumerTest` (10 tests), and
`ChainEventDtoDeserializationTest` (3 tests) per the frozen brief.

## `PaymentServiceTest`

Uses a **real** `PaymentStateMachine` (stateless, no dependencies) rather than a mock, mirroring
`VerificationRecordServiceTest`'s own real-`Clock` precedent — genuine confidence the two layers
agree, not a mock that would just answer `true` for everything. Covers: the named `SEEN`/
`CONFIRMING`/`FINALIZED` transitions; the Finding-3 transition/refresh/stale three-way split for
`chain.tx.confirmed` (including the `VerificationRecordService` interaction, never called for a
refresh); `chain.tx.finalized` from both `SEEN` and `CONFIRMING`, including the real
amount/address persistence; the two Finding-1 guards (`OPEN`-invoice and existing-non-terminal-
payment); the AC9 guard (`Payment.createSeen` always sets `SEEN`); "payment not found" drops for
both `confirmed`/`finalized`; null-argument handling.

**Found a real test-fixture bug before it could mask anything**: the first version of
`shouldRecordVerificationWithNullFromStateOnFirstSeen` used `any(Long.class)` to match the
`paymentId` argument, but the mocked `repository.save(...)` just echoes the unsaved `Payment`
back (no real Hibernate flush populates the `@GeneratedValue` `id`), so the actual argument is
`null` — and `any(Long.class)` never matches `null` in Mockito. Fixed with the untyped `any()`.

## `ChainEventConsumerTest`

Covers: `shouldDedupeDuplicateChainEventsByEventKey` (named, all 3 listeners independently),
`shouldUnregisterWatchOnFirstSeenAndIgnoreSecondMatch` (named, `InOrder`-verified —
`unregisterWatch` strictly after `applySeen`, not merely "both called"), the Finding-1
consumer-side consequence (`applySeen` returning `false` means `unregisterWatch` is never
called), an unknown-`watchId` drop, and the Finding-2 regression (a malformed payload on both
`chain.tx.seen`/`chain.tx.confirmed` is caught and dropped, never propagates).

## `ChainEventDtoDeserializationTest`

3 tests, one per consumed event type, each citing the real schema file it mirrors. Confirms
`ChainTxFinalized.amount()` deserializes a JSON string to `BigDecimal` with full precision
(empirically confirmed at Phase 6, re-confirmed here as a permanent regression guard).

## AC10 — a deliberate, reasoned omission, not a forgotten test

`consumer`/`payment` never referencing `InvoiceRepository` directly needs no test:
`InvoiceRepository` is **package-private** (confirmed by direct read — no `public` modifier), so
referencing it from another package is a **compile-time impossibility**, a stronger guarantee
than any runtime/ArchUnit check could provide.

## Mutation verification performed this phase

| Mutation | Test(s) that caught it |
|---|---|
| Finding 1's `existsByInvoiceIdAndStateIn` guard disabled (`if (false && ...)`) | `shouldNotCreateASecondPaymentWhenANonTerminalPaymentAlreadyExistsForTheInvoice` |

Mutation testing was intentionally not exhausted across every new assertion this phase (time
was reallocated per direction mid-session) — the fixes already carry their own direct
behavioral test coverage (confirmed passing above), and the two self-caught defects already
mutation-verified at Phases 7/9's own time (the `fromState` race, Finding 1 itself). Finding 4's
own fix (`findCurrentState` bypassing the first-level cache) cannot be meaningfully
mutation-tested at the mocked-repository unit level at all — Mockito has no concept of a
persistence-context identity map, so reverting to `findById(...).map(...)` would still pass the
exact same mocked test; its correctness rests on JPA's own documented specification behavior
(Phase 9), not something a unit test can exercise — true verification needs a live
Hibernate/Postgres run (Docker unavailable this session).

## Verification performed this phase

- `mvn -pl services/payment -am test-compile` — clean.
- Full non-Docker suite: **226/226 passing** (196 carried forward + 30 new).

## Net result

All of the frozen brief's own Required Tests are present. Proceeding to Phase 11 — per the Kimi
handoff/wait discipline, this is Kimi 2.7's job; stopping here to hand off.
