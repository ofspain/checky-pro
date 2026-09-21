# crypto · T28 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/crypto` (Spring Boot 3.5.4, Java 21) is Themistra's attestation engine: it watches
Ethereum/Tron for a registered payment, arbitrates 2-of-3 provider quorum on every fact, applies
per-chain finality policy, and signs an attestation via KMS once quorum + finality (+ screening) pass.
No single provider's answer is ever emitted as fact (L1) — the service's entire reason to exist.

**Modules** (`com.themistra.crypto`, 11 feature packages + `common` + `adapter`): `adapter` (Ethereum/Tron
chain clients, `ChainAdapter` interface, `ProviderSet`), `watch` (watch registration, `Watcher`,
`WatcherRegistry`, `Watch` entity), `quorum` (`QuorumEvaluator`, `QuorumDecisionService`,
`ProviderAnswer<T>`), `observation` (`ObservationLog`, `ObservationSnapshotStore` — S3 WORM verbatim
snapshot), `finality` (per-chain `FinalityPolicy` implementations), `reorg` (`ReorgDetector`, cursor
walk-back), `attest` (`AttestationService`, `KmsSigner` — the only class permitted to call `kms:Sign`),
`token` (`TokenAllowlist`, `AddressValidator`, `AddressPoisoningDetector`), `provider`
(`ProviderHealthTracker`, degraded-provider publishing), `screening` (sanctions/counterparty screening,
fail-closed), `events` (outbox pattern, `OutboxRelay`, Kafka), `common` (shared config, security,
ArchUnit boundary tests).

**Persistence:** Postgres via JPA + Flyway (DDL-only migrations, Hibernate validates against them —
D-005). Entities are largely package-private within their own feature module, accessed from other
packages (when legitimately needed) via `EntityManager`/JPQL rather than autowiring a package-private
repository — an established, repeated pattern (T25/T26), not an oversight.

**Events/outbox:** every emitted fact goes through an outbox table + `OutboxRelay` → Kafka, never a
direct publish inside the same transaction as the domain write (mirrors auth's own D-009 outbox
convention). Every emitted event carries a deterministic idempotency key `chain:txhash:eventtype` (L5).

**Security:** OAuth2 resource server only (never an issuer) — validates auth-issued service-to-service
JWTs; `/internal/v1/watches` and `/attest` both require the `internal` scope (R27). `kms:Sign` is
reachable only from `attest.KmsSigner`, enforced by two ArchUnit rules
(`KmsSignerArchitectureTest`, R22/L11/ADR-0004) that deliberately scan test sources too, not just main.

## 2. Existing code this task touches

T28 is a verification-only task — it should not need new production code. Everything it touches already
exists:

- `SECURITY-THREAT-MODEL.md` (repo root, **not** under `spec/`) — the threats table, rows #1–#6 owned by
  this service.
- The already-implemented test suite (697 tests as of T27) that this task must map against each row.
- `KmsSignerArchitectureTest` (`attest`) — the existing, direct evidence for "no non-attest path can
  reach `kms:Sign`."
- `QuorumEvaluatorTest`/`QuorumDecisionServiceTest`/`WatcherTest` (`quorum`/`watch`) — the existing,
  direct evidence for "no single-provider fact is ever emitted" (L1).

## 3. Established patterns to follow

- **Persistence:** JPA entities + Flyway DDL-only migrations; Hibernate validates, never generates DDL.
- **Outbox/idempotency:** every emitted event via outbox → `OutboxRelay` → Kafka; deterministic key
  `chain:txhash:eventtype` (L5).
- **Resource-server security:** JWT validation via `spring-boot-starter-oauth2-resource-server`; scope
  checks via `@PreAuthorize`/security-filter-chain config, not ad hoc checks in controllers.
- **ArchUnit as the enforcement mechanism for architectural LOCKED decisions:** `@ArchTest` fields are
  documentation-only in this repo (Surefire doesn't execute the JUnit5 ArchUnit engine here — confirmed
  independently at T20 and T25); a plain `@Test` canary calling `.check(...)` directly is what actually
  gates the build. Any new ArchUnit-style assertion this task needs must follow that same shape.
- **Fixed `Clock`, mocked providers for unit tests; Testcontainers (Postgres + Kafka, `LocalStack` for
  S3) for integration tests with fake provider adapters** — the real RPC providers are never called in
  tests (package.md §8).

## 4. Testing conventions

- Unit tests: plain JUnit 5, fixed `Clock` injected, mocked/fake collaborators.
- Integration tests: `@SpringBootTest` + `@Testcontainers`, real Postgres/Kafka/LocalStack, fake chain
  adapters (`FakeChainAdapter`) scripted to agree/disagree/lag/reorg.
- ArchUnit: package-boundary and cross-module-entity rules (T25's `CrossModuleEntityArchitectureTest`,
  T20/T27's `KmsSignerArchitectureTest`), both with a real negative-proof test (a deliberately-violating
  fixture class) proving the rule can actually fail, not just pass on already-clean code — an
  established, load-bearing discipline throughout this pipeline (Kimi Phase 8/11 findings caught this
  gap independently at both T20 and T25).
- Docker has been unavailable in this development environment throughout the entire session (T23–T27);
  Testcontainers-dependent tests compile and are correct by construction but have not executed locally.
  This is a standing, disclosed limitation, not something T28 can resolve.

## 5. Known gaps / unknowns

- **Threat-model row #5's stated mitigation ("Hash-chain ledger + S3 Object Lock + on-chain anchor")
  spans two services, only part of which is crypto-service's own scope.** `ARCHITECTURE.md` §6.5 and
  its data-ownership table (line 95) attribute the hash-chain ledger and the on-chain anchor to the
  **Payment Service** (`payments` schema, "weeks 5–9," not yet built per `ARCHITECTURE.md`'s own phased
  rollout) — not to crypto-service. `package.md`'s own threat-model row #5 "Implementing task" column
  lists only `crypto T08 (observation log) / T09 (quorum decision persistence)`, consistent with that:
  crypto-service's actual contribution is the verbatim persistence (L3) and the S3 WORM snapshot
  (`ObservationSnapshotStore`, T08) — confirmed present in main source (`ObservationLog`'s own Javadoc
  references the S3/Postgres dual-write ordering directly). No hash-chaining (`prevHash`/SHA-256 chain)
  or on-chain anchor code exists anywhere in `services/crypto`, and I did not find one in `requirements.md`
  either — I believe this is because those two pieces are correctly out of this service's scope, not a
  missing implementation, but this needs to be stated explicitly in Phase 1 rather than assumed silently,
  since T28's own task statement is a literal "verify each row has a corresponding passing test" and row
  #5's mitigation text, read literally and out of context, implies more than crypto-service actually owns.
- **`package.md` §11 Q6** (an anchor-write endpoint, `POST /internal/v1/anchors`) is an open question,
  explicitly a blocker for a *different* service's requirement (Payment R26), not this service's own. I
  do not know whether T28's "threat-model closure" is meant to touch this open question at all — Phase 1
  should check whether tasks.md's task 29 (spec status bump, gated on §11 Q1/Q2/Q3/Q7) implies Q6 is out
  of scope for reaching `READY FOR IMPL`, or whether T28 should note it as a residual, cross-service risk.
- I have not yet located a single, named test that asserts the "no single-provider fact is ever emitted"
  property at the *system* level (as opposed to `QuorumEvaluatorTest`'s and `WatcherTest`'s per-unit
  coverage) — Phase 1 needs to determine whether the existing per-unit tests are what "a corresponding
  passing test" means for this row, or whether a cross-cutting assertion is expected.
