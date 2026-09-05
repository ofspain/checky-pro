<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T35 · Phase 7 — Self Review

Reviewed the full diff (`ArchitectureTest.java`) against the frozen brief and `agents.md`. Thread
-safety, transaction boundaries, money types, and idempotency don't apply to build-time static
analysis — N/A rather than silently skipped.

## Finding 1 — Both rules silently enforce nothing for a class outside the 10 listed feature modules

**Severity:** Medium

**Evidence:** `featureModuleOf()` returns `null` for any class not under one of the 10
`FEATURE_MODULES` packages. Both `onlyBeAccessedFromTheSameFeatureModule()` (line 92: `if
(entityModule == null) { return; }`) and `dependOnlyOnServicesFromTheSameFeatureModuleOrAnAllowedException()`
(line 141: `controllerModule != null && ...`) treat an unrecognized module as "nothing to enforce"
(the entity rule) or "no dependency can ever be considered same-module" (the controller rule —
which would actually make such a controller need every dependency explicitly allowlisted, the
opposite failure mode). Verified via direct inspection that **every current `@Entity` class is
inside one of the 10 listed modules** (`grep -rl "@Entity"` across the whole codebase: account,
apikey, audit, authn, authz, events, mfa, token — no misses), and all 7 controllers are likewise
inside recognized modules — so this is a currently-dormant boundary condition, not a live gap,
matching the same class of finding T32's self-review flagged for a bare `@RequestMapping` handler.

**Recommendation:** Not fixing now — no class in this codebase triggers it today, and `common` is
deliberately excluded by design (per L12/`agents.md`'s own "shared plumbing lives in `common`"
framing). Flagging so a future entity or controller added to a genuinely new top-level package
(not yet in `FEATURE_MODULES`) doesn't get a false sense that these two rules would catch a
violation involving it — they wouldn't, until `FEATURE_MODULES` is updated too.

## Correctness — verified, not merely inspected

- Both rules were proven non-vacuous via real negative-proof runs during Phase 6, including one
  genuine implementation bug (access-based vs. dependency-based tracking) caught and fixed by the
  very first negative-proof, not by inspection.
- Confirmed `account.dto`/`account.event`'s legitimate use of `Account` remains exempt under the
  new dependency-based mechanism, not just the old simpler one (re-verified after the D1b fix, not
  assumed carried over).
- Confirmed both real, allowlisted controller→service exceptions still pass after D2's
  implementation (the unmodified codebase itself is the proof — no scratch step needed for this
  half).
- Confirmed `@RestControllerAdvice` classes are excluded from D2 *structurally* (verified via
  bytecode that the annotation is never `@RestController`), not merely by observing the rule
  happens to pass on the current advice classes — a stronger guarantee than "no current advice
  class trips it."

## Boundary conditions considered, none additionally found lacking

- A hypothetical future service-layer helper class *inside* a controller's own module, given the
  same name pattern as another module's service, is still correctly scoped by module-of-package,
  not by class name — no naming-collision risk.
- `getDirectDependenciesToSelf()`/`getDirectDependenciesFromSelf()` are both *direct* (not
  transitive) — matches every other existing rule in this file's own established scope (none of
  the pre-existing 9 rules check transitive dependencies either), so this isn't a new inconsistency
  T35 introduces.

## No findings on

Null-safety (the one real null-handling case, `featureModuleOf` returning `null`, is exactly
Finding 1's own subject, already covered); readability (both new `ArchCondition` implementations
have clear, specific `.because(...)` text and inline comments explaining the access-vs-dependency
distinction); module boundaries (this task's own subject, extensively verified); complexity (each
new condition is a single, flat loop with no nested branching beyond the necessary module
comparison).

---

**Phase 7 complete — self-review written.** Proceed to Phase 8 (Kimi independent review) on approval.
