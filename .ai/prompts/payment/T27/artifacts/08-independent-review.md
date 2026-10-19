# payment · T27 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 implementation (`services/payment/Dockerfile`, `T01SkeletonRegressionTest.java`) and Phase 7 self-review against the frozen brief.

---

## Finding 1 — The guard test does not require the root `pom.xml` to be copied

- **Issue:** `paymentDockerfileHonoursBuildContextAndSecurityRules` loops over root-pom modules and asserts each module `pom.xml` is copied, but it never asserts that the root `pom.xml` itself is copied. If someone removed `COPY pom.xml ./`, the test would still pass while the Docker build would fail.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java` lines 285-296.
- **Recommendation:** Add an assertion such as `assertThat(lines).contains("COPY pom.xml ./");` before the module loop.
- **Confidence:** Low — the Dockerfile currently has the line, but the guard should encode the invariant.

---

## Finding 2 — The guard test does not verify the exact `COPY --from=build` of the jar

- **Issue:** The guard checks that no broad `COPY .` or `ADD` exists, but it does not verify that the runtime stage copies only the single repackaged jar from the build stage. A future edit could copy the whole `target/` directory (including test classes, dependency jars, or build metadata) into the runtime image and still pass the current checks.
- **Evidence:** `services/payment/Dockerfile` line 21; `T01SkeletonRegressionTest.java` lines 280-281.
- **Recommendation:** Add an assertion that exactly one `COPY --from=build` line exists and that it ends with `/payment-service.jar app.jar`.
- **Confidence:** Low — the current Dockerfile is correct; this is a regression guard.

---

## Finding 3 — The guard test does not verify `ENTRYPOINT` form or target

- **Issue:** The guard does not check that the final stage has a JSON-form `ENTRYPOINT` pointing at `app.jar`. A shell-form `ENTRYPOINT java -jar app.jar` would fail at runtime in distroless (no shell), but the failure would only surface in AC2/AC4, not in the committed unit-test guard.
- **Evidence:** `services/payment/Dockerfile` line 24; `T01SkeletonRegressionTest.java` lines 274-278.
- **Recommendation:** Add an assertion such as `assertThat(lines).contains("ENTRYPOINT [\"java\", \"-XX:MaxRAMPercentage=75.0\", \"-jar\", \"app.jar\"]");`.
- **Confidence:** Low — AC2/AC4 would catch a malformed entrypoint, but the guard test is meant to catch Dockerfile regressions early.

---

## Finding 4 — The guard test does not ban `ARG` or `LABEL` as secret vectors

- **Issue:** This aligns with Phase 7 self-review Finding S2. `ARG` values appear in `docker history`, and `LABEL` values are persisted in image config. Neither is banned by the current guard, so a `--build-arg SECRET=…` or `LABEL secret=…` would pass the test and violate L13.
- **Evidence:** `T01SkeletonRegressionTest.java` lines 282-283.
- **Recommendation:** Add `ARG` and `LABEL` to the banned-instruction check (case-insensitively, per Finding 6 below), or explicitly document that the CI pipeline must never pass secret build args/labels.
- **Confidence:** Low — the Dockerfile today has neither instruction.

---

## Finding 5 — The build stage compiles test sources unnecessarily

- **Issue:** `RUN mvn -q -pl services/payment package -DskipTests` still compiles `src/test/java`. The Dockerfile therefore copies the entire `services/payment/src` tree (including tests) into the build stage, pulling in test-only dependencies and enlarging the build context/layers. The final image is unaffected, but the build is slower and less clean than it could be.
- **Evidence:** `services/payment/Dockerfile` lines 16-17.
- **Recommendation:** Either switch to `mvn package -Dmaven.test.skip=true` and copy only `src/main`, or keep the current approach and document that test sources are intentionally included so the build stage can compile them. The latter is simpler and matches the sibling.
- **Confidence:** Low — build-time only; no runtime impact.

---

## Finding 6 — The guard test is case-sensitive for all Dockerfile instructions

- **Issue:** This expands Phase 7 self-review S1. The test uses `startsWith("FROM ")`, `startsWith("ENV ")`, `startsWith("ADD ")`, and `contains("COPY " + module + "/pom.xml ...")`. Docker treats instructions case-insensitively, so `from`, `env`, `add`, and `copy` would evade detection.
- **Evidence:** `T01SkeletonRegressionTest.java` lines 272, 277, 281, 283, 295.
- **Recommendation:** Normalise the instruction keyword (upper-case the first token) before all checks, or use `(?i)` regexes.
- **Confidence:** Low — the current Dockerfile uses upper-case instructions, but the guard should not rely on convention.

---

## Finding 7 — The guard test does not assert that `USER nonroot` appears only in the runtime stage

- **Issue:** The test takes the sublist from the final `FROM` to the end and checks it contains `USER nonroot`. It does not verify that the build stage lacks `USER nonroot` or contains a different user. A `USER nonroot` in the build stage plus a missing `USER` in the final stage would be caught only if the final sublist lacks it, but if both stages had it, the test would pass even though the final stage might not need it.
- **Evidence:** `T01SkeletonRegressionTest.java` lines 274-278.
- **Recommendation:** Check that the final stage has exactly one `USER nonroot` and that the build stage has no `USER nonroot` (or use `lastIndexOf` as self-review S4 suggests and then verify the runtime stage).
- **Confidence:** Info — cannot happen today because the build stage image has no nonroot directive.

---

## Finding 8 — Base images are pinned by tag, not by digest

- **Issue:** This aligns with Phase 7 self-review S6. `maven:3.9-eclipse-temurin-21` and `gcr.io/distroless/java21-debian12:nonroot` can drift. Reproducibility and supply-chain hardening would improve with SHA-pinned digests.
- **Evidence:** `services/payment/Dockerfile` lines 8 and 19.
- **Recommendation:** Record as accepted tech debt (the sibling pattern is frozen) and let the infra workstream decide when to pin digests across all services.
- **Confidence:** Info — accepted trade-off.

---

## Summary

T27's Dockerfile is secure and follows the Deployment rule. The guard test in `T01SkeletonRegressionTest` is a good addition, but it can be made more robust with the low-cost assertions in Findings 1–4 and 6. Findings 5, 7, and 8 are build cleanliness or documentation notes. No production code changes are needed.
