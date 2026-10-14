# payment · T04 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T04 — Outbox & EventTopics |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | this artifact |

Findings only — no fixes applied here (Phase 9).

## Finding 1 — No runtime guard that `OutboxPublisher.publish` runs inside an open transaction
- **Severity:** Low (observation, not a defect)
- **Evidence:** `events/OutboxPublisher.java:35-52` — `publish` calls `repository.save(...)`
  directly with no check (e.g. `TransactionSynchronizationManager.isActualTransactionActive()`)
  that a caller's `@Transactional` method is actually open. If a future call site forgets the
  annotation, the save auto-commits independently and the documented "atomic with the state
  change" guarantee (agents.md "Events & messaging") silently does not hold — no exception, no
  log, just a correctness gap that only surfaces as a missing/duplicated event much later.
- **Recommendation:** No change requested for this task. `crypto-service`'s own `OutboxPublisher`
  has the identical no-guard shape (confirmed by direct comparison), so this is an established,
  consistent precedent across the codebase rather than a new risk introduced here. Worth a future
  cross-cutting hardening task if ever revisited, not an R30-scoped fix.

## Finding 2 — `EventTopics` has no compile-time enumeration safety for `aggregateType`
- **Severity:** Low (inherent to the design, already accepted at freeze)
- **Evidence:** `events/EventTopics.java:22-28` — `aggregateType` is a plain `String`; a typo at
  a call site (`"Invoice"` vs `"invoice"`, trailing whitespace, etc.) only fails at the moment
  `OutboxRelay` tries to route that row, not at compile time or at publish time.
- **Recommendation:** No change requested — `design.md` §4c specifies this table with plain
  string keys verbatim, and the frozen brief (Phase 4) did not introduce an enum. Noted so a
  future task does not assume type-safety this layer does not provide.

## Finding 3 — `OutboxEventRepository` and `OutboxEvent` have no explicit access modifier (package-private)
- **Severity:** None — this is correct, flagging only to confirm it was deliberate
- **Evidence:** `events/OutboxEventRepository.java:8` (`interface OutboxEventRepository`, no
  `public`). Only `OutboxPublisher` and `OutboxRelay`, both in the same `events` package, touch
  it. This matches the task's own "the only sanctioned way to emit" framing (agents.md) —
  nothing outside the package can reach the repository or construct an `OutboxEvent` directly
  (its constructor is `protected`, its factory is the only public entry point).

## Boundary / null-safety / thread-safety / transaction-boundary review

- Null/blank checks on `aggregateType`/`aggregateId`/`eventType` are present and tested (`Objects.
  requireNonNull` + `requireNonBlank`); `payload` is null-checked but not blank-checked, correct
  since it is an arbitrary `Object`, not a `String`.
- `OutboxRelay` is a Spring singleton driven by a single `@Scheduled` thread per instance; no
  shared mutable state inside the bean. Multi-instance races are documented and accepted
  (at-least-once, no ShedLock — design §4c, Phase 3).
- `OutboxRelay.relayOne`'s three catch blocks (`ExecutionException|TimeoutException`,
  `InterruptedException`, `DataAccessException`) correctly leave the row unpublished on any
  failure path rather than swallowing it silently — matches the frozen brief's AC2.
- No money types or secrets are touched by this task; not applicable.

## Verdict

No fixes required before Phase 8. Findings 1 and 2 are disclosed, accepted-precedent
observations, not defects against the frozen brief.
