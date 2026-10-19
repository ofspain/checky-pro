# payment · T27 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

This artifact contains no code; it is documentation only.

**Branching:** as with every prior task in this pipeline, the work ran on the long-running
`spec/service-specs-and-ai-framework` branch. `main` was never touched and stays deployable.

**Trailer:** the template names "Claude Opus 4.8 (1M context)". The trailer below names the
model that actually did the work, following the T23–T26 precedent.

## Commit title

`payment T27: Dockerfile and full-suite gate (verify 497/497, repo-root image build)`

## Commit message

```
payment T27: Dockerfile and full-suite gate (verify 497/497, repo-root image build)

Add services/payment/Dockerfile, built from the repo root.
- Build stage: maven:3.9-eclipse-temurin-21. It copies the root pom and
  all four module poms, because Maven validates the whole reactor even
  with -pl. Then it runs go-offline and package -DskipTests.
- Runtime stage: gcr.io/distroless/java21-debian12:nonroot, receiving
  only the repackaged jar. It sets USER nonroot and EXPOSE 8083, and uses
  an exec-form java entrypoint. There is no ENV, no ARG and no copied
  config (L13).

Add a committed guard to T01SkeletonRegressionTest. It reads the module
list from the root pom, so a new module fails the test until its pom is
copied: the bug class that broke the sibling Dockerfiles. It checks
keywords case-insensitively and asserts the following:
- the distroless final stage, with exactly one USER nonroot
- an instruction allowlist for the runtime stage
- exactly one jar COPY --from=build, and the exec-form ENTRYPOINT
- a single EXPOSE 8083
- no ADD, no whole-context COPY, and no ENV/ARG

Verified:
- mvn -pl services/payment verify passes 497/497
- docker build succeeds (157 MB, nonroot, no shell, no credential in Env)
- under --read-only --tmpfs /tmp the container reaches /actuator/health UP,
  and all four chain.tx.* listeners are assigned partitions

No production code or pom changed.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed

- **Build/deploy:** `services/payment/Dockerfile` (new).
- **Tests:** `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`.
  It gains the test `paymentDockerfileHonoursBuildContextAndSecurityRules`, plus two private
  helpers and one constant. The class goes from 8 to 9 tests, and the suite from 496 to 497.
- **Production code and poms:** none changed.
- **Pipeline artifacts:** `.ai/prompts/payment/T27/artifacts/00`–`13`, including
  `11-test-review-resolution.md`.

## Summary

T27 is payment-service's build gate. Before this task the service had no Dockerfile. It now
ships an image that meets the `agents.md` Deployment rule: multi-stage, distroless JRE 21,
non-root, and proven on a read-only rootfs. It also meets L13: no secret in any layer, nor in
the image history.

**Human decisions that shaped it:**

- **Phase 4 (Kimi Phase 3):**
  - **Smoke start:** the start-up test must prove Kafka partition assignment, not just a
    health of `UP`.
  - **Committed guard:** a committed guard test replaced Kimi's uncommitted script.
  - **Siblings and JWKS:** the broken sibling Dockerfiles and the hard-coded JWKS URI were
    logged as follow-ups.
- **Phase 9 (Kimi Phase 8):**
  - **Matching:** case-insensitive keyword matching.
  - **Bans:** `ARG` is banned and `LABEL` allowed; the whole-context `COPY` check is broadened.
  - **Required lines:** the root pom copy, the exact jar copy and the exact entrypoint.
  - **Not changed:** test-source compilation, digest pinning and `.dockerignore` are left
    documented, not fixed.
- **Phase 11 (Kimi Phase 11):**
  - **Runtime stage:** exactly one `USER nonroot`; a single `EXPOSE` in the runtime stage; an
    instruction allowlist for the runtime stage.
  - **Manual gate:** the Docker build and smoke start stay manual gate evidence.

Kimi's accuracy, checked against source before each decision:
- **Phase 8 F6** was partly wrong. The module-pom check was over-strict, not bypassable.
- **Phase 11 Gap 5** was already covered.

**Open follow-ups (human-owned, out of T27 scope):**

1. **FU-T27-1.** The auth, crypto and notification Dockerfiles fail `docker build`, because they
   don't copy every module pom. Notification broke when payment joined the reactor. These are
   deploy blockers for those services.
   **Fix:** copy all module poms, plus the same root-pom-derived guard test.
2. **FU-T27-2.** `jwk-set-uri` is hard-coded to `localhost:8080` in payment, crypto and
   notification. Deployments must set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI`
   until an `${AUTH_JWKS_URI:…}` placeholder is added.
3. **Build-hardening notes.** These go to the infra workstream:
   - base images are tag-pinned, not digest-pinned
   - there is no `.dockerignore`
   - the build stage compiles test sources
   - AC2–AC4 are not CI-automated
4. **Earlier follow-ups, still open:**
   - **G2:** partition-key drift.
   - **`agents.md:39`:** model generation.
   - **`tokenDecimals`:** a primitive.
   - **FU-1:** the layering rule.
   - **FU-2:** the `@PreAuthorize` sweep.
   - **Crypto-service:** `unregisterWatch` stops tracking matched transactions.

## Testing performed

- **Full suite.** `mvn -o -pl services/payment verify` from the repo root passes **497/497**
  with `BUILD SUCCESS` and the repackaged jar. It was run at Phase 6, Phase 10 and Phase 11.
- **Image build.** `docker build -f services/payment/Dockerfile -t payment-service .` exits 0;
  the image is 157 MB.
- **Image inspect.**
  - `User=nonroot`, `ExposedPorts` 8083/tcp, exec-form entrypoint.
  - `Env` holds only `PATH`, `SSL_CERT_FILE`, `LANG` and `JAVA_VERSION`.
  - `docker run --entrypoint sh` fails, so there is no shell.
- **Read-only smoke start.**
  - **Setup:** a throwaway network, `postgres:16-alpine` (Flyway-migrated) and
    `apache/kafka:3.8.0` KRaft with network-scoped listeners.
  - **Run:** the image ran under `--read-only --tmpfs /tmp`.
  - **Result:** `/actuator/health` reached `UP` in about 7 s. All four `chain.tx.*` listeners
    logged `partitions assigned` for group `payment-service`. Grepping the log for
    `ERROR`/`WARN` returned nothing (excluding the transient first-join `NOT_COORDINATOR` lines).
  - **Teardown:** everything was removed. The developer's `auth-postgres-1` and `auth-kafka-1`
    were untouched.
- **Guard mutation checks.** Each Dockerfile mutation was temporary, and `cmp` confirmed the
  restore.

  | Phase | Mutation | Effect |
  |---|---|---|
  | 6 | Remove a module-pom `COPY`; add `ENV` | Both fail |
  | 9 | Lowercase `env`; `ARG`; `COPY --chown . .`; lowercase `add`; root pom removed; `target/` copied; shell-form entrypoint; lowercase or wrong final `FROM` | All fail on their intended assertions |
  | 11 | Later `USER root`; `RUN` or `VOLUME` in runtime; `EXPOSE` moved to build; a second `EXPOSE` | All fail |
  | 11 | `LABEL` | Passes, as decided |

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 27 (`:48`).
- **Requirements:** none scoped. The 497-test run is the regression evidence for R1–R31; R26 is
  deferred.
- **LOCKED decisions honoured:**
  - **L13:** enforced by the test and by the image inspect.
  - **L3:** continuity; the dependency set is unchanged.
- **Standing rules:** `agents.md:60-61` (Deployment).
- **Follow-up provenance:** frozen brief FU-T27-1 and FU-T27-2.
