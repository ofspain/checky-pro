# payment · T27 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the final `T01SkeletonRegressionTest.paymentDockerfileHonoursBuildContextAndSecurityRules` against the frozen acceptance criteria.

---

## Gap 1 — No committed test replays AC2–AC4 (Docker build, image inspect, read-only smoke start)

- **Why it matters:** The suite passes 497/497, but the Dockerfile could still fail to build, the image could have the wrong user/port, or the container could fail under `--read-only` on a different machine. Those checks are manual gate evidence, not part of the automated regression suite.
- **Suggested test:** Add a new test class tagged with a Docker/Testcontainers marker that, when enabled, runs the build and inspects the resulting image. If Docker-in-Docker is unavailable in CI, keep it as an opt-in integration test and document that AC2–AC4 remain manual in the current environment.

---

## Gap 2 — `USER nonroot` is not asserted to be the final/only `USER` in the runtime stage

- **Why it matters:** The guard checks that the runtime stage contains `USER nonroot`, but it does not check that no later `USER` instruction (e.g., `USER root`) overrides it. A malicious or accidental later user switch would pass the test.
- **Suggested test:** Assert that the runtime stage has exactly one `USER` instruction and that it is `USER nonroot`.

---

## Gap 3 — `EXPOSE 8083` is not asserted to be in the runtime stage

- **Why it matters:** The guard checks `lines.contains("EXPOSE 8083")` anywhere in the Dockerfile. If the build stage exposed port 8083 and the runtime stage exposed a different port (or none), the test would still pass.
- **Suggested test:** Assert that the runtime stage contains `EXPOSE 8083` and that no other `EXPOSE` instruction appears in the Dockerfile.

---

## Gap 4 — No ban on `RUN`, `SHELL`, `VOLUME`, `LABEL`, or `STOPSIGNAL` in the runtime stage

- **Why it matters:** Distroless images should not run package-manager commands or redefine the shell. A `RUN` or `SHELL` in the final stage would fail at runtime, but the guard does not fail at test time. `LABEL` could leak metadata, and `VOLUME`/`STOPSIGNAL` could violate the intended deployment shape.
- **Suggested test:** Add a runtime-stage assertion that the only allowed instructions are `FROM`, `WORKDIR`, `COPY`, `USER`, `EXPOSE`, and `ENTRYPOINT`.

---

## Gap 5 — No verification that the build stage is named `build`

- **Why it matters:** The runtime-stage assertion checks `COPY --from=build ...`. If the build stage were renamed (e.g., `AS builder`) and the copy were updated to match, the test would still pass. That is fine functionally, but the guard's intent is to enforce the sibling pattern.
- **Suggested test:** Optional — assert the first `FROM` line is followed by `AS build`. This is a style/consistency guard, not a correctness one.

---

## Gap 6 — No test asserts the Dockerfile does not copy `src/test` into the build stage

- **Why it matters:** The current Dockerfile copies `services/payment/src` (main and test) and runs `mvn package -DskipTests`. Test sources and test-only dependencies therefore enter the build stage. The final image is unaffected, but this widens the attack surface of the build environment and enlarges the build context.
- **Suggested test:** Either assert that the only `COPY` of source is `services/payment/src/main`, or assert that the `mvn` command uses `-Dmaven.test.skip=true` (which makes test sources unnecessary). The latter is simpler and more robust.

---

## Gap 7 — The follow-up for broken sibling Dockerfiles is not tracked as an open test/CI item

- **Why it matters:** The guard test references FU-T27-1 (auth, crypto, notification Dockerfiles broke when payment was added). The Phase 10 manifest says "Open Questions: None," but the follow-up is real and affects the same build-context invariant.
- **Suggested test:** Add a note to the manifest's Open Questions, or add a skeleton failing test in each sibling module that mirrors `paymentDockerfileHonoursBuildContextAndSecurityRules` once those modules are in scope. Until then, record the follow-up explicitly.

---

## Summary

T27's committed guard test is thorough after Phase 9: it covers the runtime base image, user, jar copy, entrypoint, build context, and secret instructions. The highest-value additions are Gap 1 (automating the Docker build/image inspect smoke) and Gap 2 (ensuring `USER nonroot` is final). Gaps 3–7 are smaller hardening or documentation notes that would make the test suite fully self-describing.
