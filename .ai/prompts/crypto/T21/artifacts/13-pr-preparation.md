# crypto · T21 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Add crypto-service attest endpoint gating on quorum, finality, and screening (T21)
```

## Commit message

```
Add crypto-service attest endpoint gating on quorum, finality, and
screening (T21)

Wire together the three independently-built gates from T06-T20 -
quorum/finality decisions, counterparty screening, and KMS signing -
behind POST /internal/v1/attest, the platform's single most
consequential endpoint: the one that actually produces a Themistra
attestation.

AttestationService gates signing in a strict, non-negotiable order:
all of EXISTENCE/AMOUNT/TOKEN/FINALITY must be AGREED before the
counterparty is ever screened (the screening vendor is never called
for an unproven transaction - a self-caught contradiction from this
task's own earlier draft, where an acceptance criterion claimed
BLOCKED was reachable "regardless of quorum/finality state" while the
gate order itself made that impossible). The counterparty is the
transaction's fromAddress, not toAddress - the external, unknown
paying party, not the platform's own already-known watch address.
Since chain_cursors carries no uniqueness constraint on (chain,
tx_hash) - verified directly against the schema - more than one watch
can legitimately observe the same transaction; every distinct
fromAddress among them is screened, and a single sanctioned hit blocks
the whole request.

Every reachable outcome (SIGNED, BLOCKED, REFUSED) persists exactly
one row to chain.attestations before the response is returned. A
KMS/infrastructure failure during signing itself is a deliberately
different signal: it propagates uncaught, surfacing as 500 rather
than a 409 refusal, and persists no row at all, since none of the
three outcomes accurately describes an incomplete request.

Two new public read seams were added to existing services, following
this codebase's own established convention that a service - never a
repository directly - is a module's cross-module read API:
QuorumDecisionService.isAgreed and WatchService.findChainCursors.

Two real bugs were caught and fixed during implementation, before
either review phase ran: AttestationService's first draft threw a
refusal exception without persisting the REFUSED row first,
contradicting its own design and R20's "persist every outcome" -
caught by the refusal test's own mock-interaction assertion. Separately,
Attestation.receiptDigest needed @JdbcTypeCode(SqlTypes.CHAR), not the
first attempt's columnDefinition override, to match the DDL's
fixed-length CHAR(64) column - Hibernate's schema validator rejected
the mismatch at context startup.

Note: with no real screening vendor wired yet (Q2 unanswered), every
/attest request that reaches the screening gate in the current
deployed system resolves to 409 REFUSED - 200 SIGNED and 200 BLOCKED
are both reachable only in tests today. This is expected, disclosed
behavior, not a defect in this task.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created:**
- `services/crypto/src/main/java/com/themistra/crypto/attest/AttestOutcome.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/Attestation.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/AttestationRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/AttestRequest.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/AttestResponse.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/AttestationRefusedException.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/AttestExceptionHandler.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/AttestationService.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/AttestController.java`
- `services/crypto/src/main/resources/db/migration/V10__crypto_chain_cursors_chain_tx_hash_idx.sql`
- `services/crypto/src/test/java/com/themistra/crypto/attest/AttestOutcomeTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/AttestationTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/AttestationRepositoryIntegrationTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/AttestationServiceTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/AttestControllerTest.java`

