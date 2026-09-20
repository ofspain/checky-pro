<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T35 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T35 — ArchUnit / module-boundary tests |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Below are adversarial findings on the Phase 2 TIB. Each finding is presented as **Issue · Severity · Evidence · Recommended brief amendment**. No redesign or implementation is proposed.

---

## Finding 1 — LOCKED-decision mapping mismatch in `package.md`

**Issue.** The brief treats both rules as enforcing L12 (module boundaries), but `spec/auth-service/package.md` §8 maps the named test `shouldPreventCrossModuleEntityImports` to **L10**. `design.md` §4a defines L10 as the "MFA enforcement role rule" and L12 as "Module boundaries." This is a spec inconsistency, not merely a brief issue.

**Severity.** High — acceptance criteria may be traced to the wrong LOCKED decision, and an auditor reading `package.md` will look for MFA behavior, not entity imports.

**Evidence.**
- `spec/auth-service/design.md` lines 14-16: L10 = MFA role rule; L12 = module boundaries.
- `spec/auth-service/package.md` line 115: `- shouldPreventCrossModuleEntityImports → L10`.
- TIB §31-33 and §57-65 describe the entity-import fix as enforcing L12.

**Recommended brief amendment.** Add an explicit note: "The rule enforces L12. The mapping `shouldPreventCrossModuleEntityImports → L10` in `package.md` §8 appears to be a typographical error and must be flagged to the spec author for correction in Phase 4; the implementation must not silently adopt the L10 mapping."

---

## Finding 2 — "Account entities" is ambiguous; the existing rule only protects `Account`

**Issue.** The task statement says "Ensure new modules do not import **account entities**" (plural), and L12 broadly forbids cross-module entity imports. The existing/fixed rule, however, guards only `com.themistra.auth.account.Account`. It does not protect `VerificationToken`, `MfaEnrollment`, `ApiKey`, or other entities living under `com.themistra.auth.account` or sibling modules.

**Severity.** Medium — the fix is correct for the stated bug but may leave reviewers believing the broader requirement is satisfied.

**Evidence.**
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` lines 37-42: rule target is `com.themistra.auth.account.Account` only.
- `services/auth/src/main/java/com/themistra/auth/account/VerificationToken.java` and other entity files exist in the same module.
- `spec/auth-service/design.md` line 16: L12 says "No feature module may import an **entity class** from another feature module" (general, not Account-specific).

**Recommended brief amendment.** Clarify the scope: "This task's D1 fixes the wildcard bug in the **Account aggregate root** rule only. The broader L12 requirement for other account-module entities (e.g., `VerificationToken`) is out of scope for T35 unless the author explicitly expands it."

---

## Finding 3 — D2 controller→service rule mechanism is deferred instead of specified

**Issue.** The brief identifies D2 as the genuinely open design question and defers the exact ArchUnit mechanism to Phase 5. For a Phase 3 challenge, this leaves the rule's shape, detection predicates, and exclusion mechanism unspecified, making the brief unfreezeable.

**Severity.** High — the single most important new behavior has no concrete design.

**Evidence.** TIB §75: "Exact ArchUnit mechanism (a shared per-controller assertion vs. one generic rule with an exclusion predicate) is a Phase 5 decision."

**Recommended brief amendment.** Commit to one concrete mechanism before freezing, for example:

> "Controllers (classes annotated with `@RestController` under `com.themistra.auth.*`) may not depend on classes annotated with `@Service` that reside in a different feature-module package. Explicit `Priority#alwaysAllow` (or equivalent predicate) exemptions are granted for `AccountController → com.themistra.auth.token.SessionService` and `AdminAccountController → com.themistra.auth.authn.LockoutService`."

Also state the canary pattern: a JUnit `@Test` method named per the spec will invoke `theNewRule.check(analyzedClasses())`.

---

## Finding 4 — "Controller" and "service" are not defined

**Issue.** The rule's effectiveness depends entirely on how it detects controllers and services. Spring controllers may be annotated with `@RestController` or `@Controller`; service-layer classes may be `@Service`, or named `*Service`, or neither. The brief does not choose.

**Severity.** Medium — an under-specified detector will either miss violations (false negatives) or flag acceptable classes (false positives).

**Evidence.**
- All current HTTP controllers use `@RestController` (`AccountController`, `AdminAccountController`, `ApiKeyController`, etc.).
- `LockoutService` and `SessionService` are annotated with `@Service` and end in `Service`; other service-like classes (e.g., `AccountUserDetailsService`, `LoginSuccessHandler`) are not in controller packages but could be caught by a name-only predicate.

**Recommended brief amendment.** Define detection explicitly, e.g.:

> "Controller: any class annotated with `@RestController` and residing in `com.themistra.auth..`. Service: any class annotated with `@Service` and residing in a feature-module package (i.e., `com.themistra.auth.<module>` or subpackages, excluding `common`)."

---

## Finding 5 — Module / package boundary model is implicit

**Issue.** The rule must know which top-level packages are "feature modules," which subpackages belong to the same module (e.g., `account.dto`, `account.event`), and whether `common` is a module. The brief assumes this is obvious but never lists it.

