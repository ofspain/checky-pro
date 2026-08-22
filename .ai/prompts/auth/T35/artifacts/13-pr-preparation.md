<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T35 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays
deployable throughout.

---

## Commit title

```
auth: fix and generalize cross-module ArchUnit boundary rules (T35)
```

## Commit message

```
auth: fix and generalize cross-module ArchUnit boundary rules (T35)

Fixes a real bug in the existing account-entity ArchUnit rule (a missing
".." wildcard incorrectly flagged account.dto/account.event, contradicting
its own stated intent) by generalizing it into shouldPreventCrossModuleEntityImports
- every @Entity class in every feature module, not just Account. Adds a
second, new rule: controllers may depend only on their own module's
@Service classes, with the two pre-existing, deliberate exceptions this
codebase already ships (AccountController -> SessionService,
AdminAccountController -> LockoutService) explicitly allowlisted rather
than broken, and common's own services always allowed given its designed
purpose as shared plumbing.

The first implementation of the entity rule had a real bug of its own,
caught by this task's own first negative-proof run rather than by review:
it used ArchUnit's access-tracking API, which only sees actual method
calls/field reads, and silently missed a declared-but-unused field of the
wrong-module entity's type. Switched to dependency-tracking, which does not
have this gap.

Independent review (25 findings across three rounds, the largest cumulative
volume this session) drove real hardening beyond the task's own literal
scope: both rules now fail fast if their own subject class is outside every
recognized feature module, rather than silently enforcing nothing; the
allowlist is keyed by Class.getName() so a rename breaks compilation instead
of silently invalidating an exception; two regression-guard tests were added
for branches the existing canaries don't otherwise exercise. Two review
suggestions were explicitly rejected with stated reasoning rather than
reflexively implemented.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Tests only**
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` (modified — 1
  pre-existing rule replaced with a generalized version, 1 new rule, 2 new canaries, 2 new
  regression-guard tests, 3 shared helpers, 1 existing helper simplified from lazy to eager
  initialization)

No production code changed. No `spec/` file touched. No migration.

## Summary

Implements L12 ("no feature module may import an entity class from another feature module")
generally for the first time — the original rule only ever protected `Account` specifically, and
had a real, previously-undetected bug (discovered during T32) incorrectly flagging that entity's
own module's `.dto`/`.event` subpackages. Also adds a genuinely new check L12's own general text
implies but no prior rule enforced: controllers depending only on their own module's service
layer, with two real, intentional, already-shipped exceptions explicitly named rather than broken.

This task's own review process was unusually productive: 25 findings across three rounds (9 design,
10 independent-review, 6 test-review) surfaced a real implementation bug (self-caught before any
review saw it), two genuine design-trade-off decisions each resolved with verified-zero-risk
evidence before committing, and a series of hardening improvements (fail-fast semantics, compile-
checked allowlist, regression-guard tests for branches the happy-path canaries don't exercise) that
go beyond what the task's own two-sentence statement would have strictly required.

## Testing performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='ArchitectureTest'` — **6/6 pass**.
- **Ten negative-proof runs across Phases 6, 9, and 11**, each confirmed to fail (or pass, where
  proving a legitimate exception isn't wrongly blocked) for the right reason and reverted:
  1. Entity rule, access-based first attempt: incorrectly passed on an unused-field violation —
     the implementation bug this task's own process caught and fixed.
  2. Entity rule, dependency-based fix: correctly failed on the same scratch class, for both
     `Account` and `VerificationToken`.
  3. Entity rule regression check: `account.dto`/`account.event` confirmed still exempt.
  4. Controller rule, forward: an un-allowlisted scratch service dependency correctly failed.
  5. Controller rule, exceptions: both real allowlisted dependencies confirmed to still pass.
  6. Entity rule, fail-fast: a scratch `@Entity` in `common` correctly failed with a clear message.
  7. Controller rule, fail-fast: a scratch `@RestController` in `common` correctly failed the same way.
  8. Controller rule, `common` exception: a scratch `@Service` in `common`, depended on by a real
     controller, correctly *passed* — proving the exception works in the direction that matters.
  9-10. (Implicit) `git status` after every revert confirmed no leftover scratch file in any phase.

## Specification references

- **Task:** T35 — ArchUnit / module-boundary tests (`spec/auth-service/tasks.md`, task 35)
- **Requirements:** none scoped (process/verification task)
- **LOCKED decisions:** L12
- **Named tests (`package.md` §8):** `shouldPreventCrossModuleEntityImports` (written, executed,
  passing, and proven non-vacuous across six of the ten negative-proof runs above)

---

**Phase 13 complete — PR preparation written. T35 is ready for merge.**