**Modified:**
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecisionService.java` — new
  `isAgreed(chain, txHash, factType)` public read seam.
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursorRepository.java` — new
  `findByChainAndTxHash(chain, txHash) -> List<ChainCursor>` (package-private).
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchService.java` — new
  `findChainCursors(chain, txHash)` public read seam.
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` —
  Flyway-version-list assertion extended to include `"10"`.
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerSpringWiringTest.java` — narrowed
  `@ComponentScan(basePackageClasses = KmsSigner.class)` to `@Import(KmsSigner.class)`, fixing a
  test-isolation collision this task's own package growth exposed (a duplicate `clock` bean, since the
  broad scan swept up `AttestationRepositoryIntegrationTest`'s nested test config too).
- `services/crypto/src/test/java/com/themistra/crypto/quorum/QuorumDecisionServiceTest.java` — 4 new
  `isAgreed` tests.
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchServiceTest.java` — 2 new
  `findChainCursors` tests.
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatchRepositoryIntegrationTest.java` — 1 new
  real-DB multi-cursor test.

23 files changed (15 created, 8 modified), +1276/-4 lines. One new migration (`V10`, additive index only
— no new table, column, or grant).

## Summary

Delivers the platform's core attestation orchestration: `AttestationService` composes three
independently-built, independently-tested gates (T06-T18's quorum/finality, T19's screening, T20's KMS
signer) behind `POST /internal/v1/attest`, refusing to sign unless `EXISTENCE`/`AMOUNT`/`TOKEN`/`FINALITY`
are all `AGREED` and the transaction's counterparty screens `CLEARED` — in that strict order, so the
screening vendor is never called for a transaction that hasn't yet proven itself. Every reachable outcome
(`SIGNED`, `BLOCKED`, `REFUSED`) is persisted to `chain.attestations`, forming the audit trail R20
requires; a `KMS`/infrastructure fault is deliberately routed differently — an uncaught `500`, with no
row persisted, since none of the three outcomes accurately describes an incomplete request.

Design review (Phase 3) surfaced and corrected a genuine internal contradiction in the task's own first
draft (an acceptance criterion claiming `BLOCKED` was reachable "regardless of quorum/finality state," at
odds with the gate order the same draft specified) and a real schema gap, verified directly against the
DDL: `chain_cursors` carries no uniqueness constraint on `(chain, tx_hash)`, so a naive
`Optional<ChainCursor>` lookup would have silently discarded legitimate multi-watch transactions. The
final design screens every distinct `fromAddress` among all matching cursors and fails closed (`BLOCKED`)
on the first hit.

Two real defects were caught and fixed during implementation, before either review phase ran: a missing
`REFUSED`-row persistence in the earliest refusal path (caught by the test suite's own mock-interaction
assertion, not by inspection), and a JPA column-type mismatch on `receiptDigest` (the DDL's fixed-length
`CHAR(64)` needed `@JdbcTypeCode(SqlTypes.CHAR)`, not the first attempt's `columnDefinition` override,
which only affects schema generation, not Hibernate's own validator).

One disclosed, non-defect operational fact carries into this PR: with no real screening vendor wired yet
(Q2 unanswered), every `/attest` request that reaches the screening gate in the current deployed system
resolves to `409 REFUSED`, since T19's `FailClosedScreeningClient` unconditionally returns `ERROR`. `200
SIGNED` and `200 BLOCKED` are both reachable only in tests until a real vendor is wired — expected,
disclosed behavior, not a bug in this task.

## Testing performed

- `mvn -pl services/crypto test-compile` — clean.
- `mvn -pl services/crypto test -Dtest=AttestOutcomeTest,AttestationTest,AttestationRepositoryIntegrationTest,AttestationServiceTest,AttestControllerTest,QuorumDecisionServiceTest,WatchServiceTest,WatchRepositoryIntegrationTest,KmsSignerArchitectureTest,ResourceServerConfigIntegrationTest`
  — 110/110 pass.
- `mvn -pl services/crypto -am test` (full module regression) — 704 tests, 6 failures, all pre-existing
  and unrelated to this task (disclosed since T18/T19/T20:
  `ObservationRepositoryIntegrationTest` ×2, `ProviderHealthRepositoryIntegrationTest` ×1,
  `QuorumDecisionRepositoryIntegrationTest` ×1, `TokenAllowlistRepositoryIntegrationTest` ×2). Zero
  regressions.
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`:
  `artifacts/12-specification-verification.md` — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 21 ("Attest endpoint").
- **Requirements:** R20 (KMS signature + persisted audit trail), R21 (`BLOCKED` on sanctioned
  counterparty), R23 (`409` on unmet quorum/finality).
- **LOCKED decisions:** L10 (attestation only at proven finality; screening only reachable after
  quorum+finality). L11 (KMS-only signing, single path — unmodified, re-verified). L12 (screening
  gates attestation, fail-closed). Two new task-scoped Locked Decisions introduced at Phase 4:
  L-T21a (counterparty is `fromAddress`, not `toAddress`), L-T21b (a KMS/infra failure is a `500`,
  never converted to `REFUSED`).
- **Deferred, human-approved Open Questions:** R21's compliance-queue mechanism; `/attest` idempotency.
  Neither is required by any task in `tasks.md`; both are disclosed, not silently dropped.
