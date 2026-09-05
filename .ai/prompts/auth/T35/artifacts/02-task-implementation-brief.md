<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T35 · Phase 2 — Task Implementation Brief

## Task

Fix and extend `ArchitectureTest.java`'s cross-module-boundary enforcement: (1) fix the existing
account-entity rule's real bug so it correctly exempts `account.dto`/`account.event`, and (2) add a
new rule constraining which modules' services a controller may depend on.

## Purpose

Make L12 ("no feature module may import an entity class from another feature module") actually
correct where it's already meant to apply, and close a second, related gap the task's own wording
names: controllers reaching into another module's service layer without a defined boundary.

## Scope

**In:** fixing `only_the_account_module_may_touch_the_Account_entity`'s wildcard bug; adding one
new rule (name/shape TBD by D2) for controller→service dependencies; a canary test for each,
matching T32's established pattern (ArchUnit doesn't execute under `mvn test` in this environment).

**Out:** any change to `AccountController`/`AdminAccountController`'s actual code — both existing
cross-module dependencies (`SessionService`, `LockoutService`) are accepted, pre-existing, and
explicitly documented design decisions (Phase 0), not defects this task fixes.

## Business Rules

None (process/verification task).

## Locked Decisions

- **L12.** Enforced, now correctly, by both rules this task adds/fixes.

## Dependencies

ArchUnit 1.3.0 (already resolved). No new library.

## Inputs / Outputs / State Changes

None (build/test-time only).

## Files to Create

None.

## Files to Modify

- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`.

## Files NOT to Modify

- `AccountController.java`, `AdminAccountController.java`, `SessionService.java`,
  `LockoutService.java` — read-only inputs, not touched.
- Any `spec/` file.

## This Task's Own Design Decisions (D1-D2, tentative — Phase 3/4 to confirm)

- **D1 (Phase 0 Finding 1 fix).** Change `only_the_account_module_may_touch_the_Account_entity`'s
  `.resideOutsideOfPackage("com.themistra.auth.account")` to
  `.resideOutsideOfPackage("com.themistra.auth.account..")` — the `..` suffix is ArchUnit's own
  package-plus-subpackages convention (already used elsewhere in this same file, e.g.
  `authz_never_depends_on_the_account_module`'s `resideInAnyPackage(..., "com.themistra.auth.account..")`).
  This single-token change correctly exempts `account.dto`/`account.event` while leaving the rule's
  actual protection (no OTHER module may touch `Account`) unchanged. Given the named test
  (`shouldPreventCrossModuleEntityImports`) maps onto this exact rule, D1 also renames it to match
  the spec's own name, keeping the `.because(...)` text otherwise unchanged.
- **D2 (Phase 0 Finding 2).** New rule: no controller depends on another module's service, **except
  two explicitly-named, already-shipped exceptions**: `AccountController`→`SessionService`
  (T28, sessions are account-adjacent by design) and `AdminAccountController`→`LockoutService`
  (T14/R20, documented in `AdminAccountController`'s own Javadoc as an established, precedented
  pattern). The rule's purpose is to catch a *third, unapproved* cross-module controller→service
  dependency being introduced in the future, not to retroactively forbid the two that already
  exist and were each already deliberately decided. Exact ArchUnit mechanism (a shared
  per-controller assertion vs. one generic rule with an exclusion predicate) is a Phase 5 decision.

## Acceptance Criteria

- **AC1.** `shouldPreventCrossModuleEntityImports` (the fixed/renamed rule) fails on a deliberately
  reintroduced violation and passes with `account.dto`/`account.event` correctly exempted — both
  proven via negative-proof, not assumed.
- **AC2.** The new controller→service rule fails if a controller is given a new, un-allowlisted
  cross-module service dependency, and passes for the two existing named exceptions plus every
  controller's own in-module dependencies.
- **AC3.** Both rules are actually enforced by `mvn test` (T32's canary pattern), not merely
  present as inert `@ArchTest` fields.

## Required Tests

- `shouldPreventCrossModuleEntityImports` (named) + its own canary.
- The new controller→service rule + its own canary.
- Negative-proof runs for both, each direction.

## Constraints

- **Performance / thread-safety / transaction / null handling:** N/A.
- **Security:** N/A directly, though module-boundary discipline is itself a defense-in-depth
  measure per L12's own framing.
- **Module boundaries (L12):** this task's own subject.

## Open Questions

No blockers. D1/D2 above are tentative, flagged for Phase 3/4 challenge — D2 in particular, since
the exact mechanism for expressing a "per-controller allowed module, plus named exceptions" rule
in ArchUnit is the one genuinely open design question.

---

**Phase 2 complete — Task Implementation Brief written.** Proceed to Phase 3 (Kimi design
challenge) on approval.
