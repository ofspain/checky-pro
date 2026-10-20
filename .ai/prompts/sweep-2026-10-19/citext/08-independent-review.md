<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). Hand-written, not produced by .ai/generate.py: the citext fix ran as a direct fix outside the 14-phase pipeline. The generator never deletes files, so regenerating leaves this folder alone. -->

# citext callback · 2026-10-20 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` (with auth-service's V1 as the other side of the conflict) |
| **Work** | A Flyway `beforeMigrate.sql` callback that installs `citext` into the `notifications` schema, and refuses a database where it lives elsewhere. The fix for the shared-database migration failure found by the Phase 11 Docker smoke run. |
| **Diff** | `git diff b6a4ba49..de07d046` (one commit, 18 files, +97 / −89) |
| **Branch** | `spec/service-specs-and-ai-framework` |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` (the defect, the author's decision, the change, the evidence, and self-found weaknesses S1–S8) |
| **Produces** | `artifacts/08-independent-review.md` (write it here; exactly one artifact) |

**Spec package:** `spec/notification-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`.
- **Standing rules:** `agents.md` is authoritative.
- **Verbatim block:** V1 is the §4c block in `design.md` and must be unchanged. The fix's note follows that block.
- **Other side of the conflict:** auth's V1 is `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql`. Auth's Flyway schema settings are in `services/auth/src/main/resources/application.properties` and `services/auth/pom.xml`.
- **Background:** the defect first appeared in `.ai/prompts/notification/T02/artifacts/06-implementation-notes.md`, step 5.

**Out of scope by the author's decision:** part 2, giving each service its own database in the deployment. That is infrastructure work. Do not report it as missing. Do report anything in this commit that makes it harder, or that would break a deployment which follows it.

---

Review the diff and the self-review with fresh, adversarial eyes. You are reviewing a finished pull request. Do NOT rewrite it.

Hunt for:
- **Callback correctness:**
  - Does Flyway run `beforeMigrate.sql` from both paths deployments and tests use (the Maven plugin and the Flyway API with default locations), and only before migrate?
  - Can the `DO` block pass when it should refuse, or refuse when it should pass? Consider no `citext` at all, `citext` in `public`, in `auth`, or in `notifications`, and a database migrated before the callback existed.
  - What happens on a database already at V19, such as the hand-fixed local `checky` database?
- **Ordering and privileges:**
  - What Flyway creates before the callback runs.
  - What role the migration needs.
  - Whether a non-superuser can install `citext` in the Postgres versions this may run on.
- **Auth's side of the conflict (S1):** in a shared database, what does this change do to auth if notification migrates first? Is any part of the self-review's account wrong?
- **Tests:**
  - Do the two new tests and the 14 changed tests prove the callback, or could any of them pass with it broken?
  - Is the refusal test's database setup a faithful copy of what auth's V1 does?
  - Did removing the hand-written setup leave any test still installing `citext` some other way?
- **Spec:** does the note in `design.md` claim more than the code and tests show? Is the §4c block byte-for-byte unchanged?
- **LOCKED decisions:** any deviation from `spec/notification-service/design.md` §4a.

Before you report a finding, check the cited file and line in the source at `de07d046`. Several past findings were wrong or already fixed, so cite file:line for every finding.

Return, per finding: **Issue · Evidence (file:line) · Recommendation · Confidence.** Findings only, ranked most severe first.

---

## Guardrails
- Review ONLY commit `de07d046`. Pre-existing code is in scope only where this commit relies on it or changes how it behaves.
- No rewrites, no speculative improvements, no new scope.
- Never modify files under `spec/` or any source file.
- Reference documents; do not paste whole specs or files into the artifact.
- Produce exactly one artifact: `artifacts/08-independent-review.md`. Write it, then STOP and wait.
