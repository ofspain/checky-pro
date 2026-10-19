# payment · T27 · Phase 10 — Test Manifest

Consumes:
- `artifacts/04-frozen-task-brief.md` (FROZEN)
- `artifacts/09-review-resolution.md`

T27 is a build-gate task. The brief names exactly one committed test, and treats the AC1–AC4
runs as evidence. Phase 10 adds no new test file. The named test already exists, from Phase 6,
and was hardened in Phase 9. This phase re-runs the full suite against the resolved
implementation.

No production code was changed.

## Committed tests

| Test | AC | Covers |
|---|---|---|
| `T01SkeletonRegressionTest.paymentDockerfileHonoursBuildContextAndSecurityRules` | AC5 (D4), and guards AC2 and AC3 against regression | **Final stage:** it is `gcr.io/distroless/java21-debian12:nonroot`, found with `lastIndexOf`, and it contains `USER nonroot`. <br>**Jar copy:** exactly one `COPY --from=`, and it is the repackaged jar. <br>**Entrypoint:** the exec-form `ENTRYPOINT`. <br>**Bans:** no `ADD`, no whole-context `COPY` (including flag forms like `--chown`), and no `ENV` or `ARG` (L13). <br>**Build context:** `COPY pom.xml ./`, plus one `COPY <m>/pom.xml <m>/` for each `<module>` declared in the root pom, with the module list asserted non-empty. <br>**Port:** `EXPOSE 8083`. <br>All keyword checks are case-insensitive, by normalisation. |

Boundaries covered by the mutation proofs (Phase 6 and Phase 9; temporary, then restored):
- a missing module pom
- a missing root pom
- `ENV` and `ARG`, in either case
- `ADD`, in lowercase
- whole-context copies, including `--chown`
- a whole `target/` copy
- a shell-form entrypoint
- a lowercase or wrong final base image

Every mutation fails on its intended assertion. See the tables in `06` and `09`.

**No state transitions are in scope.** T27 adds no domain behaviour.

## Run evidence

| AC | Evidence | Result |
|---|---|---|
| **AC1** | `mvn -o -pl services/payment verify` from the repo root, re-run after Phase 9 | `Tests run: 497, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` in 01:19. The `spring-boot:repackage` step replaced `services/payment/target/payment-service.jar` with the repackaged archive. |
| **AC2** | `docker build -f services/payment/Dockerfile -t payment-service .` (Phase 6) | Exit 0, 157 MB. The Dockerfile has not changed since: Phase 9 touched only the test. |
| **AC3** | `docker image inspect` and `docker run --entrypoint sh` (Phase 6) | `User` is nonroot and `ExposedPorts` is 8083/tcp. The entrypoint is exec-form java. `Env` holds no credential. There is no shell. |
| **AC4** | D3 read-only smoke start (Phase 6) | `/actuator/health` returned `UP`. All four `chain.tx.*` partitions were assigned to the `payment-service` group. Teardown complete. |
| **AC6** | `git diff --stat` across T27's commits, outside `.ai/` | Only `services/payment/Dockerfile` and `T01SkeletonRegressionTest.java` changed. |

## Requirement mapping

- **No business requirement is scoped** (Phase 1). The 497-test run is the regression evidence
  for R1–R31. R26 is deferred.
- **L13** is enforced by the `ENV`/`ARG` ban, and confirmed at runtime by AC3's `Env` inspection.
- The **`agents.md` Deployment rule** (multi-stage, distroless JRE 21, non-root, read-only rootfs)
  is covered by the guard test plus AC3 and AC4.

## Open Questions

None.
