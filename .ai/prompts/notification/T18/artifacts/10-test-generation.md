# notification · T18 · Phase 10 — Test Generation

No new tests were written for this task. The frozen brief (Phase 4, Required Tests) explicitly
states none are required: this is an infrastructure task whose verification is a real `mvn verify`
and a real `docker build`, both already executed in Phases 6 and 9.

## Audit

Checked for a genuine coverage gap, the kind earlier phases closed for code tasks:

- **Drift between the root `pom.xml` `<modules>` list and the Dockerfile's copied sibling poms** —
  the one real hazard Phase 7 identified. Verified against the current tree: the two lists match
  exactly (`services/auth`, `services/crypto`, `services/notification`). No live defect today.
- That hazard has **no automated guard**, so a fourth module added later would break `docker build`
  silently until someone ran it.

## Decision

Not closed in this phase. A guard test (asserting the Dockerfile copies every root module's pom)
would be a real improvement, but it is new scope beyond the frozen brief, and the brief deliberately
scoped this task to today's exact tree. It is recorded as the concrete follow-up alongside the
auth/crypto Dockerfile fix, where the two share one root cause and should be addressed together.

## Verification

Unchanged from Phase 9: `mvn -pl services/notification clean verify` — 369 tests, 0 failures,
0 errors; `docker build` exit 0. No code or test files were modified in this phase.
