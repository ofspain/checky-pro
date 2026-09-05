# auth · T01 · Phase 12 — Specification Verification

Consumes all prior artifacts (Phases 0–11). Compares the final implementation against
`requirements.md`, `design.md`, and `tasks.md` task 1 only.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R17** (lockout: 5 failures/30min → LOCKED 15min) | **No — out of scope for T01 by design.** Only the supporting index is provisioned. | `V5__lockout_cleanup_and_shedlock.sql:4-6` (`idx_lockout_state_locked_until`) | N/A — rule itself is tested when implemented (task #11) | The lockout state machine (`LockoutStateMachine`, `LockoutService`) — deliberately deferred to task #11, not missing from T01 | None — matches frozen brief Purpose exactly |
| **R40** (scheduled cleanup job hard-deletes stale rows) | **No — out of scope for T01 by design.** Only the `shedlock` coordination table is provisioned. | `V5__lockout_cleanup_and_shedlock.sql:9-14` (`shedlock` table) | N/A — job itself is tested when implemented (task #30) | ShedLock library dependency + `@SchedulerLock` job — deliberately deferred to task #30 | None |
| **L1** (V1–V4 immutable; new work only as new `V<n>`) | **Yes.** | `git diff`/`git status` (Phase 6, re-confirmed Phase 9): `V1`–`V4` byte-identical; only `V5` added | Verified manually (`git status`); no CI-enforced guard yet | CI check preventing future edits to `V1`–`V4` (Phase 11 Finding 3) — process gap, not a T01 defect | None |
| **Task statement** (add `V5`, run `mvn -pl services/auth flyway:migrate` against local Compose Postgres) | **Yes.** | `V5__lockout_cleanup_and_shedlock.sql` (new); `services/auth/pom.xml` `flyway-maven-plugin` block (new, added to make the literal command runnable — frozen brief Finding 1/Option A) | Verified operationally: `mvn -pl services/auth flyway:migrate` → `BUILD SUCCESS`; `auth.flyway_schema_history` row confirmed for v5; `pg_indexes`/`information_schema.tables` confirm both new objects | Dedicated automated schema-regression test (Phase 11 Finding 1) — optional hardening, not required by the frozen brief | None from frozen brief; SQL matches `design.md` §4c verbatim (re-diffed, only markdown fences differ) |

## Principal-engineer assessment

**1. Is the task fully complete?** Yes, within the boundary the frozen brief set: a schema-only
migration plus the build-tooling change needed to make the task statement's literal command work.
Nothing in `tasks.md` task 1 or the frozen brief is outstanding.

**2. Does it satisfy every acceptance criterion?** Yes — AC1 (`shedlock` table), AC2 (partial
index), AC3 (`V1`–`V4` unchanged), and AC4 (migration runs cleanly via the stated Maven goal) were
each independently verified with a concrete command/query, not asserted. See Phase 6 and Phase 9
notes.

**3. Does it violate any LOCKED decision?** No. L1 is fully complied with: the only schema change is
additive, in a new `V5` file, and its content (including `IF NOT EXISTS` on both statements) matches
`design.md` §4c verbatim — the one proposal to alter that SQL (Phase 3 Finding 5) was correctly
rejected at Phase 4 because it conflicts with the VERBATIM instruction.

**4. Remaining risks** (none block this task's own completion; carried forward, not resolved here):
- **Pre-existing, unrelated `token`-module compile break** (`OAuth2TokenType`, `JwtAuthenticationConverter`
  not found) prevents `mvn -pl services/auth verify` from passing module-wide. Proven pre-existing via
  `git stash` reproduction (Phase 6) and reproduced again identically in Phase 8 (independent
  review) and this session's touch-base re-run. Needs its own defect ticket, outside T01.
- **No CI-enforced guard for L1** (Phase 11 Finding 3) or for the Flyway plugin's lifecycle-binding
  safety (Phase 11 Finding 4) — currently protected by convention and manual review only.
- **Non-`CONCURRENTLY` index build** will briefly lock `lockout_state` during a production deploy if
  the table has rows by then (Phase 7/8 Finding). Not actionable in T01 (LOCKED verbatim SQL);
  flagged for whoever owns the production rollout runbook.
- **ShedLock `TIMESTAMPTZ` compatibility** with the eventual `LockProvider` — explicitly deferred to
  task #30 in the frozen brief.
- **No dedicated automated schema-regression test** for V5 (Phase 11 Finding 1) — current coverage
  is direct operational verification (this session) plus indirect Testcontainers coverage, itself
  currently blocked by the pre-existing compile error until that's fixed separately.

None of the above are deviations from what T01 was scoped to deliver; they are pre-existing
conditions or deliberately deferred follow-ups, each already logged with an owner or a clear reason
it isn't this task's to fix.

## Verdict

**PASS** — every acceptance criterion in the frozen brief is independently verified, no LOCKED
decision is violated, and all outstanding items are explicitly out-of-scope or deferred rather than
unmet requirements of T01 itself.
