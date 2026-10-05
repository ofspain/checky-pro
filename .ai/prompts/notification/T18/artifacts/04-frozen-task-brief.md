STATUS: FROZEN

# notification · T18 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 6 findings verified directly against actual source before disposition — Kimi's own factual
claims (the root `pom.xml` line numbers, both sibling Dockerfiles' own `COPY` lines, notification's
own real default port) were independently re-confirmed via direct `grep`, not taken on word. All 6
are **ACCEPTED**; none rejected.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | The sibling-pom reactor bug is the only material design problem | — | **ACCEPTED** | Matches Phase 0's own reproduced finding exactly. `COPY` every sibling module's `pom.xml` before any `mvn` command runs. |
| 2 | Exposed port should match this service's actual default port | Low | **ACCEPTED — corrects the Phase 2 brief's own unstated assumption** | Verified directly: `application.properties:9`, `server.port=${SERVER_PORT:8082}` — notification's own real default is `8082`, not `8080` (auth's own port, which both existing Dockerfiles' `EXPOSE 8080` matches correctly for auth, but which this task's own brief never explicitly checked before implicitly assuming the same literal line). `EXPOSE 8082`. |
| 3 | The build must be invoked from the repo root | — | **ACCEPTED** | A documentation comment at the top of the Dockerfile, mirroring both existing precedents' own identical comment. |
| 4 | No production code or dependency changes | — | **ACCEPTED** | Confirmed — only `services/notification/Dockerfile` is created. |
| 5 | Verification cannot be performed in Kimi's own environment | — | **ACCEPTED, with a correction**: this agent's own environment *does* have both `mvn` and `docker` available (confirmed directly, Phase 0) — real verification happens at Phase 6, not deferred to "an environment with Maven and Docker," which already exists here. |
| 6 | `read-only rootfs` stays out of the Dockerfile | — | **ACCEPTED** | Matches Phase 2's own identical disposition exactly — a pod-spec/runtime concern, not a `Dockerfile`-level one, confirmed neither existing precedent sets it. |

## Task

Unchanged from Phase 2, with Finding #2's port correction folded in.

## Scope

Unchanged from Phase 2.

## Business Rules

None (confirmed at Phase 1).

## Locked Decisions

Deployment (`agents.md`): multi-stage Docker, distroless JRE 21 base, non-root user (unchanged).

## Dependencies

None new (Finding #4).

## Files to Create

- `services/notification/Dockerfile`

## Files to Modify

None.

## Files NOT to Modify

`services/auth/Dockerfile`, `services/crypto/Dockerfile`, root `pom.xml`, `services/notification/pom.xml`,
every file under `spec/` (unchanged from Phase 2).

## Acceptance Criteria

Unchanged AC1-AC4 from Phase 1, with AC3's "distroless, non-root" reading now also pinned to the
correct exposed port (`8082`) as a concrete implementation detail, not a new criterion.

## Required Tests

Unchanged from Phase 1 — a real `mvn` run (AC1) and a real `docker build` run (AC2), both executed
directly at Phase 6 in this agent's own environment (Finding #5's own correction), not deferred.

## Constraints

Unchanged from Phase 2, plus (Finding #2): `EXPOSE 8082`, matching `server.port`'s own real default
in `application.properties`, not `8080` copied literally from the sibling precedent.

## Open Questions

No blockers. All 6 Phase 3 findings resolved above, every one ACCEPTED — Finding #2 is this task's
own real, substantive catch, corresponding to T16's own Finding #4 (a design-phase correction before
any code was written) in spirit if not in size.
