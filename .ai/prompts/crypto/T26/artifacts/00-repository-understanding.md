# crypto · T26 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` (`com.themistra.crypto`) is a Spring Boot 3.5.4 / Java 21 service, package-by-feature
across 11 feature modules + `common`. One Postgres schema (`chain`), Flyway migrations, Kafka via a
transactional outbox (`OutboxEvent` → `OutboxRelay` → real Kafka producer), ArchUnit-enforced module
boundaries (T25). Security is pure resource-server (T03): internal endpoints require
`internal.crypto:write`; the well-known verification-keys endpoint is public. `CryptoServiceApplication`
(a real, bootable `@SpringBootApplication` class) exists at the package root.

The full request-to-event pipeline this task must exercise end-to-end: `WatchController` (register) →
`WatchService`/`Watch` (persist) → `Watcher` (per-watch, subscribes to `ProviderSet.NamedAdapter`s,
correlates provider answers, calls `QuorumDecisionService`) → `TxLifecyclePublisher`
(`seen`/`confirmed`/`finalized`, via `OutboxPublisher`) → `ReorgDetector` (`reorged`) →
`AttestController`/`AttestationService` (gates on quorum+finality+screening, calls `KmsSigner`).

## 2. Existing code this task touches

Every module in the pipeline above already exists and is fully built (T02-T25). No new production
code is anticipated — this task is purely a new, real-infrastructure test proving the already-built
pieces work together, not in isolation. Nothing analogous to this task exists yet: every current
integration test (8 total, all `@SpringBootTest` + a single `@Container PostgreSQLContainer`, no
shared base class) exercises exactly one repository/module in isolation; none boots the pipeline
end-to-end, and none uses a real Kafka broker.

## 3. Established patterns to follow

- **`@SpringBootTest` + `@Testcontainers` + a static `@Container PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))`**
  is the established per-test-class pattern (8 existing precedents, e.g. `WatchRepositoryIntegrationTest`)
  — no shared abstract base class exists; each test class declares its own container(s) independently.
- **`org.testcontainers:kafka` is already a declared test-scope dependency** (`pom.xml:99`) but
  **is not used by any existing test** — this task would be the first to actually spin up a real
  `KafkaContainer`. No new dependency needed, but no precedent to mirror for the Kafka side either.
- **LocalStack precedent for KMS** exists: `KmsSignerLocalStackIntegrationTest`,
  `ObservationSnapshotStoreLocalStackIntegrationTest` — `org.testcontainers:localstack` is also already
  a declared dependency (`pom.xml:103`).
- **`FakeChainAdapter`** (test-only, `adapter` package) is the established, sole mechanism for
  simulating provider behavior in any test — `agents.md`: "real RPC providers are never called in
  tests or CI." `WatcherTest`'s own 3-adapter harness (mocked collaborators) is the closest existing
  precedent for orchestrating multi-provider agreement/disagreement scenarios, though it mocks
  `Watcher`'s collaborators rather than using a real Spring context.
- **Fixed `Clock`** throughout, injected, never wall-clock time in tests.

## 4. Testing conventions

Unit (plain JUnit, fixed `Clock`, `FakeChainAdapter`) → ArchUnit + contract → integration
(Testcontainers: Postgres + Kafka, still fake providers, never real RPC) — `agents.md`'s own stated
ladder, with this task being the first to actually reach the full "integration" rung as originally
envisioned (real Postgres AND real Kafka together, in one real Spring context).

## 5. Known gaps / unknowns

- **Docker is not available in this environment right now** (confirmed via `docker info`) — this
  task's entire premise (Testcontainers Postgres + Kafka, and likely LocalStack for a real signature)
  cannot be executed in this environment as currently provisioned. This is not a code gap, but it
  directly blocks verifying this task's own acceptance criteria here; flagged now so it isn't
  discovered only after implementation is complete.
- **The 4th flow ("sanctioned counterparty → attest `BLOCKED`") cannot be exercised through the real,
  currently-wired `ScreeningClient` bean at all.** Verified directly against source:
  `FailClosedScreeningClient` (the only production `ScreeningClient` implementation) always returns
  `ScreeningOutcome.ERROR` for every input, by deliberate design (no real vendor chosen yet, `package.md`
  §11 Q2 still open) — it structurally can never return `BLOCKED` or `CLEARED`. No fake/test-double
  `ScreeningClient` exists anywhere in the test tree today (confirmed via search — only
  `FailClosedScreeningClientTest`, the real class's own unit test, exists). To exercise this flow, a
  test-scoped `ScreeningClient` bean override that can genuinely return `BLOCKED` for a configured
  address will be needed — a new test fixture, not existing anywhere yet. This is architecturally
  reachable (quorum+finality can be driven to `AGREED` via `FakeChainAdapter`s before screening is ever
  consulted, per T21's own gating order), just not via the bean that ships in production today.
- **Whether a shared Testcontainers base class should be introduced for this task**, given no
  precedent exists and 8 other tests already each declare their own container independently — a Phase
  2/5 design/proportionality decision, not resolved here.
- **Whether attest's signature step should hit a real LocalStack KMS (mirroring
  `KmsSignerLocalStackIntegrationTest`) or the existing mocked-`KmsSigner` pattern `AttestationServiceTest`
  uses** — the task statement says "attest returns a signature," which reads as wanting the real path;
  a Phase 2 decision, not resolved here.
- I do not know whether Kafka message consumption/assertion (proving an event was genuinely published
  to the real topic, not just that the outbox row was written) is expected to be part of this task's
  own scope, or whether asserting the outbox row + `OutboxRelay`'s successful send (already covered at
  the unit level by `OutboxRelayTest`) is sufficient — a Phase 1/2 scoping question.
