# payment · T09 · Phase 6 — Implementation Notes

Implemented exactly per `artifacts/05-implementation-plan.md`. No test files written this phase
(Phase 10's own job, per standing discipline).

## Files created

- `PaymentState.java` — 7-value enum, matches `chk_payment_state` exactly.
- `IllegalPaymentStateTransitionException.java` — public, carries `current`/`target`.
- `PaymentStateMachine.java` — stateless `@Component`; `PERMITTED_TRANSITIONS` is an
  `EnumMap<PaymentState, EnumSet<PaymentState>>` built once in a static initializer from the
  frozen brief's own 10-edge list; `transition`/`isPermitted` both delegate to the same lookup.
- `RogueInvoiceEntityReferencer.java` (test fixture) — mirrors `crypto-service`'s
  `RogueWatchEntityReferencer` exactly: a single never-read `private final Invoice` field, placed
  inside the real `payment` test-source package.
- `RogueUnmappedEntity.java` (test fixture) — mirrors `crypto-service`'s own identical fixture: a
  bare `@Entity` class in the existing `archtestfixtures` package.

## Files modified

- `ArchitectureTest.java` — added `FEATURE_MODULES`, `featureModuleOf`,
  `onlyBeAccessedFromTheSameFeatureModule()`, the `shouldPreventCrossModuleEntityImports` rule,
  and its three canaries, mirroring `crypto-service`'s `CrossModuleEntityArchitectureTest` shape
  but with no `@ArchTest` field (this file's own established no-`@ArchTest` convention).

## A necessary correction found by running the new rule against real code, not assumed

The plan's own `FEATURE_MODULES` list (`invoice`, `payment`) was incomplete. Running the real
canary against the actual codebase (not just the two new fixtures) failed immediately:
`com.themistra.payment.events.OutboxEvent is annotated @Entity but does not reside in any module
listed in FEATURE_MODULES` — T04's own `OutboxEvent` entity lives in the `events` package, which
isn't a "business feature" like `invoice`/`payment` but is a real top-level package owning a real
`@Entity`. Added `events` to `FEATURE_MODULES` (disclosed in the field's own Javadoc) — this is
the correct fix, not a workaround: `events` package classes (`OutboxPublisher`/`OutboxRelay`)
access `OutboxEvent` from within the same module, so this resolves cleanly to "same module," and
it means a future module that ever imports `OutboxEvent` directly (bypassing `OutboxPublisher`'s
own public API) would now be caught too — a real, if incidental, hardening the plan didn't
anticipate but the rule itself surfaced the moment it ran against real code. No production
behavior changed; only the test's own module-recognition list.

## Verification performed this phase

- `mvn -pl services/payment -am compile` / `test-compile` — clean.
- `mvn -pl services/payment -am test -Dtest='ArchitectureTest'` — 5/5 passing, including both new
  genuine-violation canaries (confirmed they actually fail against their respective fixtures
  before the `FEATURE_MODULES` fix above, and pass cleanly after).
- Full non-Docker-dependent suite: `mvn -pl services/payment -am test
  -Dtest='!SchemaBaselineMigrationIntegrationTest,!InvoiceRepositoryIntegrationTest'` —
  **165/165 passing** (162 carried forward + 3 new `ArchitectureTest` tests; no
  `PaymentStateMachineTest` yet — Phase 10's own job).

## Net result

Production code for `PaymentState`/`PaymentStateMachine`/`IllegalPaymentStateTransitionException`
complete per the frozen brief's own 10-edge, narrow-`HELD` decision. `ArchitectureTest` now has a
real, working `shouldPreventCrossModuleEntityImports` rule (it did not exist before this task,
correcting Phase 2's own wrong "near-zero change" assumption, confirmed in Phase 4). Proceeding to
Phase 7.
