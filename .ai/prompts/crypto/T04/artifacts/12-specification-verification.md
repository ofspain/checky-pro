# crypto · T04 · Phase 12 — Specification Verification

Principal-engineer sign-off pass over the final implementation + tests against `requirements.md`,
`design.md`, `tasks.md`, and the frozen brief (`artifacts/04-frozen-task-brief.md`), for T04 only.

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R26** — `EventTopics` routes each of the 5 `chain.*` aggregate types to its topic | Yes | `EventTopics.java:12-18` (verbatim `Map.of(...)` from design §4c), `:23-29` (`forAggregateType`) | Yes — named test `shouldRouteEachChainEventToItsTopic` (parameterized, ×5) + `unmappedAggregateTypeFailsLoudRatherThanGuessing` + null/blank boundary tests | No | No |
| **L5** — deterministic idempotency key, mechanically unavoidable to omit | Yes | `OutboxPublisher.java:41-51` (`requireNonNull` + `requireNonBlank` on `idempotencyKey`), DB `NOT NULL UNIQUE` (V1, pre-existing) | Yes — `OutboxPublisherTest` (null/blank tests), `OutboxTransactionIntegrationTest.publishingTheSameIdempotencyKeyTwiceInSeparateTransactionsThrowsOnTheSecondAttempt` (needs Docker) | No | No |
| **AC1–AC6 (R26)** | Yes | as above | Yes | No | No |
| **AC7 (L5)** — no overload omits the key; persisted row carries exactly what was passed | Yes | `OutboxPublisher.java:60-61`; `OutboxEvent.java` (idempotencyKey field) | Yes — `OutboxPublisherTest.publishSerializesPayloadAndSavesEventWithGivenMetadataIncludingIdempotencyKey` | No | No |
| **AC8 (agents.md)** — `OutboxPublisher` never calls a Kafka producer directly | Yes | `OutboxPublisher.java` — only `repository.save(...)`, no `KafkaTemplate` dependency anywhere in the class | Yes — implicit (class has no Kafka import at all) | No | No |
| **AC9 (confirmed gap)** — `crypto_app` can `INSERT`/`UPDATE` `chain.outbox` over real TCP | Yes | `V3__crypto_app_outbox_grant.sql` | Written, not run — `OutboxGrantMigrationIntegrationTest` (4 tests, needs Docker) | Not empirically proven in this environment | Documented, not hidden |
| **AC10 (`OutboxRelay`)** | Yes | `OutboxRelay.java:57-91` (`relayOne`) — send-then-mark, unroutable-skip, `BATCH_SIZE=100`, bounded `.get(30s)` timeout, scoped exception handling | Yes — `OutboxRelayTest` (14 tests: success, failure, timeout, interrupted, DB-save-failure, unroutable, empty batch, batch size, partial-batch-continuation, all-5-topics) | No | No |
| **AC11 (transaction join, amendment #5)** | Yes | `OutboxPublisher.java` — no `@Transactional` annotation anywhere | Written, not run — `OutboxTransactionIntegrationTest.publishInsideARolledBackTransactionPersistsNoRow`/`...CommittedTransactionPersistsTheRowWithTheGivenContent` (needs Docker) | Not empirically proven in this environment | Documented, not hidden |
| **AC12 (`Long` id, amendment #1)** | Yes | `OutboxEvent.java:30-32` (`@Id @GeneratedValue(IDENTITY)` typed `Long`) | Written, not run — `OutboxGrantMigrationIntegrationTest.cryptoAppCanInsertIntoOutboxAndTheGeneratedIdIsALong` (needs Docker) | Not empirically proven in this environment | Documented, not hidden |
| **L4/L11/L12/L13/L15 (T03, unrelated)** | N/A to this task | — | — | — | Untouched, confirmed via `git status` |

## Frozen-brief file-list compliance

`git status --porcelain services/crypto` (excluding `target/`) shows only files already on the frozen
brief's Files to Create/Modify list, plus two test-infrastructure additions necessitated directly by
accepted Phase 11 findings and disclosed in that phase's own resolution notes
(`OutboxEventRepository.findByIdempotencyKey`, needed so tests could assert on a specific row rather
than a table shared across the whole Testcontainers-backed test class). `git status --porcelain spec/`
is empty — no specification file was touched at any point in this task. `ChainBaselineMigrationIntegrationTest`
(T02) was modified, but only as the direct, necessary consequence of `V3` moving `outbox` from
ungranted to granted — a regression this task's own schema change caused and Phase 10 caught and fixed
(documented prominently in that phase's own artifact), not an unrelated change.

## Answers

**(1) Is the task fully complete?** Yes, for T04's own scope. `EventTopics` and `OutboxPublisher` (the
task statement's two named deliverables) are implemented and tested; the supporting infrastructure
Phase 1/2 identified as functionally unavoidable (`OutboxEvent`, `OutboxEventRepository`, `OutboxRelay`,
`KafkaProducerConfig`, `ClockConfig`, the `V3` grant migration) was built and is either tested directly
or reasoned through and disclosed where Docker prevented empirical proof.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC12 all have either a passing
test (42/42 Docker-independent tests green) or a written-but-unexecuted test plus direct code
inspection (AC9, AC11, AC12 — all three need Docker, unavailable in this environment). No AC is
unaddressed.

**(3) Does it violate any LOCKED decision?** No. L5 is respected (idempotency key required,
null-and-blank-checked, DB-enforced-unique, duplicate behavior tested). The task deliberately did
**not** touch any T03-scoped locked decision (L4/L11/L12/L13/L15) — confirmed by `git status` showing
no changes outside this task's own files. `agents.md`'s "Events & messaging" rules (outbox-in-same-
transaction, no direct producer call, topic naming, dedupe-on-idempotency-key,
depend-only-on-libs/contracts) are all followed; auth's `events/` package was mirrored structurally,
never imported.

**(4) Remaining risks?**
- **AC9/AC11/AC12 are reasoned-correct but not empirically proven in this environment** — Docker is
  unavailable (confirmed via `docker ps` at every phase from implementation through test generation).
  The two Testcontainers test classes (8 tests total) compile cleanly and follow the exact pattern
  `ChainBaselineMigrationIntegrationTest` already proved works in this repository; risk is low but
  not zero until they actually run.
- **No cross-event ordering guarantee** (`OutboxRelay`, amendment #8) — documented and tested
  (`oneFailedSendDoesNotStopTheRestOfTheBatch`) as a deliberate characteristic, not a defect; any
  future consumer of `chain.tx.*` events must tolerate out-of-order delivery.
- **No quarantine mechanism for unroutable events** (Phase 9/11 Finding 6) — deliberately rejected as
  out of reach for this task (would require either violating `outbox`'s immutable/verbatim schema or
  building alerting infrastructure not in this task's scope); documented as an accepted operational
  characteristic requiring a redeploy to resolve, should it ever actually occur (no aggregate type this
  service currently emits is unroutable).
- **The platform-level audience-validation gap noted at T03's Phase 4** is unrelated to this task and
  remains open at the platform level, unaffected by anything built here.

## Verdict

**PASS** — every requirement, LOCKED decision, and acceptance criterion in T04's scope is implemented
and traceable to the frozen brief; 42/42 runnable tests pass; the only unverified pieces (AC9/AC11/AC12)
are blocked solely by this environment's lack of Docker, not by any defect, and are written and ready
to run the moment Docker is available.
