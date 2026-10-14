# payment · T11 · Phase 9 — Review Resolution

Human Approval gate decision not required — none of Kimi's 7 Phase 8 findings is a design
trade-off; each has an obvious, uncontroversial fix or a clear, defensible deferral. Recorded
here for the audit trail.

## Finding 1 — `applySeen` never guarded against an existing non-terminal payment row

**ACCEPTED and FIXED — the most significant finding this phase, a confirmed, real omission.**
The frozen brief's own Decision 3 named this exact race (a second `chain.tx.seen` slipping
through the disclosed T08 residual unregister-timing window) and said to guard against it — but
Phase 6's implementation never actually wrote the check. Added
`PaymentRepository.existsByInvoiceIdAndStateIn` (a Spring Data derived query over the same
non-terminal set `InvoiceRepository.findExpiredCandidatesWithoutInFlightPayment` already uses:
`CREATED`/`WATCHING`/`SEEN`/`CONFIRMING`), called in `applySeen` immediately after the
`OPEN`-invoice check. A match logs `WARN` and returns `false` — same disclosed, schema-driven
"no `VerificationRecord` possible" limitation as the `OPEN`-invoice case, since the event
genuinely doesn't belong to any addressable row.

## Finding 2 — listener methods propagate `JsonProcessingException`

**ACCEPTED and FIXED.** `ChainEventConsumer`'s own 3 listener methods no longer declare
`throws JsonProcessingException`; a new private `deserialize` helper catches it, logs at
`ERROR` with the raw payload, and returns `null` (the caller treats `null` as "drop this
message," same as a duplicate or an unrecognized watch). **Disclosed, not silently diverged
from the established precedent**: `notification-service`'s own `AuthEventConsumer` still
propagates this same exception uncaught — an identical, pre-existing gap there, not uniquely
T11's problem to solve, and not retroactively fixed in that service as part of this task.

## Finding 3 — `applySeen` returned the `Payment` entity across the module boundary

**ACCEPTED and FIXED.** `applySeen` now returns `boolean`, not `Optional<Payment>` — simpler
than Kimi's own suggested dedicated result record, since `ChainEventConsumer` never actually
needed any `Payment` field, only success/failure (it already holds the one piece of information
it needs — `watchId` — via its own `InvoiceWatchView`). Avoids introducing a type purely to
satisfy a boundary that doesn't need it.

## Finding 4 — the re-fetched "current state" for an ignored record could be stale (first-level cache)

**ACCEPTED and FIXED — confirmed correct per JPA's own first-level-cache/identity-map
semantics** (well-documented specification behavior, not something requiring empirical
verification the way a framework implementation detail would). Added
`PaymentRepository.findCurrentState` — a plain scalar JPQL projection (`SELECT p.state FROM
Payment p WHERE p.id = :id`), which always executes a real query rather than returning an
already-managed, potentially-stale entity instance from the same transaction's own persistence
context. `applyConfirmed`/`applyFinalized` both use this instead of
`repository.findById(...).map(Payment::getState)`.

## Finding 5 — no chain/token consistency validation between invoice and event

**ACKNOWLEDGED, deferred — not added here.** `chain.tx.*` events are internal,
trusted-producer traffic (L3, same security posture as every other consumed event in this
codebase, with no established precedent anywhere for this kind of defensive cross-field
validation on an internal producer). A genuine chain/token mismatch is thematically
amount-discrepancy-adjacent territory — task 13's own natural scope (R15/R16) — not pre-empted
here.

## Finding 6 — confirmed/finalized paths enforce via `WHERE`, not `PaymentStateMachine` calls

**ACCEPTED and FIXED (code, not just documentation).** Added a defensive
`stateMachine.isPermitted(...)` sanity check immediately before each atomic update attempt in
`applyConfirmed`/`tryFinalizeFrom` — mirroring `applySeen`'s own identical, already-established
pattern. Not an enforcement mechanism (the `UPDATE`'s own `WHERE` clause already is one); a
guard against `PaymentStateMachine`'s own edge set (T09) ever drifting out of sync with this
repository's own hardcoded queries without anyone noticing, throwing loudly if it ever does.

## Finding 7 — `InvoiceRepositoryIntegrationTest` (T08) still broken

**ACKNOWLEDGED, no new action.** Already disclosed and logged in memory since T09; remains out
of T11's own scope.

## Verification performed this phase

- `mvn -pl services/payment -am compile` / `test-compile` — clean.
- Full non-Docker suite: **196/196 passing, unchanged** (all fixes are production-only; Phase 10
  will add dedicated regression tests for each).

## Net result

5 real findings fixed (1 high-severity, confirmed omission; 1 medium operational risk; 1
medium/low boundary hardening; 1 medium audit-accuracy fix; 1 low documentation/drift guard), 1
deferred with clear rationale to its own natural future task, 1 re-acknowledged as already
out of scope. Proceeding to Phase 10.
