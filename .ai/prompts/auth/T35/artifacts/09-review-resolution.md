<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T35 · Phase 9 — Review Resolution

Consumes `artifacts/07-self-review.md` (1 finding) and `artifacts/08-independent-review.md` (Kimi,
10 findings — the largest single review round this task saw, and one of the largest this session).
All findings verified against actual source before disposition. femi decided the two findings with
genuine design-trade-off weight via human gate; the remaining eight are folded in directly.

## Comment resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| Self-review Finding 1 / Kimi Findings 1+8 | Asymmetric, silent fallback for classes outside `FEATURE_MODULES` | **ACCEPTED, femi's gate decision.** Fail-fast: an `@Entity` or `@RestController` class outside every listed module is itself now a violation, symmetric across both rules. | Both `ArchCondition` implementations now emit a `SimpleConditionEvent(..., false, ...)` naming the offending class instead of silently returning. Negative-proofed: a scratch `@Entity` and a scratch `@RestController`, each placed in `common`, both correctly failed their respective canary with a clear message; reverted. |
| Kimi Finding 2 | `common`-housed services would be forbidden for every controller unless individually allowlisted, contradicting `common`'s own designed purpose | **ACCEPTED, femi's gate decision.** `common` services are now always allowed, checked before the same-module/allowlist logic. | Added `isInCommonModule()` + a short-circuit in `dependOnlyOnServicesFromTheSameFeatureModuleOrAnAllowedException()`. Negative-proofed: a scratch `@Service` in `common`, depended on by `AdminRoleController`, correctly passed (not flagged); reverted. |
| Kimi Finding 3 | Allowlist keyed by brittle string literals, no compile-time check on rename | **ACCEPTED.** | `ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES` now built from `AccountController.class.getName()` etc. — a future rename of any of the 4 named classes now breaks compilation here instead of silently invalidating the allowlist. |
| Kimi Finding 4 | `FEATURE_MODULES` is a hidden maintenance dependency, no guard | **ACCEPTED, partially superseded by Findings 1/8's fix.** The fail-fast resolution already closes most of this (a new module's entity/controller added without updating the list is now caught, not silently missed) — added the comment Kimi's own "at minimum" suggestion asked for, pointing future authors to `agents.md` and explaining why. | Comment added directly above `FEATURE_MODULES`. |
| Kimi Finding 5 | Stale "9 pre-existing" count in the T32 canary's comment (verified: actually 10 now) | **ACCEPTED.** | Reworded to not restate a number at all, explicitly noting *why* (this exact staleness, caught by Kimi) — avoids the same comment going stale again the next time a rule is added. |
| Kimi Finding 6 | Only `@Service`-annotated classes detected as services; `@Component`/meta-annotations would be missed | **ACCEPTED, documented, not broadened** (matches Kimi's own primary recommendation — every real service in this codebase uses `@Service` today). | Assumption stated explicitly in the rule's own Javadoc. |
| Kimi Finding 7 | Only `@RestController` detected; plain `@Controller` would be missed | **ACCEPTED, documented, not broadened** (matches Kimi's own "(b) is acceptable if explicit" recommendation — no `@Controller` class exists in this codebase). | Assumption stated in the same Javadoc block as Finding 6. |
| Kimi Finding 9 | `package.md` maps the named test to L10, not L12 | **Accepted, no new action** — the same recurring `package.md` staleness pattern already found and logged in T32/T33/T34/T35's own Phase 0/3; not re-logged as new here. | None (already tracked). |
| Kimi Finding 10 | `analyzedClasses()` re-imports the whole package on every one of 3 canary calls | **ACCEPTED.** | Converted to a lazy-initialized static field, imported once and reused by all three canaries; documented why no synchronization is needed (JUnit Jupiter's default sequential execution, no parallel config anywhere in this project). |

## Summary

Ten of eleven Phase 8 findings applied (Finding 9 already tracked, no new action needed). Two
required genuine design decisions (fail-fast semantics; `common`'s special-cased role) and were
gated; the rest were mechanical hardening, documentation, or efficiency improvements with no real
alternative outcome to weigh. All three new/changed behaviors (entity fail-fast, controller
fail-fast, common-services-allowed) were individually negative-proofed with a real scratch class,
confirmed to produce the exact expected result, and reverted.

Verification after all changes: `mvn clean test-compile` clean; `mvn test -Dtest='ArchitectureTest'`
— **4/4 pass**. `git status` confirms only `ArchitectureTest.java` changed this phase; no other
file touched, no leftover scratch files from any of the three negative-proof runs.

---

**Phase 9 complete — resolution log written; sign-off recorded.** Proceed to Phase 10 (Test
Generation) on approval.
