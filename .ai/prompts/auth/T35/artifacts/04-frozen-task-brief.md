<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T35 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Consumes `artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md` (Kimi,
9 findings — well-evidenced with file:line citations throughout). All verified against actual
source before disposition, including a direct check of whether broadening the entity rule would
break any existing code (it wouldn't — see D1b). femi decided the two findings with genuine
trade-off weight via human gate; the remaining seven are folded in directly.

## Findings disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | `package.md` maps the named test to L10, not L12 | High | **Accepted, folded in.** Same recurring `package.md` staleness pattern found in T32/T33/T34 — L12 is correct (matches this task's own Phase 0 header), flagged as a stale spec cross-reference, not adopted. |
| 2 | The rule only protects `Account`, not "account entities" (plural) / L12's general text | Medium | **Resolved, femi's gate decision.** Broadened to cover every `@Entity` class in any module, not just `Account` — verified zero existing code would violate this (D1b). |
| 3 | D2's mechanism was deferred to Phase 5, unfreezeable as written | High | **Resolved, femi's gate decision.** Kimi's own proposed concrete design adopted (D2 below). |
| 4 | Controller/service detection undefined | Medium | **Resolved as part of D2.** Controller = `@RestController`-annotated class under `com.themistra.auth..`; service = `@Service`-annotated class in a feature-module package. |
| 5 | Module/package boundary model implicit | Medium | **Resolved as part of D2.** Explicit feature-module list below. |
| 6 | Negative-proof procedure for AC1/AC2 not described | Medium | **Accepted, but not added as new brief text** — this is already this entire pipeline's own established, repeatedly-demonstrated practice (T29-T34 all did exactly this at Phase 6/9), not something specific to T35 needing fresh specification. |
| 7 | Other pre-existing cross-module service dependencies (`LockoutService`→`AccountService`, `AccountUserDetailsService`→`AccountService`) could confuse reviewers into thinking D2 eliminates them | Low | **Accepted, folded in.** Both verified real (direct import check). Explicit scope sentence added: D2 constrains *controller*→service dependencies only, not service-to-service. |
| 8 | `@RestControllerAdvice`/`@ControllerAdvice` classes unaddressed | Low | **Accepted, folded in.** Explicitly excluded from D2's rule — confirmed `SessionExceptionHandler` (in `token`) legitimately handles `AccountController`'s exceptions, a pattern this rule must not break. |
| 9 | ArchRule field naming vs. named test vs. canary method naming inconsistent guidance | Low | **Resolved by existing precedent, not a new decision.** T32 already established the pattern this file uses: the `@ArchTest` field itself takes the named-test's exact name; the canary method gets its own descriptive name. Followed here unchanged — `shouldPreventCrossModuleEntityImports` is the field name, not the canary's. |

## Task

Fix `only_the_account_module_may_touch_the_Account_entity`'s wildcard bug and broaden it into a
general, all-modules, all-entities rule (renamed `shouldPreventCrossModuleEntityImports`, the named
test); add a new controller→service module-boundary rule.

## Scope

**In:** as Phase 2, plus D1b's broadened scope.

**Out:** unchanged from Phase 2 — no change to `AccountController`/`AdminAccountController`/
`SessionService`/`LockoutService`; no change to the accepted service-to-service dependencies named
in Finding 7's disposition.

## Locked Decisions

- **L12.** Now enforced generally (any entity, any module) rather than `Account`-specifically.

## This Task's Own Design Decisions (D1, D1b, D2 — final)

- **D1.** `.resideOutsideOfPackage("com.themistra.auth.account")` → 
  `.resideOutsideOfPackage("com.themistra.auth.account..")` (unchanged from Phase 2).
- **D1b (Finding 2).** Generalize the rule beyond `Account` specifically: no class outside an
  entity's own module (and that module's own subpackages, e.g. `account.dto`) may depend on any
  `@Entity`-annotated class belonging to a *different* module. Verified zero existing code
  violates this broader form (Phase 3 gate investigation: the only cross-module `Account`
  mentions found anywhere are Javadoc comments already citing L12 as the reason they don't
  import it). Renamed to the named test, `shouldPreventCrossModuleEntityImports`.
- **D2 (Findings 3-5, 7-8).** New rule, adopting Kimi's proposed concrete design in full:
  - **Controller**: any class annotated `@RestController`, residing under `com.themistra.auth..`.
  - **Service**: any class annotated `@Service`, residing in a feature-module package (i.e.
    `com.themistra.auth.<module>` or a subpackage), excluding `common`.
  - **Feature modules** (for both D1b and D2's purposes): the immediate subpackages of
    `com.themistra.auth` containing domain code — `account`, `authn`, `authz`, `audit`, `token`,
    `mfa`, `apikey`, `events`, `cleanup`, `ratelimit`. `common` and `account.dto`/`account.event`
    (and equivalent same-module subpackages elsewhere) are not separate modules for either rule's
    purposes.
  - **Exceptions** (explicitly allowlisted, not silently permitted): `AccountController` →
    `SessionService` (T28); `AdminAccountController` → `LockoutService` (T14/R20, per that
    controller's own Javadoc).
  - **Excluded from the rule entirely**: `@RestControllerAdvice`/`@ControllerAdvice` classes
    (Finding 8) — confirmed `SessionExceptionHandler` (in `token`) legitimately handles exceptions
    thrown by `AccountController` (in `account`), a cross-module pattern this rule must not break.
  - **Explicit scope note** (Finding 7): this rule constrains *controller*→service dependencies
    only. Pre-existing service-to-service cross-module dependencies (`LockoutService`→
    `AccountService`, `AccountUserDetailsService`→`AccountService`) are unaffected and not treated
    as defects.
  - Each rule needs its own canary test (T32's established pattern), matching D1b's ArchRule field
    name / D2's own new field name respectively.

## Files to Modify

- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`.

## Files NOT to Modify

Unchanged from Phase 2, plus: `LockoutService.java`, `AccountUserDetailsService.java` (Finding 7's
accepted, out-of-scope service-to-service dependencies), any `@RestControllerAdvice` class.

## Acceptance Criteria

- **AC1.** `shouldPreventCrossModuleEntityImports` (broadened, D1b) fails on a deliberately
  reintroduced violation for `Account` AND for at least one other entity (e.g. `VerificationToken`),
  and passes with every module's own `.dto`/`.event` subpackages correctly exempted.
- **AC2.** The new controller→service rule (D2) fails if a controller gets a new, un-allowlisted
  cross-module service dependency, passes for the two named exceptions, and does not flag any
  `@RestControllerAdvice` class.
- **AC3.** Both rules are enforced by `mvn test` via their own canary tests (T32's pattern), not
  merely present as inert `@ArchTest` fields.

## Required Tests

- `shouldPreventCrossModuleEntityImports` (named) + canary, both directions negative-proofed.
- The new D2 rule + canary, negative-proofed for a new violation, the two exceptions, and the
  `@RestControllerAdvice` exclusion.

## Open Questions

No blockers. All 9 Phase 3 findings resolved above.

---

**Phase 4 complete — brief FROZEN.** Proceed to Phase 5 (Implementation Plan).
