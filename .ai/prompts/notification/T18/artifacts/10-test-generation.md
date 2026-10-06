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

## Addendum (post Phase 11) — 3 gaps raised, all verified, no code change

Kimi's Phase 11 review raised 3 gaps. Each was checked directly before disposition.

- **Gap 1** (Maven and Docker unavailable in Kimi's sandbox) — **re-confirmed with fresh runs, not a
  real gap**: `mvn -pl services/notification clean verify` exit 0, 369 tests, 0 failures, 0 errors,
  run again this phase; `docker build` exit 0 in Phase 9. Kimi's sandbox limitation is not evidence
  the claims were never verified.
- **Gap 2** (no automated guard against root-pom / Dockerfile module drift) — **already disclosed and
  deferred** in this phase's own main text, with the same reasoning. Kimi's suggested follow-up is the
  same one already recorded. No change.
- **Gap 3** (no automated test of runtime image properties) — **verified directly**: `docker export`
  of the built image lists only `app/app.jar` under `/app` and `usr/bin/java`; `docker inspect` shows
  `User=nonroot` and entrypoint `java -XX:MaxRAMPercentage=75.0 -jar app.jar`. Kimi's claim matches
  the image exactly. Container-level tests would need CI container tooling, out of this task's
  scope, as Kimi itself notes.

**Verification:** `mvn -pl services/notification clean verify` — 369 tests, 0 failures, 0 errors,
exit 0 (unchanged from this phase's main text; no code was modified).
