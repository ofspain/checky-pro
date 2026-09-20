# auth · T01 · Phase 10 — Test Generation (Test Manifest)

Consumes `artifacts/09-review-resolution.md`. No test file created, no production code changed.

## No new test authored — this is a frozen-brief decision, not an oversight

The frozen brief (Phase 4) explicitly settled this: **Required Tests: "None named in `package.md` §8
map to this task... this task adds no tests of its own."** `Files to Create` and `Files to Modify`
in the frozen brief list only the migration SQL and `pom.xml`'s plugin block — no test file. Phase 3's
Finding 2 ("verification method is manual/untestable") was already resolved at Phase 4 by adding
concrete SQL introspection steps to AC4, not by authoring an automated test. Per the guardrails,
downstream phases (this one included) may not renegotiate the frozen brief, so no test file is
introduced here even though Phase 10's generic instruction says "generate tests" — there is nothing
in this task's scope for that instruction to act on.

## Acceptance criteria → verification mapping

| AC | Requirement | Verification method | Status |
|---|---|---|---|
| AC1 | R40-supporting: `shedlock` table exists with the ShedLock default schema | Manual SQL introspection (`information_schema.tables`), executed in Phase 6, re-confirmed unaffected by Phase 9's changes | **Verified** (Phase 6 notes) |
| AC2 | R17-supporting: `idx_lockout_state_locked_until` partial index exists | Manual SQL introspection (`pg_indexes`), executed in Phase 6 | **Verified** (Phase 6 notes) |
| AC3 | L1: only `V5` is new, `V1`–`V4` unchanged | `git status`/`git diff`, executed in Phase 6 and re-confirmed in Phase 9 | **Verified** |
| AC4 | Task statement: `mvn -pl services/auth flyway:migrate` runs cleanly | Full command executed against live Testcontainers-free local Postgres (Docker Compose), `BUILD SUCCESS`, `flyway_schema_history` row confirmed for v5 | **Verified** (Phase 6 notes) |

No requirement/LOCKED-decision ID in this task's scope (`R17`, `R40`, `L1`) maps to a named §8 test —
confirmed at Phase 1 and unchanged since.

## Existing indirect coverage (not new, not authored here — noted for completeness)

Every existing `@SpringBootTest` integration test in `services/auth` that uses
`TestcontainersConfiguration` (real Postgres via Testcontainers) — e.g.
`AccountPersistenceIntegrationTest`, `AuditTrailIntegrationTest`, `RefreshTokenFamilyIntegrationTest`,
`RoleAssignmentIntegrationTest` — boots a Spring context that runs Flyway's autoconfigured migration
on startup, which now includes `V5`. A syntactically invalid or checksum-mismatched `V5` file would
fail every one of those tests at context-startup, not just a dedicated V5 test. This is pre-existing
suite behavior, not something authored in this phase, and it is why `mvn -pl services/auth verify`
(once the pre-existing, unrelated `token`-module compile error is separately fixed) would also
exercise V5 automatically.

## Open Questions

None. No blockers.
