# crypto · T27 · Phase 0 — Repository Understanding

## 1. Architecture summary

This task's scope is the entire `crypto-service` module (T02-T26's combined output) plus, per its own
literal wording ("Docker image builds from repo root"), the monorepo's build tooling for packaging it.
Unlike every prior task, T27 authors no new domain code — it is a verification gate over everything
already built, plus (per a genuine gap found this phase) one missing piece of packaging infrastructure.

## 2. Existing code this task touches

Everything: all 12 packages, all Flyway migrations, all ArchUnit rules, the full test suite (`mvn -pl
services/crypto verify`). No individual class is "new" to this task; the task's own subject is the
build's aggregate result.

**Genuinely new finding this phase: no `services/crypto/Dockerfile` exists anywhere in the repository.**
`services/auth/Dockerfile` does exist (confirmed present, read in full) — a working, multi-stage,
distroless, non-root build matching `agents.md`'s own "Deployment" standing rule exactly (`FROM
maven:3.9-eclipse-temurin-21 AS build` → `FROM gcr.io/distroless/java21-debian12:nonroot`, `USER
nonroot`, built from the repo root via `docker build -f services/auth/Dockerfile -t auth-service .`).
Auth's own equivalent task (its `tasks.md` task 37: "Run full test suite. `mvn -pl services/auth verify`
must pass. Docker image must build from repo root.") has the identical wording to crypto's T27 — no
other task in either service's list explicitly authors a Dockerfile, and none exists for crypto today.
This strongly suggests authoring `services/crypto/Dockerfile` is this task's own, otherwise-unclaimed
scope, mirroring auth's structure.

## 3. Established patterns to follow

- **Auth's own `Dockerfile`** is the direct, working precedent to mirror: multi-stage (a `maven`-image
  build stage producing the jar via `mvn package -DskipTests`, copying only `pom.xml`s first for layer
  caching, then `src/`), a `gcr.io/distroless/java21-debian12:nonroot` runtime stage, `USER nonroot`,
  `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]`. `agents.md`'s own "Deployment"
  rule (crypto's, unchanged from what auth already satisfies): "Multi-stage Docker → distroless JRE 21,
  non-root, read-only rootfs."
- **`mvn -pl services/crypto verify`'s actual lifecycle** — confirmed via direct `pom.xml` inspection:
  no `maven-failsafe-plugin` is bound anywhere in this module or the parent POM, so `verify` triggers
  nothing beyond what `test` already does (Surefire) plus the `package`-phase
  `spring-boot-maven-plugin:repackage` goal (producing the runnable, executable jar) that phase order
  already includes. This means the compile/package/unit-test portions of `mvn verify` are fully
  achievable in this environment right now, independent of Docker; only the Testcontainers-based
  integration tests within that same `test` phase, and any actual Docker image build, are blocked by
  Docker's unavailability.

## 4. Testing conventions

Unchanged from every prior task: unit (Docker-free) → ArchUnit + contract (Docker-free) → integration
(Testcontainers: Postgres + Kafka). `mvn -pl services/crypto verify` runs literally everything in one
invocation — this task's own job is to confirm that full, undifferentiated run succeeds, not to add or
change any individual test.

## 5. Known gaps / unknowns

- **Docker is not available in this environment** (confirmed via `docker info`, consistent with every
  prior task since T23). This directly blocks two distinct things `mvn -pl services/crypto verify`
  needs: the Testcontainers-based integration tests (confirmed throughout this session: 0 real
  failures, a consistent set of Docker-unavailability errors), and — independently — any actual `docker
  build` invocation for the image-build half of this task's own acceptance bar.
- **No `services/crypto/Dockerfile` exists.** Whether authoring one is genuinely this task's own scope
  (as auth's identical precedent strongly suggests) or was assumed to be handled by infrastructure work
  outside this AI-driven task list (`agents.md`: "Infra is AWS CDK (TypeScript)") is not something Phase
  0 should resolve unilaterally — flagged here for Phase 1/2 to decide explicitly, with auth's own
  precedent as the strongest available evidence for "yes, this task's scope."
- I do not know whether this environment will ever have Docker available before this task needs to be
  considered complete, or whether the actual `mvn verify`/`docker build` runs are expected to happen in
  a different environment (e.g., CI, or the user's own machine) with this task's own artifacts limited
  to preparing everything correctly-by-construction and disclosing what could not be executed here —
  the same posture T26 already established and disclosed at every one of its own phases.
