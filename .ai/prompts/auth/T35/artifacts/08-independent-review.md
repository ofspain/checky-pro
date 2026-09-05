<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T35 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T35 — ArchUnit / module-boundary tests |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Reviewed the completed implementation in `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` and the Phase 7 self-review with fresh eyes. Findings only.

---

## Finding 1 — Classes outside `FEATURE_MODULES` are handled inconsistently between the two rules

**Issue.** `featureModuleOf()` returns `null` for any class not under the 10 listed modules. The entity rule then silently returns (no enforcement), while the controller rule treats `controllerModule == null` as "not same module," so a controller in an unrecognized top-level package would need every service dependency explicitly allowlisted. The two rules have opposite fallback semantics for the same boundary condition.

**Evidence.**
- `ArchitectureTest.java` lines 91-94: entity rule short-circuits on `entityModule == null`.
- `ArchitectureTest.java` lines 134, 140-141: controller rule computes `sameModule = controllerModule != null && controllerModule.equals(serviceModule)`; if `controllerModule` is null, `sameModule` is false and only the allowlist can satisfy.
- Phase 7 self-review Finding 1 documents this but accepts it as dormant.

**Recommendation.** Make the fallback behavior explicit and symmetric. Either (a) document that classes outside the listed feature modules are intentionally not constrained by either rule, and change the controller rule to short-circuit when `controllerModule == null`, or (b) fail fast / assert that every `@Entity` and `@RestController` class must reside in a recognized feature module so the boundary model cannot drift silently. If (a), add a comment in the controller rule explaining the short-circuit; if (b), add an explicit ArchUnit assertion.

**Confidence.** High.

---

## Finding 2 — Controllers in feature modules cannot depend on services in `common`

**Issue.** Because `common` is not in `FEATURE_MODULES`, `serviceModule` is `null` for any `@Service` class under `com.themistra.auth.common`. The controller rule's `sameModule` check is therefore false, and such a dependency would be forbidden unless allowlisted. There are no `@Service` classes in `common` today, but the codebase's own standing rule says "shared plumbing lives only in `common`" — if that ever includes a shared service, controllers would be blocked from using it without editing the allowlist.

**Evidence.**
- `ArchitectureTest.java` lines 48-50: `FEATURE_MODULES` excludes `common`.
- `ArchitectureTest.java` lines 140-145: controller rule only allows same-module or allowlisted service dependencies.
- `services/auth/src/main/java/com/themistra/auth/common/` contains no `@Service` classes today (confirmed via grep).

**Recommendation.** Explicitly state the intended boundary for `common` in the rule's `because` text or a comment: either `common` services are allowed to any controller (treat `common` as a shared module), or they are forbidden and must be explicitly allowlisted. Do not leave the implicit behavior to chance.

**Confidence.** High.

---

## Finding 3 — Allowlist strings are brittle and not compile-time checked

**Issue.** `ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES` is keyed by fully-qualified class names as plain strings. A future rename of `AccountController`, `AdminAccountController`, `SessionService`, or `LockoutService` will silently invalidate the allowlist, causing the canary to fail with a cross-module violation that looks like a new bug rather than a stale exception.

**Evidence.**
- `ArchitectureTest.java` lines 55-57: allowlist entries are string literals.
- `services/auth/src/main/java/com/themistra/auth/account/AccountController.java` and related classes are currently named exactly as in the strings, but nothing enforces this.

**Recommendation.** Add a lightweight compile-time guard: reference the classes via `Class.getName()` literals (e.g., `AccountController.class.getName() + "->" + SessionService.class.getName()`) so a rename breaks compilation at the call site. If importing the production classes into the test is undesirable, add a short JUnit test that asserts each half of every allowlist entry resolves to an existing class.

**Confidence.** High.

---

## Finding 4 — Hardcoded `FEATURE_MODULES` list is a hidden maintenance dependency

**Issue.** The list of feature modules is duplicated as a hardcoded constant. Adding a new top-level package (e.g., `com.themistra.auth.billing`) requires remembering to update this test; otherwise the new module's entities and controllers are silently outside both rules' coverage.

**Evidence.**
- `ArchitectureTest.java` lines 48-50.
- Phase 7 self-review Finding 1 already flags this as dormant but does not propose a guard.

**Recommendation.** At minimum, add a prominent comment near the constant pointing to `spec/auth-service/design.md` §Package layout and instructing future authors to update `FEATURE_MODULES` when adding a module. A stronger fix (out of scope here) would dynamically discover feature modules from the package tree or fail the test if an `@Entity`/`@RestController` class is found outside the known set.

**Confidence.** High.

---

## Finding 5 — Stale comment on the T32 canary still says "9 pre-existing" rules

**Issue.** The comment for `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild` states that the other `@ArchTest` rules remain un-gated and refers to "the other 9 pre-existing {@code @ArchTest} rules." T35 added 2 new `@ArchTest` rules, so there are now 10 other `@ArchTest` rules, not 9.

**Evidence.**
- `ArchitectureTest.java` lines 251-253: "the other 9 pre-existing {@code @ArchTest} rules in this file remain un-gated."
- Count of `@ArchTest` fields in the current file: 11 total; excluding the public-endpoint rule itself, 10 others.

**Recommendation.** Update the comment to reflect the current count ("the other 10 pre-existing {@code @ArchTest} rules"). This is purely a documentation hygiene issue.

