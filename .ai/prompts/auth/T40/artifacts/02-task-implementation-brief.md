<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T40 · Phase 2 — Task Implementation Brief

## Task

Bump `spec/auth-service/package.md`'s Status from `DRAFT` to `READY FOR IMPL` and Version from `0.1`
to `0.2` — conditional on §11's questions being closed and the test suite passing.

## Purpose

Final gate of the entire spec-driven pipeline: mark the auth-service spec as implementation-complete
once its own stated preconditions are genuinely satisfied.

## Scope

**In**: verifying (not assuming) both preconditions; fixing what is cheaply fixable within this
task's own reasonable scope (Q2's marking); presenting the genuine judgment calls found at Phase 0/1
for an explicit human decision; performing the actual header bump once that decision is made.

**Out**: building new production features to close Q3/Q4 (out of a "bump status" task's proportion);
fixing Groups A/B (already separately logged, out of scope per T36/T37/T38's own precedent);
answering Q4 definitively if it turns out to require `spec/notification-service/` visibility this
task doesn't have.

## Business Rules

R43 (indirectly, via the Q5/lock-audit gap finding).

## Locked Decisions

None scoped.

## Dependencies

None new.

## Inputs

Current `package.md` header/§11 state; current `mvn -pl services/auth verify` outcome; current
source state for Q2-Q5's underlying implementation questions.

## Outputs

Either (a) `package.md`'s Status/Version bumped, with §11 updated to accurately reflect every
question's real status (resolved, resolved-with-caveat, or explicitly-accepted-open), or (b) a clear
record of why the bump is deferred, pending further work — decided at Phase 4, not assumed here.

## State Changes

`spec/auth-service/package.md`'s header and §11 text (the one sanctioned spec-file edit in this
entire task sequence). No production code, no schema, no runtime behavior change from this task
itself (a possible R43 audit-gap fix is a separate scope question, not assumed in-scope here).

## Files to Create

None.

## Files to Modify

- `spec/auth-service/package.md`

## Files NOT to Modify

Every other `spec/auth-service/` file; all production/test source (pending Phase 4's scope decision
on the R43 gap — not assumed in scope by default).

## Acceptance Criteria

- AC1 — §11's six questions each carry an accurate status: Q1/Q6 already correct; Q2 corrected to
  resolved (citing D-026); Q3/Q4/Q5 each explicitly resolved, marked-open-and-accepted, or deferred,
  per Phase 4's decision — never left silently inaccurate.
- AC2 — the test-suite precondition is addressed explicitly: either genuinely passing, or an
  explicit, human-approved acceptance of the already-logged Groups A/B as a named exception.
- AC3 — `package.md`'s Status/Version are bumped only after AC1/AC2 are genuinely satisfied or
  explicitly, knowingly waived by the human gate — never bumped by default or silently.

## Required Tests

None.

## Constraints

- **Honesty over completeness**: an inaccurate "all clear" is worse than an accurate "here's what's
  still open, and here's why we're proceeding anyway" — matching this session's standing practice
  (T39's O3 precedent).
- **Scope discipline**: do not silently expand into fixing the R43 audit gap or building Q3/Q4
  features without an explicit Phase 4 decision to do so.

## Open Questions

**Blockers, both requiring Phase 4 human-gate decisions:**
1. How to resolve/record Q3, Q4, Q5 in §11 (each has a different right answer — see Phase 1).
2. Whether to bump the spec status despite `mvn verify` not literally passing, given Groups A/B are
   already-logged, out-of-code-scope environmental issues — matching how T37 itself was already
   accepted as PASS under an explicit AC1a/AC1b framing.

---

**Phase 2 complete — implementation brief written.** Proceed to Phase 3 (Design Challenge) on
approval.
