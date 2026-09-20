<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T37 · Phase 1 — Specification Extraction

## Business Rules

- **R43** — every security-relevant action (login success/failure, lock/unlock, MFA events,
  password/key changes, token reuse, API-key operations) appends an `auth_audit` row and mirrors a
  reduced event to `auth.security.audit` via the outbox. Scoped here because Group C's FK-violation
  failures (`AuditTrailIntegrationTest`, `RoleAssignmentIntegrationTest`) are the only currently-red
  tests that exercise R43's own audit-append behavior — a full-suite pass is, among other things, the
  final confirmation that R43 actually holds.

No other requirement ID is uniquely scoped to this task — T37's job is confirming the *whole* suite
(every requirement every other task already implemented), not adding new behavior.

## Locked Decisions

None constrain this task directly. Indirectly relevant (not to be violated by any fix made under
this task): L1 (immutable migrations — a Group C fix must not need schema changes, and none does),
L12 (module boundaries — any fix stays inside the two affected test files, no new cross-module
dependency).

## Files involved

**To read/run (no code expected to change unless a fix is authorized at Phase 4):**
- Every file under `services/auth/src/test/java` — literally the task's own scope (`mvn ... verify`
  runs all of them).
- `services/auth/Dockerfile` — the image-build half of the task statement.

**Existing files a fix would touch, if Group C is brought in scope (Phase 4 decision, not decided
here):**
- `services/auth/src/test/java/com/themistra/auth/audit/AuditTrailIntegrationTest.java`
- `services/auth/src/test/java/com/themistra/auth/authz/RoleAssignmentIntegrationTest.java`

**New files expected:** none. This task is verification, not feature/test authorship.

## Dependencies

- **Infra**: Docker (for both Testcontainers-backed tests and the image build), Maven, the module's
  own `pom.xml` (no separate Failsafe/integration-test binding — Testcontainers tests run under the
  ordinary Surefire `test` phase, confirmed at Phase 0).
- **A known-good fix pattern, already established and reused repeatedly this session, for Group C**:
  `AccountService.register`/`.activateEmail` (or the HTTP equivalent) before using an account UUID
  as an audit/role-assignment principal — matches `SessionIntegrationTest`/`CleanupIntegrationTest`'s
  own `registerAndActivate` helper.
- **No dependency exists for Groups A/B** — Group A (Kafka producer→broker connectivity) is
  environment/Docker networking, not a `services/auth` code dependency; Group B (null-response
  flakiness under full-suite load) has no confirmed root cause to depend on.

## Acceptance Criteria

Derived directly from the task statement's own two literal clauses (no numbered AC list exists in
`design.md` for this task):

| AC | Statement | Status at Phase 0 |
|---|---|---|
| AC1 | `mvn -pl services/auth verify` passes with zero failures/errors | **Not met** — 702 tests, 1 failure, 8 errors (3 groups, see Phase 0) |
| AC2 | Docker image builds from the repo root via `services/auth/Dockerfile` | **Met** — verified at Phase 0, exit code 0 |

## Tests required

None new. `package.md` §8 has no named test for this task (confirmed at Phase 0/header). This task's
own "test" is running the existing suite to completion and, depending on Phase 4's scope decision,
possibly fixing Group C's two files using their own established pattern — not authoring new test
coverage.

## Open Questions

**Blocker-class, for Phase 4.** AC1 is currently false, and the three groups of failures have three
different risk/effort profiles:
- **Group C** (FK-violation, 3 tests) has a known, cheap, already-proven fix pattern — clearly
  fixable within this task's reasonable scope.
- **Group A** (Kafka environment issue, blocks 2+ tests including T36's own) has **no known
  code-level fix** — it is a local Docker/host networking problem already logged and explicitly
  deferred at T36's own human gate. Fixing it, if even possible from inside this task, could mean
  environment/infrastructure changes outside `services/auth`'s own code, a materially different kind
  of work than every other task in this pipeline.
- **Group B** (null-response flakiness, 4 tests) has no confirmed root cause; today's fresh run is
  the first time a plausible link to Group A's producer-retry contention was noticed, unconfirmed.

Whether T37's scope is "fix everything until `mvn verify` is genuinely green" (which may not be
achievable purely through `services/auth` code changes, given Group A) or "fix what's fixable
(Group C), and formally document/defer the rest with the human's sign-off" is a genuine trade-off
this pipeline's Phase 4 gate exists for — not decidable from the spec alone.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
