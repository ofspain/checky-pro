# payment · T27 · Phase 11 — Test Review Resolution

Consumes:
- `artifacts/11-test-review.md` (Kimi, Gaps 1–7)
- `artifacts/10-test-manifest.md`

**Human sign-off:** 2026-10-19. Every recommended option was chosen.

Each gap was checked against `T01SkeletonRegressionTest.java` and the Dockerfile before it
went to the gate.

| Gap | Verified | Decision | Change |
|---|---|---|---|
| **1**: AC2–AC4 not automated | True. They are manual run evidence. | **REJECTED (documented).** A Docker build inside the suite adds minutes and requires Docker in CI, and the brief excludes CI wiring. | Recorded as a manual gate in the `10` follow-ups section. |
| **2**: a later `USER` could override | True. `contains("USER nonroot")` allowed a later `USER root`. | **ACCEPTED** | The runtime stage's `USER` lines must be exactly `["USER nonroot"]`. |
| **3**: `EXPOSE` not stage-scoped | True. The check matched `EXPOSE` anywhere. | **ACCEPTED** | The file's `EXPOSE` lines must be exactly `["EXPOSE 8083"]`, and the runtime stage must contain it. |
| **4**: no runtime instruction allowlist | True. | **ACCEPTED, with `LABEL` allowed** (consistent with the Phase 9 decision to allow OCI labels) | New constant `RUNTIME_STAGE_INSTRUCTIONS` = {`FROM`, `WORKDIR`, `COPY`, `USER`, `EXPOSE`, `ENTRYPOINT`, `LABEL`}. Every runtime-stage line's keyword must be in that set. `RUN`, `SHELL`, `VOLUME`, `STOPSIGNAL` and the like now fail. |
| **5**: build stage name | **Inaccurate.** Since Phase 9, the jar-copy check requires the literal `COPY --from=build …`. Renaming the stage to `builder` therefore already fails the test. | **REJECTED** | None. |
| **6**: `src/test` copied into the build stage | True, but it is the same as Phase 8 F5, which the human rejected at Phase 9. | **REJECTED** | None. It stays in the PR notes. |
| **7**: follow-ups not listed in the manifest | True. | **ACCEPTED (documentation)** | `10-test-manifest.md` now has an "Open follow-ups" section listing FU-T27-1, FU-T27-2 and the Gap 1 manual gate. |

## Files changed

- **`T01SkeletonRegressionTest.java`** (test only):
  - one new constant, plus an import of `java.util.Set`
  - the `USER` and `EXPOSE` assertions were tightened
  - the allowlist assertion was added
- **`10-test-manifest.md`**: the follow-ups section.
- The Dockerfile and all production code are unchanged.

## Verification

`mvn -o -q test -Dtest=T01SkeletonRegressionTest` gives **9/9** on the real Dockerfile.

Mutation proof. Each mutation was applied alone, then restored (`cmp` OK, clean re-run exit 0):

| Mutation | Result |
|---|---|
| `USER root` after `EXPOSE` | FAIL: "nothing switches the user back" |
| `RUN echo hi` in the runtime stage | FAIL: the distroless allowlist |
| `VOLUME /data` in the runtime stage | FAIL: the distroless allowlist |
| `EXPOSE 8083` moved to the build stage | FAIL: "the port is exposed by the image that runs" |
| a second `EXPOSE 9090` | FAIL: "exposed once" |
| `LABEL org.opencontainers.image.source=x` | PASS, as intended (Phase 9 allowance) |

**Full suite:** `mvn -o -pl services/payment verify` gives **497/497** and `BUILD SUCCESS`
in 01:24, re-run after the Phase 11 changes.

## Open Questions

None.