**Severity.** Medium — an incorrect boundary model breaks the rule or creates accidental exemptions.

**Evidence.**
- `spec/auth-service/agents.md` §Package layout: "Package-by-feature under `com.themistra.auth` ... Shared plumbing lives only in `common`."
- `spec/auth-service/design.md` line 16: "Shared plumbing lives in `common`."
- `services/auth/src/main/java/com/themistra/auth/account/dto/` and `.../account/event/` are subpackages of `account` and must be treated as inside the account module.

**Recommended brief amendment.** Add: "Feature modules are the immediate subpackages of `com.themistra.auth` that contain domain code (account, authn, authz, audit, token, mfa, apikey, events, cleanup). `com.themistra.auth.common` and `com.themistra.auth.account.dto` / `...account.event` are not separate modules for the purposes of this rule."

---

## Finding 6 — Negative-proof procedure for AC1/AC2 is not described

**Issue.** AC1 and AC2 require the rules to fail on deliberately reintroduced violations, but the brief does not say how or where to introduce those violations without leaving broken code in `main`.

**Severity.** Medium — without a reproducible procedure, the acceptance criteria are not verifiable in CI or review.

**Evidence.** TIB §79-84 (AC1/AC2): "fails on a deliberately reintroduced violation ... proven via negative-proof" but no procedure is given.

**Recommended brief amendment.** Specify a reproducible, temporary method such as:

> "For each rule, create a short-lived scratch class (or a `@Disabled` canary test) that intentionally violates the rule, run `mvn -pl services/auth test`, confirm failure, then remove the scratch class / re-disable the canary before merge. The scratch class must not be committed."

---

## Finding 7 — Out-of-scope cross-module service dependencies may confuse reviewers

**Issue.** The brief correctly excludes `AccountController → SessionService` and `AdminAccountController → LockoutService`, and it notes these are pre-existing, deliberate design decisions. However, it does not mention other pre-existing cross-module service dependencies such as `LockoutService → AccountService` and `AccountUserDetailsService → AccountService`. A reviewer might assume the new rule is meant to eliminate all such dependencies.

**Severity.** Low — scoped out in TIB §23-25, but silence invites misinterpretation.

**Evidence.**
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` line 3: imports `com.themistra.auth.account.AccountService`.
- `services/auth/src/main/java/com/themistra/auth/authn/AccountUserDetailsService.java`: depends on `AccountService`.

**Recommended brief amendment.** Add a single sentence under D2 or Scope: "Service-to-service cross-module dependencies (e.g., `LockoutService → AccountService`) are outside this task's scope and are not treated as defects; only controller-to-service dependencies are constrained."

---

## Finding 8 — `@RestControllerAdvice` / `@ControllerAdvice` classes are not addressed

**Issue.** The rule targets controllers, but the project also contains `@RestControllerAdvice` classes (`AccountExceptionHandler`, `SessionExceptionHandler`, `ApiKeyExceptionHandler`, etc.) that live in feature modules and may reference services or DTOs from other modules. It is unclear whether these are in scope.

**Severity.** Low — current advices appear to stay within their own modules, but the boundary should be explicit.

**Evidence.**
- `services/auth/src/main/java/com/themistra/auth/token/SessionExceptionHandler.java` is in the `token` module but handles exceptions thrown by `AccountController`.
- `services/auth/src/main/java/com/themistra/auth/account/AccountExceptionHandler.java` is in the `account` module.

**Recommended brief amendment.** State: "`@RestControllerAdvice` / `@ControllerAdvice` classes are excluded from the controller→service rule; this rule applies only to HTTP endpoint controllers annotated with `@RestController`."

---

## Finding 9 — ArchRule field naming and the spec's named-test mapping

**Issue.** The brief proposes renaming the existing ArchRule field to `shouldPreventCrossModuleEntityImports` to match the spec's named test. The existing file mixes snake_case (`only_the_account_module_may_touch_the_Account_entity`) with camelCase (`shouldEnforcePublicEndpointAllowlist`). The spec's named test should map to a JUnit `@Test` canary method, not necessarily to the `@ArchTest` field name.

**Severity.** Low — cosmetic, but inconsistency makes the test plan harder to trace.

**Evidence.**
- `ArchitectureTest.java` line 37 (snake_case field) vs. line 121 (camelCase field).
- `ArchitectureTest.java` lines 148-151: the canary method is named `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild` and calls the camelCase ArchRule field.

**Recommended brief amendment.** Either:
- Keep the ArchRule field in camelCase as `shouldPreventCrossModuleEntityImports` and add a JUnit canary named `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`, or
- Keep the ArchRule field in snake_case and add the camelCase canary method; explicitly note that the spec's named test maps to the **canary method**, not the ArchRule field.

---

## Summary

The brief is directionally correct but should be frozen only after:
1. Resolving the L10/L12 mapping mismatch with the spec author.
2. Explicitly defining the controller→service rule's detection predicates, module boundaries, and exclusion mechanism.
3. Adding a reproducible negative-proof procedure to the acceptance criteria.
4. Clarifying scope around other entity classes, service-to-service dependencies, and controller advice classes.

(End of Phase 3 design challenge.)
