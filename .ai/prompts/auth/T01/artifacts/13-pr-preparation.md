# auth · T01 · Phase 13 — PR / Commit Preparation

Consumes `artifacts/12-specification-verification.md` — **PASS**. Proceeding per gate. No code
changed in this phase; this artifact documents the commit/PR only.

## Trailer deviation (flagged, not silent)

The phase template's literal instruction is to close the commit message with
`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` — a fixed string baked into
every generated task's Phase 13 prompt by `.ai/generate.py`, regardless of which model actually runs
the task (same class of generator artifact as the blanket `Contracts:` header line documented in
`AI_CONTEXT_ANALYSIS.md`). This session's work was performed by Claude Sonnet 5, not Opus 4.8, so
the trailer below uses the accurate model identity instead of the template's placeholder — using
the literal text would be a false attribution in the repo's permanent history.

## Commit title

```
auth: add V5 lockout-cleanup index and ShedLock table (T01)
```

## Commit message

```
auth: add V5 lockout-cleanup index and ShedLock table (T01)

Add V5__lockout_cleanup_and_shedlock.sql: a partial index on
lockout_state(locked_until) for efficient expired-lock scans, and a
shedlock table sized to ShedLock's standard JDBC schema. Both are
additive only; V1-V4 remain untouched (L1).

Add the flyway-maven-plugin to services/auth/pom.xml, pinned to
11.7.2 and configured against the local Docker Compose Postgres, so
the task's stated `mvn -pl services/auth flyway:migrate` command is
runnable directly (previously only Spring Boot's migrate-on-boot
path existed). The plugin carries no <executions> binding, so it
only runs on explicit invocation - never during package/verify or
the production Docker build, which copies just the built jar into a
distroless runtime image with no Maven present.

Verified: migration applies cleanly (BUILD SUCCESS, schema at v5),
both new schema objects confirmed via direct introspection query,
and flyway_schema_history shows one successful row for v5.

Full 14-phase workflow run (spec-driven audit trail in
.ai/prompts/auth/T01/artifacts/), including a Phase 3/8/11
adversarial review pass and a Phase 4/9 human-approved resolution.
One pre-existing, unrelated compile failure in the token module
(missing OAuth2TokenType / JwtAuthenticationConverter symbols) was
discovered and confirmed out of scope for this task - it reproduces
identically with this task's only source change removed - and is
left flagged for separate triage rather than fixed here.

Task: spec/auth-service/tasks.md #1. Requirements: R17, R40.
Locked decisions: L1.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files changed

**Code:**
- `services/auth/pom.xml` — modified: `flyway-maven-plugin` added (pinned version, local-dev-only
  comment, datasource configuration).
- `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql` — new.

**Spec-driven workflow scaffolding (generated, already committed in earlier commits on this
branch):**
- `.ai/prompts/auth/T01/00-*.md` … `13-pr-preparation.md`, `README.md` — the 14 phase prompts.

**Audit trail (this task's produced artifacts):**
- `.ai/prompts/auth/T01/artifacts/00-repository-understanding.md` through
  `11-test-review.md` — already committed (commits `cb92fea`…`9aacd12` on this branch).
- `.ai/prompts/auth/T01/artifacts/12-specification-verification.md` — written this session, **not
  yet committed**.
- `.ai/prompts/auth/T01/artifacts/13-pr-preparation.md` (this file) — **not yet committed**.

**Explicitly NOT part of this task's changes:** the root-level `prompt` scratch file (untracked,
predates and is unrelated to T01's implementation — the original audit request, not part of the
schema-migration deliverable).

## Summary

T01 provisions the schema V5 needed by two later tasks (#11 lockout state machine, #30 scheduled
cleanup job) and makes the task's own stated verification command runnable by adding the missing
Flyway Maven plugin. No application code, entity, repository, service, or controller was touched —
confirmed scope-correct against the frozen brief throughout.

## Testing performed

- `docker compose -f services/auth/compose.local.yaml up -d --wait` — Postgres + Kafka healthy.
- `mvn -pl services/auth flyway:migrate` — **BUILD SUCCESS**; schema `auth` migrated to v5.
- `SELECT indexname FROM pg_indexes WHERE schemaname='auth' AND indexname='idx_lockout_state_locked_until';`
  → confirmed present.
- `SELECT table_name FROM information_schema.tables WHERE table_schema='auth' AND table_name='shedlock';`
  → confirmed present.
- `SELECT version, description, success FROM auth.flyway_schema_history WHERE version='5';` →
  `5 | lockout cleanup and shedlock | t`.
- `git diff`/`git status` — confirmed `V1`–`V4` unchanged; only `V5` added.
- `mvn -pl services/auth validate` — re-run after Phase 9 edits, `BUILD SUCCESS`.
- `mvn -pl services/auth compile -DskipTests` — reproduces a pre-existing, unrelated `BUILD FAILURE`
  in the `token` module; confirmed via `git stash` isolation (Phase 6) and independently reproduced
  again in Phase 8 (independent review) and this session's Phase-11 touch-base — proven not caused
  by this task.
- SQL body of `V5__lockout_cleanup_and_shedlock.sql` re-diffed against `design.md` §4c — identical
  apart from markdown code-fence lines.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 1 — "Schema V5."
- **Requirements:** R17 (lockout threshold — supporting index only), R40 (scheduled cleanup —
  supporting table only).
- **LOCKED decisions:** L1 (migration immutability — complied; `V5` additive, `V1`–`V4` untouched).

## Branch / merge note

This work is on `spec/service-specs-and-ai-framework`, not `main`; per `agents.md`, `main` remains
deployable throughout — no direct changes were made to `main`, and this branch's only code diff is
additive (new migration + build-plugin config), consistent with that rule.
