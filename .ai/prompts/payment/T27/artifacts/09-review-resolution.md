# payment · T27 · Phase 9 — Review Resolution

Consumes:
- `artifacts/07-self-review.md` (S1–S7)
- `artifacts/08-independent-review.md` (Kimi F1–F8)

**Human sign-off:** 2026-10-19, via the Phase 9 gate. Every recommended option was chosen.

Each Kimi finding was checked against source before it went to the gate. Two corrections:
- **F6 is partly inaccurate.** It lists the `contains("COPY " + module + …)` check (line 295) as
  an evasion. A lowercase `copy` line there makes the test *fail*, so it is over-strict, not
  bypassed. The bypass applies only to the `FROM`, `ENV` and `ADD` checks. Normalising the
  keyword fixes both directions.
- **F7's scenario is muddled.** A `USER nonroot` in the build stage is harmless. The real gap
  is S4: `indexOf` would pick the wrong stage if two `FROM` lines were identical. F7 is resolved
  through S4.

## Resolution log

| Comment | Decision | Reason | Change made |
|---|---|---|---|
| **S1 / F6**: case-sensitive keywords | **ACCEPTED** | Docker keywords are case-insensitive. | `stripDockerfileComments` now upper-cases each line's first token (`Locale.ROOT`), so every check sees the canonical form. Its Javadoc says why. |
| **S2 / F4**: `ARG` / `LABEL` | **ACCEPTED: `ARG` only** | `ARG` values persist in `docker history` (L13). `LABEL` stays allowed, because CI commonly adds non-secret OCI labels. | The banned set is `ENV` or `ARG`, with the assertion message updated. This extends frozen D4, with human approval. |
| **S3**: narrow broad-copy regex | **ACCEPTED** | `COPY --chown=x . .` and `COPY ./ .` were missed. | New helper `copiesWholeContext(String)`. For a `COPY`, it drops the `--flag` tokens and the destination, then flags any source equal to `.` or `./`. The `ADD` ban is now its own assertion. |
| **S4 / F7**: `indexOf` stage window | **ACCEPTED** (F7 via S4) | The window must start at the actual last `FROM`. | `lines.indexOf(finalFrom)` became `lines.lastIndexOf(finalFrom)`, held in a `runtimeStage` local. |
| **F1**: root pom `COPY` not asserted | **ACCEPTED** | Verified: lines 285–296 checked only the module poms. | Added `contains("COPY pom.xml ./")`. |
| **F2**: jar copy not pinned | **ACCEPTED** | A `COPY --from=build …/target/ ./` would have passed. | The runtime stage's `COPY --from=` lines must be exactly `COPY --from=build /workspace/services/payment/target/payment-service.jar app.jar`. |
| **F3**: `ENTRYPOINT` form | **ACCEPTED** | A shell-form entrypoint fails at runtime on distroless, which has no shell. | The runtime stage must contain the exact exec-form `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]`. |
| **S5**: fully-qualified `java.util` names | **ACCEPTED** | Readability, in a method already being edited. | Imported `java.util.ArrayList`, `Arrays`, `List` and `Locale`. No fully-qualified `java.util.` names remain. |
| **F5**: test sources compiled in the build stage | **REJECTED (documented)** | Frozen D1 mirrors notification's Dockerfile. The cost is build-time only, since the runtime image receives just the jar. | None. Carried to the PR notes. |
| **S6 / F8**: tag-pinned base images | **REJECTED (documented)** | Matches the sibling pattern (D1). Digest pinning belongs to the infra workstream, across all services. | None. Carried to the PR notes. |
| **S7**: no `.dockerignore` | **REJECTED (documented)** | The TIB scope excludes it, and only explicit paths are copied. | None. Carried to the PR notes. |

## Files changed in Phase 9

- `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java` only.
  It still has one test for T27. The changes:
  - new private helper `copiesWholeContext`
  - `stripDockerfileComments` now normalises keywords
  - imports added
- The Dockerfile is unchanged, and so is all production code.

## Verification

- `mvn -o -q test -Dtest=T01SkeletonRegressionTest`: **9/9 pass** on the real Dockerfile.
- **Mutation proof.** Each mutation was applied alone to a copy of the Dockerfile, then
  restored, with `cmp` OK and a clean re-run returning exit 0. Every mutation fails on its
  intended assertion:

| Mutation | Failing assertion |
|---|---|
| `env FOO=bar` (lowercase) | L13: no ENV or ARG |
| `ARG SECRET` | L13: no ENV or ARG |
| `COPY --chown=1000 . .` | only explicit paths are copied |
| `add x.tar /x` (lowercase) | ADD is never used |
| remove `COPY pom.xml ./` | the root pom is in the build context |
| jar copy replaced by `…/target/ ./` | the runtime stage takes only the repackaged jar |
| `ENTRYPOINT java -jar app.jar` (shell form) | exec-form ENTRYPOINT |
| `from gcr.io/distroless/java21-debian12:latest` (lowercase) | the runtime stage is distroless JRE 21, non-root |

The Phase 6 mutations (removing the crypto pom `COPY`, adding `ENV FOO=bar`) still hit the
same assertions, which are unchanged. The suite total is unchanged at 497, because no test was
added. The full-suite re-run is Phase 10's evidence.

## Open Questions

None.
