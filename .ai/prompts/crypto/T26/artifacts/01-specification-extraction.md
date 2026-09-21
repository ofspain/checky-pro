# crypto · T26 · Phase 1 — Specification Extraction

## Business Rules

**Flow 1 (register → seen → confirmed → finalized → attest signature):**
- **R18.** Registering a watch via `POST /internal/v1/watches` returns a `watchId` with status `REGISTERED`.
- **R1.** Every fact is fetched from N independent providers, true only at 2-of-3 agreement.
- **R8.** First quorum-agreed sighting emits `chain.tx.seen`.
- **R9.** A `SEEN` transaction gaining confirmations under quorum emits `chain.tx.confirmed`.
- **R6/R7.** Finality is evaluated per-chain (Ethereum: beacon `finalized` checkpoint; Tron: solidified block).
- **R10.** Meeting the chain's finality policy under quorum emits `chain.tx.finalized`, never before.
- **R20.** A valid attest request for a tx that passed quorum+finality+screening returns
  `{ signature, kmsKeyId, signedAt, outcome: "SIGNED" }`.
- **R12.** Every emitted event carries `chain:txhash:eventtype`.

**Flow 2 (two-provider disagreement holds the fact, emits nothing):**
- **R1/R2/R3.** Disagreement → `HELD`, ops-alerted, never auto-resolved, no downstream event emitted for it.

**Flow 3 (reorg after confirmed emits `chain.tx.reorged`):**
- **R11.** A chain reorg invalidating a previously-observed transaction walks the watcher cursor
  backward and emits `chain.tx.reorged`.

**Flow 4 (sanctioned counterparty → attest `BLOCKED`):**
- **R21.** A sanctioned/OFAC hit under screening returns `{ outcome: "BLOCKED", reason }`, places the
  item in the compliance queue, and produces no signature.
- **R23 (cross-cutting to all 4 flows).** An attest request for a transaction that hasn't met quorum
  and finality is refused (never a signature) — implicitly proven by flows 2/3 never reaching a
  successful attest call.

## Locked Decisions

