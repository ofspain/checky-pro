# payment · T09 · Phase 7 — Self Review

Re-read every AC/Constraint in `artifacts/04-frozen-task-brief.md` against the real code (not
the plan).

## AC1/AC2 (the 10-edge set, and everything else throws)

Verified by direct trace: `PERMITTED_TRANSITIONS`'s own 7 `put` calls total exactly 10 edges
(`CREATED`→1, `WATCHING`→1, `SEEN`→3, `CONFIRMING`→2, `FINALIZED`→2, `ATTESTED`→1, `HELD`→0),
matching the frozen brief's own locked list edge-for-edge. `HELD`'s own `EnumSet.noneOf(...)`
makes every `(HELD, x)` pair — including `(HELD, HELD)` — fall through to `false`/throw, closing
Finding 4 (terminal `HELD`) by construction, not by a special case. No test exists yet to prove
any of this at runtime (Phase 10's own job) — this is a static-trace confirmation only.

## AC3 (cross-module ArchUnit rule)

All 5 `ArchitectureTest` tests pass, including the two new genuine-violation canaries. Found and
fixed one real gap during implementation itself (disclosed in `06-implementation-notes.md`, not
re-litigated here): `FEATURE_MODULES` needed to also list `events` (`OutboxEvent`'s own real
package), or the rule would fail against already-shipped T04 code, not just the deliberate
fixtures. One cosmetic fix made this phase: a grammatically awkward Javadoc sentence in
`shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule`'s own comment ("entirely's own module
list") — corrected to reference `FEATURE_MODULES` directly and list all three real modules.

## AC4 (null handling)

`isPermitted` validates both arguments via `Objects.requireNonNull` before any lookup;
`transition` calls `isPermitted` first (inside its own `if` condition), so a null argument NPEs
there before `transition`'s own `throw new IllegalPaymentStateTransitionException(...)` line is
ever reached. Confirmed by direct trace, not yet by a runtime test (Phase 10).

## AC5 (stateless, zero-arg)

`PaymentStateMachine` declares one `private static final` field (`PERMITTED_TRANSITIONS` — static,
not instance state) and no instance field at all; its only constructor is the implicit public
no-arg one. Confirmed by direct code read; the frozen brief's own reflection-based test for this
is Phase 10's job, not re-verified here by any other means.

## Constraints

- **Thread-safety**: `PERMITTED_TRANSITIONS` is `static final`, populated once in a static
  initializer before the class is visible to any other thread (JLS-guaranteed), never mutated
  afterward — read-only for the lifetime of the JVM. No field added to the instance side at all.
- **Module boundaries (L12)**: `PaymentStateMachine`/`PaymentState`/
  `IllegalPaymentStateTransitionException` import nothing from `invoice`/`common`/`events`.
  Confirmed by direct read of each file's own import list (3-4 lines each, all
  `java.*`/`org.springframework.stereotype.Component`).
- **`IllegalPaymentStateTransitionException` is public**: confirmed (`public class
  IllegalPaymentStateTransitionException extends RuntimeException`), not package-private.
- **R14 out-of-contract**: confirmed by design — no staleness logic anywhere in
  `PaymentStateMachine`; it only ever answers "is this pair in the fixed edge set."

## No other findings

No defect found beyond the one cosmetic Javadoc fix above (already applied). The
`FEATURE_MODULES` gap was caught and fixed during Phase 6 implementation itself (empirically, by
running the test against real code), not newly found here.

## Verification performed this phase

- `mvn -pl services/payment -am test -Dtest='ArchitectureTest'` — 5/5 passing (re-confirmed after
  the cosmetic Javadoc fix).
- Full non-Docker suite: 165/165 (unchanged from Phase 6 — this phase made no behavioral change).

## Net result

No code defects found. One Javadoc wording fix applied directly (self-evident, not a design
question). Proceeding to Phase 8 — per the Kimi handoff/wait discipline, stopping here to hand
off.