**Confidence.** High.

---

## Finding 6 — Rule only detects services annotated directly with `@Service`

**Issue.** `controllersDependOnlyOnTheirOwnModuleServices` uses `targetClass.isAnnotatedWith(Service.class)`. Service-layer classes that are Spring components via `@Component`, `@Bean`, or custom meta-annotations are not recognized as services and could be imported from another module without triggering the rule. All current service classes appear to use `@Service`, so this is a latent assumption, not a live gap.

**Evidence.**
- `ArchitectureTest.java` line 137: `if (!targetClass.isAnnotatedWith(Service.class)) { return; }`.
- Current service classes (`AccountService`, `SessionService`, `LockoutService`, `AuditService`, etc.) are all annotated with `@Service`.

**Recommendation.** Document the assumption in the rule's comment: "Service-layer classes are identified by the `@Service` annotation; controllers must not use other Spring stereotype annotations for cross-module service collaborators." Alternatively, broaden detection to include `@Component` and meta-annotated stereotypes if the project allows them.

**Confidence.** Medium.

---

## Finding 7 — Rule misses plain `@Controller` classes

**Issue.** The controller rule selects classes with `areAnnotatedWith(RestController.class)`. If a future controller uses Spring's plain `@Controller` annotation (e.g., for Thymeleaf or redirect endpoints), it would not be constrained by the rule. The current codebase has no `@Controller` classes, so this is latent.

**Evidence.**
- `ArchitectureTest.java` line 120: `.that().areAnnotatedWith(RestController.class)`.
- Grep for `@Controller` (excluding `@RestController` and `@RestControllerAdvice`) in `services/auth/src/main/java` returned no matches.

**Recommendation.** Either (a) broaden the selector to include `@Controller` and `@RestController`, or (b) document that all HTTP controllers in this service must use `@RestController` and rely on a separate lint/rule to enforce that. Given the project currently uses only `@RestController`, (b) is acceptable if explicit.

**Confidence.** Medium.

---

## Finding 8 — Entity rule silently skips `@Entity` classes in `common` or unrecognized packages

**Issue.** The generalized entity rule is a strong improvement, but if an `@Entity` class is ever placed in `common` or a new top-level package not yet in `FEATURE_MODULES`, `featureModuleOf()` returns `null` and the rule short-circuits. Other modules could then import that entity without failing the build.

**Evidence.**
- `ArchitectureTest.java` lines 91-94: `if (entityModule == null) { return; }`.
- No `@Entity` classes exist outside the listed modules today, but the rule's design does not enforce this invariant.

**Recommendation.** Add a fail-fast assertion: if a class is annotated with `@Entity` and `featureModuleOf()` returns `null`, add a violation event instead of silently returning. This converts the dormant boundary condition into an explicit invariant.

**Confidence.** High.

---

## Finding 9 — Named test / LOCKED-decision mapping inconsistency in `package.md` persists

**Issue.** The implementation correctly enforces L12 (module boundaries) and references L12 in its `because` text. However, `spec/auth-service/package.md` §8 still maps `shouldPreventCrossModuleEntityImports` to **L10** (MFA enforcement role rule per `design.md` §4a). The implementation does not deviate from the correct LOCKED decision, but the spec artifact is inconsistent.

**Evidence.**
- `ArchitectureTest.java` lines 84-85: rule rationale cites L12.
- `spec/auth-service/design.md` lines 14-16: L10 = MFA role rule; L12 = module boundaries.
- `spec/auth-service/package.md` line 115: `shouldPreventCrossModuleEntityImports → L10`.

**Recommendation.** Do not modify `spec/` files. Log an open question / follow-up for the spec author to correct the mapping in `package.md` §8 from L10 to L12, so future traceability is accurate.

**Confidence.** High.

---

## Finding 10 — `analyzedClasses()` is re-invoked per canary, importing the world three times

**Issue.** Each of the three canary tests calls `analyzedClasses()`, which re-imports all classes under `com.themistra.auth` with a new `ClassFileImporter`. For this service the cost is small, but it is unnecessary and scales linearly with the number of canaries.

**Evidence.**
- `ArchitectureTest.java` lines 256-258, 274-276, 280-282: each canary calls `analyzedClasses()`.
- `ArchitectureTest.java` lines 263-267: `analyzedClasses()` creates a fresh importer and imports on every invocation.

**Recommendation.** Cache the `JavaClasses` result in a static field initialized once per test class (e.g., via `@BeforeAll` or a lazy static holder), or have all canaries share a single import. Verify that the cached instance is still used by `DoNotIncludeTests` and matches the `@AnalyzeClasses` configuration.

**Confidence.** High.

---

## Summary

The implementation is directionally correct and the negative-proof discipline caught real defects. The highest-value fixes before merge are:
- Resolve the inconsistent `FEATURE_MODULES` fallback behavior (Findings 1 and 8).
- Decide and document the intended treatment of `common` services (Finding 2).
- Add compile-time or test-time validation for the allowlist strings (Finding 3).
- Fix the stale "9 pre-existing rules" comment (Finding 5).
- File the spec follow-up for the L10/L12 mapping mismatch (Finding 9).

No logic bug was found that would cause a false negative against the current codebase.

(End of Phase 8 independent review.)
