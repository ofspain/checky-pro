<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T37 · Phase 0 — Repository Understanding

Task statement: `mvn -pl services/auth verify` must pass. Docker image must build from repo root.

---

## 1. Architecture summary

Spring Boot 3.5.4 / Java 21, package-by-feature under `com.themistra.auth`: `account`, `authn`,
`authz`, `audit`, `token`, `mfa`, `apikey`, `events`, `cleanup`, `ratelimit`, `admin`, plus shared
`common` — 11 feature packages under `src/main`, matching `ArchitectureTest`'s `FEATURE_MODULES`
list plus `admin` (a controller-only package, not independently entity-owning). PostgreSQL (schema
`auth`), 8 Flyway migrations (`V1`-`V8`, 351 lines total). Kafka outbox: `OutboxPublisher` writes
in-transaction, `OutboxRelay` polls and relays to 3 topics (`auth.user.lifecycle`,
`auth.security.audit`, `auth.email.requested`) via `EventTopics`'s fixed aggregate-type→topic map.
Spring Authorization Server 1.5.1 is the OIDC/OAuth2 issuer; zero-trust resource-server posture with
an exhaustive, CI-enforced public-endpoint allowlist. `services/auth/Dockerfile`: multi-stage build
(`maven:3.9-eclipse-temurin-21` → `gcr.io/distroless/java21-debian12:nonroot`), its own header
comment documents the exact command: `docker build -f services/auth/Dockerfile -t auth-service .`
from the repo root (needed because the build stage `COPY`s the parent POM too — a monorepo build).

## 2. Existing code this task touches

This task adds no new feature code — its "existing code" is the entire `services/auth` test suite
and the `Dockerfile` itself. `mvn -pl services/auth verify` runs `test-compile` → `test` (Surefire)
→ `package` → any bound `verify`-phase checks (none beyond the default lifecycle in this module's
`pom.xml`, confirmed no separate integration-test/Failsafe plugin binding exists — Testcontainers
tests run under the ordinary `test` phase via Surefire like every other test in this module).

**A full run was performed as part of this phase's own grounding** (not simulated or assumed):

```
mvn -pl services/auth verify
Tests run: 702, Failures: 1, Errors: 8
```

The 9 failing/erroring tests fall into three independently-diagnosed groups, none newly introduced
by this phase's own investigation — all match already-logged, already-understood issues from earlier
in this session:

| Group | Tests | Root cause | First noted |
|---|---|---|---|
| **A — Kafka producer→broker environment issue** | `EndToEndLifecycleIntegrationTest` (T36), `AccountPersistenceIntegrationTest` | App's Kafka producer cannot reach the Testcontainers broker in this environment (`Bootstrap broker localhost:9094 ... could not be established`); confirmed reproducible, not a code defect | Logged during T36 (2026-08-22), reproduced again just now |
| **B — full-suite-only response-null flakiness** | `ApiKeyLifecycleIntegrationTest.shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle`, `ApiKeyExchangeIntegrationTest` (3 methods) | `TestRestTemplate` calls returning a null response body only under full-suite load, not in isolation — likely resource contention from Group A's constant producer reconnect-retry loop, not yet confirmed | Logged during T31 (2026-08-17) as "unrelated to T31, not yet root-caused" |
| **C — pre-existing FK-violation bug** | `AuditTrailIntegrationTest.recordMirrorsToKafkaWithoutLeakingIpOrUserAgent`, `RoleAssignmentIntegrationTest` (2 methods) | Fabricated `UUID.randomUUID()` test principals violate `auth_audit.account_uuid`'s real FK to `accounts` — never registers a real account first, unlike the established `registerAndActivate` pattern every newer integration test uses | Logged during T31 (2026-08-17), confirmed still present, unfixed |

**Docker image build**: performed as part of this phase's own grounding —
`docker build -f services/auth/Dockerfile -t auth-service-t37-check .` from the repo root.
**Succeeded** (exit code 0, 459MB image built and exported). The `mvn dependency:go-offline` build
stage took ~485s (cold Maven cache inside the container, not a hang); the `package -DskipTests` stage
itself took ~16s. Test image removed after confirmation (`docker rmi`) — not left behind. This half
of T37's literal criterion is **already met**, independent of the test-suite failures below.

## 3. Established patterns to follow

- **The FK-violation bug in Group C has an exact, already-proven fix pattern used repeatedly this
  session**: register a real account via `AccountService.register`/`.activateEmail` (or the HTTP
  equivalent) before using its UUID as an audit/role-assignment principal, matching
  `SessionIntegrationTest`/`CleanupIntegrationTest`/every newer integration test's own
  `registerAndActivate` helper. The two affected files (`AuditTrailIntegrationTest`,
  `RoleAssignmentIntegrationTest`) predate this pattern's establishment.
- **Group A (Kafka environment issue) has no known code-level fix** — confirmed no hardcoded
  `bootstrap-servers` override, no stale/lingering containers (`docker ps -a`); this is Docker/host
  networking, not something a `services/auth` code change addresses.
- **Group B (null-response flakiness) has never been root-caused** — noted at T31 as observed but
  not investigated; today's fresh run is the first time a plausible connection to Group A's producer
  contention has been drawn, not yet confirmed.

## 4. Testing conventions

Per `agents.md`: unit (plain JUnit, fixed `Clock`, no Spring context) → ArchUnit + contract →
integration (Testcontainers: Postgres + Kafka) → image build → gitleaks/dependency scan. This task
sits at the "run everything, plus image build" level — it doesn't add new tests or new conventions,
it exercises every existing one at once. Separately tracked, still-open issue (found T32): ArchUnit's
own JUnit5 engine does not execute under this project's Surefire setup — rules with a canary test
(the pattern established since T32) are still enforced; rules without one are not, a distinct gap
from any of Groups A/B/C above.

## 5. Known gaps / unknowns

- **I do not know whether Group B's flakiness is genuinely caused by Group A's producer contention**
  or is an independent, coincidentally-timed issue — this is a real open question, not a confirmed
  finding, and should not be assumed correct without further isolation testing if it becomes relevant
  to this task's scope.
- **The task statement's own literal bar ("`mvn -pl services/auth verify` must pass") is currently
  not met** — 1 failure + 8 errors, as documented above. Whether T37's scope is "fix everything until
  green" or "run, document, and gate-decide what's in/out of scope" is a Phase 1/2/4 question, not
  decided here.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