- **L1.** 2-of-3 quorum, no single-provider truth — the central property flows 1 and 2 together prove
  end-to-end (the package's own §9 checklist names this as needing a real test, not just a unit one).
- **L2.** Disagreement → `HELD`, ops-alerted, never auto-resolved (flow 2).
- **L3.** Observation log is verbatim and written first, before the quorum decision (implicit in every
  flow — the observation log's own append-only writes must precede every quorum decision this test
  drives).
- **L4.** Finality is a per-chain policy object (flow 1 — this task should exercise at least one real
  chain's actual policy, not a stubbed constant).
- **L5.** Deterministic idempotency key `chain:txhash:eventtype` on every event (all flows).
- **L6.** Reorg is a first-class transition; no forward-derived state survives it (flow 3).
- **L10.** Attestation only at proven finality — signing from anything less is impossible by
  construction (flows 2/3/4 must each independently prove attest never signs).
- **L11 (unmodified, relevant only for accurate exercise).** KMS-only signing, single path — flow 1's
  "attest returns a signature" clause is the one place this task calls the real signer.
- **L12.** Screening gates attestation, fail-closed (flow 4).

## Files involved

**Existing, real production code this task exercises (read-only, no changes anticipated):**
- `watch/WatchController.java`, `WatchService.java`, `Watch.java`, `WatchRepository.java` (registration).
- `watch/Watcher.java`, `WatcherRegistry.java`, `ChainCursor.java`, `ChainCursorRepository.java`
  (correlation, quorum hand-off, cursor).
- `quorum/QuorumEvaluator.java`, `QuorumDecisionService.java`, `QuorumDecision.java`,
  `HeldFactAlerter.java` (2-of-3 arbitration, `HELD` alerting).
- `observation/ObservationLog.java`, `Observation.java` (verbatim log).
- `finality/FinalityPolicy.java` and its Ethereum/Tron implementations (per-chain finality).
- `watch/TxLifecyclePublisher.java` (`seen`/`confirmed`/`finalized` events).
- `reorg/ReorgDetector.java` (`chain.tx.reorged`).
- `attest/AttestController.java`, `AttestationService.java`, `KmsSigner.java`,
  `AttestationRefusedException.java` (attest gate + real signing).
- `screening/ScreeningClient.java` (interface), `FailClosedScreeningClient.java` (real impl — Phase 0's
  own finding: cannot produce `BLOCKED`, a test double is required for flow 4).
- `events/OutboxEvent.java`, `OutboxPublisher.java`, `OutboxRelay.java`, `EventTopics.java` (real Kafka
  publish path).
- `adapter/FakeChainAdapter.java`, `adapter/ProviderSet.java` (the only permitted provider simulation
  mechanism, `agents.md`).

**Precedent to mirror the shape/style of:**
- The 8 existing `@SpringBootTest` + `@Testcontainers` + `PostgreSQLContainer` integration tests
  (e.g. `WatchRepositoryIntegrationTest`) — the closest existing pattern, though none boots the full
  pipeline or uses Kafka.
- `KmsSignerLocalStackIntegrationTest` — the established LocalStack pattern, if flow 1's "returns a
  signature" is read as requiring a real KMS round-trip rather than a mocked `KmsSigner`.

**New, this task's own likely deliverable (exact shape a Phase 2 decision):**
- One (or a small number of) new `@SpringBootTest` integration test class(es) under a location TBD at
  Phase 2, using real `PostgreSQLContainer` + `KafkaContainer` (both already declared dependencies,
  neither used by any existing test) + possibly `LocalStackContainer` for KMS.
- A test-scoped `ScreeningClient` implementation/bean override capable of returning `BLOCKED` for a
  configured address (Phase 0 finding — does not exist anywhere today).

## Dependencies

None new — `org.testcontainers:kafka`, `org.testcontainers:localstack`, `org.testcontainers:postgresql`,
`spring-boot-testcontainers`, and `org.testcontainers:junit-jupiter` are all already declared
(test-scope). `org.testcontainers:kafka` has zero existing usages to mirror; this task would be the
first.

## Acceptance Criteria

1. **AC1 (Flow 1, the primary happy path).** Registering a watch, then delivering matching 2-of-3
   provider answers through existence → confirmations → finality via real `FakeChainAdapter`s against
   a real Spring context, results in: `chain.tx.seen`, `chain.tx.confirmed`, and `chain.tx.finalized`
   all genuinely published (outbox row + real Kafka delivery), and a subsequent `POST /internal/v1/attest`
   call for that transaction returns `200 { outcome: "SIGNED", signature, kmsKeyId, signedAt }`.
2. **AC2 (Flow 2, disagreement).** A 2-provider-disagreement scenario results in the fact being
   persisted `HELD` (not `AGREED`), `HeldFactAlerter` invoked, and no `chain.tx.*` event for that fact
   published to the outbox/Kafka at all.
3. **AC3 (Flow 3, reorg).** After a transaction reaches `CONFIRMED`, a simulated reorg
   (`FakeChainAdapter.simulateReorg` reporting `exists=false`) results in `chain.tx.reorged` being
   published and the `ChainCursor` invalidated — no `chain.tx.finalized` is ever published for that
   transaction afterward.
4. **AC4 (Flow 4, sanctioned counterparty).** With a test-double `ScreeningClient` configured to return
   `BLOCKED` for a specific address, once quorum+finality are genuinely satisfied, `POST
   /internal/v1/attest` returns `200 { outcome: "BLOCKED", reason }` — no signature is produced, no
   `kms:Sign` call is made.

## Tests required

No new named test from `package.md` §8 — none is pre-mapped to this task, and the task statement itself
doesn't introduce a new named test. This task instead satisfies several of `package.md` §9's own
verification-checklist bullets with real, end-to-end evidence rather than per-unit mocked evidence:
the L1 bullet ("a test asserts every emitted fact passed 2-of-3 quorum") explicitly calls for exactly
this kind of test.

## Open Questions

Two genuine, disclosed gaps from Phase 0, requiring explicit Phase 2 design proposals rather than
blocking this task outright:

- **Docker is unavailable in this environment right now** — this task's own tests cannot be executed
  here as currently provisioned. Not a design blocker (the tests can still be written and are correct
  by construction/code review), but real execution/verification must wait for a Docker-available
  environment or be explicitly deferred, flagged transparently rather than silently claimed as passing.
- **The sanctioned-counterparty flow requires a new test-double `ScreeningClient`** that does not exist
  anywhere in this codebase today — Phase 2 must decide its exact shape (a `@TestConfiguration`
  `@Primary` bean override, a `@Profile`-gated test bean, or similar Spring-idiomatic mechanism).
