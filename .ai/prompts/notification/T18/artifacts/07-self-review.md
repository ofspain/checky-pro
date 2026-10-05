# notification · T18 · Phase 7 — Self Review

Self-review of the Phase 6 Dockerfile against the frozen brief and `agents.md`'s Deployment rule.
Findings only — no fixes applied here.

## Finding 1 · The sibling-pom list is hand-maintained and will silently re-break when a module is added

**Severity:** Low

**Evidence:** `services/notification/Dockerfile` copies `services/auth/pom.xml` and
`services/crypto/pom.xml` by name. The root `pom.xml` `<modules>` list (`:24-26`) is the real
source of truth for which sibling poms Maven's reactor validation requires.

**Issue:** This is the same bug class the task fixed, just deferred: if a fourth module is ever
registered in the root `pom.xml`, this Dockerfile will fail `docker build` again with the same
"Child module ... does not exist" error, and nothing in the build would flag the mismatch before a
Docker run does. The fix is correct for today's three-module tree, not for the tree as it may later
become.

**Recommendation:** Not fixed here — the frozen brief scoped this task to today's exact tree, and a
generalized copy mechanism (e.g. copying all `services/*/pom.xml` in one step) is a small, separate
design choice worth making deliberately. Worth raising alongside the already-known auth/crypto
Dockerfile follow-up, since the two are the same root cause.

## Finding 2 · Only a `latest` tag is produced

**Severity:** Informational

**Evidence:** `docker build ... -t notification-service` tags `latest` only; no version or git-SHA
tag is applied.

**Assessment:** Matches the sibling precedents' own build commands exactly, and the task's own
literal wording ("Docker image builds") does not require a release-tagging scheme. Image tagging for
deployment belongs to CI/CD, which is explicitly out of this task's scope (Phase 2). No change.

## Checked and found correct

- Multi-stage build; final image is `distroless/java21-debian12:nonroot` with no build tools, Maven,
  or source present (confirmed structurally from the Dockerfile itself).
- `USER nonroot` is set and confirmed via `docker inspect` (Phase 6).
- `EXPOSE 8082` matches notification's real `server.port` default (Phase 4, Finding 2).
- Dependency-resolution layer (`dependency:go-offline`) runs before `src` is copied, so source edits
  don't invalidate the dependency cache.
- `read-only rootfs` is intentionally not in the Dockerfile (Phase 3/4) — runtime/pod-spec concern.

## Open Questions

No blockers. Finding 1 is a real, deferred maintenance hazard, not a defect in today's build.
