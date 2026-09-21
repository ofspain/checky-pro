# crypto · T29 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation. This is the final task in
`spec/crypto-service/tasks.md`'s own ordered list.

## Commit title

```
Bump crypto-service spec to READY FOR IMPL, version 0.2 (T29)
```

## Commit message

```
Bump crypto-service spec to READY FOR IMPL, version 0.2 (T29)

Closes the final task in this spec's own tasks.md: package.md 0.11's
Q1, Q2, Q3, Q7 each carry an explicit, code-cited resolution of their
engineering half, with the real vendor/procurement decisions they
depend on explicitly deferred rather than silently marked closed.
Section 9's 14-item verification checklist is ticked against real,
fresh evidence - 13 of 14 items pass, each with a typed citation
(unit test, ArchUnit, contract test, code review, or CI process).

Item 13 (mvn verify / Docker image builds) stays honestly unchecked.
Investigating it surfaced two real facts, both only discoverable
because Docker became available in this environment for the first
time this entire session, during T28: several pre-existing,
unrelated integration-test failures across four repository-test
classes, and a genuine Maven reactor defect - the root pom.xml
declares both services/auth and services/crypto as modules, but
neither service's own Dockerfile copies the sibling's pom.xml into
its build context, so docker build fails validation before any real
compilation starts. This affects auth-service's own, already-shipped
Dockerfile identically, not just crypto's - confirmed by direct
comparison, and squarely outside this task's own package.md-only
scope to fix.

This tension - bumping the header while one real checklist item
fails - was presented directly rather than resolved unilaterally.
The chosen path: disclose fully, regression-guard the disclosure so
it can't be silently dropped or the checkbox silently flipped, and
track the Dockerfile fix as a new, real follow-up task rather than
block the whole spec's readiness on a cross-service packaging defect
unrelated to its own requirements.

Three new regression-guard tests in T01SkeletonRegressionTest close
the loop: the header bump itself, Q1/Q2/Q3/Q7's own resolution notes
(and that Q4/Q5/Q6 don't falsely claim one), and item 13's own honest
disclosure - both its prose and its checkbox, closing a gap the first
version of that guard left open.
```

## Testing performed

- `mvn -pl services/crypto -am test -Dtest=T01SkeletonRegressionTest` — 10/10 passing, covering every
  assertion this task's own edits touch, re-run after each incremental change across Phases 6, 7, 9, 11.
- `mvn -pl services/crypto -am verify` (full module, run fresh at every phase from 6 through 12) — final,
  stable result: 763 tests, 6 failures, 4 errors, all in pre-existing, unrelated defects, none introduced
  by this task.
- `mvn -pl services/crypto -am test -Dtest=KmsSignerLocalStackIntegrationTest` — directly re-confirmed
  passing (Docker available) rather than assumed, closing Q7's own end-to-end verification claim with
  real evidence.
- Direct source verification throughout: `ProviderProperties`, `FailClosedScreeningClient`,
  `AttestationService`, `KmsSigner.SIGNING_ALGORITHM`, `spec/auth-service/package.md`'s own identical
  `<name>`/`TBD` convention, and `ARCHITECTURE.md`'s own "Phase 1" launch-scope naming were all read and
  confirmed directly, not assumed.
- Full traceability against Phase 1's AC1/AC2/AC3: `artifacts/12-specification-verification.md` —
  verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 29 ("Bump spec status") — the last task in the file.
- **Requirements:** none individually numbered — a process/verification gate, not a functional behavior.
- **LOCKED decisions:** L1–L15, all indirectly gated via §9's checklist; none modified.
- **Document updated:** `spec/crypto-service/package.md` — header, §9, §11 only. This is the one task in
  the entire 29-task package whose own scope requires modifying a file under `spec/` — the standing
  "never modify spec/" guardrail every other task followed exists precisely so this one, deliberate
  exception stands out rather than blending in.
- **Flagged, not fixed, for future tasks:**
  - `services/crypto/Dockerfile` and `services/auth/Dockerfile`'s shared Maven reactor defect —
    highest-priority candidate, directly blocking §9 item 13's own closure.
  - `EndToEndIntegrationTest`'s Spring-context/Flyway defect and 6 further unrelated repository-test
    failures, both carried forward unchanged from T28.
  - Q1/Q2's own remaining vendor-selection halves (procurement, not engineering).
  - Q7's own remaining half: make the KMS signing algorithm configurable rather than a code constant.

## Closing note

This completes all 29 tasks in `spec/crypto-service/tasks.md`. The spec is now `READY FOR IMPL`,
version `0.2` — a formal declaration made honest by disclosure rather than by silence: every LOCKED
decision, every named test, and every real, discovered gap along the way (the recurring branch-poisoning
incidents, the T26 `EndToEndIntegrationTest` defect, the shared Dockerfile reactor bug) is recorded in
this pipeline's own artifacts, not glossed over to reach this line.
