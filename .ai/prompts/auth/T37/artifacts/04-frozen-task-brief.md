<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T37 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

## Decision packet

All 10 Phase 3 (Kimi) findings verified before disposition.

| # | Finding | Disposition |
|---|---|---|
| 1 | Phase 2 TIB missing | **Stale — no longer true.** Kimi's review consumed only Phase 1 (ran before Phase 2 was written); `artifacts/02-task-implementation-brief.md` exists and is the frozen basis here. |
| 2 | AC1 may be impossible if Group A is environmental | Accepted. AC1 split into AC1a (Group C code-level fixes, suite green modulo Groups A/B) and AC1b (Groups A/B documented + human-approved as deferred), below. |
| 3 | Group B root cause speculative | Accepted — scoped out of T37 entirely (not a time-boxed investigation). No confirmed root cause exists; chasing it risks unbounded work on a task whose own literal scope is "run the suite," not "root-cause every flake." |
| 4 | Group C fix changes test semantics (masks a validation question) | Accepted, resolved via human gate: **fix the tests, log the validation gap, do not add production code.** See Finding 5's verification below — real, but out of scope. |
| 5 | Unclear whether FK violations are test bugs or service bugs | **Verified, real.** Neither `AuditService.record` nor `RoleService.assignRole` validates `accountUuid` existence before touching the DB — both rely entirely on the real Postgres FK (`auth_audit_account_uuid_fkey`, `account_role_assignment`'s FK, both `REFERENCES accounts(account_uuid)`). Confirmed no existing `DataIntegrityViolationException` handling covers this specific FK (`RoleService`'s own two existing catches are for a *different* constraint — duplicate role/template names). Production impact: `POST /admin/accounts/{typo'd-uuid}/roles/MERCHANT` currently yields an opaque 500 (via `ApiExceptionHandler.onUnexpected`, no info leak — RFC 9457 fail-safe holds) rather than a clean 404. **Human-gate decision: log as an explicit out-of-scope follow-up, do not fix in T37** — adding validation is production-behavior scope creep for a task whose statement is purely "make the suite pass." |
| 6 | Docker build command underspecified | Accepted. Exact command recorded below. |
| 7 | Cross-module import in Group C's test fix | Accepted as a documented note — confirmed test code is excluded from `ArchitectureTest` analysis (`ImportOption.DoNotIncludeTests`, verified this session during T36 Phase 7); no boundary violation. |
| 8 | No-rollback DB growth | Accepted as a documented note — matches every sibling integration test's existing convention; not a new risk introduced by this fix. |
| 9 | Group A fix may need changes outside `services/auth` | Accepted — already correctly scoped Out in Phase 1/2; reinforced explicitly below. |
| 10 | No definition of "pass" under partial environmental failure | Accepted. Explicit success definition written below. |

## Frozen brief (Phase 2 TIB, as amended)

### Task

Bring `mvn -pl services/auth verify` to a passing state for every code-level failure, and confirm
the Docker image builds from the repo root.

### Purpose

Final-verification gate: confirm the whole suite this pipeline has built across T01-T36 actually
passes together, and the deployable artifact actually builds.

### Scope

**In**: Group C fix (`AuditTrailIntegrationTest`, `RoleAssignmentIntegrationTest`) via the established
`registerAndActivate` pattern; confirming/recording the Docker image build (already done, Phase 0).

**Out**: Group A (Kafka producer→broker environment connectivity — no known code-level fix, already
logged/deferred at T36's own gate; any fix would likely touch Docker/host networking or
`TestcontainersConfiguration`/CI config, outside `services/auth`'s own application code per Kimi
Finding 9); Group B (unconfirmed flakiness, scoped out entirely per Kimi Finding 3 — not even a
time-boxed investigation); adding account-existence validation to `AuditService`/`RoleService`
(Kimi Finding 5, human-gated out — logged as a follow-up, not fixed here).

### Business Rules

R43 — audit-append behavior; Group C's fix restores the two tests that exercise it.

### Locked Decisions

L1 (immutable migrations — no schema change needed or made), L12 (module boundaries — fix confined
to the two test files; cross-module import from `account` into `audit`/`authz` test code is
acceptable per Kimi Finding 7, since test code is excluded from `ArchitectureTest` analysis).

### Dependencies

`AccountService.register`/`.activateEmail`, matching `SessionIntegrationTest`/
`CleanupIntegrationTest`'s established `registerAndActivate` pattern.

### Inputs / Outputs / State Changes

Inputs: current suite + `Dockerfile` state. Outputs: a suite passing modulo the documented,
gate-approved Group A/B exceptions; a confirmed Docker build. State changes: none to production
runtime/schema; test-file-only.

### Files to Create

None.

### Files to Modify

- `services/auth/src/test/java/com/themistra/auth/audit/AuditTrailIntegrationTest.java`
- `services/auth/src/test/java/com/themistra/auth/authz/RoleAssignmentIntegrationTest.java`

### Files NOT to Modify

All production source (including `AuditService`/`RoleService` — the validation gap is logged, not
fixed); all `spec/` files; `Dockerfile` (already confirmed working); every other test file.

### Acceptance Criteria

- **AC1a** — Group C's 3 failing tests pass after the fix; every other currently-green test in the
  suite remains green (no regression from the fix).
- **AC1b** — Groups A (2+ tests, Kafka environment connectivity) and B (4 tests, unconfirmed
  flakiness) are documented in this task's own artifacts as deferred, each with independent
  corroborating evidence (Group A: reproduced on an unrelated already-merged test, per T36's own
  finding; Group B: observed since T31, not newly introduced), and this human-gate decision as their
  sign-off.
- **AC2** — Docker image builds from the repo root via
  `docker build -f services/auth/Dockerfile -t auth-service .`. **Already met** (Phase 0, exit 0).

### Success definition (Kimi Finding 10)

T37 is complete when: (1) Group C's 3 tests pass via the code changes above with zero regressions
elsewhere; (2) Groups A and B are documented as environmental/unconfirmed-flaky, each backed by
independent evidence, with this Phase 4 sign-off as their explicit deferral; (3) the Docker image
build is confirmed (done). `mvn -pl services/auth verify` is not required to exit zero on a run that
happens to hit Group A/B — those are named, evidenced, human-approved exceptions, not silent
tolerance of an unexplained red build.

### Required Tests

None new — Group C's fix uses the existing `registerAndActivate` pattern already proven in sibling
files, not new test authorship.

### Constraints

No production code change (Kimi Finding 5's validation gap is logged, not fixed). Module boundaries:
fix confined to the two named test files. Determinism: `registerAndActivate` is synchronous, no new
timing dependency introduced.

### Open Questions

No blockers. All carried forward from Phase 1/2 are resolved above.

---

**Phase 4 complete — task brief frozen and approved.** Proceed to Phase 5 (Implementation Plan).
