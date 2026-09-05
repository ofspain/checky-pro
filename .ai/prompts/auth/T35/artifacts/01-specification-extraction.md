<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T35 · Phase 1 — Specification Extraction

## Business Rules

No R-numbered requirement applies (process/verification task, per `package.md` §7 — confirmed via
the task's own header).

## Locked Decisions

- **L12 — Module boundaries.** "No feature module may import an entity class from another feature
  module. Shared plumbing lives in `common`. This is enforced by `ArchitectureTest`." This task's
  entire subject — extending/fixing that enforcement, not introducing a new boundary rule from
  scratch.

## Files involved

**Existing file to modify:**
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` — the sole file. Contains
  the existing, buggy `only_the_account_module_may_touch_the_Account_entity` rule (Phase 0 Finding
  1) and no existing rule for "controllers depend only on their module services" (Phase 0 Finding
  2).

**No new files expected** — `design.md`'s own file tree was not found to name a new file for this
task (T35 extends the existing ArchUnit suite, matching T32's own precedent of extending rather
than replacing).

## Dependencies

ArchUnit 1.3.0 (already resolved, already used for every existing rule and T32's own
`callMethod`-based rule). No new library. Depends on understanding all 7 controllers' actual
service dependencies (Phase 0's direct import inspection) to scope the second rule correctly.

## Acceptance Criteria

- **AC1 (L12, named test).** `shouldPreventCrossModuleEntityImports` exists and correctly enforces
  "no feature module may import an entity class from another feature module" — including fixing
  the existing rule's `..`-wildcard bug so `account.dto`/`account.event` are correctly exempted
  (Phase 0 Finding 1), not just renaming or duplicating the existing rule.
- **AC2 (task's second clause).** A new rule enforces "controllers depend only on their module's
  services" — scope pending Phase 2/4 resolution of Phase 0 Finding 2's tension with
  `AccountController`→`SessionService` and `AdminAccountController`→`LockoutService`, both
  pre-existing and intentional.

## Tests required

- **`shouldPreventCrossModuleEntityImports`** (named, `package.md` §8) — maps directly onto fixing/
  renaming the existing account-entity rule. Given T32's own established precedent (ArchUnit rules
  don't execute under `mvn test` in this environment — a still-unresolved, separately-tracked
  issue), this rule will also need its own canary test to actually gate a real build, matching
  `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild`'s exact pattern.
- An unnamed second rule/test for the controller-dependency clause (AC2) — exact name TBD at
  Phase 2/5, needs its own canary too for the same reason.
- Negative-proof runs for both (established hard requirement by this point in the pipeline): the
  fixed account-entity rule must be proven to both (a) still catch a real violation, and (b) no
  longer flag `account.dto`/`account.event`'s legitimate use of `Account`.

## Open Questions

**Two genuine, non-blocking-but-must-be-resolved-at-a-gate design questions, both already
identified with full evidence at Phase 0 — restated here as this phase's own extraction, not new
findings:**

1. How to fix the account-entity rule's `..`-wildcard bug — trivial technically (add `..` to the
   package string), but worth an explicit decision given it changes an existing rule's behavior,
   not just adds a new one.
2. How to scope "controllers depend only on their module services" given two real, intentional
   exceptions already exist in shipped code. Not a blocker (Phase 2 can propose a tentative
   resolution per this pipeline's own convention), but must not be silently narrowed or silently
   ignored — Phase 0's evidence must carry forward into whatever rule Phase 2 proposes.

No `package.md` §11 item covers this task (confirmed — none of the existing Q1-Q6 entries mention
module boundaries or controller dependencies).

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
