STATUS: FROZEN

# crypto · T27 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 6 findings independently verified directly against this branch's real, restored source (not taken
at face value in either direction) — 5 of 6 describe conditions that do not exist on this branch.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | `server.port=${SERVER_PORT:8082}` overrides the default; `EXPOSE 8080` is wrong | **REJECTED — verified factually wrong** | The real `application.properties:43` has this exact text only as a **commented-out** line (`# server.port=8080 / ...`) — no active override exists anywhere in the file. The real default is Spring Boot's 8080, exactly as the original TIB stated. `EXPOSE 8080` is correct, unchanged. |
| 2 | `maven-failsafe-plugin` runs `*IT` classes during `verify` | **REJECTED — verified factually wrong** | No `failsafe` plugin exists anywhere in the real `pom.xml` (parent or module), re-confirmed directly this phase. `mvn -pl services/crypto verify` genuinely triggers nothing beyond `test` (Surefire) + `package` (`spring-boot-maven-plugin:repackage`), exactly as Phase 0/2 already established. |
| 3 | Current branch doesn't compile (Tron trident, KMS SDK, `ProviderAnswer` mismatches) | **NOT APPLICABLE — describes the wrong codebase** | The same `feat`-stack poisoning pattern seen 9 times already this session (T24-T26). This branch's real dependencies resolve and compile cleanly, re-confirmed directly this phase (`mvn -pl services/crypto -am test-compile` — clean). |
| 4 | Only `./mvnw` is available; `mvn` is not on PATH | **NOT APPLICABLE — specific to the reviewer's own execution environment** | `mvn` has been used successfully, directly, dozens of times throughout this entire session in this actual environment. Whatever environment Kimi's review ran in apparently lacks it; this session does not. |
| 5 | No smoke test that the runtime image actually starts | **ACCEPTED, with a factual correction** | The scoping point is sound (a successful image *build* doesn't prove runtime *startup* works) and is adopted. The specific claim — "the service refuses to start without runtime config" — is corrected: `DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`, and `AUTH_ISSUER_URI` all resolve to real `localhost` default fallbacks in the actual `application.properties` (not hard-required), so the more accurate characterization is that the Spring context would likely start but fail to connect to anything real — either way, a full runtime smoke test needs real infrastructure this environment doesn't have, so it remains correctly out of this task's own scope, stated explicitly rather than left implicit. |
| 6 | `dependency:go-offline` may fail if dependencies are genuinely unresolvable | **NOT APPLICABLE — describes the wrong codebase** | Same root cause as Finding #3 — this branch's real dependencies are all genuinely resolvable, confirmed by the same clean compile. |

## Task

Author `services/crypto/Dockerfile` (unchanged from Phase 2's design — `EXPOSE 8080`, confirmed correct)
and attempt, then honestly report, `mvn -pl services/crypto verify` and a `docker build` from the repo
root.

## Purpose

Unchanged from Phase 1/2.

## Scope

**In:** Unchanged from Phase 2's Dockerfile design (direct mirror of `services/auth/Dockerfile`,
`EXPOSE 8080`, `crypto-service.jar`). Phase 6's own verification-attempt report will explicitly state
(Finding #5) that a successful build does not verify runtime startup, and why that's out of scope here.

**Out:** Unchanged from Phase 2, plus: no runtime smoke test (Finding #5's accepted scoping).

## Business Rules / Locked Decisions

Unchanged from Phase 1 (L13).

## Files to Create

- `services/crypto/Dockerfile`

## Files to Modify / NOT to Modify

Unchanged from Phase 2.

## Acceptance Criteria

Unchanged from Phase 1's AC1-AC3, with AC1 unambiguously confirmed (not just assumed) to mean: `mvn
-pl services/crypto verify` triggers `test` + `package` only (no failsafe/integration-test phase
exists) — so the compile/package/unit-test/ArchUnit/contract-test portion is the entirety of what a
local run in this environment can attempt, and it is not partial coverage of some larger `verify`
scope, it IS `verify`'s actual real scope for this module today.

## Required Tests

None (Phase 1, unchanged).

## Constraints

Unchanged from Phase 2, plus Finding #5's corrected scoping note above.

## Open Questions

No blockers. All three of Kimi's own open questions are resolved by direct verification: (1) port is
8080, not 8082 — no change needed; (2) the compile errors Kimi observed do not exist on this branch —
a wrong-codebase artifact, not a real prerequisite gap; (3) `verify`'s real scope for this module
already excludes Docker-dependent integration tests entirely (no failsafe plugin), so there is no
`-DskipITs` flag to consider — the question doesn't apply.
