# crypto · T27 · Phase 10 — Test Generation

## Scope

None. Phase 1's own "Tests required" section is explicit: "None — this task authors no test. Its own
'test' is the full-suite run itself (AC1) and the image build (AC2), both process-level checks, not new
JUnit tests." Restated, unchanged, at Phase 2, Phase 4, and Phase 5. No deviation found while
implementing.

## What stands in for tests here

- `mvn -pl services/crypto -am verify` — run twice this task (Phase 6, Phase 9 after the branch
  restoration), both times producing 696 tests / 0 failures / 14 already-disclosed Docker-only errors.
- `docker build -f services/crypto/Dockerfile -t crypto-service .` — attempted, confirmed blocked only
  at the daemon-connection level (no Docker daemon in this environment), not a Dockerfile-content issue.
- `KmsSignerArchitectureTest` — the one piece of test *code* this task touched (Phase 6's allowlist
  addition, Phase 9's message-assertion addition) belongs to T25/T26's own pre-existing file and was
  modified only to resolve a genuine cross-task conflict (Phase 6) and a Kimi review finding (Phase 9),
  not authored fresh for T27 itself.

## Phase 11 (Kimi Test Review) additions

Kimi's Phase 11 pass (`artifacts/11-test-review.md`) confirmed the prediction above: all 8 findings were
about `KmsSignerArchitectureTest`'s T27-touched pieces or about this task's own already-disclosed
scope, not a dedicated new test suite.

| # | Finding | Disposition |
|---|---|---|
| 1 | `mvn verify` doesn't fully pass here (14 Docker errors) | **Rejected — already disclosed.** Standing, disclosed environment limitation (Docker daemon unavailable) since T23; not fixable within this task. |
| 2 | `docker build` wasn't actually executed | **Rejected — already disclosed.** Same root cause as #1; Dockerfile reviewed correct by direct construction instead. |
| 3 | Negative-proof assertions don't verify the failure message | **Already resolved (Phase 9).** `bothRulesActuallyFailAgainstAGenuineViolation` already has `.hasMessageContaining(...)` on both assertions, added in response to this same finding from Kimi's Phase 8 review. Stale by the time Phase 11 ran. |
| 4 | No proof the allowlist's "allowed" branch actually works, only that the whole-codebase canary happens to pass | **Accepted.** Added `allowlistedClassIsNotFlaggedAsAViolation()` — a direct, scoped-to-`watch`+`attest` positive proof that the rule does not throw against the real, allowlisted `EndToEndIntegrationTest`, complementing the existing negative-proof and regression-guard tests. |
| 5 | No automated Dockerfile packaging-rules check (multi-stage, distroless, non-root, no secrets) | **Rejected — out of scope.** New CI/tooling infrastructure for future Dockerfile changes; T27's own scope is authoring and verifying this one Dockerfile, not building ongoing enforcement tooling. Candidate for a future task. |
| 6 | Traceability matrix omits the `KmsSignerArchitectureTest` updates | **Accepted** — this section (added post-Phase-11) is that traceability: `noClassOutsideAttestMayReferenceKmsSigner` (Phase 6, cross-module allowlist), the two `hasMessageContaining` assertions (Phase 9), and `allowlistedClassIsNotFlaggedAsAViolation` (Phase 11) all trace to T27's own conflict-resolution and review work on this pre-existing T25 file. |
| 7 | No test linking the POM `finalName` to the Dockerfile's `COPY` path | **Rejected — out of scope.** Same category as #5, future CI-infrastructure suggestion, not this task's own deliverable. |
| 8 | Full-suite result not archived as a reproducible artifact (surefire/failsafe reports, build log) | **Rejected — out of scope.** CI-pipeline configuration is outside this repo-local task's own scope and this session's own access. |

Full suite re-verified after the Finding #4 addition: 697 tests (696 + the new test), 0 failures, the
same 14 already-disclosed Docker-only errors.
