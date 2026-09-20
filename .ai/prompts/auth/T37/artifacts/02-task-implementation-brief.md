<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T37 · Phase 2 — Task Implementation Brief

## Task

Bring `mvn -pl services/auth verify` to a passing state and confirm the Docker image builds from
the repo root.

## Purpose

Final-verification gate: confirm the whole suite this pipeline has built across T01-T36 actually
passes together, and the deployable artifact actually builds — not per-task in isolation.

## Scope

**In**: fixing the one group of failures with a known, cheap, already-established fix pattern
(Group C — FK-violation in `AuditTrailIntegrationTest`/`RoleAssignmentIntegrationTest`); confirming
and documenting the Docker image build (already done, Phase 0).

**Out** (pending Phase 4): Group A (Kafka producer→broker environment connectivity — no known
code-level fix, already logged/deferred at T36's own gate) and Group B (null-response flakiness
under full-suite load — no confirmed root cause). Neither is silently dropped; both are carried to
Phase 4 for an explicit scope decision.

## Business Rules

- R43 — every security-relevant action appends an `auth_audit` row + `auth.security.audit` mirror.
  Group C's fix restores the two tests that actually exercise this.

## Locked Decisions

None directly scoped. L1 (immutable migrations) and L12 (module boundaries) bound any Group C fix:
no schema change, no new cross-module dependency, change confined to the two affected test files.

## Dependencies

`AccountService.register`/`.activateEmail` (Group C fix source); the `registerAndActivate` pattern
already established in `SessionIntegrationTest`/`CleanupIntegrationTest`/every newer integration
test in this module; Docker + Maven (already confirmed working for the image-build half).

## Inputs

The current state of `services/auth`'s full test suite and `Dockerfile` — no new external input.

## Outputs

A passing `mvn -pl services/auth verify` (or, if Groups A/B are gated out of scope, a passing run
modulo the explicitly-deferred failures) and a confirmed Docker image build.

## State Changes

None to production runtime behavior or schema. Test-file-only changes, if Group C is authorized.

## Files to Create

None.

## Files to Modify

- `services/auth/src/test/java/com/themistra/auth/audit/AuditTrailIntegrationTest.java` (Group C,
  pending Phase 4 authorization)
- `services/auth/src/test/java/com/themistra/auth/authz/RoleAssignmentIntegrationTest.java` (Group C,
  pending Phase 4 authorization)

## Files NOT to Modify

All production source; all `spec/` files; `services/auth/Dockerfile` (already confirmed working, no
change needed); every test file outside Groups A/B/C (already passing).

## Acceptance Criteria

- AC1 — `mvn -pl services/auth verify` passes with zero failures/errors, OR passes with only the
  Phase-4-authorized deferrals explicitly documented (not silently tolerated).
- AC2 — Docker image builds from the repo root via `services/auth/Dockerfile`. **Already met**
  (Phase 0, exit code 0).

## Required Tests

None new. If Group C is authorized, the fix is applied to the two existing failing test files using
their own already-established `registerAndActivate` pattern — not new test authorship.

## Constraints

- **No production code change** unless a genuine defect is found in Phase 6 that Groups A/B/C don't
  already explain (not expected, but not to be ruled out before actually running the fix).
- **Module boundaries**: any Group C fix stays inside the `audit`/`authz` test files themselves.
- **Determinism**: the Group C fix must not introduce timing dependencies — `registerAndActivate` is
  synchronous, matching the pattern it's copied from.

## Open Questions

**Blocker.** Whether Groups A (Kafka environment connectivity, no known code fix) and B (unconfirmed
full-suite-only flakiness) are in scope for this task to actually resolve, versus formally deferred
with the human's explicit sign-off (matching the disposition T36's own Phase 6 gate already gave
Group A specifically). This determines whether AC1 can be literally satisfied by this task at all, or
only satisfied modulo documented, gate-approved exceptions. Requires a Phase 4 human-gate decision.

---

**Phase 2 complete — implementation brief written.** Proceed to Phase 3 (Design Challenge) on
approval.
