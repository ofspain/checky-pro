# payment · T27 · Phase 12 — Specification Verification

Consumes artifacts `00`–`11`. Checked against `spec/payment-service/tasks.md:48` (task 27),
`agents.md:60-61` (Deployment), `design.md:17` (L13) and the frozen brief's AC1–AC6.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| tasks.md:48: `mvn -pl services/payment verify` must pass | Yes | Phase 10 and Phase 11 re-runs: `Tests run: 497, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, repackaged `payment-service.jar` | The full suite itself (AC1) | — | None |
| tasks.md:48: Docker image builds from repo root | Yes | `services/payment/Dockerfile:1-24`; `docker build -f services/payment/Dockerfile -t payment-service .` exit 0, 157 MB (`06`) | AC2 run; `T01SkeletonRegressionTest.java:278` guards the build context (`:307` root pom, plus the per-module pom loop) | Not automated in CI (Phase 11 Gap 1, rejected, manual gate) | None |
| agents.md:61: multi-stage | Yes | `Dockerfile:8` (build), `Dockerfile:19` (runtime) | `T01…:278` (≥2 `FROM`) | — | None |
| agents.md:61: distroless JRE 21 | Yes | `Dockerfile:19` `gcr.io/distroless/java21-debian12:nonroot` | `T01…:278` (final `FROM`), `:39` instruction allowlist; AC3 (no shell) | Digest pinning (S6/F8, documented) | None |
| agents.md:61: non-root | Yes | `Dockerfile:22` `USER nonroot` | `T01…:289` (exactly one `USER`, nonroot); AC3 `User=nonroot` | — | None |
| agents.md:61: read-only rootfs | Yes, at runtime | AC4: `--read-only --tmpfs /tmp`, `/actuator/health` `UP`, 4 `chain.tx.*` partitions assigned (`06`) | AC4 run (manual) | The K8s `securityContext` belongs to the infra workstream (TIB Out) | None |
| design.md:17 L13: no secret in the image | Yes | `Dockerfile` has no `ENV`/`ARG` and copies no config. AC3 `Env` shows only `PATH`/`SSL_CERT_FILE`/`LANG`/`JAVA_VERSION`. AC4 passes every value as an env var. | `T01…:305` (no `ENV`/`ARG`, case-insensitive via `:331`), whole-context ban `:345` | — | Ban widened to `ARG` (human, Phase 9) |
| Frozen D1: sibling pattern, jar, port, entrypoint | Yes | `Dockerfile:21,23,24`; `application.properties:8` `server.port` 8083 | `T01…:295` (jar copy), `:297` (exec entrypoint), `:323` (single `EXPOSE`) | — | None |
| Frozen D2: all module poms copied | Yes | `Dockerfile:10-14` | `T01…:278` loop over the root pom's `<module>` list | — | None |
| Frozen D3: smoke start | Yes | `06` AC4 section; teardown verified; `auth-*-1` untouched | Manual | — | One harness-only addition (topic creation), disclosed in `06` |
| Frozen D4: committed guard | Yes | `T01…:278-327`, plus helpers `:331`, `:345` | Self-guarding; 2 + 8 + 6 mutations proven (`06`, `09`, `11`) | — | Hardened beyond D4 at Phases 9 and 11 (human-approved) |
| AC6: no change outside 2 files | Yes | `git diff --stat` across T27, excluding `.ai/`: only `services/payment/Dockerfile` and `T01SkeletonRegressionTest.java` | — | — | None |

## Principal-engineer assessment

1. **Is the task fully complete?** Yes. Both clauses of task 27 hold:
   - verify passes from the repo root, 497/497
   - the image builds from the repo root

   The Deployment rule is satisfied and proven: the image is multi-stage, distroless, runs as
   non-root, and was shown working on a read-only filesystem.
2. **Does it satisfy every acceptance criterion?** Yes. AC1–AC6 have recorded evidence. AC5's
   guard is mutation-proven across all three review rounds.
3. **Does it violate any LOCKED decision?** No.
   - **L13** is upheld and enforced by a committed test.
   - **L3:** the dependency set is unchanged, since neither pom was touched.
   - **`spec/` and `contracts/`** are untouched.
4. **Remaining risks:**
   - **FU-T27-1.** The auth, crypto and notification Dockerfiles still fail `docker build`.
     These are real deploy blockers for those services, but outside this task.
   - **FU-T27-2.** `jwk-set-uri` is hard-coded to localhost. Deployments must set the
     relaxed-binding env var until the placeholder is added.
   - **AC2–AC4 are manual gate evidence,** not CI-enforced. A Dockerfile that is correct by
     text could still break against a future base-image tag, because images are tag-pinned,
     not digest-pinned.
   - **The build stage compiles test sources** (F5). This is a build-time cost only.

**PASS.** Task 27's verify and repo-root image build both succeed. The image meets the
Deployment rule and L13, and a committed, mutation-proven test guards it.
