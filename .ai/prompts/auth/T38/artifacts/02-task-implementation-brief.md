<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T38 · Phase 2 — Task Implementation Brief

## Task

Verify that five reference-project defect classes (plaintext credentials, unauthenticated admin
routes, shared model artifact, `Long.getLong` config misread, `allow-circular-references`) are
absent from `services/auth`.

## Purpose

Final-verification gate: confirm this service did not inherit any of the specific, named defects the
`gap-analysis.md` document identified in the reference project (`authrex`) it was designed to avoid
repeating.

## Scope

**In**: direct source verification of all five defect classes; documenting the evidence for each.

**Out**: fixing anything (none expected — all five are Met per Phase 0/1's direct verification);
auditing CI configuration outside `services/auth`'s own source tree (the gitleaks gate question,
carried as a non-blocking open item); any defect class not named in the task statement.

## Business Rules

None scoped (process/verification task, no requirement ID).

## Locked Decisions

- L11 — public-endpoint discipline; governs the admin-route check.
- L12 — module boundaries; governs the shared-model-artifact check.
- L13 — secrets discipline; governs the plaintext-credentials check.

## Dependencies

None new. Verification-only against existing mechanisms: `PublicEndpoints.java`,
`ArchitectureTest.shouldEnforcePublicEndpointAllowlist`, both `pom.xml` files, `application.properties`,
and every credential-shaped domain field already reviewed at Phase 0.

## Inputs

The current state of `services/auth`'s source tree and both `pom.xml` files — no new external input.

## Outputs

A documented verification record for each of the five defect classes, with a specific file/mechanism
cited as evidence per class.

## State Changes

None. Read-only verification task.

## Files to Create

None.

## Files to Modify

None expected. If Phase 3/8's adversarial review overturns any "absent" finding, a fix would be
scoped at that point — not decided here, since none is currently expected.

## Files NOT to Modify

All production source; all `spec/` files; `services/auth/docs/architecture/gap-analysis.md` (a
historical record, not something this task updates).

## Acceptance Criteria

- AC1 — Plaintext credentials absent. **Met.**
- AC2 — Unauthenticated admin routes absent. **Met.**
- AC3 — Shared model artifact absent. **Met.**
- AC4 — `Long.getLong` config misread absent. **Met.**
- AC5 — `allow-circular-references=true` absent. **Met.**

## Required Tests

None new. AC1-AC5 are each already enforced or evidenced by pre-existing mechanisms
(`@ConfigurationProperties`/`@Validated` convention, `ArchitectureTest`'s public-endpoint rule, the
absence of any shared-model dependency) — this task confirms them, it does not add new coverage.

## Constraints

None beyond the task's own read-only nature — no performance, thread-safety, transaction, or
null-handling surface is touched.

## Open Questions

No blockers. The gitleaks-CI-gate question (Phase 1) is non-blocking and carried forward for
awareness only, not resolution within this task's scope.

---

**Phase 2 complete — implementation brief written.** Proceed to Phase 3 (Design Challenge) on
approval.
